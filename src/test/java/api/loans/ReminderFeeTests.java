package api.loans;

import api.support.APITests;
import api.support.builders.*;
import api.support.fakes.FakeModNotify;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.OkapiHeaders;
import api.support.http.UserResource;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.domain.LoanAction;
import org.folio.circulation.support.utils.DateFormatUtil;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.UUID;

import static api.support.fixtures.CalendarExamples.CASE_FIRST_DAY_CLOSED_FOLLOWING_OPEN;
import static api.support.fixtures.CalendarExamples.FIRST_DAY;
import static api.support.fixtures.ItemExamples.basedUponSmallAngryPlanet;
import static api.support.utl.BlockOverridesUtils.buildOkapiHeadersWithPermissions;
import static api.support.utl.PatronNoticeTestHelper.*;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static java.time.ZoneOffset.UTC;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.waitAtMost;
import static org.folio.circulation.domain.representations.LoanProperties.ACTION;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE_ERROR;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.folio.circulation.support.utils.DateFormatUtil.parseDateTime;
import static org.folio.circulation.support.utils.DateTimeUtil.atStartOfDay;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.equalTo;

class ReminderFeeTests extends APITests {

  private ItemResource item;
  private UserResource borrower;
  private ZonedDateTime loanDate;
  private UUID loanPolicyId;
  private UUID requestPolicyId;
  private UUID noticePolicyId;
  private UUID lostItemFeePolicyId;

  private UUID remindersTwoDaysBetweenIncludeClosedDaysPolicyId;
  private UUID remindersOneDayBetweenNotOnClosedDaysId;

  private UUID remindersTwoDaysBetweenNotOnClosedDaysPolicyId;

  private static final String OVERRIDE_RENEWAL_BLOCK_PERMISSION = "circulation.override-renewal-block.post";

  @BeforeEach
  void beforeEach() {

    HoldingBuilder holdingsBuilder = itemsFixture.applyCallNumberHoldings(
      "CN",
      "Prefix",
      "Suffix",
      Collections.singletonList("CopyNumbers"));

    final UUID servicePointId = servicePointsFixture.cd1().getId();

    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      item -> item.withPrimaryServicePoint(servicePointId));

    ItemBuilder itemBuilder = basedUponSmallAngryPlanet(
      materialTypesFixture.book().getId(),
      loanTypesFixture.canCirculate().getId())
      .withPermanentLocation(homeLocation);

    JsonObject servicePointOwner = new JsonObject();
    servicePointOwner.put("value", homeLocation.getJson().getString("primaryServicePoint"));
    servicePointOwner.put("label", "label");
    UUID ownerId = UUID.randomUUID();
    feeFineOwnersClient.create(new FeeFineOwnerBuilder()
      .withId(ownerId)
      .withOwner("fee-fine-owner")
      .withServicePointOwner(Collections.singletonList(servicePointOwner))
    );

    item = itemsFixture.basedUponSmallAngryPlanet(itemBuilder, holdingsBuilder);

    borrower = usersFixture.steve();

    templateFixture.createDummyNoticeTemplate(overdueFinePoliciesFixture.FIRST_REMINDER_TEMPLATE_ID);
    templateFixture.createDummyNoticeTemplate(overdueFinePoliciesFixture.SECOND_REMINDER_TEMPLATE_ID);
    templateFixture.createDummyNoticeTemplate(overdueFinePoliciesFixture.THIRD_REMINDER_TEMPLATE_ID);

    remindersTwoDaysBetweenIncludeClosedDaysPolicyId = overdueFinePoliciesFixture
      .remindersTwoDaysBetween(true).getId();

    IndividualResource policy = overdueFinePoliciesFixture
      .remindersOneDayBetween(false);
    remindersOneDayBetweenNotOnClosedDaysId = policy.getId();

    remindersTwoDaysBetweenNotOnClosedDaysPolicyId = overdueFinePoliciesFixture
      .remindersTwoDaysBetween(false).getId();


    loanPolicyId = loanPoliciesFixture.canCirculateRolling().getId();
    lostItemFeePolicyId = lostItemFeePoliciesFixture.facultyStandard().getId();
    requestPolicyId = requestPoliciesFixture.allowAllRequestPolicy().getId();
    noticePolicyId = noticePoliciesFixture.activeNotice().getId();

