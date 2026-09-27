package utp.siga.identity.application.port.out;

import java.time.Instant;
import java.util.UUID;

public record RefreshSession(
    UUID familyId, boolean revoked, Instant expiresAt, Instant mfaAuthenticatedAt) {}
