package api.loans;

import static api.support.fixtures.TemplateContextMatchers.getFeeActionContextMatcher;
import static api.support.fixtures.TemplateContextMatchers.getFeeChargeContextMatcher;
import static api.support.fixtures.TemplateContextMatchers.getItemContextMatchers;
import static api.support.fixtures.TemplateContextMatchers.getLoanContextMatchers;
import static api.support.fixtures.TemplateContextMatchers.getUserContextMatchers;
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

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.awaitility.Awaitility;
import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.utils.ClockUtil;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    final List<JsonObject> existingAccounts = accountsClient.getAll();

    assertThat(existingAccounts, allOf(
      iterableWithSize(2),
      hasItems(
        isAccount(LOST_ITEM_FEE_AMOUNT, LOST_ITEM_FEE_AMOUNT, ACCOUNT_STATUS_OPEN,
          PAYMENT_STATUS_OUTSTANDING, LOST_ITEM_FEE, agedToLostLoan.getUser().getId()),
        isAccount(PROCESSING_FEE_AMOUNT, PROCESSING_FEE_AMOUNT, ACCOUNT_STATUS_OPEN,
          PAYMENT_STATUS_OUTSTANDING, LOST_ITEM_PROCESSING_FEE, agedToLostLoan.getUser().getId())
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
    assertThat(loansFixture.getLoanById(agedToLostLoan.getLoanId()).getJson(), isClosed());

    // 2 charges + 2 payments + 2 credits + 2 refunds + 2 cancellations
    assertThat(feeFineActionsClient.getAll(), hasSize(10));

    final UUID loanId = agedToLostLoan.getLoanId();
    final UUID userId = agedToLostLoan.getUser().getId();

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

    scaleFields(refundLostItemFeeAction, "amountAction", "balance");
    scaleFields(refundProcessingFeeAction, "amountAction", "balance");
    scaleFields(cancelLostItemFeeAction, "amountAction", "balance");
    scaleFields(cancelProcessingFeeAction, "amountAction", "balance");

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

    final List<JsonObject> existingAccounts = accountsClient.getAll();

    assertThat(existingAccounts, allOf(
      iterableWithSize(2),
      hasItems(
        isAccount(LOST_ITEM_FEE_AMOUNT, LOST_ITEM_FEE_AMOUNT, ACCOUNT_STATUS_OPEN,
          PAYMENT_STATUS_OUTSTANDING, LOST_ITEM_FEE, agedToLostLoan.getUser().getId()),
        isAccount(PROCESSING_FEE_AMOUNT, PROCESSING_FEE_AMOUNT, ACCOUNT_STATUS_OPEN,
          PAYMENT_STATUS_OUTSTANDING, LOST_ITEM_PROCESSING_FEE, agedToLostLoan.getUser().getId())
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

    scaleFields(lostItemFeeRefundAction, "amountAction", "balance");
    scaleFields(processingFeeRefundAction, "amountAction", "balance");
    scaleFields(lostItemFeeCancellationAction, "amountAction", "balance");
    scaleFields(processingFeeCancellationAction, "amountAction", "balance");

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
            getFeeChargeContextMatcher(
              scaleFields(findAccountForFeeFineAction(feeFineAction), "amount", "remaining")))))
      .forEach(matcher -> assertThat(FakeModNotify.getSentPatronNotices(), hasItem(matcher)));
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

  private JsonObject scaleFields(JsonObject feeFineAction, String fieldOne, String fieldTwo) {
    return feeFineAction
      .put(fieldOne, new BigDecimal(feeFineAction.getDouble(fieldOne)).setScale(2))
      .put(fieldTwo, new BigDecimal(feeFineAction.getDouble(fieldTwo)).setScale(2));
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

    return toStringMatcher(noticeContextMatchers);
  }
}
