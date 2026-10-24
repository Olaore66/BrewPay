package com.brewpay.identity;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Random session secrets. The database stores the SHA-256 hex, never the secret. */
final class SessionTokens {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

  private SessionTokens() {}

  static String newToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return ENCODER.encodeToString(bytes);
  }

  static String hash(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required", exception);
    }
  }
}
