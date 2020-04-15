package api.loans;

import static api.support.matchers.PatronNoticeMatcher.hasNoticeProperties;
import static api.support.matchers.ScheduledNoticeMatchers.hasScheduledFeeFineNotice;
import static java.util.UUID.randomUUID;
import static org.folio.circulation.domain.notice.NoticeEventType.OVERDUE_FINE_RETURNED;
import static org.folio.circulation.domain.notice.NoticeTiming.AFTER;
import static org.folio.circulation.domain.notice.NoticeTiming.UPON_AT;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.awaitility.Awaitility;
import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.FeeFineAction;
import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.notice.schedule.TriggeringEvent;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.JsonPropertyWriter;
import org.folio.circulation.support.http.client.IndividualResource;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import api.support.APITests;
import api.support.CheckInByBarcodeResponse;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.FeeFineBuilder;
import api.support.builders.FeeFineOwnerBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.fixtures.TemplateContextMatchers;
import io.vertx.core.json.JsonObject;

public class FeeFineScheduledNoticesProcessingTests extends APITests {
  private static final Map<NoticeTiming, UUID> TEMPLATE_IDS;
  private static final Period AFTER_PERIOD = Period.days(1);
  private static final Period RECURRING_PERIOD = Period.hours(6);
  private static final DateTime CHECK_OUT_DATE = new DateTime(2019, 3, 18, 11, 43, 54, DateTimeZone.UTC);
  private static final DateTime CHECKIN_DATE = new DateTime(2019, 4, 18, 11, 43, 54, DateTimeZone.UTC);
  private static final String OVERDUE_FINE = "Overdue fine";

  private Account account;
  private FeeFineAction action;
  private DateTime actionDateTime;
  private UUID userId;
  private UUID loanId;
  private UUID actionId;
  private UUID accountId;

  static {
    TEMPLATE_IDS = new HashMap<>();
    TEMPLATE_IDS.put(UPON_AT, randomUUID());
    TEMPLATE_IDS.put(AFTER, randomUUID());
  }

  @Test
  public void oneTimeUponAtNoticeIsSentAndDeleted() {
    final NoticeTiming timing = UPON_AT;
    createOverdueFineViaCheckin(createNoticeConfig(OVERDUE_FINE_RETURNED, timing, false));

    checkScheduledNotice(TriggeringEvent.OVERDUE_FINE_RETURNED, timing, false, actionDateTime);

    scheduledNoticeProcessingClient.runFeeFineNoticesProcessing(actionDateTime.plusMinutes(1));

    checkSentNotice(TEMPLATE_IDS.get(timing));
    checkNumberOfScheduledNotices(0);
  }

  @Test
  public void recurringUponAtNoticeIsSentAndRescheduled() {
    final NoticeTiming timing = UPON_AT;
    createOverdueFineViaCheckin(createNoticeConfig(OVERDUE_FINE_RETURNED, timing, true));

    checkScheduledNotice(TriggeringEvent.OVERDUE_FINE_RETURNED, timing, true, actionDateTime);

    scheduledNoticeProcessingClient.runFeeFineNoticesProcessing(actionDateTime.plusMinutes(1));

    checkSentNotice(TEMPLATE_IDS.get(timing));
    checkNumberOfScheduledNotices(1);
    checkScheduledNotice(TriggeringEvent.OVERDUE_FINE_RETURNED, timing, true,
      actionDateTime.plus(RECURRING_PERIOD.timePeriod()));
  }

  @Test
  public void oneTimeAfterNoticeIsSentAndDeleted() {
    final NoticeTiming timing = AFTER;
    createOverdueFineViaCheckin(createNoticeConfig(OVERDUE_FINE_RETURNED, timing, false));
    DateTime nextRunTime = actionDateTime.plus(AFTER_PERIOD.timePeriod());

    checkScheduledNotice(TriggeringEvent.OVERDUE_FINE_RETURNED, timing, false, nextRunTime);

    scheduledNoticeProcessingClient.runFeeFineNoticesProcessing(nextRunTime.plusMinutes(1));

    checkSentNotice(TEMPLATE_IDS.get(timing));
    checkNumberOfScheduledNotices(0);
  }

