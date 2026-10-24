package com.brewpay.banking;

import java.util.List;
import java.util.UUID;

/** The provider's statement for one organization. Adapters implement this. */
public interface StatementProvider {

  List<StatementLine> fetch(UUID organizationId);
}
