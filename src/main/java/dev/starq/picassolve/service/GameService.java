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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게임의 핵심 상태/브로드캐스트를 관리하는 서비스.
 * - 로그인/로그아웃 및 온라인 유저 집계
 * - 출제자/제시어/라운드 전환
 * - 채팅 정답 판정 및 마스킹
 * - 드로잉(세그먼트/액션) 브로드캐스트 + 실행취소/스냅샷
 * - 랭킹(전체 유저, 0점 제외) 및 참여자 목록(온라인만) 송출
 */
@Service
@RequiredArgsConstructor
public class GameService {

    private final UserRepository userRepo;
    private final WordRepository wordRepo;
    private final SimpMessagingTemplate broker;

    /** 현재 라운드의 제시어(출제자/관리자에게만 공개). */
    private volatile String currentWord = null;

    /** 제시어/역할/라운드 전환 등 공용 상태 보호용 락. */
    private final Object lock = new Object();

    /** 현재 접속 중인 사용자 이름(세션 기준). */
    private final Set<String> online = ConcurrentHashMap.newKeySet();

    /** 마지막 드로잉 시각(epoch ms) — ‘내가 그리기’ 쿨타임용. */
    private volatile long lastDrawAtMs = 0L;

    /** ‘내가 그리기’ 버튼 쿨타임(밀리초). */
    private static final long DRAW_COOLDOWN_MS = 30_000L;

    /** 내부적으로 지정한 관리자 이름(필요시 설정값으로 교체 가능). */
    private static final String ADMIN_NAME = "SYSTEM";

    /* --------------------------------- 로그인/로그아웃 --------------------------------- */

    /** 사전 등록된 유저만 로그인 허용, 최대 30명. */
    @Transactional
    public boolean login(String name) {
        if (!userRepo.existsByName(name)) return false;
        if (online.size() >= 30 && !online.contains(name)) return false;

        online.add(name);

        // 역할 기본값/관리자 지정
        userRepo.findByName(name).ifPresent(u -> {
            if (ADMIN_NAME.equals(u.getName())) {
                u.setRole(Role.ADMIN);
            } else if (u.getRole() == null) {
                u.setRole(Role.PARTICIPANT);
            }
        });

        publishUsersAndScoreboard();
        return true;
    }

    /** 온라인 집합에서 제거하고 스냅샷 재송출. */
    @Transactional
    public void logout(String name) {
        online.remove(name);
        publishUsersAndScoreboard();
    }

    /* --------------------------------- 관리자 기능 --------------------------------- */

    /** 관리자: 특정 유저를 출제자로 지정하고 새 라운드를 시작. */
    @Transactional
    public void setDrawerByAdmin(String adminName, String targetUserName) {
        User admin = userRepo.findByName(adminName).orElseThrow();
        if (admin.getRole() != Role.ADMIN) throw new RuntimeException("관리자만 가능");

        synchronized (lock) {
            makeAllDrawersParticipants();                   // 기존 출제자 해제
            User drawer = userRepo.findByName(targetUserName).orElseThrow();
            drawer.setRole(Role.DRAWER);

            currentWord = pickRandomWord();                 // 새 제시어 선정
            startNewRoundAndBroadcast(drawer,               // 라운드 초기화 + 단어/글자수/유저/랭킹 송출
                /*systemMsg*/ null);
        }
    }

    /* --------------------------------- 채팅/정답 --------------------------------- */

