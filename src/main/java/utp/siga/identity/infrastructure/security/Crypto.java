package utp.siga.identity.infrastructure.security;

import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Crypto {
  private final byte[] key;
  private final SecureRandom random = new SecureRandom();

  public Crypto(@Value("${iam.encryption-key}") String value) {
    key = Base64.getDecoder().decode(value);
    if (key.length != 32)
      throw new IllegalArgumentException("MFA_ENCRYPTION_KEY must be a base64 256-bit key");
  }

  public String randomToken() {
    byte[] b = new byte[32];
    random.nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  public String newSecret() {
    byte[] b = new byte[20];
    random.nextBytes(b);
    StringBuilder s = new StringBuilder();
    int v = 0, bits = 0;
    for (byte x : b) {
      v = (v << 8) | (x & 255);
      bits += 8;
      while (bits >= 5) {
        bits -= 5;
        s.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".charAt((v >> bits) & 31));
      }
    }
    return s.toString();
  }

  public String encrypt(String value) {
    try {
      byte[] iv = new byte[12];
      random.nextBytes(iv);
      var c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
      byte[] encrypted = c.doFinal(value.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder()
          .encodeToString(
              ByteBuffer.allocate(12 + encrypted.length).put(iv).put(encrypted).array());
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Encryption failed", e);
    }
  }

  public String decrypt(String value) {
    try {
      byte[] b = Base64.getDecoder().decode(value);
      var c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(key, "AES"),
          new GCMParameterSpec(128, Arrays.copyOf(b, 12)));
      return new String(c.doFinal(Arrays.copyOfRange(b, 12, b.length)), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Decryption failed", e);
    }
  }

  public static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  public static String totp(String secret, long step) {
    try {
      byte[] decoded = new byte[secret.length() * 5 / 8];
      int v = 0, bits = 0, i = 0;
      for (char ch : secret.toCharArray()) {
        int digit = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(ch);
        if (digit < 0) throw new IllegalArgumentException("Invalid Base32");
        v = (v << 5) | digit;
        bits += 5;
        if (bits >= 8) {
          bits -= 8;
          decoded[i++] = (byte) (v >> bits);
        }
      }
      var mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(decoded, "HmacSHA1"));
      byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
      int offset = h[h.length - 1] & 15;
      int binary = ByteBuffer.wrap(h, offset, 4).getInt() & 0x7fffffff;
      return String.format(Locale.ROOT, "%06d", binary % 1000000);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  public static Long verify(String secret, String otp, long now, Long last) {
    for (long step = now / 30 - 1; step <= now / 30 + 1; step++) {
      if ((last == null || step > last)
          && MessageDigest.isEqual(
              totp(secret, step).getBytes(StandardCharsets.US_ASCII),
              otp.getBytes(StandardCharsets.US_ASCII))) return step;
    }
    return null;
  }
}
