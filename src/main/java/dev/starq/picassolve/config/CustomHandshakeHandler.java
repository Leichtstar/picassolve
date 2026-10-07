package dev.starq.picassolve.config;

import java.security.Principal;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeFailureException;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * HTTP 세션의 name만 STOMP Principal로 사용한다. 세션이 없으면 핸드셰이크 거부.
 */
public class CustomHandshakeHandler extends DefaultHandshakeHandler {
    @Override
    protected Principal determineUser(@NonNull ServerHttpRequest request,
                                      @NonNull WebSocketHandler wsHandler,
                                      @NonNull Map<String, Object> attributes) {
        Object name = attributes.get("name");
        if (!(name instanceof String s) || s.isBlank()) {
            throw new HandshakeFailureException("Login required for WebSocket connection");
        }
        return () -> s;
    }
}
