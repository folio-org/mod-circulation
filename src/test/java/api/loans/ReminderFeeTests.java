package api.loans;

import api.support.APITests;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.FeeFineOwnerBuilder;
import api.support.builders.HoldingBuilder;
import api.support.builders.ItemBuilder;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.UserResource;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.utils.DateFormatUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.UUID;

import static api.support.fixtures.ItemExamples.basedUponSmallAngryPlanet;
import static api.support.utl.PatronNoticeTestHelper.*;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.waitAtMost;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE_ERROR;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.folio.circulation.support.utils.DateTimeUtil.atEndOfDay;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;

class ReminderFeeTests extends APITests {

  private ItemResource item;
  private UserResource borrower;

  private ZonedDateTime loanDate;

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

    IndividualResource loanPolicy = loanPoliciesFixture.canCirculateFixed();
    IndividualResource overdueFinePolicy = overdueFinePoliciesFixture.reminderFeesPolicy();
    IndividualResource lostItemFeePolicy = lostItemFeePoliciesFixture.facultyStandard();

    useFallbackPolicies(loanPolicy.getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePolicy.getId(),
      lostItemFeePolicy.getId());

    loanDate = atEndOfDay(getZonedDateTime());

    getZonedDateTime()
      .withMonth(3)
      .withDayOfMonth(18)
      .withHour(11)
      .withMinute(43)
      .withSecond(54)
      .truncatedTo(ChronoUnit.SECONDS);

  }

  @Test
  void checkOutWithReminderFeePolicyWillScheduleFirstReminder() {

    checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .on(loanDate)
        .at(servicePointsFixture.cd1()));

    verifyNumberOfScheduledNotices(1);
  }

  @Test
  void willSendThreeRemindersAndCreateTwoAccountsThenStop() {

    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .on(loanDate)
        .at(servicePointsFixture.cd1()));

    final JsonObject loan = response.getJson();

    ZonedDateTime dueDate = DateFormatUtil.parseDateTime(loan.getString("dueDate"));

    ZonedDateTime firstRunTime = dueDate.plusMinutes(2);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(firstRunTime);

    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(1));

    ZonedDateTime secondRunTime = dueDate.plusMinutes(4);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(secondRunTime);

    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(2);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    // Second reminder has zero fee, don't create account
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(1));

    ZonedDateTime thirdRunTime = dueDate.plusMinutes(6);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(thirdRunTime);

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(3);
    verifyNumberOfPublishedEvents(NOTICE, 3);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(2));

    ZonedDateTime fourthRunTime = dueDate.plusMinutes(8);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(fourthRunTime);

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(3);
    verifyNumberOfPublishedEvents(NOTICE, 3);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(2));
  }

  @Test
  void willStopSendingRemindersCreatingAccountsAfterCheckIn() {

    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .on(loanDate)
        .at(servicePointsFixture.cd1()));

    final JsonObject loan = response.getJson();

    ZonedDateTime dueDate = DateFormatUtil.parseDateTime(loan.getString("dueDate"));

    ZonedDateTime firstRunTime = dueDate.plusMinutes(2);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(firstRunTime);

    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(1));

    checkInFixture.checkInByBarcode(item);

    ZonedDateTime secondRunTime = dueDate.plusMinutes(4);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(secondRunTime);

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(1));
  }

  @Test
  void blockRenewalIfRuledByRemindersFeePolicyAndHasReminders() {
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
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(2));

    latestRunTime = latestRunTime.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    // Fifth processing (don't send yet).
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    // One day after second reminder, don't send yet
    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(2);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(2));

    latestRunTime = latestRunTime.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    // Sixth processing (send now).
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    // Two days after second reminder, send third and last
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(3);
    verifyNumberOfPublishedEvents(NOTICE, 3);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(3));

    latestRunTime = latestRunTime.plusDays(1).truncatedTo(DAYS.toChronoUnit()).plusMinutes(1);
    // Seventh processing (no reminders to send).
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(latestRunTime);

    // One day after third reminder, no scheduled reminder to send, no additional accounts
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(3);
    verifyNumberOfPublishedEvents(NOTICE, 3);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(3));

    // Attempt renewal when Reminders Policy allowRenewalOfItemsWithReminderFees is set to 'False' and loan has reminders already sent out
    final Response renewalResponse = loansFixture.attemptRenewal(item, borrower);

    // Assert that error message for renewal block is received in response
    assertThat(renewalResponse.getBody(), containsString("Patron's fee/fine balance exceeds the limit for their patron group! Pay up!"));
  }
}
