package api.loans.scenarios;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;

import api.support.fixtures.AgeToLostFixture;
import api.support.fixtures.OverrideRenewalFixture;

class OverrideRenewAgedToLostItemTest extends RefundAgedToLostFeesTestBase {
  @Autowired
  private OverrideRenewalFixture overrideRenewalFixture;

  public OverrideRenewAgedToLostItemTest() {
    super("Cancelled item renewed");
  }

  @Override
  protected void performActionThatRequiresRefund(AgeToLostFixture.AgeToLostResult result,
     DateTime actionDate) {

    mockClockManagerToReturnFixedDateTime(actionDate);

    overrideRenewalFixture.overrideRenewalByBarcode(result.getLoan(),
      servicePointsFixture.cd1().getId());
  }
}