    /**
     * 채팅 수신:
     * - 출제자/관리자가 정답을 입력하면 마스킹 후 브로드캐스트(정답 처리 없음)
     * - 일반 참가자가 정답을 맞추면 점수 + 출제자 권한 이전 + 새 라운드 시작
     */
    @Transactional
    public void handleChat(String from, String text) {
        String raw = (text == null) ? "" : text;
        String msg = raw.trim();

        synchronized (lock) {
            boolean fromIsDrawer = userRepo.findByName(from)
                .map(u -> u.getRole() == Role.DRAWER).orElse(false);
            boolean fromIsAdmin  = userRepo.findByName(from)
                .map(u -> u.getRole() == Role.ADMIN).orElse(false);

            // 출제자/관리자가 정답을 그대로 입력 → 정답 미처리 + 마스킹만 전송
            if ((fromIsDrawer || fromIsAdmin) && currentWord != null && msg.equals(currentWord)) {
                broker.convertAndSend("/topic/chat",
                    Map.of("from", from, "text", maskWord(currentWord), "system", false));
                return;
            }

            // 일반 채팅 브로드캐스트
            publishChat(from, raw, false);

            // 비-출제자/비-관리자 정답 처리
            if (currentWord != null && msg.equals(currentWord)) {
                User winner = userRepo.findByName(from).orElseThrow();
                if (winner.getRole() == Role.PARTICIPANT) {
                    winner.setScore(winner.getScore() + 1);
                    makeAllDrawersParticipants();
                    winner.setRole(Role.DRAWER);

                    currentWord = pickRandomWord();
                    startNewRoundAndBroadcast(
                        winner,
                        winner.getName() + "님 정답! [" + text + "]"
                    );
                }
            }
        }
    }

    /* --------------------------------- 권한/제시어 --------------------------------- */

    /** 현재 사용자가 출제자인가? */
    public boolean canDraw(Principal principal) {
        if (principal == null) return false;
        return userRepo.findByName(principal.getName())
            .map(u -> u.getRole() == Role.DRAWER)
            .orElse(false);
    }

    /** (출제자) 제시어 다시 받기 + 라운드 초기화. */
    @Transactional
    public void rerollWord(Principal p) {
        if (p == null) return;
        User me = userRepo.findByName(p.getName()).orElse(null);
        if (me == null || me.getRole() != Role.DRAWER) return;

        synchronized (lock) {
            currentWord = pickRandomWordDifferentFrom(currentWord);
            startNewRoundAndBroadcast(me, me.getName() + "님이 제시어를 다시 받았습니다.");
        }
    }

    /** 일반 유저: ‘내가 그리기’(최근 드로잉 없을 때만 출제자 권한 가져가기). */
    @Transactional
    public void setMeAsDrawer(Principal p) {
        if (p == null) return;
        User me = userRepo.findByName(p.getName()).orElse(null);
        if (me == null) return;

        // 쿨타임 검사
        long elapsed = System.currentTimeMillis() - lastDrawAtMs;
        if (elapsed < DRAW_COOLDOWN_MS) {
            long remain = (DRAW_COOLDOWN_MS - elapsed) / 1000 + 1;
            throw new IllegalStateException("출제자가 그림을 그리는 중입니다. '내가 그리기'는 " + remain + "초 후에 가능합니다.");
        }

        synchronized (lock) {
            if (me.getRole() == Role.DRAWER) return;

            makeAllDrawersParticipants();
            me.setRole(Role.DRAWER);

            currentWord = pickRandomWord();
            startNewRoundAndBroadcast(me, me.getName() + "님이 출제자로 지정되었습니다.");
        }
    }

    /* --------------------------------- 참여자/랭킹/스냅샷 --------------------------------- */

    /**
     * 참여자 목록(온라인만) + 랭킹(오프라인 포함, 0점 제외)을 브로드캐스트.
     */
    private void publishUsersAndScoreboard() {
        // 참여자: 온라인만
        List<User> onlineUsers = online.isEmpty() ? List.of() : userRepo.findByNameIn(online);
        List<String> users = onlineUsers.stream()
            .map(u -> u.getName() + " (" + u.getRole() + ")")
            .toList();
        broker.convertAndSend("/topic/users", users);

        // 랭킹: 전체 유저(오프라인 포함), 0점 제외
        List<ScoreBoardEntry> ranking = userRepo.findAll().stream()
            .filter(u -> u.getScore() > 0)
            .sorted(Comparator.comparingInt(User::getScore).reversed())
            .map(u -> new ScoreBoardEntry(u.getName(), u.getTeam(), u.getScore()))
            .toList();
        broker.convertAndSend("/topic/scoreboard", ranking);
    }

