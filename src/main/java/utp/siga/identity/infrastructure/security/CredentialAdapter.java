package utp.siga.identity.infrastructure.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import utp.siga.identity.application.port.out.CredentialPort;

@Component
public class CredentialAdapter implements CredentialPort {
  private final PasswordEncoder encoder;

  public CredentialAdapter(PasswordEncoder encoder) {
    this.encoder = encoder;
  }

  @Override
  public String encode(CharSequence raw) {
    return encoder.encode(raw);
  }

  @Override
  public boolean matches(CharSequence raw, String encoded) {
    return encoder.matches(raw, encoded);
  }
}
