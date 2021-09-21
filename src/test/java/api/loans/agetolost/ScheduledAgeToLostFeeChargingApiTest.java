package api.loans.agetolost;

import static api.support.PubsubPublisherTestUtils.assertThatPublishedLoanLogRecordEventsAreValid;
import static api.support.builders.DeclareItemLostRequestBuilder.forLoan;
import static api.support.matchers.AccountMatchers.isOpen;
import static api.support.matchers.ItemMatchers.isAgedToLost;
import static api.support.matchers.ItemMatchers.isDeclaredLost;
import static api.support.matchers.ItemMatchers.isLostAndPaid;
import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static api.support.matchers.JsonObjectMatcher.hasNoJsonPath;
import static api.support.matchers.LoanAccountActionsMatcher.hasLostItemFeeCreatedBySystemAction;
import static api.support.matchers.LoanAccountActionsMatcher.hasLostItemProcessingFeeCreatedBySystemAction;
import static api.support.matchers.LoanAccountMatcher.hasLostItemFee;
import static api.support.matchers.LoanAccountMatcher.hasLostItemFees;
import static api.support.matchers.LoanAccountMatcher.hasLostItemProcessingFee;
import static api.support.matchers.LoanAccountMatcher.hasNoLostItemFee;
import static api.support.matchers.LoanAccountMatcher.hasNoLostItemProcessingFee;
import static api.support.matchers.LoanAccountMatcher.hasNoOverdueFine;
import static api.support.matchers.LoanHistoryMatcher.hasLoanHistoryInOrder;
import static api.support.matchers.LoanMatchers.isClosed;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static java.lang.Boolean.TRUE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.iterableWithSize;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.utils.ClockUtil;
import org.hamcrest.Matcher;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import api.support.builders.FeeFineOwnerBuilder;
import api.support.builders.ItemBuilder;
import api.support.builders.LostItemFeePolicyBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.builders.ServicePointBuilder;
import api.support.http.CheckOutResource;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.matchers.LoanMatchers;
import api.support.spring.SpringApiTest;
import io.vertx.core.json.JsonObject;
import lombok.val;

class ScheduledAgeToLostFeeChargingApiTest extends SpringApiTest {

  public ScheduledAgeToLostFeeChargingApiTest() {
    super(true, true);
  }

  @BeforeEach
  public void createOwnersAndFeeFineTypes() {
    feeFineOwnerFixture.cd1Owner();
    feeFineTypeFixture.lostItemFee();
    feeFineTypeFixture.lostItemProcessingFee();
  }

  @Test
  void shouldChargeItemFee() {
    final double expectedSetCost = 12.88;
    final IndividualResource permanentLocation = locationsFixture.fourthFloor();
    final UUID ownerServicePoint = servicePointsFixture.cd6().getId();
    final String expectedOwnerId = feeFineOwnerFixture.ownerForServicePoint(ownerServicePoint)
    .getId().toString();

    val lostItemFeePolicy = lostItemFeePoliciesFixture
      .ageToLostAfterOneMinutePolicy()
      .withSetCost(expectedSetCost)
      .doNotChargeProcessingFeeWhenAgedToLost();
    val noticePolicy = createNoticePolicyWithAgedToLostChargedNotice();
    val result = ageToLostFixture.createLoanAgeToLostAndChargeFeesWithNotice(
        builder -> builder.withPermanentLocation(permanentLocation), lostItemFeePolicy,
        noticePolicy);

    assertThat(result.getLoan().getJson(), isLostItemHasBeenBilled());
    assertThat(result.getLoan(), hasLostItemFee(allOf(isOpen(expectedSetCost),
      hasJsonPath("ownerId", expectedOwnerId))));
    assertThat(result.getLoan(), hasLostItemFeeCreatedBySystemAction());

    assertThat(result.getLoan(), hasLoanHistoryInOrder(
      hasJsonPath("loan.action", ""),
      hasJsonPath("loan.action", "itemAgedToLost"),
      hasJsonPath("loan.action", "checkedout")
    ));
    assertThat(scheduledNoticesClient.getAll(), hasSize(1));
    assertThatPublishedLoanLogRecordEventsAreValid(loansClient.getById(result.getLoan().getId()).getJson());
  }

