package api.loans;

import static api.support.fixtures.ItemExamples.basedUponSmallAngryPlanet;
import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.matchers.ScheduledNoticeMatchers.hasScheduledLoanNotice;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfPublishedEvents;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfScheduledNotices;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfSentNotices;
import static java.util.Comparator.comparing;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE_ERROR;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.joda.time.DateTimeZone.UTC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.folio.circulation.domain.policy.Period;
import org.hamcrest.Matcher;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.HoldingBuilder;
import api.support.builders.ItemBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.fakes.FakeModNotify;
import api.support.fakes.FakePubSub;
import api.support.fixtures.ConfigurationExample;
import api.support.fixtures.TemplateContextMatchers;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.UserResource;
import io.vertx.core.json.JsonObject;

class DueDateScheduledNoticesProcessingTests extends APITests {
  private static final String BEFORE_TIMING = "Before";
  private static final String UPON_AT_TIMING = "Upon At";
  private static final String AFTER_TIMING = "After";

  private static final int SCHEDULED_NOTICES_PROCESSING_LIMIT = 100;
  private static final String NEXT_RUN_TIME = "nextRunTime";

  private static final Period BEFORE_PERIOD = Period.days(2);
  private static final Period BEFORE_RECURRING_PERIOD = Period.hours(6);
  private static final Period AFTER_PERIOD = Period.days(3);
  private static final Period AFTER_RECURRING_PERIOD = Period.hours(4);

  private static final UUID BEFORE_TEMPLATE_ID = UUID.randomUUID();
  private static final UUID BEFORE_RECURRING_TEMPLATE_ID = UUID.randomUUID();
  private static final UUID UPON_AT_TEMPLATE_ID = UUID.randomUUID();
  private static final UUID AFTER_TEMPLATE_ID = UUID.randomUUID();
  private static final UUID AFTER_RECURRING_TEMPLATE_ID = UUID.randomUUID();

  private static final DateTime LOAN_DATE = new DateTime(2018, 3, 18, 11, 43, 54, UTC);

  private ItemResource item;
  private UserResource borrower;
  private IndividualResource loan;
  private UUID loanId;
  private DateTime dueDate;

  @BeforeEach
  public void beforeEach() {
    ItemBuilder itemBuilder = basedUponSmallAngryPlanet(
      materialTypesFixture.book().getId(), loanTypesFixture.canCirculate().getId());

    HoldingBuilder holdingBuilder = itemsFixture.applyCallNumberHoldings(
      "CN",
      "Prefix",
      "Suffix",
      Collections.singletonList("CopyNumbers"));

    item = itemsFixture.basedUponSmallAngryPlanet(itemBuilder, holdingBuilder);
    borrower = usersFixture.steve();

    templateFixture.createDummyNoticeTemplate(BEFORE_TEMPLATE_ID);
    templateFixture.createDummyNoticeTemplate(BEFORE_RECURRING_TEMPLATE_ID);
    templateFixture.createDummyNoticeTemplate(UPON_AT_TEMPLATE_ID);
    templateFixture.createDummyNoticeTemplate(AFTER_TEMPLATE_ID);
    templateFixture.createDummyNoticeTemplate(AFTER_RECURRING_TEMPLATE_ID);
  }

