package api.loans;

import static api.support.fixtures.ItemExamples.basedUponSmallAngryPlanet;
import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.matchers.ScheduledNoticeMatchers.hasScheduledLoanNotice;
import static java.util.Comparator.comparing;
import static org.folio.circulation.support.JsonPropertyFetcher.getDateTimeProperty;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
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
import org.folio.circulation.support.http.client.IndividualResource;
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
import api.support.fixtures.ConfigurationExample;
import api.support.fixtures.TemplateContextMatchers;
import api.support.http.InventoryItemResource;
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

  private InventoryItemResource item;
  private IndividualResource borrower;
  private IndividualResource loan;
  private DateTime dueDate;

  @Before
  public void beforeEach() {
    setUpNoticePolicy();

    ItemBuilder itemBuilder = basedUponSmallAngryPlanet(
      materialTypesFixture.book().getId(), loanTypesFixture.canCirculate().getId());

    HoldingBuilder holdingBuilder = itemsFixture.applyCallNumberHoldings(
      "CN",
      "Prefix",
      "Suffix",
      Collections.singletonList("CopyNumbers"));

    item = itemsFixture.basedUponSmallAngryPlanet(itemBuilder, holdingBuilder);
    borrower = usersFixture.steve();

    loan = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .on(loanDate)
        .at(UUID.randomUUID()));

    dueDate = new DateTime(loan.getJson().getString("dueDate"));

    assertSetUpIsCorrect();
  }

  @Test
  public void beforeNoticeShouldBeSentAndItsNextRunTimeShouldBeUpdated() {

    DateTime beforeDueDateTime = dueDate.minus(beforePeriod.timePeriod()).plusSeconds(1);
    templateClient.create(new JsonObject().put("id", beforeTemplateId.toString()));
    scheduledNoticeProcessingClient.runDueDateNoticesProcessing(beforeDueDateTime);
    checkSentNotices(beforeTemplateId);

    DateTime expectedNewRunTimeForBeforeNotice = dueDate
      .minus(beforePeriod.timePeriod())
      .plus(beforeRecurringPeriod.timePeriod());

    checkScheduledNotices(
      expectedNewRunTimeForBeforeNotice,
      dueDate,
      dueDate.plus(afterPeriod.timePeriod()));
  }

  @Test
  public void beforeNoticeShouldBeSendAndDeletedWhenItsNextRunTimeIsAfterDueDate() {

    DateTime justBeforeDueDateTime = dueDate.minusSeconds(1);
    templateClient.create(new JsonObject().put("id", beforeTemplateId.toString()));
    scheduledNoticeProcessingClient.runDueDateNoticesProcessing(justBeforeDueDateTime);

    checkSentNotices(beforeTemplateId);

    checkScheduledNotices(
      null,
      dueDate,
      dueDate.plus(afterPeriod.timePeriod()));
  }

  @Test
  public void uponAtNoticeShouldBeSentWhenProcessingJustAfterDueDate() {

    DateTime justAfterDueDateTime = dueDate.plusSeconds(1);
    templateClient.create(new JsonObject().put("id", uponAtTemplateId.toString()));
    scheduledNoticeProcessingClient.runDueDateNoticesProcessing(justAfterDueDateTime);

    checkSentNotices(uponAtTemplateId);

    checkScheduledNotices(
      null,
      null,
      dueDate.plus(afterPeriod.timePeriod()));
  }

  @Test
  public void afterRecurringNoticeShouldBeSentSeveralTimesBeforeLoanIsClosed() {

    DateTime justAfterDueDateTime = dueDate.plusSeconds(1);
    templateClient.create(new JsonObject().put("id", afterTemplateId.toString()));
    scheduledNoticeProcessingClient.runDueDateNoticesProcessing(justAfterDueDateTime);
    //Clear all sent notices before actual test
    patronNoticesClient.deleteAll();

    DateTime afterNoticeRunTime = dueDate.plus(afterPeriod.timePeriod()).plusSeconds(1);
    scheduledNoticeProcessingClient.runDueDateNoticesProcessing(afterNoticeRunTime);

    DateTime afterNoticeExpectedRunTime = dueDate
      .plus(afterPeriod.timePeriod())
      .plus(afterRecurringPeriod.timePeriod());

    checkScheduledNotices(
      null,
      null,
      afterNoticeExpectedRunTime);

    //Run again to send recurring notice
    scheduledNoticeProcessingClient.runDueDateNoticesProcessing(
      afterNoticeExpectedRunTime.plusSeconds(1));

    checkSentNotices(afterTemplateId, afterTemplateId);

    DateTime secondRecurringRunTime =
      afterNoticeExpectedRunTime.plus(afterRecurringPeriod.timePeriod());

    checkScheduledNotices(
      null,
      null,
      secondRecurringRunTime);

    loansFixture.checkInByBarcode(item);
    //Clear sent notices again
    patronNoticesClient.deleteAll();

    //Run after loan is closed
    scheduledNoticeProcessingClient.runDueDateNoticesProcessing(
      secondRecurringRunTime.plusSeconds(1));

    checkSentNotices();
    checkScheduledNotices(null, null, null);
  }

  @Test
  public void processingTakesNoticesInThePastLimitedAndOrdered() {
    //Clean scheduled notices before this test
    scheduledNoticesClient.deleteAll();

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

    scheduledNoticeProcessingClient.runDueDateNoticesProcessing();
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
  }

  @Test
  public void testNumberOfProcessedNoticesWithSchedulerNoticesLimitConfiguration() {

    scheduledNoticesClient.deleteAll();

    int noticesLimitConfig = 200;
    int numberOfNotices = 300;
    int expectedNumberOfUnprocessedNotices = numberOfNotices - noticesLimitConfig;

    // create a new configuration
    configClient.create(ConfigurationExample.schedulerNoticesLimitConfiguration(Integer.toString(noticesLimitConfig)));

    createNotices(numberOfNotices);
    scheduledNoticeProcessingClient.runDueDateNoticesProcessing();
    List<JsonObject> unprocessedScheduledNotices = scheduledNoticesClient.getAll();

    assertThat(unprocessedScheduledNotices, hasSize(expectedNumberOfUnprocessedNotices));
  }

  @Test
  public void testNumberOfProcessedNotificationsWithIncorrectConfiguration() {

    scheduledNoticesClient.deleteAll();

    int numberOfNotices = 259;
    int expectedNumberOfUnprocessedNotices = numberOfNotices - SCHEDULED_NOTICES_PROCESSING_LIMIT;

    // create a incorrect configuration
    configClient.create(ConfigurationExample.schedulerNoticesLimitConfiguration("IncorrectVal"));

    createNotices(numberOfNotices);
    scheduledNoticeProcessingClient.runDueDateNoticesProcessing();
    List<JsonObject> unprocessedScheduledNotices = scheduledNoticesClient.getAll();

    assertThat(unprocessedScheduledNotices, hasSize(expectedNumberOfUnprocessedNotices));
  }

  @Test
  public void testDefaultNumberOfProcessedNotices() {

    scheduledNoticesClient.deleteAll();

    int expectedNumberOfUnprocessedNotices = 0;

    createNotices(SCHEDULED_NOTICES_PROCESSING_LIMIT);
    scheduledNoticeProcessingClient.runDueDateNoticesProcessing();
    List<JsonObject> unprocessedScheduledNotices = scheduledNoticesClient.getAll();

    assertThat(unprocessedScheduledNotices, hasSize(expectedNumberOfUnprocessedNotices));
  }

  @Test
  public void testNoticeIsDeletedIfReferencedLoanDoesNotExist() {

    scheduledNoticesClient.deleteAll();
    int expectedNumberOfUnprocessedNotices = 0;

    JsonObject brokenNotice = createNoticesOverTime(dueDate.minusMinutes(1)::minusHours, 1).get(0);
    brokenNotice.put("loanId", UUID.randomUUID().toString());

    scheduledNoticesClient.create(brokenNotice);
    scheduledNoticeProcessingClient.runDueDateNoticesProcessing(dueDate.minusSeconds(1));

    checkSentNotices();

    List<JsonObject> unprocessedScheduledNotices = scheduledNoticesClient.getAll();
    assertThat(unprocessedScheduledNotices, hasSize(expectedNumberOfUnprocessedNotices));
  }

  @Test
  public void testNoticeIsDeletedIfReferencedItemDoesNotExist() {

    scheduledNoticesClient.deleteAll();
    int expectedNumberOfUnprocessedNotices = 0;

    JsonObject brokenNotice = createNoticesOverTime(dueDate.minusMinutes(1)::minusHours, 1).get(0);

    itemsClient.delete(item);

    scheduledNoticesClient.create(brokenNotice);
    scheduledNoticeProcessingClient.runDueDateNoticesProcessing(dueDate.minusSeconds(1));

    checkSentNotices();

    List<JsonObject> unprocessedScheduledNotices = scheduledNoticesClient.getAll();
    assertThat(unprocessedScheduledNotices, hasSize(expectedNumberOfUnprocessedNotices));
  }

  @Test
  public void testNoticeIsDeletedIfReferencedUserDoesNotExist() {

    scheduledNoticesClient.deleteAll();
    int expectedNumberOfUnprocessedNotices = 0;

    JsonObject brokenNotice = createNoticesOverTime(dueDate.minusMinutes(1)::minusHours, 1).get(0);

    usersFixture.remove(borrower);

    scheduledNoticesClient.create(brokenNotice);
    scheduledNoticeProcessingClient.runDueDateNoticesProcessing(dueDate.minusSeconds(1));

    checkSentNotices();

    List<JsonObject> unprocessedScheduledNotices = scheduledNoticesClient.getAll();
    assertThat(unprocessedScheduledNotices, hasSize(expectedNumberOfUnprocessedNotices));
  }

  @Test
  public void testNoticesForNonExistentLoansDoNotBlockTheQueue() {

    scheduledNoticesClient.deleteAll();
    int expectedNumberOfUnprocessedNotices = 0;

    List<JsonObject> notices = createNoticesOverTime(dueDate.minusMinutes(1)::minusHours, 4);

    notices.get(0).put("loanId", UUID.randomUUID().toString());
    notices.get(2).put("loanId", UUID.randomUUID().toString());

    for (JsonObject notice : notices) {
      scheduledNoticesClient.create(notice);
    }

    scheduledNoticeProcessingClient.runDueDateNoticesProcessing(dueDate.minusSeconds(1));

    UUID expectedSentTemplateId1 = UUID.fromString(
        notices.get(1).getJsonObject("noticeConfig").getString("templateId"));

    UUID expectedSentTemplateId2 = UUID.fromString(
        notices.get(3).getJsonObject("noticeConfig").getString("templateId"));

    checkSentNotices(expectedSentTemplateId1, expectedSentTemplateId2);

    List<JsonObject> unprocessedScheduledNotices = scheduledNoticesClient.getAll();
    assertThat(unprocessedScheduledNotices, hasSize(expectedNumberOfUnprocessedNotices));
  }

  @Test
  public void noticeIsDeletedIfReferencedTemplateDoesNotExist() {
    DateTime beforeDueDateTime = dueDate.minus(beforePeriod.timePeriod()).plusSeconds(1);

    templateClient.deleteAll();
    assertThat(scheduledNoticesClient.getAll(), hasSize(3));

    scheduledNoticeProcessingClient.runDueDateNoticesProcessing(beforeDueDateTime);

    assertThat(scheduledNoticesClient.getAll(), hasSize(2));
  }

  private void createNotices(int numberOfNotices) {

    DateTime systemTime = DateTime.now(DateTimeZone.UTC);
    List<JsonObject> notices = createNoticesOverTime(systemTime::minusHours, numberOfNotices);
    for (JsonObject notice : notices) {
      scheduledNoticesClient.create(notice);
    }
  }

  private void setUpNoticePolicy() {
    JsonObject beforeDueDateNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(beforeTemplateId)
      .withDueDateEvent()
      .withBeforeTiming(beforePeriod)
      .recurring(beforeRecurringPeriod)
      .sendInRealTime(true)
      .create();

    JsonObject uponAtDueDateNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(uponAtTemplateId)
      .withDueDateEvent()
      .withUponAtTiming()
      .sendInRealTime(true)
      .create();

    JsonObject afterDueDateNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(afterTemplateId)
      .withDueDateEvent()
      .withAfterTiming(afterPeriod)
      .recurring(afterRecurringPeriod)
      .sendInRealTime(true)
      .create();

    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with due date notices")
      .withLoanNotices(Arrays.asList(
        beforeDueDateNoticeConfiguration,
        uponAtDueDateNoticeConfiguration,
        afterDueDateNoticeConfiguration));

    useFallbackPolicies(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());
  }

  private void assertSetUpIsCorrect() {
    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(3));

    checkScheduledNotices(
      dueDate.minus(beforePeriod.timePeriod()),
      dueDate,
      dueDate.plus(afterPeriod.timePeriod()));
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

    Matcher[] matchers = Stream.of(expectedTemplateIds)
      .map(templateId -> hasEmailNoticeProperties(borrower.getId(), templateId, noticeContextMatchers))
      .toArray(Matcher[]::new);

    List<JsonObject> sentNotices = patronNoticesClient.getAll();
    assertThat(sentNotices, hasSize(expectedTemplateIds.length));
    assertThat(sentNotices, hasItems(matchers));
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
    String templateId = UUID.randomUUID().toString();
    templateClient.create(new JsonObject().put("id", templateId));

    return new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("loanId", loan.getId().toString())
      .put(NEXT_RUN_TIME, nextRunTime.withZone(DateTimeZone.UTC).toString())
      .put("triggeringEvent", "Due date")
      .put("noticeConfig",
        new JsonObject()
          .put("timing", BEFORE_TIMING)
          .put("templateId",templateId)
          .put("format", "Email")
          .put("sendInRealTime", true)
      );
  }
}
