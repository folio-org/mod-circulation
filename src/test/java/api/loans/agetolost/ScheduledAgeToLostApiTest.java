package api.loans.agetolost;

import static api.support.PubsubPublisherTestUtils.assertThatPublishedLoanLogRecordEventsAreValid;
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
import static java.time.Clock.fixed;
import static java.time.ZoneOffset.UTC;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimePropertyByPath;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.hamcrest.Matchers.not;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.folio.circulation.domain.policy.lostitem.ChargeAmountType;
import org.folio.circulation.support.utils.ClockUtil;
import org.hamcrest.Matcher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import api.support.MultipleJsonRecords;
import api.support.PubsubPublisherTestUtils;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.ItemBuilder;
import api.support.http.IndividualResource;
import api.support.spring.SpringApiTest;
import api.support.spring.clients.ScheduledJobClient;
import io.vertx.core.json.JsonObject;
import lombok.val;

class ScheduledAgeToLostApiTest extends SpringApiTest {
  private IndividualResource overdueLoan;
  private IndividualResource overdueItem;
  @Autowired
  private ScheduledJobClient scheduledAgeToLostClient;

  public ScheduledAgeToLostApiTest() {
    super(true, true);
  }

  @ParameterizedTest
  @EnumSource(value = ChargeAmountType.class)
  void shouldAgeItemToLostWhenOverdueByMoreThanInterval(ChargeAmountType costType) {
    initLostItemFeePolicy(costType);
    checkOutItem();
    scheduledAgeToLostClient.triggerJob();

    assertThat(itemsClient.get(overdueItem).getJson(), isAgedToLost());
    assertThat(getLoanActions(), hasAgedToLostAction());
    assertThat(loansStorageClient.get(overdueLoan).getJson(), hasPatronBillingDate());
    assertThat(loansStorageClient.get(overdueLoan).getJson(), hasAgedToLostDate());

    assertThatPublishedLoanLogRecordEventsAreValid(overdueLoan.getJson());
    assertThatItemAgedToLostEventWasPublished(overdueLoan);
  }

  @ParameterizedTest
  @EnumSource(value = ChargeAmountType.class)
  void shouldAgeRecalledItemToLostWhenOverdueByMoreThanInterval(ChargeAmountType costType) {
    initLostItemFeePolicy(costType);
    checkOutItem();
    requestsFixture.recallItem(overdueItem, usersFixture.jessica());
    ClockUtil.setClock(fixed(getZonedDateTime().plusMonths(7).toInstant(), UTC));
    scheduledAgeToLostClient.triggerJob();

    assertThat(itemsClient.get(overdueItem).getJson(), isAgedToLost());
    assertThat(getLoanActions(), hasAgedToLostAction());
    assertThat(loansStorageClient.get(overdueLoan).getJson(), hasPatronBillingDate());
    assertThat(loansStorageClient.get(overdueLoan).getJson(), hasAgedToLostDate());

    assertThatPublishedLoanLogRecordEventsAreValid(overdueLoan.getJson());
    assertThatItemAgedToLostEventWasPublished(overdueLoan);
  }

  @ParameterizedTest
  @EnumSource(value = ChargeAmountType.class)
  void canAgeTenItemsToLostWhenOverdueByMoreThanInterval(ChargeAmountType costType) {
    initLostItemFeePolicy(costType);
    val loanToItemMap = checkOutTenItems();

    scheduledAgeToLostClient.triggerJob();

    loanToItemMap.forEach((loan, item) -> {
      val itemFromStorage = itemsClient.get(item);
      val loanFromStorage = loansStorageClient.get(loan);

      assertThat(itemFromStorage.getJson(), isAgedToLost());
      assertThat(getLoanActions(loanFromStorage), hasAgedToLostAction());
      assertThat(loanFromStorage.getJson(), hasPatronBillingDate(loanFromStorage));
      assertThat(loanFromStorage.getJson(), hasAgedToLostDate());
      assertThatPublishedLoanLogRecordEventsAreValid(loansClient.getById(loan.getId()).getJson());
    });
    assertThatItemAgedToLostEventsWerePublished(loanToItemMap.keySet());
  }