  @Test
  void shouldChargeItemProcessingFee() {
    final double expectedProcessingFee = 99.54;

    val lostItemFeePolicy = new LostItemFeePolicyBuilder()
      .withName("shouldChargeItemProcessingFee")
      .withItemAgedToLostAfterOverdue(Period.weeks(1))
      .withPatronBilledAfterItemAgedToLost(Period.weeks(2))
      .withNoChargeAmountItem()
      .doNotChargeProcessingFeeWhenDeclaredLost()
      .chargeProcessingFeeWhenAgedToLost(expectedProcessingFee);
    val noticePolicy = createNoticePolicyWithAgedToLostChargedNotice();
    val result = ageToLostFixture.createLoanAgeToLostAndChargeFeesWithNotice(
      lostItemFeePolicy, noticePolicy);

    assertThat(result.getLoan().getJson(), isLostItemHasBeenBilled());
    assertThat(result.getLoan(), hasLostItemProcessingFee(isOpen(expectedProcessingFee)));
    assertThat(result.getLoan(), hasLostItemProcessingFeeCreatedBySystemAction());
    assertThat(scheduledNoticesClient.getAll(), hasSize(1));
    assertThatPublishedLoanLogRecordEventsAreValid(loansClient.getById(result.getLoan().getId()).getJson());
  }

  @Test
  void shouldChargeItemAndProcessingFee() {
    final double expectedItemFee = 99.54;
    final double expectedProcessingFee = 99.54;

    val lostItemFeePolicy = lostItemFeePoliciesFixture
      .ageToLostAfterOneMinutePolicy()
      .withSetCost(expectedItemFee)
      .chargeProcessingFeeWhenAgedToLost(expectedProcessingFee);

    val noticePolicy = createNoticePolicyWithAgedToLostChargedNotice();
    val result = ageToLostFixture.createLoanAgeToLostAndChargeFeesWithNotice(
      lostItemFeePolicy, noticePolicy);

    assertThat(result.getLoan().getJson(), isLostItemHasBeenBilled());

    String contributorName = itemsFixture.basedUponSmallAngryPlanet().getInstance().getJson()
      .getJsonArray("contributors")
      .getJsonObject(0)
      .getString("name");

    assertThat(result.getLoan(), hasLostItemFee(allOf(
      isOpen(expectedItemFee),
      hasJsonPath("contributors[0].name", contributorName))));
    assertThat(result.getLoan(), hasLostItemFeeCreatedBySystemAction());

    assertThat(result.getLoan(), hasLostItemProcessingFee(allOf(
      isOpen(expectedProcessingFee),
      hasJsonPath("contributors[0].name", contributorName))));
    assertThat(result.getLoan(), hasLostItemProcessingFeeCreatedBySystemAction());
    assertThat(scheduledNoticesClient.getAll(), hasSize(2));
    assertThatPublishedLoanLogRecordEventsAreValid(loansClient.getById(result.getLoan().getId()).getJson());
  }

  @Test
  void shouldNotChargeFeesIfStatedByPolicy() {
    val lostItemFeePolicy = lostItemFeePoliciesFixture
      .ageToLostAfterOneMinutePolicy()
      .withNoChargeAmountItem()
      .doNotChargeProcessingFeeWhenAgedToLost();

    val noticePolicy = createNoticePolicyWithAgedToLostChargedNotice();
    val result = ageToLostFixture.createLoanAgeToLostAndChargeFeesWithNotice(
      lostItemFeePolicy, noticePolicy);

    assertThat(result.getLoan().getJson(), isLostItemHasBeenBilled());

    assertThat(result.getLoan(), hasNoLostItemFee());
    assertThat(result.getLoan(), hasNoLostItemProcessingFee());
    assertThat(scheduledNoticesClient.getAll(), hasSize(0));
    assertThatPublishedLoanLogRecordEventsAreValid(loansClient.getById(result.getLoan().getId()).getJson());
  }

