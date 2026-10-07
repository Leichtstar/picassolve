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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
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
    /** Users with at least one active WebSocket (game presence). */
    private final Set<String> online = ConcurrentHashMap.newKeySet();
    /** Per-user WS connection ref-count (refresh/multi-tab safe). */
    private final ConcurrentHashMap<String, AtomicInteger> wsConnections = new ConcurrentHashMap<>();
    private volatile long lastDrawAtMs = 0L;
    private final AtomicLong lastStrokeNs = new AtomicLong(0L);
    private volatile List<String> wordCache = null;

    // Live stroke micro-batch (flush every ~32ms or 24 segs) → /topic/draw-batch
    private final List<DrawEvent> liveDrawBatch = new ArrayList<>();
    private final ScheduledExecutorService drawFlushScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "draw-live-flush");
                t.setDaemon(true);
                return t;
            });

    // --- 상수 설정 ---
    private static final long DRAW_COOLDOWN_MS = 30_000L;
    private static final String ADMIN_NAME = "SYSTEM";
    private static final int MAX_ONLINE = 30;
    private static final int MAX_ACTIONS = 1_200;
    private static final int MAX_TOTAL_SEGMENTS = 40_000;
    private static final long MAX_ACTION_AGE_MS = 10 * 60_000L;
    private static final int MAX_CHAT_LEN = 200;
    private static final int SCOREBOARD_LIMIT = 50;
    private static final int SNAPSHOT_CHUNK = 500;
    private static final double MAX_STROKE_WIDTH = 40;
    private static final int MAX_COLOR_LEN = 32;
    private static final int MAX_ACTION_ID_LEN = 64;
    /** Soft anti-flood: ~1000 segments/sec. Normal mouse drawing stays well under this. */
    private static final long MIN_STROKE_INTERVAL_NS = 1_000_000L;
    private static final int LIVE_DRAW_BATCH_MAX = 24;
    private static final long LIVE_DRAW_FLUSH_MS = 32L;

    @PostConstruct
    void startDrawFlusher() {
        drawFlushScheduler.scheduleAtFixedRate(this::flushLiveDrawBatchSafe,
                LIVE_DRAW_FLUSH_MS, LIVE_DRAW_FLUSH_MS, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stopDrawFlusher() {
        drawFlushScheduler.shutdownNow();
        flushLiveDrawBatchSafe();
    }

    /* -------------------------------------------------------------------------- */
    /* 1. Presence (HTTP session vs WebSocket) */
    /* -------------------------------------------------------------------------- */

    /** Capacity gate for HTTP login — does not add to online. */
    public boolean hasCapacityFor(String name) {
        if (name == null || name.isBlank())
            return false;
        if (!userRepo.existsByName(name))
            return false;
        return online.contains(name) || online.size() < MAX_ONLINE;
    }

    @Transactional
    public void ensureLoginRoles(String name) {
        userRepo.findByName(name).ifPresent(u -> {
            if (ADMIN_NAME.equals(u.getName())) {
                u.setRole(Role.ADMIN);
            } else if (u.getRole() == null) {
                u.setRole(Role.PARTICIPANT);
            }
        });
    }

    /**
     * First WS connect joins the online set (with capacity check).
     * Subsequent tabs/reconnects only bump the ref-count.
     */
    @Transactional
    public boolean wsConnect(String name) {
        if (name == null || name.isBlank() || !userRepo.existsByName(name))
            return false;

        AtomicInteger counter = wsConnections.computeIfAbsent(name, k -> new AtomicInteger(0));
        int next = counter.incrementAndGet();
        if (next == 1) {
            if (online.size() >= MAX_ONLINE && !online.contains(name)) {
                counter.decrementAndGet();
                if (counter.get() <= 0)
                    wsConnections.remove(name, counter);
                return false;
            }
            online.add(name);
            ensureLoginRoles(name);
            log.info("[게임] WS 접속(온라인 진입): {} (접속자: {}명)", name, online.size());
            publishUsersAndScoreboard();
        } else {
            log.debug("[게임] WS 추가 연결: {} (connections={})", name, next);
        }
        return true;
    }

    /** Decrement WS ref-count; leave online only when last connection closes. */
    public void wsDisconnect(String name) {
        if (name == null)
            return;
        AtomicInteger counter = wsConnections.get(name);
        if (counter == null)
            return;
        int left = counter.decrementAndGet();
        if (left <= 0) {
            wsConnections.remove(name, counter);
            if (online.remove(name)) {
                log.info("[게임] WS 종료(오프라인): {} (접속자: {}명)", name, online.size());
                publishUsersAndScoreboard();
            }
        } else {
            log.debug("[게임] WS 연결 감소: {} (connections={})", name, left);
        }
    }

    /** HTTP logout / kick — clear presence immediately. */
    public void forceLeave(String name) {
        if (name == null)
            return;
        wsConnections.remove(name);
        if (online.remove(name)) {
            log.info("[게임] 강제 퇴장: {} (접속자: {}명)", name, online.size());
            publishUsersAndScoreboard();
        }
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
        if (me.getRole() == Role.ADMIN)
            throw new IllegalStateException("관리자는 '내가 그리기'를 사용할 수 없습니다.");

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
        userRepo.findByRole(Role.DRAWER).forEach(u -> u.setRole(Role.PARTICIPANT));
    }

    /* -------------------------------------------------------------------------- */
    /* 4. Chat & Answer Logic */
    /* -------------------------------------------------------------------------- */

    @Transactional
    public void handleChat(String from, String text) {
        String raw = (text == null) ? "" : text;
        if (raw.length() > MAX_CHAT_LEN) {
            raw = raw.substring(0, MAX_CHAT_LEN);
        }
        String msg = raw.trim();

        synchronized (lock) {
            User sender = userRepo.findByName(from).orElse(null);
            if (sender == null)
                return;
            boolean fromIsDrawer = sender.getRole() == Role.DRAWER;
            boolean fromIsAdmin = sender.getRole() == Role.ADMIN;

            if ((fromIsDrawer || fromIsAdmin) && currentWord != null && msg.equals(currentWord)) {
                broker.convertAndSend("/topic/chat",
                        Map.of("from", from, "text", maskWord(currentWord), "system", false));
                return;
            }

            publishChat(from, raw, false);

            if (currentWord != null && msg.equals(currentWord)) {
                if (sender.getRole() == Role.PARTICIPANT) {
                    sender.setScore(sender.getScore() + 1);
                    makeAllDrawersParticipants();
                    sender.setRole(Role.DRAWER);
                    String oldWord = currentWord;
                    currentWord = pickRandomWord();
                    log.info("[게임] 정답 발생! 승자: {} (정답: {}), 다음 제시어: {}", from, oldWord, currentWord);
                    startNewRoundAndBroadcast(sender, sender.getName() + "님 정답! [" + text + "]");
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
        if (!canDraw(p) || e == null)
            return;
        if (!Double.isFinite(e.getX1()) || !Double.isFinite(e.getY1())
                || !Double.isFinite(e.getX2()) || !Double.isFinite(e.getY2())
                || !Double.isFinite(e.getWidth()))
            return;
        double width = e.getWidth();
        if (width < 1)
            width = 1;
        if (width > MAX_STROKE_WIDTH)
            width = MAX_STROKE_WIDTH;
        e.setWidth(width);
        if (e.getColor() != null && e.getColor().length() > MAX_COLOR_LEN)
            return;
        if (e.getActionId() != null && e.getActionId().length() > MAX_ACTION_ID_LEN)
            return;

        long nowNs = System.nanoTime();
        long prevNs = lastStrokeNs.get();
        if (prevNs != 0L && nowNs - prevNs < MIN_STROKE_INTERVAL_NS)
            return;
        lastStrokeNs.set(nowNs);

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
        queueLiveDraw(e);
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
        synchronized (liveDrawBatch) {
            liveDrawBatch.clear();
        }
        if (broadcastClear)
            broker.convertAndSend("/topic/canvas/clear", "");
    }

    private void queueLiveDraw(DrawEvent e) {
        List<DrawEvent> toSend = null;
        synchronized (liveDrawBatch) {
            liveDrawBatch.add(e);
            if (liveDrawBatch.size() >= LIVE_DRAW_BATCH_MAX) {
                toSend = List.copyOf(liveDrawBatch);
                liveDrawBatch.clear();
            }
        }
        if (toSend != null) {
            broker.convertAndSend("/topic/draw-batch", toSend);
        }
    }

    private void flushLiveDrawBatchSafe() {
        try {
            List<DrawEvent> toSend;
            synchronized (liveDrawBatch) {
                if (liveDrawBatch.isEmpty())
                    return;
                toSend = List.copyOf(liveDrawBatch);
                liveDrawBatch.clear();
            }
            broker.convertAndSend("/topic/draw-batch", toSend);
        } catch (Exception ex) {
            log.debug("[게임] live draw flush 실패: {}", ex.toString());
        }
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

        List<ScoreBoardEntry> ranking = topScoreboard();
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
        List<DrawEvent> batch = new ArrayList<>(SNAPSHOT_CHUNK);
        synchronized (strokeActions) {
            for (var action : strokeActions) {
                for (var seg : action.segments) {
                    batch.add(seg);
                    if (batch.size() >= SNAPSHOT_CHUNK) {
                        broker.convertAndSendToUser(username, "/queue/draw-batch", List.copyOf(batch));
                        batch.clear();
                    }
                }
            }
        }
        if (!batch.isEmpty()) {
            broker.convertAndSendToUser(username, "/queue/draw-batch", batch);
        }
    }

    private void publishUsersAndScoreboard() {
        List<User> onlineUsers = online.isEmpty() ? List.of() : userRepo.findByNameIn(online);
        List<String> users = onlineUsers.stream().map(u -> u.getName() + " (" + u.getRole() + ")").toList();
        broker.convertAndSend("/topic/users", users);

        List<ScoreBoardEntry> ranking = topScoreboard();
        broker.convertAndSend("/topic/scoreboard", ranking);
    }


    private List<ScoreBoardEntry> topScoreboard() {
        return userRepo.findTopByScore(PageRequest.of(0, SCOREBOARD_LIMIT)).stream()
                .map(u -> new ScoreBoardEntry(u.getName(), u.getTeam(), u.getScore()))
                .toList();
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

    private List<String> wordTexts() {
        List<String> cached = wordCache;
        if (cached != null)
            return cached;
        synchronized (this) {
            if (wordCache == null) {
                wordCache = wordRepo.findAll().stream().map(Word::getText).toList();
            }
            return wordCache;
        }
    }

    private String pickRandomWord() {
        List<String> all = wordTexts();
        if (all.isEmpty())
            throw new RuntimeException("단어 DB가 비었습니다");
        return all.get(ThreadLocalRandom.current().nextInt(all.size()));
    }

    private String pickRandomWordDifferentFrom(String prev) {
        List<String> all = wordTexts();
        if (all.isEmpty())
            throw new RuntimeException("단어 DB가 비었습니다");
        if (all.size() == 1)
            return all.get(0);
        String next;
        do {
            next = all.get(ThreadLocalRandom.current().nextInt(all.size()));
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
