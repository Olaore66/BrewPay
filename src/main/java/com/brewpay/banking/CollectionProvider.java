package com.brewpay.banking;

/** What the platform asks a bank to do for collections. Adapters implement this. */
public interface CollectionProvider {

  String provisionReference();

  InboundPayment verifyWebhook(String rawBody, String signature);
}
