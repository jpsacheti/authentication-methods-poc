package dev.jpsacheti.authpoc.model;

import com.yubico.webauthn.data.ByteArray;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "webauthn_credentials")
public class WebAuthnCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Lob
    @Column(nullable = false, length = 10000, unique = true, columnDefinition = "bytea")
    private byte[] credentialId;

    @Lob
    @Column(nullable = false, length = 10000, columnDefinition = "bytea")
    private byte[] publicKeyCose;

    @Column(nullable = false)
    private long signatureCount;

    // Helper methods for Yubico library interaction if needed
    public ByteArray getCredentialIdByteArray() {
        return new ByteArray(credentialId);
    }

    public ByteArray getPublicKeyCoseByteArray() {
        return new ByteArray(publicKeyCose);
    }
}
