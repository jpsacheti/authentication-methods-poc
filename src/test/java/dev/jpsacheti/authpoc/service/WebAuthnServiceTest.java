package dev.jpsacheti.authpoc.service;

import dev.jpsacheti.authpoc.dto.AuthDtos;
import dev.jpsacheti.authpoc.model.User;
import dev.jpsacheti.authpoc.model.WebAuthnChallenge;
import dev.jpsacheti.authpoc.model.WebAuthnCredential;
import dev.jpsacheti.authpoc.repository.UserRepository;
import dev.jpsacheti.authpoc.repository.WebAuthnCredentialRepository;
import dev.jpsacheti.authpoc.repository.WebAuthnChallengeRepository;
import dev.jpsacheti.authpoc.security.JwtService;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebAuthnServiceTest {

    @Mock
    private WebAuthnClient webAuthnClient;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WebAuthnCredentialRepository credentialRepository;
    @Mock
    private WebAuthnChallengeRepository challengeRepository;

    private WebAuthnService webAuthnService;

    private static class JwtServiceStub extends JwtService {
        @Override
        public String generateToken(org.springframework.security.core.userdetails.UserDetails userDetails) {
            return "token123";
        }
    }

    @org.junit.jupiter.api.BeforeEach
    void setupService() {
        JwtService jwtService = new JwtServiceStub();
        webAuthnService = new WebAuthnService(webAuthnClient, userRepository, credentialRepository, challengeRepository, jwtService);
    }

    // === Registration Tests ===

    @Test
    void startRegistration_WithExistingUser_ShouldReturnJsonOptions() throws IOException {
        String username = "existinguser";
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).username(username).displayName("Existing User").build();

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        when(webAuthnClient.startRegistrationJson(any(), any())).thenReturn("{\"challenge\":\"abc123\"}");

        String result = webAuthnService.startRegistration(username, "platform");

        assertThat(result).isEqualTo("{\"challenge\":\"abc123\"}");
        verify(userRepository).findByUsername(username);
        verify(userRepository, never()).save(any(User.class));
        verify(challengeRepository).deleteByUsernameAndType(username, "REG");
        verify(challengeRepository).save(any(WebAuthnChallenge.class));
    }

    @Test
    void startRegistration_WithNewUser_ShouldCreateUserAndReturnOptions() throws IOException {
        String username = "newuser";
        User newUser = User.builder().id(UUID.randomUUID()).username(username).displayName(username).build();

        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(newUser);

        when(webAuthnClient.startRegistrationJson(any(), any())).thenReturn("{\"challenge\":\"xyz789\"}");

        String result = webAuthnService.startRegistration(username, null);

        assertThat(result).isEqualTo("{\"challenge\":\"xyz789\"}");
        verify(userRepository).findByUsername(username);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getUsername()).isEqualTo(username);
        assertThat(userCaptor.getValue().getDisplayName()).isEqualTo(username);
    }

    @Test
    void startRegistration_WhenSerializationFails_ShouldThrowRuntimeException() throws IOException {
        String username = "user";
        User user = User.builder().id(UUID.randomUUID()).username(username).displayName(username).build();
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        when(webAuthnClient.startRegistrationJson(any(), any())).thenThrow(new IOException("boom"));

        assertThatThrownBy(() -> webAuthnService.startRegistration(username, "platform"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to serialize options");
    }

    @Test
    void startRegistration_WithCrossPlatformAttachment_ShouldCallRelyingParty() throws IOException {
        String username = "user";
        User user = User.builder().id(UUID.randomUUID()).username(username).displayName(username).build();
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        when(webAuthnClient.startRegistrationJson(any(), any())).thenReturn("{}");

        String result = webAuthnService.startRegistration(username, "cross-platform");

        assertThat(result).isEqualTo("{}");
        verify(webAuthnClient).startRegistrationJson(any(), any());
    }

    @Test
    void finishRegistration_Success_ShouldSaveCredentialAndDeleteChallenge() throws Exception {
        String username = "user";
        String credentialJson = "{\"id\":\"cred\"}";

        // Mock challenge
        WebAuthnChallenge challenge = WebAuthnChallenge.builder()
                .username(username).type("REG").requestJson("{\"rp\":\"x\"}").build();
        when(challengeRepository.findTopByUsernameAndTypeOrderByCreatedAtDesc(username, "REG"))
                .thenReturn(Optional.of(challenge));

        WebAuthnClient.RegistrationOutcome regOutcome = new WebAuthnClient.RegistrationOutcome(
                new byte[]{1,2,3}, new byte[]{4,5}, 42L
        );
        when(webAuthnClient.finishRegistrationFromJson(any(String.class), any(String.class))).thenReturn(regOutcome);

        // Mock user lookup
        User user = User.builder().id(UUID.randomUUID()).username(username).displayName(username).build();
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        webAuthnService.finishRegistration(username, credentialJson);

        verify(credentialRepository).save(any(WebAuthnCredential.class));
        verify(challengeRepository).deleteByUsernameAndType(username, "REG");
    }

    @Test
    void finishRegistration_WhenRelyingPartyFails_ShouldThrow() throws Exception {
        String username = "user";
        String credentialJson = "{}";

        WebAuthnChallenge challenge = WebAuthnChallenge.builder()
                .username(username).type("REG").requestJson("{}").build();
        when(challengeRepository.findTopByUsernameAndTypeOrderByCreatedAtDesc(username, "REG"))
                .thenReturn(Optional.of(challenge));

        when(webAuthnClient.finishRegistrationFromJson(any(String.class), any(String.class)))
                .thenThrow(new RegistrationFailedException(new IllegalArgumentException("bad")));

        assertThatThrownBy(() -> webAuthnService.finishRegistration(username, credentialJson))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Registration failed");
    }


    @Test
    void finishRegistration_WithoutChallenge_ShouldThrowException() {
        String username = "user";
        String credentialJson = "{\"id\":\"credId\"}";

        when(challengeRepository.findTopByUsernameAndTypeOrderByCreatedAtDesc(username, "REG"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> webAuthnService.finishRegistration(username, credentialJson))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Registration request not found");

        verify(credentialRepository, never()).save(any());
    }

    @Test
    void startLogin_WhenSerializationFails_ShouldThrowRuntimeException() throws IOException {
        String username = "user";
        when(webAuthnClient.startAssertionJson(username)).thenThrow(new IOException("boom"));

        assertThatThrownBy(() -> webAuthnService.startLogin(username))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to serialize options");
    }

    @Test
    void finishRegistration_WithInvalidOptionsJson_ShouldThrowException() {
        String username = "user";
        String credentialJson = "{\"id\":\"credId\"}";
        String invalidOptionsJson = "invalid json";

        WebAuthnChallenge challenge = WebAuthnChallenge.builder()
                .username(username)
                .type("REG")
                .requestJson(invalidOptionsJson)
                .build();

        when(challengeRepository.findTopByUsernameAndTypeOrderByCreatedAtDesc(username, "REG"))
                .thenReturn(Optional.of(challenge));

        // The wrapper should fail to parse and throw IOException causing a wrapped RuntimeException
        try {
            when(webAuthnClient.finishRegistrationFromJson(any(String.class), any(String.class)))
                    .thenThrow(new java.io.IOException("bad json"));
        } catch (Exception ignored) {}

        assertThatThrownBy(() -> webAuthnService.finishRegistration(username, credentialJson))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Registration failed");
    }

    @Test
    void finishLogin_Success_ShouldReturnTokenAndUpdateSignatureCount() throws Exception {
        String username = "user";
        String credentialJson = "{\"id\":\"abc\"}";

        WebAuthnChallenge challenge = WebAuthnChallenge.builder()
                .username(username).type("ASSERT").requestJson("{}").build();
        when(challengeRepository.findTopByUsernameAndTypeOrderByCreatedAtDesc(username, "ASSERT"))
                .thenReturn(Optional.of(challenge));

        WebAuthnClient.AssertionOutcome outcome = new WebAuthnClient.AssertionOutcome(true, new byte[]{9,9}, 77L);
        when(webAuthnClient.finishAssertionFromJson(any(String.class), any(String.class))).thenReturn(outcome);

        WebAuthnCredential existing = WebAuthnCredential.builder().credentialId(new byte[]{9, 9}).signatureCount(1).build();
        when(credentialRepository.findByCredentialId(any(byte[].class))).thenReturn(Optional.of(existing));

        User user = User.builder().id(UUID.randomUUID()).username(username).displayName(username).build();
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        AuthDtos.AuthResponse response = webAuthnService.finishLogin(username, credentialJson);

        assertThat(response.getToken()).isEqualTo("token123");
        verify(credentialRepository).save(any(WebAuthnCredential.class));
        verify(challengeRepository).deleteByUsernameAndType(username, "ASSERT");
    }

    @Test
    void finishLogin_WhenRelyingPartyFails_ShouldThrow() throws Exception {
        String username = "user";
        String credentialJson = "{}";

        WebAuthnChallenge challenge = WebAuthnChallenge.builder()
                .username(username).type("ASSERT").requestJson("{}").build();
        when(challengeRepository.findTopByUsernameAndTypeOrderByCreatedAtDesc(username, "ASSERT"))
                .thenReturn(Optional.of(challenge));

        when(webAuthnClient.finishAssertionFromJson(any(String.class), any(String.class)))
                .thenThrow(new AssertionFailedException(new IllegalArgumentException("bad")));

        assertThatThrownBy(() -> webAuthnService.finishLogin(username, credentialJson))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Authentication failed");
    }

    @Test
    void finishLogin_WhenResultNotSuccess_ShouldThrow() throws Exception {
        String username = "user";
        String credentialJson = "{}";

        WebAuthnChallenge challenge = WebAuthnChallenge.builder()
                .username(username).type("ASSERT").requestJson("{}").build();
        when(challengeRepository.findTopByUsernameAndTypeOrderByCreatedAtDesc(username, "ASSERT"))
                .thenReturn(Optional.of(challenge));

        WebAuthnClient.AssertionOutcome failOutcome = new WebAuthnClient.AssertionOutcome(false, new byte[]{}, 0);
        when(webAuthnClient.finishAssertionFromJson(any(String.class), any(String.class))).thenReturn(failOutcome);

        assertThatThrownBy(() -> webAuthnService.finishLogin(username, credentialJson))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Authentication failed");
    }


    // === Login Tests ===

    @Test
    void startLogin_ShouldReturnAssertionRequest() throws IOException {
        String username = "user";
        when(webAuthnClient.startAssertionJson(username)).thenReturn("{\"challenge\":\"login123\"}");

        String result = webAuthnService.startLogin(username);

        assertThat(result).isEqualTo("{\"challenge\":\"login123\"}");
        verify(challengeRepository).deleteByUsernameAndType(username, "ASSERT");
        verify(challengeRepository).save(any(WebAuthnChallenge.class));
    }



    @Test
    void finishLogin_WithoutChallenge_ShouldThrowException() {
        String username = "user";
        String credentialJson = "{\"id\":\"credId\"}";

        when(challengeRepository.findTopByUsernameAndTypeOrderByCreatedAtDesc(username, "ASSERT"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> webAuthnService.finishLogin(username, credentialJson))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Login request not found");
    }

    @Test
    void finishLogin_WithInvalidRequest_ShouldThrowException() throws Exception {
        String username = "user";
        String credentialJson = "{\"id\":\"credId\"}";
        String invalidRequestJson = "invalid json";

        WebAuthnChallenge challenge = WebAuthnChallenge.builder()
                .username(username)
                .type("ASSERT")
                .requestJson(invalidRequestJson)
                .build();

        when(challengeRepository.findTopByUsernameAndTypeOrderByCreatedAtDesc(username, "ASSERT"))
                .thenReturn(Optional.of(challenge));

        when(webAuthnClient.finishAssertionFromJson(any(String.class), any(String.class))).thenThrow(new IOException("bad json"));

        assertThatThrownBy(() -> webAuthnService.finishLogin(username, credentialJson))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Authentication failed");
    }
}
