package dev.starq.picassolve.config;

import java.security.Principal;                            // 사용자 식별 인터페이스
import java.util.Map;
import org.springframework.messaging.Message;               // STOMP 메시지
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor; // STOMP 헤더 접근
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

public class UserPrincipalChannelInterceptor implements ChannelInterceptor {

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		// STOMP 헤더 꺼내기
		StompHeaderAccessor accessor =
			MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

		if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
			// 이미 유저가 있으면 그대로(재연결 등)
			Principal current = accessor.getUser();
			if (current == null) {
				// HTTP 세션에서 우리가 저장한 이름 꺼냄 (PageController에서 setAttribute("name", name))
				Map<String, Object> attrs = accessor.getSessionAttributes();
				if (attrs != null) {
					Object n = attrs.get("name");
					if (n instanceof String s && !s.isBlank()) {
						// 심플 Principal 구현으로 주입
						accessor.setUser(() -> s);
					}
				}
			}
		}
		return message;
	}
}