  @Test
  void shouldSkipChargingFeesWhenActualCostFeeShouldBeIssued() {
    val lostItemFeePolicy = lostItemFeePoliciesFixture
      .ageToLostAfterOneMinutePolicy()
      .withSetCost(10.00)
      .doNotChargeProcessingFeeWhenAgedToLost();
    val noticePolicy = createNoticePolicyWithAgedToLostChargedNotice();
    val result = ageToLostFixture.createLoanAgeToLostAndChargeFeesWithNotice(
      lostItemFeePolicy, noticePolicy);

    assertThat(result.getLoan().getJson(), isLostItemHasBeenBilled());
    assertThat(scheduledNoticesClient.getAll(), hasSize(1));
    assertThatPublishedLoanLogRecordEventsAreValid(loansClient.getById(result.getLoan().getId()).getJson());
  }

  @Test
  void shouldChargeFeesToMultipleItems() {
    val loanToFeeMap = checkoutTenItems();

    ageToLostFixture.ageToLostAndChargeFees();

    loanToFeeMap.forEach((loan, expectedFee) -> {
      val loanFromStorage = loansStorageClient.get(loan);

      assertThat(loanFromStorage.getJson(), isLostItemHasBeenBilled());

      assertThat(loanFromStorage, hasLostItemFee(isOpen(expectedFee)));
      assertThat(loanFromStorage, hasLostItemFeeCreatedBySystemAction());
      assertThatPublishedLoanLogRecordEventsAreValid(loansClient.getById(loan.getId()).getJson());
    });

  }

  @Test
  void shouldContinueToProcessLoansAfterChargingFeesForSomeHaveFailed() {
    val loanToFeeMap = checkoutTenItems();
    val loansExpectedToBeFailed = loanToFeeMap.entrySet().stream()
      .limit(2)
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    val loansExpectedToBeProcessedSuccessfully = loanToFeeMap.entrySet().stream()
      .skip(2)
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    val firstOwner = feeFineOwnerFixture.getExistingRecord("Owner 1");
    val secondOwner = feeFineOwnerFixture.getExistingRecord("Owner 2");

    feeFineOwnerFixture.delete(firstOwner);
    feeFineOwnerFixture.delete(secondOwner);

    ageToLostFixture.ageToLostAndAttemptChargeFees();

    loansExpectedToBeFailed.forEach((loan, fee) ->
        assertThat(loansStorageClient.get(loan).getJson(), isLostItemHasNotBeenBilled()));

    loansExpectedToBeProcessedSuccessfully.forEach((loan, fee) -> {
      val loanFromStorage = loansStorageClient.get(loan);
      assertThat(loanFromStorage.getJson(), isLostItemHasBeenBilled());

      assertThat(loanFromStorage, hasLostItemFee(isOpen(fee)));
      assertThat(loanFromStorage, hasLostItemFeeCreatedBySystemAction());
      assertThatPublishedLoanLogRecordEventsAreValid(loansClient.getById(loan.getId()).getJson());
    });
  }

  @Test
  void cannotChargeFeesWhenNoProcessingFeeType() {
    feeFineTypeFixture.delete(feeFineTypeFixture.lostItemProcessingFee());

    val policy = lostItemFeePoliciesFixture
      .ageToLostAfterOneMinutePolicy()
      .withNoChargeAmountItem()
      .chargeProcessingFeeWhenAgedToLost(10.0);

    useLostItemPolicy(lostItemFeePoliciesFixture.create(policy).getId());

    val item = itemsFixture.basedUponNod(ItemBuilder::withRandomBarcode);
    val loan = checkOutFixture.checkOutByBarcode(item, usersFixture.steve()).getJson();

    val response = ageToLostFixture.ageToLostAndAttemptChargeFees();

    assertThat(response.getJson(), hasErrorWith(
      hasMessage("No automated Lost item processing fee type found")));
    assertThatPublishedLoanLogRecordEventsAreValid(loan);
  }

