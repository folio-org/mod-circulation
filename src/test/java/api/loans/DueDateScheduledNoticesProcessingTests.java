package api.loans;

import static api.support.fakes.PublishedEvents.byLogEventType;
import static api.support.fixtures.ItemExamples.basedUponSmallAngryPlanet;
import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.matchers.ScheduledNoticeMatchers.hasScheduledLoanNotice;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfPublishedEvents;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfSentNotices;
import static java.util.Comparator.comparing;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE_ERROR;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.awaitility.Awaitility;
import org.folio.circulation.domain.policy.Period;
import org.hamcrest.Matcher;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.HoldingBuilder;
import api.support.builders.ItemBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.fakes.FakePubSub;
import api.support.fixtures.ConfigurationExample;
import api.support.fixtures.TemplateContextMatchers;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.UserResource;
import io.vertx.core.json.JsonObject;

public class DueDateScheduledNoticesProcessingTests extends APITests {
  private static final String BEFORE_TIMING = "Before";
  private static final String UPON_AT_TIMING = "Upon At";
  private static final String AFTER_TIMING = "After";

  private static final int SCHEDULED_NOTICES_PROCESSING_LIMIT = 100;
  private static final String NEXT_RUN_TIME = "nextRunTime";


  private final UUID beforeTemplateId = UUID.randomUUID();
  private final Period beforePeriod = Period.days(2);
  private final Period beforeRecurringPeriod = Period.hours(6);

  private final UUID uponAtTemplateId = UUID.randomUUID();

  private final UUID afterTemplateId = UUID.randomUUID();
  private final Period afterPeriod = Period.days(3);
  private final Period afterRecurringPeriod = Period.hours(4);

  private final DateTime loanDate = new DateTime(2018, 3, 18, 11, 43, 54, DateTimeZone.UTC);

  private ItemResource item;
  private UserResource borrower;
  private IndividualResource loan;
  private DateTime dueDate;

  @Before
  public void beforeEach() {
    FakePubSub.clearPublishedEvents();

    ItemBuilder itemBuilder = basedUponSmallAngryPlanet(
      materialTypesFixture.book().getId(), loanTypesFixture.canCirculate().getId());

    HoldingBuilder holdingBuilder = itemsFixture.applyCallNumberHoldings(
      "CN",
      "Prefix",
      "Suffix",
      Collections.singletonList("CopyNumbers"));

    item = itemsFixture.basedUponSmallAngryPlanet(itemBuilder, holdingBuilder);
    borrower = usersFixture.steve();
  }

