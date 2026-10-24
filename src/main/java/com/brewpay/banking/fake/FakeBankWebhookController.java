package com.brewpay.banking.fake;

import com.brewpay.banking.CollectionProvider;
import com.brewpay.banking.InboundPayment;
import com.brewpay.payments.CollectionService;
import com.brewpay.payments.CollectionWebhookResponse;
import com.brewpay.platform.error.ErrorCode;
import com.brewpay.platform.error.PlatformException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks/fake-bank")
public class FakeBankWebhookController {

  public static final String SIGNATURE = "X-Fake-Bank-Signature";

  private final CollectionProvider provider;
  private final CollectionService collections;

  public FakeBankWebhookController(CollectionProvider provider, CollectionService collections) {
    this.provider = provider;
    this.collections = collections;
  }

  @PostMapping
  public ResponseEntity<CollectionWebhookResponse> receive(
      @RequestHeader(value = SIGNATURE, required = false) String signature,
      HttpServletRequest request) {
    InboundPayment payment = provider.verifyWebhook(readBody(request), signature);
    CollectionWebhookResponse body = collections.accept(payment);
    HttpStatus status = body.collection() == null ? HttpStatus.OK : HttpStatus.CREATED;
    return ResponseEntity.status(status).body(body);
  }

  /** The signature covers these bytes. A JSON converter would not preserve them. */
  private static String readBody(HttpServletRequest request) {
    try {
      byte[] bytes = request.getInputStream().readAllBytes();
      return new String(bytes, StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Webhook could not be read.");
    }
  }
}
