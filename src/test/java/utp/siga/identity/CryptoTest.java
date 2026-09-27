package utp.siga.identity;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import utp.siga.identity.infrastructure.security.Crypto;

class CryptoTest {
  @Test
  void rfc6238Sha1VectorAndReplay() {
    String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    assertThat(Crypto.totp(secret, 1)).isEqualTo("287082");
    assertThat(Crypto.verify(secret, "287082", 59, null)).isEqualTo(1L);
    assertThat(Crypto.verify(secret, "287082", 59, 1L)).isNull();
    assertThat(Crypto.verify(secret, "287082", 180, null)).isNull();
  }

  @Test
  void encryptionAuthenticatesAndUsesRandomNonces() {
    var crypto = new Crypto("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    String value = crypto.newSecret();
    String encrypted = crypto.encrypt(value);
    assertThat(crypto.decrypt(encrypted)).isEqualTo(value);
    assertThat(crypto.encrypt(value)).isNotEqualTo(encrypted);
    byte[] damaged = java.util.Base64.getDecoder().decode(encrypted);
    damaged[15] ^= 1;
    assertThatThrownBy(() -> crypto.decrypt(java.util.Base64.getEncoder().encodeToString(damaged)))
        .isInstanceOf(IllegalStateException.class);
  }
}
