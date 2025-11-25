package dev.jpsacheti.authpoc.service;

import dev.jpsacheti.authpoc.dto.AuthDtos;
import dev.jpsacheti.authpoc.model.User;
import dev.jpsacheti.authpoc.repository.UserRepository;
import dev.jpsacheti.authpoc.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtService jwtService;
        private final AuthenticationManager authenticationManager;

        @Transactional
        public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
                var user = User.builder()
                                .username(request.getUsername())
                                .passwordHash(passwordEncoder.encode(request.getPassword()))
                                .displayName(request.getDisplayName())
                                .build();
                userRepository.save(user);
                var jwtToken = jwtService.generateToken(user);
                return AuthDtos.AuthResponse.builder()
                                .token(jwtToken)
                                .build();
        }

        public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
                authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(
                                                request.getUsername(),
                                                request.getPassword()));
                var user = userRepository.findByUsername(request.getUsername())
                                .orElseThrow();
                var jwtToken = jwtService.generateToken(user);
                return AuthDtos.AuthResponse.builder()
                                .token(jwtToken)
                                .build();
        }
}