  @Test
  void cannotChargeFeesWhenNoItemFeeType() {
    feeFineTypeFixture.delete(feeFineTypeFixture.lostItemFee());

    val policy = lostItemFeePoliciesFixture
      .ageToLostAfterOneMinutePolicy()
      .withSetCost(11.00)
      .doNotChargeProcessingFeeWhenAgedToLost();

    useLostItemPolicy(lostItemFeePoliciesFixture.create(policy).getId());

    val item = itemsFixture.basedUponNod(ItemBuilder::withRandomBarcode);
    val loan = checkOutFixture.checkOutByBarcode(item, usersFixture.steve());
    assertThatPublishedLoanLogRecordEventsAreValid(loan.getJson());
    val response = ageToLostFixture.ageToLostAndAttemptChargeFees();

    assertThat(response.getJson(), hasErrorWith(
      hasMessage("No automated Lost item fee type found")));
  }

  @Test
  void shouldNotChargeFeesWhenDelayedBillingPeriodHasNotPassed() {
    val lostItemFeePolicy = lostItemFeePoliciesFixture
      .ageToLostAfterOneMinutePolicy()
      .withSetCost(11.00)
      .withPatronBilledAfterItemAgedToLost(Period.months(7));
    val noticePolicy = createNoticePolicyWithAgedToLostChargedNotice();
    val result = ageToLostFixture.createLoanAgeToLostAndChargeFeesWithNotice(
      lostItemFeePolicy, noticePolicy);

    assertThat(result.getLoan().getJson(), isLostItemHasNotBeenBilled());
    assertThat(result.getLoan(), hasNoLostItemFee());
    assertThat(scheduledNoticesClient.getAll(), hasSize(0));
    assertThatPublishedLoanLogRecordEventsAreValid(loansClient.getById(result.getLoan().getId()).getJson());
  }

  @Test
  void shouldNotChargeFeesMoreThanOnce() {
    val lostItemFeePolicy = lostItemFeePoliciesFixture
      .ageToLostAfterOneMinutePolicy()
      .withSetCost(11.00);
    val noticePolicy = createNoticePolicyWithAgedToLostChargedNotice();
    val result = ageToLostFixture.createLoanAgeToLostAndChargeFeesWithNotice(
      lostItemFeePolicy, noticePolicy);

    assertThat(result.getLoan().getJson(), isLostItemHasBeenBilled());
    assertThat(result.getLoan(), hasLostItemFee(isOpen(11.00)));

    ageToLostFixture.ageToLostAndChargeFees();

    assertThat(result.getLoan(), hasLostItemFees(iterableWithSize(1)));
    assertThat(scheduledNoticesClient.getAll(), hasSize(2));
    assertThatPublishedLoanLogRecordEventsAreValid(loansClient.getById(result.getLoan().getId()).getJson());
  }

  @Test
  void shouldCloseLoanWhenNoFeesToCharge() {
    val lostItemFeePolicy = lostItemFeePoliciesFixture
      .ageToLostAfterOneMinutePolicy()
      .withPatronBilledAfterItemAgedToLost(Period.weeks(1))
      .withNoChargeAmountItem()
      .doNotChargeProcessingFeeWhenAgedToLost();

    val noticePolicy = createNoticePolicyWithAgedToLostChargedNotice();
    val result = ageToLostFixture.createLoanAgeToLostAndChargeFeesWithNotice(
      lostItemFeePolicy, noticePolicy);

    assertThat(result.getLoan().getJson(), isLostItemHasBeenBilled());
    assertThat(result.getLoan().getJson(), isClosed());
    assertThat(result.getItem().getJson(), isLostAndPaid());
    assertThat(scheduledNoticesClient.getAll(), hasSize(0));
    assertThatPublishedLoanLogRecordEventsAreValid(loansClient.getById(result.getLoan().getId()).getJson());
  }

