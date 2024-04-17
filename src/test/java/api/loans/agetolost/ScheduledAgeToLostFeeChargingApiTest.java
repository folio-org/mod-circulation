package api.loans.agetolost;

import static api.support.PubsubPublisherTestUtils.assertThatPublishedLoanLogRecordEventsAreValid;
import static api.support.builders.DeclareItemLostRequestBuilder.forLoan;
import static api.support.http.CqlQuery.queryFromTemplate;
import static api.support.matchers.AccountMatchers.isOpen;
import static api.support.matchers.AccountMatchers.isPaidFully;
import static api.support.matchers.ActualCostRecordMatchers.isActualCostRecord;
import static api.support.matchers.ItemMatchers.isAgedToLost;
import static api.support.matchers.ItemMatchers.isAvailable;
import static api.support.matchers.ItemMatchers.isCheckedOut;
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
import static api.support.matchers.LoanMatchers.isOpen;
import static java.lang.Boolean.TRUE;
import static java.util.function.Function.identity;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.iterableWithSize;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.awaitility.Awaitility;
import org.folio.circulation.domain.ItemLossType;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.domain.policy.lostitem.ChargeAmountType;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import api.support.MultipleJsonRecords;
import api.support.builders.FeeFineOwnerBuilder;
import api.support.builders.ItemBuilder;
import api.support.builders.LostItemFeePolicyBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.builders.ServicePointBuilder;
import api.support.fixtures.policies.PoliciesToActivate;
import api.support.http.CheckOutResource;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.UserResource;
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
    feeFineTypeFixture.lostItemActualCostFee();
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
    val loanBefore = checkOutFixture.checkOutByBarcode(item, usersFixture.steve());
    ageToLostFixture.ageToLostAndChargeFees();

    IndividualResource loanAfter = loansFixture.getLoanById(loanBefore.getId());

    assertThat(loanAfter, hasNoLostItemFee());
    assertThat(loanAfter, hasNoLostItemProcessingFee());
    assertThat(accountsClient.getAll(), hasSize(0));
    assertThat(scheduledNoticesClient.getAll(), hasSize(0));
    assertThatPublishedLoanLogRecordEventsAreValid(loanAfter.getJson());
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
    val loanBefore = checkOutFixture.checkOutByBarcode(item, usersFixture.steve());
    ageToLostFixture.ageToLostAndChargeFees();

    IndividualResource loanAfter = loansFixture.getLoanById(loanBefore.getId());

    assertThat(loanAfter, hasNoLostItemFee());
    assertThat(loanAfter, hasNoLostItemProcessingFee());
    assertThat(accountsClient.getAll(), hasSize(0));
    assertThat(scheduledNoticesClient.getAll(), hasSize(0));
    assertThatPublishedLoanLogRecordEventsAreValid(loanAfter.getJson());
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
    assertThat(actualCostRecordsClient.getAll(), hasSize(0));
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
    final ZonedDateTime checkInDate = getZonedDateTime().plusWeeks(9);
    mockClockManagerToReturnFixedDateTime(checkInDate);
    checkInFixture.checkInByBarcode(result.getItem(), checkInDate);
    assertThat(loansFixture.getLoanById(loanId), hasNoOverdueFine());

  }

  @Test
  void shouldCreateActualCostRecordAndChargeLostItemProcessingFee() {
    final double agedToLostLostProcessingFee = 10.00;

    IndividualResource lostItemPolicy = lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
        .doNotChargeOverdueFineWhenReturned()
        .withNoFeeRefundInterval()
        .withActualCost(10.00)
        .billPatronImmediatelyWhenAgedToLost()
        .chargeProcessingFeeWhenAgedToLost(agedToLostLostProcessingFee));

    useLostItemPolicy(lostItemPolicy.getId());

    final ItemResource item = itemsFixture.basedUponSmallAngryPlanet(identity(),
      builder -> builder.addIdentifier(identifierTypesFixture.isbn().getId(), "9781466636897"),
      itemBuilder -> itemBuilder
        .withPermanentLoanType(loanTypesFixture.canCirculate().getId())
        .withPermanentLocation(locationsFixture.thirdFloor())
        .withTemporaryLocation(locationsFixture.mainFloor())
        .withCallNumber("callNumber", "prefix", "suffix")
        .withVolume("volume")
        .withChronology("chronology")
        .withEnumeration("enumeration")
        .withCopyNumber("copyNumber"));

    final UserResource user = usersFixture.steve();
    final CheckOutResource checkOut = checkOutFixture.checkOutByBarcode(item, user);

    ageToLostFixture.ageToLostAndChargeFees();

    final IndividualResource loan = loansStorageClient.get(checkOut.getId());
    List<JsonObject> actualCostRecords = actualCostRecordsClient.getAll();

    assertThat(loan.getJson(), isOpen());
    assertThat(loan.getJson(), isLostItemHasBeenBilled());
    assertThat(loan, hasLostItemProcessingFee(isOpen(agedToLostLostProcessingFee)));
    assertThat(itemsFixture.getById(item.getId()).getJson(), isAgedToLost());
    assertThat(actualCostRecords, hasSize(1));
    assertThat(actualCostRecords.get(0), isActualCostRecord(loan, item, user,
      ItemLossType.AGED_TO_LOST, locationsFixture.thirdFloor(), locationsFixture.mainFloor(),
      feeFineOwnerFixture.cd1Owner(), feeFineTypeFixture.lostItemActualCostFee(), "ISBN",
      patronGroupsFixture.regular()));
  }

  @Test
  void shouldCreateActualCostRecordAndNotChargeLostItemProcessingFee () {
    IndividualResource lostItemPolicy = lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
        .doNotChargeOverdueFineWhenReturned()
        .withNoFeeRefundInterval()
        .withActualCost(10.00)
        .billPatronImmediatelyWhenAgedToLost()
        .doNotChargeProcessingFeeWhenAgedToLost());

    useLostItemPolicy(lostItemPolicy.getId());

    final UserResource user = usersFixture.steve();
    final ItemResource item = itemsFixture.basedUponSmallAngryPlanet(identity(),
      builder -> builder.addIdentifier(identifierTypesFixture.isbn().getId(), "9781466636897"),
      itemBuilder -> itemBuilder
        .withPermanentLoanType(loanTypesFixture.canCirculate().getId())
        .withPermanentLocation(locationsFixture.thirdFloor())
        .withTemporaryLocation(locationsFixture.mainFloor())
        .withCallNumber("callNumber", "prefix", "suffix")
        .withVolume("volume")
        .withChronology("chronology")
        .withEnumeration("enumeration")
        .withCopyNumber("copyNumber"));

    final CheckOutResource checkOut = checkOutFixture.checkOutByBarcode(item, user);

    ageToLostFixture.ageToLostAndChargeFees();

    final IndividualResource loan = loansStorageClient.get(checkOut.getId());
    List<JsonObject> actualCostRecords = actualCostRecordsClient.getAll();

    assertThat(loan.getJson(), isOpen());
    assertThat(loan.getJson(), isLostItemHasBeenBilled());
    assertThat(loan, hasNoLostItemProcessingFee());
    assertThat(itemsFixture.getById(item.getId()).getJson(), isAgedToLost());
    assertThat(actualCostRecords, hasSize(1));
    assertThat(actualCostRecords.get(0), isActualCostRecord(loan, item, user,
      ItemLossType.AGED_TO_LOST, locationsFixture.thirdFloor(), locationsFixture.mainFloor(),
      feeFineOwnerFixture.cd1Owner(), feeFineTypeFixture.lostItemActualCostFee(), "ISBN",
      patronGroupsFixture.regular()));
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
    assertThat(loanFromStorage.getJson(), isOpen());
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

  @Test
  void loanWithMissingPrimaryServicePointOwnerDoesNotBlockTheQueue() {
    useLostItemPolicy(lostItemFeePoliciesFixture.ageToLostAfterOneMinute().getId());

    IndividualResource locationWithoutOwner = locationsFixture.basedUponExampleLocation(
      builder -> builder.withPrimaryServicePoint(servicePointsFixture.cd2().getId()));

    final var firstItem = itemsFixture.basedUponSmallAngryPlanet();
    final var secondItem = itemsFixture.basedUponNod(
      itemBuilder -> itemBuilder.withPermanentLocation(locationWithoutOwner));
    final var thirdItem = itemsFixture.basedUponTemeraire();

    final var firstLoanBefore = checkOutFixture.checkOutByBarcode(firstItem, usersFixture.charlotte());
    final var secondLoanBefore = checkOutFixture.checkOutByBarcode(secondItem, usersFixture.steve());
    final var thirdLoanBefore = checkOutFixture.checkOutByBarcode(thirdItem, usersFixture.james());

    ageToLostFixture.ageToLostAndChargeFees();

    assertThat(accountsClient.getAll(), hasSize(4));

    IndividualResource firstLoanAfter = loansStorageClient.get(firstLoanBefore);
    IndividualResource secondLoanAfter = loansStorageClient.get(secondLoanBefore);
    IndividualResource thirdLoanAfter = loansStorageClient.get(thirdLoanBefore);

    assertThat(firstLoanAfter.getJson(), isLostItemHasBeenBilled());
    assertThat(firstLoanAfter, hasLostItemFee(isOpen(10.0)));
    assertThat(firstLoanAfter, hasLostItemProcessingFee(isOpen(5.0)));

    assertThat(secondLoanAfter.getJson(), isLostItemHasNotBeenBilled());
    assertThat(secondLoanAfter, hasNoLostItemFee());
    assertThat(secondLoanAfter, hasNoLostItemProcessingFee());

    assertThat(thirdLoanAfter.getJson(), isLostItemHasBeenBilled());
    assertThat(thirdLoanAfter, hasLostItemFee(isOpen(10.0)));
    assertThat(thirdLoanAfter, hasLostItemProcessingFee(isOpen(5.0)));
  }

  @Test
  void accountShouldContainAppropriatePolicyIds() {
    final PoliciesToActivate policies = defaultRollingPolicies().build();
    IndividualResource loanPolicy = policies.getLoanPolicy();
    IndividualResource overduePolicy = policies.getOverduePolicy();
    IndividualResource lostItemPolicy = lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
        .doNotChargeOverdueFineWhenReturned()
        .withNoFeeRefundInterval()
        .withActualCost(10.00)
        .billPatronImmediatelyWhenAgedToLost()
        .chargeProcessingFeeWhenAgedToLost(10.00));

    useFallbackPolicies(loanPolicy.getId(), policies.getRequestPolicy().getId(),
      policies.getNoticePolicy().getId(), overduePolicy.getId(), lostItemPolicy.getId());

    final ItemResource item = itemsFixture.basedUponNod();
    checkOutFixture.checkOutByBarcode(item);
    ageToLostFixture.ageToLostAndChargeFees();

    assertThat(accountsClient.getAll().get(0).getString("loanPolicyId"),
      is(loanPolicy.getId().toString()));
    assertThat(accountsClient.getAll().get(0).getString("overdueFinePolicyId"),
      is(overduePolicy.getId().toString()));
    assertThat(accountsClient.getAll().get(0).getString("lostItemFeePolicyId"),
      is(lostItemPolicy.getId().toString()));
  }

  @Test
  void shouldScheduleNoticeWhenActualCostLostItemFeeIsCharged() {
    var lostItemFeePolicy = lostItemFeePoliciesFixture
      .ageToLostAfterOneMinutePolicy()
      .withActualCost(0.00)
      .withLostItemProcessingFee(0.0);
    var noticePolicy = createNoticePolicyWithAgedToLostChargedNotice();
    var ageToLostResult = ageToLostFixture.createLoanAgeToLostAndChargeFeesWithNotice(
      lostItemFeePolicy, noticePolicy);

    assertThat(actualCostRecordsClient.getAll(), hasSize(1));

    var loan = ageToLostResult.getLoan();
    var feeFineAccount = feeFineAccountFixture.createLostItemFeeActualCostAccount(10.0,
      loan, feeFineTypeFixture.lostItemActualCostFee(), feeFineOwnerFixture.cd1Owner(),
      "stuffInfo", "patronInfo");
    eventSubscribersFixture.publishFeeFineBalanceChangedEvent(loan.getId(), feeFineAccount.getId());

    assertThat(loan.getJson(), isLostItemHasBeenBilled());
    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(1));
    assertThatPublishedLoanLogRecordEventsAreValid(loansClient.getById(
      ageToLostResult.getLoan().getId()).getJson());
  }

  @Test
  void shouldNotScheduleNoticeIfFeeFineBalanceChangedEventWithoutFeeFineId() {
    eventSubscribersFixture.publishFeeFineBalanceChangedEvent(UUID.randomUUID(), null);
    assertThat(scheduledNoticesClient.getAll(), hasSize(0));
  }

  @Test
  void shouldNotScheduleNoticeIfFeeFineBalanceChangedEventWithoutLoanId() {
    eventSubscribersFixture.publishFeeFineBalanceChangedEvent(null, UUID.randomUUID());
    assertThat(scheduledNoticesClient.getAll(), hasSize(0));
  }

  @Test
  void closingLostItemFeeDoesNotAffectMoreRecentLoanForSameItem() {
    ItemResource item = itemsFixture.basedUponNod();
    UUID itemId = item.getId();
    UserResource firstPatron = usersFixture.steve();
    UserResource secondPatron = usersFixture.james();

    policiesActivation.use(PoliciesToActivate.builder().lostItemPolicy(
      lostItemFeePoliciesFixture.create(
        new LostItemFeePolicyBuilder()
          .withName("Processing fee only, no refund on check-in")
          .withItemAgedToLostAfterOverdue(Period.minutes(1))
          .withPatronBilledAfterItemAgedToLost(Period.minutes(1))
          .withNoChargeAmountItem()
          .withLostItemProcessingFee(1.0)
          .withChargeAmountItemPatron(true)
          .doNotRefundProcessingFeeWhenReturned()
      )));

    // create first loan and declare item lost
    CheckOutResource firstLoan = checkOutFixture.checkOutByBarcode(item, firstPatron);
    UUID firstLoanId = firstLoan.getId();
    assertThat(itemsFixture.getById(itemId).getJson(), isCheckedOut());
    declareLostFixtures.declareItemLost(firstLoanId);

    assertThat(feeFineAccountFixture.getAccounts(firstLoanId), hasSize(1));
    assertThat(firstLoan, hasLostItemProcessingFee(isOpen(1.0)));
    assertThat(itemsFixture.getById(itemId).getJson(), isDeclaredLost());
    assertThat(loansFixture.getLoanById(firstLoanId).getJson(), isOpen());

    // check in first loan
    checkInFixture.checkInByBarcode(item);
    int firstLoanHistorySizeAfterCheckIn = getLoanHistory(firstLoanId).size();

    assertThat(feeFineAccountFixture.getAccounts(firstLoanId), hasSize(1));
    assertThat(firstLoan, hasLostItemProcessingFee(isOpen(1.0)));
    assertThat(itemsFixture.getById(itemId).getJson(), isAvailable());
    assertThat(loansFixture.getLoanById(firstLoanId).getJson(), isClosed());

    // create second loan and declare item lost
    CheckOutResource secondLoan = checkOutFixture.checkOutByBarcode(item, secondPatron);
    UUID secondLoanId = secondLoan.getId();
    assertThat(itemsFixture.getById(itemId).getJson(), isCheckedOut());
    declareLostFixtures.declareItemLost(secondLoanId);

    assertThat(feeFineAccountFixture.getAccounts(secondLoanId), hasSize(1));
    assertThat(secondLoan, hasLostItemProcessingFee(isOpen(1.0)));
    assertThat(itemsFixture.getById(itemId).getJson(), isDeclaredLost());
    assertThat(loansFixture.getLoanById(firstLoanId).getJson(), isClosed());
    assertThat(loansFixture.getLoanById(secondLoanId).getJson(), isOpen());

    // pay lost item fee for first loan
    feeFineAccountFixture.payLostItemProcessingFee(firstLoanId);
    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(firstLoanId);

    assertThat(getLoanHistory(firstLoanId).size(), equalTo(firstLoanHistorySizeAfterCheckIn));
    assertThat(firstLoan, hasLostItemProcessingFee(allOf(isClosed(), isPaidFully())));
    assertThat(itemsFixture.getById(itemId).getJson(), isDeclaredLost());
    assertThat(loansFixture.getLoanById(firstLoanId).getJson(), isClosed());
    assertThat(loansFixture.getLoanById(secondLoanId).getJson(), isOpen());

    // pay lost item fee for second loan
    feeFineAccountFixture.payLostItemProcessingFee(secondLoanId);
    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(secondLoanId);

    assertThat(secondLoan, hasLostItemProcessingFee(allOf(isClosed(), isPaidFully())));
    assertThat(itemsFixture.getById(itemId).getJson(), isLostAndPaid());
    assertThat(loansFixture.getLoanById(firstLoanId).getJson(), isClosed());
    assertThat(loansFixture.getLoanById(secondLoanId).getJson(), isClosed());
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

  private MultipleJsonRecords getLoanHistory(UUID loanId) {
    return loanHistoryClient.getMany(
      queryFromTemplate("loan.id=\"%s\" sortBy createdDate/sort.descending", loanId));
  }
}
