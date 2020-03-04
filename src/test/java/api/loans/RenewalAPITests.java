package api.loans;

import static api.support.builders.FixedDueDateSchedule.forDay;
import static api.support.builders.FixedDueDateSchedule.todayOnly;
import static api.support.builders.FixedDueDateSchedule.wholeMonth;
import static api.support.builders.ItemBuilder.CHECKED_OUT;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_SERVICE_POINT_NEXT_DAY;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_SERVICE_POINT_PREV_DAY;
import static api.support.fixtures.CalendarExamples.CASE_WED_THU_FRI_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.END_TIME_SECOND_PERIOD;
import static api.support.fixtures.CalendarExamples.START_TIME_FIRST_PERIOD;
import static api.support.fixtures.CalendarExamples.START_TIME_SECOND_PERIOD;
import static api.support.fixtures.CalendarExamples.WEDNESDAY_DATE;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.TextDateTimeMatcher.withinSecondsAfter;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static api.support.matchers.ValidationErrorMatchers.hasUUIDParameter;
import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.END_OF_A_DAY;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.StringUtils;
import org.awaitility.Awaitility;
import org.folio.circulation.domain.policy.DueDateManagement;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.domain.representations.ItemProperties;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.ValidationError;
import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.core.Is;
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.Seconds;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.FeeFineBuilder;
import api.support.builders.FeeFineOwnerBuilder;
import api.support.builders.ClaimItemReturnedRequestBuilder;
import api.support.builders.FixedDueDateSchedule;
import api.support.builders.FixedDueDateSchedulesBuilder;
import api.support.builders.ItemBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.builders.RequestBuilder;
import api.support.fixtures.ConfigurationExample;
import api.support.fixtures.ItemExamples;
import api.support.fixtures.TemplateContextMatchers;
import api.support.http.InventoryItemResource;
import api.support.matchers.UUIDMatcher;
import io.vertx.core.json.JsonObject;

abstract class RenewalAPITests extends APITests {
  abstract Response attemptRenewal(IndividualResource user, IndividualResource item);

  abstract IndividualResource renew(IndividualResource user, IndividualResource item);

  abstract Matcher<ValidationError> hasUserRelatedParameter(IndividualResource user);

  abstract Matcher<ValidationError> hasItemRelatedParameter(IndividualResource item);

  abstract Matcher<ValidationError> hasItemNotFoundMessage(IndividualResource item);

