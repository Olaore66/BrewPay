package com.brewpay.banking.fake;

import com.brewpay.banking.KycApplication;
import com.brewpay.banking.KycProvider;
import com.brewpay.banking.KycVerdict;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/**
 * Local stand-in for business verification. The next verdict can be scripted by a test. A merchant
 * request cannot choose it. An empty script approves.
 */
@Service
public class FakeKycProvider implements KycProvider {

  private final ConcurrentLinkedQueue<KycVerdict> script = new ConcurrentLinkedQueue<>();
  private final AtomicInteger reviews = new AtomicInteger();
  private volatile KycApplication last;

  public void pendingNext() {
    script.add(KycVerdict.PENDING);
  }

  public void rejectNext() {
    script.add(KycVerdict.REJECTED);
  }

  public void clear() {
    script.clear();
    reviews.set(0);
    last = null;
  }

  public int reviewCount() {
    return reviews.get();
  }

  public KycApplication lastApplication() {
    return last;
  }

  @Override
  public KycVerdict review(KycApplication application) {
    reviews.incrementAndGet();
    last = application;
    KycVerdict scripted = script.poll();
    return scripted == null ? KycVerdict.APPROVED : scripted;
  }
}
