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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GameAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final GameService gameService;
    private final SessionRegistry sessionRegistry;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        String username = authentication.getName();
        HttpSession session = request.getSession(true);

        if (!gameService.login(username)) {
            SecurityContextHolder.clearContext();
            session.invalidate();
            response.sendRedirect("/login?error=capacity");
            return;
        }

        session.setAttribute("name", username);
        sessionRegistry.kickAndBind(username, session);

        // AJAX(React 등) 요청인지 브라우저 직접 요청인지 확인합니다.
        String requestedWith = request.getHeader("X-Requested-With");
        String accept = request.getHeader("Accept");

        if ("XMLHttpRequest".equals(requestedWith) || (accept != null && accept.contains("application/json"))) {
            // API 요청인 경우 성공 상태코드만 반환 (React 프론트엔드 대응)
            response.setStatus(HttpServletResponse.SC_OK);
        } else {
            // 브라우저 폼 제출 등 정적 HTML 환경인 경우 /game으로 리다이렉트
            response.sendRedirect("/game");
        }
    }
}