  @Test
  public void recurringAfterNoticeIsSentAndRescheduled() {
    final NoticeTiming timing = AFTER;
    createOverdueFineViaCheckin(createNoticeConfig(OVERDUE_FINE_RETURNED, timing, true));
    DateTime nextRunTime = actionDateTime.plus(AFTER_PERIOD.timePeriod());

    checkScheduledNotice(TriggeringEvent.OVERDUE_FINE_RETURNED, timing, true, nextRunTime);

    scheduledNoticeProcessingClient.runFeeFineNoticesProcessing(nextRunTime.plusMinutes(1));

    checkSentNotice(TEMPLATE_IDS.get(timing));
    checkNumberOfScheduledNotices(1);
    checkScheduledNotice(TriggeringEvent.OVERDUE_FINE_RETURNED, timing, true,
      nextRunTime.plus(RECURRING_PERIOD.timePeriod()));
  }

  @Test
  public void recurringNoticeIsRescheduledCorrectlyWhenNextCalculatedRunTimeIsBeforeNow() {
    final NoticeTiming timing = UPON_AT;
    createOverdueFineViaCheckin(createNoticeConfig(OVERDUE_FINE_RETURNED, timing, true));

    checkScheduledNotice(TriggeringEvent.OVERDUE_FINE_RETURNED, timing, true, actionDateTime);

    final DateTime now = ClockManager.getClockManager().getDateTime();

    mockClockManagerToReturnFixedDateTime(now);

    DateTime nowPlusRecurringPeriod = now.plus(RECURRING_PERIOD.timePeriod());
    DateTime processingTime = nowPlusRecurringPeriod.plusHours(1);

    scheduledNoticeProcessingClient.runFeeFineNoticesProcessing(processingTime);

    mockClockManagerToReturnDefaultDateTime();

    checkSentNotice(TEMPLATE_IDS.get(timing));
    checkNumberOfScheduledNotices(1);
    checkScheduledNotice(TriggeringEvent.OVERDUE_FINE_RETURNED, timing, true,
      nowPlusRecurringPeriod);
  }

  @Test
  public void noticeIsDiscardedWhenReferencedActionDoesNotExist() {
    final NoticeTiming timing = UPON_AT;
    createOverdueFineViaCheckin(createNoticeConfig(OVERDUE_FINE_RETURNED, timing, false));

    checkScheduledNotice(TriggeringEvent.OVERDUE_FINE_RETURNED, timing, false, actionDateTime);

    feeFineActionsClient.delete(actionId);
    scheduledNoticeProcessingClient.runFeeFineNoticesProcessing(actionDateTime.plusMinutes(1));

    assertThatNoNoticesWereSent();
    checkNumberOfScheduledNotices(0);
  }

  @Test
  public void noticeIsDiscardedWhenReferencedAccountDoesNotExist() {
    final NoticeTiming timing = UPON_AT;
    createOverdueFineViaCheckin(createNoticeConfig(OVERDUE_FINE_RETURNED, timing, false));

    checkScheduledNotice(TriggeringEvent.OVERDUE_FINE_RETURNED, timing, false, actionDateTime);

    accountsClient.delete(accountId);
    scheduledNoticeProcessingClient.runFeeFineNoticesProcessing(actionDateTime.plusMinutes(1));

    assertThatNoNoticesWereSent();
    checkNumberOfScheduledNotices(0);
  }

  @Test
  public void oneTimeNoticeIsDiscardedWhenAccountIsClosed() {
    final NoticeTiming timing = UPON_AT;
    createOverdueFineViaCheckin(createNoticeConfig(OVERDUE_FINE_RETURNED, timing, false));

    checkScheduledNotice(TriggeringEvent.OVERDUE_FINE_RETURNED, timing, false, actionDateTime);

    JsonObject closedAccountJson = account.toJson();
    JsonPropertyWriter.writeNamedObject(closedAccountJson, "status", "Closed");

    accountsClient.replace(accountId, closedAccountJson);
    scheduledNoticeProcessingClient.runFeeFineNoticesProcessing(actionDateTime.plusMinutes(1));

    assertThatNoNoticesWereSent();
    checkNumberOfScheduledNotices(0);
  }

