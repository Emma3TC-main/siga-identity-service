package utp.siga.identity.domain;

public class IdentityException extends RuntimeException {
  public final int status;
  public final String code;

  public IdentityException(int status, String code, String detail) {
    super(detail);
    this.status = status;
    this.code = code;
  }

  public static IdentityException unauthorized() {
    return new IdentityException(401, "AUTH_INVALID", "Credenciales o sesión inválidas");
  }
}
