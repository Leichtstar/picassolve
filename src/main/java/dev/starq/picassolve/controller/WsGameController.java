package dev.starq.picassolve.controller;

import dev.starq.picassolve.dto.ChatMessage;
import dev.starq.picassolve.dto.DrawEvent;
import dev.starq.picassolve.dto.SetDrawerRequest;
import dev.starq.picassolve.service.GameService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WsGameController {

    private final GameService gameService; // 서비스로 위임만 담당

    /** 채팅 전송: 보낸 사람은 Principal 우선, 없으면 DTO의 from 사용 */
    @MessageMapping("/chat.send")
    public void onChat(@Payload ChatMessage msg, Principal p) {
        final String sender = (p != null) ? p.getName()
            : (msg != null && msg.getFrom() != null ? msg.getFrom() : "guest");
        final String text = (msg != null && msg.getText() != null) ? msg.getText() : "";
        gameService.handleChat(sender, text);
    }

    /** 드로잉 스트로크 수신: 서비스가 권한(DRAWER) 체크 */
    @MessageMapping("/draw.stroke")
    public void onDraw(@Payload DrawEvent e, Principal p) {
        if (e == null) return;                 // 널 방어
        gameService.addStroke(p, e);           // 내부에서 actionId/newStroke 보정
    }

    /** 실행취소: DRAWER만 동작(서비스에서 검증) */
    @MessageMapping("/draw.undo")
    public void onUndo(Principal p) {
        gameService.undoLastStroke(p);         // 서버는 actionId만 방송(/topic/undo)
    }

    /** 전체 지우기: DRAWER만 동작(서비스에서 검증) */
    @MessageMapping("/canvas.clear")
    public void onClear(Principal p) {
        gameService.clearCanvas(p);            // /topic/canvas/clear 브로드캐스트
    }

    /** 관리자: 특정 유저를 출제자로 지정 */
    @MessageMapping("/admin.setDrawer")
    public void onSetDrawer(@Payload SetDrawerRequest req, Principal p) {
        if (p == null || req == null || req.getName() == null || req.getName().isBlank()) return;
        gameService.setDrawerByAdmin(p.getName(), req.getName());
    }

    /** 클라이언트 연결 직후 상태 동기화(유저/랭킹/제시어/캔버스 스냅샷) */
    @MessageMapping("/state.sync")
    public void onStateSync(Principal p) {
        if (p != null) gameService.sendSnapshotTo(p.getName());
    }

    /** (출제자) 제시어 다시받기 */
    @MessageMapping("/word.reroll")
    public void rerollWord(Principal p) {
        gameService.rerollWord(p);
    }

    /** (참여자) 내가 그리기 — 본인을 출제자로 */
    @MessageMapping("/drawer.me")
    public void meDrawer(Principal p) {
        gameService.setMeAsDrawer(p);
    }

    /** 예외가 나도 세션 끊기지 않게: 에러 메시지를 개인 큐로 전달 */
    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public String handleException(Throwable ex) {
        log.warn("WS handler error: {}", ex.toString());
        return ex.getMessage() != null ? ex.getMessage() : "Unexpected error";
    }
}