  @Test
  public void canRenewRollingLoanFromSystemDate() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource loan = loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
            new DateTime(2018, 4, 21, 11, 21, 43, DateTimeZone.UTC));
    final UUID loanId = loan
      .getId();

    //TODO: Renewal based upon system date,
    // needs to be approximated, at least until we introduce a calendar and clock
    DateTime approximateRenewalDate = DateTime.now(DateTimeZone.UTC);

    final JsonObject renewedLoan = renew(smallAngryPlanet, jessica).getJson();

    assertThat(renewedLoan.getString("id"), is(loanId.toString()));

    assertThat("user ID should match barcode",
      renewedLoan.getString("userId"), is(jessica.getId().toString()));

    assertThat("item ID should match barcode",
      renewedLoan.getString("itemId"), is(smallAngryPlanet.getId().toString()));

    assertThat("status should be open",
      renewedLoan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action should be renewed",
      renewedLoan.getString("action"), is("renewed"));

    assertThat("renewal count should be incremented",
      renewedLoan.getInteger("renewalCount"), is(1));

    loanHasLoanPolicyProperties(renewedLoan, loanPoliciesFixture.canCirculateRolling());

    assertThat("due date should be approximately 3 weeks after renewal date, based upon loan policy",
      renewedLoan.getString("dueDate"),
      withinSecondsAfter(Seconds.seconds(10), approximateRenewalDate.plusWeeks(3)));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canRenewRollingLoanFromCurrentDueDate() {

    configClient.create(ConfigurationExample.utcTimezoneConfiguration());

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource loan = loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43, DateTimeZone.UTC));

    final UUID loanId = loan.getId();

    LoanPolicyBuilder currentDueDateRollingPolicy = new LoanPolicyBuilder()
      .withName("Current Due Date Rolling Policy")
      .rolling(Period.months(2))
      .renewFromCurrentDueDate();

    final IndividualResource loanPolicy = loanPoliciesFixture
            .create(currentDueDateRollingPolicy);

    use(currentDueDateRollingPolicy);

    final JsonObject renewedLoan = renew(smallAngryPlanet, jessica).getJson();

    assertThat(renewedLoan.getString("id"), is(loanId.toString()));

    assertThat("user ID should match barcode",
      renewedLoan.getString("userId"), is(jessica.getId().toString()));

    assertThat("item ID should match barcode",
      renewedLoan.getString("itemId"), is(smallAngryPlanet.getId().toString()));

    assertThat("status should be open",
      renewedLoan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action should be renewed",
      renewedLoan.getString("action"), is("renewed"));

    assertThat("renewal count should be incremented",
      renewedLoan.getInteger("renewalCount"), is(1));

    loanHasLoanPolicyProperties(renewedLoan, loanPolicy);

    assertThat("due date should be 2 months after initial due date date",
      renewedLoan.getString("dueDate"),
      isEquivalentTo(new DateTime(2018, 7, 12, 11, 21, 43, DateTimeZone.UTC)));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canRenewUsingDueDateLimitedRollingLoanPolicy() {

    FixedDueDateSchedulesBuilder dueDateLimitSchedule = new FixedDueDateSchedulesBuilder()
      .withName("March Only Due Date Limit")
      .addSchedule(wholeMonth(2018, 3));

    final UUID dueDateLimitScheduleId = loanPoliciesFixture.createSchedule(
      dueDateLimitSchedule).getId();

    LoanPolicyBuilder dueDateLimitedPolicy = new LoanPolicyBuilder()
      .withName("Due Date Limited Rolling Policy")
      .rolling(Period.weeks(2))
      .limitedBySchedule(dueDateLimitScheduleId)
      .renewFromCurrentDueDate();

    final IndividualResource loanPolicy = loanPoliciesFixture.create(dueDateLimitedPolicy);

    use(dueDateLimitedPolicy);

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate = new DateTime(2018, 3, 7, 11, 43, 54, DateTimeZone.UTC);

    loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(UUID.randomUUID()));

    final IndividualResource response = renew(smallAngryPlanet, steve);

    final JsonObject loan = response.getJson();

    loanHasLoanPolicyProperties(loan, loanPolicy);

    assertThat("due date should be limited by schedule",
      loan.getString("dueDate"),
      isEquivalentTo(new DateTime(2018, 3, 31, 23, 59, 59, DateTimeZone.UTC)));
  }

  @Test
  public void canRenewRollingLoanUsingDifferentPeriod() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource loan = loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43, DateTimeZone.UTC));

    final UUID loanId = loan.getId();

    LoanPolicyBuilder currentDueDateRollingPolicy = new LoanPolicyBuilder()
      .withName("Current Due Date Different Period Rolling Policy")
      .rolling(Period.months(2))
      .renewFromCurrentDueDate()
      .renewWith(Period.months(1));

    final IndividualResource dueDateLimitedPolicy = loanPoliciesFixture
            .create(currentDueDateRollingPolicy);

    use(currentDueDateRollingPolicy);

    final JsonObject renewedLoan = renew(smallAngryPlanet, jessica).getJson();

    assertThat(renewedLoan.getString("id"), is(loanId.toString()));

    assertThat("user ID should match barcode",
      renewedLoan.getString("userId"), is(jessica.getId().toString()));

    assertThat("item ID should match barcode",
      renewedLoan.getString("itemId"), is(smallAngryPlanet.getId().toString()));

    assertThat("status should be open",
      renewedLoan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action should be renewed",
      renewedLoan.getString("action"), is("renewed"));

    assertThat("renewal count should be incremented",
      renewedLoan.getInteger("renewalCount"), is(1));

    loanHasLoanPolicyProperties(renewedLoan, dueDateLimitedPolicy);

    assertThat("due date should be 2 months after initial due date date",
      renewedLoan.getString("dueDate"),
      isEquivalentTo(new DateTime(2018, 6, 12, 11, 21, 43, DateTimeZone.UTC)));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canRenewUsingAlternateDueDateLimitedRollingLoanPolicy() {

    FixedDueDateSchedulesBuilder dueDateLimitSchedule = new FixedDueDateSchedulesBuilder()
      .withName("March Only Due Date Limit")
      .addSchedule(wholeMonth(2018, 3));

    final UUID dueDateLimitScheduleId = loanPoliciesFixture.createSchedule(
      dueDateLimitSchedule).getId();

    LoanPolicyBuilder dueDateLimitedPolicy = new LoanPolicyBuilder()
      .withName("Due Date Limited Rolling Policy")
      .rolling(Period.weeks(3))
      .renewFromCurrentDueDate()
      .renewWith(Period.days(8), dueDateLimitScheduleId);

    final IndividualResource loanPolicy = loanPoliciesFixture
            .create(dueDateLimitedPolicy);

    use(dueDateLimitedPolicy);

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate = new DateTime(2018, 3, 4, 11, 43, 54, DateTimeZone.UTC);

    loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(UUID.randomUUID()));

    final IndividualResource response = renew(smallAngryPlanet, steve);

    final JsonObject loan = response.getJson();

    loanHasLoanPolicyProperties(loan, loanPolicy);

    assertThat("due date should be limited by schedule",
      loan.getString("dueDate"),
      isEquivalentTo(new DateTime(2018, 3, 31, 23, 59, 59, DateTimeZone.UTC)));
  }

  @Test
  public void canRenewUsingLoanDueDateLimitSchedulesWhenDifferentPeriodAndNotAlternateLimits() {

    FixedDueDateSchedulesBuilder dueDateLimitSchedule = new FixedDueDateSchedulesBuilder()
      .withName("March Only Due Date Limit")
      .addSchedule(wholeMonth(2018, 3));

    final UUID dueDateLimitScheduleId = loanPoliciesFixture.createSchedule(
      dueDateLimitSchedule).getId();

    LoanPolicyBuilder dueDateLimitedPolicy = new LoanPolicyBuilder()
      .withName("Due Date Limited Rolling Policy")
      .rolling(Period.weeks(3))
      .limitedBySchedule(dueDateLimitScheduleId)
      .renewFromCurrentDueDate()
      .renewWith(Period.days(8));

    final IndividualResource loanPolicy = loanPoliciesFixture.create(dueDateLimitedPolicy);

    use(dueDateLimitedPolicy);

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate = new DateTime(2018, 3, 4, 11, 43, 54, DateTimeZone.UTC);

    loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(UUID.randomUUID()));

    final IndividualResource response = renew(smallAngryPlanet, steve);

    final JsonObject loan = response.getJson();

    loanHasLoanPolicyProperties(loan, loanPolicy);

    assertThat("due date should be limited by schedule",
      loan.getString("dueDate"),
      isEquivalentTo(new DateTime(2018, 3, 31, 23, 59, 59, DateTimeZone.UTC)));
  }

  @Test
  public void canCheckOutUsingFixedDueDateLoanPolicy() {

    //TODO: Need to be able to inject system date here
    final DateTime renewalDate = DateTime.now(DateTimeZone.UTC);
    //e.g. Clock.freeze(renewalDate)

    FixedDueDateSchedulesBuilder fixedDueDateSchedules = new FixedDueDateSchedulesBuilder()
      .withName("Kludgy Fixed Due Date Schedule")
      .addSchedule(wholeMonth(2018, 2))
      .addSchedule(forDay(renewalDate));

    final UUID fixedDueDateSchedulesId = loanPoliciesFixture.createSchedule(
      fixedDueDateSchedules).getId();

    LoanPolicyBuilder dueDateLimitedPolicy = new LoanPolicyBuilder()
      .withName("Fixed Due Date Policy")
      .fixed(fixedDueDateSchedulesId)
      .renewFromSystemDate();

    final IndividualResource fiexDueDatePolicy = loanPoliciesFixture.create(dueDateLimitedPolicy);

    use(dueDateLimitedPolicy);

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate = new DateTime(2018, 2, 10, 11, 23, 12, DateTimeZone.UTC);

    loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(UUID.randomUUID()));

    final IndividualResource response = renew(smallAngryPlanet, steve);

    final JsonObject loan = response.getJson();

    loanHasLoanPolicyProperties(loan, fiexDueDatePolicy);

    assertThat("renewal count should be incremented",
      loan.getInteger("renewalCount"), is(1));

    final DateTime endOfRenewalDate = renewalDate
      .withTimeAtStartOfDay()
      .withHourOfDay(23)
      .withMinuteOfHour(59)
      .withSecondOfMinute(59);

    assertThat("due date should be defined by schedule",
      loan.getString("dueDate"), isEquivalentTo(endOfRenewalDate));
  }

  @Test
  public void canRenewMultipleTimesUpToRenewalLimit() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    LoanPolicyBuilder limitedRenewalsPolicy = new LoanPolicyBuilder()
      .withName("Limited Renewals Policy")
      .rolling(Period.days(2))
      .renewFromCurrentDueDate()
      .limitedRenewals(3);

    final IndividualResource loanPolicy = loanPoliciesFixture.create(limitedRenewalsPolicy);

    use(limitedRenewalsPolicy);

    final IndividualResource loan = loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43, DateTimeZone.UTC));

    final UUID loanId = loan.getId();

    renew(smallAngryPlanet, jessica).getJson();

    renew(smallAngryPlanet, jessica);

    final JsonObject renewedLoan = renew(smallAngryPlanet, jessica).getJson();

    assertThat(renewedLoan.getString("id"), is(loanId.toString()));

    assertThat("status should be open",
      renewedLoan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action should be renewed",
      renewedLoan.getString("action"), is("renewed"));

    assertThat("renewal count should be incremented",
      renewedLoan.getInteger("renewalCount"), is(3));

    loanHasLoanPolicyProperties(renewedLoan, loanPolicy);

    assertThat("due date should be 8 days after initial loan date date",
      renewedLoan.getString("dueDate"),
      isEquivalentTo(new DateTime(2018, 4, 29, 11, 21, 43, DateTimeZone.UTC)));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canGetRenewedLoan() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    final UUID loanId = loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43, DateTimeZone.UTC))
      .getId();

    //TODO: Renewal based upon system date,
    // needs to be approximated, at least until we introduce a calendar and clock
    DateTime approximateRenewalDate = DateTime.now(DateTimeZone.UTC);

    final IndividualResource response = renew(smallAngryPlanet, jessica);

    final Response getResponse = loansFixture.getLoanByLocation(response);

    JsonObject renewedLoan = getResponse.getJson();

    assertThat(renewedLoan.getString("id"), is(loanId.toString()));

    assertThat("user ID should match barcode",
      renewedLoan.getString("userId"), is(jessica.getId().toString()));

    assertThat("item ID should match barcode",
      renewedLoan.getString("itemId"), is(smallAngryPlanet.getId().toString()));

    assertThat("status should be open",
      renewedLoan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action should be renewed",
      renewedLoan.getString("action"), is("renewed"));

    assertThat("renewal count should be incremented",
      renewedLoan.getInteger("renewalCount"), is(1));

    loanHasLoanPolicyProperties(renewedLoan, loanPoliciesFixture.canCirculateRolling());

    assertThat("due date should be approximately 3 weeks after renewal date, based upon loan policy",
      renewedLoan.getString("dueDate"),
      withinSecondsAfter(Seconds.seconds(10), approximateRenewalDate.plusWeeks(3)));
  }

  @Test
  public void cannotRenewWhenLoanPolicyDoesNotExist() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    final UUID unknownLoanPolicyId = UUID.randomUUID();

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43, DateTimeZone.UTC));

    useFallbackPolicies(
      unknownLoanPolicyId,
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId()
    );

    final Response response = loansFixture.attemptRenewal(500, smallAngryPlanet, jessica);

    assertThat(response.getBody(), is(String.format(
      "Loan policy %s could not be found, please check circulation rules", unknownLoanPolicyId)));
  }

  @Test
  public void canRenewLoanWithAnotherLoanPolicyName() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    final String policyName = "Limited Renewals Policy";
    LoanPolicyBuilder limitedRenewalsPolicy = new LoanPolicyBuilder().withName(policyName)
      .rolling(Period.days(2))
      .renewFromCurrentDueDate()
      .limitedRenewals(3);

    final IndividualResource loanPolicyResponse = loanPoliciesFixture.create(limitedRenewalsPolicy);

    IndividualResource loan = loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
        new DateTime(2019, 4, 21, 11, 21, 43, DateTimeZone.UTC));

    loanHasLoanPolicyProperties(loan.getJson(), loanPoliciesFixture.canCirculateRolling());

    use(limitedRenewalsPolicy);

    loan = renew(smallAngryPlanet, jessica);

    loanHasLoanPolicyProperties(loan.getJson(), loanPolicyResponse);
  }

  @Test
  public void cannotRenewWhenRenewalLimitReached() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    LoanPolicyBuilder limitedRenewalsPolicy = new LoanPolicyBuilder()
      .withName("Limited Renewals Policy")
      .rolling(Period.days(2))
      .renewFromCurrentDueDate()
      .limitedRenewals(3);

    UUID limitedRenewalsPolicyId = loanPoliciesFixture
      .create(limitedRenewalsPolicy).getId();

    use(limitedRenewalsPolicy);

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43, DateTimeZone.UTC));

    renew(smallAngryPlanet, jessica);
    renew(smallAngryPlanet, jessica);
    renew(smallAngryPlanet, jessica);

    final Response response = attemptRenewal(smallAngryPlanet, jessica);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("loan at maximum renewal number"),
      hasLoanPolicyIdParameter(limitedRenewalsPolicyId),
      hasLoanPolicyNameParameter("Limited Renewals Policy"))));
  }

  @Test
  public void multipleRenewalFailuresWhenLoanHasReachedMaximumNumberOfRenewalsAndOpenRecallRequest() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    LoanPolicyBuilder limitedRenewalsPolicy = new LoanPolicyBuilder()
      .withName("Limited Renewals Policy")
      .rolling(Period.days(2))
      .renewFromCurrentDueDate()
      .limitedRenewals(3);

    UUID limitedRenewalsPolicyId = loanPoliciesFixture
      .create(limitedRenewalsPolicy).getId();

    use(limitedRenewalsPolicy);

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43, DateTimeZone.UTC));

    renew(smallAngryPlanet, jessica);
    renew(smallAngryPlanet, jessica);
    renew(smallAngryPlanet, jessica);

    requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.charlotte()));

    final Response response = attemptRenewal(smallAngryPlanet, jessica);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("loan at maximum renewal number"),
      hasLoanPolicyIdParameter(limitedRenewalsPolicyId),
      hasLoanPolicyNameParameter("Limited Renewals Policy"))));

    assertThat(response.getJson(), hasErrorWith(
      hasMessage("items cannot be renewed when there is an active recall request")));
  }

  @Test
  public void multipleReasonsWhyCannotRenewWhenRenewalLimitReachedAndDueDateNotChanged() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    //TODO: Replace with better example when can fix system date
    FixedDueDateSchedulesBuilder yesterdayAndTodayOnlySchedules = new FixedDueDateSchedulesBuilder()
      .withName("Yesterday and Today Only Due Date Limit")
      .addSchedule(FixedDueDateSchedule.yesterdayOnly())
      .addSchedule(FixedDueDateSchedule.todayOnly());

    final UUID yesterdayAndTodayOnlySchedulesId
      = loanPoliciesFixture.createSchedule(yesterdayAndTodayOnlySchedules).getId();

    LoanPolicyBuilder limitedRenewalsPolicy = new LoanPolicyBuilder()
      .withName("Limited Renewals And Limited Due Date Policy")
      .fixed(yesterdayAndTodayOnlySchedulesId)
      .limitedRenewals(1);

    UUID limitedRenewalsPolicyId = loanPoliciesFixture
      .create(limitedRenewalsPolicy).getId();

    use(limitedRenewalsPolicy);

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      DateTime.now(DateTimeZone.UTC).minusDays(1)).getJson();

    renew(smallAngryPlanet, jessica);

    final Response response = attemptRenewal(smallAngryPlanet, jessica);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("loan at maximum renewal number"),
      hasLoanPolicyIdParameter(limitedRenewalsPolicyId),
      hasLoanPolicyNameParameter("Limited Renewals And Limited Due Date Policy"))));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("renewal would not change the due date"),
      hasLoanPolicyIdParameter(limitedRenewalsPolicyId),
      hasLoanPolicyNameParameter("Limited Renewals And Limited Due Date Policy"))));
  }

  @Test
  public void cannotRenewWhenNonRenewableRollingPolicy() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    LoanPolicyBuilder limitedRenewalsPolicy = new LoanPolicyBuilder()
      .withName("Non Renewable Policy")
      .rolling(Period.days(2))
      .notRenewable();

    UUID notRenewablePolicyId = loanPoliciesFixture
      .create(limitedRenewalsPolicy).getId();

    use(limitedRenewalsPolicy);

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43, DateTimeZone.UTC));

    final Response response = attemptRenewal(smallAngryPlanet, jessica);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("loan is not renewable"),
      hasLoanPolicyIdParameter(notRenewablePolicyId),
      hasLoanPolicyNameParameter("Non Renewable Policy"))));
  }

  @Test
  public void cannotRenewWhenNonRenewableFixedPolicy() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    //TODO: Replace with better example when can fix system date
    FixedDueDateSchedulesBuilder todayOnlySchedules = new FixedDueDateSchedulesBuilder()
      .withName("Today Only Due Date Limit")
      .addSchedule(FixedDueDateSchedule.todayOnly());

    final UUID todayOnlySchedulesId = loanPoliciesFixture.createSchedule(
      todayOnlySchedules).getId();

    LoanPolicyBuilder limitedRenewalsPolicy = new LoanPolicyBuilder()
      .withName("Non Renewable Policy")
      .fixed(todayOnlySchedulesId)
      .notRenewable();

    UUID notRenewablePolicyId = loanPoliciesFixture
      .create(limitedRenewalsPolicy).getId();

    use(limitedRenewalsPolicy);

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      DateTime.now(DateTimeZone.UTC));

    final Response response = attemptRenewal(smallAngryPlanet, jessica);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("loan is not renewable"),
      hasLoanPolicyIdParameter(notRenewablePolicyId),
      hasLoanPolicyNameParameter("Non Renewable Policy"))));
  }

  @Test
  public void cannotRenewWhenItemIsNotLoanable() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    LoanPolicyBuilder policyForCheckout = new LoanPolicyBuilder()
      .withName("Policy for checkout")
      .rolling(Period.days(2))
      .notRenewable();

    use(policyForCheckout);

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43, DateTimeZone.UTC));

    LoanPolicyBuilder nonLoanablePolicy = new LoanPolicyBuilder()
      .withName("Non loanable policy")
      .withLoanable(false);

    UUID notLoanablePolicyId = loanPoliciesFixture
      .create(nonLoanablePolicy).getId();

    use(nonLoanablePolicy);

    final Response response = attemptRenewal(smallAngryPlanet, jessica);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("item is not loanable"),
      hasLoanPolicyIdParameter(notLoanablePolicyId),
      hasLoanPolicyNameParameter("Non loanable policy"))));
  }

  @Test
  public void cannotRenewWhenItemIsDeclaredLost() {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    final JsonObject loanJson = loansFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.jessica())
      .getJson();

    loansFixture.declareItemLost(loanJson);

    final Response response = attemptRenewal(smallAngryPlanet, jessica);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("item is Declared lost"),
      hasUUIDParameter("itemId", smallAngryPlanet.getId()))));
  }

  @Test
  public void cannotRenewWhenItemIsClaimedReturned() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();
    final String comment = "testing";
    final DateTime dateTime = DateTime.now();

    final JsonObject loanJson = loansFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.jessica())
      .getJson();

    loansFixture.claimItemReturned(new ClaimItemReturnedRequestBuilder()
        .forLoan(loanJson.getString("id"))
        .withItemClaimedReturnedDate(dateTime)
        .withComment(comment));

    final Response response = attemptRenewal(smallAngryPlanet, jessica);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("item is Claimed returned"),
      hasUUIDParameter("itemId", smallAngryPlanet.getId()))));
  }

  @Test
  public void cannotRenewWhenLoaneeCannotBeFound() {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    loansFixture.checkOutByBarcode(smallAngryPlanet, steve);

    usersFixture.remove(steve);

    Response response = attemptRenewal(smallAngryPlanet, steve);

    //Occurs when current loanee is not found, so relates to loan rather than user in request
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("user is not found"),
      hasUUIDParameter("userId", steve.getId()))));
  }

  @Test
  public void cannotRenewWhenItemCannotBeFound() {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    loansFixture.checkOutByBarcode(smallAngryPlanet, steve);

    itemsClient.delete(smallAngryPlanet.getId());

    Response response = attemptRenewal(smallAngryPlanet, steve);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasItemNotFoundMessage(smallAngryPlanet),
      hasItemRelatedParameter(smallAngryPlanet))));
  }

  @Test
  public void cannotRenewLoanForDifferentUser() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    final IndividualResource jessica = usersFixture.jessica();

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43, DateTimeZone.UTC));

    final Response response = attemptRenewal(smallAngryPlanet, james);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Cannot renew item checked out to different user"),
      hasUserRelatedParameter(james))));
  }

  @Test
  public void testMoveToEndOfPreviousOpenDay() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource jessica = usersFixture.jessica();
    UUID checkoutServicePointId = UUID.fromString(CASE_FRI_SAT_MON_SERVICE_POINT_ID);
    int loanPeriodDays = 5;
    int renewPeriodDays = 3;

    DateTime loanDate =
      new DateTime(2019, DateTimeConstants.JANUARY, 25, 10, 0, DateTimeZone.UTC);

    LoanPolicyBuilder loanPolicy = new LoanPolicyBuilder()
      .withName("Loan policy")
      .rolling(Period.days(loanPeriodDays))
      .renewWith(Period.days(renewPeriodDays))
      .withClosedLibraryDueDateManagement(
        DueDateManagement.KEEP_THE_CURRENT_DUE_DATE.getValue());

    use(loanPolicy);

    loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(jessica)
        .at(checkoutServicePointId)
        .on(loanDate));

    LoanPolicyBuilder renewPolicy = loanPolicy
      .withName("For renew")
      .withClosedLibraryDueDateManagement(
        DueDateManagement.MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY.getValue());

    use(renewPolicy);

    JsonObject renewedLoan = renew(smallAngryPlanet, jessica).getJson();

    DateTime expectedDate =
      CASE_FRI_SAT_MON_SERVICE_POINT_PREV_DAY
        .toDateTime(END_OF_A_DAY, DateTimeZone.UTC);
    assertThat("due date should be " + expectedDate,
      renewedLoan.getString("dueDate"), isEquivalentTo(expectedDate));
  }

  @Test
  public void testMoveToEndOfNextOpenDay() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource jessica = usersFixture.jessica();
    UUID checkoutServicePointId = UUID.fromString(CASE_FRI_SAT_MON_SERVICE_POINT_ID);
    int loanPeriodDays = 5;
    int renewPeriodDays = 3;

    DateTime loanDate =
      new DateTime(2019, DateTimeConstants.JANUARY, 25, 10, 0, DateTimeZone.UTC);

    LoanPolicyBuilder loanPolicy = new LoanPolicyBuilder()
      .withName("Loan policy")
      .rolling(Period.days(loanPeriodDays))
      .renewWith(Period.days(renewPeriodDays))
      .withClosedLibraryDueDateManagement(
        DueDateManagement.KEEP_THE_CURRENT_DUE_DATE.getValue());

    use(loanPolicy);

    loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(jessica)
        .at(checkoutServicePointId)
        .on(loanDate));

    LoanPolicyBuilder renewPolicy = loanPolicy
      .withName("For renew")
      .withClosedLibraryDueDateManagement(
        DueDateManagement.MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY.getValue());

    use(renewPolicy);

    JsonObject renewedLoan = renew(smallAngryPlanet, jessica).getJson();

    DateTime expectedDate =
      CASE_FRI_SAT_MON_SERVICE_POINT_NEXT_DAY
        .toDateTime(END_OF_A_DAY, DateTimeZone.UTC);
    assertThat("due date should be " + expectedDate,
      renewedLoan.getString("dueDate"), isEquivalentTo(expectedDate));
  }

  @Test
  public void testMoveToEndOfNextOpenServicePointHours() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource jessica = usersFixture.jessica();
    UUID checkoutServicePointId = UUID.fromString(CASE_FRI_SAT_MON_SERVICE_POINT_ID);
    int loanPeriodHours = 8;

    DateTime loanDate =
      new DateTime(2019, DateTimeConstants.FEBRUARY, 1, 10, 0, DateTimeZone.UTC);

    LoanPolicyBuilder loanPolicy = new LoanPolicyBuilder()
      .withName("Loan policy")
      .rolling(Period.hours(loanPeriodHours))
      .withClosedLibraryDueDateManagement(
        DueDateManagement.KEEP_THE_CURRENT_DUE_DATE.getValue());

    use(loanPolicy);

    loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(jessica)
        .at(checkoutServicePointId)
        .on(loanDate));

    LoanPolicyBuilder renewPolicy = loanPolicy
      .withName("For renew")
      .withClosedLibraryDueDateManagement(
        DueDateManagement.MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS.getValue());

    use(renewPolicy);

    JsonObject renewedLoan = renew(smallAngryPlanet, jessica).getJson();

    DateTime expectedDate =
      CASE_FRI_SAT_MON_SERVICE_POINT_NEXT_DAY
        .toDateTime(START_TIME_FIRST_PERIOD, DateTimeZone.UTC);
    assertThat("due date should be " + expectedDate,
      renewedLoan.getString("dueDate"), isEquivalentTo(expectedDate));
  }

  @Test
  public void testMoveToEndOfCurrentServicePointHours() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource jessica = usersFixture.jessica();
    UUID checkoutServicePointId = UUID.fromString(CASE_WED_THU_FRI_SERVICE_POINT_ID);
    int loanPeriodHours = 12;
    int renewPeriodHours = 5;

    DateTime loanDate =
      WEDNESDAY_DATE.toDateTime(START_TIME_SECOND_PERIOD, DateTimeZone.UTC);

    LoanPolicyBuilder loanPolicy = new LoanPolicyBuilder()
      .withName("Loan policy")
      .rolling(Period.hours(loanPeriodHours))
      .renewWith(Period.hours(renewPeriodHours))
      .withClosedLibraryDueDateManagement(
        DueDateManagement.KEEP_THE_CURRENT_DUE_DATE_TIME.getValue());

    use(loanPolicy);

    loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(jessica)
        .at(checkoutServicePointId)
        .on(loanDate));

    LoanPolicyBuilder renewPolicy = loanPolicy
      .withName("For renew")
      .withClosedLibraryDueDateManagement(
        DueDateManagement.MOVE_TO_END_OF_CURRENT_SERVICE_POINT_HOURS.getValue());

    use(renewPolicy);

    DateTimeUtils.setCurrentMillisFixed(loanDate.plusHours(1).getMillis());
    JsonObject renewedLoan = renew(smallAngryPlanet, jessica).getJson();
    DateTimeUtils.setCurrentMillisSystem();

    DateTime expectedDate =
      WEDNESDAY_DATE
        .toDateTime(END_TIME_SECOND_PERIOD, DateTimeZone.UTC);
    assertThat("due date should be " + expectedDate,
      renewedLoan.getString("dueDate"), isEquivalentTo(expectedDate));
  }

  @Test
  public void testRespectSelectedTimezoneForDueDateCalculations() {

    String expectedTimeZone = "America/New_York";

    Response response = configClient.create(ConfigurationExample.newYorkTimezoneConfiguration())
      .getResponse();
    assertThat(response.getBody(), containsString(expectedTimeZone));

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource jessica = usersFixture.jessica();
    UUID checkoutServicePointId = UUID.fromString(CASE_FRI_SAT_MON_SERVICE_POINT_ID);
    int loanPeriodHours = 8;

    DateTime loanDate =
      new DateTime(2019, DateTimeConstants.FEBRUARY, 1, 10, 0, DateTimeZone.forID(expectedTimeZone));

    LoanPolicyBuilder loanPolicy = new LoanPolicyBuilder()
      .withName("Loan policy")
      .rolling(Period.hours(loanPeriodHours))
      .withClosedLibraryDueDateManagement(
        DueDateManagement.KEEP_THE_CURRENT_DUE_DATE.getValue());

    use(loanPolicy);

    loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(jessica)
        .at(checkoutServicePointId)
        .on(loanDate));

    LoanPolicyBuilder renewPolicy = loanPolicy
      .withName("For renew")
      .withClosedLibraryDueDateManagement(
        DueDateManagement.MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS.getValue());

    use(renewPolicy);

    JsonObject renewedLoan = renew(smallAngryPlanet, jessica).getJson();

    DateTime expectedDate =
      CASE_FRI_SAT_MON_SERVICE_POINT_NEXT_DAY
        .toDateTime(START_TIME_FIRST_PERIOD, DateTimeZone.forID(expectedTimeZone));

    assertThat(response.getBody(), containsString(expectedTimeZone));

    assertThat("due date should be " + expectedDate,
      renewedLoan.getString("dueDate"), isEquivalentTo(expectedDate));
  }

  @Test
  public void canRenewWhenCurrentDueDateFallsWithinLimitingDueDateSchedule() throws
    InterruptedException,
    ExecutionException,
    TimeoutException {

    FixedDueDateSchedulesBuilder fixedDueDateSchedules = new FixedDueDateSchedulesBuilder()
      .withName("Fixed Due Date Schedule")
      .addSchedule(wholeMonth(2019, DateTimeConstants.MARCH))
      .addSchedule(wholeMonth(2019, DateTimeConstants.MAY));

    DateTime expectedDueDate =
      new DateTime(2019, DateTimeConstants.MAY, 31, 23, 59, 59)
        .withZoneRetainFields(DateTimeZone.UTC);

    final UUID fixedDueDateSchedulesId = loanPoliciesFixture.createSchedule(
      fixedDueDateSchedules).getId();

    LoanPolicyBuilder currentDueDateRollingPolicy = new LoanPolicyBuilder()
      .withName("Current Due Date Rolling Policy")
      .rolling(Period.months(1))
      .limitedBySchedule(fixedDueDateSchedulesId)
      .renewFromCurrentDueDate();

    UUID dueDateLimitedPolicyId = loanPoliciesFixture.create(currentDueDateRollingPolicy)
      .getId();

    checkRenewalAttempt(expectedDueDate, dueDateLimitedPolicyId);
  }

  @Test
  public void canRenewWhenSystemDateFallsWithinLimitingDueDateSchedule() throws
    InterruptedException,
    ExecutionException,
    TimeoutException {

    FixedDueDateSchedulesBuilder fixedDueDateSchedules = new FixedDueDateSchedulesBuilder()
      .withName("Fixed Due Date Schedule")
      .addSchedule(wholeMonth(2019, DateTimeConstants.MARCH))
      .addSchedule(todayOnly());

    DateTime expectedDueDate = DateTime.now(DateTimeZone.UTC)
      .withTimeAtStartOfDay()
      .withHourOfDay(23)
      .withMinuteOfHour(59)
      .withSecondOfMinute(59);

    final UUID fixedDueDateSchedulesId = loanPoliciesFixture.createSchedule(
      fixedDueDateSchedules).getId();

    LoanPolicyBuilder currentDueDateRollingPolicy = new LoanPolicyBuilder()
      .withName("System Date Rolling Policy")
      .rolling(Period.months(1))
      .limitedBySchedule(fixedDueDateSchedulesId)
      .renewFromSystemDate();

    UUID dueDateLimitedPolicyId = loanPoliciesFixture.create(currentDueDateRollingPolicy)
      .getId();

    checkRenewalAttempt(expectedDueDate, dueDateLimitedPolicyId);
  }

  @Test
  public void cannotRenewWhenCurrentDueDateDoesNotFallWithinLimitingDueDateSchedule() {

    DateTime futureDateTime = DateTime.now(DateTimeZone.UTC).plusMonths(1);

    FixedDueDateSchedulesBuilder fixedDueDateSchedules = new FixedDueDateSchedulesBuilder()
      .withName("Fixed Due Date Schedule in the Future")
      .addSchedule(wholeMonth(futureDateTime.getYear(), futureDateTime.getMonthOfYear()));

    final UUID fixedDueDateSchedulesId = loanPoliciesFixture.createSchedule(
      fixedDueDateSchedules).getId();

    LoanPolicyBuilder currentDueDateRollingPolicy = new LoanPolicyBuilder()
      .withName("System Date Rolling Policy")
      .rolling(Period.months(1))
      .limitedBySchedule(fixedDueDateSchedulesId)
      .renewFromSystemDate();


    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    DateTime loanDueDate =
      new DateTime(2019, DateTimeConstants.APRIL, 21, 11, 21, 43);

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica, loanDueDate);

    use(currentDueDateRollingPolicy);

    loansFixture.attemptRenewal(422, smallAngryPlanet, jessica);
  }

  @Test
  public void  canRenewFromCurrentDueDateWhenDueDateFallsWithinRangeOfAlternateDueDateLimit() {

    FixedDueDateSchedulesBuilder dueDateLimitSchedule = new FixedDueDateSchedulesBuilder()
      .withName("Alternate Due Date Limit")
      .addSchedule(wholeMonth(2019, DateTimeConstants.MARCH))
      .addSchedule(wholeMonth(2019, DateTimeConstants.MAY));

    DateTime expectedDueDate =
      new DateTime(2019, DateTimeConstants.MAY, 31, 23, 59, 59)
        .withZoneRetainFields(DateTimeZone.UTC);

    final UUID dueDateLimitScheduleId = loanPoliciesFixture.createSchedule(
      dueDateLimitSchedule).getId();

    LoanPolicyBuilder dueDateLimitedPolicy = new LoanPolicyBuilder()
      .withName("Due Date Limited Rolling Policy")
      .rolling(Period.months(1))
      .renewFromCurrentDueDate()
      .renewWith(Period.months(1), dueDateLimitScheduleId);

    final IndividualResource loanPolicy = loanPoliciesFixture
      .create(dueDateLimitedPolicy);
    UUID dueDateLimitedPolicyId = loanPolicy.getId();

    checkRenewalAttempt(expectedDueDate, dueDateLimitedPolicyId);
  }

  @Test
  public void canRenewWhenSystemDateFallsWithinAlternateScheduleAndDueDateDoesNot() {

    FixedDueDateSchedulesBuilder dueDateLimitSchedule = new FixedDueDateSchedulesBuilder()
      .withName("Alternate Due Date Limit")
      .addSchedule(wholeMonth(2019, DateTimeConstants.MARCH))
      .addSchedule(todayOnly());

    DateTime expectedDueDate = DateTime.now(DateTimeZone.UTC)
      .withTimeAtStartOfDay()
      .withHourOfDay(23)
      .withMinuteOfHour(59)
      .withSecondOfMinute(59);

    final UUID dueDateLimitScheduleId = loanPoliciesFixture.createSchedule(
      dueDateLimitSchedule).getId();

    LoanPolicyBuilder dueDateLimitedPolicy = new LoanPolicyBuilder()
      .withName("Due Date Limited Rolling Policy")
      .rolling(Period.months(1))
      .renewFromSystemDate()
      .renewWith(Period.months(1), dueDateLimitScheduleId);

    final IndividualResource loanPolicy = loanPoliciesFixture
      .create(dueDateLimitedPolicy);
    UUID dueDateLimitedPolicyId = loanPolicy.getId();

    checkRenewalAttempt(expectedDueDate, dueDateLimitedPolicyId);
  }

  @Test
  public void renewalNoticeIsSentWhenPolicyDefinesRenewalNoticeConfiguration() {

    UUID renewalTemplateId = UUID.randomUUID();
    JsonObject renewalNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(renewalTemplateId)
      .withRenewalEvent()
      .create();
    JsonObject checkInNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(UUID.randomUUID())
      .withCheckInEvent()
      .create();

    NoticePolicyBuilder policyWithRenewalNotice = new NoticePolicyBuilder()
      .withName("Policy with renewal notice")
      .withLoanNotices(Arrays.asList(renewalNoticeConfiguration, checkInNoticeConfiguration));

    LoanPolicyBuilder limitedRenewalsLoanPolicy = new LoanPolicyBuilder()
      .withName("Limited renewals loan policy")
      .rolling(Period.months(1))
      .limitedRenewals(3);

    use(limitedRenewalsLoanPolicy, policyWithRenewalNotice);

    ItemBuilder itemBuilder = ItemExamples.basedUponSmallAngryPlanet(
      materialTypesFixture.book().getId(),
      loanTypesFixture.canCirculate().getId(),
      StringUtils.EMPTY,
      "ItemPrefix",
      "ItemSuffix",
      "");

    InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(itemBuilder, itemsFixture.thirdFloorHoldings());
    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate =
      new DateTime(2018, 3, 18, 11, 43, 54, DateTimeZone.UTC);

    loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(UUID.randomUUID()));

    IndividualResource loanAfterRenewal = loansFixture.renewLoan(smallAngryPlanet, steve);

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(patronNoticesClient::getAll, Matchers.hasSize(1));
    List<JsonObject> sentNotices = patronNoticesClient.getAll();

    int expectedRenewalLimit = 3;
    int expectedRenewalsRemaining = 2;
    Map<String, Matcher<String>> noticeContextMatchers = new HashMap<>();
    noticeContextMatchers.putAll(TemplateContextMatchers.getUserContextMatchers(steve));
    noticeContextMatchers.putAll(TemplateContextMatchers.getItemContextMatchers(smallAngryPlanet, true));
    noticeContextMatchers.putAll(TemplateContextMatchers.getLoanContextMatchers(loanAfterRenewal));
    noticeContextMatchers.putAll(TemplateContextMatchers.getLoanPolicyContextMatchers(
      expectedRenewalLimit, expectedRenewalsRemaining));
    MatcherAssert.assertThat(sentNotices,
      hasItems(
        hasEmailNoticeProperties(steve.getId(), renewalTemplateId, noticeContextMatchers)));
  }

  @Test
  public void overdueFineShouldBeChargedWhenItemIsOverdue() throws InterruptedException {
    IndividualResource loanPolicy = loanPoliciesFixture.create(
      new LoanPolicyBuilder().rolling(Period.from(10, "Days")));

    useFallbackPolicies(loanPolicy.getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandardDoNotCountClosed().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    final IndividualResource james = usersFixture.james();
    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      item -> item.withPrimaryServicePoint(checkInServicePointId));
    final IndividualResource nod = itemsFixture.basedUponNod(item ->
      item.withPermanentLocation(homeLocation.getId()));

    final IndividualResource loan = loansFixture.checkOutByBarcode(nod, james,
      new DateTime(2020, 1, 1, 12, 0, 0, DateTimeZone.UTC));

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
    );

    loansFixture.renewLoan(nod, james);

    TimeUnit.SECONDS.sleep(1);
    List<JsonObject> createdAccounts = accountsClient.getAll();

    org.hamcrest.junit.MatcherAssert.assertThat("Fee/fine record should be created", createdAccounts, hasSize(1));

    JsonObject account = createdAccounts.get(0);
    assertThat("owner ID is included",
      account.getString("ownerId"), is(ownerId.toString()));
    assertThat("fee/fine ID is included",
      account.getString("feeFineId"), is(feeFineId.toString()));
    assertThat("amount is correct", account.getDouble("amount"), is(5.0));
    assertThat("remaining is the same as amount",
      account.getDouble("remaining"), is(5.0));
    assertThat("correct fee/fine type is included",
      account.getString("feeFineType"), is("Overdue fine"));
    assertThat("fee/fine owner is included",
      account.getString("feeFineOwner"), is("fee-fine-owner"));
    assertThat("item's title is included",
      account.getString("title"), is(loan.getJson().getJsonObject("item").getString("title")));
    assertThat("item's barcode is included",
      account.getString("barcode"), is(nod.getJson().getString("barcode")));
    assertThat("call number from the item is included",
      account.getString("callNumber"),
      is(nod.getJson().getJsonObject("effectiveCallNumberComponents").getString("callNumber")));
    assertThat("effective location ID is included",
      account.getString("location"), UUIDMatcher.is(servicePointsFixture.cd1().getId()));
    assertThat("item's material type is included",
      account.getString("materialTypeId"),
      is(nod.getJson().getString(ItemProperties.MATERIAL_TYPE_ID)));
    assertThat("loan ID is included", account.getString("loanId"), UUIDMatcher.is(loan.getId()));
    assertThat("user ID is included",
      account.getString("userId"), is(loan.getJson().getString("userId")));
    assertThat("item ID is included", account.getString("itemId"), UUIDMatcher.is(nod.getId()));
  }

  @Test
  public void overdueFineShouldNotBeChargedWhenShouldBeForgiven() throws InterruptedException {
    IndividualResource loanPolicy = loanPoliciesFixture.create(
      new LoanPolicyBuilder().rolling(Period.from(10, "Days")));

    useFallbackPolicies(loanPolicy.getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(), // forgiveOverdueFine = true
      lostItemFeePoliciesFixture.facultyStandard().getId());

    final IndividualResource james = usersFixture.james();
    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      item -> item.withPrimaryServicePoint(checkInServicePointId));
    final IndividualResource nod = itemsFixture.basedUponNod(item ->
      item.withPermanentLocation(homeLocation.getId()));

    loansFixture.checkOutByBarcode(nod, james,
      new DateTime(2020, 1, 1, 12, 0, 0, DateTimeZone.UTC));

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
    );

    loansFixture.renewLoan(nod, james);

    TimeUnit.SECONDS.sleep(1);
    List<JsonObject> createdAccounts = accountsClient.getAll();

    org.hamcrest.junit.MatcherAssert.assertThat("Fee/fine record shouldn't have been created",
      createdAccounts, hasSize(0));
  }

  private void checkRenewalAttempt(DateTime expectedDueDate, UUID dueDateLimitedPolicyId) {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    DateTime loanDate =
      new DateTime(2019, DateTimeConstants.APRIL, 21, 11, 21, 43);

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica, loanDate);

    useFallbackPolicies(
      dueDateLimitedPolicyId,
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId()
    );

    Response response = loansFixture.attemptRenewal(200, smallAngryPlanet, jessica);
    assertThat(response.getJson().getString("action"), is("renewed"));

    assertThat("due date should be the end date of the last fixed due date schedule",
      response.getJson().getString("dueDate"),
      isEquivalentTo(expectedDueDate));
  }

  private Matcher<ValidationError> hasLoanPolicyIdParameter(UUID loanPolicyId) {
    return hasUUIDParameter("loanPolicyId", loanPolicyId);
  }

  private Matcher<ValidationError> hasLoanPolicyNameParameter(String policyName) {
    return hasParameter("loanPolicyName", policyName);
  }
}
