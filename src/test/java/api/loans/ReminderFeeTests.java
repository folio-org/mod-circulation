package api.loans;

import api.support.APITests;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.HoldingBuilder;
import api.support.builders.ItemBuilder;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.UserResource;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.utils.DateFormatUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.UUID;

import static api.support.fixtures.ItemExamples.basedUponSmallAngryPlanet;
import static api.support.utl.PatronNoticeTestHelper.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.waitAtMost;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE_ERROR;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.folio.circulation.support.utils.DateTimeUtil.atEndOfDay;
import static org.hamcrest.Matchers.hasSize;

class ReminderFeeTests extends APITests {

  private ItemResource item;
  private UserResource borrower;

  private ZonedDateTime loanDate;

  @BeforeEach
  void beforeEach() {
    ItemBuilder itemBuilder = basedUponSmallAngryPlanet(
      materialTypesFixture.book().getId(), loanTypesFixture.canCirculate().getId());

    HoldingBuilder holdingBuilder = itemsFixture.applyCallNumberHoldings(
      "CN",
      "Prefix",
      "Suffix",
      Collections.singletonList("CopyNumbers"));

    item = itemsFixture.basedUponSmallAngryPlanet(itemBuilder, holdingBuilder);
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
        .at(UUID.randomUUID()));

    verifyNumberOfScheduledNotices(1);
  }

  @Test
  void willSendThreeRemindersAndCreateThreeAccountsThenStop() {

    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .on(loanDate)
        .at(UUID.randomUUID()));

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
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(2));

    ZonedDateTime thirdRunTime = dueDate.plusMinutes(6);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(thirdRunTime);

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(3);
    verifyNumberOfPublishedEvents(NOTICE, 3);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(3));

    ZonedDateTime fourthRunTime = dueDate.plusMinutes(8);
    scheduledNoticeProcessingClient.runScheduledDigitalRemindersProcessing(fourthRunTime);

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(3);
    verifyNumberOfPublishedEvents(NOTICE, 3);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    waitAtMost(1, SECONDS).until(accountsClient::getAll, hasSize(3));
  }

  @Test
  void willStopSendingRemindersCreatingAccountsAfterCheckIn() {

    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .on(loanDate)
        .at(UUID.randomUUID()));

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
}
