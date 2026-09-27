package utp.siga.identity.application.port.out;

public interface CryptoPort {
  String randomToken();

  String newSecret();

  String encrypt(String value);

  String decrypt(String value);

  String hashValue(String value);

  Long verifyTotp(String secret, String otp, long now, Long lastStep);
}
