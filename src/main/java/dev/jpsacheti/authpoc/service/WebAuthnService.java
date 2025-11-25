package dev.jpsacheti.authpoc.service;

import dev.jpsacheti.authpoc.dto.AuthDtos;
import dev.jpsacheti.authpoc.model.User;
import dev.jpsacheti.authpoc.model.WebAuthnCredential;
import dev.jpsacheti.authpoc.repository.UserRepository;
import dev.jpsacheti.authpoc.repository.WebAuthnCredentialRepository;
import dev.jpsacheti.authpoc.security.JwtService;
import com.yubico.webauthn.*;
import com.yubico.webauthn.data.*;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class WebAuthnService {

    private final RelyingParty relyingParty;
    private final UserRepository userRepository;
    private final WebAuthnCredentialRepository credentialRepository;
    private final JwtService jwtService;

    // Temporary storage for challenges. In production, use Redis or Session.
    private final Map<String, PublicKeyCredentialCreationOptions> registrationRequests = new ConcurrentHashMap<>();
    private final Map<String, AssertionRequest> assertionRequests = new ConcurrentHashMap<>();

    // --- Registration ---

    /**
     * Starts the WebAuthn registration ceremony.
     * Generates the creation options to be sent to the client (browser).
     *
     * @param username The username of the user registering.
     * @return JSON string of PublicKeyCredentialCreationOptions.
     */
    public String startRegistration(String username) {
        User user = userRepository.findByUsername(username)
                .orElseGet(() -> userRepository.save(User.builder()
                        .username(username)
                        .displayName(username)
                        .build()));

        UserIdentity userIdentity = UserIdentity.builder()
                .name(username)
                .displayName(user.getDisplayName())
                .id(new ByteArray(user.getUsername().getBytes()))
                .build();

        PublicKeyCredentialCreationOptions options = relyingParty.startRegistration(StartRegistrationOptions.builder()
                .user(userIdentity)
                .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                        .authenticatorAttachment(AuthenticatorAttachment.PLATFORM)
                        .userVerification(UserVerificationRequirement.REQUIRED)
                        .build())
                .build());

        String jsonOptions;
        try {
            jsonOptions = options.toCredentialsCreateJson();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize options", e);
        }

        registrationRequests.put(username, options);
        return jsonOptions;
    }

    /**
     * Finishes the WebAuthn registration ceremony.
     * Verifies the credential returned by the client and saves it.
     *
     * @param username       The username of the user.
     * @param credentialJson The JSON string of the credential response from the
     *                       client.
     */
    public void finishRegistration(String username, String credentialJson) {
        PublicKeyCredentialCreationOptions options = registrationRequests.remove(username);
        if (options == null) {
            throw new RuntimeException("Registration request not found");
        }

        try {
            RegistrationResult result = relyingParty.finishRegistration(FinishRegistrationOptions.builder()
                    .request(options)
                    .response(PublicKeyCredential.parseRegistrationResponseJson(credentialJson))
                    .build());

            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            WebAuthnCredential credential = WebAuthnCredential.builder()
                    .user(user)
                    .credentialId(result.getKeyId().getId().getBytes())
                    .publicKeyCose(result.getPublicKeyCose().getBytes())
                    .signatureCount(result.getSignatureCount())
                    .build();

            credentialRepository.save(credential);

        } catch (RegistrationFailedException | IOException e) {
            throw new RuntimeException("Registration failed", e);
        }
    }

    // --- Authentication ---

    public String startLogin(String username) {
        AssertionRequest request = relyingParty.startAssertion(StartAssertionOptions.builder()
                .username(username)
                .build());

        String jsonOptions;
        try {
            jsonOptions = request.toCredentialsGetJson();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize options", e);
        }

        assertionRequests.put(username, request);
        return jsonOptions;
    }

    public AuthDtos.AuthResponse finishLogin(String username, String credentialJson) {
        AssertionRequest request = assertionRequests.remove(username);
        if (request == null) {
            throw new RuntimeException("Login request not found");
        }

        try {
            AssertionResult result = relyingParty.finishAssertion(FinishAssertionOptions.builder()
                    .request(request)
                    .response(PublicKeyCredential.parseAssertionResponseJson(credentialJson))
                    .build());

            if (!result.isSuccess()) {
                throw new RuntimeException("Authentication failed");
            }

            // Update signature count
            credentialRepository.findByCredentialId(result.getCredential().getCredentialId().getBytes())
                    .ifPresent(c -> {
                        c.setSignatureCount(result.getSignatureCount());
                        credentialRepository.save(c);
                    });

            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String token = jwtService.generateToken(user);
            return AuthDtos.AuthResponse.builder().token(token).build();

        } catch (AssertionFailedException | IOException e) {
            throw new RuntimeException("Authentication failed", e);
        }
    }
}
