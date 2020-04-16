package api.loans;

import static api.support.matchers.PatronNoticeMatcher.hasNoticeProperties;
import static api.support.matchers.ScheduledNoticeMatchers.hasScheduledFeeFineNotice;
import static java.util.UUID.randomUUID;
import static org.folio.circulation.domain.notice.NoticeTiming.AFTER;
import static org.folio.circulation.domain.notice.NoticeTiming.UPON_AT;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.OVERDUE_FINE_RENEWED;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.OVERDUE_FINE_RETURNED;
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
import org.hamcrest.Matcher;
import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import api.support.APITests;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.FeeFineBuilder;
import api.support.builders.FeeFineOwnerBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.fixtures.TemplateContextMatchers;
import io.vertx.core.json.JsonObject;

@RunWith(value = Parameterized.class)
public class FeeFineScheduledNoticesProcessingTests extends APITests {
  private static final Period AFTER_PERIOD = Period.days(1);
  private static final Period RECURRING_PERIOD = Period.hours(6);
  private static final String OVERDUE_FINE = "Overdue fine";
  private static final Map<NoticeTiming, UUID> TEMPLATE_IDS = new HashMap<>();

  private Account account;
  private FeeFineAction action;
  private DateTime actionDateTime;
  private UUID loanId;
  private UUID userId;
  private UUID actionId;
  private UUID accountId;

  private final TriggeringEvent triggeringEvent;

  public FeeFineScheduledNoticesProcessingTests(TriggeringEvent triggeringEvent) {
    this.triggeringEvent = triggeringEvent;
  }

  static {
    TEMPLATE_IDS.put(UPON_AT, randomUUID());
    TEMPLATE_IDS.put(AFTER, randomUUID());
  }

  @Parameterized.Parameters
  public static Object[] parameters() {
    return new Object[]{OVERDUE_FINE_RETURNED, OVERDUE_FINE_RENEWED};
  }

  @Test
  public void uponAtNoticeIsSentAndDeleted() {
    generateOverdueFine(createNoticeConfig(UPON_AT, false));

    checkNumberOfScheduledNotices(1);
    checkScheduledNotice(UPON_AT, false, actionDateTime);

    scheduledNoticeProcessingClient.runFeeFineNoticesProcessing(actionDateTime.plusMinutes(1));

    checkSentNotice(TEMPLATE_IDS.get(UPON_AT));
    checkNumberOfScheduledNotices(0);
  }

  @Test
  public void oneTimeAfterNoticeIsSentAndDeleted() {
    generateOverdueFine(createNoticeConfig(AFTER, false));
    DateTime nextRunTime = actionDateTime.plus(AFTER_PERIOD.timePeriod());

    checkNumberOfScheduledNotices(1);
    checkScheduledNotice(AFTER, false, nextRunTime);

    scheduledNoticeProcessingClient.runFeeFineNoticesProcessing(nextRunTime.plusMinutes(1));

    checkSentNotice(TEMPLATE_IDS.get(AFTER));
    checkNumberOfScheduledNotices(0);
  }

  @Test
  public void recurringAfterNoticeIsSentAndRescheduled() {
    generateOverdueFine(createNoticeConfig(AFTER, true));
    DateTime nextRunTime = actionDateTime.plus(AFTER_PERIOD.timePeriod());

    checkNumberOfScheduledNotices(1);
    checkScheduledNotice(AFTER, true, nextRunTime);

    scheduledNoticeProcessingClient.runFeeFineNoticesProcessing(nextRunTime.plusMinutes(1));

    checkSentNotice(TEMPLATE_IDS.get(AFTER));
    checkNumberOfScheduledNotices(1);
    checkScheduledNotice(AFTER, true, nextRunTime.plus(RECURRING_PERIOD.timePeriod()));
  }

  @Test
  public void recurringNoticeIsRescheduledCorrectlyWhenNextCalculatedRunTimeIsBeforeNow() {
    generateOverdueFine(createNoticeConfig(AFTER, true));
    DateTime firstScheduledRunTime = actionDateTime.plus(AFTER_PERIOD.timePeriod());

    checkNumberOfScheduledNotices(1);
    checkScheduledNotice(AFTER, true, firstScheduledRunTime);

    DateTime fakeProcessingTime = firstScheduledRunTime
      .plus(RECURRING_PERIOD.timePeriod())
      .plusHours(1);

    mockClockManagerToReturnFixedDateTime(fakeProcessingTime);
    scheduledNoticeProcessingClient.runFeeFineNoticesProcessing(fakeProcessingTime);
    mockClockManagerToReturnDefaultDateTime();

    DateTime expectedNextRunTime = fakeProcessingTime.plus(RECURRING_PERIOD.timePeriod());

    checkSentNotice(TEMPLATE_IDS.get(AFTER));
    checkNumberOfScheduledNotices(1);
    checkScheduledNotice(AFTER, true, expectedNextRunTime);
  }

