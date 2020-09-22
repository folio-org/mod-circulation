package api.loans.scenarios;

import org.joda.time.DateTime;

import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.fixtures.AgeToLostFixture;

public class CheckInAgedToLostItemTest extends RefundAgedToLostFeesTestBase {
  @Override
  protected void performActionThatRequiresRefund(AgeToLostFixture.AgeToLostResult result,
    DateTime actionDate) {

    mockClockManagerToReturnFixedDateTime(actionDate);

    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .on(actionDate)
      .at(servicePointsFixture.cd1())
      .forItem(result.getItem()));
  }
}
