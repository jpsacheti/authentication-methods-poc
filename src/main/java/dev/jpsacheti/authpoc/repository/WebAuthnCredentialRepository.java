package dev.jpsacheti.authpoc.repository;

import dev.jpsacheti.authpoc.model.User;
import dev.jpsacheti.authpoc.model.WebAuthnCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebAuthnCredentialRepository extends JpaRepository<WebAuthnCredential, UUID> {
    List<WebAuthnCredential> findByUser(User user);

    Optional<WebAuthnCredential> findByCredentialId(byte[] credentialId);
}
