package org.folio.circulation.domain;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getBigDecimalProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;

import io.vertx.core.json.JsonObject;

public final class FeeAmount {
  private static final FeeAmount ZERO = new FeeAmount(BigDecimal.ZERO);

  private final BigDecimal amount;

  public FeeAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public FeeAmount(double amount) {
    this(BigDecimal.valueOf(amount));
  }

  public final FeeAmount subtract(FeeAmount toSubtract) {
    return new FeeAmount(amount.subtract(toSubtract.amount));
  }

  public final FeeAmount add(FeeAmount toAdd) {
    return new FeeAmount(amount.add(toAdd.amount));
  }

  public final double toDouble() {
    return scaledAmount().doubleValue();
  }

  public boolean hasAmount() {
    return amount.compareTo(BigDecimal.ZERO) > 0;
  }

  public boolean hasZeroAmount() {
    return amount.compareTo(BigDecimal.ZERO) == 0;
  }

  private BigDecimal scaledAmount() {
    return amount.setScale(2, RoundingMode.HALF_UP);
  }

  public String toScaledString() {
    return scaledAmount().toString();
  }

  public static FeeAmount noFeeAmount() {
    return zeroFeeAmount();
  }

  public static FeeAmount zeroFeeAmount() {
    return ZERO;
  }

  public static FeeAmount from(JsonObject json, String propertyName) {
    return new FeeAmount(getBigDecimalProperty(json, propertyName));
  }
}
