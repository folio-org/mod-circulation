package api.loans;

import static api.support.matchers.ScheduledNoticeMatchers.hasScheduledLoanNotice;
import static org.folio.circulation.support.JsonPropertyFetcher.getDateTimeProperty;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.joda.time.DateTimeZone.UTC;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.JsonPropertyWriter;
import org.folio.circulation.support.http.client.IndividualResource;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.FeeFineBuilder;
import api.support.builders.FeeFineOwnerBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.builders.RequestBuilder;
import io.vertx.core.json.JsonObject;

public class DueDateScheduledNoticesTests extends APITests {

  private static final String BEFORE_TIMING = "Before";
  private static final String UPON_AT_TIMING = "Upon At";
  private static final String AFTER_TIMING = "After";

  @Test
  public void allDueDateNoticesShouldBeScheduledOnCheckoutWhenPolicyDefinesDueDateNoticeConfiguration() {

    UUID beforeTemplateId = UUID.randomUUID();
    Period beforePeriod = Period.days(2);
    Period beforeRecurringPeriod = Period.hours(6);

    UUID uponAtTemplateId = UUID.randomUUID();

    UUID afterTemplateId = UUID.randomUUID();
    Period afterPeriod = Period.days(3);
    Period afterRecurringPeriod = Period.hours(4);

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
        beforeDueDateNoticeConfiguration,
        uponAtDueDateNoticeConfiguration,
        afterDueDateNoticeConfiguration));
    use(noticePolicy);

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate =
      new DateTime(2018, 3, 18, 11, 43, 54, DateTimeZone.UTC);

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(UUID.randomUUID()));
    DateTime dueDate = getDateTimeProperty(loan.getJson(), "dueDate");

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(3));

    List<JsonObject> scheduledNotices = scheduledNoticesClient.getAll();
    assertThat(scheduledNotices,
      hasItems(
        hasScheduledLoanNotice(
          loan.getId(), dueDate.minus(beforePeriod.timePeriod()),
          BEFORE_TIMING, beforeTemplateId,
          beforeRecurringPeriod, true),
        hasScheduledLoanNotice(
          loan.getId(), dueDate,
          UPON_AT_TIMING, uponAtTemplateId,
          null, false),
        hasScheduledLoanNotice(
          loan.getId(), dueDate.plus(afterPeriod.timePeriod()),
          AFTER_TIMING, afterTemplateId,
          afterRecurringPeriod, true)
      ));
  }

  @Test
  public void checkOutSchedulesDifferentBeforeDueDateNotices() {

    UUID firstBeforeTemplateId = UUID.randomUUID();
    Period firstBeforePeriod = Period.weeks(1);
    UUID secondBeforeTemplateId = UUID.randomUUID();
    Period secondBeforePeriod = Period.hours(12);

    JsonObject firstBeforeDueDateNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(firstBeforeTemplateId)
      .withDueDateEvent()
      .withBeforeTiming(firstBeforePeriod)
      .sendInRealTime(false)
      .create();
    JsonObject secondBeforeDueDateNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(secondBeforeTemplateId)
      .withDueDateEvent()
      .withBeforeTiming(secondBeforePeriod)
      .sendInRealTime(true)
      .create();

    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with before due date notices")
      .withLoanNotices(Arrays.asList(
        firstBeforeDueDateNoticeConfiguration,
        secondBeforeDueDateNoticeConfiguration));

    use(noticePolicy);

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate =
      new DateTime(2018, 3, 18, 11, 43, 54, DateTimeZone.UTC);

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(UUID.randomUUID()));
    DateTime dueDate = getDateTimeProperty(loan.getJson(), "dueDate");

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(2));

    List<JsonObject> scheduledNotices = scheduledNoticesClient.getAll();
    assertThat(scheduledNotices,
      hasItems(
        hasScheduledLoanNotice(
          loan.getId(), dueDate.minus(firstBeforePeriod.timePeriod()),
          BEFORE_TIMING, firstBeforeTemplateId,
          null, false),
        hasScheduledLoanNotice(
          loan.getId(), dueDate.minus(secondBeforePeriod.timePeriod()),
          BEFORE_TIMING, secondBeforeTemplateId,
          null, true)
      ));
  }

  @Test
  public void noNoticesShouldBeScheduledOnCheckOutWhenPolicyDoesNotDefineTimeBasedNotices()
    throws
    InterruptedException {

    JsonObject checkOutNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(UUID.randomUUID())
      .withCheckOutEvent()
      .create();
    JsonObject checkInNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(UUID.randomUUID())
      .withCheckInEvent()
      .create();

    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy without time-based notices")
      .withLoanNotices(Arrays.asList(
        checkOutNoticeConfiguration,
        checkInNoticeConfiguration));

    use(noticePolicy);

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate =
      new DateTime(2018, 3, 18, 11, 43, 54, DateTimeZone.UTC);

      checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(UUID.randomUUID()));

    TimeUnit.SECONDS.sleep(1);
    List<JsonObject> scheduledNotices = scheduledNoticesClient.getAll();
    assertThat("No notices should be scheduled",
      scheduledNotices, Matchers.empty());
  }

  @Test
  public void noticesShouldBeRescheduledAfterRenewal() {

    UUID beforeTemplateId = UUID.randomUUID();
    Period beforePeriod = Period.days(2);
    Period beforeRecurringPeriod = Period.hours(6);

    UUID uponAtTemplateId = UUID.randomUUID();

    UUID afterTemplateId = UUID.randomUUID();
    Period afterPeriod = Period.days(3);
    Period afterRecurringPeriod = Period.hours(4);

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
        beforeDueDateNoticeConfiguration,
        uponAtDueDateNoticeConfiguration,
        afterDueDateNoticeConfiguration));

    use(noticePolicy);

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource borrower = usersFixture.steve();

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(item, borrower);
    checkOutFixture.checkOutByBarcode(itemsFixture.basedUponNod(), usersFixture.jessica());

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(6));

    IndividualResource renewedLoan = loansFixture.renewLoan(item, borrower);
    DateTime dueDateAfterRenewal = getDateTimeProperty(renewedLoan.getJson(), "dueDate");

    Matcher<Iterable<JsonObject>> scheduledNoticesAfterRenewalMatcher = hasItems(
      hasScheduledLoanNotice(
        loan.getId(), dueDateAfterRenewal.minus(beforePeriod.timePeriod()),
        BEFORE_TIMING, beforeTemplateId,
        beforeRecurringPeriod, true),
      hasScheduledLoanNotice(
        loan.getId(), dueDateAfterRenewal,
        UPON_AT_TIMING, uponAtTemplateId,
        null, false),
      hasScheduledLoanNotice(
        loan.getId(), dueDateAfterRenewal.plus(afterPeriod.timePeriod()),
        AFTER_TIMING, afterTemplateId,
        afterRecurringPeriod, true)
    );

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, scheduledNoticesAfterRenewalMatcher);
    assertThat(scheduledNoticesClient.getAll(), hasSize(6));
  }

  @Test
  public void noticesShouldBeRescheduledAfterRenewalOverride() {

    UUID beforeTemplateId = UUID.randomUUID();
    Period beforePeriod = Period.days(2);
    Period beforeRecurringPeriod = Period.hours(6);

    UUID uponAtTemplateId = UUID.randomUUID();

    UUID afterTemplateId = UUID.randomUUID();
    Period afterPeriod = Period.days(3);
    Period afterRecurringPeriod = Period.hours(4);

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
        beforeDueDateNoticeConfiguration,
        uponAtDueDateNoticeConfiguration,
        afterDueDateNoticeConfiguration));

    LoanPolicyBuilder loanPolicy = new LoanPolicyBuilder()
      .withName("Not renewable")
      .rolling(Period.weeks(3))
      .notRenewable();

    use(loanPolicy, noticePolicy);

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource borrower = usersFixture.steve();

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(item, borrower);
    DateTime dueDate = getDateTimeProperty(loan.getJson(), "dueDate");
    checkOutFixture.checkOutByBarcode(itemsFixture.basedUponNod(), usersFixture.jessica());

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(6));

    DateTime dueDateAfterRenewal = dueDate.plusWeeks(3);
    loansFixture.overrideRenewalByBarcode(item, borrower,
      "Renewal comment", dueDateAfterRenewal.toString());

    Matcher<Iterable<JsonObject>> scheduledNoticesAfterRenewalMatcher = hasItems(
      hasScheduledLoanNotice(
        loan.getId(), dueDateAfterRenewal.minus(beforePeriod.timePeriod()),
        BEFORE_TIMING, beforeTemplateId,
        beforeRecurringPeriod, true),
      hasScheduledLoanNotice(
        loan.getId(), dueDateAfterRenewal,
        UPON_AT_TIMING, uponAtTemplateId,
        null, false),
      hasScheduledLoanNotice(
        loan.getId(), dueDateAfterRenewal.plus(afterPeriod.timePeriod()),
        AFTER_TIMING, afterTemplateId,
        afterRecurringPeriod, true)
    );

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, scheduledNoticesAfterRenewalMatcher);
    assertThat(scheduledNoticesClient.getAll(), hasSize(6));
  }

  @Test
  public void noticesShouldBeRescheduledAfterRecall() {

    UUID beforeTemplateId = UUID.randomUUID();
    Period beforePeriod = Period.days(2);
    Period beforeRecurringPeriod = Period.hours(6);

    UUID uponAtTemplateId = UUID.randomUUID();

    UUID afterTemplateId = UUID.randomUUID();
    Period afterPeriod = Period.days(3);
    Period afterRecurringPeriod = Period.hours(4);

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
        beforeDueDateNoticeConfiguration,
        uponAtDueDateNoticeConfiguration,
        afterDueDateNoticeConfiguration));

    LoanPolicyBuilder loanPolicy = new LoanPolicyBuilder()
      .withName("Can Circulate Rolling for recall")
      .rolling(Period.weeks(3))
      .withRecallsMinimumGuaranteedLoanPeriod(Period.weeks(2));

    use(loanPolicy, noticePolicy);

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource borrower = usersFixture.steve();

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(item, borrower);
    checkOutFixture.checkOutByBarcode(itemsFixture.basedUponNod(), usersFixture.jessica());

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(6));

    requestsFixture.place(new RequestBuilder()
      .open()
      .recall()
      .forItem(item)
      .by(usersFixture.james())
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));
    IndividualResource loanAfterRecall = loansClient.get(loan.getId());
    DateTime dueDateAfterRecall = getDateTimeProperty(loanAfterRecall.getJson(), "dueDate");

    Matcher<Iterable<JsonObject>> scheduledNoticesAfterRecallMatcher = hasItems(
      hasScheduledLoanNotice(
        loan.getId(), dueDateAfterRecall.minus(beforePeriod.timePeriod()),
        BEFORE_TIMING, beforeTemplateId,
        beforeRecurringPeriod, true),
      hasScheduledLoanNotice(
        loan.getId(), dueDateAfterRecall,
        UPON_AT_TIMING, uponAtTemplateId,
        null, false),
      hasScheduledLoanNotice(
        loan.getId(), dueDateAfterRecall.plus(afterPeriod.timePeriod()),
        AFTER_TIMING, afterTemplateId,
        afterRecurringPeriod, true)
    );

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, scheduledNoticesAfterRecallMatcher);
    assertThat(scheduledNoticesClient.getAll(), hasSize(6));
  }

  @Test
  public void noticesShouldBeRescheduledAfterManualDueDateChange() {

    UUID beforeTemplateId = UUID.randomUUID();
    Period beforePeriod = Period.days(2);
    Period beforeRecurringPeriod = Period.hours(6);

    UUID uponAtTemplateId = UUID.randomUUID();

    UUID afterTemplateId = UUID.randomUUID();
    Period afterPeriod = Period.days(3);
    Period afterRecurringPeriod = Period.hours(4);

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
        beforeDueDateNoticeConfiguration,
        uponAtDueDateNoticeConfiguration,
        afterDueDateNoticeConfiguration));

    use(noticePolicy);

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource borrower = usersFixture.steve();

    IndividualResource loan = checkOutFixture.checkOutByBarcode(item, borrower);
    checkOutFixture.checkOutByBarcode(itemsFixture.basedUponNod(), usersFixture.jessica());

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(6));

    JsonObject loanJson = loan.getJson();
    DateTime dueDate = getDateTimeProperty(loanJson, "dueDate");

    DateTime updatedDueDate = dueDate.plusWeeks(2);
    JsonPropertyWriter.write(loanJson, "dueDate", updatedDueDate);
    loansClient.replace(loan.getId(), loanJson);

    Matcher<Iterable<JsonObject>> scheduledNoticesAfterRecallMatcher = hasItems(
      hasScheduledLoanNotice(
        loan.getId(), updatedDueDate.minus(beforePeriod.timePeriod()),
        BEFORE_TIMING, beforeTemplateId,
        beforeRecurringPeriod, true),
      hasScheduledLoanNotice(
        loan.getId(), updatedDueDate,
        UPON_AT_TIMING, uponAtTemplateId,
        null, false),
      hasScheduledLoanNotice(
        loan.getId(), updatedDueDate.plus(afterPeriod.timePeriod()),
        AFTER_TIMING, afterTemplateId,
        afterRecurringPeriod, true)
    );

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, scheduledNoticesAfterRecallMatcher);
    assertThat(scheduledNoticesClient.getAll(), hasSize(6));
  }

  @Test
  public void scheduledOverdueNoticesShouldBeDeletedAfterOverdueFineIsCharged() {
    useFallbackPolicies(loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandardDoNotCountClosed().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

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
    use(noticePolicy);

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

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(2));

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
          afterRecurringPeriod, true)
      ));

    JsonObject servicePointOwner = new JsonObject();
    servicePointOwner.put("value", homeLocation.getJson().getString("primaryServicePoint"));
    servicePointOwner.put("label", "label");
    UUID ownerId = UUID.randomUUID();
    feeFineOwnersClient.create(new FeeFineOwnerBuilder()
      .withId(ownerId)
      .withOwner("fee-fine-owner")
      .withServicePointOwner(Collections.singletonList(servicePointOwner))
    );

    UUID feeFineId = UUID.randomUUID();
    feeFinesClient.create(new FeeFineBuilder()
      .withId(feeFineId)
      .withFeeFineType("Overdue fine")
      .withOwnerId(ownerId)
      .withAutomatic(true)
    );

    DateTime checkInDate = new DateTime(2020, 1, 25, 12, 0, 0, UTC);

    checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .on(checkInDate)
        .at(checkInServicePointId));

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(0));
  }
}
