package api.loans;

import static api.support.fixtures.ItemExamples.basedUponSmallAngryPlanet;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfPublishedEvents;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfScheduledNotices;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfSentNotices;
import static java.time.ZoneOffset.UTC;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE_ERROR;
import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTime;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.utils.DateFormatUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.ItemBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.fakes.FakeModNotify;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import io.vertx.core.json.JsonObject;

class DueDateScheduledNoticeFormatPreferenceTests extends APITests {

  private static final UUID EMAIL_TEMPLATE_ID = UUID.randomUUID();
  private static final UUID SMS_TEMPLATE_ID = UUID.randomUUID();
  private static final UUID FOREIGN_TEMPLATE_ID = UUID.randomUUID();

  private static final String CONTACT_TYPE_EMAIL = "002";
  private static final String CONTACT_TYPE_SMS = "003";
  private static final String CONTACT_TYPE_PHONE = "004";

  private static final Period BEFORE_PERIOD = Period.days(2);
  private static final Period SECOND_BEFORE_PERIOD = Period.hours(12);
  private static final Period AFTER_PERIOD = Period.days(3);
  private static final Period AFTER_RECURRING_PERIOD = Period.hours(4);

  private static final ZonedDateTime LOAN_DATE =
    ZonedDateTime.of(2018, 3, 18, 11, 43, 54, 0, UTC);

  private ItemResource item;
  private IndividualResource loan;
  private ZonedDateTime dueDate;

  @BeforeEach
  void beforeEach() {
    ItemBuilder itemBuilder = basedUponSmallAngryPlanet(
      materialTypesFixture.book().getId(), loanTypesFixture.canCirculate().getId());

    item = itemsFixture.basedUponSmallAngryPlanet(itemBuilder,
      itemsFixture.thirdFloorHoldings());

    templateFixture.createDummyNoticeTemplate(EMAIL_TEMPLATE_ID);
    templateFixture.createDummyNoticeTemplate(SMS_TEMPLATE_ID);
    templateFixture.createDummyNoticeTemplate(FOREIGN_TEMPLATE_ID);
  }

  @Test
  void scheduledNoticesStoreFormatFromPolicyConfiguration() {
    usePolicy(
      beforeNotice(EMAIL_TEMPLATE_ID, BEFORE_PERIOD, false),
      smsBeforeNotice(SMS_TEMPLATE_ID, BEFORE_PERIOD, false));

    checkOutTo(patronWithoutPreference());

    verifyNumberOfScheduledNotices(2);
    assertThat(scheduledNoticeFormatsByTemplateId(),
      containsInAnyOrder(
        EMAIL_TEMPLATE_ID + "=Email",
        SMS_TEMPLATE_ID + "=SMS"));
  }

  @Test
  void bothBeforeNoticesAreSentWhenTimingPeriodsDiffer() {
    usePolicy(
      beforeNotice(EMAIL_TEMPLATE_ID, BEFORE_PERIOD, false),
      smsBeforeNotice(SMS_TEMPLATE_ID, SECOND_BEFORE_PERIOD, false));

    checkOutTo(patronWithoutPreference());

    verifyNumberOfScheduledNotices(2);

    scheduledNoticeProcessingClient.runLoanNoticesProcessing(dueDate.minusSeconds(1));

    verifyNumberOfSentNotices(2);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    verifyNumberOfScheduledNotices(0);

    assertThat(sentTemplateIds(),
      containsInAnyOrder(EMAIL_TEMPLATE_ID.toString(), SMS_TEMPLATE_ID.toString()));
  }

  @Test
  void onlySmsNoticeIsSentWhenSmsIsPreferred() {
    useEmailAndSmsPolicyWithSameTiming(true);

    checkOutTo(patronPreferring(CONTACT_TYPE_SMS));

    verifyNumberOfScheduledNotices(2);

    scheduledNoticeProcessingClient.runLoanNoticesProcessing(dueDate.minusSeconds(1));

    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    assertSingleSentNotice(SMS_TEMPLATE_ID, "sms", "text/plain");
    verifyNumberOfScheduledNotices(0);
  }

  @Test
  void onlyEmailNoticeIsSentWhenPatronHasNoPreference() {
    useEmailAndSmsPolicyWithSameTiming(true);

    checkOutTo(patronWithoutPreference());

    scheduledNoticeProcessingClient.runLoanNoticesProcessing(dueDate.minusSeconds(1));

    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    assertSingleSentNotice(EMAIL_TEMPLATE_ID, "email", "text/html");
    verifyNumberOfScheduledNotices(0);
  }

