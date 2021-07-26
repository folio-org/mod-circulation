package api.loans;

import static api.support.fixtures.TemplateContextMatchers.getLoanPolicyContextMatchersForUnlimitedRenewals;
import static api.support.fixtures.TemplateContextMatchers.getMultipleLoansContextMatcher;
import static api.support.matchers.JsonObjectMatcher.toStringMatcher;
import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfPublishedEvents;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfScheduledNotices;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfSentNotices;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE_ERROR;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import org.apache.commons.lang3.tuple.Pair;
import org.folio.circulation.domain.policy.Period;
import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalTime;
import org.junit.Before;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.fakes.FakeModNotify;
import api.support.fakes.FakePubSub;
import api.support.fixtures.ConfigurationExample;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import io.vertx.core.json.JsonObject;
import lombok.val;

public class DueDateNotRealTimeScheduledNoticesProcessingTests extends APITests {
  private final static UUID TEMPLATE_ID = UUID.randomUUID();

  @Before
  public void setUp() {
    templateFixture.createDummyNoticeTemplate(TEMPLATE_ID);
  }

  @Test
  public void uponAtDueDateNoticesShouldBeSentInGroups() {
    JsonObject uponAtDueDateNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(TEMPLATE_ID)
      .withDueDateEvent()
      .withUponAtTiming()
      .sendInRealTime(false)
      .create();
    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with due date notices")
      .withLoanNotices(Collections.singletonList(uponAtDueDateNoticeConfig));
    use(noticePolicy);

    DateTime loanDate = new DateTime(2019, 8, 23, 10, 30);

    IndividualResource james = usersFixture.james();
    ItemResource nod = itemsFixture.basedUponNod();
    ItemResource interestingTimes = itemsFixture.basedUponInterestingTimes();
    IndividualResource nodToJamesLoan = checkOutFixture.checkOutByBarcode(nod, james, loanDate);
    IndividualResource interestingTimesToJamesLoan = checkOutFixture.checkOutByBarcode(interestingTimes, james, loanDate);

    IndividualResource rebecca = usersFixture.rebecca();
    ItemResource temeraire = itemsFixture.basedUponTemeraire();
    ItemResource dunkirk = itemsFixture.basedUponDunkirk();
    IndividualResource temeraireToRebeccaLoan = checkOutFixture.checkOutByBarcode(temeraire, rebecca, loanDate);
    IndividualResource dunkirkToRebeccaLoan = checkOutFixture.checkOutByBarcode(dunkirk, rebecca, loanDate);

    verifyNumberOfScheduledNotices(4);

    DateTime dueDate = new DateTime(nodToJamesLoan.getJson().getString("dueDate"));
    DateTime afterLoanDueDateTime = dueDate.plusDays(1);

    scheduledNoticeProcessingClient.runDueDateNotRealTimeNoticesProcessing(afterLoanDueDateTime);

    final var loanPolicyMatcher = toStringMatcher(getLoanPolicyContextMatchersForUnlimitedRenewals());

    final var noticeToJamesContextMatcher =
      getMultipleLoansContextMatcher(
        james,
        Arrays.asList(
          Pair.of(nodToJamesLoan, nod),
          Pair.of(interestingTimesToJamesLoan, interestingTimes)),
      loanPolicyMatcher);

    final var noticeToRebeccaContextMatcher =
      getMultipleLoansContextMatcher(
        rebecca,
        Arrays.asList(
          Pair.of(temeraireToRebeccaLoan, temeraire),
          Pair.of(dunkirkToRebeccaLoan, dunkirk)),
        loanPolicyMatcher);

    assertThat(FakeModNotify.getSentPatronNotices(), hasItems(
      hasEmailNoticeProperties(james.getId(), TEMPLATE_ID, noticeToJamesContextMatcher),
      hasEmailNoticeProperties(rebecca.getId(), TEMPLATE_ID, noticeToRebeccaContextMatcher)));

    verifyNumberOfSentNotices(2);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  public void beforeRecurringNoticesAreRescheduled() {
    configClient.create(ConfigurationExample.utcTimezoneConfiguration());

    Period beforePeriod = Period.weeks(1);
    Period recurringPeriod = Period.days(1);

    JsonObject uponAtDueDateNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(TEMPLATE_ID)
      .withDueDateEvent()
      .withBeforeTiming(beforePeriod)
      .recurring(recurringPeriod)
      .sendInRealTime(false)
      .create();

    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with due date notices")
      .withLoanNotices(Collections.singletonList(uponAtDueDateNoticeConfig));
    use(noticePolicy);

    DateTime loanDate = new DateTime(2019, 8, 23, 10, 30);

    IndividualResource james = usersFixture.james();
    IndividualResource nodToJamesLoan = checkOutFixture.checkOutByBarcode(itemsFixture.basedUponNod(), james, loanDate);
    checkOutFixture.checkOutByBarcode(itemsFixture.basedUponInterestingTimes(), james, loanDate);

    verifyNumberOfScheduledNotices(2);

    DateTime dueDate = new DateTime(nodToJamesLoan.getJson().getString("dueDate"));

    DateTime timeForNoticeToBeSent = dueDate.minusWeeks(1);
    DateTime nextDayAfterBeforeNoticeShouldBeSend = timeForNoticeToBeSent.withTime(LocalTime.MIDNIGHT).plusDays(1);

    scheduledNoticeProcessingClient.runDueDateNotRealTimeNoticesProcessing(nextDayAfterBeforeNoticeShouldBeSend);

    DateTime newNextRunTime = timeForNoticeToBeSent.plus(recurringPeriod.timePeriod());

    assertTrue("all scheduled notices are rescheduled", scheduledNoticesClient.getAll().stream()
      .map(entries -> entries.getString("nextRunTime"))
      .map(DateTime::parse)
      .allMatch(newNextRunTime::isEqual));

    verifyNumberOfSentNotices(1);
    verifyNumberOfScheduledNotices(2);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  public void beforeNoticesAreNotSentIfLoanIsClosed() {
    Period beforePeriod = Period.weeks(1);

    JsonObject uponAtDueDateNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(TEMPLATE_ID)
      .withDueDateEvent()
      .withBeforeTiming(beforePeriod)
      .sendInRealTime(false)
      .create();

    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with due date notices")
      .withLoanNotices(Collections.singletonList(uponAtDueDateNoticeConfig));
    use(noticePolicy);

    DateTime loanDate = new DateTime(2019, 8, 23, 10, 30);

    IndividualResource james = usersFixture.james();
    ItemResource nod = itemsFixture.basedUponNod();
    IndividualResource nodToJamesLoan = checkOutFixture.checkOutByBarcode(nod, james, loanDate);

    verifyNumberOfScheduledNotices(1);

    DateTime dueDate = new DateTime(nodToJamesLoan.getJson().getString("dueDate"));

    checkInFixture.checkInByBarcode(nod);

    DateTime timeForNoticeToBeSent = dueDate.minusWeeks(1);
    DateTime nextDayAfterBeforeNoticeShouldBeSend = timeForNoticeToBeSent.withTime(LocalTime.MIDNIGHT).plusDays(1);

    scheduledNoticeProcessingClient.runDueDateNotRealTimeNoticesProcessing(nextDayAfterBeforeNoticeShouldBeSend);

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  public void processingTakesNoticesLimitedByConfiguration() {
    Period beforePeriod = Period.weeks(1);

    JsonObject uponAtDueDateNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(TEMPLATE_ID)
      .withDueDateEvent()
      .withBeforeTiming(beforePeriod)
      .sendInRealTime(false)
      .create();

    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with due date notices")
      .withLoanNotices(Collections.singletonList(uponAtDueDateNoticeConfig));

    use(noticePolicy);

    IndividualResource james = usersFixture.james();
    IndividualResource steve = usersFixture.steve();
    IndividualResource rebecca = usersFixture.rebecca();

    //Generate several loans
    for (int i = 0; i < 4; i++) {
      String baseBarcode = Integer.toString(i);
      checkOutFixture.checkOutByBarcode(
        itemsFixture.basedUponNod(b -> b.withBarcode(baseBarcode + "1")), james);
      checkOutFixture.checkOutByBarcode(
        itemsFixture.basedUponNod((b -> b.withBarcode(baseBarcode + "2"))), steve);
      checkOutFixture.checkOutByBarcode(
        itemsFixture.basedUponNod((b -> b.withBarcode(baseBarcode + "3"))), rebecca);
    }

    verifyNumberOfScheduledNotices(12);

    int noticesLimitConfig = 10;
    configClient.create(ConfigurationExample.schedulerNoticesLimitConfiguration(Integer.toString(noticesLimitConfig)));

    //Should fetch 10 notices, when total records is 12
    //So that notices for one of the users should not be processed
    final DateTime runTime = DateTime.now(DateTimeZone.UTC).plusDays(15);
    mockClockManagerToReturnFixedDateTime(runTime);

    scheduledNoticeProcessingClient.runDueDateNotRealTimeNoticesProcessing(runTime);

    long numberOfUniqueUserIds = scheduledNoticesClient.getAll().stream()
      .map(notice -> notice.getString("recipientUserId"))
      .distinct().count();

    assertThat(numberOfUniqueUserIds, is(1L));

    verifyNumberOfSentNotices(2);
    verifyNumberOfScheduledNotices(4);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  public void noticeIsDeletedIfReferencedLoanDoesNotExist() {
    JsonObject uponAtDueDateNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(TEMPLATE_ID)
      .withDueDateEvent()
      .withUponAtTiming()
      .sendInRealTime(false)
      .create();

    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with due date notices")
      .withLoanNotices(Collections.singletonList(uponAtDueDateNoticeConfig));

    use(noticePolicy);

    DateTime loanDate = new DateTime(2019, 8, 23, 10, 30);

    IndividualResource james = usersFixture.james();
    ItemResource nod = itemsFixture.basedUponNod();
    IndividualResource nodToJamesLoan = checkOutFixture.checkOutByBarcode(nod, james, loanDate);

    loansStorageClient.delete(nodToJamesLoan);

    verifyNumberOfScheduledNotices(1);

    DateTime dueDate = new DateTime(nodToJamesLoan.getJson().getString("dueDate"));
    DateTime afterLoanDueDateTime = dueDate.plusDays(1);

    scheduledNoticeProcessingClient.runDueDateNotRealTimeNoticesProcessing(afterLoanDueDateTime);

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  public void noticeIsDeletedIfReferencedItemDoesNotExist() {
    JsonObject uponAtDueDateNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(TEMPLATE_ID)
      .withDueDateEvent()
      .withUponAtTiming()
      .sendInRealTime(false)
      .create();

    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with due date notices")
      .withLoanNotices(Collections.singletonList(uponAtDueDateNoticeConfig));

    use(noticePolicy);

    DateTime loanDate = new DateTime(2019, 8, 23, 10, 30);

    IndividualResource james = usersFixture.james();
    ItemResource nod = itemsFixture.basedUponNod();
    IndividualResource nodToJamesLoan = checkOutFixture.checkOutByBarcode(nod, james, loanDate);

    itemsClient.delete(nod);

    verifyNumberOfScheduledNotices(1);

    DateTime dueDate = new DateTime(nodToJamesLoan.getJson().getString("dueDate"));
    DateTime afterLoanDueDateTime = dueDate.plusDays(1);

    scheduledNoticeProcessingClient.runDueDateNotRealTimeNoticesProcessing(afterLoanDueDateTime);

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  public void noticeIsDeletedIfReferencedUserDoesNotExist() {
    JsonObject uponAtDueDateNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(TEMPLATE_ID)
      .withDueDateEvent()
      .withUponAtTiming()
      .sendInRealTime(false)
      .create();

    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with due date notices")
      .withLoanNotices(Collections.singletonList(uponAtDueDateNoticeConfig));

    use(noticePolicy);

    DateTime loanDate = new DateTime(2019, 8, 23, 10, 30);

    val james = usersFixture.james();
    val nod = itemsFixture.basedUponNod();
    val nodToJamesLoan = checkOutFixture.checkOutByBarcode(nod, james, loanDate);

    usersFixture.remove(james);

    verifyNumberOfScheduledNotices(1);

    DateTime dueDate = new DateTime(nodToJamesLoan.getJson().getString("dueDate"));
    DateTime afterLoanDueDateTime = dueDate.plusDays(1);

    scheduledNoticeProcessingClient.runDueDateNotRealTimeNoticesProcessing(afterLoanDueDateTime);

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  public void missingReferencedEntitiesDoNotBlockProcessing() {
    JsonObject uponAtDueDateNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(TEMPLATE_ID)
      .withDueDateEvent()
      .withUponAtTiming()
      .sendInRealTime(false)
      .create();

    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with due date notices")
      .withLoanNotices(Collections.singletonList(uponAtDueDateNoticeConfig));

    use(noticePolicy);

    DateTime loanDate = new DateTime(2019, 8, 23, 10, 30);

    // users
    val james = usersFixture.james();
    val steve = usersFixture.steve();
    val jessica = usersFixture.jessica();

    // items
    ItemResource nod = itemsFixture.basedUponNod();
    ItemResource temeraire = itemsFixture.basedUponTemeraire();
    ItemResource planet = itemsFixture.basedUponSmallAngryPlanet();
    ItemResource times = itemsFixture.basedUponInterestingTimes();
    ItemResource uprooted = itemsFixture.basedUponUprooted();
    ItemResource dunkirk = itemsFixture.basedUponDunkirk();

    // loans
    IndividualResource nodToJames = checkOutFixture.checkOutByBarcode(nod, james, loanDate.plusHours(1));
    IndividualResource temeraireToJames = checkOutFixture.checkOutByBarcode(temeraire, james, loanDate.plusHours(2));
    IndividualResource planetToJames = checkOutFixture.checkOutByBarcode(planet, james, loanDate.plusHours(3));
    checkOutFixture.checkOutByBarcode(times, steve, loanDate.plusHours(4));
    IndividualResource uprootedToSteve = checkOutFixture.checkOutByBarcode(uprooted, steve, loanDate.plusHours(5));
    checkOutFixture.checkOutByBarcode(dunkirk, jessica, loanDate.plusHours(6));

    loansClient.delete(temeraireToJames);
    itemsClient.delete(times);
    usersFixture.remove(jessica);

    verifyNumberOfScheduledNotices(6);

    DateTime dueDate = new DateTime(nodToJames.getJson().getString("dueDate"));

    scheduledNoticeProcessingClient.runDueDateNotRealTimeNoticesProcessing(dueDate.plusDays(1));

    Matcher<? super String> loanPolicyMatcher = toStringMatcher(getLoanPolicyContextMatchersForUnlimitedRenewals());

    Matcher<? super String> noticeToJamesContextMatcher =
      getMultipleLoansContextMatcher(
        james,
        Arrays.asList(
          Pair.of(nodToJames, nod),
          Pair.of(planetToJames, planet)),
        loanPolicyMatcher);

    Matcher<? super String> noticeToSteveContextMatcher =
      getMultipleLoansContextMatcher(
        steve,
        Collections.singletonList(
          Pair.of(uprootedToSteve, uprooted)),
        loanPolicyMatcher);

    MatcherAssert.assertThat(FakeModNotify.getSentPatronNotices(), hasItems(
      hasEmailNoticeProperties(james.getId(), TEMPLATE_ID, noticeToJamesContextMatcher),
      hasEmailNoticeProperties(steve.getId(), TEMPLATE_ID, noticeToSteveContextMatcher)));

    verifyNumberOfSentNotices(2);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 3);
  }

  @Test
  public void noticeIsDeletedIfReferencedTemplateDoesNotExist() {
    JsonObject uponAtDueDateNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(TEMPLATE_ID)
      .withDueDateEvent()
      .withUponAtTiming()
      .sendInRealTime(false)
      .create();

    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with due date notices")
      .withLoanNotices(Collections.singletonList(uponAtDueDateNoticeConfig));

    use(noticePolicy);

    DateTime loanDate = new DateTime(2019, 8, 23, 10, 30);

    IndividualResource james = usersFixture.james();
    ItemResource nod = itemsFixture.basedUponNod();
    IndividualResource nodToJamesLoan = checkOutFixture.checkOutByBarcode(nod, james, loanDate);

    templateFixture.delete(TEMPLATE_ID);

    verifyNumberOfScheduledNotices(1);

    DateTime dueDate = new DateTime(nodToJamesLoan.getJson().getString("dueDate"));
    DateTime afterLoanDueDateTime = dueDate.plusDays(1);

    scheduledNoticeProcessingClient.runDueDateNotRealTimeNoticesProcessing(afterLoanDueDateTime);

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  public void noticeIsNotSentOrDeletedWhenPatronNoticeRequestFails() {
    JsonObject uponAtDueDateNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(TEMPLATE_ID)
      .withDueDateEvent()
      .withUponAtTiming()
      .sendInRealTime(false)
      .create();

    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with due date notices")
      .withLoanNotices(Collections.singletonList(uponAtDueDateNoticeConfig));

    use(noticePolicy);

    DateTime loanDate = new DateTime(2019, 8, 23, 10, 30);

    IndividualResource james = usersFixture.james();
    ItemResource nod = itemsFixture.basedUponNod();
    IndividualResource nodToJamesLoan = checkOutFixture.checkOutByBarcode(nod, james, loanDate);

    verifyNumberOfScheduledNotices(1);

    DateTime dueDate = new DateTime(nodToJamesLoan.getJson().getString("dueDate"));
    DateTime afterLoanDueDateTime = dueDate.plusDays(1);

    FakeModNotify.setFailPatronNoticesWithBadRequest(true);

    scheduledNoticeProcessingClient.runDueDateNotRealTimeNoticesProcessing(afterLoanDueDateTime);

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  public void scheduledNotRealTimeNoticesShouldBeSentOnlyOncePerDayIfPubSubReturnsError() {
    JsonObject afterDueDateNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(TEMPLATE_ID)
      .withDueDateEvent()
      .withAfterTiming(Period.days(1))
      .recurring(Period.days(1))
      .sendInRealTime(false)
      .create();

    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with due date notices")
      .withLoanNotices(Collections.singletonList(afterDueDateNoticeConfig));
    use(noticePolicy);

    DateTime loanDate = DateTime.now().minusMonths(1);

    IndividualResource steve = usersFixture.steve();
    ItemResource dunkirk = itemsFixture.basedUponDunkirk();
    checkOutFixture.checkOutByBarcode(dunkirk, steve, loanDate);

    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(0);

    FakePubSub.setFailPublishingWithBadRequestError(true);

    scheduledNoticeProcessingClient.runDueDateNotRealTimeNoticesProcessing();
    scheduledNoticeProcessingClient.runDueDateNotRealTimeNoticesProcessing();

    verifyNumberOfSentNotices(1);
    verifyNumberOfScheduledNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    FakePubSub.setFailPublishingWithBadRequestError(false);
  }

  @Test
  public void scheduledNotRealTimeNoticesIsSentAfterMidnightInTenantsTimeZone() {
    // A notice should be sent when the processing is run one minute after
    // midnight (in tenant's time zone)
    scheduledNotRealTimeNoticesShouldBeSentAtMidnightInTenantsTimeZone(1, 0, 1);
  }

  @Test
  public void scheduledNotRealTimeNoticesIsNotSentBeforeMidnightInTenantsTimeZone() {
    scheduledNotRealTimeNoticesShouldBeSentAtMidnightInTenantsTimeZone(-1, 1, 0);
  }

  private void scheduledNotRealTimeNoticesShouldBeSentAtMidnightInTenantsTimeZone(
    int plusMinutes, int scheduledNoticesNumber, int sentNoticesNumber) {

    String timeZoneId = "America/New_York";
    DateTime systemTime = new DateTime(2020, 6, 25, 0, 0)
      .plusMinutes(plusMinutes)
      .withZoneRetainFields(DateTimeZone.forID(timeZoneId));
    mockClockManagerToReturnFixedDateTime(systemTime);
    configClient.create(ConfigurationExample.timezoneConfigurationFor(timeZoneId));

    JsonObject uponAtDueDateNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(TEMPLATE_ID)
      .withDueDateEvent()
      .withUponAtTiming()
      .sendInRealTime(false)
      .create();

    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with due date notices")
      .withLoanNotices(Collections.singletonList(uponAtDueDateNoticeConfig));
    use(noticePolicy);

    DateTime loanDate = new DateTime(2020, 6, 3, 6, 0)
      .withZoneRetainFields(DateTimeZone.forID(timeZoneId));

    IndividualResource james = usersFixture.james();
    ItemResource nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod, james, loanDate);

    verifyNumberOfScheduledNotices(1);

    scheduledNoticeProcessingClient.runDueDateNotRealTimeNoticesProcessing(systemTime);

    verifyNumberOfSentNotices(sentNoticesNumber);
    verifyNumberOfScheduledNotices(scheduledNoticesNumber);
    verifyNumberOfPublishedEvents(NOTICE, sentNoticesNumber);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }
}
