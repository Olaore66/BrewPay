package com.brewpay.banking.fake;

import com.brewpay.banking.ProviderPaymentStatus;
import com.brewpay.banking.ProviderTimeoutException;
import com.brewpay.banking.TransferInstruction;
import com.brewpay.banking.TransferProvider;
import com.brewpay.banking.TransferRequest;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/**
 * Local stand-in for a payout provider. The next answer can be scripted by a test. A merchant
 * request cannot choose it. An empty script answers SUCCESSFUL.
 */
@Service
public class FakeTransferProvider implements TransferProvider {

  private final ConcurrentLinkedQueue<ProviderPaymentStatus> script = new ConcurrentLinkedQueue<>();
  private final AtomicInteger submits = new AtomicInteger();

  public void timeoutNextSubmit() {
    script.add(ProviderPaymentStatus.UNKNOWN);
  }

  public void failNextSubmit() {
    script.add(ProviderPaymentStatus.FAILED);
  }

  public void clearScript() {
    script.clear();
    submits.set(0);
  }

  public int submitCount() {
    return submits.get();
  }

  @Override
  public TransferInstruction submit(TransferRequest request) {
    submits.incrementAndGet();
    ProviderPaymentStatus scripted = script.poll();
    if (scripted == ProviderPaymentStatus.UNKNOWN) {
      throw new ProviderTimeoutException();
    }
    if (scripted == ProviderPaymentStatus.FAILED) {
      return new TransferInstruction(
          "FAKE-XFER-" + UUID.randomUUID(), ProviderPaymentStatus.FAILED);
    }
    return new TransferInstruction(
        "FAKE-XFER-" + UUID.randomUUID(), ProviderPaymentStatus.SUCCESSFUL);
  }
}