  public void createOverdueFineViaCheckin(JsonObject... patronNoticeConfigs) {
    use(createPatronNoticePolicy(patronNoticeConfigs));

    templateFixture.createDummyNoticeTemplate(TEMPLATE_IDS.get(UPON_AT));
    templateFixture.createDummyNoticeTemplate(TEMPLATE_IDS.get(AFTER));

    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      builder -> builder.withPrimaryServicePoint(checkInServicePointId));
    final IndividualResource borrower = usersFixture.james();
    userId = borrower.getId();
    final IndividualResource item = itemsFixture.basedUponNod(builder ->
      builder.withPermanentLocation(homeLocation.getId()));

    JsonObject servicePointOwner = new JsonObject()
      .put("value", homeLocation.getJson().getString("primaryServicePoint"))
      .put("label", "Service Desk 1");

    feeFineOwnersClient.create(new FeeFineOwnerBuilder()
      .withId(randomUUID())
      .withOwner("test owner")
      .withServicePointOwner(Collections.singletonList(servicePointOwner))
    );

    feeFinesClient.create(new FeeFineBuilder()
      .withId(randomUUID())
      .withFeeFineType(OVERDUE_FINE)
      .withAutomatic(true)
    );

    loansFixture.checkOutByBarcode(item, borrower, CHECK_OUT_DATE);

    CheckInByBarcodeResponse checkInResponse = loansFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(item)
        .on(CHECKIN_DATE)
        .at(checkInServicePointId));

    loanId = UUID.fromString(checkInResponse.getLoan().getString("id"));

    List<JsonObject> accounts = Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(accountsClient::getAll, hasSize(1));

    assertThat("Fee/fine record should have been created", accounts, hasSize(1));
    account = Account.from(accounts.get(0));
    accountId = UUID.fromString(account.getId());

    List<JsonObject> actions = Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(feeFineActionsClient::getAll, hasSize(1));

    assertThat("Fee/fine action record should have been created", actions, hasSize(1));
    action = FeeFineAction.from(actions.get(0));
    actionId = UUID.fromString(action.getId());
    actionDateTime = action.getDateAction();

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(patronNoticeConfigs.length));
  }

  private JsonObject createNoticeConfig(
    NoticeEventType eventType, NoticeTiming timing, boolean isRecurring) {

    JsonObject timingPeriod = timing == AFTER ? AFTER_PERIOD.asJson() : null;

    NoticeConfigurationBuilder builder = new NoticeConfigurationBuilder()
      .withEventType(eventType.getRepresentation())
      .withTemplateId(TEMPLATE_IDS.get(timing))
      .withTiming(timing.getRepresentation(), timingPeriod)
      .sendInRealTime(true);

    if (isRecurring) {
      builder = builder.recurring(RECURRING_PERIOD);
    }

    return builder.create();
  }

  private NoticePolicyBuilder createPatronNoticePolicy(JsonObject... noticeConfigs) {
    return new NoticePolicyBuilder()
      .withName("Patron notice policy with fee/fine notices")
      .withFeeFineNotices(Arrays.asList(noticeConfigs));
  }

  private void checkScheduledNotice(TriggeringEvent triggeringEvent,
    NoticeTiming timing, Boolean isRecurring, DateTime nextRunTime) {

    Period expectedRecurringPeriod = isRecurring ? RECURRING_PERIOD : null;

    assertThat(scheduledNoticesClient.getAll(), hasItems(
      hasScheduledFeeFineNotice(
        actionId, loanId, userId, TEMPLATE_IDS.get(timing),
        triggeringEvent, nextRunTime,
        timing, expectedRecurringPeriod, true)
    ));
  }

  private void checkSentNotice(UUID... expectedTemplateIds) {
    List<JsonObject> sentNotices = patronNoticesClient.getAll();
    assertThat(sentNotices, hasSize(expectedTemplateIds.length));

    Matcher<?> matcher = TemplateContextMatchers.getFeeFineContextMatcher(account, action);

    Stream.of(expectedTemplateIds)
      .forEach(templateId -> assertThat(sentNotices, hasItem(
          hasNoticeProperties(userId, templateId, "email", "text/html", matcher))));
  }

  private void checkNumberOfScheduledNotices(int numberOfNotices) {
    assertThat(scheduledNoticesClient.getAll(), hasSize(numberOfNotices));
  }

  private void assertThatNoNoticesWereSent() {
    assertThat(patronNoticesClient.getAll(), hasSize(0));
  }

}
