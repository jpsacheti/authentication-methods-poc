package dev.jpsacheti.authpoc.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "webauthn_challenges", indexes = {
        @Index(name = "idx_webauthn_challenges_user_type", columnList = "username,type")
})
public class WebAuthnChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(nullable = false, length = 16)
    private String type; // REG or ASSERT

    @Lob
    @Column(nullable = false)
    private String requestJson;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
