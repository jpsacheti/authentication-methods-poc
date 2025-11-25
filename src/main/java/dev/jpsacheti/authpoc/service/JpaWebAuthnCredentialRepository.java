package dev.jpsacheti.authpoc.service;

import dev.jpsacheti.authpoc.repository.UserRepository;
import dev.jpsacheti.authpoc.repository.WebAuthnCredentialRepository;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JpaWebAuthnCredentialRepository implements CredentialRepository {

    private final WebAuthnCredentialRepository credentialRepository;
    private final UserRepository userRepository;

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        var user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return Set.of();
        }
        return credentialRepository.findByUser(user).stream()
                .map(c -> PublicKeyCredentialDescriptor.builder()
                        .id(c.getCredentialIdByteArray())
                        .build())
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return userRepository.findByUsername(username)
                .map(user -> new ByteArray(user.getUsername().getBytes())); // Using username as handle for simplicity
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        return Optional.of(new String(userHandle.getBytes()));
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        return credentialRepository.findByCredentialId(credentialId.getBytes())
                .map(c -> RegisteredCredential.builder()
                        .credentialId(c.getCredentialIdByteArray())
                        .userHandle(userHandle)
                        .publicKeyCose(c.getPublicKeyCoseByteArray())
                        .signatureCount(c.getSignatureCount())
                        .build());
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        return credentialRepository.findByCredentialId(credentialId.getBytes()).stream()
                .map(c -> RegisteredCredential.builder()
                        .credentialId(c.getCredentialIdByteArray())
                        .userHandle(new ByteArray(c.getUser().getUsername().getBytes()))
                        .publicKeyCose(c.getPublicKeyCoseByteArray())
                        .signatureCount(c.getSignatureCount())
                        .build())
                .collect(Collectors.toSet());
    }
}
