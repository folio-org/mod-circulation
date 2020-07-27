package api.loans.agetolost;

import static api.support.matchers.ItemMatchers.isAgedToLost;
import static api.support.matchers.ItemMatchers.isCheckedOut;
import static api.support.matchers.ItemMatchers.isClaimedReturned;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.joda.time.DateTime.now;
import static org.joda.time.DateTimeZone.UTC;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.folio.circulation.domain.TimePeriod;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;

import api.support.APITests;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.LostItemFeePolicyBuilder;
import api.support.spring.TestSpringConfiguration;
import api.support.spring.clients.ScheduledJobClient;

@RunWith(SpringRunner.class)
@Import(TestSpringConfiguration.class)
public class ScheduledAgeToLostApiTest extends APITests {
  private static final TimePeriod ONE_MINUTE_PERIOD = new TimePeriod(1, "Minutes");

  private IndividualResource overdueLoan;
  private IndividualResource overdueItem;
  private ScheduledJobClient scheduledAgeToLostClient;

  @Before
  public void activateLostItemFeePolicy() {
    useAgeToLostAfterOneMinutePolicy();

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
    final LostItemFeePolicyBuilder policy = lostItemFeePoliciesFixture.facultyStandardPolicy()
      .withItemAgedToLostAfterOverdue(null);
    useLostItemPolicy(lostItemFeePoliciesFixture.create(policy).getId());

    checkOutItem();

    scheduledAgeToLostClient.triggerJob();

    assertThat(itemsClient.get(overdueItem).getJson(), isCheckedOut());
  }

  @Test
  public void shouldNotAgeToLostIfIntervalIsNotExceededYet() {
    useAgeToLostAfterOneMinutePolicy();

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
  }

  @Test
  public void canAgeSeveralLoanToLostAfterIntervalExceeding() {
    final List<IndividualResource> allItems = checkOutItems(10);

    scheduledAgeToLostClient.triggerJob();

    allItems.forEach(
      item -> assertThat(itemsClient.get(item).getJson(), isAgedToLost()));
  }

  private DateTime getLoanOverdueDate() {
    return now(UTC).minusWeeks(3);
  }

  private void checkOutItem() {
    overdueItem = itemsFixture.basedUponNod(
      itemBuilder -> itemBuilder.withBarcode(String.valueOf(new Random().nextLong())));

    overdueLoan = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(overdueItem)
        .at(servicePointsFixture.cd1())
        .to(usersFixture.charlotte())
        .on(getLoanOverdueDate().minusMinutes(2)));
  }

  private List<IndividualResource> checkOutItems(int numberOfItems) {
    final List<IndividualResource> items = new ArrayList<>();

    for (int i = 0; i < numberOfItems; i++) {
      checkOutItem();
      items.add(overdueItem);
    }

    return items;
  }

  private void useAgeToLostAfterOneMinutePolicy() {
    final LostItemFeePolicyBuilder policy = lostItemFeePoliciesFixture.facultyStandardPolicy()
      .withItemAgedToLostAfterOverdue(ONE_MINUTE_PERIOD);

    useLostItemPolicy(lostItemFeePoliciesFixture.create(policy).getId());
  }
}
