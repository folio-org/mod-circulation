package api.loans;

import static api.support.fakes.FakeModNotify.getSentPatronNotices;
import static api.support.fixtures.TemplateContextMatchers.getBundledFeeChargeContextMatcher;
import static api.support.http.CqlQuery.exactMatch;
import static api.support.matchers.PatronNoticeMatcher.hasNoticeProperties;
import static api.support.matchers.ScheduledNoticeMatchers.hasScheduledFeeFineNotice;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfPublishedEvents;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfScheduledNotices;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfSentNotices;
import static java.util.Collections.singletonList;
import static java.util.UUID.randomUUID;
import static org.folio.circulation.domain.notice.NoticeTiming.AFTER;
import static org.folio.circulation.domain.notice.NoticeTiming.UPON_AT;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.OVERDUE_FINE_RENEWED;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.OVERDUE_FINE_RETURNED;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE_ERROR;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import api.support.builders.AddInfoRequestBuilder;
import org.awaitility.Awaitility;
import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.FeeFineAction;
import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.notice.schedule.TriggeringEvent;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.json.JsonPropertyWriter;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import api.support.APITests;
import api.support.MultipleJsonRecords;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.FeeFineBuilder;
import api.support.builders.FeeFineOwnerBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.fakes.FakeModNotify;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.UserResource;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

class OverdueFineScheduledNoticesProcessingTests extends APITests {
  private static final Period AFTER_PERIOD = Period.days(1);
  private static final Period RECURRING_PERIOD = Period.hours(6);
  private static final String OVERDUE_FINE = "Overdue fine";
  private static final String LOAN_INFO_ADDED = "testing patron info";
  private static final Map<NoticeTiming, UUID> TEMPLATE_IDS = new HashMap<>();

  private UUID checkInServicePointId;
  private UUID itemLocationId;

  public OverdueFineScheduledNoticesProcessingTests() {
    super(true, true);
  }

  @BeforeEach
  void beforeEach() {
    // init templates
    UUID uponAtTemplateId = randomUUID();
    UUID afterTemplateId = randomUUID();
    templateFixture.createDummyNoticeTemplate(uponAtTemplateId);
    templateFixture.createDummyNoticeTemplate(afterTemplateId);
    TEMPLATE_IDS.put(UPON_AT, uponAtTemplateId);
    TEMPLATE_IDS.put(AFTER, afterTemplateId);

    // init service point and location
    checkInServicePointId = servicePointsFixture.cd1().getId();
    itemLocationId = locationsFixture.basedUponExampleLocation(
        builder -> builder.withPrimaryServicePoint(checkInServicePointId))
      .getId();

    // init owner
    feeFineOwnersClient.create(new FeeFineOwnerBuilder()
      .withId(randomUUID())
      .withOwner("test owner")
      .withServicePointOwner(singletonList(new JsonObject()
        .put("value", checkInServicePointId.toString())
        .put("label", "Service Desk 1"))));

    // init fee/fine type
    feeFinesClient.create(new FeeFineBuilder()
      .withId(randomUUID())
      .withFeeFineType(OVERDUE_FINE)
      .withAutomatic(true));
  }

