package com.brewpay.banking.fake;

import com.brewpay.banking.NameEnquiryProvider;
import com.brewpay.banking.ProviderTimeoutException;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/**
 * Local stand-in for a name enquiry. The next answer can be scripted by a test. A merchant request
 * cannot choose it. An empty script answers {@code Fake Account}.
 */
@Service
public class FakeNameEnquiryProvider implements NameEnquiryProvider {

  private static final String DEFAULT_NAME = "Fake Account";

  private final ConcurrentLinkedQueue<Scripted> script = new ConcurrentLinkedQueue<>();
  private final AtomicInteger enquiries = new AtomicInteger();

  public void answerNext(String accountName) {
    script.add(new Scripted(Outcome.ANSWER, accountName));
  }

  public void missNext() {
    script.add(new Scripted(Outcome.MISSING, null));
  }

  public void timeoutNext() {
    script.add(new Scripted(Outcome.TIMEOUT, null));
  }

  public void clear() {
    script.clear();
    enquiries.set(0);
  }

  public int enquireCount() {
    return enquiries.get();
  }

  @Override
  public Optional<String> enquire(String destinationAccountNumber, String destinationBankCode) {
    enquiries.incrementAndGet();
    Scripted scripted = script.poll();
    if (scripted == null) {
      return Optional.of(DEFAULT_NAME);
    }
    return switch (scripted.outcome()) {
      case TIMEOUT -> throw new ProviderTimeoutException();
      case MISSING -> Optional.empty();
      case ANSWER -> Optional.ofNullable(scripted.name());
    };
  }

  private enum Outcome {
    ANSWER,
    MISSING,
    TIMEOUT
  }

  private record Scripted(Outcome outcome, String name) {}
}
