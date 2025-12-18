package dev.starq.picassolve.security;

import dev.starq.picassolve.service.GameService;
import dev.starq.picassolve.support.SessionRegistry;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GameLogoutSuccessHandler implements LogoutSuccessHandler {

    private final GameService gameService;
    private final SessionRegistry sessionRegistry;

    @Override
    public void onLogoutSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        if (authentication != null) {
            gameService.logout(authentication.getName());
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            sessionRegistry.unbind(session);
            session.invalidate(); // Ensure session is invalidated
        }

        // Explicitly delete JSESSIONID cookie
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("JSESSIONID", null);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        response.addCookie(cookie);

        // AJAX(React 등) 요청인지 브라우저 직접 요청인지 확인합니다.
        String requestedWith = request.getHeader("X-Requested-With");
        String accept = request.getHeader("Accept");

        if ("XMLHttpRequest".equals(requestedWith) || (accept != null && accept.contains("application/json"))) {
            // API 요청인 경우 성공 상태코드와 메시지 반환 (React 프론트엔드 대응)
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Logged out successfully\"}");
        } else {
            // 브라우저 폼 제출 등 정적 HTML 환경인 경우 /login으로 리다이렉트
            response.sendRedirect("/login?logout");
        }
    }
}
