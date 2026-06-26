package api.loans;

import static api.support.fixtures.TemplateContextMatchers.getFeeActionContextMatcher;
import static api.support.fixtures.TemplateContextMatchers.getItemContextMatchers;
import static api.support.fixtures.TemplateContextMatchers.getLoanContextMatchers;
import static api.support.fixtures.TemplateContextMatchers.getSingleFeeChargeContextMatcher;
import static api.support.fixtures.TemplateContextMatchers.getUserContextMatchers;
import static api.support.fixtures.TemplateContextMatchers.getLoanAdditionalInfoContextMatchers;
import static api.support.http.CqlQuery.exactMatch;
import static api.support.matchers.AccountMatchers.isAccount;
import static api.support.matchers.ItemMatchers.isAvailable;
import static api.support.matchers.JsonObjectMatcher.toStringMatcher;
import static api.support.matchers.LoanMatchers.isClosed;
import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.matchers.ScheduledNoticeMatchers.hasScheduledFeeFineNotice;
import static api.support.matchers.ScheduledNoticeMatchers.hasScheduledLoanNotice;
import static api.support.utl.BlockOverridesUtils.OVERRIDE_RENEWAL_PERMISSION;
import static api.support.utl.BlockOverridesUtils.buildOkapiHeadersWithPermissions;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfPublishedEvents;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfScheduledNotices;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfSentNotices;
import static java.util.stream.Collectors.toList;
import static org.folio.circulation.domain.notice.NoticeTiming.AFTER;
import static org.folio.circulation.domain.notice.NoticeTiming.UPON_AT;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.AGED_TO_LOST_FINE_CHARGED;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.AGED_TO_LOST_RETURNED;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE_ERROR;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getUUIDProperty;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.iterableWithSize;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import api.support.builders.AddInfoRequestBuilder;
import org.awaitility.Awaitility;
import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.utils.ClockUtil;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.matchers.JsonPathMatchers;

import api.support.APITests;
import api.support.builders.ClaimItemReturnedRequestBuilder;
import api.support.builders.LostItemFeePolicyBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.fakes.FakeModNotify;
import api.support.fixtures.AgeToLostFixture.AgeToLostResult;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.OkapiHeaders;
import io.vertx.core.json.JsonObject;
import lombok.val;

class AgedToLostScheduledNoticesProcessingTests extends APITests {
  private static final UUID UPON_AT_TEMPLATE_ID = UUID.randomUUID();
  private static final UUID AFTER_ONE_TIME_TEMPLATE_ID = UUID.randomUUID();
  private static final UUID AFTER_RECURRING_TEMPLATE_ID = UUID.randomUUID();

  private static final Period TIMING_PERIOD = Period.days(1);
  private static final Period RECURRENCE_PERIOD = Period.hours(1);

  public static final String ACCOUNT_STATUS_OPEN = "Open";
  public static final String LOST_ITEM_FEE = "Lost item fee";
  public static final String LOST_ITEM_PROCESSING_FEE = "Lost item processing fee";
  public static final String PAYMENT_STATUS_OUTSTANDING = "Outstanding";

  public static final String ACTION_TYPE_CANCELLED = "Cancelled item returned";
  public static final String ACTION_TYPE_REFUNDED_PARTIALLY = "Refunded partially";
  public static final String ACTION_TYPE_REFUNDED_FULLY = "Refunded fully";

  public static final double LOST_ITEM_FEE_AMOUNT = 10;
  public static final double PROCESSING_FEE_AMOUNT = 5;
  public static final double LOST_ITEM_FEE_PAYMENT_AMOUNT = LOST_ITEM_FEE_AMOUNT / 2;
  public static final double PROCESSING_FEE_PAYMENT_AMOUNT = PROCESSING_FEE_AMOUNT / 2;
  private static final String LOAN_INFO_ADDED = "testing patron info";

  public AgedToLostScheduledNoticesProcessingTests() {
    super(true, true);
  }

  @BeforeEach
  public void beforeEach() {
    templateFixture.createDummyNoticeTemplate(UPON_AT_TEMPLATE_ID);
    templateFixture.createDummyNoticeTemplate(AFTER_ONE_TIME_TEMPLATE_ID);
    templateFixture.createDummyNoticeTemplate(AFTER_RECURRING_TEMPLATE_ID);

    feeFineOwnerFixture.cd1Owner();
    feeFineTypeFixture.lostItemFee();
    feeFineTypeFixture.lostItemProcessingFee();
  }

