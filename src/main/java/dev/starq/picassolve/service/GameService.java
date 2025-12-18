package dev.starq.picassolve.service;

import dev.starq.picassolve.dto.DrawEvent;
import dev.starq.picassolve.dto.ScoreBoardEntry;
import dev.starq.picassolve.entity.User;
import dev.starq.picassolve.entity.User.Role;
import dev.starq.picassolve.entity.Word;
import dev.starq.picassolve.repository.UserRepository;
import dev.starq.picassolve.repository.WordRepository;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게임의 핵심 상태 및 브로드캐스트를 관리하는 서비스.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GameService {

    private final UserRepository userRepo;
    private final WordRepository wordRepo;
    private final SimpMessagingTemplate broker;

    // --- 게임 상태 변수 ---
    private volatile String currentWord = null;
    private final Object lock = new Object();
    private final Set<String> online = ConcurrentHashMap.newKeySet();
    private volatile long lastDrawAtMs = 0L;

    // --- 상수 설정 ---
    private static final long DRAW_COOLDOWN_MS = 30_000L;
    private static final String ADMIN_NAME = "SYSTEM";
    private static final int MAX_ACTIONS = 1_200;
    private static final int MAX_TOTAL_SEGMENTS = 40_000;
    private static final long MAX_ACTION_AGE_MS = 10 * 60_000L;

    /* -------------------------------------------------------------------------- */
    /* 1. Session (Login/Logout) */
    /* -------------------------------------------------------------------------- */

    @Transactional
    public boolean login(String name) {
        if (!userRepo.existsByName(name))
            return false;
        if (online.size() >= 30 && !online.contains(name))
            return false;

        online.add(name);

        userRepo.findByName(name).ifPresent(u -> {
            if (ADMIN_NAME.equals(u.getName())) {
                u.setRole(Role.ADMIN);
            } else if (u.getRole() == null) {
                u.setRole(Role.PARTICIPANT);
            }
            log.info("[게임] 유저 로그인: {} (현재 접속자: {}명)", u.getName(), online.size());
        });

        publishUsersAndScoreboard();
        return true;
    }

    @Transactional
    public void logout(String name) {
        if (online.remove(name)) {
            log.info("[게임] 유저 로그아웃: {} (현재 접속자: {}명)", name, online.size());
        }
        publishUsersAndScoreboard();
    }

    /* -------------------------------------------------------------------------- */
    /* 2. Round & Word Logic */
    /* -------------------------------------------------------------------------- */

    @Transactional
    public void rerollWord(Principal p) {
        if (p == null)
            return;
        User me = userRepo.findByName(p.getName()).orElse(null);
        if (me == null || me.getRole() != Role.DRAWER)
            return;

        synchronized (lock) {
            currentWord = pickRandomWordDifferentFrom(currentWord);
            log.info("[게임] 제시어 다시 받기: {} (새 제시어: {})", me.getName(), currentWord);
            startNewRoundAndBroadcast(me, me.getName() + "님이 제시어를 다시 받았습니다.");
        }
    }

    private void startNewRoundAndBroadcast(User drawer, String systemMsg) {
        resetDrawingState(true);

        broker.convertAndSendToUser(drawer.getName(), "/queue/word", currentWord);
        userRepo.findByName(ADMIN_NAME)
                .ifPresent(admin -> broker.convertAndSendToUser(admin.getName(), "/queue/word", currentWord));

        if (systemMsg != null && !systemMsg.isBlank()) {
            publishChat("SYSTEM", systemMsg, true);
        }

        publishUsersAndScoreboard();
        publishWordLen();
    }

    /* -------------------------------------------------------------------------- */
    /* 3. Role Management */
    /* -------------------------------------------------------------------------- */

    @Transactional
    public void setMeAsDrawer(Principal p) {
        if (p == null)
            return;
        User me = userRepo.findByName(p.getName()).orElse(null);
        if (me == null)
            return;

        long elapsed = System.currentTimeMillis() - lastDrawAtMs;
        if (elapsed < DRAW_COOLDOWN_MS) {
            long remain = (DRAW_COOLDOWN_MS - elapsed) / 1000 + 1;
            throw new IllegalStateException("출제자가 그림을 그리는 중입니다. '내가 그리기'는 " + remain + "초 후에 가능합니다.");
        }

        synchronized (lock) {
            if (me.getRole() == Role.DRAWER)
                return;
            makeAllDrawersParticipants();
            me.setRole(Role.DRAWER);
            currentWord = pickRandomWord();
            log.info("[게임] '내가 그리기'로 출제자 변경: {} (새 제시어: {})", me.getName(), currentWord);
            startNewRoundAndBroadcast(me, me.getName() + "님이 출제자로 지정되었습니다.");
        }
    }

    @Transactional
    public void setDrawerByAdmin(String adminName, String targetUserName) {
        User admin = userRepo.findByName(adminName).orElseThrow();
        if (admin.getRole() != Role.ADMIN)
            throw new RuntimeException("관리자만 가능");

        synchronized (lock) {
            makeAllDrawersParticipants();
            User drawer = userRepo.findByName(targetUserName).orElseThrow();
            drawer.setRole(Role.DRAWER);
            currentWord = pickRandomWord();
            log.info("[게임] 관리자 권한으로 출제자 변경: {} -> {} (새 제시어: {})", adminName, targetUserName, currentWord);
            startNewRoundAndBroadcast(drawer, null);
        }
    }

    private void makeAllDrawersParticipants() {
        userRepo.findAll().forEach(u -> {
            if (u.getRole() == Role.DRAWER)
                u.setRole(Role.PARTICIPANT);
        });
    }

    /* -------------------------------------------------------------------------- */
    /* 4. Chat & Answer Logic */
    /* -------------------------------------------------------------------------- */

    @Transactional
    public void handleChat(String from, String text) {
        String raw = (text == null) ? "" : text;
        String msg = raw.trim();

        synchronized (lock) {
            boolean fromIsDrawer = userRepo.findByName(from).map(u -> u.getRole() == Role.DRAWER).orElse(false);
            boolean fromIsAdmin = userRepo.findByName(from).map(u -> u.getRole() == Role.ADMIN).orElse(false);

            if ((fromIsDrawer || fromIsAdmin) && currentWord != null && msg.equals(currentWord)) {
                broker.convertAndSend("/topic/chat",
                        Map.of("from", from, "text", maskWord(currentWord), "system", false));
                return;
            }

            publishChat(from, raw, false);

            if (currentWord != null && msg.equals(currentWord)) {
                User winner = userRepo.findByName(from).orElseThrow();
                if (winner.getRole() == Role.PARTICIPANT) {
                    winner.setScore(winner.getScore() + 1);
                    makeAllDrawersParticipants();
                    winner.setRole(Role.DRAWER);
                    String oldWord = currentWord;
                    currentWord = pickRandomWord();
                    log.info("[게임] 정답 발생! 승자: {} (정답: {}), 다음 제시어: {}", from, oldWord, currentWord);
                    startNewRoundAndBroadcast(winner, winner.getName() + "님 정답! [" + text + "]");
                }
            }
        }
    }

    /* -------------------------------------------------------------------------- */
    /* 5. Drawing & Canvas */
    /* -------------------------------------------------------------------------- */

    public boolean canDraw(Principal principal) {
        if (principal == null)
            return false;
        return userRepo.findByName(principal.getName()).map(u -> u.getRole() == Role.DRAWER).orElse(false);
    }

    private final List<StrokeAction> strokeActions = new ArrayList<>();
    private int totalSegments = 0;

    public void addStroke(Principal p, DrawEvent e) {
        if (!canDraw(p))
            return;
        if (e.getMode() == null || e.getMode().isBlank())
            e.setMode("pen");
        if (e.getActionId() == null || e.getActionId().isBlank()) {
            e.setActionId(UUID.randomUUID().toString());
            e.setNewStroke(Boolean.TRUE);
        }

        synchronized (strokeActions) {
            if (Boolean.TRUE.equals(e.getNewStroke()) || strokeActions.isEmpty()
                    || !strokeActions.get(strokeActions.size() - 1).id.equals(e.getActionId())) {
                StrokeAction a = new StrokeAction(e.getActionId());
                a.segments.add(e);
                strokeActions.add(a);
                totalSegments++;
            } else {
                strokeActions.get(strokeActions.size() - 1).segments.add(e);
                totalSegments++;
            }
            trimStrokeHistoryLocked();
        }
        broker.convertAndSend("/topic/draw", e);
        lastDrawAtMs = System.currentTimeMillis();
    }

    public void undoLastStroke(Principal p) {
        if (!canDraw(p))
            return;
        String removedId;
        synchronized (strokeActions) {
            if (strokeActions.isEmpty())
                return;
            StrokeAction removed = strokeActions.remove(strokeActions.size() - 1);
            removedId = removed.id;
            totalSegments -= removed.segments.size();
        }
        broker.convertAndSend("/topic/undo", Map.of("actionId", removedId));
    }

    public void clearCanvas(Principal p) {
        if (!canDraw(p))
            return;
        resetDrawingState(true);
    }

    private void resetDrawingState(boolean broadcastClear) {
        synchronized (strokeActions) {
            strokeActions.clear();
            totalSegments = 0;
        }
        if (broadcastClear)
            broker.convertAndSend("/topic/canvas/clear", "");
    }

    private void trimStrokeHistoryLocked() {
        long now = System.currentTimeMillis();
        while (!strokeActions.isEmpty()) {
            StrokeAction oldest = strokeActions.get(0);
            boolean overCount = strokeActions.size() > MAX_ACTIONS;
            boolean overSegments = totalSegments > MAX_TOTAL_SEGMENTS;
            boolean tooOld = now - oldest.createdAtMs > MAX_ACTION_AGE_MS;
            if (!overCount && !overSegments && !tooOld)
                break;
            strokeActions.remove(0);
            totalSegments -= oldest.segments.size();
        }
        if (totalSegments < 0)
            totalSegments = 0;
    }

    /* -------------------------------------------------------------------------- */
    /* 6. State Sync & Snapshots */
    /* -------------------------------------------------------------------------- */

    public void sendSnapshotTo(String username) {
        List<User> onlineUsers = online.isEmpty() ? List.of() : userRepo.findByNameIn(online);
        List<String> users = onlineUsers.stream().map(u -> u.getName() + " (" + u.getRole() + ")").toList();
        broker.convertAndSendToUser(username, "/queue/users", users);

        List<ScoreBoardEntry> ranking = userRepo.findAll().stream()
                .filter(u -> u.getScore() > 0)
                .sorted(Comparator.comparingInt(User::getScore).reversed())
                .map(u -> new ScoreBoardEntry(u.getName(), u.getTeam(), u.getScore()))
                .toList();
        broker.convertAndSendToUser(username, "/queue/scoreboard", ranking);

        broker.convertAndSendToUser(username, "/queue/wordlen", computeWordLen(currentWord));
        userRepo.findByName(username).ifPresent(u -> {
            if (currentWord != null && (u.getRole() == Role.DRAWER || u.getRole() == Role.ADMIN)) {
                broker.convertAndSendToUser(username, "/queue/word", currentWord);
            }
        });

        sendCanvasSnapshotTo(username);
    }

    public void sendCanvasSnapshotTo(String username) {
        broker.convertAndSendToUser(username, "/queue/canvas/clear", "");
        synchronized (strokeActions) {
            for (var action : strokeActions) {
                for (var seg : action.segments) {
                    broker.convertAndSendToUser(username, "/queue/draw", seg);
                }
            }
        }
    }

    private void publishUsersAndScoreboard() {
        List<User> onlineUsers = online.isEmpty() ? List.of() : userRepo.findByNameIn(online);
        List<String> users = onlineUsers.stream().map(u -> u.getName() + " (" + u.getRole() + ")").toList();
        broker.convertAndSend("/topic/users", users);

        List<ScoreBoardEntry> ranking = userRepo.findAll().stream()
                .filter(u -> u.getScore() > 0)
                .sorted(Comparator.comparingInt(User::getScore).reversed())
                .map(u -> new ScoreBoardEntry(u.getName(), u.getTeam(), u.getScore()))
                .toList();
        broker.convertAndSend("/topic/scoreboard", ranking);
    }

    /* -------------------------------------------------------------------------- */
    /* 7. Utility & Helpers */
    /* -------------------------------------------------------------------------- */

    private int computeWordLen(String word) {
        if (word == null)
            return 0;
        String cleaned = word.replaceAll("\\s+", "");
        return cleaned.codePointCount(0, cleaned.length());
    }

    private void publishWordLen() {
        broker.convertAndSend("/topic/wordlen", computeWordLen(currentWord));
    }

    private String maskWord(String word) {
        int n = Math.max(1, computeWordLen(word));
        return "☆".repeat(n);
    }

    private String pickRandomWord() {
        List<Word> all = wordRepo.findAll();
        if (all.isEmpty())
            throw new RuntimeException("단어 DB가 비었습니다");
        return all.get(ThreadLocalRandom.current().nextInt(all.size())).getText();
    }

    private String pickRandomWordDifferentFrom(String prev) {
        List<Word> all = wordRepo.findAll();
        if (all.isEmpty())
            throw new RuntimeException("단어 DB가 비었습니다");
        if (all.size() == 1)
            return all.get(0).getText();
        String next;
        do {
            next = all.get(ThreadLocalRandom.current().nextInt(all.size())).getText();
        } while (Objects.equals(next, prev));
        return next;
    }

    private void publishChat(String from, String text, boolean system) {
        broker.convertAndSend("/topic/chat", Map.of("from", from, "text", text, "system", system));
    }

    static class StrokeAction {
        final String id;
        final long createdAtMs = System.currentTimeMillis();
        final List<DrawEvent> segments = new ArrayList<>();

        StrokeAction(String id) {
            this.id = id;
        }
    }
}