  @Test
  void shouldNotChargeOverdueFeesDuringCheckInWhenItemHasAgedToLostAndRefundFeePeriodHasPassed() {
    // for a loan that charges both lost fees and overdue fines
    // where the policies are set such that:
    // 1. the item charges overdue fees on checkin
    // 2. A refund expiration date is set
    // 3. The item is aged to lost
    // 4.  the item is checked in after the refund expiration date has passed
    // THEN the loan SHOULD NOT have overdue charges
    IndividualResource overDueFinePolicy = overdueFinePoliciesFixture.facultyStandard();

    IndividualResource lostItemPolicy = lostItemFeePoliciesFixture.ageToLostAfterOneWeek();

    val result = ageToLostFixture.createLoanAgeToLostAndChargeFeesWithOverdues(lostItemPolicy, overDueFinePolicy);

    assertThat(result.getLoan().getJson(), isLostItemHasBeenBilled());

    UUID loanId = result.getLoan().getId();

    // the creation function ages the loan eight weeks into the future.
    // it must be checked in after that timeframe to properly examine the
    // overdue charges
    final DateTime checkInDate = ClockUtil.getDateTime().plusWeeks(9);
    mockClockManagerToReturnFixedDateTime(checkInDate);
    checkInFixture.checkInByBarcode(result.getItem(), checkInDate);
    assertThat(loansFixture.getLoanById(loanId), hasNoOverdueFine());

  }

  @Test
  void shouldAgeToLostAndChargeLostItemProcessingFeeWhenActualFeeSet() {
    final double agedToLostLostProcessingFee = 10.00;
    IndividualResource lostItemPolicy = lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
        .doNotChargeOverdueFineWhenReturned()
        .withNoFeeRefundInterval()
        .withActualCost(10.00)
        .billPatronImmediatelyWhenAgedToLost()
        .chargeProcessingFeeWhenAgedToLost(agedToLostLostProcessingFee));

    useLostItemPolicy(lostItemPolicy.getId());

    final ItemResource item = itemsFixture.basedUponNod();
    final CheckOutResource checkOut = checkOutFixture.checkOutByBarcode(item);

    ageToLostFixture.ageToLostAndChargeFees();

    final IndividualResource loanFromStorage = loansStorageClient.get(checkOut.getId());

