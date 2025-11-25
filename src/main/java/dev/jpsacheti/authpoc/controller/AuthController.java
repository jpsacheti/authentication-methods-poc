package dev.jpsacheti.authpoc.controller;

import dev.jpsacheti.authpoc.dto.AuthDtos;
import dev.jpsacheti.authpoc.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<AuthDtos.AuthResponse> register(
            @RequestBody AuthDtos.RegisterRequest request) {
        return ResponseEntity.ok(userService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.AuthResponse> login(
            @RequestBody AuthDtos.LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }
}
