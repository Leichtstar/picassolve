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

@Slf4j
@Controller
@RequiredArgsConstructor
public class WsGameController {

    private final GameService gameService;

    @MessageMapping("/chat.send")
    public void onChat(@Payload ChatMessage msg, Principal p) {
        if (p == null) {
            log.warn("[웹소켓] 인증되지 않은 채팅 시도 거부");
            return;
        }
        final String text = (msg != null && msg.getText() != null) ? msg.getText() : "";
        log.trace("[웹소켓] 채팅 메시지 수신 - 보낸이: {}, 내용: {}", p.getName(), text);
        gameService.handleChat(p.getName(), text);
    }

    @MessageMapping("/word.reroll")
    public void onReroll(Principal p) {
        if (p == null) return;
        log.info("[웹소켓] 제시어 다시받기 요청: {}", p.getName());
        gameService.rerollWord(p);
    }

    @MessageMapping("/draw.stroke")
    public void onDraw(@Payload DrawEvent event, Principal p) {
        if (p == null) return;
        gameService.addStroke(p, event);
    }

    @MessageMapping("/draw.undo")
    public void onUndo(Principal p) {
        if (p == null) return;
        gameService.undoLastStroke(p);
    }

    @MessageMapping("/canvas.clear")
    public void onClear(Principal p) {
        if (p == null) return;
        gameService.clearCanvas(p);
    }

    @MessageMapping("/drawer.me")
    public void onSetMeAsDrawer(Principal p) {
        if (p == null) return;
        log.info("[웹소켓] 내가 그리기 요청: {}", p.getName());
        gameService.setMeAsDrawer(p);
    }

    @MessageMapping("/admin.setDrawer")
    public void onSetDrawer(@Payload SetDrawerRequest req, Principal p) {
        if (p == null || req == null || req.getName() == null || req.getName().isBlank())
            return;
        log.info("[웹소켓] 관리자 요청 - 출제자 지정: {} -> {}", p.getName(), req.getName());
        gameService.setDrawerByAdmin(p.getName(), req.getName());
    }

    @MessageMapping("/state.sync")
    public void onStateSync(Principal p) {
        if (p == null) return;
        log.debug("[웹소켓] 상태 동기화 요청: {}", p.getName());
        gameService.sendSnapshotTo(p.getName());
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public String handleException(Throwable ex) {
        log.error("[웹소켓] 예외 발생: {}", ex.toString(), ex);
        return ex.getMessage() != null ? ex.getMessage() : "Unexpected error";
    }
}