    loanDate = getZonedDateTime();

  }

  @Test
  void checkOutWithReminderFeePolicyWillScheduleFirstReminder() {

    useFallbackPolicies(
      loanPolicyId,
      requestPolicyId,
      noticePolicyId,
      remindersTwoDaysBetweenIncludeClosedDaysPolicyId,
      lostItemFeePolicyId);

    checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .on(loanDate)
        .at(servicePointsFixture.cd1()));

    verifyNumberOfScheduledNotices(1);
  }

  @Test
  void willProcessRemindersAllDaysOpenAllowOnClosed() {

    useFallbackPolicies(
      loanPolicyId,
      requestPolicyId,
      noticePolicyId,
      remindersTwoDaysBetweenIncludeClosedDaysPolicyId,
      lostItemFeePolicyId);

    // Check out item, all days open service point
    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .on(loanDate)
        .at(servicePointsFixture.cd1()));
    JsonObject loan = response.getJson();

    // Assert that loan action is Checked out initially
    assertThat(loan.getString(ACTION), Is.is(LoanAction.CHECKED_OUT.getValue()));

    ZonedDateTime dueDate = DateFormatUtil.parseDateTime(loan.getString("dueDate"));

    waitAtMost(1, SECONDS).until(scheduledNoticesClient::getAll, hasSize(1));

    // Run scheduled reminder fee processing from the first day after due date
    ZonedDateTime latestRunTime = dueDate.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);

    // First processing
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    // One day after due date, don't send yet
    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(0));

    latestRunTime = latestRunTime.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    // Second processing. Send.
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);
    // Two days after due date, send first
    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(1));


    latestRunTime = latestRunTime.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    // Third processing, next reminder not yet due.
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(1));

    latestRunTime = latestRunTime.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    // Fourth processing (send).
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    // Two days after latest reminder, send second.
    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(2);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    // Second reminder has zero fee, don't create account
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(1));

    latestRunTime = latestRunTime.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    // Fifth processing (don't send yet).
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    // One day after second reminder, don't send yet
    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(2);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(1));

    latestRunTime = latestRunTime.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    // Sixth processing (send now).
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    // Two days after second reminder, send third and last
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(3);
    verifyNumberOfPublishedEvents(NOTICE, 3);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(2));

    latestRunTime = latestRunTime.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    // Seventh processing (no reminders to send).
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    // One day after third reminder, no scheduled reminder to send, no additional accounts
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(3);
    verifyNumberOfPublishedEvents(NOTICE, 3);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(2));

    // Assert that loan action is changed to Reminder fee after sending reminders
    loan = loansFixture.getLoanById(response.getId()).getJson();
    assertThat(loan.getString(ACTION), Is.is(LoanAction.REMINDER_FEE.getValue()));
  }

  @Test
  void willProcessRemindersAllDaysOpenNoRemindersOnClosed() {

    useFallbackPolicies(
      loanPolicyId,
      requestPolicyId,
      noticePolicyId,
      remindersTwoDaysBetweenNotOnClosedDaysPolicyId,
      lostItemFeePolicyId);

    // Check out item, all days open service point
    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .on(loanDate)
        .at(servicePointsFixture.cd1()));
    final JsonObject loan = response.getJson();
    ZonedDateTime dueDate = DateFormatUtil.parseDateTime(loan.getString("dueDate"));

    waitAtMost(1, SECONDS).until(scheduledNoticesClient::getAll, hasSize(1));

    // Run scheduled reminder fee processing from the first day after due date
    ZonedDateTime latestRunTime = dueDate.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);

    // First processing.
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    // One day after due date, don't send yet
    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(0));

    latestRunTime = latestRunTime.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    // Second processing. Send.
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);
    // Two days after due date, send first
    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(1));


    latestRunTime = latestRunTime.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(1));

    latestRunTime = latestRunTime.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    // Two days after latest reminder, send second (has zero fee so no additional account)
    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(2);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(1));

    latestRunTime = latestRunTime.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    // One day after second reminder, don't send yet
    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(2);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(1));

    latestRunTime = latestRunTime.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    // Two days after second reminder, send third and last
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(3);
    verifyNumberOfPublishedEvents(NOTICE, 3);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(2));

    latestRunTime = latestRunTime.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    // One day after third reminder, no scheduled reminder to send, no additional accounts
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(3);
    verifyNumberOfPublishedEvents(NOTICE, 3);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(2));
  }

  @Test
  void willScheduleRemindersAroundClosedDays() {
    useFallbackPolicies(
      loanPolicyId,
      requestPolicyId,
      noticePolicyId,
      remindersOneDayBetweenNotOnClosedDaysId,
      lostItemFeePolicyId);

    loanDate = atStartOfDay(FIRST_DAY.minusDays(1), UTC).plusHours(10).minusWeeks(3);
    mockClockManagerToReturnFixedDateTime(loanDate);
    JsonObject loan = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .on(loanDate)
        .at(CASE_FIRST_DAY_CLOSED_FOLLOWING_OPEN)).getJson();

    ZonedDateTime dueDate = DateFormatUtil.parseDateTime(loan.getString("dueDate"));
    assertThat(dueDate, is(ZonedDateTime.of(FIRST_DAY.minusDays(1), LocalTime.MIDNIGHT.minusHours(14), UTC)));

    waitAtMost(1, SECONDS).until(scheduledNoticesClient::getAll, hasSize(1));

    ZonedDateTime latestRunTime = dueDate.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);

    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    // Midnight after due date, don't send yet
    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(0));

    latestRunTime = latestRunTime.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(1));

    latestRunTime = latestRunTime.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(2);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    // Fee was zero, no additional account
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(1));

    latestRunTime = latestRunTime.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(3);
    verifyNumberOfPublishedEvents(NOTICE, 3);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(2));
    waitAtMost(1, SECONDS).until(() ->
      FakeModNotify.getSentPatronNotices().stream()
        .filter(r -> r.getString("deliveryChannel").equals("mail")).toList(), hasSize(1));
  }

  @Test
  void willScheduleRemindersIgnoringClosedDays() {
    useFallbackPolicies(
      loanPolicyId,
      requestPolicyId,
      noticePolicyId,
      remindersTwoDaysBetweenIncludeClosedDaysPolicyId,
      lostItemFeePolicyId);

    loanDate = atStartOfDay(FIRST_DAY, UTC).plusHours(10).minusWeeks(3);
    mockClockManagerToReturnFixedDateTime(loanDate);
    JsonObject loan = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .on(loanDate)
        .at(CASE_FIRST_DAY_CLOSED_FOLLOWING_OPEN)).getJson();
    mockClockManagerToReturnDefaultDateTime();

    assertThat(parseDateTime(loan.getString("dueDate")),
      is(ZonedDateTime.of(FIRST_DAY, LocalTime.MIDNIGHT.minusHours(14), UTC)));

    ZonedDateTime dueDate = DateFormatUtil.parseDateTime(loan.getString("dueDate"));

    waitAtMost(1, SECONDS).until(scheduledNoticesClient::getAll, hasSize(1));

    // Run scheduled reminder fee processing from the first day after due date
    ZonedDateTime latestRunTime = dueDate.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    // One day after due date, don't send yet
    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(0));

    latestRunTime = latestRunTime.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);
    // Two days after due date, send first
    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(1));

    latestRunTime = latestRunTime.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(1));

    latestRunTime = latestRunTime.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    // Two days after latest reminder, send second.
    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(2);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    // Zero fee, no additional account
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(1));

    latestRunTime = latestRunTime.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    // One day after second reminder, don't send yet
    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(2);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(1));

    latestRunTime = latestRunTime.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    // Two days after second reminder, send third and last
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(3);
    verifyNumberOfPublishedEvents(NOTICE, 3);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(2));

    latestRunTime = latestRunTime.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    // One day after third reminder, no scheduled reminder to send, no additional accounts
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(3);
    verifyNumberOfPublishedEvents(NOTICE, 3);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(2));
  }

  @Test
  void willStopSendingRemindersCreatingAccountsAfterCheckIn() {

    useFallbackPolicies(
      loanPolicyId,
      requestPolicyId,
      noticePolicyId,
      remindersTwoDaysBetweenIncludeClosedDaysPolicyId,
      lostItemFeePolicyId);

    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .on(loanDate)
        .at(servicePointsFixture.cd1()));

    final JsonObject loan = response.getJson();

    ZonedDateTime dueDate = DateFormatUtil.parseDateTime(loan.getString("dueDate"));
    ZonedDateTime latestRunTime = dueDate.plusDays(2).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(1));

    checkInFixture.checkInByBarcode(item);

    // After one day, the now obsolete reminder has not become due yet, nothing happens
    latestRunTime = latestRunTime.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(1));

    // After two days, the obsolete reminder should be due, and thus be found and discarded
    latestRunTime = latestRunTime.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(1));
  }

  @Test
  void willBlockRenewalIfRuledByReminderFeesPolicyAndHasReminder() {
    useFallbackPolicies(
      loanPolicyId,
      requestPolicyId,
      noticePolicyId,
      remindersTwoDaysBetweenIncludeClosedDaysPolicyId,
      lostItemFeePolicyId);

    // Check out item, all days open service point
    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .on(loanDate)
        .at(servicePointsFixture.cd1()));
    final JsonObject loan = response.getJson();
    ZonedDateTime dueDate = DateFormatUtil.parseDateTime(loan.getString("dueDate"));

    waitAtMost(1, SECONDS).until(scheduledNoticesClient::getAll, hasSize(1));

    ZonedDateTime latestRunTime = dueDate.plusDays(2).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);
    // Two days after due date, send reminder
    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(1));

    // Attempt renewal when Reminders Policy allowRenewalOfItemsWithReminderFees
    // is set to 'False' and loan has reminders already sent out
    final Response renewalResponse = loansFixture.attemptRenewal(item, borrower);

    // Assert that error message for renewal block is received in response
    assertThat(renewalResponse.getBody(), containsString(
      "Renewals not allowed for loans with reminders."));
  }

  @Test
  void willAllowRenewalIfRuledByReminderFeesPolicyButHasNoReminders() {
    useFallbackPolicies(
      loanPolicyId,
      requestPolicyId,
      noticePolicyId,
      remindersTwoDaysBetweenIncludeClosedDaysPolicyId,
      lostItemFeePolicyId);

    // Check out item, all days open service point
    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .on(loanDate)
        .at(servicePointsFixture.cd1()));
    final JsonObject loan = response.getJson();
    ZonedDateTime dueDate = DateFormatUtil.parseDateTime(loan.getString("dueDate"));

    waitAtMost(1, SECONDS).until(scheduledNoticesClient::getAll, hasSize(1));

    ZonedDateTime latestRunTime = dueDate.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);
    // One day after due date, no reminder yet
    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(0));

    assertThat("has no renewals yet", not(loan.containsKey("renewalCount")));

    // Attempt renewal when Reminders Policy allowRenewalOfItemsWithReminderFees
    // is set to 'False' but loan has no reminders yet
    JsonObject renewedLoan = loansFixture.attemptRenewal(200,
      item, borrower).getJson();

    assertThat("renewal count should be 1 after renewal",
      renewedLoan.getInteger("renewalCount"), is(1));

  }

  @Test
  void willAllowRenewalIfHasRemindersButRemindersAllowed() {
    useFallbackPolicies(
      loanPolicyId,
      requestPolicyId,
      noticePolicyId,
      remindersOneDayBetweenNotOnClosedDaysId,
      lostItemFeePolicyId);

    // Check out item, all days open service point
    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .on(loanDate)
        .at(servicePointsFixture.cd1()));
    final JsonObject loan = response.getJson();
    ZonedDateTime dueDate = DateFormatUtil.parseDateTime(loan.getString("dueDate"));

    waitAtMost(1, SECONDS).until(scheduledNoticesClient::getAll, hasSize(1));

    ZonedDateTime latestRunTime = dueDate.plusDays(2).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);
    // One day after due date, no reminder yet
    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(1));

    assertThat("has no renewals yet", not(loan.containsKey("renewalCount")));

    // Attempt renewal when there are reminders but Reminders Policy
    // allowRenewalOfItemsWithReminderFees is set to 'True'
    JsonObject renewedLoan = loansFixture.attemptRenewal(200,
      item, borrower).getJson();

    assertThat("renewal count should be 1 after renewal",
      renewedLoan.getInteger("renewalCount"), is(1));

  }

  @Test
  void willRenewWithOverride() {
    useFallbackPolicies(
      loanPolicyId,
      requestPolicyId,
      noticePolicyId,
      remindersTwoDaysBetweenIncludeClosedDaysPolicyId,
      lostItemFeePolicyId);

    // Check out item, all days open service point
    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .on(loanDate)
        .at(servicePointsFixture.cd1()));
    final JsonObject loan = response.getJson();
    ZonedDateTime dueDate = DateFormatUtil.parseDateTime(loan.getString("dueDate"));

    final OkapiHeaders okapiHeaders = buildOkapiHeadersWithPermissions(
      OVERRIDE_RENEWAL_BLOCK_PERMISSION);

    waitAtMost(1, SECONDS).until(scheduledNoticesClient::getAll, hasSize(1));

    ZonedDateTime latestRunTime = dueDate.plusDays(2).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);
    // Two days after due date, send reminder
    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(1));

    // Attempt renewal with override when Reminders Policy allowRenewalOfItemsWithReminderFees
    // is set to 'False' and loan has reminders already sent out
    JsonObject renewedLoan = loansFixture.renewLoan(
      new RenewByBarcodeRequestBuilder()
        .forItem(item)
        .forUser(borrower)
      .withServicePointId(servicePointsFixture.cd1().getId().toString())
      .withOverrideBlocks(
        new RenewBlockOverrides()
          .withRenewalBlock(new JsonObject())
          .withComment("TEST_COMMENT").create()),
      okapiHeaders).getJson();

    assertThat("renewal count should be 1 after renewal",
      renewedLoan.getInteger("renewalCount"), is(1));
  }

  @Test
  void willResetRemindersRescheduleNoticeOnRenewal() {
    useFallbackPolicies(
      loanPolicyId,
      requestPolicyId,
      noticePolicyId,
      remindersOneDayBetweenNotOnClosedDaysId,
      lostItemFeePolicyId);

    // Check out item, all days open service point
    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .on(loanDate)
        .at(servicePointsFixture.cd1()));
    final JsonObject loan = response.getJson();
    ZonedDateTime dueDate = DateFormatUtil.parseDateTime(loan.getString("dueDate"));

    waitAtMost(1, SECONDS).until(scheduledNoticesClient::getAll, hasSize(1));
    JsonObject initialScheduledNotice = scheduledNoticesClient.getAll().get(0);

    ZonedDateTime latestRunTime = dueDate.plusDays(2).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(1));

    JsonObject loanAfterReminder = loansClient.getById(UUID.fromString(loan.getString("id"))).getJson();
    assertThat("loan should have first reminder", loanAfterReminder.encode(),
      hasJsonPath("reminders.lastFeeBilled.number", is(1)));

    JsonObject scheduledNoticeSecondReminder = scheduledNoticesClient.getAll().get(0);

    JsonObject loanAfterRenewal = loansFixture.attemptRenewal(200,
      item, borrower).getJson();
    waitAtMost(1, SECONDS).until(scheduledNoticesClient::getAll, hasSize(1));
    JsonObject rescheduledNotice = scheduledNoticesClient.getAll().get(0);

    assertThat("renewal count should be 1 after renewal",
      loanAfterRenewal.getInteger("renewalCount"), is(1));
    assertThat("rescheduled reminder should have different runtime than previous, second reminder",
      scheduledNoticeSecondReminder.getInstant("nextRunTime").toString(),
      is(not(equalTo(rescheduledNotice.getInstant("nextRunTime").toString()))));
    assertThat("rescheduled notice should be scheduled later than initial notice",
      rescheduledNotice.getInstant("nextRunTime")
        .isAfter(initialScheduledNotice.getInstant("nextRunTime")));
    assertThat("Loan should have no reminders after renewal",
      !loanAfterRenewal.containsKey("reminders"));
  }

  @Test
  void willResetRemindersRescheduleNoticeOnManualDueDateChange() {
    useFallbackPolicies(
      loanPolicyId,
      requestPolicyId,
      noticePolicyId,
      remindersOneDayBetweenNotOnClosedDaysId,
      lostItemFeePolicyId);

    // Check out item, all days open service point
    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .on(loanDate)
        .at(servicePointsFixture.cd1()));
    final JsonObject loan = response.getJson();
    ZonedDateTime dueDate = DateFormatUtil.parseDateTime(loan.getString("dueDate"));

    waitAtMost(1, SECONDS).until(scheduledNoticesClient::getAll, hasSize(1));
    JsonObject initialScheduledNotice = scheduledNoticesClient.getAll().get(0);

    ZonedDateTime latestRunTime = dueDate.plusDays(2).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(1));

    JsonObject scheduledNoticeSecondReminder = scheduledNoticesClient.getAll().get(0);

    JsonObject loanAfterReminder = loansClient.getById(UUID.fromString(loan.getString("id"))).getJson();
    assertThat("loan should have first reminder", loanAfterReminder.encode(),
      hasJsonPath("reminders.lastFeeBilled.number", is(1)));

    ChangeDueDateRequestBuilder changeDueDate =
      new ChangeDueDateRequestBuilder()
        .forLoan(loan.getString("id"))
        .withDueDate(dueDate.plusDays(10));
    changeDueDateFixture.attemptChangeDueDate(changeDueDate).getJson();
    waitAtMost(1, SECONDS).until(scheduledNoticesClient::getAll, hasSize(1));
    JsonObject rescheduledNotice = scheduledNoticesClient.getAll().get(0);
    JsonObject loanAfterDueDateChange = loansClient.getById(UUID.fromString(loan.getString("id"))).getJson();
    assertThat("Loan should have no reminders after due date change",
      !loanAfterDueDateChange.containsKey("reminders"));
    assertThat("rescheduled reminder should have different runtime than previous, second reminder",
      scheduledNoticeSecondReminder.getInstant("nextRunTime").toString(),
      is(not(equalTo(rescheduledNotice.getInstant("nextRunTime").toString()))));
    assertThat("rescheduled notice should be scheduled later than initial notice",
      rescheduledNotice.getInstant("nextRunTime")
        .isAfter(initialScheduledNotice.getInstant("nextRunTime")));
  }

  @Test
  void willResetRemindersRescheduleNoticeOnRecall() {
    useFallbackPolicies(
      loanPolicyId,
      requestPolicyId,
      noticePolicyId,
      remindersOneDayBetweenNotOnClosedDaysId,
      lostItemFeePolicyId);

    // Check out item, all days open service point
    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .on(loanDate)
        .at(servicePointsFixture.cd1()));
    final JsonObject loan = response.getJson();
    ZonedDateTime dueDate = DateFormatUtil.parseDateTime(loan.getString("dueDate"));

    waitAtMost(1, SECONDS).until(scheduledNoticesClient::getAll, hasSize(1));
    JsonObject initialScheduledNotice = scheduledNoticesClient.getAll().get(0);

    ZonedDateTime latestRunTime = dueDate.plusDays(2).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(1));

    JsonObject scheduledNoticeSecondReminder = scheduledNoticesClient.getAll().get(0);

    JsonObject loanAfterReminder = loansClient.getById(UUID.fromString(loan.getString("id"))).getJson();
    assertThat("loan should have first reminder", loanAfterReminder.encode(),
      hasJsonPath("reminders.lastFeeBilled.number", is(1)));

    requestsFixture.place(new RequestBuilder()
      .open()
      .recall()
      .forItem(item)
      .by(usersFixture.james())
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));

    waitAtMost(1, SECONDS).until(scheduledNoticesClient::getAll, hasSize(1));
    JsonObject loanAfterRecall = loansClient.getById(UUID.fromString(loan.getString("id"))).getJson();

    JsonObject rescheduledNotice = scheduledNoticesClient.getAll().get(0);

    assertThat("rescheduled reminder should have different runtime than previous, second reminder",
      scheduledNoticeSecondReminder.getInstant("nextRunTime").toString(),
      is(not(equalTo(rescheduledNotice.getInstant("nextRunTime").toString()))));
    assertThat("Loan should have no reminders after recall",
      !loanAfterRecall.containsKey("reminders"));
    assertThat("a new, rescheduled notice with a new ID should be created",
      rescheduledNotice.getString("id"),
        is(not(equalTo(initialScheduledNotice.getString("id")))));
  }

}
