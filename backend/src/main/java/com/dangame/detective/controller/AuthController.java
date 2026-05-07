package com.dangame.detective.controller;

import com.dangame.detective.ApiException;
import com.dangame.detective.dto.Dtos.AuthResponse;
import com.dangame.detective.dto.Dtos.Login;
import com.dangame.detective.dto.Dtos.Register;
import com.dangame.detective.entity.UserEntity;
import com.dangame.detective.mapper.UserMapper;
import com.dangame.detective.repo.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.util.HexFormat;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final BCryptPasswordEncoder encoder;
    private final UserMapper userMapper;

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody Register req) {
        if (users.findByEmail(req.email()).isPresent()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Email уже зарегистрирован");
        }
        UserEntity user = new UserEntity();
        user.setEmail(req.email());
        user.setPasswordHash(encoder.encode(req.password()));
        user.setAgentName(req.agentName());
        user.setToken(newToken());
        user.setRank("Стажёр");
        user.setCasesSolved(0);

        users.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(userMapper.toAuthResponse(user));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody Login req) {
        UserEntity user = users.findByEmail(req.email())
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Неверный email или пароль"));
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Неверный email или пароль");
        }
        return userMapper.toAuthResponse(user);
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