  @Test
  public void beforeNoticeShouldBeSentAndItsNextRunTimeShouldBeUpdated() {
    generateLoanAndScheduledNotices(true, false, false);

    DateTime beforeDueDateTime = dueDate.minus(beforePeriod.timePeriod()).plusSeconds(1);
    templateFixture.createDummyNoticeTemplate(beforeTemplateId);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(beforeDueDateTime);
    checkSentNotices(beforeTemplateId);

    DateTime expectedNewRunTimeForBeforeNotice = dueDate
      .minus(beforePeriod.timePeriod())
      .plus(beforeRecurringPeriod.timePeriod());

    checkScheduledNotices(
      expectedNewRunTimeForBeforeNotice,
      null,
      null);

    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  public void beforeNoticeShouldBeSendAndDeletedWhenItsNextRunTimeIsAfterDueDate() {
    generateLoanAndScheduledNotices(true, false, false);

    DateTime justBeforeDueDateTime = dueDate.minusSeconds(1);
    templateFixture.createDummyNoticeTemplate(beforeTemplateId);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(justBeforeDueDateTime);

    checkSentNotices(beforeTemplateId);

    checkScheduledNotices(
      null,
      null,
      null);

    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  public void uponAtNoticeShouldBeSentWhenProcessingJustAfterDueDate() {
    generateLoanAndScheduledNotices(false, true, false);

    DateTime justAfterDueDateTime = dueDate.plusSeconds(1);
    templateFixture.createDummyNoticeTemplate(uponAtTemplateId);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(justAfterDueDateTime);

    checkSentNotices(uponAtTemplateId);

    checkScheduledNotices(
      null,
      null,
      null);

    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  public void afterRecurringNoticeShouldBeSentSeveralTimesBeforeLoanIsClosed() {
    generateLoanAndScheduledNotices(false, false, true);

    DateTime justAfterDueDateTime = dueDate.plusSeconds(1);
    templateFixture.createDummyNoticeTemplate(afterTemplateId);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(justAfterDueDateTime);
    //Clear all sent notices before actual test
    patronNoticesClient.deleteAll();

    DateTime afterNoticeRunTime = dueDate.plus(afterPeriod.timePeriod()).plusSeconds(1);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(afterNoticeRunTime);

    DateTime afterNoticeExpectedRunTime = dueDate
      .plus(afterPeriod.timePeriod())
      .plus(afterRecurringPeriod.timePeriod());

    checkScheduledNotices(
      null,
      null,
      afterNoticeExpectedRunTime);

    //Run again to send recurring notice
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(
      afterNoticeExpectedRunTime.plusSeconds(1));

    checkSentNotices(afterTemplateId, afterTemplateId);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    DateTime secondRecurringRunTime =
      afterNoticeExpectedRunTime.plus(afterRecurringPeriod.timePeriod());

    checkScheduledNotices(
      null,
      null,
      secondRecurringRunTime);

    checkInFixture.checkInByBarcode(item);
    //Clear sent notices again
    patronNoticesClient.deleteAll();
    FakePubSub.clearPublishedEvents();

    //Run after loan is closed
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(
      secondRecurringRunTime.plusSeconds(1));

    checkSentNotices();
    checkScheduledNotices(null, null, null);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  public void processingTakesNoticesInThePastLimitedAndOrdered() {
    generateLoanAndScheduledNotices(false, false, false);

    DateTime systemTime = DateTime.now(DateTimeZone.UTC);
    int expectedNumberOfUnprocessedNoticesInThePast = 10;
    int numberOfNoticesInThePast =
      SCHEDULED_NOTICES_PROCESSING_LIMIT + expectedNumberOfUnprocessedNoticesInThePast;
    int numberOfNoticesInTheFuture = 20;

    List<JsonObject> noticesInThePast =
      createNoticesOverTime(systemTime::minusHours, numberOfNoticesInThePast);
    List<JsonObject> noticesInTheFuture =
      createNoticesOverTime(systemTime::plusHours, numberOfNoticesInTheFuture);

    List<JsonObject> allScheduledNotices = new ArrayList<>(noticesInThePast);
    allScheduledNotices.addAll(noticesInTheFuture);
    for (JsonObject notice : allScheduledNotices) {
      scheduledNoticesClient.create(notice);
    }

    scheduledNoticeProcessingClient.runLoanNoticesProcessing();
    List<JsonObject> unprocessedScheduledNotices = scheduledNoticesClient.getAll();

    Comparator<JsonObject> nextRunTimeComparator =
      comparing(json -> getDateTimeProperty(json, NEXT_RUN_TIME));
    JsonObject[] expectedUnprocessedNoticesInThePast = noticesInThePast.stream()
      .sorted(nextRunTimeComparator.reversed())
      .limit(expectedNumberOfUnprocessedNoticesInThePast)
      .toArray(JsonObject[]::new);

    assertThat(unprocessedScheduledNotices,
      hasSize(expectedNumberOfUnprocessedNoticesInThePast + numberOfNoticesInTheFuture));
    assertThat(unprocessedScheduledNotices, hasItems(expectedUnprocessedNoticesInThePast));
    assertThat(unprocessedScheduledNotices, hasItems(noticesInTheFuture.toArray(new JsonObject[0])));

    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  public void testNumberOfProcessedNoticesWithSchedulerNoticesLimitConfiguration() {
    generateLoanAndScheduledNotices(false, false, false);

    int noticesLimitConfig = 200;
    int numberOfNotices = 300;
    int expectedNumberOfUnprocessedNotices = numberOfNotices - noticesLimitConfig;

    // create a new configuration
    configClient.create(ConfigurationExample.schedulerNoticesLimitConfiguration(Integer.toString(noticesLimitConfig)));

    createNotices(numberOfNotices);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing();
    List<JsonObject> unprocessedScheduledNotices = scheduledNoticesClient.getAll();

    assertThat(unprocessedScheduledNotices, hasSize(expectedNumberOfUnprocessedNotices));

    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  public void testNumberOfProcessedNotificationsWithIncorrectConfiguration() {
    generateLoanAndScheduledNotices(false, false, false);

    int numberOfNotices = 259;
    int expectedNumberOfUnprocessedNotices = numberOfNotices - SCHEDULED_NOTICES_PROCESSING_LIMIT;

    // create a incorrect configuration
    configClient.create(ConfigurationExample.schedulerNoticesLimitConfiguration("IncorrectVal"));

    createNotices(numberOfNotices);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing();
    List<JsonObject> unprocessedScheduledNotices = scheduledNoticesClient.getAll();

    assertThat(unprocessedScheduledNotices, hasSize(expectedNumberOfUnprocessedNotices));

    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  public void testDefaultNumberOfProcessedNotices() {
    generateLoanAndScheduledNotices(false, false, false);

    int expectedNumberOfUnprocessedNotices = 0;

    createNotices(SCHEDULED_NOTICES_PROCESSING_LIMIT);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing();
    List<JsonObject> unprocessedScheduledNotices = scheduledNoticesClient.getAll();

    assertThat(unprocessedScheduledNotices, hasSize(expectedNumberOfUnprocessedNotices));

    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  public void testNoticeIsDeletedIfItHasNoLoanId() {
    generateLoanAndScheduledNotices(false, false, false);

    int expectedNumberOfUnprocessedNotices = 0;

    JsonObject brokenNotice = createNoticesOverTime(dueDate.minusMinutes(1)::minusHours, 1).get(0);
    brokenNotice.remove("loanId");

    scheduledNoticesClient.create(brokenNotice);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(dueDate.minusSeconds(1));

    checkSentNotices();

    List<JsonObject> unprocessedScheduledNotices = scheduledNoticesClient.getAll();
    assertThat(unprocessedScheduledNotices, hasSize(expectedNumberOfUnprocessedNotices));

    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  public void testNoticeIsDeletedIfReferencedLoanDoesNotExist() {
    generateLoanAndScheduledNotices(false, false, false);

    int expectedNumberOfUnprocessedNotices = 0;

    JsonObject brokenNotice = createNoticesOverTime(dueDate.minusMinutes(1)::minusHours, 1).get(0);
    brokenNotice.put("loanId", UUID.randomUUID().toString());

    scheduledNoticesClient.create(brokenNotice);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(dueDate.minusSeconds(1));

    checkSentNotices();

    List<JsonObject> unprocessedScheduledNotices = scheduledNoticesClient.getAll();
    assertThat(unprocessedScheduledNotices, hasSize(expectedNumberOfUnprocessedNotices));

    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  public void testNoticeIsDeletedIfReferencedItemDoesNotExist() {
    generateLoanAndScheduledNotices(false, false, false);

    int expectedNumberOfUnprocessedNotices = 0;

    JsonObject brokenNotice = createNoticesOverTime(dueDate.minusMinutes(1)::minusHours, 1).get(0);

    itemsClient.delete(item);

    scheduledNoticesClient.create(brokenNotice);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(dueDate.minusSeconds(1));

    checkSentNotices();

    List<JsonObject> unprocessedScheduledNotices = scheduledNoticesClient.getAll();
    assertThat(unprocessedScheduledNotices, hasSize(expectedNumberOfUnprocessedNotices));

    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  public void testNoticeIsDeletedIfReferencedUserDoesNotExist() {
    generateLoanAndScheduledNotices(false, false, false);

    int expectedNumberOfUnprocessedNotices = 0;

    JsonObject brokenNotice = createNoticesOverTime(dueDate.minusMinutes(1)::minusHours, 1).get(0);

    usersFixture.remove(borrower);

    scheduledNoticesClient.create(brokenNotice);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(dueDate.minusSeconds(1));

    checkSentNotices();

    List<JsonObject> unprocessedScheduledNotices = scheduledNoticesClient.getAll();
    assertThat(unprocessedScheduledNotices, hasSize(expectedNumberOfUnprocessedNotices));

    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  public void testNoticeIsDeletedIfReferencedTemplateDoesNotExist() {
    generateLoanAndScheduledNotices(false, true, false);

    int expectedNumberOfUnprocessedNotices = 0;

    templateFixture.delete(uponAtTemplateId);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(dueDate.plusSeconds(1));

    checkSentNotices();

    List<JsonObject> unprocessedScheduledNotices = scheduledNoticesClient.getAll();
    assertThat(unprocessedScheduledNotices, hasSize(expectedNumberOfUnprocessedNotices));

    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  public void testNoticesForNonExistentLoansDoNotBlockTheQueue() {
    generateLoanAndScheduledNotices(false, false, false);

    int expectedNumberOfUnprocessedNotices = 0;

    List<JsonObject> notices = createNoticesOverTime(dueDate.minusMinutes(1)::minusHours, 4);

    notices.get(0).put("loanId", UUID.randomUUID().toString());
    notices.get(2).put("loanId", UUID.randomUUID().toString());

    for (JsonObject notice : notices) {
      scheduledNoticesClient.create(notice);
    }

    scheduledNoticeProcessingClient.runLoanNoticesProcessing(dueDate.minusSeconds(1));

    UUID expectedSentTemplateId1 = UUID.fromString(
        notices.get(1).getJsonObject("noticeConfig").getString("templateId"));

    UUID expectedSentTemplateId2 = UUID.fromString(
        notices.get(3).getJsonObject("noticeConfig").getString("templateId"));

    checkSentNotices(expectedSentTemplateId1, expectedSentTemplateId2);

    List<JsonObject> unprocessedScheduledNotices = scheduledNoticesClient.getAll();
    assertThat(unprocessedScheduledNotices, hasSize(expectedNumberOfUnprocessedNotices));

    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 2);
  }

  private void createNotices(int numberOfNotices) {

    DateTime systemTime = DateTime.now(DateTimeZone.UTC);
    List<JsonObject> notices = createNoticesOverTime(systemTime::minusHours, numberOfNotices);
    for (JsonObject notice : notices) {
      scheduledNoticesClient.create(notice);
    }
  }

  private void generateLoanAndScheduledNotices(boolean generateBeforeNotice,
    boolean generateUponAtNotice, boolean generateAfterNotice) {

    List<JsonObject> noticeConfigurations = new ArrayList<>();

    if (generateBeforeNotice) {
      noticeConfigurations.add(
        new NoticeConfigurationBuilder()
          .withTemplateId(beforeTemplateId)
          .withDueDateEvent()
          .withBeforeTiming(beforePeriod)
          .recurring(beforeRecurringPeriod)
          .sendInRealTime(true)
          .create()
      );
    }

    if (generateUponAtNotice) {
      noticeConfigurations.add(
        new NoticeConfigurationBuilder()
          .withTemplateId(uponAtTemplateId)
          .withDueDateEvent()
          .withUponAtTiming()
          .sendInRealTime(true)
          .create()
      );
    }

    if (generateAfterNotice) {
      noticeConfigurations.add(
        new NoticeConfigurationBuilder()
          .withTemplateId(afterTemplateId)
          .withDueDateEvent()
          .withAfterTiming(afterPeriod)
          .recurring(afterRecurringPeriod)
          .sendInRealTime(true)
          .create()
      );
    }

    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with due date notices")
      .withLoanNotices(noticeConfigurations);

    useFallbackPolicies(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    loan = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .on(loanDate)
        .at(UUID.randomUUID()));

    dueDate = new DateTime(loan.getJson().getString("dueDate"));

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(noticeConfigurations.size()));

    checkScheduledNotices(
      generateBeforeNotice ? dueDate.minus(beforePeriod.timePeriod()) : null,
      generateUponAtNotice ? dueDate : null,
      generateAfterNotice ? dueDate.plus(afterPeriod.timePeriod()) : null);
  }

  private void checkScheduledNotices(
    DateTime beforeNoticeNextRunTime,
    DateTime uponAtNoticeNextRunTime,
    DateTime afterNoticeNextRunTime) {

    int numberOfExpectedScheduledNotices = 0;
    numberOfExpectedScheduledNotices += beforeNoticeNextRunTime != null ? 1 : 0;
    numberOfExpectedScheduledNotices += uponAtNoticeNextRunTime != null ? 1 : 0;
    numberOfExpectedScheduledNotices += afterNoticeNextRunTime != null ? 1 : 0;

    List<JsonObject> scheduledNotices = scheduledNoticesClient.getAll();

    assertThat(scheduledNotices, hasSize(numberOfExpectedScheduledNotices));
    if (beforeNoticeNextRunTime != null) {
      assertThat(scheduledNotices, hasItems(
        hasScheduledLoanNotice(
          loan.getId(), beforeNoticeNextRunTime,
          BEFORE_TIMING, beforeTemplateId,
          beforeRecurringPeriod, true)));
    }
    if (uponAtNoticeNextRunTime != null) {
      assertThat(scheduledNotices, hasItems(
        hasScheduledLoanNotice(
          loan.getId(), uponAtNoticeNextRunTime,
          UPON_AT_TIMING, uponAtTemplateId,
          null, true)));
    }
    if (afterNoticeNextRunTime != null) {
      assertThat(scheduledNotices, hasItems(
        hasScheduledLoanNotice(
          loan.getId(), afterNoticeNextRunTime,
          AFTER_TIMING, afterTemplateId,
          afterRecurringPeriod, true)));
    }
  }

  @SuppressWarnings("unchecked")
  private void checkSentNotices(UUID... expectedTemplateIds) {
    Map<String, Matcher<String>> noticeContextMatchers = new HashMap<>();
    noticeContextMatchers.putAll(TemplateContextMatchers.getUserContextMatchers(borrower));
    noticeContextMatchers.putAll(TemplateContextMatchers.getItemContextMatchers(item, true));
    noticeContextMatchers.putAll(TemplateContextMatchers.getLoanContextMatchers(loan));
    noticeContextMatchers.putAll(TemplateContextMatchers.getLoanPolicyContextMatchersForUnlimitedRenewals());

    final var matchers = Stream.of(expectedTemplateIds)
      .map(templateId -> hasEmailNoticeProperties(borrower.getId(), templateId, noticeContextMatchers))
      .toArray(Matcher[]::new);

    List<JsonObject> sentNotices = patronNoticesClient.getAll();

    assertThat(sentNotices, hasSize(expectedTemplateIds.length));
    assertThat(sentNotices, hasItems(matchers));

    verifyNumberOfPublishedEvents(NOTICE, expectedTemplateIds.length);
  }

  private List<JsonObject> createNoticesOverTime(
    Function<Integer, DateTime> timeOffset, int numberOfNotices) {

    return IntStream.iterate(0, i -> i + 1)
      .boxed()
      .map(timeOffset)
      .map(this::createFakeScheduledNotice)
      .limit(numberOfNotices)
      .collect(Collectors.toList());
  }

  private JsonObject createFakeScheduledNotice(DateTime nextRunTime) {
    UUID templateId = UUID.randomUUID();
    templateFixture.createDummyNoticeTemplate(templateId);

    return new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("loanId", loan.getId().toString())
      .put("recipientUserId", borrower.getId().toString())
      .put(NEXT_RUN_TIME, nextRunTime.withZone(DateTimeZone.UTC).toString())
      .put("triggeringEvent", "Due date")
      .put("noticeConfig",
        new JsonObject()
          .put("timing", BEFORE_TIMING)
          .put("templateId",templateId.toString())
          .put("format", "Email")
          .put("sendInRealTime", true)
      );
  }
}
