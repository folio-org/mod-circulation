package org.folio.circulation.services.agedtolost;

import static org.folio.circulation.services.agedtolost.LoanToChargeFees.usingLoan;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.domain.policy.lostitem.LostItemPolicy;
import org.junit.jupiter.api.Test;

import api.support.builders.LostItemFeePolicyBuilder;
import io.vertx.core.json.JsonObject;
import lombok.val;

public class LoanToChargeFeesTest {

  @Test
  public void shouldCloseLoanIfNoFeesToChargeForImmediateBilling() {
    val lostItemPolicy = new LostItemFeePolicyBuilder()
      .billPatronImmediatelyWhenAgedToLost()
      .withNoChargeAmountItem()
      .doNotChargeProcessingFeeWhenAgedToLost();

    assertTrue(loanForLostItemPolicy(lostItemPolicy).shouldCloseLoan());
  }

  @Test
  public void shouldCloseLoanIfNoFeesToChargeForDelayedBilling() {
    val lostItemPolicy = new LostItemFeePolicyBuilder()
      .withPatronBilledAfterItemAgedToLost(Period.minutes(1));

    assertTrue(loanForLostItemPolicy(lostItemPolicy).shouldCloseLoan());
  }

  @Test
  public void shouldNotCloseLoanIfProcessingFeeHasToBeCharged() {
    val lostItemPolicy = new LostItemFeePolicyBuilder()
      .billPatronImmediatelyWhenAgedToLost()
      .chargeProcessingFeeWhenAgedToLost(10.00);

    assertFalse(loanForLostItemPolicy(lostItemPolicy).shouldCloseLoan());
  }

  @Test
  public void shouldNotCloseLoanIfActualCostFeeHasToBeCharged() {
    val lostItemPolicy = new LostItemFeePolicyBuilder()
      .billPatronImmediatelyWhenAgedToLost()
      .withActualCost(11.00);

    assertFalse(loanForLostItemPolicy(lostItemPolicy).shouldCloseLoan());
  }

  @Test
  public void shouldNotCloseLoanIfSetCostFeeHasToBeCharged() {
    val lostItemPolicy = new LostItemFeePolicyBuilder()
      .billPatronImmediatelyWhenAgedToLost()
      .withSetCost(11.00);

    assertFalse(loanForLostItemPolicy(lostItemPolicy).shouldCloseLoan());
  }

  private LoanToChargeFees loanForLostItemPolicy(LostItemFeePolicyBuilder builder) {
    final Loan loan = Loan.from(new JsonObject())
      .withLostItemPolicy(LostItemPolicy.from(builder.create()));

    return usingLoan(loan);
  }
}
