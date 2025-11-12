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
        Authentication authentication
    ) throws IOException, ServletException {
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
        response.sendRedirect("/game");
    }
}
