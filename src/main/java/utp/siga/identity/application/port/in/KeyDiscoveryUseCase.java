package utp.siga.identity.application.port.in;

import java.util.Map;

public interface KeyDiscoveryUseCase {
  Map<String, Object> jwks();
}
