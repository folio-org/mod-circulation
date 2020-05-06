package org.folio.circulation.support.utils;

import java.math.BigDecimal;

public final class BigDecimalUtil {

  private BigDecimalUtil() {}

  public static Double toDouble(BigDecimal amount) {
    return amount != null ? amount.doubleValue() : null;
  }

  public static BigDecimal scaleFeeAmount(BigDecimal amount) {
    return amount != null
      ? amount.setScale(2, BigDecimal.ROUND_HALF_UP)
      : null;
  }
}
