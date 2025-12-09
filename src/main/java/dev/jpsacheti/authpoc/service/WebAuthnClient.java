package dev.jpsacheti.authpoc.service;

import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;

import java.io.IOException;

/**
 * Thin wrapper around Yubico WebAuthn SDK static/final classes to improve testability.
 * <p>
 * Rationale: the Yubico SDK relies on final classes and static factory/parse methods,
 * which are cumbersome to mock in unit tests. By encapsulating those calls here, the
 * application service layer can depend on this interface and be tested with simple
 * Mockito stubs. The Spring implementation delegates to the real SDK.
 */
public interface WebAuthnClient {
    // High-level helpers to avoid exposing final/static SDK types to tests
    /**
     * Starts a WebAuthn registration ceremony and returns the creation options as JSON.
     *
     * @param user       the WebAuthn {@link UserIdentity}
     * @param selection  authenticator selection preferences (e.g., platform vs cross-platform)
     * @return JSON string of {@code PublicKeyCredentialCreationOptions}
     * @throws IOException if serialization fails
     */
    String startRegistrationJson(UserIdentity user, AuthenticatorSelectionCriteria selection) throws IOException;

    /**
     * Finishes a WebAuthn registration ceremony from JSON inputs.
     *
     * @param optionsJson    JSON for {@code PublicKeyCredentialCreationOptions} (previously returned to the client)
     * @param credentialJson JSON attestation response sent back by the browser
     * @return simplified outcome with credential id, public key, and signature counter
     * @throws RegistrationFailedException if verification fails
     * @throws IOException                 if parsing fails
     */
    WebAuthnClient.RegistrationOutcome finishRegistrationFromJson(String optionsJson, String credentialJson) throws RegistrationFailedException, IOException;

    /**
     * Starts a WebAuthn assertion (login) ceremony, returning the request options as JSON.
     *
     * @param username application username
     * @return JSON string of {@code AssertionRequest}
     * @throws IOException if serialization fails
     */
    String startAssertionJson(String username) throws IOException;

    /**
     * Finishes a WebAuthn assertion (login) ceremony from JSON inputs.
     *
     * @param requestJson    JSON {@code AssertionRequest} previously sent to the client
     * @param credentialJson JSON assertion response from the browser
     * @return simplified outcome with success flag, credential id, and signature count
     * @throws AssertionFailedException if verification fails
     * @throws IOException              if parsing fails
     */
    WebAuthnClient.AssertionOutcome finishAssertionFromJson(String requestJson, String credentialJson) throws AssertionFailedException, IOException;

    class RegistrationOutcome {
        public final byte[] credentialId;
        public final byte[] publicKeyCose;
        public final long signatureCount;

        public RegistrationOutcome(byte[] credentialId, byte[] publicKeyCose, long signatureCount) {
            this.credentialId = credentialId;
            this.publicKeyCose = publicKeyCose;
            this.signatureCount = signatureCount;
        }
    }

    class AssertionOutcome {
        public final boolean success;
        public final byte[] credentialId;
        public final long signatureCount;

        public AssertionOutcome(boolean success, byte[] credentialId, long signatureCount) {
            this.success = success;
            this.credentialId = credentialId;
            this.signatureCount = signatureCount;
        }
    }
}
