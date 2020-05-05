package org.folio.circulation.support.utils;

import java.math.BigDecimal;

public final class BigDecimalUtil {

  private BigDecimalUtil() {}

  public static BigDecimal roundTaxValue(BigDecimal amount) {
    return amount != null
      ? amount.setScale(2, BigDecimal.ROUND_HALF_UP)
      : null;
  }
}