    /**
     * 개인 큐로 현재 상태(참여자/랭킹/단어길이/제시어/캔버스)를 1회 전송.
     */
    public void sendSnapshotTo(String username) {
        List<User> onlineUsers = online.isEmpty() ? List.of() : userRepo.findByNameIn(online);

        // 참여자(온라인)
        List<String> users = onlineUsers.stream()
            .map(u -> u.getName() + " (" + u.getRole() + ")")
            .toList();
        broker.convertAndSendToUser(username, "/queue/users", users);

        // 랭킹(전체, 0점 제외)
        List<ScoreBoardEntry> ranking = userRepo.findAll().stream()
            .filter(u -> u.getScore() > 0)
            .sorted(Comparator.comparingInt(User::getScore).reversed())
            .map(u -> new ScoreBoardEntry(u.getName(), u.getTeam(), u.getScore()))
            .toList();
        broker.convertAndSendToUser(username, "/queue/scoreboard", ranking);

        // 단어 길이/제시어
        broker.convertAndSendToUser(username, "/queue/wordlen", computeWordLen(currentWord));
        userRepo.findByName(username).ifPresent(u -> {
            if (currentWord != null && (u.getRole() == Role.DRAWER || u.getRole() == Role.ADMIN)) {
                broker.convertAndSendToUser(username, "/queue/word", currentWord);
            }
        });

        // 캔버스 스냅샷
        sendCanvasSnapshotTo(username);
    }

    /* --------------------------------- 드로잉(액션/세그먼트) --------------------------------- */

    /** 스트로크 = DrawEvent 묶음(드래그 1회). */
    static class StrokeAction {
        final String id;
        final List<DrawEvent> segments = new ArrayList<>();
        StrokeAction(String id) { this.id = id; }
    }

    /** 서버가 유지하는 드로잉 히스토리. */
    private final List<StrokeAction> strokeActions = new ArrayList<>();

    /** 메모리 보호용 상한. */
    private static final int MAX_ACTIONS = 1_200;       // 최대 액션(드래그) 수
    private static final int MAX_TOTAL_SEGMENTS = 40_000; // 전체 세그먼트 수

    /** 현재까지 누적된 세그먼트 수. */
    private int totalSegments = 0;

    /**
     * 라운드/리롤/정답 전환 시 드로잉 상태 초기화(+옵션: 모든 클라 clear).
     */
    private void resetDrawingState(boolean broadcastClear) {
        synchronized (strokeActions) {
            strokeActions.clear();
            totalSegments = 0;
        }
        if (broadcastClear) broker.convertAndSend("/topic/canvas/clear", "");
    }

    /**
     * 드로잉 세그먼트 수신:
     * - actionId/newStroke 기준으로 서버 히스토리에 축적
     * - 메모리 상한을 넘기면 가장 오래된 액션부터 제거
     * - 즉시 브로드캐스트
     */
    public void addStroke(Principal p, DrawEvent e) {
        if (!canDraw(p)) return;

        if (e.getMode() == null || e.getMode().isBlank()) e.setMode("pen");
        if (e.getActionId() == null || e.getActionId().isBlank()) {
            e.setActionId(UUID.randomUUID().toString()); // 안전장치
            e.setNewStroke(Boolean.TRUE);
        }

        synchronized (strokeActions) {
            if (Boolean.TRUE.equals(e.getNewStroke())
                || strokeActions.isEmpty()
                || !strokeActions.get(strokeActions.size() - 1).id.equals(e.getActionId())) {
                // 새 액션 시작
                StrokeAction a = new StrokeAction(e.getActionId());
                a.segments.add(e);
                strokeActions.add(a);
                totalSegments++;
            } else {
                // 직전 액션에 이어붙이기
                strokeActions.get(strokeActions.size() - 1).segments.add(e);
                totalSegments++;
            }

            // 상한 초과 시 앞에서 제거
            while (strokeActions.size() > MAX_ACTIONS || totalSegments > MAX_TOTAL_SEGMENTS) {
                StrokeAction old = strokeActions.remove(0);
                totalSegments -= old.segments.size();
            }
        }

        // 실시간 방송
        broker.convertAndSend("/topic/draw", e);
        lastDrawAtMs = System.currentTimeMillis(); // 최근 드로잉 시각 갱신
    }

