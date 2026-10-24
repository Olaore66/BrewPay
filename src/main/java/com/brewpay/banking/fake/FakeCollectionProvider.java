package com.brewpay.banking.fake;

import com.brewpay.banking.CollectionProvider;
import com.brewpay.banking.InboundPayment;
import com.brewpay.banking.ProviderPaymentStatus;
import com.brewpay.platform.error.ErrorCode;
import com.brewpay.platform.error.PlatformException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class FakeCollectionProvider implements CollectionProvider {

  private static final String HMAC = "HmacSHA256";

  private final ObjectMapper json;
  private final byte[] secret;

  public FakeCollectionProvider(ObjectMapper json, FakeBankProperties properties) {
    this.json = json;
    this.secret = properties.webhookSecret().getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public String provisionReference() {
    return "FAKE-" + UUID.randomUUID();
  }

  @Override
  public InboundPayment verifyWebhook(String rawBody, String signature) {
    if (!signaturesMatch(signature, mac(rawBody == null ? "" : rawBody))) {
      throw new PlatformException(ErrorCode.UNAUTHORIZED, "Webhook signature is invalid.");
    }
    FakeWebhookBody body = read(rawBody);
    if (body == null
        || body.eventId() == null
        || body.accountReference() == null
        || body.amountKobo() == null
        || body.status() == null) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Webhook could not be read.");
    }
    ProviderPaymentStatus status;
    try {
      status = ProviderPaymentStatus.valueOf(body.status());
    } catch (IllegalArgumentException exception) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Webhook could not be read.");
    }
    return new InboundPayment(body.eventId(), body.accountReference(), body.amountKobo(), status);
  }

  private FakeWebhookBody read(String rawBody) {
    try {
      return json.readValue(rawBody, FakeWebhookBody.class);
    } catch (JacksonException exception) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Webhook could not be read.");
    }
  }

  private byte[] mac(String rawBody) {
    try {
      Mac mac = Mac.getInstance(HMAC);
      mac.init(new SecretKeySpec(secret, HMAC));
      return mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
      throw new IllegalStateException("HmacSHA256 is required", exception);
    }
  }

  private static boolean signaturesMatch(String provided, byte[] expected) {
    if (provided == null || provided.isBlank()) {
      return false;
    }
    byte[] actual;
    try {
      actual = HexFormat.of().parseHex(provided.trim());
    } catch (IllegalArgumentException exception) {
      return false;
    }
    return MessageDigest.isEqual(actual, expected);
  }

  private record FakeWebhookBody(
      String eventId, String accountReference, Long amountKobo, String status) {}
}
