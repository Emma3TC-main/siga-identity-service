package utp.siga.identity.application;

import jakarta.validation.constraints.*;
import java.util.*;

public final class Api {
  private Api() {}

  public record Login(
      @NotBlank @Size(max = 100) String username, @NotBlank @Size(max = 1024) String password) {}

  public record Mfa(@NotNull UUID challengeId, @NotNull @Pattern(regexp = "[0-9]{6}") String otp) {}

  public record Enrollment(@NotNull UUID challengeId) {}

  public record Refresh(@NotBlank @Size(max = 256) String refreshToken) {}

  public record CreateUser(
      @NotBlank @Size(max = 100) String username,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 12, max = 1024) String password,
      Boolean mfaEnabled,
      Set<@NotNull UUID> roleIds) {}

  public record UpdateUser(
      @Email @Size(min = 1, max = 254) String email, Boolean active, Boolean mfaEnabled) {}

  public record AssignRoles(@NotNull Set<@NotNull UUID> roleIds) {}

  public record CreateRole(
      @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String code,
      @NotBlank @Size(max = 120) String name,
      Set<@NotNull UUID> permissionIds) {}

  public record AssignPermissions(@NotNull Set<@NotNull UUID> permissionIds) {}

  public record User(
      UUID id,
      String username,
      String email,
      boolean active,
      boolean mfaEnabled,
      Set<UUID> roleIds) {}

  public record Role(UUID id, String code, String name, boolean active, Set<UUID> permissionIds) {}

  public record Tokens(String accessToken, String refreshToken, long expiresIn, String tokenType) {}

  public record LoginResult(boolean mfaRequired, UUID challengeId, Tokens tokens) {}

  public record Page(List<User> content, int page, int size, long totalElements) {}

  public record EnrollmentResult(String secret, String otpauthUri) {}
}
