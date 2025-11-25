package dev.jpsacheti.authpoc.repository;

import dev.jpsacheti.authpoc.model.WebAuthnChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebAuthnChallengeRepository extends JpaRepository<WebAuthnChallenge, UUID> {
    Optional<WebAuthnChallenge> findTopByUsernameAndTypeOrderByCreatedAtDesc(String username, String type);
    void deleteByUsernameAndType(String username, String type);
}
