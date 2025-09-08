package dev.starq.picassolve.controller;

import dev.starq.picassolve.service.GameService;
import dev.starq.picassolve.support.SessionRegistry;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class PageController {

    private final GameService gameService;
    private final SessionRegistry sessionRegistry;

    @GetMapping("/")                           // 1) GET /
    public String root(HttpSession session) {
        String name = (String) session.getAttribute("name"); // 세션에 이름이 있으면 로그인된 상태
        return (name == null) ? "redirect:/login"            // 로그인 안 됨 → /login으로
            : "redirect:/game";            // 로그인 됨 → /game으로
    }

    @GetMapping("/login")
    public String loginPage() { return "login"; }

    @PostMapping("/login")
    public String doLogin(@RequestParam String name,
        HttpSession session,
        Model model) {
        if (!gameService.login(name)) {
            model.addAttribute("error", "허용되지 않은 이름이거나 접속 인원 초과");
            return "login";
        }
        session.setAttribute("name", name);

        // ★ 같은 이름으로 로그인한 이전 세션이 있으면 끊고 이 세션을 등록
        sessionRegistry.kickAndBind(name, session);

        return "redirect:/game";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        String name = (String) session.getAttribute("name");
        if (name != null) gameService.logout(name);
        sessionRegistry.unbind(session);  // ★ 레지스트리 정리
        session.invalidate();
        return "redirect:/login";
    }


    @GetMapping("/game")
    public String game(HttpSession session, Model model) {
        String name = (String) session.getAttribute("name");
        if (name == null) return "redirect:/login";
        model.addAttribute("name", name);
        model.addAttribute("sid", session.getId()); // ★ 클라에서 세션 비교용
        return "game";
    }
}
