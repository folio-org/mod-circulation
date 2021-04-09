package api.loans.scenarios;

import static api.support.utl.BlockOverridesUtils.OVERRIDE_RENEWAL_PERMISSION;
import static api.support.utl.BlockOverridesUtils.buildOkapiHeadersWithPermissions;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;

import api.support.fixtures.AgeToLostFixture;
import api.support.fixtures.OverrideRenewalFixture;
import api.support.http.OkapiHeaders;

public class OverrideRenewAgedToLostItemTest extends RefundAgedToLostFeesTestBase {
  @Autowired
  private OverrideRenewalFixture overrideRenewalFixture;

  public OverrideRenewAgedToLostItemTest() {
    super("Cancelled item renewed");
  }

  @Override
  protected void performActionThatRequiresRefund(AgeToLostFixture.AgeToLostResult result,
     DateTime actionDate) {

    mockClockManagerToReturnFixedDateTime(actionDate);

    final OkapiHeaders okapiHeaders = buildOkapiHeadersWithPermissions(OVERRIDE_RENEWAL_PERMISSION);
    overrideRenewalFixture.overrideRenewalByBarcode(result.getLoan(),
      servicePointsFixture.cd1().getId());
  }
}
