package dev.jpsacheti.authpoc.service;

import com.yubico.webauthn.*;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Spring implementation of {@link WebAuthnClient} delegating to Yubico's {@link RelyingParty}.
 * <p>
 * This class exists to isolate direct calls to the Yubico SDK (which uses final classes
 * and static parse/factory methods). Keeping those calls here allows the service layer
 * to be tested by mocking the {@code WebAuthnClient} interface without static mocking.
 */
@Component
public class WebAuthnClientImpl implements WebAuthnClient {

    private final RelyingParty relyingParty;

    public WebAuthnClientImpl(RelyingParty relyingParty) {
        this.relyingParty = relyingParty;
    }

    /** {@inheritDoc} */
    @Override
    public String startRegistrationJson(UserIdentity user, AuthenticatorSelectionCriteria selection) throws IOException {
        PublicKeyCredentialCreationOptions options = relyingParty.startRegistration(
                StartRegistrationOptions.builder()
                        .user(user)
                        .authenticatorSelection(selection)
                        .build()
        );
        return options.toCredentialsCreateJson();
    }

    /** {@inheritDoc} */
    @Override
    public WebAuthnClient.RegistrationOutcome finishRegistrationFromJson(String optionsJson, String credentialJson) throws RegistrationFailedException, IOException {
        PublicKeyCredentialCreationOptions options = PublicKeyCredentialCreationOptions.fromJson(optionsJson);
        RegistrationResult result = relyingParty.finishRegistration(
                FinishRegistrationOptions.builder()
                        .request(options)
                        .response(com.yubico.webauthn.data.PublicKeyCredential.parseRegistrationResponseJson(credentialJson))
                        .build()
        );
        return new WebAuthnClient.RegistrationOutcome(
                result.getKeyId().getId().getBytes(),
                result.getPublicKeyCose().getBytes(),
                result.getSignatureCount()
        );
    }

    /** {@inheritDoc} */
    @Override
    public String startAssertionJson(String username) throws IOException {
        AssertionRequest request = relyingParty.startAssertion(
                StartAssertionOptions.builder().username(username).build()
        );
        return request.toCredentialsGetJson();
    }

    /** {@inheritDoc} */
    @Override
    public WebAuthnClient.AssertionOutcome finishAssertionFromJson(String requestJson, String credentialJson) throws AssertionFailedException, IOException {
        AssertionRequest request = AssertionRequest.fromJson(requestJson);
        AssertionResult result = relyingParty.finishAssertion(
                FinishAssertionOptions.builder()
                        .request(request)
                        .response(com.yubico.webauthn.data.PublicKeyCredential.parseAssertionResponseJson(credentialJson))
                        .build()
        );
        return new WebAuthnClient.AssertionOutcome(
                result.isSuccess(),
                result.getCredential().getCredentialId().getBytes(),
                result.getSignatureCount()
        );
    }
}
