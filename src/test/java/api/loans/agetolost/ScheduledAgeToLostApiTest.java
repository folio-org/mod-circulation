package api.loans.agetolost;

import static api.support.http.CqlQuery.queryFromTemplate;
import static api.support.matchers.ItemMatchers.isAgedToLost;
import static api.support.matchers.ItemMatchers.isCheckedOut;
import static api.support.matchers.ItemMatchers.isClaimedReturned;
import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.joda.time.DateTime.now;
import static org.joda.time.DateTimeZone.UTC;

import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import api.support.MultipleJsonRecords;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.spring.SpringApiTest;
import api.support.spring.clients.ScheduledJobClient;
import lombok.val;

public class ScheduledAgeToLostApiTest extends SpringApiTest {
  private IndividualResource overdueLoan;
  private IndividualResource overdueItem;
  @Autowired
  private ScheduledJobClient scheduledAgeToLostClient;

  public ScheduledAgeToLostApiTest() {
    super(true, true);
  }

  @Before
  public void activateLostItemFeePolicy() {
    useLostItemPolicy(lostItemFeePoliciesFixture.ageToLostAfterOneMinute().getId());

    checkOutItem();
  }

  @Test
  public void shouldSkipOverdueClaimReturnedLoans() {
    claimItemReturnedFixture.claimItemReturned(overdueLoan.getId());

    scheduledAgeToLostClient.triggerJob();

    assertThat(itemsClient.get(overdueItem).getJson(), isClaimedReturned());
  }

  @Test
  public void shouldNotAgeToLostIfIntervalIsNull() {
    val policy = lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
      .withName("Aged to lost disabled")
      .withItemAgedToLostAfterOverdue(null);
    useLostItemPolicy(lostItemFeePoliciesFixture.create(policy).getId());

    checkOutItem();

    scheduledAgeToLostClient.triggerJob();

    assertThat(itemsClient.get(overdueItem).getJson(), isCheckedOut());
  }

  @Test
  public void shouldNotAgeToLostIfIntervalIsNotExceededYet() {
    overdueItem = itemsFixture.basedUponNod();
    overdueLoan = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(overdueItem)
        .at(servicePointsFixture.cd1())
        .to(usersFixture.james())
        .on(now(UTC)));

    scheduledAgeToLostClient.triggerJob();

    assertThat(itemsClient.get(overdueItem).getJson(), isCheckedOut());
  }

  @Test
  public void shouldAgeLoanToLostAfterIntervalExceeding() {
    scheduledAgeToLostClient.triggerJob();

    assertThat(itemsClient.get(overdueItem).getJson(), isAgedToLost());
    assertThat(getLoanActions(), hasItem(allOf(
      hasJsonPath("loan.status.name", "Open"),
      hasJsonPath("loan.itemStatus", "Aged to lost")
    )));
  }

  private DateTime getLoanOverdueDate() {
    return now(UTC).minusWeeks(3);
  }

  private void checkOutItem() {
    overdueItem = itemsFixture.basedUponNod(
      builder -> builder.withBarcode(generateString()));

    overdueLoan = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(overdueItem)
        .at(servicePointsFixture.cd1())
        .to(usersFixture.charlotte())
        .on(getLoanOverdueDate().minusMinutes(2)));
  }

  private MultipleJsonRecords getLoanActions() {
    return loanHistoryClient
      .getMany(queryFromTemplate("loan.id==%s and operation==U", overdueLoan.getId()));
  }
}
