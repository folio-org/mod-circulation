package org.folio.circulation.domain.policy;

public class Charge {

  public final static String ACTUAL_COST = "Actual Cost";
  private final String chargeType;
  private final Double amount;

  private Charge(String chargeType, Double amount) {
    this.chargeType = chargeType;
    this.amount = amount;
  }
  
  public static Charge from(String chargeType, Double amount) {
    return new Charge(chargeType, amount);
  }

  public String getChargeType() {
    return chargeType;
  }

  public Double getAmount() {
    return amount;
  }

}
