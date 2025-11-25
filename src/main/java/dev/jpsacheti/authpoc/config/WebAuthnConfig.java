package dev.jpsacheti.authpoc.config;

import dev.jpsacheti.authpoc.service.JpaWebAuthnCredentialRepository;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
@RequiredArgsConstructor
public class WebAuthnConfig {

    private final JpaWebAuthnCredentialRepository credentialRepository;

    @Value("${webauthn.rp.id}")
    private String rpId;

    @Value("${webauthn.rp.name}")
    private String rpName;

    @Value("${webauthn.origin}")
    private String origin;

    @Bean
    public RelyingParty relyingParty() {
        RelyingPartyIdentity rpIdentity = RelyingPartyIdentity.builder()
                .id(rpId)
                .name(rpName)
                .build();

        return RelyingParty.builder()
                .identity(rpIdentity)
                .credentialRepository(credentialRepository)
                .origins(Set.of(origin))
                .build();
    }
}
