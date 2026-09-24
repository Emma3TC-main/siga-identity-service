package utp.siga.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record Account(
    UUID id,
    String username,
    String email,
    String passwordHash,
    boolean active,
    boolean mfaEnabled,
    String encryptedSecret,
    boolean enrolled,
    Long lastStep,
    int failures,
    Instant lockedUntil) {}
