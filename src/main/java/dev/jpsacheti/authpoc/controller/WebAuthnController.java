package dev.jpsacheti.authpoc.controller;

import dev.jpsacheti.authpoc.service.WebAuthnService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webauthn")
@RequiredArgsConstructor
public class WebAuthnController {

    private final WebAuthnService webAuthnService;

    @PostMapping("/register/start")
    public ResponseEntity<String> startRegistration(@RequestParam String username) {
        return ResponseEntity.ok(webAuthnService.startRegistration(username));
    }

    @PostMapping("/register/finish")
    public ResponseEntity<Void> finishRegistration(
            @RequestParam String username,
            @RequestBody String credentialJson) {
        webAuthnService.finishRegistration(username, credentialJson);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login/start")
    public ResponseEntity<String> startLogin(@RequestParam String username) {
        return ResponseEntity.ok(webAuthnService.startLogin(username));
    }

    @PostMapping("/login/finish")
    public ResponseEntity<AuthDtos.AuthResponse> finishLogin(
            @RequestParam String username,
            @RequestBody String credentialJson) {
        return ResponseEntity.ok(webAuthnService.finishLogin(username, credentialJson));
    }
}