  @ParameterizedTest
  @EnumSource(value = ChargeAmountType.class)
  void shouldIgnoreOverdueLoansWhenItemIsClaimedReturned(ChargeAmountType costType) {
    initLostItemFeePolicy(costType);
    checkOutItem();
    claimItemReturnedFixture.claimItemReturned(overdueLoan.getId());

    scheduledAgeToLostClient.triggerJob();

    assertThat(itemsClient.get(overdueItem).getJson(), isClaimedReturned());
    assertThat(loansClient.get(overdueLoan).getJson(), not(hasPatronBillingDate()));
  }

  @ParameterizedTest
  @EnumSource(value = ChargeAmountType.class)
  void shouldNotAgeAnyItemsToLostIfNoIntervalDefined(ChargeAmountType costType) {
    initLostItemFeePolicy(costType);
    val policy = lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
      .withName("Aged to lost disabled")
      .withItemAgedToLostAfterOverdue(null);

    useLostItemPolicy(lostItemFeePoliciesFixture.create(policy).getId());

    checkOutItem();

    scheduledAgeToLostClient.triggerJob();

    assertThat(itemsClient.get(overdueItem).getJson(), isCheckedOut());

    var loan = loansClient.get(overdueLoan).getJson();
    assertThat(loan, not(hasPatronBillingDate()));
    assertThatPublishedLoanLogRecordEventsAreValid(loan);
  }

  @ParameterizedTest
  @EnumSource(value = ChargeAmountType.class)
  void shouldNotAgeItemToLostWhenNotOverdueByMoreThanIntervalYet(ChargeAmountType costType) {
    initLostItemFeePolicy(costType);
    overdueItem = itemsFixture.basedUponNod();
    overdueLoan = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(overdueItem)
        .at(servicePointsFixture.cd1())
        .to(usersFixture.james())
        .on(ClockUtil.getZonedDateTime()));

    scheduledAgeToLostClient.triggerJob();

    assertThat(itemsClient.get(overdueItem).getJson(), isCheckedOut());

    var loan = loansClient.get(overdueLoan).getJson();
    assertThat(loan, not(hasPatronBillingDate()));
    assertThatPublishedLoanLogRecordEventsAreValid(loan);
  }

  @ParameterizedTest
  @EnumSource(value = ChargeAmountType.class)
  void shouldNotProcessAgedToLostItemSecondTime(ChargeAmountType costType) {
    initLostItemFeePolicy(costType);
    checkOutItem();
    scheduledAgeToLostClient.triggerJob();

    mockClockManagerToReturnFixedDateTime(ClockUtil.getZonedDateTime().plusMinutes(30));
    scheduledAgeToLostClient.triggerJob();
    mockClockManagerToReturnDefaultDateTime();

    assertThat(itemsClient.get(overdueItem).getJson(), isAgedToLost());
    assertThat(getLoanActions(), hasAgedToLostAction());
    assertThat(loansStorageClient.get(overdueLoan).getJson(), hasPatronBillingDate());

    val agedToLostActions = getLoanActions().stream()
      .filter(json -> "itemAgedToLost".equals(json.getJsonObject("loan").getString("action")))
      .collect(Collectors.toList());

    assertThat(agedToLostActions, iterableWithSize(1));
    agedToLostActions.forEach(PubsubPublisherTestUtils::assertThatPublishedLoanLogRecordEventsAreValid);
  }

  private ZonedDateTime getLoanOverdueDate() {
    return ClockUtil.getZonedDateTime().minusWeeks(3);
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
    final ZonedDateTime agedToLostDate = getDateTimePropertyByPath(loanFromStorage.getJson(),
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

  private void initLostItemFeePolicy(ChargeAmountType costType) {
    useLostItemPolicy(lostItemFeePoliciesFixture.ageToLostAfterOneMinute(costType).getId());
  }
}
