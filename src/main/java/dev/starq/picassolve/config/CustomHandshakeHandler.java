package dev.starq.picassolve.config;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * HTTP 세션에 저장된 name을 STOMP Principal로 연결해주는 핸들러.
 * 없으면 임의 이름을 부여하지만, 서버 권한 체크는 세션 이름으로 작동하므로
 * 정상 경로(로그인 후)로 접속해야 의미가 있습니다.
 */
public class CustomHandshakeHandler extends DefaultHandshakeHandler {
    @Override
    protected Principal determineUser(@NonNull ServerHttpRequest request,
                                      @NonNull WebSocketHandler wsHandler,
                                      @NonNull Map<String, Object> attributes) {
        Object name = attributes.get("name");
        String username = (name instanceof String s && !s.isBlank()) ? s : "anon-" + UUID.randomUUID();
        return () -> username;
    }
}