    /**
     * 실행취소:
     * - 서버 히스토리에서 마지막 액션 제거
     * - 전체 리플레이 대신 {actionId}만 방송 → 클라가 로컬 히스토리로 재생
     */
    public void undoLastStroke(Principal p) {
        if (!canDraw(p)) return;
        String removedId;
        synchronized (strokeActions) {
            if (strokeActions.isEmpty()) return;
            StrokeAction removed = strokeActions.remove(strokeActions.size() - 1);
            removedId = removed.id;
            totalSegments -= removed.segments.size();
        }
        broker.convertAndSend("/topic/undo", Map.of("actionId", removedId));
    }

    /** 전체 지우기(히스토리/버퍼도 초기화). */
    public void clearCanvas(Principal p) {
        if (!canDraw(p)) return;
        resetDrawingState(true);
    }

    /**
     * 새로 입장한 사용자에게 현재 캔버스 스냅샷 전송:
     * - 개인 큐로 clear → 서버 히스토리 전체를 순서대로 재생
     */
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

    /* --------------------------------- 유틸/헬퍼 --------------------------------- */

    /** 현재 제시어 글자 수(공백 제거, 코드포인트 기준). */
    private int computeWordLen(String word) {
        if (word == null) return 0;
        String cleaned = word.replaceAll("\\s+", "");
        return cleaned.codePointCount(0, cleaned.length());
    }

    /** 모든 참여자에게 제시어 글자 수 브로드캐스트. */
    private void publishWordLen() {
        broker.convertAndSend("/topic/wordlen", computeWordLen(currentWord));
    }

    /** 단어에서 글자 수만큼 '☆'로 마스킹. */
    private String maskWord(String word) {
        int n = Math.max(1, computeWordLen(word));
        return "☆".repeat(n);
    }

    /** 랜덤 제시어. */
    private String pickRandomWord() {
        List<Word> all = wordRepo.findAll();
        if (all.isEmpty()) throw new RuntimeException("단어 DB가 비었습니다");
        return all.get(ThreadLocalRandom.current().nextInt(all.size())).getText();
    }

    /** 이전과 다른 랜덤 제시어. */
    private String pickRandomWordDifferentFrom(String prev) {
        List<Word> all = wordRepo.findAll();
        if (all.isEmpty()) throw new RuntimeException("단어 DB가 비었습니다");
        if (all.size() == 1) return all.get(0).getText();
        String next;
        do {
            next = all.get(ThreadLocalRandom.current().nextInt(all.size())).getText();
        } while (Objects.equals(next, prev));
        return next;
    }

    /** 시스템/일반 채팅 브로드캐스트. */
    private void publishChat(String from, String text, boolean system) {
        broker.convertAndSend("/topic/chat", Map.of("from", from, "text", text, "system", system));
    }

    /** (트랜잭션 내) 모든 출제자를 참여자로 되돌림. */
    private void makeAllDrawersParticipants() {
        userRepo.findAll().forEach(u -> {
            if (u.getRole() == Role.DRAWER) u.setRole(Role.PARTICIPANT);
        });
    }

    /**
     * 새 라운드 공통 처리:
     * - 캔버스/히스토리 초기화(clear 방송 포함)
     * - 출제자/관리자에게 제시어 전송
     * - 글자 수/참여자/랭킹 방송
     * - 선택적 시스템 메시지 방송
     */
    private void startNewRoundAndBroadcast(User drawer, String systemMsg) {
        resetDrawingState(true);

        // 출제자 + 관리자에게 제시어
        broker.convertAndSendToUser(drawer.getName(), "/queue/word", currentWord);
        userRepo.findByName(ADMIN_NAME).ifPresent(admin ->
            broker.convertAndSendToUser(admin.getName(), "/queue/word", currentWord));

        if (systemMsg != null && !systemMsg.isBlank()) {
            publishChat("SYSTEM", systemMsg, true);
        }

        publishUsersAndScoreboard();
        publishWordLen();
    }
}
