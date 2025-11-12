package dev.starq.picassolve.controller;

import dev.starq.picassolve.dto.Request.UserCreateRequest;
import dev.starq.picassolve.service.UserService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class PageController {

    private final UserService userService;

    @GetMapping("/")
    public String root(Authentication authentication) {
        return (authentication == null || !authentication.isAuthenticated())
            ? "redirect:/login"
            : "redirect:/game";
    }

    @GetMapping("/login")
    public String loginPage(
        @RequestParam(value = "error", required = false) String error,
        @RequestParam(value = "logout", required = false) String logout,
        @RequestParam(value = "registered", required = false) String registered,
        Model model
    ) {
        if ("capacity".equals(error)) {
            model.addAttribute("errorMessage", "동시에 접속할 수 있는 인원을 초과했습니다.");
        } else if (error != null) {
            model.addAttribute("errorMessage", "이름 또는 비밀번호가 올바르지 않습니다.");
        } else {
            model.addAttribute("errorMessage", null);
        }
        model.addAttribute("logoutMessage", logout != null ? "정상적으로 로그아웃되었습니다." : null);
        model.addAttribute("registeredMessage", registered != null ? "회원가입이 완료되었습니다. 로그인해 주세요." : null);
        return "login";
    }

    @GetMapping("/register")
    public String registerForm() {
        return "register";
    }

    @PostMapping("/register")
    public String register(
        @RequestParam String name,
        @RequestParam String password,
        @RequestParam int team,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        try {
            userService.create(new UserCreateRequest(name, password, team));
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("prefillName", name);
            model.addAttribute("prefillTeam", team);
            return "register";
        }
        redirectAttributes.addAttribute("registered", "true");
        return "redirect:/login";
    }

    @GetMapping("/game")
    public String game(HttpSession session, Model model) {
        String name = (String) session.getAttribute("name");
        if (name == null) {
            return "redirect:/login";
        }
        model.addAttribute("name", name);
        model.addAttribute("sid", session.getId());
        return "game";
    }
}