  @Test
  public void multipleScheduledNoticesAreSentDuringSingleProcessingIteration() {
    generateOverdueFine(
      createNoticeConfig(UPON_AT, false),
      createNoticeConfig(AFTER, false),
      createNoticeConfig(AFTER, true)
    );

    DateTime firstAfterRunTime = actionDateTime.plus(AFTER_PERIOD.timePeriod());

    checkNumberOfScheduledNotices(3);
    checkScheduledNotice(UPON_AT, false, actionDateTime);
    checkScheduledNotice(AFTER, false, firstAfterRunTime);
    checkScheduledNotice(AFTER, true, firstAfterRunTime);

    scheduledNoticeProcessingClient.runFeeFineNoticesProcessing(firstAfterRunTime.plusMinutes(1));

    checkSentNotice(TEMPLATE_IDS.get(UPON_AT), TEMPLATE_IDS.get(AFTER), TEMPLATE_IDS.get(AFTER));
    checkNumberOfScheduledNotices(1);
    checkScheduledNotice(AFTER, true, firstAfterRunTime.plus(RECURRING_PERIOD.timePeriod()));
  }

  @Test
  public void noticeIsDiscardedWhenReferencedActionDoesNotExist() {
    generateOverdueFine(createNoticeConfig(UPON_AT, false));

    checkNumberOfScheduledNotices(1);
    checkScheduledNotice(UPON_AT, false, actionDateTime);

    feeFineActionsClient.delete(actionId);
    scheduledNoticeProcessingClient.runFeeFineNoticesProcessing(actionDateTime.plusMinutes(1));

    assertThatNoNoticesWereSent();
    checkNumberOfScheduledNotices(0);
  }

  @Test
  public void noticeIsDiscardedWhenReferencedAccountDoesNotExist() {
    generateOverdueFine(createNoticeConfig(UPON_AT, false));

    checkNumberOfScheduledNotices(1);
    checkScheduledNotice(UPON_AT, false, actionDateTime);

    accountsClient.delete(accountId);
    scheduledNoticeProcessingClient.runFeeFineNoticesProcessing(actionDateTime.plusMinutes(1));

    assertThatNoNoticesWereSent();
    checkNumberOfScheduledNotices(0);
  }

  @Test
  public void noticeIsDiscardedWhenAccountIsClosed() {
    generateOverdueFine(createNoticeConfig(UPON_AT, false));

    checkNumberOfScheduledNotices(1);
    checkScheduledNotice(UPON_AT, false, actionDateTime);

    JsonObject closedAccountJson = account.toJson();
    JsonPropertyWriter.writeNamedObject(closedAccountJson, "status", "Closed");

    accountsClient.replace(accountId, closedAccountJson);
    scheduledNoticeProcessingClient.runFeeFineNoticesProcessing(actionDateTime.plusMinutes(1));

    assertThatNoNoticesWereSent();
    checkNumberOfScheduledNotices(0);
  }

  public void generateOverdueFine(JsonObject... patronNoticeConfigs) {
    NoticePolicyBuilder noticePolicyBuilder = new NoticePolicyBuilder()
      .withName("Patron notice policy with fee/fine notices")
      .withFeeFineNotices(Arrays.asList(patronNoticeConfigs));

    use(noticePolicyBuilder);

    templateFixture.createDummyNoticeTemplate(TEMPLATE_IDS.get(UPON_AT));
    templateFixture.createDummyNoticeTemplate(TEMPLATE_IDS.get(AFTER));

    UUID checkInServicePointId = servicePointsFixture.cd1().getId();
    IndividualResource location = locationsFixture.basedUponExampleLocation(
      builder -> builder.withPrimaryServicePoint(checkInServicePointId));
    IndividualResource user = usersFixture.james();
    userId = user.getId();
    IndividualResource item = itemsFixture.basedUponNod(builder ->
      builder.withPermanentLocation(location.getId()));

    JsonObject servicePointOwner = new JsonObject()
      .put("value", checkInServicePointId.toString())
      .put("label", "Service Desk 1");

    feeFineOwnersClient.create(new FeeFineOwnerBuilder()
      .withId(randomUUID())
      .withOwner("test owner")
      .withServicePointOwner(Collections.singletonList(servicePointOwner)));

    feeFinesClient.create(new FeeFineBuilder()
      .withId(randomUUID())
      .withFeeFineType(OVERDUE_FINE)
      .withAutomatic(true));

    final DateTime checkOutDate = ClockManager.getClockManager().getDateTime().minusYears(1);
    final DateTime checkInDate = checkOutDate.plusMonths(1);

    IndividualResource checkOutResponse = loansFixture.checkOutByBarcode(item, user, checkOutDate);
    loanId = UUID.fromString(checkOutResponse.getJson().getString("id"));

    switch (triggeringEvent) {
    case OVERDUE_FINE_RETURNED:
      loansFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
        .forItem(item)
        .on(checkInDate)
        .at(checkInServicePointId));
      break;
    case OVERDUE_FINE_RENEWED:
      loansFixture.renewLoan(item, user);
      break;
    }

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
  }

  private JsonObject createNoticeConfig(NoticeTiming timing, boolean isRecurring) {
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

  private void checkScheduledNotice(NoticeTiming timing, Boolean recurring, DateTime nextRunTime) {
    Period expectedRecurringPeriod = recurring ? RECURRING_PERIOD : null;

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