    assertThat(itemsFixture.getById(item.getId()).getJson(), isAgedToLost());
    assertThat(loanFromStorage, hasLostItemProcessingFee(isOpen(agedToLostLostProcessingFee)));
  }

  @Test
  void declaredLostItemShouldNotBeAgedToLost() {
    final double declaredLostProcessingFee = 10.00;
    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
        .withPatronBilledAfterItemAgedToLost(Period.weeks(1))
        .withNoChargeAmountItem()
        .doNotChargeProcessingFeeWhenAgedToLost()
        .chargeProcessingFeeWhenDeclaredLost(declaredLostProcessingFee)).getId());

    final ItemResource item = itemsFixture.basedUponNod();
    final CheckOutResource checkOut = checkOutFixture.checkOutByBarcode(item);

    declareLostFixtures.declareItemLost(forLoan(checkOut.getId()));

    ageToLostFixture.ageToLostAndChargeFees();

    final IndividualResource loanFromStorage = loansStorageClient.get(checkOut.getId());
    assertThat(loanFromStorage.getJson(), hasNoDelayedBillingInfo());
    assertThat(loanFromStorage.getJson(), LoanMatchers.isOpen());
    assertThat(loanFromStorage, hasLostItemProcessingFee(isOpen(declaredLostProcessingFee)));
    assertThat(loanFromStorage, hasNoLostItemFee());

    assertThat(itemsFixture.getById(item.getId()).getJson(), isDeclaredLost());
  }

  @Test
  void loanWithRemovedItemShouldBeSkipped() {
    useLostItemPolicy(lostItemFeePoliciesFixture.ageToLostAfterOneMinute().getId());

    final var firstItem = itemsFixture.basedUponNod(ItemBuilder::withRandomBarcode);
    final var secondItem = itemsFixture.basedUponNod(ItemBuilder::withRandomBarcode);
    final var thirdItem = itemsFixture.basedUponNod(ItemBuilder::withRandomBarcode);

    final var firstLoan = checkOutFixture.checkOutByBarcode(firstItem, usersFixture.charlotte());
    final var secondLoan = checkOutFixture.checkOutByBarcode(secondItem, usersFixture.steve());
    final var thirdLoan = checkOutFixture.checkOutByBarcode(thirdItem, usersFixture.james());

    itemsClient.delete(secondItem);

    ageToLostFixture.ageToLostAndChargeFees();

    assertThat(loansStorageClient.get(firstLoan).getJson(), isLostItemHasBeenBilled());
    assertThat(loansStorageClient.get(secondLoan).getJson(), hasNoDelayedBillingInfo());
    assertThat(loansStorageClient.get(thirdLoan).getJson(), isLostItemHasBeenBilled());

    assertThat(itemsClient.get(firstItem).getJson(), isAgedToLost());
    assertThat(itemsClient.attemptGet(secondItem).getStatusCode(), is(404));
    assertThat(itemsClient.get(thirdItem).getJson(), isAgedToLost());
  }

  private Map<IndividualResource, Double> checkoutTenItems() {
    val loanToFeeMap = new LinkedHashMap<IndividualResource, Double>();

    for (int itemIndex = 0; itemIndex < 10; itemIndex++) {
      val itemIndexFinal = itemIndex;
      val servicePoint = createServicePointForItemIndex(itemIndex);

      val location = locationsFixture.basedUponExampleLocation(builder -> builder
        .withName("Location for sp " + itemIndexFinal)
        .withCode("agl-loc-" + itemIndexFinal)
        .withPrimaryServicePoint(servicePoint.getId()));

      val item = itemsFixture.basedUponNod(itemBuilder -> itemBuilder
        .withRandomBarcode()
        .withPermanentLocation(location.getId()));

      val setCostFee = 10.0 + itemIndex;
      val policyBuilder = lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
        .withName("Age to lost item " + itemIndex)
        .doNotChargeProcessingFeeWhenAgedToLost()
        .withSetCost(setCostFee);

      useLostItemPolicy(lostItemFeePoliciesFixture.create(policyBuilder).getId());
      feeFineOwnerFixture.create(new FeeFineOwnerBuilder()
        .withOwner("Owner " + (itemIndex + 1))
        .withServicePointOwner(servicePoint.getId()));

      val loan = checkOutFixture.checkOutByBarcode(item, usersFixture.charlotte());
      loanToFeeMap.put(loan, setCostFee);
    }

    return loanToFeeMap;
  }

  private IndividualResource createServicePointForItemIndex(int itemIndex) {
    return servicePointsFixture.create(new ServicePointBuilder(
      "Age to lost service point " + itemIndex, "agl-sp-" + itemIndex,
      "Age to lost service point " + itemIndex)
      .withPickupLocation(TRUE));
  }

  private Matcher<JsonObject> isLostItemHasBeenBilled() {
    return hasJsonPath("agedToLostDelayedBilling.lostItemHasBeenBilled", true);
  }

  private Matcher<JsonObject> isLostItemHasNotBeenBilled() {
    return hasJsonPath("agedToLostDelayedBilling.lostItemHasBeenBilled", false);
  }

  private Matcher<JsonObject> hasNoDelayedBillingInfo() {
    return hasNoJsonPath("agedToLostDelayedBilling");
  }

  private NoticePolicyBuilder createNoticePolicyWithAgedToLostChargedNotice() {
    return new NoticePolicyBuilder()
      .active()
      .withName("Aged to lost charged notice policy")
      .withFeeFineNotices(List.of(
        new NoticeConfigurationBuilder()
          .withEventType("Aged to lost - fine charged")
          .withTemplateId(UUID.randomUUID())
          .withUponAtTiming()
          .create()));
  }
}
