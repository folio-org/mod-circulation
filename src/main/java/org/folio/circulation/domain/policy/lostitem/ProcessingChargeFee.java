package org.folio.circulation.domain.policy.lostitem;

import java.math.BigDecimal;

public final class ProcessingChargeFee extends ChargeFee {
  public ProcessingChargeFee(BigDecimal amount) {
    super(amount);
  }
}
