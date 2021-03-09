package api.loans.agetolost;

import static api.support.fakes.FakePubSub.getPublishedEvents;
import static api.support.fakes.PublishedEvents.byEventType;
import static api.support.http.CqlQuery.queryFromTemplate;
import static api.support.matchers.EventMatchers.isValidItemAgedToLostEvent;
import static api.support.matchers.EventTypeMatchers.ITEM_AGED_TO_LOST;
import static api.support.matchers.ItemMatchers.isAgedToLost;
import static api.support.matchers.ItemMatchers.isCheckedOut;
import static api.support.matchers.ItemMatchers.isClaimedReturned;
import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.PubsubPublisherTestUtils.assertThatPublishedLoanLogRecordEventsAreValid;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimePropertyByPath;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.hamcrest.Matchers.not;
import static org.joda.time.DateTime.now;
import static org.joda.time.DateTimeZone.UTC;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.hamcrest.Matcher;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import api.support.MultipleJsonRecords;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.ItemBuilder;
import api.support.http.IndividualResource;
import api.support.spring.SpringApiTest;
import api.support.spring.clients.ScheduledJobClient;
import io.vertx.core.json.JsonObject;
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
  }

  @Test
  public void shouldAgeItemToLostWhenOverdueByMoreThanInterval() {
    checkOutItem();
    scheduledAgeToLostClient.triggerJob();

    assertThat(itemsClient.get(overdueItem).getJson(), isAgedToLost());
    assertThat(getLoanActions(), hasAgedToLostAction());
    assertThat(loansStorageClient.get(overdueLoan).getJson(), hasPatronBillingDate());
    assertThat(loansStorageClient.get(overdueLoan).getJson(), hasAgedToLostDate());
    assertThatPublishedLoanLogRecordEventsAreValid();
    assertThatItemAgedToLostEventWasPublished(overdueLoan);
  }

  @Test
  public void canAgeTenItemsToLostWhenOverdueByMoreThanInterval() {
    val loanToItemMap = checkOutTenItems();

    scheduledAgeToLostClient.triggerJob();

    loanToItemMap.forEach((loan, item) -> {
      val itemFromStorage = itemsClient.get(item);
      val loanFromStorage = loansStorageClient.get(loan);

      assertThat(itemFromStorage.getJson(), isAgedToLost());
      assertThat(getLoanActions(loanFromStorage), hasAgedToLostAction());
      assertThat(loanFromStorage.getJson(), hasPatronBillingDate(loanFromStorage));
      assertThat(loanFromStorage.getJson(), hasAgedToLostDate());
    });
    assertThatPublishedLoanLogRecordEventsAreValid();
    assertThatItemAgedToLostEventsWerePublished(loanToItemMap.keySet());
  }

  @Test
  public void shouldIgnoreOverdueLoansWhenItemIsClaimedReturned() {
    checkOutItem();
    claimItemReturnedFixture.claimItemReturned(overdueLoan.getId());

    scheduledAgeToLostClient.triggerJob();

    assertThat(itemsClient.get(overdueItem).getJson(), isClaimedReturned());
    assertThat(loansClient.get(overdueLoan).getJson(), not(hasPatronBillingDate()));
  }

  @Test
  public void shouldNotAgeAnyItemsToLostIfNoIntervalDefined() {
    val policy = lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
      .withName("Aged to lost disabled")
      .withItemAgedToLostAfterOverdue(null);

    useLostItemPolicy(lostItemFeePoliciesFixture.create(policy).getId());

    checkOutItem();

    scheduledAgeToLostClient.triggerJob();

    assertThat(itemsClient.get(overdueItem).getJson(), isCheckedOut());
    assertThat(loansClient.get(overdueLoan).getJson(), not(hasPatronBillingDate()));
    assertThatPublishedLoanLogRecordEventsAreValid();
  }

  @Test
  public void shouldNotAgeItemToLostWhenNotOverdueByMoreThanIntervalYet() {
    overdueItem = itemsFixture.basedUponNod();
    overdueLoan = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(overdueItem)
        .at(servicePointsFixture.cd1())
        .to(usersFixture.james())
        .on(now(UTC)));

    scheduledAgeToLostClient.triggerJob();

    assertThat(itemsClient.get(overdueItem).getJson(), isCheckedOut());
    assertThat(loansClient.get(overdueLoan).getJson(), not(hasPatronBillingDate()));
    assertThatPublishedLoanLogRecordEventsAreValid();
  }

  @Test
  public void shouldNotProcessAgedToLostItemSecondTime() {
    checkOutItem();
    scheduledAgeToLostClient.triggerJob();

    mockClockManagerToReturnFixedDateTime(DateTime.now(UTC).plusMinutes(30));
    scheduledAgeToLostClient.triggerJob();
    mockClockManagerToReturnDefaultDateTime();

    assertThat(itemsClient.get(overdueItem).getJson(), isAgedToLost());
    assertThat(getLoanActions(), hasAgedToLostAction());
    assertThat(loansStorageClient.get(overdueLoan).getJson(), hasPatronBillingDate());

    val agedToLostActions = getLoanActions().stream()
      .filter(json -> "itemAgedToLost".equals(json.getJsonObject("loan").getString("action")))
      .collect(Collectors.toList());

    assertThat(agedToLostActions, iterableWithSize(1));
    assertThatPublishedLoanLogRecordEventsAreValid();
  }

  private DateTime getLoanOverdueDate() {
    return now(UTC).minusWeeks(3);
  }

  private void checkOutItem() {
    overdueItem = itemsFixture.basedUponNod(ItemBuilder::withRandomBarcode);

    overdueLoan = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(overdueItem)
        .at(servicePointsFixture.cd1())
        .to(usersFixture.charlotte())
        .on(getLoanOverdueDate().minusMinutes(2)));
  }

  private Map<IndividualResource, IndividualResource> checkOutTenItems() {
    val numberOfItems = 10;
    val loanToItemMap = new HashMap<IndividualResource, IndividualResource>();

    for (int i = 0; i < numberOfItems; i++) {
      checkOutItem();

      loanToItemMap.put(overdueLoan, overdueItem);
    }

    return loanToItemMap;
  }

  private MultipleJsonRecords getLoanActions(IndividualResource loan) {
    return loanHistoryClient
      .getMany(queryFromTemplate("loan.id==%s and operation==U", loan.getId()));
  }

  private MultipleJsonRecords getLoanActions() {
    return getLoanActions(overdueLoan);
  }

  private Matcher<Iterable<? super JsonObject>> hasAgedToLostAction() {
    return hasItem(allOf(
      hasJsonPath("loan.status.name", "Open"),
      hasJsonPath("loan.action", "itemAgedToLost"),
      hasJsonPath("loan.itemStatus", "Aged to lost")
    ));
  }

  private Matcher<JsonObject> hasPatronBillingDate() {
    return hasPatronBillingDate(overdueLoan);
  }

  private Matcher<JsonObject> hasPatronBillingDate(IndividualResource loan) {
    final IndividualResource loanFromStorage = loansStorageClient.get(loan);
    final DateTime agedToLostDate = getDateTimePropertyByPath(loanFromStorage.getJson(),
      "agedToLostDelayedBilling", "agedToLostDate");

    val expectedBillingDate = agedToLostDate != null
      // bill patron after age to lost interval, per default policy
      ? agedToLostDate.plusMinutes(5)
      : null;

    return allOf(hasJsonPath("agedToLostDelayedBilling.lostItemHasBeenBilled", false),
      hasJsonPath("agedToLostDelayedBilling.dateLostItemShouldBeBilled",
        isEquivalentTo(expectedBillingDate)));
  }

  private Matcher<JsonObject> hasAgedToLostDate() {
    return hasJsonPath("agedToLostDelayedBilling.agedToLostDate", notNullValue());
  }

  private void assertThatItemAgedToLostEventWasPublished(IndividualResource loan) {
    assertThatItemAgedToLostEventsWerePublished(List.of(loan));
  }

  private static void assertThatItemAgedToLostEventsWerePublished(
    Collection<IndividualResource> loans) {

    List<JsonObject> itemAgedToLostEvents = getPublishedEvents()
      .filterToList(byEventType(ITEM_AGED_TO_LOST));

    assertThat(itemAgedToLostEvents, hasSize(loans.size()));

    for (IndividualResource loan : loans) {
      assertThat(itemAgedToLostEvents, hasItem(isValidItemAgedToLostEvent(loan.getJson())));
    }
  }
}
