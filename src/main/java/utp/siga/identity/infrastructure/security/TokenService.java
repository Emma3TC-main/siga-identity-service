package utp.siga.identity.infrastructure.security;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import utp.siga.identity.domain.model.Account;

@Component
public class TokenService {
  private final RSAKey jwk;
  private final JwtEncoder encoder;
  public final RSAPublicKey publicKey;
  public final String issuer, audience;
  public final long accessSeconds, refreshSeconds;

  public TokenService(
      @Value("${iam.private-key}") Resource privateResource,
      @Value("${iam.public-key}") Resource publicResource,
      @Value("${iam.issuer}") String issuer,
      @Value("${iam.audience}") String audience,
      @Value("${iam.access-seconds}") long accessSeconds,
      @Value("${iam.refresh-seconds}") long refreshSeconds)
      throws Exception {
    var f = KeyFactory.getInstance("RSA");
    publicKey = (RSAPublicKey) f.generatePublic(new X509EncodedKeySpec(pem(publicResource)));
    var privateKey = (RSAPrivateKey) f.generatePrivate(new PKCS8EncodedKeySpec(pem(privateResource)));
    if (!privateKey.getModulus().equals(publicKey.getModulus())
        || publicKey.getModulus().bitLength() < 2048)
      throw new IllegalArgumentException("Invalid RSA key pair");
    jwk = new RSAKey.Builder(publicKey)
        .privateKey(privateKey)
        .keyID(
            Crypto.hash(Base64.getEncoder().encodeToString(publicKey.getEncoded()))
                .substring(0, 16))
        .build();
    encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    this.issuer = issuer;
    this.audience = audience;
    this.accessSeconds = accessSeconds;
    this.refreshSeconds = refreshSeconds;
  }

  private static byte[] pem(Resource resource) throws Exception {
    try (var in = resource.getInputStream()) {
      return Base64.getDecoder()
          .decode(
              new String(in.readAllBytes(), java.nio.charset.StandardCharsets.US_ASCII)
                  .replaceAll("-----[^-]+-----", "")
                  .replaceAll("\\s", ""));
    }
  }

  public String access(Account a, Set<String> permissions, UUID family, Long mfaTime) {
    var now = Instant.now();
    var claims = JwtClaimsSet.builder()
        .issuer(issuer)
        .audience(List.of(audience))
        .subject(a.id().toString())
        .issuedAt(now)
        .expiresAt(now.plusSeconds(accessSeconds))
        .id(UUID.randomUUID().toString())
        .claim("username", a.username())
        .claim("permissions", permissions)
        .claim("sid", family.toString())
        .claim("amr", mfaTime == null ? List.of("pwd") : List.of("pwd", "otp"));
    if (mfaTime != null)
      claims.claim("auth_time", mfaTime);
    return encoder
        .encode(
            JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId(jwk.getKeyID()).build(),
                claims.build()))
        .getTokenValue();
  }

  public Map<String, Object> jwks() {
    return new JWKSet(jwk.toPublicJWK()).toJSONObject();
  }
}
