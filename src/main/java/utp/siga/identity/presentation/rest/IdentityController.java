package utp.siga.identity.presentation.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import utp.siga.identity.application.dto.Api;
import utp.siga.identity.application.usecase.AuthService;
import utp.siga.identity.application.usecase.ManagementService;
import utp.siga.identity.domain.exception.IdentityException;
import utp.siga.identity.infrastructure.persistence.IdentityRepository;
import utp.siga.identity.infrastructure.security.TokenService;

@RestController
public class IdentityController {
  private final AuthService auth;
  private final ManagementService management;
  private final IdentityRepository repo;
  private final TokenService tokens;

  public IdentityController(
      AuthService auth,
      ManagementService management,
      IdentityRepository repo,
      TokenService tokens) {
    this.auth = auth;
    this.management = management;
    this.repo = repo;
    this.tokens = tokens;
  }

  @PostMapping("/api/v1/auth/login")
  public Api.LoginResult login(@Valid @RequestBody Api.Login input, HttpServletRequest req) {
    return auth.login(input, req.getRemoteAddr());
  }

  @PostMapping("/api/v1/auth/mfa/enroll")
  public Api.EnrollmentResult enroll(@Valid @RequestBody Api.Enrollment input) {
    return auth.enroll(input);
  }

  @PostMapping("/api/v1/auth/mfa/verify")
  public Api.Tokens verify(@Valid @RequestBody Api.Mfa input) {
    return auth.verify(input);
  }

  @PostMapping("/api/v1/auth/refresh")
  public Api.Tokens refresh(@Valid @RequestBody Api.Refresh input) {
    return auth.refresh(input);
  }

  @PostMapping("/api/v1/auth/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@AuthenticationPrincipal Jwt jwt) {
    auth.logout(jwt);
  }

  @GetMapping("/.well-known/jwks.json")
  public Map<String, Object> jwks() {
    return tokens.jwks();
  }

  @GetMapping("/api/v1/users")
  @PreAuthorize("hasAuthority('USER_MANAGE')")
  public Api.Page users(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    if (page < 0 || size < 1 || size > 100)
      throw new IdentityException(400, "INVALID_PAGE", "page >= 0 y size entre 1 y 100");
    return repo.users(page, size);
  }

  @GetMapping("/api/v1/users/{id}")
  @PreAuthorize("hasAuthority('USER_MANAGE')")
  public Api.User user(@PathVariable UUID id) {
    return repo.user(id);
  }

  @PostMapping("/api/v1/users")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('USER_MANAGE') and @accessPolicy.recent(authentication) and ( #input.roleIds()"
      + " == null or #input.roleIds().isEmpty() or hasAuthority('ROLE_MANAGE'))")
  public Api.User create(@Valid @RequestBody Api.CreateUser input) {
    return management.create(input);
  }

  @PatchMapping("/api/v1/users/{id}")
  @PreAuthorize("hasAuthority('USER_MANAGE') and @accessPolicy.recent(authentication)")
  public Api.User update(
      @PathVariable UUID id,
      @Valid @RequestBody Api.UpdateUser input,
      @AuthenticationPrincipal Jwt jwt) {
    if (Boolean.FALSE.equals(input.active()) && id.toString().equals(jwt.getSubject()))
      throw new IdentityException(
          409, "SELF_DEACTIVATION", "No puedes desactivar tu propia cuenta");
    return management.update(id, input);
  }

  @PutMapping("/api/v1/users/{id}/roles")
  @PreAuthorize("hasAuthority('ROLE_MANAGE') and @accessPolicy.recent(authentication)")
  public Api.User roles(@PathVariable UUID id, @Valid @RequestBody Api.AssignRoles input) {
    return management.assignRoles(id, input);
  }

  @GetMapping("/api/v1/roles")
  @PreAuthorize("hasAuthority('ROLE_MANAGE')")
  public List<Api.Role> roles() {
    return repo.roles();
  }

  @PostMapping("/api/v1/roles")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('ROLE_MANAGE') and @accessPolicy.recent(authentication)")
  public Api.Role role(@Valid @RequestBody Api.CreateRole input) {
    return management.createRole(input);
  }

  @PutMapping("/api/v1/roles/{id}/permissions")
  @PreAuthorize("hasAuthority('ROLE_MANAGE') and @accessPolicy.recent(authentication)")
  public Api.Role permissions(
      @PathVariable UUID id, @Valid @RequestBody Api.AssignPermissions input) {
    return management.assignPermissions(id, input);
  }

  @GetMapping("/api/v1/permissions")
  @PreAuthorize("hasAuthority('ROLE_MANAGE')")
  public List<Map<String, Object>> permissions() {
    return repo.jdbc().queryForList("SELECT id,code,description FROM iam.permission ORDER BY code");
  }
}