  @Test
  void recurringBeforeNoticeShouldBeSentAndRescheduled() {
    generateLoanAndScheduledNotices(beforeNotice(true));

    DateTime beforeDueDateTime = dueDate.minus(BEFORE_PERIOD.timePeriod()).plusSeconds(1);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(beforeDueDateTime);
    checkSentNotices(BEFORE_RECURRING_TEMPLATE_ID);

    DateTime expectedNextRunTime = dueDate
      .minus(BEFORE_PERIOD.timePeriod())
      .plus(BEFORE_RECURRING_PERIOD.timePeriod());

    verifyScheduledNotices(
      scheduledNoticeMatcher(loanId, BEFORE_RECURRING_TEMPLATE_ID, BEFORE_TIMING,
        BEFORE_RECURRING_PERIOD, expectedNextRunTime)
    );

    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void beforeNoticeShouldBeSendAndDeletedWhenItsNextRunTimeIsAfterDueDate() {
    generateLoanAndScheduledNotices(beforeNotice(true));

    DateTime justBeforeDueDateTime = dueDate.minusSeconds(1);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(justBeforeDueDateTime);

    checkSentNotices(BEFORE_RECURRING_TEMPLATE_ID);

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void uponAtNoticeShouldBeSentWhenProcessingJustAfterDueDate() {
    generateLoanAndScheduledNotices(uponAtNotice());

    DateTime justAfterDueDateTime = dueDate.plusSeconds(1);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(justAfterDueDateTime);

    checkSentNotices(UPON_AT_TEMPLATE_ID);

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void afterRecurringNoticeShouldBeSentSeveralTimesBeforeLoanIsClosed() {
    generateLoanAndScheduledNotices(afterNotice(true));

    DateTime justAfterDueDateTime = dueDate.plusSeconds(1);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(justAfterDueDateTime);
    //Clear all sent notices before actual test
    FakeModNotify.clearSentPatronNotices();

    DateTime afterNoticeRunTime = dueDate.plus(AFTER_PERIOD.timePeriod()).plusSeconds(1);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(afterNoticeRunTime);

    DateTime expectedNextRunTime = dueDate
      .plus(AFTER_PERIOD.timePeriod())
      .plus(AFTER_RECURRING_PERIOD.timePeriod());

    verifyScheduledNotices(
      scheduledNoticeMatcher(loanId, AFTER_RECURRING_TEMPLATE_ID, AFTER_TIMING,
        AFTER_RECURRING_PERIOD, expectedNextRunTime)
    );

    //Run again to send recurring notice
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(
      expectedNextRunTime.plusSeconds(1));

    checkSentNotices(AFTER_RECURRING_TEMPLATE_ID, AFTER_RECURRING_TEMPLATE_ID);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    DateTime secondRecurringRunTime =
      expectedNextRunTime.plus(AFTER_RECURRING_PERIOD.timePeriod());

    verifyScheduledNotices(
      scheduledNoticeMatcher(loanId, AFTER_RECURRING_TEMPLATE_ID, AFTER_TIMING,
        AFTER_RECURRING_PERIOD, secondRecurringRunTime)
    );

    checkInFixture.checkInByBarcode(item);
    //Clear sent notices again
    FakeModNotify.clearSentPatronNotices();
    FakePubSub.clearPublishedEvents();

    //Run after loan is closed
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(
      secondRecurringRunTime.plusSeconds(1));

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void processingTakesNoticesInThePastLimitedAndOrdered() {
    generateLoanAndScheduledNotices();

    DateTime systemTime = DateTime.now(UTC);
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

    Comparator<JsonObject> nextRunTimeComparator =
      comparing(json -> getDateTimeProperty(json, NEXT_RUN_TIME));
    JsonObject[] expectedUnprocessedNoticesInThePast = noticesInThePast.stream()
      .sorted(nextRunTimeComparator.reversed())
      .limit(expectedNumberOfUnprocessedNoticesInThePast)
      .toArray(JsonObject[]::new);

    List<JsonObject> unprocessedScheduledNotices = scheduledNoticesClient.getAll();
    assertThat(unprocessedScheduledNotices, hasItems(expectedUnprocessedNoticesInThePast));
    assertThat(unprocessedScheduledNotices, hasItems(noticesInTheFuture.toArray(new JsonObject[0])));

    verifyNumberOfScheduledNotices(expectedNumberOfUnprocessedNoticesInThePast + numberOfNoticesInTheFuture);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void testNumberOfProcessedNoticesWithSchedulerNoticesLimitConfiguration() {
    generateLoanAndScheduledNotices();

    int noticesLimitConfig = 200;
    int numberOfNotices = 300;

    // create a new configuration
    configClient.create(ConfigurationExample.schedulerNoticesLimitConfiguration(Integer.toString(noticesLimitConfig)));

    createNotices(numberOfNotices);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing();

    verifyNumberOfScheduledNotices(numberOfNotices - noticesLimitConfig);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void testNumberOfProcessedNotificationsWithIncorrectConfiguration() {
    generateLoanAndScheduledNotices();

    int numberOfNotices = 259;

    // create a incorrect configuration
    configClient.create(ConfigurationExample.schedulerNoticesLimitConfiguration("IncorrectVal"));

    createNotices(numberOfNotices);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing();

    verifyNumberOfScheduledNotices(numberOfNotices - SCHEDULED_NOTICES_PROCESSING_LIMIT);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void testDefaultNumberOfProcessedNotices() {
    generateLoanAndScheduledNotices();

    createNotices(SCHEDULED_NOTICES_PROCESSING_LIMIT);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing();

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void testNoticeIsDeletedIfItHasNoLoanId() {
    generateLoanAndScheduledNotices();

    JsonObject brokenNotice = createNoticesOverTime(dueDate.minusMinutes(1)::minusHours, 1).get(0);
    brokenNotice.remove("loanId");

    scheduledNoticesClient.create(brokenNotice);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(dueDate.minusSeconds(1));

    checkSentNotices();

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  void testNoticeIsDeletedIfReferencedLoanDoesNotExist() {
    generateLoanAndScheduledNotices();

    JsonObject brokenNotice = createNoticesOverTime(dueDate.minusMinutes(1)::minusHours, 1).get(0);
    brokenNotice.put("loanId", UUID.randomUUID().toString());

    scheduledNoticesClient.create(brokenNotice);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(dueDate.minusSeconds(1));

    checkSentNotices();

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  void testNoticeIsDeletedIfReferencedItemDoesNotExist() {
    generateLoanAndScheduledNotices();

    JsonObject brokenNotice = createNoticesOverTime(dueDate.minusMinutes(1)::minusHours, 1).get(0);

    itemsClient.delete(item);

    scheduledNoticesClient.create(brokenNotice);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(dueDate.minusSeconds(1));

    checkSentNotices();


    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  void testNoticeIsDeletedIfReferencedUserDoesNotExist() {
    generateLoanAndScheduledNotices();

    JsonObject brokenNotice = createNoticesOverTime(dueDate.minusMinutes(1)::minusHours, 1).get(0);

    usersFixture.remove(borrower);

    scheduledNoticesClient.create(brokenNotice);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(dueDate.minusSeconds(1));

    checkSentNotices();

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  void testNoticeIsDeletedIfReferencedTemplateDoesNotExist() {
    generateLoanAndScheduledNotices(uponAtNotice());

    templateFixture.delete(UPON_AT_TEMPLATE_ID);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(dueDate.plusSeconds(1));

    checkSentNotices();

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  void testNoticeIsNotSentOrDeletedWhenPatronNoticeRequestFails() {
    generateLoanAndScheduledNotices(uponAtNotice());

    FakeModNotify.setFailPatronNoticesWithBadRequest(true);

    scheduledNoticeProcessingClient.runLoanNoticesProcessing(dueDate.plusSeconds(1));

    checkSentNotices();

    verifyNumberOfScheduledNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  void testNoticesForNonExistentLoansDoNotBlockTheQueue() {
    generateLoanAndScheduledNotices();

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

    verifyNumberOfScheduledNotices(expectedNumberOfUnprocessedNotices);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 2);
  }

  @Test
  void scheduledOverdueNoticesShouldBeDeletedAfterOverdueFineIsCharged() {
    UUID uponAtTemplateId = UUID.randomUUID();

    UUID afterTemplateId = UUID.randomUUID();
    Period afterPeriod = Period.days(3);
    Period afterRecurringPeriod = Period.hours(4);

    JsonObject uponAtDueDateNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(uponAtTemplateId)
      .withDueDateEvent()
      .withUponAtTiming()
      .sendInRealTime(false)
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
        uponAtDueDateNoticeConfiguration,
        afterDueDateNoticeConfiguration));

    useFallbackPolicies(loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId(),
      overdueFinePoliciesFixture.facultyStandardDoNotCountClosed().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    final IndividualResource james = usersFixture.james();
    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      item -> item.withPrimaryServicePoint(checkInServicePointId));
    final IndividualResource nod = itemsFixture.basedUponNod(item ->
      item.withPermanentLocation(homeLocation.getId()));
    DateTime loanDate = new DateTime(2020, 1, 1, 12, 0, 0, UTC);

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(nod)
        .to(james)
        .on(loanDate)
        .at(UUID.randomUUID()));

    DateTime dueDate = getDateTimeProperty(loan.getJson(), "dueDate");

    verifyNumberOfScheduledNotices(2);

    List<JsonObject> scheduledNotices = scheduledNoticesClient.getAll();
    assertThat(scheduledNotices,
      hasItems(
        hasScheduledLoanNotice(
          loan.getId(), dueDate,
          UPON_AT_TIMING, uponAtTemplateId,
          null, false),
        hasScheduledLoanNotice(
          loan.getId(), dueDate.plus(afterPeriod.timePeriod()),
          AFTER_TIMING, afterTemplateId,
          afterRecurringPeriod, true)));

    UUID ownerId = feeFineOwnerFixture.ownerForServicePoint(
      UUID.fromString(homeLocation.getJson().getString("primaryServicePoint"))).getId();
    feeFineTypeFixture.overdueFine(ownerId);

    DateTime checkInDate = new DateTime(2020, 1, 25, 12, 0, 0, UTC);

    checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .on(checkInDate)
        .at(checkInServicePointId));

    verifyNumberOfScheduledNotices(0);
  }

  @Test
  void allDueDateNoticesAreDiscardedWhenLoanIsClosed() {
    generateLoanAndScheduledNotices(
      beforeNotice(false),
      beforeNotice(true),
      uponAtNotice(),
      afterNotice(false),
      afterNotice(true)
    );

    verifyNumberOfScheduledNotices(5);

    checkInFixture.checkInByBarcode(item);

    verifyNumberOfScheduledNotices(5);

    var processingTime = dueDate
      .plus(AFTER_PERIOD.timePeriod())
      .plus(AFTER_RECURRING_PERIOD.timePeriod())
      .plusSeconds(1);

    scheduledNoticeProcessingClient.runLoanNoticesProcessing(processingTime);

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void beforeNoticesAreDiscardedWhenDueDateHasAlreadyPassed() {
    generateLoanAndScheduledNotices(
      beforeNotice(false),
      beforeNotice(true)
    );

    verifyNumberOfScheduledNotices(2);

    scheduledNoticeProcessingClient.runLoanNoticesProcessing(dueDate.plusSeconds(1));

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void recurringAfterNoticeIsDiscardedWhenItemIsDeclaredLost() {
    recurringAfterNoticeIsDiscardedWhenItemStatusIsWrong(
      () -> declareLostFixtures.declareItemLost(loanId));
  }

  @Test
  void recurringAfterNoticeIsDiscardedWhenItemIsClaimedReturned() {
    recurringAfterNoticeIsDiscardedWhenItemStatusIsWrong(
      () -> claimItemReturnedFixture.claimItemReturned(loanId));
  }

  @Test
  void recurringAfterNoticeIsDiscardedWhenLoanIsAgedToLost() {
    recurringAfterNoticeIsDiscardedWhenItemStatusIsWrong(ageToLostFixture::ageToLost);
  }

  private void recurringAfterNoticeIsDiscardedWhenItemStatusIsWrong(
    Runnable itemStatusChangingAction) {

    generateLoanAndScheduledNotices(
      afterNotice(true),
      afterNotice(false)
    );

    verifyNumberOfScheduledNotices(2);

    itemStatusChangingAction.run();

    verifyNumberOfScheduledNotices(2);

    var processingTime = dueDate
      .plus(AFTER_PERIOD.timePeriod())
      .plus(AFTER_RECURRING_PERIOD.timePeriod())
      .plusSeconds(1);

    scheduledNoticeProcessingClient.runLoanNoticesProcessing(processingTime);

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    checkSentNotices(AFTER_TEMPLATE_ID);
  }

  private void createNotices(int numberOfNotices) {
    DateTime systemTime = DateTime.now(UTC);
    List<JsonObject> notices = createNoticesOverTime(systemTime::minusHours, numberOfNotices);
    for (JsonObject notice : notices) {
      scheduledNoticesClient.create(notice);
    }
  }

  private void generateLoanAndScheduledNotices(JsonObject... patronNoticeConfigurations) {
    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with due date notices")
      .withLoanNotices(List.of(patronNoticeConfigurations));

    useFallbackPolicies(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId(),
      overdueFinePoliciesFixture.noOverdueFine().getId(),
      lostItemFeePoliciesFixture.chargeFee().getId());

    loan = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .on(LOAN_DATE)
        .at(UUID.randomUUID()));

    loanId = loan.getId();

    dueDate = new DateTime(loan.getJson().getString("dueDate"));

    verifyNumberOfScheduledNotices(patronNoticeConfigurations.length);
  }

  private static JsonObject beforeNotice(boolean recurring) {
    return new NoticeConfigurationBuilder()
      .withTemplateId(recurring ? BEFORE_RECURRING_TEMPLATE_ID : BEFORE_TEMPLATE_ID)
      .withDueDateEvent()
      .withBeforeTiming(BEFORE_PERIOD)
      .recurring(recurring ? BEFORE_RECURRING_PERIOD : null)
      .sendInRealTime(true)
      .create();
  }

  private static JsonObject uponAtNotice() {
    return new NoticeConfigurationBuilder()
      .withTemplateId(UPON_AT_TEMPLATE_ID)
      .withDueDateEvent()
      .withUponAtTiming()
      .sendInRealTime(true)
      .create();
  }

  private static JsonObject afterNotice(boolean recurring) {
    return new NoticeConfigurationBuilder()
      .withTemplateId(recurring ? AFTER_RECURRING_TEMPLATE_ID : AFTER_TEMPLATE_ID)
      .withDueDateEvent()
      .withAfterTiming(AFTER_PERIOD)
      .recurring(recurring ? AFTER_RECURRING_PERIOD : null)
      .sendInRealTime(true)
      .create();
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

    List<JsonObject> sentNotices = FakeModNotify.getSentPatronNotices();

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
      .put(NEXT_RUN_TIME, nextRunTime.withZone(UTC).toString())
      .put("triggeringEvent", "Due date")
      .put("noticeConfig",
        new JsonObject()
          .put("timing", BEFORE_TIMING)
          .put("templateId",templateId.toString())
          .put("format", "Email")
          .put("sendInRealTime", true)
      );
  }

  private static Matcher<JsonObject> scheduledNoticeMatcher(UUID loanId, UUID templateId,
    String timing, Period recurringPeriod, DateTime nextRunTime) {

    return hasScheduledLoanNotice(loanId, nextRunTime, timing, templateId, recurringPeriod, true);
  }

  @SafeVarargs
  private void verifyScheduledNotices(Matcher<JsonObject>... noticeMatchers) {
    List<JsonObject> scheduledNotices = scheduledNoticesClient.getAll();

    assertThat(scheduledNotices, hasSize(noticeMatchers.length));
    assertThat(scheduledNotices, hasItems(noticeMatchers));
  }
}
