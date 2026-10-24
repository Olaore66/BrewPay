package com.brewpay.banking;

/** What the platform asks a bank to do for a payout. Adapters implement this. */
public interface TransferProvider {

  TransferInstruction submit(TransferRequest request);
}
