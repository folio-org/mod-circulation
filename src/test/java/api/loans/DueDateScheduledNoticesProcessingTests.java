package api.loans;

import static api.support.fixtures.ItemExamples.basedUponSmallAngryPlanet;
import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.matchers.ScheduledNoticeMatchers.hasScheduledLoanNotice;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfPublishedEvents;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfScheduledNotices;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfSentNotices;
import static java.time.ZoneOffset.UTC;
import static java.util.Comparator.comparing;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE_ERROR;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTime;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.time.ZonedDateTime;
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

import api.support.builders.AddInfoRequestBuilder;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.utils.ClockUtil;
import org.folio.circulation.support.utils.DateFormatUtil;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.HoldingBuilder;
import api.support.builders.ItemBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.builders.UserBuilder;
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
  private static final String LOAN_INFO_ADDED = "testing patron info";

  private static final ZonedDateTime LOAN_DATE = ZonedDateTime.of(2018, 3, 18, 11, 43, 54, 0, UTC);

  private ItemResource item;
  private UserResource borrower;
  private IndividualResource loan;
  private UUID loanId;
  private ZonedDateTime dueDate;

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

    ZonedDateTime beforeDueDateTime = BEFORE_PERIOD.minusDate(dueDate).plusSeconds(1);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(beforeDueDateTime);
    checkSentNotices(BEFORE_RECURRING_TEMPLATE_ID);

    ZonedDateTime expectedNextRunTime = BEFORE_RECURRING_PERIOD
      .plusDate(BEFORE_PERIOD.minusDate(dueDate));

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

    ZonedDateTime justBeforeDueDateTime = dueDate.minusSeconds(1);
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

    ZonedDateTime justAfterDueDateTime = dueDate.plusSeconds(1);
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

    ZonedDateTime justAfterDueDateTime = dueDate.plusSeconds(1);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(justAfterDueDateTime);
    //Clear all sent notices before actual test
    FakeModNotify.clearSentPatronNotices();

    ZonedDateTime afterNoticeRunTime = AFTER_PERIOD.plusDate(dueDate).plusSeconds(1);

    scheduledNoticeProcessingClient.runLoanNoticesProcessing(afterNoticeRunTime);

    ZonedDateTime expectedNextRunTime = AFTER_RECURRING_PERIOD
      .plusDate(AFTER_PERIOD.plusDate(dueDate));

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

    ZonedDateTime secondRecurringRunTime = AFTER_RECURRING_PERIOD.plusDate(expectedNextRunTime);

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

    ZonedDateTime systemTime = ClockUtil.getZonedDateTime();
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
  void testNoticeIsDeletedIfPatronGroupIsNull() {
    generateLoanAndScheduledNotices();

    JsonObject brokenNotice = createNoticesOverTime(dueDate.minusMinutes(1)::minusHours,1).get(0);

    usersClient.replace(borrower.getId(), new UserBuilder().withPatronGroupId(null));
    scheduledNoticesClient.create(brokenNotice);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(dueDate.minusSeconds(1));

    verifyNumberOfSentNotices(0);
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
  void testNoticeIsDeletedWhenPatronNoticeRequestFails() {
    generateLoanAndScheduledNotices(uponAtNotice());

    FakeModNotify.setFailPatronNoticesWithBadRequest(true);

    scheduledNoticeProcessingClient.runLoanNoticesProcessing(dueDate.plusSeconds(1));

    checkSentNotices();

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  void testNoticeIsDeletedWhenItemIsClaimedReturned() {
    generateLoanAndScheduledNotices(beforeNotice(false));

    claimItemReturnedFixture.claimItemReturned(loanId);
    ZonedDateTime beforeDueDateTime = BEFORE_PERIOD.minusDate(dueDate).plusSeconds(1);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(beforeDueDateTime);

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
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
  void testNoticesForNullPatronGroupsDoNotBlockTheQueue() {
    generateLoanAndScheduledNotices();

    int expectedNumberOfUnprocessedNotices = 0;

    List<JsonObject> notices = createNoticesOverTime(dueDate.minusMinutes(1)::minusHours, 4);

    var basedUponNod = itemsFixture.basedUponNod();
    var jessica = usersFixture.jessica();

    var jessicaNodLoan = checkOutFixture.checkOutByBarcode(basedUponNod, jessica);
    addPatronInfoToLoan(jessicaNodLoan.getId().toString());

    usersClient.replace(borrower.getId(), new UserBuilder().withPatronGroupId(null));
    notices.get(1).put("loanId", jessicaNodLoan.getId());
    notices.get(3).put("loanId", jessicaNodLoan.getId());
    notices.get(3).put("recipientUserId", jessica.getId().toString());
    notices.get(1).put("recipientUserId", jessica.getId().toString());

    notices.forEach(scheduledNoticesClient::create);

    scheduledNoticeProcessingClient.runLoanNoticesProcessing(dueDate.minusSeconds(1));

    UUID expectedSentTemplateId1 = UUID.fromString(
      notices.get(1).getJsonObject("noticeConfig").getString("templateId"));

    UUID expectedSentTemplateId2 = UUID.fromString(
      notices.get(3).getJsonObject("noticeConfig").getString("templateId"));

    checkSentNotices(basedUponNod, jessica, jessicaNodLoan,
      expectedSentTemplateId1, expectedSentTemplateId2);

    verifyNumberOfSentNotices(2);
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
    ZonedDateTime loanDate = ZonedDateTime.of(2020, 1, 1, 12, 0, 0, 0, UTC);

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(nod)
        .to(james)
        .on(loanDate)
        .at(UUID.randomUUID()));

    ZonedDateTime dueDate = getDateTimeProperty(loan.getJson(), "dueDate");

    verifyNumberOfScheduledNotices(2);

    List<JsonObject> scheduledNotices = scheduledNoticesClient.getAll();
    assertThat(scheduledNotices,
      hasItems(
        hasScheduledLoanNotice(
          loan.getId(), dueDate,
          UPON_AT_TIMING, uponAtTemplateId,
          null, false),
        hasScheduledLoanNotice(
          loan.getId(), afterPeriod.plusDate(dueDate),
          AFTER_TIMING, afterTemplateId,
          afterRecurringPeriod, true)));

    UUID ownerId = feeFineOwnerFixture.ownerForServicePoint(
      UUID.fromString(homeLocation.getJson().getString("primaryServicePoint"))).getId();
    feeFineTypeFixture.overdueFine(ownerId);

    ZonedDateTime checkInDate = ZonedDateTime.of(2020, 1, 25, 12, 0, 0, 0, UTC);

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

    var processingTime = AFTER_RECURRING_PERIOD
      .plusDate(AFTER_PERIOD.plusDate(dueDate))
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
    noticesAreDiscardedWhenItemStatusIsWrong(
      () -> declareLostFixtures.declareItemLost(loanId));
  }

  @Test
  void recurringAfterNoticeIsDiscardedWhenItemIsClaimedReturned() {
    noticesAreDiscardedWhenItemStatusIsWrong(
      () -> claimItemReturnedFixture.claimItemReturned(loanId));
  }

  @Test
  void recurringAfterNoticeIsDiscardedWhenLoanIsAgedToLost() {
    noticesAreDiscardedWhenItemStatusIsWrong(ageToLostFixture::ageToLost);
  }

  private void noticesAreDiscardedWhenItemStatusIsWrong(Runnable itemStatusChangingAction) {
    generateLoanAndScheduledNotices(
      afterNotice(true),
      afterNotice(false),
      beforeNotice(true),
      beforeNotice(false),
      uponAtNotice()
    );

    verifyNumberOfScheduledNotices(5);

    itemStatusChangingAction.run();

    verifyNumberOfScheduledNotices(5);

    var processingTime = AFTER_RECURRING_PERIOD
      .plusDate(AFTER_PERIOD.plusDate(dueDate))
      .plusSeconds(1);

    scheduledNoticeProcessingClient.runLoanNoticesProcessing(processingTime);

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  private void createNotices(int numberOfNotices) {
    ZonedDateTime systemTime = ClockUtil.getZonedDateTime();
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

    addPatronInfoToLoan(loanId.toString());

    dueDate = DateFormatUtil.parseDateTime(loan.getJson().getString("dueDate"));

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
    checkSentNotices(item, borrower, loan, expectedTemplateIds);
  }

  private void checkSentNotices(ItemResource itemResource,
    UserResource userResource, IndividualResource checkoutResource, UUID... expectedTemplateIds) {

    Map<String, Matcher<String>> noticeContextMatchers = new HashMap<>();
    noticeContextMatchers.putAll(TemplateContextMatchers.getUserContextMatchers(userResource));
    noticeContextMatchers.putAll(
      TemplateContextMatchers.getItemContextMatchers(itemResource, true));
    noticeContextMatchers.putAll(TemplateContextMatchers.getLoanContextMatchers(checkoutResource));
    noticeContextMatchers.putAll(
      TemplateContextMatchers.getLoanPolicyContextMatchersForUnlimitedRenewals());
    noticeContextMatchers.putAll(
      TemplateContextMatchers.getLoanAdditionalInfoContextMatchers(LOAN_INFO_ADDED));

    final var matchers = Stream.of(expectedTemplateIds)
      .map(templateId -> hasEmailNoticeProperties(userResource.getId(), templateId,
        noticeContextMatchers))
      .toArray(Matcher[]::new);

    List<JsonObject> sentNotices = FakeModNotify.getSentPatronNotices();

    assertThat(sentNotices, hasSize(expectedTemplateIds.length));
    assertThat(sentNotices, hasItems(matchers));

    verifyNumberOfPublishedEvents(NOTICE, expectedTemplateIds.length);
  }

  private List<JsonObject> createNoticesOverTime(
    Function<Integer, ZonedDateTime> timeOffset, int numberOfNotices) {

    return IntStream.iterate(0, i -> i + 1)
      .boxed()
      .map(timeOffset)
      .map(this::createFakeScheduledNotice)
      .limit(numberOfNotices)
      .collect(Collectors.toList());
  }

  private JsonObject createFakeScheduledNotice(ZonedDateTime nextRunTime) {
    UUID templateId = UUID.randomUUID();
    templateFixture.createDummyNoticeTemplate(templateId);

    return new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("loanId", loan.getId().toString())
      .put("recipientUserId", borrower.getId().toString())
      .put(NEXT_RUN_TIME, formatDateTime(nextRunTime.withZoneSameInstant(UTC)))
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
    String timing, Period recurringPeriod, ZonedDateTime nextRunTime) {

    return hasScheduledLoanNotice(loanId, nextRunTime, timing, templateId, recurringPeriod, true);
  }

  @SafeVarargs
  private void verifyScheduledNotices(Matcher<JsonObject>... noticeMatchers) {
    List<JsonObject> scheduledNotices = scheduledNoticesClient.getAll();

    assertThat(scheduledNotices, hasSize(noticeMatchers.length));
    assertThat(scheduledNotices, hasItems(noticeMatchers));
  }

  private void addPatronInfoToLoan(String loanId){
    addInfoFixture.addInfo(new AddInfoRequestBuilder(loanId,
      "patronInfoAdded", LOAN_INFO_ADDED));
  }
}
