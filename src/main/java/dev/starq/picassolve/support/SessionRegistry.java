package dev.starq.picassolve.support;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class SessionRegistry {

	private final SimpMessagingTemplate broker; // 선택: 강제 로그아웃 알림용

	// username -> HttpSession
	private final Map<String, HttpSession> map = new ConcurrentHashMap<>();

	/** 같은 유저의 이전 세션을 찾아 강제 종료하고, 새 세션을 바인딩 */
	public synchronized void kickAndBind(String username, HttpSession newSession) {
		HttpSession old = map.get(username);
		if (old != null && !old.getId().equals(newSession.getId())) {
			// (선택) 이전/새 세션 모두에게 알림을 보내지만,
			// 클라이언트에서 SESSION_ID 비교로 "나만" 로그아웃하게 할 것
			try {
				broker.convertAndSendToUser(username,
					"/queue/force-logout",
					Map.of("allowSid", newSession.getId()));
			} catch (Exception ignore) {}

			try { old.invalidate(); } catch (IllegalStateException ignore) {}
		}
		map.put(username, newSession);
		newSession.setAttribute("LOGIN_OWNER", username); // 역참조용 표시
	}

	/** 세션이 정상 로그아웃될 때 레지스트리에서 정리 */
	public synchronized void unbind(HttpSession session) {
		Object owner = session.getAttribute("LOGIN_OWNER");
		if (owner == null) return;
		String username = owner.toString();
		HttpSession cur = map.get(username);
		if (cur != null && cur.getId().equals(session.getId())) {
			map.remove(username);
		}
	}
}