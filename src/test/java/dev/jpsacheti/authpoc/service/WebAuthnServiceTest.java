package dev.jpsacheti.authpoc.service;

import dev.jpsacheti.authpoc.model.User;
import dev.jpsacheti.authpoc.repository.UserRepository;
import dev.jpsacheti.authpoc.repository.WebAuthnCredentialRepository;
import dev.jpsacheti.authpoc.security.JwtService;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.UserIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebAuthnServiceTest {

    @Mock
    private RelyingParty relyingParty;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WebAuthnCredentialRepository credentialRepository;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private WebAuthnService webAuthnService;

    @Test
    void startRegistration_ShouldReturnJsonOptions() throws IOException {
        String username = "user";
        User user = User.builder().username(username).displayName("User").build();

        // Mock User
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        // Mock RelyingParty response
        PublicKeyCredentialCreationOptions options = mock(PublicKeyCredentialCreationOptions.class);
        when(options.toCredentialsCreateJson()).thenReturn("{}");

        when(relyingParty.startRegistration(any(StartRegistrationOptions.class))).thenReturn(options);

        String result = webAuthnService.startRegistration(username);

        assertThat(result).isEqualTo("{}");
    }
}
