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
        Authentication authentication
    ) throws IOException, ServletException {
        if (authentication != null) {
            gameService.logout(authentication.getName());
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            sessionRegistry.unbind(session);
        }
        response.sendRedirect("/login?logout");
    }
}
