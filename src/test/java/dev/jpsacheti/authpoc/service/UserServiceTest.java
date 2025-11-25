package dev.jpsacheti.authpoc.service;

import dev.jpsacheti.authpoc.dto.AuthDtos;
import dev.jpsacheti.authpoc.model.User;
import dev.jpsacheti.authpoc.repository.UserRepository;
import dev.jpsacheti.authpoc.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private UserService userService;

    @Test
    void register_ShouldCreateUserAndReturnToken() {
        AuthDtos.RegisterRequest request = new AuthDtos.RegisterRequest("user", "pass", "User");
        User user = User.builder().username("user").build();

        when(passwordEncoder.encode("pass")).thenReturn("encodedPass");
        when(jwtService.generateToken(any(User.class))).thenReturn("token");

        AuthDtos.AuthResponse response = userService.register(request);

        assertThat(response.getToken()).isEqualTo("token");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void login_ShouldAuthenticateAndReturnToken() {
        AuthDtos.LoginRequest request = new AuthDtos.LoginRequest("user", "pass");
        User user = User.builder().username("user").build();

        when(userRepository.findByUsername("user")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("token");

        AuthDtos.AuthResponse response = userService.login(request);

        assertThat(response.getToken()).isEqualTo("token");
        verify(authenticationManager).authenticate(any());
    }
}
