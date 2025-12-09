package dev.jpsacheti.authpoc.service;

import dev.jpsacheti.authpoc.dto.AuthDtos;
import dev.jpsacheti.authpoc.model.User;
import dev.jpsacheti.authpoc.model.WebAuthnCredential;
import dev.jpsacheti.authpoc.model.WebAuthnChallenge;
import dev.jpsacheti.authpoc.repository.UserRepository;
import dev.jpsacheti.authpoc.repository.WebAuthnCredentialRepository;
import dev.jpsacheti.authpoc.repository.WebAuthnChallengeRepository;
import dev.jpsacheti.authpoc.security.JwtService;
import com.yubico.webauthn.*;
import com.yubico.webauthn.data.*;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Application service orchestrating WebAuthn registration and authentication flows.
 * <p>
 * Design notes:
 * - This service depends on {@link WebAuthnClient}, a small wrapper around the Yubico WebAuthn SDK,
 *   to avoid direct use of final classes and static parse/factory methods in the service layer.
 *   That indirection keeps unit tests straightforward with regular Mockito stubs.
 * - Registration and assertion (login) option payloads are persisted as JSON "challenges" and
 *   removed when the ceremonies complete to prevent replay and keep storage tidy.
 * - On successful login, a JWT is produced by {@link dev.jpsacheti.authpoc.security.JwtService}.
 */
@Service
@RequiredArgsConstructor
public class WebAuthnService {

    private final WebAuthnClient webAuthnClient;
    private final UserRepository userRepository;
    private final WebAuthnCredentialRepository credentialRepository;
    private final WebAuthnChallengeRepository challengeRepository;
    private final JwtService jwtService;


    // --- Registration ---

    /**
     * Starts the WebAuthn registration ceremony.
     * Generates the creation options to be sent to the client (browser).
     *
     * @param username   The username of the user registering.
     * @param attachment Optional authenticator attachment preference: "platform", "cross-platform", or "any" (null treated as platform for POC).
     * @return JSON string of PublicKeyCredentialCreationOptions.
     */
    @Transactional
    public String startRegistration(String username, String attachment) {
        User user = userRepository.findByUsername(username)
                .orElseGet(() -> userRepository.save(User.builder()
                        .username(username)
                        .displayName(username)
                        .build()));

        UserIdentity userIdentity = UserIdentity.builder()
                .name(username)
                .displayName(user.getDisplayName())
                .id(new ByteArray(uuidToBytes(user.getId())))
                .build();

        AuthenticatorSelectionCriteria.AuthenticatorSelectionCriteriaBuilder selectionBuilder = AuthenticatorSelectionCriteria.builder()
                .userVerification(UserVerificationRequirement.REQUIRED);

        // Normalize and apply attachment preference (POC-friendly)
        AuthenticatorAttachment chosenAttachment = selectAuthenticatorAttachment(attachment);

        if (chosenAttachment != null) {
            selectionBuilder.authenticatorAttachment(chosenAttachment);
        }

        String jsonOptions;
        try {
            jsonOptions = webAuthnClient.startRegistrationJson(userIdentity, selectionBuilder.build());
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize options", e);
        }
        // Persist challenge request JSON
        challengeRepository.deleteByUsernameAndType(username, "REG");
        challengeRepository.save(WebAuthnChallenge.builder()
                .username(username)
                .type("REG")
                .requestJson(jsonOptions)
                .build());
        return jsonOptions;
    }

    private static AuthenticatorAttachment selectAuthenticatorAttachment(String attachment) {
        AuthenticatorAttachment chosenAttachment = null;
        if (attachment != null) {
            String att = attachment.trim().toLowerCase();
            if (att.equals("platform")) {
                chosenAttachment = AuthenticatorAttachment.PLATFORM;
            } else if (att.equals("cross-platform") || att.equals("cross_platform") || att.equals("crossplatform") || att.equals("external") || att.equals("security-key") || att.equals("security_key")) {
                chosenAttachment = AuthenticatorAttachment.CROSS_PLATFORM;
            }

        } else {
            // Default to platform (passkey) for better UX on supported devices
            chosenAttachment = AuthenticatorAttachment.PLATFORM;
        }
        return chosenAttachment;
    }

    /**
     * Finishes the WebAuthn registration ceremony.
     * Verifies the credential returned by the client and saves it.
     *
     * @param username       The username of the user.
     * @param credentialJson The JSON string of the credential response from the client.
     * @throws RuntimeException if verification fails or request wasn’t previously started.
     */
    @Transactional
    public void finishRegistration(String username, String credentialJson) {
        var challengeOpt = challengeRepository.findTopByUsernameAndTypeOrderByCreatedAtDesc(username, "REG");
        if (challengeOpt.isEmpty()) {
            throw new RuntimeException("Registration request not found");
        }
        String optionsJson = challengeOpt.get().getRequestJson();

        try {
            WebAuthnClient.RegistrationOutcome result = webAuthnClient.finishRegistrationFromJson(optionsJson, credentialJson);

            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            WebAuthnCredential credential = WebAuthnCredential.builder()
                    .user(user)
                    .credentialId(result.credentialId)
                    .publicKeyCose(result.publicKeyCose)
                    .signatureCount(result.signatureCount)
                    .build();

            credentialRepository.save(credential);
            challengeRepository.deleteByUsernameAndType(username, "REG");

        } catch (RegistrationFailedException | IOException e) {
            throw new RuntimeException("Registration failed", e);
        }
    }

    // --- Authentication ---

    /**
     * Starts the WebAuthn assertion (login) ceremony and returns the request options (JSON).
     *
     * @param username the application username
     * @return JSON string of AssertionRequest
     */
    public String startLogin(String username) {
        String jsonOptions;
        try {
            jsonOptions = webAuthnClient.startAssertionJson(username);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize options", e);
        }
        challengeRepository.deleteByUsernameAndType(username, "ASSERT");
        challengeRepository.save(WebAuthnChallenge.builder()
                .username(username)
                .type("ASSERT")
                .requestJson(jsonOptions)
                .build());
        return jsonOptions;
    }


    /**
     * Finishes the WebAuthn assertion (login) ceremony.
     * On success, updates the stored signature counter and returns a JWT.
     *
     * @param username       the application username
     * @param credentialJson JSON assertion response from the browser
     * @return a JWT-wrapping response
     * @throws RuntimeException if verification fails or request wasn’t previously started
     */
    @Transactional
    public AuthDtos.AuthResponse finishLogin(String username, String credentialJson) {
        var challengeOpt = challengeRepository.findTopByUsernameAndTypeOrderByCreatedAtDesc(username, "ASSERT");
        if (challengeOpt.isEmpty()) {
            throw new RuntimeException("Login request not found");
        }
        String requestJson = challengeOpt.get().getRequestJson();

        try {
            WebAuthnClient.AssertionOutcome result = webAuthnClient.finishAssertionFromJson(requestJson, credentialJson);

            if (!result.success) {
                throw new RuntimeException("Authentication failed");
            }

            // Update signature count
            credentialRepository.findByCredentialId(result.credentialId)
                    .ifPresent(c -> {
                        c.setSignatureCount(result.signatureCount);
                        credentialRepository.save(c);
                    });

            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String token = jwtService.generateToken(user);
            challengeRepository.deleteByUsernameAndType(username, "ASSERT");
            return AuthDtos.AuthResponse.builder().token(token).build();

        } catch (AssertionFailedException | IOException e) {
            throw new RuntimeException("Authentication failed", e);
        }
    }

    private static byte[] uuidToBytes(java.util.UUID uuid) {
        ByteBuffer bb = ByteBuffer.allocate(16);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }
}
