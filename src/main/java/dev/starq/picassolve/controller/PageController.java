package dev.starq.picassolve.controller;

import dev.starq.picassolve.dto.Request.UserCreateRequest;

import dev.starq.picassolve.dto.UserDto;
import dev.starq.picassolve.service.UserService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
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
            Model model) {
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
            RedirectAttributes redirectAttributes) {
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

    @GetMapping("/account/password")
    public String passwordForm(Model model, Authentication authentication) {
        model.addAttribute("username", authentication != null ? authentication.getName() : "");
        return "password";
    }

    @PostMapping("/account/password")
    public String changePassword(
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("username", authentication.getName());
            model.addAttribute("errorMessage", "새 비밀번호와 확인 비밀번호가 일치하지 않습니다.");
            return "password";
        }

        try {
            userService.changePassword(authentication.getName(), currentPassword, newPassword);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("username", authentication.getName());
            model.addAttribute("errorMessage", ex.getMessage());
            return "password";
        }

        redirectAttributes.addFlashAttribute("successMessage", "비밀번호가 변경되었습니다.");
        return "redirect:/account/password";
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

    @GetMapping("/api/me")
    @ResponseBody
    public ResponseEntity<UserDto> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserDto user = userService.findByName(authentication.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(user);
    }

    @PutMapping("/api/user")
    @ResponseBody
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, String> payload,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String currentName = authentication.getName();
        String newName = payload.get("name");
        String password = payload.get("password");

        Integer team = null;
        if (payload.containsKey("team")) {
            try {
                team = Integer.parseInt(payload.get("team"));
            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest().body(Map.of("message", "유효하지 않은 팀 번호입니다."));
            }
        }

        try {
            UserDto updated = userService.updateProfile(currentName, newName, team, password);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/api/user")
    @ResponseBody
    public ResponseEntity<?> deleteAccount(@RequestBody Map<String, String> payload,
            Authentication authentication,
            jakarta.servlet.http.HttpServletRequest request) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String password = payload.get("password");

        try {
            userService.deleteUser(authentication.getName(), password);
            request.getSession().invalidate(); // Logout
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/api/password")
    @ResponseBody
    public ResponseEntity<?> changePasswordApi(@RequestBody Map<String, String> payload,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String currentPassword = payload.get("currentPassword");
        String newPassword = payload.get("newPassword");

        try {
            userService.changePassword(authentication.getName(), currentPassword, newPassword);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

}