  @ParameterizedTest
  @MethodSource("triggeringEvents")
  void uponAtNoticeIsSentAndDeleted(TriggeringEvent triggeringEvent) {
    UserResource user = usersFixture.james();
    createPatronNoticePolicy(createNoticeConfig(triggeringEvent, UPON_AT, false));

    OverdueFineContext overdueFine = generateOverdueFine(triggeringEvent, user);

    verifyNumberOfScheduledNotices(1);
    ZonedDateTime chargeActionDateTime = overdueFine.getActionDateTime();
    assertThatNoticeExists(triggeringEvent, UPON_AT, false, chargeActionDateTime, overdueFine);

    endCheckInSession(user);
    scheduledNoticeProcessingClient.runOverdueFineNoticesProcessing(rightAfter(chargeActionDateTime));

    verifyNumberOfSentNotices(1);
    assertThatNoticeWasSent(UPON_AT, overdueFine);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @ParameterizedTest
  @MethodSource("triggeringEvents")
  void oneTimeAfterNoticeIsSentAndDeleted(TriggeringEvent triggeringEvent) {
    UUID checkInSessionId = randomUUID();
    UserResource user = usersFixture.james();
    createPatronNoticePolicy(createNoticeConfig(triggeringEvent, AFTER, false));

    OverdueFineContext overdueFine = generateOverdueFine(triggeringEvent, user, checkInSessionId);

    ZonedDateTime chargeActionDateTime = overdueFine.getActionDateTime();
    ZonedDateTime expectedNextRunTime = AFTER_PERIOD.plusDate(chargeActionDateTime);

    verifyNumberOfScheduledNotices(1);
    assertThatNoticeExists(triggeringEvent, AFTER, false, expectedNextRunTime, overdueFine);

    endCheckInSession(user);
    scheduledNoticeProcessingClient.runOverdueFineNoticesProcessing(rightAfter(expectedNextRunTime));

    verifyNumberOfSentNotices(1);
    assertThatNoticeWasSent(AFTER, overdueFine);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @ParameterizedTest
  @MethodSource("triggeringEvents")
  void recurringAfterNoticeIsSentAndRescheduled(TriggeringEvent triggeringEvent) {
    UUID checkInSessionId = randomUUID();
    UserResource user = usersFixture.james();
    createPatronNoticePolicy(createNoticeConfig(triggeringEvent, AFTER, true));

    OverdueFineContext overdueFine = generateOverdueFine(triggeringEvent, user, checkInSessionId);

    verifyNumberOfScheduledNotices(1);
    ZonedDateTime expectedFirstRunTime = AFTER_PERIOD.plusDate(overdueFine.getActionDateTime());
    assertThatNoticeExists(triggeringEvent, AFTER, true, expectedFirstRunTime, overdueFine);

    endCheckInSession(user);
    scheduledNoticeProcessingClient.runOverdueFineNoticesProcessing(rightAfter(expectedFirstRunTime));

    ZonedDateTime expectedSecondRunTime = RECURRING_PERIOD.plusDate(expectedFirstRunTime);

    verifyNumberOfSentNotices(1);
    assertThatNoticeWasSent(AFTER, overdueFine);
    verifyNumberOfScheduledNotices(1);
    assertThatNoticeExists(triggeringEvent, AFTER, true, expectedSecondRunTime, overdueFine);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @ParameterizedTest
  @MethodSource("triggeringEvents")
  void recurringNoticeIsRescheduledCorrectlyWhenNextCalculatedRunTimeIsBeforeNow(TriggeringEvent triggeringEvent) {
    UserResource user = usersFixture.james();
    createPatronNoticePolicy(createNoticeConfig(triggeringEvent, AFTER, true));

    OverdueFineContext overdueFine = generateOverdueFine(triggeringEvent, user);

    verifyNumberOfScheduledNotices(1);
    ZonedDateTime expectedFirstRunTime = AFTER_PERIOD.plusDate(overdueFine.getActionDateTime());
    assertThatNoticeExists(triggeringEvent, AFTER, true, expectedFirstRunTime, overdueFine);

    ZonedDateTime fakeNow = rightAfter(RECURRING_PERIOD.plusDate(expectedFirstRunTime));

    endCheckInSession(user);
    scheduledNoticeProcessingClient.runOverdueFineNoticesProcessing(fakeNow);

    ZonedDateTime expectedNextRunTime = RECURRING_PERIOD.plusDate(fakeNow);

    verifyNumberOfSentNotices(1);
    assertThatNoticeWasSent(AFTER, overdueFine);
    verifyNumberOfScheduledNotices(1);
    assertThatNoticeExists(triggeringEvent, AFTER, true, expectedNextRunTime, overdueFine);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @ParameterizedTest
  @MethodSource("triggeringEvents")
  void multipleScheduledNoticesAreProcessedDuringOneProcessingIteration(TriggeringEvent triggeringEvent) {
    UserResource user = usersFixture.james();
    createPatronNoticePolicy(
      createNoticeConfig(triggeringEvent, UPON_AT, false),
      createNoticeConfig(triggeringEvent, AFTER, false),
      createNoticeConfig(triggeringEvent, AFTER, true));

    OverdueFineContext overdueFine = generateOverdueFine(triggeringEvent, user);

    ZonedDateTime chargeActionDateTime = overdueFine.getActionDateTime();
    ZonedDateTime firstAfterRunTime = AFTER_PERIOD.plusDate(chargeActionDateTime);

    verifyNumberOfScheduledNotices(3);
    assertThatNoticeExists(triggeringEvent, UPON_AT, false, chargeActionDateTime, overdueFine);  // send and delete
    assertThatNoticeExists(triggeringEvent, AFTER, false, firstAfterRunTime, overdueFine); // send and delete
    assertThatNoticeExists(triggeringEvent, AFTER, true, firstAfterRunTime, overdueFine);  // send and reschedule

    endCheckInSession(user);
    scheduledNoticeProcessingClient.runOverdueFineNoticesProcessing(rightAfter(firstAfterRunTime));

    ZonedDateTime expectedRecurrenceRunTime = RECURRING_PERIOD.plusDate(firstAfterRunTime);

    verifyNumberOfSentNotices(2); // 1 "upon at" notice + 1 bundled "after" notice
    assertThatNoticeWasSent(UPON_AT, overdueFine);
    assertThatNoticeWasSent(AFTER, List.of(overdueFine, overdueFine));

    verifyNumberOfScheduledNotices(1);
    assertThatNoticeExists(triggeringEvent, AFTER, true, expectedRecurrenceRunTime, overdueFine);
    // 3 charges in 2 notices
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @ParameterizedTest
  @MethodSource("triggeringEvents")
  void noticeIsDiscardedWhenReferencedActionDoesNotExist(TriggeringEvent triggeringEvent) {
    UserResource user = usersFixture.james();
    createPatronNoticePolicy(createNoticeConfig(triggeringEvent, UPON_AT, false));

    OverdueFineContext overdueFine = generateOverdueFine(triggeringEvent, user);
    ZonedDateTime chargeActionDateTime = overdueFine.getActionDateTime();

    verifyNumberOfScheduledNotices(1);
    assertThatNoticeExists(triggeringEvent, UPON_AT, false, chargeActionDateTime, overdueFine);

    endCheckInSession(user);
    feeFineActionsClient.delete(overdueFine.getActionId());
    scheduledNoticeProcessingClient.runOverdueFineNoticesProcessing(rightAfter(chargeActionDateTime));

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @ParameterizedTest
  @MethodSource("triggeringEvents")
  void noticeIsDiscardedWhenReferencedAccountDoesNotExist(TriggeringEvent triggeringEvent) {
    UserResource user = usersFixture.james();
    createPatronNoticePolicy(createNoticeConfig(triggeringEvent, UPON_AT, false));

    OverdueFineContext overdueFine = generateOverdueFine(triggeringEvent, user);
    ZonedDateTime chargeActionDateTime = overdueFine.getActionDateTime();

    verifyNumberOfScheduledNotices(1);
    assertThatNoticeExists(triggeringEvent, UPON_AT, false, chargeActionDateTime, overdueFine);

    endCheckInSession(user);
    accountsClient.delete(overdueFine.getAccountId());
    scheduledNoticeProcessingClient.runOverdueFineNoticesProcessing(rightAfter(chargeActionDateTime));

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @ParameterizedTest
  @MethodSource("triggeringEvents")
  void noticeIsDiscardedWhenReferencedLoanDoesNotExist(TriggeringEvent triggeringEvent) {
    UserResource user = usersFixture.james();
    createPatronNoticePolicy(createNoticeConfig(triggeringEvent, UPON_AT, false));

    OverdueFineContext overdueFine = generateOverdueFine(triggeringEvent, user);
    ZonedDateTime chargeActionDateTime = overdueFine.getActionDateTime();

    verifyNumberOfScheduledNotices(1);
    assertThatNoticeExists(triggeringEvent, UPON_AT, false, chargeActionDateTime, overdueFine);

    endCheckInSession(user);
    loansClient.delete(overdueFine.getLoanId());
    scheduledNoticeProcessingClient.runOverdueFineNoticesProcessing(rightAfter(chargeActionDateTime));

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @ParameterizedTest
  @MethodSource("triggeringEvents")
  void noticeIsDiscardedWhenReferencedItemDoesNotExist(TriggeringEvent triggeringEvent) {
    UserResource user = usersFixture.james();
    createPatronNoticePolicy(createNoticeConfig(triggeringEvent, UPON_AT, false));

    OverdueFineContext overdueFine = generateOverdueFine(triggeringEvent, user);
    ZonedDateTime chargeActionDateTime = overdueFine.getActionDateTime();

    verifyNumberOfScheduledNotices(1);
    assertThatNoticeExists(triggeringEvent, UPON_AT, false, chargeActionDateTime, overdueFine);

    endCheckInSession(user);
    itemsClient.delete(overdueFine.getItem().getId());
    scheduledNoticeProcessingClient.runOverdueFineNoticesProcessing(rightAfter(chargeActionDateTime));

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @ParameterizedTest
  @MethodSource("triggeringEvents")
  void noticeIsDiscardedWhenReferencedUserDoesNotExist(TriggeringEvent triggeringEvent) {
    UserResource user = usersFixture.james();
    createPatronNoticePolicy(createNoticeConfig(triggeringEvent, UPON_AT, false));

    OverdueFineContext overdueFine = generateOverdueFine(triggeringEvent, user);
    ZonedDateTime chargeActionDateTime = overdueFine.getActionDateTime();

    verifyNumberOfScheduledNotices(1);
    assertThatNoticeExists(triggeringEvent, UPON_AT, false, chargeActionDateTime, overdueFine);

    endCheckInSession(user);
    usersClient.delete(overdueFine.getUser().getId());
    scheduledNoticeProcessingClient.runOverdueFineNoticesProcessing(rightAfter(chargeActionDateTime));

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @ParameterizedTest
  @MethodSource("triggeringEvents")
  void noticeIsDiscardedWhenReferencedTemplateDoesNotExist(TriggeringEvent triggeringEvent) {
    UserResource user = usersFixture.james();
    createPatronNoticePolicy(createNoticeConfig(triggeringEvent, UPON_AT, false));

    OverdueFineContext overdueFine = generateOverdueFine(triggeringEvent, user);
    ZonedDateTime chargeActionDateTime = overdueFine.getActionDateTime();

    verifyNumberOfScheduledNotices(1);
    assertThatNoticeExists(triggeringEvent, UPON_AT, false, chargeActionDateTime, overdueFine);

    endCheckInSession(user);
    templateFixture.delete(TEMPLATE_IDS.get(UPON_AT));
    scheduledNoticeProcessingClient.runOverdueFineNoticesProcessing(rightAfter(chargeActionDateTime));

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @ParameterizedTest
  @MethodSource("triggeringEvents")
  void noticeIsNotDeletedWhenPatronNoticeRequestFails(TriggeringEvent triggeringEvent) {
    UserResource user = usersFixture.james();
    createPatronNoticePolicy(createNoticeConfig(triggeringEvent, UPON_AT, false));

    OverdueFineContext overdueFine = generateOverdueFine(triggeringEvent, user);
    ZonedDateTime chargeActionDateTime = overdueFine.getActionDateTime();

    verifyNumberOfScheduledNotices(1);
    assertThatNoticeExists(triggeringEvent, UPON_AT, false, chargeActionDateTime, overdueFine);

    endCheckInSession(user);
    FakeModNotify.setFailPatronNoticesWithBadRequest(true);
    scheduledNoticeProcessingClient.runOverdueFineNoticesProcessing(rightAfter(chargeActionDateTime));

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @ParameterizedTest
  @MethodSource("triggeringEvents")
  void noticeIsDiscardedWhenAccountIsClosed(TriggeringEvent triggeringEvent) {
    UserResource user = usersFixture.james();
    createPatronNoticePolicy(createNoticeConfig(triggeringEvent, UPON_AT, false));

    OverdueFineContext overdueFine = generateOverdueFine(triggeringEvent, user);
    ZonedDateTime chargeActionDateTime = overdueFine.getActionDateTime();

    verifyNumberOfScheduledNotices(1);
    assertThatNoticeExists(triggeringEvent, UPON_AT, false, chargeActionDateTime, overdueFine);

    JsonObject closedAccountJson = overdueFine.getAccount().toJson();
    JsonPropertyWriter.writeNamedObject(closedAccountJson, "status", "Closed");
    accountsClient.replace(overdueFine.getAccountId(), closedAccountJson);

    endCheckInSession(user);
    scheduledNoticeProcessingClient.runOverdueFineNoticesProcessing(rightAfter(chargeActionDateTime));

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void overdueFineReturnedNoticeShouldContainUnlimitedNumberOfRenewals() {
    UserResource user = usersFixture.james();
    createPatronNoticePolicy(createNoticeConfig(OVERDUE_FINE_RETURNED, UPON_AT, false));

    OverdueFineContext overdueFine = generateOverdueFine(OVERDUE_FINE_RETURNED, user);
    ZonedDateTime chargeActionDateTime = overdueFine.getActionDateTime();

    verifyNumberOfScheduledNotices(1);
    assertThatNoticeExists(OVERDUE_FINE_RETURNED, UPON_AT, false, chargeActionDateTime, overdueFine);

    endCheckInSession(user);
    scheduledNoticeProcessingClient.runOverdueFineNoticesProcessing(rightAfter(chargeActionDateTime));

    JsonObject loanInSentNotice = getSentPatronNotices().get(0)
      .getJsonObject("context").getJsonArray("feeCharges").getJsonObject(0).getJsonObject("loan");
    assertThat(loanInSentNotice.getString("numberOfRenewalsAllowed"), is("unlimited"));
    assertThat(loanInSentNotice.getString("numberOfRenewalsRemaining"), is("unlimited"));
    assertThat(loanInSentNotice.getString("additionalInfo"), is(LOAN_INFO_ADDED));

    verifyNumberOfSentNotices(1);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void uponAtNoticesCreatedDuringSameCheckInSessionAreBundled() {
    UUID checkInSessionId = randomUUID();
    UserResource user = usersFixture.james();

    createPatronNoticePolicy(createNoticeConfig(OVERDUE_FINE_RETURNED, UPON_AT, false));

    OverdueFineContext fine1 = generateOverdueFine(OVERDUE_FINE_RETURNED, user, checkInSessionId);
    OverdueFineContext fine2 = generateOverdueFine(OVERDUE_FINE_RETURNED, user, checkInSessionId);

    verifyNumberOfScheduledNotices(2);
    assertThatNoticeExists(OVERDUE_FINE_RETURNED, UPON_AT, false, fine1.getActionDateTime(), fine1);
    assertThatNoticeExists(OVERDUE_FINE_RETURNED, UPON_AT, false, fine2.getActionDateTime(), fine2);

    endCheckInSession(user);
    scheduledNoticeProcessingClient.runOverdueFineNoticesProcessing(
      rightAfter(fine2.getAction().getDateAction()));

    verifyNumberOfSentNotices(1);
    assertThatNoticeWasSent(UPON_AT, List.of(fine1, fine2));
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void uponAtNoticesCreatedDuringDifferentCheckInSessionsAreNotBundled() {
    UUID firstCheckInSessionId = randomUUID();
    UUID secondCheckInSessionId = randomUUID();
    UserResource user = usersFixture.james();

    createPatronNoticePolicy(createNoticeConfig(OVERDUE_FINE_RETURNED, UPON_AT, false));

    OverdueFineContext fine1 = generateOverdueFine(OVERDUE_FINE_RETURNED, user, firstCheckInSessionId);
    OverdueFineContext fine2 = generateOverdueFine(OVERDUE_FINE_RETURNED, user, secondCheckInSessionId);

    verifyNumberOfScheduledNotices(2);
    assertThatNoticeExists(OVERDUE_FINE_RETURNED, UPON_AT, false, fine1.getActionDateTime(), fine1);
    assertThatNoticeExists(OVERDUE_FINE_RETURNED, UPON_AT, false, fine2.getActionDateTime(), fine2);

    endCheckInSession(user);
    scheduledNoticeProcessingClient.runOverdueFineNoticesProcessing(
      rightAfter(fine2.getAction().getDateAction()));

    verifyNumberOfSentNotices(2);
    assertThatNoticeWasSent(UPON_AT, fine1);
    assertThatNoticeWasSent(UPON_AT, fine2);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void uponAtNoticesCreatedUponRenewalAreBundled() {
    UserResource user = usersFixture.james();
    createPatronNoticePolicy(createNoticeConfig(OVERDUE_FINE_RENEWED, UPON_AT, false));

    OverdueFineContext fine1 = generateOverdueFine(OVERDUE_FINE_RENEWED, user);
    OverdueFineContext fine2 = generateOverdueFine(OVERDUE_FINE_RENEWED, user);

    verifyNumberOfScheduledNotices(2);
    assertThatNoticeExists(OVERDUE_FINE_RENEWED, UPON_AT, false, fine1.getActionDateTime(), fine1);
    assertThatNoticeExists(OVERDUE_FINE_RENEWED, UPON_AT, false, fine2.getActionDateTime(), fine2);

    endCheckInSession(user);
    scheduledNoticeProcessingClient.runOverdueFineNoticesProcessing(
      rightAfter(fine2.getActionDateTime()));

    verifyNumberOfSentNotices(1);
    assertThatNoticeWasSent(UPON_AT, List.of(fine1, fine2));
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @ParameterizedTest
  @MethodSource("triggeringEvents")
  void afterNoticesAreBundled(TriggeringEvent triggeringEvent) {
    UUID firstCheckInSessionId = randomUUID();
    UUID secondCheckInSessionId = randomUUID();
    UserResource user = usersFixture.james();

    createPatronNoticePolicy(createNoticeConfig(triggeringEvent, AFTER, false));

    OverdueFineContext fine1 = generateOverdueFine(triggeringEvent, user, firstCheckInSessionId);
    OverdueFineContext fine2 = generateOverdueFine(triggeringEvent, user, secondCheckInSessionId);

    ZonedDateTime firstNextRunTime = AFTER_PERIOD.plusDate(fine1.getActionDateTime());
    ZonedDateTime secondNextRunTime = AFTER_PERIOD.plusDate(fine2.getActionDateTime());

    verifyNumberOfScheduledNotices(2);
    assertThatNoticeExists(triggeringEvent, AFTER, false, firstNextRunTime, fine1);
    assertThatNoticeExists(triggeringEvent, AFTER, false, secondNextRunTime, fine2);

    endCheckInSession(user);
    scheduledNoticeProcessingClient.runOverdueFineNoticesProcessing(
      rightAfter(secondNextRunTime));

    verifyNumberOfSentNotices(1);
    assertThatNoticeWasSent(AFTER, List.of(fine1, fine2));
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void noticesWithOpenCheckInSessionsAreSkipped() {
    UserResource user = usersFixture.james();
    createPatronNoticePolicy(createNoticeConfig(OVERDUE_FINE_RETURNED, UPON_AT, false));
    UUID firstCheckInSessionId = randomUUID();
    UUID secondCheckInSessionId = randomUUID();

    // first check-in session
    OverdueFineContext fine1 = generateOverdueFine(OVERDUE_FINE_RETURNED, user, firstCheckInSessionId);
    OverdueFineContext fine2 = generateOverdueFine(OVERDUE_FINE_RETURNED, user, firstCheckInSessionId);
    verifyNumberOfScheduledNotices(2);
    assertThatNoticeExists(OVERDUE_FINE_RETURNED, UPON_AT, false, fine1.getActionDateTime(), fine1);
    assertThatNoticeExists(OVERDUE_FINE_RETURNED, UPON_AT, false, fine2.getActionDateTime(), fine2);

    endCheckInSession(user); // end first check-in session

    // second check-in session
    OverdueFineContext fine3 = generateOverdueFine(OVERDUE_FINE_RETURNED, user, secondCheckInSessionId);
    OverdueFineContext fine4 = generateOverdueFine(OVERDUE_FINE_RETURNED, user, secondCheckInSessionId);
    verifyNumberOfScheduledNotices(4);
    assertThatNoticeExists(OVERDUE_FINE_RETURNED, UPON_AT, false, fine3.getActionDateTime(), fine3);
    assertThatNoticeExists(OVERDUE_FINE_RETURNED, UPON_AT, false, fine4.getActionDateTime(), fine4);

    // second check-in session is still open
    scheduledNoticeProcessingClient.runOverdueFineNoticesProcessing(
      rightAfter(fine4.getAction().getDateAction()));

    // notices created during first session are bundled and sent
    verifyNumberOfSentNotices(1);
    assertThatNoticeWasSent(UPON_AT, List.of(fine1, fine2));

    // notices created during second session are still there
    verifyNumberOfScheduledNotices(2);
    assertThatNoticeExists(OVERDUE_FINE_RETURNED, UPON_AT, false, fine3.getActionDateTime(), fine3);
    assertThatNoticeExists(OVERDUE_FINE_RETURNED, UPON_AT, false, fine4.getActionDateTime(), fine4);

    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void uponAtNoticesCreatedUponCheckInAndRenewalAreNotBundled() {
    UserResource user = usersFixture.james();
    createPatronNoticePolicy(
      createNoticeConfig(OVERDUE_FINE_RETURNED, UPON_AT, false),
      createNoticeConfig(OVERDUE_FINE_RENEWED, UPON_AT, false));

    OverdueFineContext fine1 = generateOverdueFine(OVERDUE_FINE_RETURNED, user);
    OverdueFineContext fine2 = generateOverdueFine(OVERDUE_FINE_RENEWED, user);

    verifyNumberOfScheduledNotices(2);
    assertThatNoticeExists(OVERDUE_FINE_RETURNED, UPON_AT, false, fine1.getActionDateTime(), fine1);
    assertThatNoticeExists(OVERDUE_FINE_RENEWED, UPON_AT, false, fine2.getActionDateTime(), fine2);

    endCheckInSession(user);
    scheduledNoticeProcessingClient.runOverdueFineNoticesProcessing(
      rightAfter(fine2.getActionDateTime()));

    verifyNumberOfSentNotices(2);
    assertThatNoticeWasSent(UPON_AT, fine1);
    assertThatNoticeWasSent(UPON_AT, fine2);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void afterNoticesCreatedUponCheckInAndRenewalAreNotBundled() {
    UserResource user = usersFixture.james();
    createPatronNoticePolicy(
      createNoticeConfig(OVERDUE_FINE_RETURNED, AFTER, false),
      createNoticeConfig(OVERDUE_FINE_RENEWED, AFTER, false));

    OverdueFineContext fine1 = generateOverdueFine(OVERDUE_FINE_RETURNED, user);
    OverdueFineContext fine2 = generateOverdueFine(OVERDUE_FINE_RENEWED, user);

    ZonedDateTime firstNextRunTime = AFTER_PERIOD.plusDate(fine1.getActionDateTime());
    ZonedDateTime secondNextRunTime = AFTER_PERIOD.plusDate(fine2.getActionDateTime());

    verifyNumberOfScheduledNotices(2);
    assertThatNoticeExists(OVERDUE_FINE_RETURNED, AFTER, false, firstNextRunTime, fine1);
    assertThatNoticeExists(OVERDUE_FINE_RENEWED, AFTER, false, secondNextRunTime, fine2);

    endCheckInSession(user);
    scheduledNoticeProcessingClient.runOverdueFineNoticesProcessing(
      rightAfter(secondNextRunTime));

    verifyNumberOfSentNotices(2);
    assertThatNoticeWasSent(AFTER, fine1);
    assertThatNoticeWasSent(AFTER, fine2);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  private void createPatronNoticePolicy(JsonObject... patronNoticeConfigs) {
    NoticePolicyBuilder noticePolicyBuilder = new NoticePolicyBuilder()
      .withName("Patron notice policy with fee/fine notices")
      .withFeeFineNotices(Arrays.asList(patronNoticeConfigs));

    use(noticePolicyBuilder);
  }

  private OverdueFineContext generateOverdueFine(TriggeringEvent triggeringEvent,
    UserResource user) {

    return generateOverdueFine(triggeringEvent, user, randomUUID());
  }

  private OverdueFineContext generateOverdueFine(TriggeringEvent triggeringEvent, UserResource user,
    UUID sessionId) {

    final List<JsonObject> initialAccounts = accountsClient.getAll();
    final List<JsonObject> initialActions = feeFineActionsClient.getAll();

    ItemResource item = itemsFixture.basedUponNod(builder -> builder.withRandomBarcode()
      .withPermanentLocation(itemLocationId));

    final ZonedDateTime checkOutDate = ClockUtil.getZonedDateTime().minusYears(1);
    final ZonedDateTime checkInDate = checkOutDate.plusMonths(1);

    IndividualResource checkOutResponse = checkOutFixture.checkOutByBarcode(item, user, checkOutDate);
    addInfoFixture.addInfo(new AddInfoRequestBuilder(checkOutResponse.getId().toString(),
      "patronInfoAdded", LOAN_INFO_ADDED));
    UUID loanId = UUID.fromString(checkOutResponse.getJson().getString("id"));

    switch (triggeringEvent) {
    case OVERDUE_FINE_RETURNED:
      checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
        .withSessionId(sessionId.toString())
        .forItem(item)
        .on(checkInDate)
        .at(checkInServicePointId));
      break;
    case OVERDUE_FINE_RENEWED:
      loansFixture.renewLoan(item, user);
      break;
    default:
      break;
    }

    List<JsonObject> accounts = Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(accountsClient::getAll, hasSize(initialAccounts.size() + 1));
    Account account = Account.from(getNewObject(initialAccounts, accounts));

    List<JsonObject> actions = Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(feeFineActionsClient::getAll, hasSize(initialActions.size() + 1));
    FeeFineAction action = FeeFineAction.from(getNewObject(initialActions, actions));

    return new OverdueFineContext(account, action, item, user, loanId);
  }

  private JsonObject createNoticeConfig(TriggeringEvent triggeringEvent, NoticeTiming timing,
    boolean isRecurring) {

    JsonObject timingPeriod = timing == AFTER ? AFTER_PERIOD.asJson() : null;

    NoticeConfigurationBuilder builder = new NoticeConfigurationBuilder()
      .withEventType(NoticeEventType.from(triggeringEvent.getRepresentation()).getRepresentation())
      .withTemplateId(TEMPLATE_IDS.get(timing))
      .withTiming(timing.getRepresentation(), timingPeriod)
      .sendInRealTime(true);

    if (isRecurring) {
      builder = builder.recurring(RECURRING_PERIOD);
    }

    return builder.create();
  }

  private void assertThatNoticeExists(TriggeringEvent triggeringEvent, NoticeTiming timing,
    boolean recurring, ZonedDateTime nextRunTime, OverdueFineContext context) {

    Period expectedRecurringPeriod = recurring ? RECURRING_PERIOD : null;

    assertThat(scheduledNoticesClient.getAll(), hasItem(
      hasScheduledFeeFineNotice(
        context.getActionId(), context.getLoanId(), context.getUser().getId(), TEMPLATE_IDS.get(timing),
        triggeringEvent, nextRunTime, timing, expectedRecurringPeriod, true)
    ));
  }

  private void assertThatNoticeWasSent(NoticeTiming timing, OverdueFineContext context) {
    assertThatNoticeWasSent(timing, singletonList(context));
  }

  private void assertThatNoticeWasSent(NoticeTiming timing, Collection<OverdueFineContext> contexts) {
    List<Account> accounts = contexts.stream()
      .map(OverdueFineContext::getAccount)
      .collect(Collectors.toList());

    // same user for all charges is expected
    UserResource user = contexts.stream()
      .findFirst()
      .map(OverdueFineContext::getUser)
      .orElseThrow();

    assertThat(getSentPatronNotices(), hasItem(
        hasNoticeProperties(user.getId(), TEMPLATE_IDS.get(timing), "email", "text/html",
          getBundledFeeChargeContextMatcher(user, accounts))));
  }

  private static ZonedDateTime rightAfter(ZonedDateTime dateTime) {
    return dateTime.plusMinutes(1);
  }

  private static Object[] triggeringEvents() {
    return new Object[] { OVERDUE_FINE_RETURNED, OVERDUE_FINE_RENEWED };
  }

  private static <T> T getNewObject(Collection<T> oldCollection, Collection<T> newCollection) {
    assertThat(newCollection.size() - oldCollection.size(), is(1));
    boolean newCollectionChanged = newCollection.removeAll(oldCollection);

    if (oldCollection.size() > 0) {
      assertTrue(newCollectionChanged);
    }

    assertThat("Only one new object was expected", newCollection, hasSize(1));

    return newCollection.stream()
      .findFirst()
      .orElseThrow();
  }

  private void endCheckInSession(UserResource user) {
    endPatronSessionClient.endCheckInSession(user.getId());
    Awaitility.waitAtMost(5, TimeUnit.SECONDS)
      .until(() -> getCheckInSession(user.getId()), emptyIterable());
  }

  private MultipleJsonRecords getCheckInSession(UUID userId) {
    return patronSessionRecordsClient.getMany(
      exactMatch("patronId", userId.toString())
        .and(exactMatch("actionType", "Check-in")));
  }

  @AllArgsConstructor
  @Getter
  private static class OverdueFineContext {
    @NonNull private Account account;
    @NonNull private FeeFineAction action;
    @NonNull private ItemResource item;
    @NonNull private UserResource user;
    @NonNull private UUID loanId;

    public ZonedDateTime getActionDateTime() {
      return action.getDateAction();
    }

    public UUID getActionId() {
      return UUID.fromString(action.getId());
    }

    public UUID getAccountId() {
      return UUID.fromString(account.getId());
    }
  }
}