  @Test
  void emailNoticeIsSentWhenPreferredContactTypeHasNoNoticeFormat() {
    useEmailAndSmsPolicyWithSameTiming(true);

    checkOutTo(patronPreferring(CONTACT_TYPE_PHONE));

    scheduledNoticeProcessingClient.runLoanNoticesProcessing(dueDate.minusSeconds(1));

    verifyNumberOfSentNotices(1);
    assertSingleSentNotice(EMAIL_TEMPLATE_ID, "email", "text/html");
    verifyNumberOfScheduledNotices(0);
  }

  @Test
  void emailNoticeIsSentWhenPreferredFormatIsNotConfigured() {
    usePolicy(
      beforeNotice(EMAIL_TEMPLATE_ID, BEFORE_PERIOD, false),
      printBeforeNotice(FOREIGN_TEMPLATE_ID, BEFORE_PERIOD));

    checkOutTo(patronPreferring(CONTACT_TYPE_SMS));

    scheduledNoticeProcessingClient.runLoanNoticesProcessing(dueDate.minusSeconds(1));

    verifyNumberOfSentNotices(1);
    assertSingleSentNotice(EMAIL_TEMPLATE_ID, "email", "text/html");
    verifyNumberOfScheduledNotices(0);
  }

  @Test
  void bothNoticesAreSentWhenBothFormatsArePreferred() {
    useEmailAndSmsPolicyWithSameTiming(true);

    checkOutTo(patronPreferring(CONTACT_TYPE_EMAIL, CONTACT_TYPE_SMS));

    scheduledNoticeProcessingClient.runLoanNoticesProcessing(dueDate.minusSeconds(1));

    verifyNumberOfSentNotices(2);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    assertThat(sentTemplateIds(),
      containsInAnyOrder(EMAIL_TEMPLATE_ID.toString(), SMS_TEMPLATE_ID.toString()));
    verifyNumberOfScheduledNotices(0);
  }

  @Test
  void singleFormatNoticeIsSentRegardlessOfPreference() {
    usePolicy(beforeNotice(EMAIL_TEMPLATE_ID, BEFORE_PERIOD, false));

    checkOutTo(patronPreferring(CONTACT_TYPE_SMS));

    scheduledNoticeProcessingClient.runLoanNoticesProcessing(dueDate.minusSeconds(1));

    verifyNumberOfSentNotices(1);
    assertSingleSentNotice(EMAIL_TEMPLATE_ID, "email", "text/html");
    verifyNumberOfScheduledNotices(0);
  }

  @Test
  void filteredOutRecurringNoticeIsRescheduledNotDeleted() {
    usePolicy(
      afterNotice(EMAIL_TEMPLATE_ID, AFTER_PERIOD, AFTER_RECURRING_PERIOD),
      smsAfterNotice(SMS_TEMPLATE_ID, AFTER_PERIOD, AFTER_RECURRING_PERIOD));

    checkOutTo(patronPreferring(CONTACT_TYPE_SMS));

    verifyNumberOfScheduledNotices(2);

    ZonedDateTime runTime = AFTER_PERIOD.plusDate(dueDate).plusSeconds(1);
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(runTime);

    verifyNumberOfSentNotices(1);
    assertSingleSentNotice(SMS_TEMPLATE_ID, "sms", "text/plain");
    verifyNumberOfScheduledNotices(2);
  }

  @Test
  void noticeIsSentWhenItsTemplateIsNotPresentInPolicy() {
    useEmailAndSmsPolicyWithSameTiming(true);

    checkOutTo(patronPreferring(CONTACT_TYPE_SMS));

    verifyNumberOfScheduledNotices(2);

    scheduledNoticesClient.create(fakeScheduledNotice(FOREIGN_TEMPLATE_ID, "Email",
      dueDate.minusHours(1)));

    verifyNumberOfScheduledNotices(3);

    scheduledNoticeProcessingClient.runLoanNoticesProcessing(dueDate.minusSeconds(1));

    verifyNumberOfSentNotices(2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    assertThat(sentTemplateIds(),
      containsInAnyOrder(SMS_TEMPLATE_ID.toString(), FOREIGN_TEMPLATE_ID.toString()));
    verifyNumberOfScheduledNotices(0);
  }

  @Test
  void groupedNoticeUsesPreferredFormat() {
    usePolicy(
      uponAtNotice(EMAIL_TEMPLATE_ID),
      smsUponAtNotice(SMS_TEMPLATE_ID));

    checkOutTo(patronPreferring(CONTACT_TYPE_SMS));

    verifyNumberOfScheduledNotices(2);

    scheduledNoticeProcessingClient.runDueDateNotRealTimeNoticesProcessing(dueDate.plusDays(1));

    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    assertSingleSentNotice(SMS_TEMPLATE_ID, "sms", "text/plain");
    verifyNumberOfScheduledNotices(0);
  }

  private void useEmailAndSmsPolicyWithSameTiming(boolean realTime) {
    usePolicy(
      beforeNotice(EMAIL_TEMPLATE_ID, BEFORE_PERIOD, !realTime),
      smsBeforeNotice(SMS_TEMPLATE_ID, BEFORE_PERIOD, !realTime));
  }

  private void usePolicy(JsonObject... noticeConfigurations) {
    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with due date notices in several formats")
      .withLoanNotices(List.of(noticeConfigurations));

    useFallbackPolicies(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId(),
      overdueFinePoliciesFixture.noOverdueFine().getId(),
      lostItemFeePoliciesFixture.chargeFee().getId());
  }

  private void checkOutTo(IndividualResource borrower) {
    loan = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .on(LOAN_DATE)
        .at(UUID.randomUUID()));

    dueDate = DateFormatUtil.parseDateTime(loan.getJson().getString("dueDate"));
  }

