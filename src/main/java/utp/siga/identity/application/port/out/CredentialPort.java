package utp.siga.identity.application.port.out;

public interface CredentialPort {
  String encode(CharSequence raw);

  boolean matches(CharSequence raw, String encoded);
}
