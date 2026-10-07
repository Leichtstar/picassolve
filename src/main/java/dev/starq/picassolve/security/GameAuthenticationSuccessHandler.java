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

        // Presence is WS-scoped; only capacity gate here so full rooms fail at login.
        if (!gameService.hasCapacityFor(username)) {
            SecurityContextHolder.clearContext();
            session.invalidate();
            response.sendRedirect("/login?error=capacity");
            return;
        }

        // Ensure SYSTEM account keeps ADMIN role on HTTP login (without joining online yet).
        gameService.ensureLoginRoles(username);

        session.setAttribute("name", username);
        sessionRegistry.kickAndBind(username, session);

        String requestedWith = request.getHeader("X-Requested-With");
        String accept = request.getHeader("Accept");

        if ("XMLHttpRequest".equals(requestedWith) || (accept != null && accept.contains("application/json"))) {
            response.setStatus(HttpServletResponse.SC_OK);
        } else {
            response.sendRedirect("/game");
        }
    }
}