  @Test
  void agedToLostLoanNoticesAreCreatedAndProcessed() {
    val agedToLostLoan = ageToLostFixture.createAgedToLostLoan(
      new NoticePolicyBuilder()
        .active()
        .withName("Aged to lost notice policy")
        .withLoanNotices(List.of(
          new NoticeConfigurationBuilder()
            .withAgedToLostEvent()
            .withTemplateId(UPON_AT_TEMPLATE_ID)
            .withUponAtTiming()
            .create(),
          new NoticeConfigurationBuilder()
            .withAgedToLostEvent()
            .withTemplateId(AFTER_ONE_TIME_TEMPLATE_ID)
            .withAfterTiming(TIMING_PERIOD)
            .create(),
          new NoticeConfigurationBuilder()
            .withAgedToLostEvent()
            .withTemplateId(AFTER_RECURRING_TEMPLATE_ID)
            .withAfterTiming(TIMING_PERIOD)
            .recurring(RECURRENCE_PERIOD)
            .create()
          )));

    final ZonedDateTime runTimeOfUponAtNotice = getAgedToLostDate(agedToLostLoan);
    final ZonedDateTime runTimeOfAfterNotices = TIMING_PERIOD.plusDate(runTimeOfUponAtNotice);

    final UUID loanId = agedToLostLoan.getLoanId();
    addPatronInfoToLoan(loanId.toString());

    // before first run, all three scheduled notices should exist
    verifyNumberOfSentNotices(0);
    assertThat(scheduledNoticesClient.getAll(), allOf(
      iterableWithSize(3),
      hasItems(
        hasScheduledLoanNotice(loanId, runTimeOfUponAtNotice,
          UPON_AT.getRepresentation(), UPON_AT_TEMPLATE_ID, null, true),
        hasScheduledLoanNotice(loanId, runTimeOfAfterNotices,
          AFTER.getRepresentation(), AFTER_ONE_TIME_TEMPLATE_ID, null, true),
        hasScheduledLoanNotice(loanId, runTimeOfAfterNotices,
          AFTER.getRepresentation(), AFTER_RECURRING_TEMPLATE_ID, RECURRENCE_PERIOD, true)
      )));

    // first run, UPON_AT notice should be sent and deleted
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(runTimeOfUponAtNotice.plusMinutes(1));
    verifyNumberOfSentNotices(1);
    assertThat(scheduledNoticesClient.getAll(), allOf(
      iterableWithSize(2),
      hasItems(
        hasScheduledLoanNotice(loanId, runTimeOfAfterNotices,
          AFTER.getRepresentation(), AFTER_ONE_TIME_TEMPLATE_ID, null, true),
        hasScheduledLoanNotice(loanId, runTimeOfAfterNotices,
          AFTER.getRepresentation(), AFTER_RECURRING_TEMPLATE_ID, RECURRENCE_PERIOD, true)
      )));

    // second run, both AFTER notices should be sent, recurring AFTER notice should be rescheduled
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(runTimeOfAfterNotices.plusMinutes(1));
    verifyNumberOfSentNotices(3);
    assertThat(scheduledNoticesClient.getAll(), allOf(
      iterableWithSize(1),
      hasItem(
        hasScheduledLoanNotice(loanId, RECURRENCE_PERIOD.plusDate(runTimeOfAfterNotices),
          AFTER.getRepresentation(), AFTER_RECURRING_TEMPLATE_ID, RECURRENCE_PERIOD, true)
      )));

    checkSentLoanNotices(agedToLostLoan,
      List.of(UPON_AT_TEMPLATE_ID, AFTER_ONE_TIME_TEMPLATE_ID, AFTER_RECURRING_TEMPLATE_ID));

    // close the loan
    checkInFixture.checkInByBarcode(agedToLostLoan.getItem());
    assertThat(itemsFixture.getById(agedToLostLoan.getItemId()).getJson(), isAvailable());
    assertThat(loansFixture.getLoanById(agedToLostLoan.getLoanId()).getJson(), isClosed());

    // third run, loan is now closed so recurring notice should be deleted without sending
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(
      RECURRENCE_PERIOD.plusDate(runTimeOfAfterNotices).plusMinutes(1));

    verifyNumberOfSentNotices(3);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 3);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void shouldStopSendingAgedToLostNoticesOnceLostItemFeeWasCharged() {
    val agedToLostLoan = ageToLostFixture.createAgedToLostLoan(
      new NoticePolicyBuilder()
        .active()
        .withName("Aged to lost notice policy")
        .withLoanNotices(List.of(
          new NoticeConfigurationBuilder()
            .withAgedToLostEvent()
            .withTemplateId(AFTER_RECURRING_TEMPLATE_ID)
            .withAfterTiming(TIMING_PERIOD)
            .recurring(RECURRENCE_PERIOD)
            .create()
        )));
    addPatronInfoToLoan(agedToLostLoan.getLoanId().toString());

    final ZonedDateTime firstRunTime = TIMING_PERIOD.plusDate(getAgedToLostDate(agedToLostLoan));
    final UUID loanId = agedToLostLoan.getLoanId();

    assertThat(scheduledNoticesClient.getAll(), allOf(
      iterableWithSize(1),
      hasItems(
        hasScheduledLoanNotice(loanId, firstRunTime,
          AFTER.getRepresentation(), AFTER_RECURRING_TEMPLATE_ID, RECURRENCE_PERIOD, true)
      )));

    // first run, notice should be sent and rescheduled
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(firstRunTime.plusMinutes(1));
    verifyNumberOfSentNotices(1);
    assertThat(scheduledNoticesClient.getAll(), allOf(
      iterableWithSize(1),
      hasItems(
        hasScheduledLoanNotice(loanId, RECURRENCE_PERIOD.plusDate(firstRunTime),
          AFTER.getRepresentation(), AFTER_RECURRING_TEMPLATE_ID, RECURRENCE_PERIOD, true)
      )));

    ageToLostFixture.chargeFees();

    // second run, notice should be deleted without sending
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(
      RECURRENCE_PERIOD.plusDate(firstRunTime).plusMinutes(1));

    verifyNumberOfSentNotices(1);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    assertThat(FakeModNotify.getSentPatronNotices(), hasItems(
      hasEmailNoticeProperties(agedToLostLoan.getUser().getId(), AFTER_RECURRING_TEMPLATE_ID,
        getLoanAdditionalInfoContextMatchers(LOAN_INFO_ADDED))));
  }

