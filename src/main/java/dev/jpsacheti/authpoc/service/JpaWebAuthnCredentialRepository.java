package dev.jpsacheti.authpoc.service;

import dev.jpsacheti.authpoc.repository.UserRepository;
import dev.jpsacheti.authpoc.repository.WebAuthnCredentialRepository;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

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
                .map(user -> new ByteArray(uuidToBytes(user.getId())));
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        byte[] bytes = userHandle.getBytes();
        if (bytes.length != 16) return Optional.empty();
        UUID id = bytesToUuid(bytes);
        return userRepository.findById(id).map(u -> u.getUsername());
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        return credentialRepository.findByCredentialId(credentialId.getBytes())
                .map(c -> RegisteredCredential.builder()
                        .credentialId(c.getCredentialIdByteArray())
                        .userHandle(new ByteArray(uuidToBytes(c.getUser().getId())))
                        .publicKeyCose(c.getPublicKeyCoseByteArray())
                        .signatureCount(c.getSignatureCount())
                        .build());
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        return credentialRepository.findByCredentialId(credentialId.getBytes()).stream()
                .map(c -> RegisteredCredential.builder()
                        .credentialId(c.getCredentialIdByteArray())
                        .userHandle(new ByteArray(uuidToBytes(c.getUser().getId())))
                        .publicKeyCose(c.getPublicKeyCoseByteArray())
                        .signatureCount(c.getSignatureCount())
                        .build())
                .collect(Collectors.toSet());
    }

    private static byte[] uuidToBytes(UUID uuid) {
        ByteBuffer bb = ByteBuffer.allocate(16);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }

    private static UUID bytesToUuid(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        long high = bb.getLong();
        long low = bb.getLong();
        return new UUID(high, low);
    }
}
