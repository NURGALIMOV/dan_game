package com.dangame.detective.service;

import com.dangame.detective.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Отправка писем. Шлёт асинхронно — регистрация не блокируется на ожидании SMTP.
 * При недоступном SMTP логируем и идём дальше: пользователь может попросить переотправку.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.public-url}")
    private String publicUrl;

    @Async
    public void sendVerification(UserEntity user) {
        if (user.getVerificationToken() == null) {
            log.warn("Не отправляем письмо: у user {} нет verification_token", user.getId());
            return;
        }
        String link = publicUrl + "/verify?token=" + user.getVerificationToken();

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(user.getEmail());
        msg.setSubject("АГЕНТСТВО — подтверждение почты");
        msg.setText(
            "Агент " + user.getAgentName() + ",\n\n" +
            "Вы зарегистрировались в Агентстве. Чтобы получить полный доступ\n" +
            "(в том числе возможность открывать платные дела) — подтвердите\n" +
            "электронную почту по ссылке:\n\n" +
            link + "\n\n" +
            "Если регистрировались не вы — просто проигнорируйте это письмо.\n\n" +
            "— Агентство"
        );

        try {
            mailSender.send(msg);
            log.info("Письмо подтверждения отправлено: user_id={} email={}", user.getId(), user.getEmail());
        } catch (Exception e) {
            log.error("Не удалось отправить письмо user_id={} email={}: {}",
                user.getId(), user.getEmail(), e.getMessage());
        }
    }
}