  @Test
  void shouldStopSendingAgedToLostNoticesOnceItemIsDeclaredLost() {
    AgeToLostResult agedToLostLoan = createRecurringAgedToLostNotice();

    declareLostFixtures.declareItemLost(agedToLostLoan.getLoan().getJson());
    final ZonedDateTime firstRunTime = TIMING_PERIOD.plusDate(getAgedToLostDate(agedToLostLoan));

    scheduledNoticeProcessingClient.runLoanNoticesProcessing(
      RECURRENCE_PERIOD.plusDate(firstRunTime).plusMinutes(1));

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void shouldStopSendingAgedToLostNoticesOnceItemIsClaimedReturned() {
    AgeToLostResult agedToLostLoan = createRecurringAgedToLostNotice();

    claimItemReturnedFixture.claimItemReturned(new ClaimItemReturnedRequestBuilder()
      .forLoan(agedToLostLoan.getLoanId().toString())
      .withItemClaimedReturnedDate(ClockUtil.getZonedDateTime()));

    final ZonedDateTime firstRunTime = TIMING_PERIOD.plusDate(getAgedToLostDate(agedToLostLoan));
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(
      RECURRENCE_PERIOD.plusDate(firstRunTime).plusMinutes(1));

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void shouldStopSendingAgedToLostNoticesOnceItemIsRenewedThroughOverride() {
    AgeToLostResult agedToLostLoan = createRecurringAgedToLostNotice();

    final OkapiHeaders okapiHeaders = buildOkapiHeadersWithPermissions(OVERRIDE_RENEWAL_PERMISSION);
    loansFixture.overrideRenewalByBarcode(agedToLostLoan.getItem(), agedToLostLoan.getUser(),
      "Test overriding", agedToLostLoan.getLoan().getJson().getString("dueDate"), okapiHeaders);
    final ZonedDateTime firstRunTime = TIMING_PERIOD.plusDate(getAgedToLostDate(agedToLostLoan));

    scheduledNoticeProcessingClient.runLoanNoticesProcessing(
      RECURRENCE_PERIOD.plusDate(firstRunTime).plusMinutes(1));

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void shouldRemoveAgedToLostNoticeIfLoanIsClosed() {
    AgeToLostResult agedToLostLoan = createOneTimeAgedToLostNotice();
    checkInFixture.checkInByBarcode(agedToLostLoan.getItem());

    assertThat(itemsFixture.getById(agedToLostLoan.getItemId()).getJson(), isAvailable());
    assertThat(loansFixture.getLoanById(agedToLostLoan.getLoanId()).getJson(), isClosed());
    verifyNumberOfScheduledNotices(1);

    final ZonedDateTime firstRunTime = TIMING_PERIOD.plusDate(getAgedToLostDate(agedToLostLoan));
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(
      RECURRENCE_PERIOD.plusDate(firstRunTime).plusMinutes(1));

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  private AgeToLostResult createRecurringAgedToLostNotice() {
    val agedToLostLoan = ageToLostFixture.createAgedToLostLoan(
      new NoticePolicyBuilder()
        .active()
        .withName("Aged to lost notice policy")
        .withLoanNotices(Collections.singletonList(new NoticeConfigurationBuilder()
          .withAgedToLostEvent()
          .withTemplateId(AFTER_RECURRING_TEMPLATE_ID)
          .withAfterTiming(TIMING_PERIOD)
          .recurring(RECURRENCE_PERIOD)
          .create())));

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(1));

    return agedToLostLoan;
  }

  private AgeToLostResult createOneTimeAgedToLostNotice() {
    val agedToLostLoan = ageToLostFixture.createAgedToLostLoan(
      new NoticePolicyBuilder()
        .active()
        .withName("Aged to lost notice policy")
        .withLoanNotices(Collections.singletonList(new NoticeConfigurationBuilder()
          .withAgedToLostEvent()
          .withTemplateId(AFTER_RECURRING_TEMPLATE_ID)
          .withAfterTiming(TIMING_PERIOD)
          .create())));

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(1));

    return agedToLostLoan;
  }

  @Test
  void patronNoticesForForAgedToLostFineAdjustmentsAreCreatedAndProcessed() {
    LostItemFeePolicyBuilder lostItemFeePolicyBuilder = lostItemFeePoliciesFixture
      .ageToLostAfterOneMinutePolicy()
      .withSetCost(LOST_ITEM_FEE_AMOUNT)
      .withLostItemProcessingFee(PROCESSING_FEE_AMOUNT);

    AgeToLostResult agedToLostLoan = ageToLostFixture.createLoanAgeToLostAndChargeFees(
      lostItemFeePolicyBuilder,
      new NoticePolicyBuilder()
        .active()
        .withName("Aged to lost notice policy")
        .withFeeFineNotices(List.of(
          new NoticeConfigurationBuilder()
            .withAgedToLostReturnedEvent()
            .withTemplateId(UPON_AT_TEMPLATE_ID)
            .withUponAtTiming()
            .create()
        )));

    UUID userId = agedToLostLoan.getUser().getId();
    UUID loanId = agedToLostLoan.getLoanId();
    addPatronInfoToLoan(loanId.toString());

    final List<JsonObject> existingAccounts = accountsClient.getAll();

    assertThat(existingAccounts, allOf(
      iterableWithSize(2),
      hasItems(
        isAccount(LOST_ITEM_FEE_AMOUNT, LOST_ITEM_FEE_AMOUNT, ACCOUNT_STATUS_OPEN,
          PAYMENT_STATUS_OUTSTANDING, LOST_ITEM_FEE, userId, loanId),
        isAccount(PROCESSING_FEE_AMOUNT, PROCESSING_FEE_AMOUNT, ACCOUNT_STATUS_OPEN,
          PAYMENT_STATUS_OUTSTANDING, LOST_ITEM_PROCESSING_FEE, userId, loanId)
      )));

    // one "charge" action per account
    assertThat(feeFineActionsClient.getAll(), hasSize(2));

    // create payments for both accounts to produce "refund" actions and notices
    existingAccounts.forEach(account -> feeFineAccountFixture.pay(
      account.getString("id"),
      account.getDouble("remaining") / 2) // partial payment to produce "cancel" actions and notices
    );

    // 2 charges + 2 payments
    assertThat(feeFineActionsClient.getAll(), hasSize(4));

    // check-in should refund and cancel both "Lost item fee" and "Lost item processing fee"
    checkInFixture.checkInByBarcode(agedToLostLoan.getItem());
    assertThat(itemsFixture.getById(agedToLostLoan.getItemId()).getJson(), isAvailable());
    assertThat(loansFixture.getLoanById(loanId).getJson(), isClosed());

    // 2 charges + 2 payments + 2 credits + 2 refunds + 2 cancellations
    assertThat(feeFineActionsClient.getAll(), hasSize(10));

    final JsonObject refundLostItemFeeAction =
      findFeeFineAction(ACTION_TYPE_REFUNDED_PARTIALLY, LOST_ITEM_FEE_PAYMENT_AMOUNT);
    final JsonObject refundProcessingFeeAction =
      findFeeFineAction(ACTION_TYPE_REFUNDED_PARTIALLY, PROCESSING_FEE_PAYMENT_AMOUNT);
    final JsonObject cancelLostItemFeeAction =
      findFeeFineAction(ACTION_TYPE_CANCELLED, LOST_ITEM_FEE_AMOUNT);
    final JsonObject cancelProcessingFeeAction =
      findFeeFineAction(ACTION_TYPE_CANCELLED, PROCESSING_FEE_AMOUNT);

    final UUID refundLostItemFeeActionId = getId(refundLostItemFeeAction);
    final UUID refundProcessingFeeActionId = getId(refundProcessingFeeAction);
    final UUID cancelLostItemFeeActionId = getId(cancelLostItemFeeAction);
    final UUID cancelProcessingFeeActionId = getId(cancelProcessingFeeAction);

    final ZonedDateTime refundLostItemFeeActionDate = getActionDate(refundLostItemFeeAction);
    final ZonedDateTime refundProcessingFeeActionDate = getActionDate(refundProcessingFeeAction);
    final ZonedDateTime cancelLostItemFeeActionDate = getActionDate(cancelLostItemFeeAction);
    final ZonedDateTime cancelProcessingFeeActionDate = getActionDate(cancelProcessingFeeAction);

    verifyNumberOfSentNotices(0);
    assertThat(scheduledNoticesClient.getAll(), allOf(
      iterableWithSize(4),
      hasItems(
        hasScheduledFeeFineNotice(refundLostItemFeeActionId, loanId, userId,
          UPON_AT_TEMPLATE_ID, AGED_TO_LOST_RETURNED, refundLostItemFeeActionDate,
          UPON_AT, null, true),
        hasScheduledFeeFineNotice(refundProcessingFeeActionId, loanId, userId,
          UPON_AT_TEMPLATE_ID, AGED_TO_LOST_RETURNED, refundProcessingFeeActionDate,
          UPON_AT, null, true),
        hasScheduledFeeFineNotice(cancelLostItemFeeActionId, loanId, userId,
          UPON_AT_TEMPLATE_ID, AGED_TO_LOST_RETURNED, cancelLostItemFeeActionDate,
          UPON_AT, null, true),
        hasScheduledFeeFineNotice(cancelProcessingFeeActionId, loanId, userId,
          UPON_AT_TEMPLATE_ID, AGED_TO_LOST_RETURNED, cancelProcessingFeeActionDate,
          UPON_AT, null, true)
      )));

    ZonedDateTime maxActionDate = Stream.of(
      cancelLostItemFeeActionDate,
      cancelProcessingFeeActionDate,
      refundLostItemFeeActionDate,
      refundProcessingFeeActionDate)
      .max(ZonedDateTime::compareTo)
      .orElseThrow();

    scheduledNoticeProcessingClient.runFeeFineNoticesProcessing(maxActionDate.plusSeconds(1));

    checkSentFeeFineNotices(agedToLostLoan, Map.of(
      refundLostItemFeeAction, UPON_AT_TEMPLATE_ID,
      refundProcessingFeeAction, UPON_AT_TEMPLATE_ID,
      cancelLostItemFeeAction, UPON_AT_TEMPLATE_ID,
      cancelProcessingFeeAction, UPON_AT_TEMPLATE_ID
    ));

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 4);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void patronNoticeForAdjustmentOfFullyPaidLostItemFeeIsCreatedAndProcessed() {
    LostItemFeePolicyBuilder lostItemFeePolicyBuilder = lostItemFeePoliciesFixture
      .ageToLostAfterOneMinutePolicy()
      .withSetCost(LOST_ITEM_FEE_AMOUNT)
      .withLostItemProcessingFee(PROCESSING_FEE_AMOUNT);

    AgeToLostResult agedToLostLoan = ageToLostFixture.createLoanAgeToLostAndChargeFees(
      lostItemFeePolicyBuilder,
      new NoticePolicyBuilder()
        .active()
        .withName("Aged to lost notice policy")
        .withFeeFineNotices(List.of(
          new NoticeConfigurationBuilder()
            .withAgedToLostReturnedEvent()
            .withTemplateId(UPON_AT_TEMPLATE_ID)
            .withUponAtTiming()
            .create()
        )));

    final UUID loanId = agedToLostLoan.getLoanId();
    final UUID userId = agedToLostLoan.getUser().getId();

    addPatronInfoToLoan(loanId.toString());

    final List<JsonObject> existingAccounts = accountsClient.getAll();

    assertThat(existingAccounts, allOf(
      iterableWithSize(2),
      hasItems(
        isAccount(LOST_ITEM_FEE_AMOUNT, LOST_ITEM_FEE_AMOUNT, ACCOUNT_STATUS_OPEN,
          PAYMENT_STATUS_OUTSTANDING, LOST_ITEM_FEE, userId, loanId),
        isAccount(PROCESSING_FEE_AMOUNT, PROCESSING_FEE_AMOUNT, ACCOUNT_STATUS_OPEN,
          PAYMENT_STATUS_OUTSTANDING, LOST_ITEM_PROCESSING_FEE, userId, loanId)
      )));

    // one "charge" action per account
    assertThat(feeFineActionsClient.getAll(), hasSize(2));

    final List<Account> accounts = existingAccounts.stream()
      .map(Account::from)
      .collect(toList());

    assertThat(accounts, hasSize(2));

    final Account firstAccount = accounts.get(0);
    final Account secondAccount = accounts.get(1);

    feeFineAccountFixture.pay(firstAccount.getId(), firstAccount.getAmount().toDouble());
    feeFineAccountFixture.transfer(secondAccount.getId(), secondAccount.getAmount().toDouble());

    // 2 charges + 1 payment + 1 transfer
    assertThat(feeFineActionsClient.getAll(), hasSize(4));

    // closing a loan-related fee/fine should close the loan
    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loanId);
    assertThat(loansFixture.getLoanById(agedToLostLoan.getLoanId()).getJson(), isClosed());

    // check-in should refund both fees
    checkInFixture.checkInByBarcode(agedToLostLoan.getItem());
    assertThat(itemsFixture.getById(agedToLostLoan.getItemId()).getJson(), isAvailable());
    assertThat(loansFixture.getLoanById(agedToLostLoan.getLoanId()).getJson(), isClosed());

    // 2 charges + 1 payment + 1 transfer + 2 credits + 2 refunds + 2 cancellations
    assertThat(feeFineActionsClient.getAll(), hasSize(10));

    final JsonObject lostItemFeeRefundAction =
      findFeeFineAction(ACTION_TYPE_REFUNDED_FULLY, LOST_ITEM_FEE_AMOUNT);
    final JsonObject processingFeeRefundAction =
      findFeeFineAction(ACTION_TYPE_REFUNDED_FULLY, PROCESSING_FEE_AMOUNT);
    final JsonObject lostItemFeeCancellationAction =
      findFeeFineAction(ACTION_TYPE_CANCELLED, LOST_ITEM_FEE_AMOUNT);
    final JsonObject processingFeeCancellationAction =
      findFeeFineAction(ACTION_TYPE_CANCELLED, PROCESSING_FEE_AMOUNT);

    final UUID refundLostItemFeeActionId = getId(lostItemFeeRefundAction);
    final UUID refundProcessingFeeActionId = getId(processingFeeRefundAction);
    final UUID cancelLostItemActionId = getId(lostItemFeeCancellationAction);
    final UUID cancelProcessingFeeActionId = getId(processingFeeCancellationAction);

    final ZonedDateTime refundLostItemFeeActionDate = getActionDate(lostItemFeeRefundAction);
    final ZonedDateTime refundProcessingFeeActionDate = getActionDate(processingFeeRefundAction);
    final ZonedDateTime cancelLostItemActionDate = getActionDate(lostItemFeeCancellationAction);
    final ZonedDateTime cancelProcessingFeeActionDate = getActionDate(processingFeeCancellationAction);

    verifyNumberOfSentNotices(0);
    assertThat(scheduledNoticesClient.getAll(), allOf(
      iterableWithSize(4),
      hasItems(
        hasScheduledFeeFineNotice(refundLostItemFeeActionId, loanId, userId,
          UPON_AT_TEMPLATE_ID, AGED_TO_LOST_RETURNED, refundLostItemFeeActionDate,
          UPON_AT, null, true),
        hasScheduledFeeFineNotice(refundProcessingFeeActionId, loanId, userId,
          UPON_AT_TEMPLATE_ID, AGED_TO_LOST_RETURNED, refundProcessingFeeActionDate,
          UPON_AT, null, true),
        hasScheduledFeeFineNotice(cancelLostItemActionId, loanId, userId, UPON_AT_TEMPLATE_ID,
          AGED_TO_LOST_RETURNED, cancelLostItemActionDate, UPON_AT, null, true),
        hasScheduledFeeFineNotice(cancelProcessingFeeActionId, loanId, userId, UPON_AT_TEMPLATE_ID,
          AGED_TO_LOST_RETURNED, cancelProcessingFeeActionDate, UPON_AT, null, true)
        )));

    ZonedDateTime maxActionDate = Stream.of(refundLostItemFeeActionDate, refundProcessingFeeActionDate)
      .max(ZonedDateTime::compareTo)
      .orElseThrow();

    scheduledNoticeProcessingClient.runFeeFineNoticesProcessing(maxActionDate.plusSeconds(1));

    checkSentFeeFineNotices(agedToLostLoan, Map.of(
      lostItemFeeRefundAction, UPON_AT_TEMPLATE_ID,
      processingFeeRefundAction, UPON_AT_TEMPLATE_ID,
      lostItemFeeCancellationAction, UPON_AT_TEMPLATE_ID,
      processingFeeCancellationAction, UPON_AT_TEMPLATE_ID
    ));

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 4);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void overnightNoticesForLostItemFeeChargesAreGroupedAndSent() {
    LostItemFeePolicyBuilder lostItemFeePolicyBuilder = lostItemFeePoliciesFixture
      .ageToLostAfterOneMinutePolicy()
      .withSetCost(LOST_ITEM_FEE_AMOUNT)
      .withLostItemProcessingFee(PROCESSING_FEE_AMOUNT);

    NoticePolicyBuilder patronNoticePolicy = new NoticePolicyBuilder()
      .active()
      .withName("Aged to lost notice policy")
      .withFeeFineNotices(List.of(
        new NoticeConfigurationBuilder()
          .withAgedToLostFineChargedEvent()
          .sendInRealTime(false)
          .withTemplateId(UPON_AT_TEMPLATE_ID)
          .withUponAtTiming()
          .create()
      ));

    AgeToLostResult firstLoan = ageToLostFixture.createLoanAgeToLostAndChargeFees(
      lostItemFeePolicyBuilder, patronNoticePolicy);
    AgeToLostResult secondLoan = ageToLostFixture.createLoanAgeToLostAndChargeFees(
      lostItemFeePolicyBuilder, patronNoticePolicy);
    UUID userId = firstLoan.getUser().getId(); // same for both loans
    UUID firstLoanId = firstLoan.getLoanId();
    UUID secondLoanId = secondLoan.getLoanId();

    final List<JsonObject> accounts = accountsClient.getAll();

    assertThat(accounts, allOf(
      iterableWithSize(4), // lost item fee + lost item processing fee for both loans
      hasItems(
        isAccount(LOST_ITEM_FEE_AMOUNT, LOST_ITEM_FEE_AMOUNT, ACCOUNT_STATUS_OPEN,
          PAYMENT_STATUS_OUTSTANDING, LOST_ITEM_FEE, userId, firstLoanId),
        isAccount(PROCESSING_FEE_AMOUNT, PROCESSING_FEE_AMOUNT, ACCOUNT_STATUS_OPEN,
          PAYMENT_STATUS_OUTSTANDING, LOST_ITEM_PROCESSING_FEE, userId, firstLoanId),
        isAccount(LOST_ITEM_FEE_AMOUNT, LOST_ITEM_FEE_AMOUNT, ACCOUNT_STATUS_OPEN,
          PAYMENT_STATUS_OUTSTANDING, LOST_ITEM_FEE, userId, secondLoanId),
        isAccount(PROCESSING_FEE_AMOUNT, PROCESSING_FEE_AMOUNT, ACCOUNT_STATUS_OPEN,
          PAYMENT_STATUS_OUTSTANDING, LOST_ITEM_PROCESSING_FEE, userId, secondLoanId)
      )));

    JsonObject firstLostItemFeeAccount = getAccount(accounts, firstLoanId, LOST_ITEM_FEE);
    JsonObject firstProcessingFeeAccount = getAccount(accounts, firstLoanId, LOST_ITEM_PROCESSING_FEE);
    JsonObject secondLostItemFeeAccount = getAccount(accounts, secondLoanId, LOST_ITEM_FEE);
    JsonObject secondProcessingFeeAccount = getAccount(accounts, secondLoanId, LOST_ITEM_PROCESSING_FEE);

    List<JsonObject> feeFineActions = feeFineActionsClient.getAll();
    assertThat(feeFineActions, hasSize(4)); // one "charge" action per account

    JsonObject firstLostItemFeeChargeAction = getFeeFineAction(
      feeFineActions, getId(firstLostItemFeeAccount), LOST_ITEM_FEE);
    JsonObject firstProcessingFeeChargeAction = getFeeFineAction(
      feeFineActions, getId(firstProcessingFeeAccount), LOST_ITEM_PROCESSING_FEE);
    JsonObject secondLostItemFeeChargeAction = getFeeFineAction(
      feeFineActions, getId(secondLostItemFeeAccount), LOST_ITEM_FEE);
    JsonObject secondProcessingFeeChargeAction = getFeeFineAction(
      feeFineActions, getId(secondProcessingFeeAccount), LOST_ITEM_PROCESSING_FEE);

    verifyNumberOfSentNotices(0);
    assertThat(scheduledNoticesClient.getAll(), allOf(
      iterableWithSize(4),
      hasItems(
        hasScheduledFeeFineNotice(
          getId(firstLostItemFeeChargeAction), firstLoanId, userId, UPON_AT_TEMPLATE_ID,
          AGED_TO_LOST_FINE_CHARGED, getActionDate(firstLostItemFeeChargeAction), UPON_AT, null, false),
        hasScheduledFeeFineNotice(
          getId(firstProcessingFeeChargeAction), firstLoanId, userId, UPON_AT_TEMPLATE_ID,
          AGED_TO_LOST_FINE_CHARGED, getActionDate(firstProcessingFeeChargeAction), UPON_AT, null, false),
        hasScheduledFeeFineNotice(
          getId(secondLostItemFeeChargeAction), secondLoanId, userId, UPON_AT_TEMPLATE_ID,
          AGED_TO_LOST_FINE_CHARGED, getActionDate(secondLostItemFeeChargeAction), UPON_AT, null, false),
        hasScheduledFeeFineNotice(
          getId(secondProcessingFeeChargeAction), secondLoanId, userId, UPON_AT_TEMPLATE_ID,
          AGED_TO_LOST_FINE_CHARGED, getActionDate(secondProcessingFeeChargeAction), UPON_AT, null, false)
      )));

    scheduledNoticeProcessingClient.runFeeFineNotRealTimeNoticesProcessing(
      getActionDate(secondProcessingFeeChargeAction).plusDays(1));

    verifyNumberOfSentNotices(1);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    verifyBundledFeeFineNotice(UPON_AT_TEMPLATE_ID, userId, Map.of(
      firstLostItemFeeAccount, firstLoan,
      firstProcessingFeeAccount, firstLoan,
      secondLostItemFeeAccount, secondLoan,
      secondProcessingFeeAccount, secondLoan
    ));
  }

  private static JsonObject getAccount(Collection<JsonObject> accounts, UUID loanId,
    String feeFineType) {

    return accounts.stream()
      .filter(account -> feeFineType.equals(account.getString("feeFineType")))
      .filter(account -> loanId.toString().equals(account.getString("loanId")))
      .findFirst()
      .orElseThrow();
  }

  private static JsonObject getFeeFineAction(Collection<JsonObject> actions, UUID accountId,
    String actionType) {

    return actions.stream()
      .filter(action -> accountId.toString().equals(action.getString("accountId")))
      .filter(action -> actionType.equals(action.getString("typeAction")))
      .findFirst()
      .orElseThrow();
  }

  private JsonObject findFeeFineAction(String actionType, double actionAmount) {
    return feeFineActionsClient.getMany(
      exactMatch("typeAction", actionType)
        .and(exactMatch("amountAction", String.valueOf(actionAmount))))
      .getFirst();
  }

  private static ZonedDateTime getActionDate(JsonObject feeFineAction) {
    return ZonedDateTime.parse(feeFineAction.getString("dateAction"));
  }

  private static UUID getId(JsonObject jsonObject) {
    return UUID.fromString(jsonObject.getString("id"));
  }

  private static ZonedDateTime getAgedToLostDate(AgeToLostResult ageToLostResult) {
    return ZonedDateTime.parse(
      ageToLostResult.getLoan()
        .getJson()
        .getJsonObject("agedToLostDelayedBilling")
        .getString("agedToLostDate")
    );
  }

  private void checkSentFeeFineNotices(AgeToLostResult agedToLostResult,
    Map<JsonObject, UUID> actionsToTemplateIds) {

    final UUID userId = agedToLostResult.getUser().getId();

    assertThat(FakeModNotify.getSentPatronNotices(), hasSize(actionsToTemplateIds.size()));

    actionsToTemplateIds.keySet().stream()
      .map(feeFineAction -> hasEmailNoticeProperties(userId, actionsToTemplateIds.get(feeFineAction),
          allOf(
            getBaseNoticeContextMatcher(agedToLostResult),
            getFeeActionContextMatcher(feeFineAction),
            getSingleFeeChargeContextMatcher(findAccountForFeeFineAction(feeFineAction)))))
      .forEach(matcher -> assertThat(FakeModNotify.getSentPatronNotices(), hasItem(matcher)));
  }

  @SuppressWarnings("unchecked")
  private void verifyBundledFeeFineNotice(UUID templateId, UUID userId,
    Map<JsonObject, AgeToLostResult> accountsToLoans) {

    assertThat(FakeModNotify.getSentPatronNotices(), hasItem(
      hasEmailNoticeProperties(userId, templateId,
        allOf(
          toStringMatcher(getUserContextMatchers(usersClient.get(userId))),
          JsonPathMatchers.hasJsonPath("feeCharges[*]", hasItems(
            getMatchersForBundledFeeFineNotice(accountsToLoans)))
        ))));
  }

  private Matcher[] getMatchersForBundledFeeFineNotice(Map<JsonObject,
    AgeToLostResult> accountsToLoans) {

    return accountsToLoans.entrySet()
      .stream()
      .map(entry -> {
        JsonObject account = entry.getKey();
        AgeToLostResult loan = entry.getValue();

        final IndividualResource holdingsRecord = holdingsClient.get(
          getUUIDProperty(loan.getItem().getJson(), "holdingsRecordId"));

        final IndividualResource instance = instancesClient.get(
          getUUIDProperty(holdingsRecord.getJson(), "instanceId"));

        final ItemResource itemResource = new ItemResource(loan.getItem(),
          holdingsRecord, instance);

        Map<String, Matcher<String>> noticeContextMatchers = new HashMap<>();
        noticeContextMatchers.putAll(getLoanContextMatchers(loan.getLoan()));
        noticeContextMatchers.putAll(getItemContextMatchers(itemResource, true));

        return allOf(toStringMatcher(noticeContextMatchers), getSingleFeeChargeContextMatcher(account));
      })
      .toArray(Matcher[]::new);
  }

  private void checkSentLoanNotices(AgeToLostResult agedToLostResult, List<UUID> templateIds) {
    final UUID userId = agedToLostResult.getUser().getId();

    final List<JsonObject> sentNotices = FakeModNotify.getSentPatronNotices();
    assertThat(sentNotices, hasSize(templateIds.size()));
    templateIds.forEach(templateId -> assertThat(sentNotices, hasItem(
        hasEmailNoticeProperties(userId, templateId, getBaseNoticeContextMatcher(agedToLostResult)))));
  }

  private JsonObject findAccountForFeeFineAction(JsonObject feeFineAction) {
    return accountsClient.get(UUID.fromString(feeFineAction.getString("accountId"))).getJson();
  }

  private Matcher<? super String> getBaseNoticeContextMatcher(AgeToLostResult agedToLostResult) {
    final IndividualResource holdingsRecord = holdingsClient.get(
      getUUIDProperty(agedToLostResult.getItem().getJson(), "holdingsRecordId"));

    final IndividualResource instance = instancesClient.get(
      getUUIDProperty(holdingsRecord.getJson(), "instanceId"));

    final ItemResource itemResource = new ItemResource(agedToLostResult.getItem(),
      holdingsRecord, instance);

    Map<String, Matcher<String>> noticeContextMatchers = new HashMap<>();
    noticeContextMatchers.putAll(getUserContextMatchers(agedToLostResult.getUser()));
    noticeContextMatchers.putAll(getLoanContextMatchers(agedToLostResult.getLoan()));
    noticeContextMatchers.putAll(getItemContextMatchers(itemResource, true));
    noticeContextMatchers.putAll(getLoanAdditionalInfoContextMatchers(LOAN_INFO_ADDED));

    return toStringMatcher(noticeContextMatchers);
  }

  private void addPatronInfoToLoan(String loanId){
    addInfoFixture.addInfo(new AddInfoRequestBuilder(loanId,
      "patronInfoAdded", LOAN_INFO_ADDED));
  }
}
