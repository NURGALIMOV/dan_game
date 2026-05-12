package com.dangame.detective.controller;

import com.dangame.detective.ApiException;
import com.dangame.detective.entity.UserEntity;
import com.dangame.detective.repo.UserRepository;
import com.dangame.detective.security.CurrentUser;
import com.dangame.detective.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class VerificationController {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final EmailService emailService;

    /**
     * Подтверждение почты по токену из письма.
     * Эндпоинт публичный (без auth) — токен в ссылке и есть аутентификация.
     */
    @GetMapping("/verify-email")
    @Transactional
    public Map<String, Object> verify(@RequestParam("token") String token) {
        UserEntity user = users.findByVerificationToken(token)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ссылка недействительна или уже использована."));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return Map.of("success", true, "message", "Почта уже подтверждена.");
        }
        user.setEmailVerified(true);
        user.setVerificationToken(null);
        return Map.of("success", true, "message", "Почта подтверждена. Теперь доступна оплата.");
    }

    /**
     * Переотправка письма. Защита: не чаще раза в 60 секунд.
     */
    @PostMapping("/resend-verification")
    @Transactional
    public Map<String, Object> resend(@CurrentUser UserEntity user) {
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return Map.of("success", true, "message", "Почта уже подтверждена.");
        }
        if (user.getVerificationSentAt() != null
            && user.getVerificationSentAt().isAfter(LocalDateTime.now().minusSeconds(60))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                "Письмо уже отправлено. Попробуй ещё раз через минуту.");
        }
        user.setVerificationToken(newToken());
        user.setVerificationSentAt(LocalDateTime.now());
        emailService.sendVerification(user);
        return Map.of("success", true, "message", "Письмо отправлено повторно.");
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
