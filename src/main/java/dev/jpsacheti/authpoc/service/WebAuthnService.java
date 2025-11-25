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

@Service
@RequiredArgsConstructor
public class WebAuthnService {

    private final RelyingParty relyingParty;
    private final UserRepository userRepository;
    private final WebAuthnCredentialRepository credentialRepository;
    private final WebAuthnChallengeRepository challengeRepository;
    private final JwtService jwtService;

    // Legacy in-memory maps replaced by Postgres-backed storage to keep POC simple and consistent across restarts.

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
                .id(new ByteArray(uuidToBytes(user.getId())))
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
        // Persist challenge request JSON
        challengeRepository.deleteByUsernameAndType(username, "REG");
        challengeRepository.save(WebAuthnChallenge.builder()
                .username(username)
                .type("REG")
                .requestJson(jsonOptions)
                .build());
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
    @Transactional
    public void finishRegistration(String username, String credentialJson) {
        var challengeOpt = challengeRepository.findTopByUsernameAndTypeOrderByCreatedAtDesc(username, "REG");
        if (challengeOpt.isEmpty()) {
            throw new RuntimeException("Registration request not found");
        }
        String optionsJson = challengeOpt.get().getRequestJson();
        PublicKeyCredentialCreationOptions options;
        try {
            options = PublicKeyCredentialCreationOptions.fromJson(optionsJson);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize registration options", e);
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
            challengeRepository.deleteByUsernameAndType(username, "REG");

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
        challengeRepository.deleteByUsernameAndType(username, "ASSERT");
        challengeRepository.save(WebAuthnChallenge.builder()
                .username(username)
                .type("ASSERT")
                .requestJson(jsonOptions)
                .build());
        return jsonOptions;
    }

    @Transactional
    public AuthDtos.AuthResponse finishLogin(String username, String credentialJson) {
        var challengeOpt = challengeRepository.findTopByUsernameAndTypeOrderByCreatedAtDesc(username, "ASSERT");
        if (challengeOpt.isEmpty()) {
            throw new RuntimeException("Login request not found");
        }
        String requestJson = challengeOpt.get().getRequestJson();
        AssertionRequest request;
        try {
            request = AssertionRequest.fromJson(requestJson);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize assertion request", e);
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
