package api.loans.scenarios;

import static api.support.matchers.ItemMatchers.isCheckedOut;
import static org.hamcrest.MatcherAssert.assertThat;

import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import api.support.fixtures.OverrideRenewalFixture;
import api.support.spring.TestSpringConfiguration;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestSpringConfiguration.class)
public class OverrideRenewDeclaredLostItemTest extends RefundLostItemFeesTestBase {
  @Autowired
  private OverrideRenewalFixture overrideRenewalFixture;

  @Override
  protected void performActionThatRequiresRefund() {
    overrideRenewalFixture.overrideRenewalByBarcode(loan, servicePointsFixture.cd1().getId());
  }

  @Override
  protected void performActionThatRequiresRefund(DateTime actionDate) {
    mockClockManagerToReturnFixedDateTime(actionDate);
    performActionThatRequiresRefund();
  }

  @Before
  public void setupLoanType() {
    noteTypeFixture.generalNoteType();
  }

  @After
  public void cleanUp() {
    notesClient.deleteAll();
    noteTypeClient.deleteAll();
  }

  @Test
  public void lostFeeCancellationDoesNotTriggerMarkingItemAsLostAndPaid() {
    useChargeableRefundableLostItemFee(15.00, 0.0);

    declareItemLost();

    performActionThatRequiresRefund();
    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(itemsClient.getById(item.getId()).getJson(), isCheckedOut());
  }
}