  private IndividualResource patronWithoutPreference() {
    return usersFixture.steve();
  }

  private IndividualResource patronPreferring(String... contactTypeIds) {
    return usersFixture.steve(
      builder -> builder.withPreferredContactTypeIds(List.of(contactTypeIds)));
  }

  private static JsonObject beforeNotice(UUID templateId, Period timing, boolean grouped) {
    return new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withDueDateEvent()
      .withBeforeTiming(timing)
      .withEmailFormat()
      .sendInRealTime(!grouped)
      .create();
  }

  private static JsonObject smsBeforeNotice(UUID templateId, Period timing, boolean grouped) {
    return new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withDueDateEvent()
      .withBeforeTiming(timing)
      .withSmsFormat()
      .sendInRealTime(!grouped)
      .create();
  }

  private static JsonObject printBeforeNotice(UUID templateId, Period timing) {
    return new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withDueDateEvent()
      .withBeforeTiming(timing)
      .withPrintFormat()
      .sendInRealTime(true)
      .create();
  }

  private static JsonObject afterNotice(UUID templateId, Period timing, Period recurring) {
    return new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withDueDateEvent()
      .withAfterTiming(timing)
      .recurring(recurring)
      .withEmailFormat()
      .sendInRealTime(true)
      .create();
  }

  private static JsonObject smsAfterNotice(UUID templateId, Period timing, Period recurring) {
    return new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withDueDateEvent()
      .withAfterTiming(timing)
      .recurring(recurring)
      .withSmsFormat()
      .sendInRealTime(true)
      .create();
  }

  private static JsonObject uponAtNotice(UUID templateId) {
    return new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withDueDateEvent()
      .withUponAtTiming()
      .withEmailFormat()
      .sendInRealTime(false)
      .create();
  }

  private static JsonObject smsUponAtNotice(UUID templateId) {
    return new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withDueDateEvent()
      .withUponAtTiming()
      .withSmsFormat()
      .sendInRealTime(false)
      .create();
  }

  private JsonObject fakeScheduledNotice(UUID templateId, String format,
    ZonedDateTime nextRunTime) {

    return new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("loanId", loan.getId().toString())
      .put("recipientUserId", loan.getJson().getString("userId"))
      .put("nextRunTime", formatDateTime(nextRunTime.withZoneSameInstant(UTC)))
      .put("triggeringEvent", "Due date")
      .put("noticeConfig", new JsonObject()
        .put("timing", "Before")
        .put("templateId", templateId.toString())
        .put("format", format)
        .put("sendInRealTime", true));
  }

  private List<String> scheduledNoticeFormatsByTemplateId() {
    return scheduledNoticesClient.getAll().stream()
      .map(notice -> notice.getJsonObject("noticeConfig"))
      .map(config -> config.getString("templateId") + "=" + config.getString("format"))
      .toList();
  }

  private static List<String> sentTemplateIds() {
    return FakeModNotify.getSentPatronNotices().stream()
      .map(notice -> notice.getString("templateId"))
      .toList();
  }

  private static void assertSingleSentNotice(UUID templateId, String deliveryChannel,
    String outputFormat) {

    List<JsonObject> sentNotices = FakeModNotify.getSentPatronNotices();

    assertThat(sentNotices, hasSize(1));

    JsonObject notice = sentNotices.getFirst();
    assertThat(notice.getString("templateId"), is(templateId.toString()));
    assertThat(notice.getString("deliveryChannel"), is(deliveryChannel));
    assertThat(notice.getString("outputFormat"), is(outputFormat));
  }

}
