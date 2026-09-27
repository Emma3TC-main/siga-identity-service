package utp.siga.identity.application.usecase;

import java.util.Map;
import org.springframework.stereotype.Service;
import utp.siga.identity.application.port.in.KeyDiscoveryUseCase;
import utp.siga.identity.application.port.out.JwksPort;

@Service
public class KeyDiscoveryService implements KeyDiscoveryUseCase {
  private final JwksPort keys;

  public KeyDiscoveryService(JwksPort keys) {
    this.keys = keys;
  }

  public Map<String, Object> jwks() {
    return keys.jwks();
  }
}
