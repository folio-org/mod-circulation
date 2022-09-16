package org.folio.circulation.domain.policy;

import static java.time.ZoneOffset.UTC;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.resources.handlers.error.CirculationErrorHandler;
import org.folio.circulation.resources.handlers.error.OverridingErrorHandler;
import org.folio.circulation.resources.renewal.RenewByBarcodeResource;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import api.support.builders.FixedDueDateSchedule;
import api.support.builders.FixedDueDateSchedulesBuilder;
import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;
import io.vertx.core.json.JsonObject;

class RollingLoanPolicyRenewalDueDateCalculationTests {

  private static final String EXPECTED_REASON_DATE_FALLS_OTSIDE_DATE_RANGES =
    "renewal date falls outside of date ranges in the loan policy";

  private static final String EXPECTED_REASON_OPEN_RECALL_REQUEST =
    "items cannot be renewed when there is an active recall request";
  private static final String LOAN_PERIOD_IN_THE_LOAN_POLICY_IS_NOT_RECOGNISED =
    "the loan period in the loan policy is not recognised";
  private static final String RENEWAL_WOULD_NOT_CHANGE_THE_DUE_DATE =
    "renewal would not change the due date";
  private static final UUID ITEM_ID = UUID.randomUUID();

  @ParameterizedTest
  @ValueSource(strings = {
    "1",
    "8",
    "12",
    "15"
  })
  void shouldApplyMonthlyRollingPolicy(int duration) {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(Period.months(duration))
      .renewFromSystemDate()
      .unlimitedRenewals()
      .create());

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 3, 14, 11, 14, 54, 0, UTC);

    Loan loan = loanFor(loanDate, loanDate.plusMonths(duration), loanPolicy);

    ZonedDateTime systemDate = ZonedDateTime.of(2018, 6, 1, 21, 32, 11, 0, UTC);

    Result<Loan> result = renew(loan, systemDate,
      new RequestQueue(Collections.emptyList()), new OverridingErrorHandler(null));

    assertThat(result.value().getDueDate(), is(systemDate.plusMonths(duration)));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "1",
    "2",
    "3",
    "4",
    "5"
  })
  void shouldApplyWeeklyRollingPolicy(int duration) {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(Period.weeks(duration))
      .renewFromSystemDate()
      .unlimitedRenewals()
      .create());

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 3, 14, 11, 14, 54, 0, UTC);

    Loan loan = loanFor(loanDate, loanDate.plusWeeks(duration), loanPolicy);

    ZonedDateTime systemDate = ZonedDateTime.of(2018, 6, 1, 21, 32, 11, 0, UTC);

    Result<Loan> result = renew(loan, systemDate,
      new RequestQueue(Collections.emptyList()), new OverridingErrorHandler(null));

    assertThat(result.value().getDueDate(), is(systemDate.plusWeeks(duration)));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "1",
    "7",
    "14",
    "12",
    "30",
    "100"
  })
  void shouldApplyDailyRollingPolicy(int duration) {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(Period.days(duration))
      .renewFromSystemDate()
      .unlimitedRenewals()
      .create());

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 3, 14, 11, 14, 54, 0, UTC);

    Loan loan = loanFor(loanDate, loanDate.plusDays(duration), loanPolicy);

    ZonedDateTime systemDate = ZonedDateTime.of(2018, 6, 1, 21, 32, 11, 0, UTC);

    Result<Loan> result = renew(loan, systemDate,
      new RequestQueue(Collections.emptyList()), new OverridingErrorHandler(null));

    assertThat(result.value().getDueDate(), is(systemDate.plusDays(duration)));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "2",
    "5",
    "30",
    "45",
    "60",
    "24"
  })
  void shouldApplyHourlyRollingPolicy(int duration) {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(Period.hours(duration))
      .renewFromSystemDate()
      .unlimitedRenewals()
      .create());

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 3, 14, 11, 14, 54, 0, UTC);

    Loan loan = loanFor(loanDate, loanDate.plusHours(duration), loanPolicy);

    ZonedDateTime systemDate = ZonedDateTime.of(2018, 6, 1, 21, 32, 11, 0, UTC);

    Result<Loan> result = renew(loan, systemDate,
      new RequestQueue(Collections.emptyList()), new OverridingErrorHandler(null));

    assertThat(result.value().getDueDate(), is(systemDate.plusHours(duration)));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "1",
    "5",
    "30",
    "60",
    "200"
  })
  void shouldApplyMinuteIntervalRollingPolicy(int duration) {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(Period.minutes(duration))
      .renewFromSystemDate()
      .unlimitedRenewals()
      .create());

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 3, 14, 11, 14, 54, 0, UTC);

    Loan loan = loanFor(loanDate, loanDate, loanPolicy);

    ZonedDateTime systemDate = ZonedDateTime.of(2018, 6, 1, 21, 32, 11, 0, UTC);

    Result<Loan> result = renew(loan, systemDate,
      new RequestQueue(Collections.emptyList()), new OverridingErrorHandler(null));

    assertThat(result.value().getDueDate(), is(systemDate.plusMinutes(duration)));
  }

  @Test
  void shouldFailForUnrecognisedInterval() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(Period.from(5, "Unknown"))
      .withName("Invalid Loan Policy")
      .create());

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 3, 14, 11, 14, 54, 0, UTC);

    Loan loan = loanFor(loanDate, loanDate, loanPolicy);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, getZonedDateTime(), new RequestQueue(Collections.emptyList()), errorHandler);

    assertTrue(matchErrorReason(errorHandler,
      "the interval \"Unknown\" in the loan policy is not recognised"));
  }

  @Test
  void shouldFailWhenNoPeriodProvided() {
    final JsonObject representation = new LoanPolicyBuilder()
      .rolling(Period.from(5, "Unknown"))
      .withName("Invalid Loan Policy")
      .create();

    representation.getJsonObject("loansPolicy").remove("period");

    LoanPolicy loanPolicy = LoanPolicy.from(representation);

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 3, 14, 11, 14, 54, 0, UTC);

    Loan loan = loanFor(loanDate, loanDate, loanPolicy);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, getZonedDateTime(), new RequestQueue(Collections.emptyList()), errorHandler);

    assertTrue(matchErrorReason(errorHandler, LOAN_PERIOD_IN_THE_LOAN_POLICY_IS_NOT_RECOGNISED));
  }

  @Test
  void shouldFailWhenNoPeriodDurationProvided() {
    final JsonObject representation = new LoanPolicyBuilder()
      .rolling(Period.from(5, "Weeks"))
      .withName("Invalid Loan Policy")
      .create();

    representation.getJsonObject("loansPolicy").getJsonObject("period").remove("duration");

    LoanPolicy loanPolicy = LoanPolicy.from(representation);

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 3, 14, 11, 14, 54, 0, UTC);

    Loan loan = loanFor(loanDate, loanDate, loanPolicy);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, getZonedDateTime(), new RequestQueue(Collections.emptyList()), errorHandler);

    assertTrue(matchErrorReason(errorHandler, LOAN_PERIOD_IN_THE_LOAN_POLICY_IS_NOT_RECOGNISED));
  }

  @Test
  void shouldFailWhenNoPeriodIntervalProvided() {
    final JsonObject representation = new LoanPolicyBuilder()
      .rolling(Period.from(5, "Weeks"))
      .withName("Invalid Loan Policy")
      .create();

    representation.getJsonObject("loansPolicy").getJsonObject("period").remove("intervalId");

    LoanPolicy loanPolicy = LoanPolicy.from(representation);

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 3, 14, 11, 14, 54, 0, UTC);

    Loan loan = loanFor(loanDate, loanDate, loanPolicy);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, getZonedDateTime(), new RequestQueue(Collections.emptyList()), errorHandler);

    assertTrue(matchErrorReason(errorHandler, LOAN_PERIOD_IN_THE_LOAN_POLICY_IS_NOT_RECOGNISED));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "0",
    "-1",
  })
  void shouldFailWhenDurationIsInvalid(int duration) {
    final JsonObject representation = new LoanPolicyBuilder()
      .rolling(Period.minutes(duration))
      .withName("Invalid Loan Policy")
      .create();

    LoanPolicy loanPolicy = LoanPolicy.from(representation);

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 3, 14, 11, 14, 54, 0, UTC);

    Loan loan = loanFor(loanDate, loanDate, loanPolicy);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, getZonedDateTime(), new RequestQueue(Collections.emptyList()), errorHandler);

    assertTrue(matchErrorReason(errorHandler,
      String.format("the duration \"%s\" in the loan policy is invalid", duration)));
  }

  @Test
  void shouldTruncateDueDateWhenWithinDueDateLimitSchedule() {
    //TODO: Slight hack to use the same builder, the schedule is fed in later
    //TODO: Introduce builder for individual schedules
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(Period.days(15))
      .limitedBySchedule(UUID.randomUUID())
      .renewFromCurrentDueDate()
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3,
          ZonedDateTime.of(2018, 4, 10, 23, 59, 59, 0, UTC)))
        .create());

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 3, 14, 11, 14, 54, 0, UTC);

    Loan loan = loanFor(loanDate, loanDate.plusDays(15), loanPolicy);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    Result<Loan> result = renew(loan, getZonedDateTime(), new RequestQueue(Collections.emptyList()),
      errorHandler);

    assertThat(result.value().getDueDate(),
      is(ZonedDateTime.of(2018, 4, 10, 23, 59, 59, 0, UTC)));
  }

  @Test
  void shouldNotTruncateDueDateWhenWithinDueDateLimitScheduleButInitialDateIsSooner() {
    //TODO: Slight hack to use the same builder, the schedule is fed in later
    //TODO: Introduce builder for individual schedules
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(Period.days(6))
      .limitedBySchedule(UUID.randomUUID())
      .renewFromCurrentDueDate()
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3))
        .create());

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 3, 11, 16, 21, 43, 0, UTC);

    Loan loan = loanFor(loanDate, loanDate.plusDays(6), loanPolicy);

    Result<Loan> result = renew(loan, getZonedDateTime(),
      new RequestQueue(Collections.emptyList()), new OverridingErrorHandler(null));

    assertThat(result.value().getDueDate(),
      is(ZonedDateTime.of(2018, 3, 23, 16, 21, 43, 0, UTC)));
  }

  @Test
  void shouldFailWhenNotWithinOneOfProvidedDueDateLimitSchedules() {
    //TODO: Slight hack to use the same builder, the schedule is fed in later
    //TODO: Introduce builder for individual schedules
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .withName("One Month")
      .rolling(Period.months(1))
      .renewFromCurrentDueDate()
      .limitedBySchedule(UUID.randomUUID())
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 5))
        .create());

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 4, 3, 9, 25, 43, 0, UTC);

    Loan loan = loanFor(loanDate, loanDate, loanPolicy);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, getZonedDateTime(), new RequestQueue(Collections.emptyList()), errorHandler);

    assertEquals(1, errorHandler.getErrors().size());
    assertTrue(matchErrorReason(errorHandler, EXPECTED_REASON_DATE_FALLS_OTSIDE_DATE_RANGES));
  }

  @Test
  void shouldFailWhenNoDueDateLimitSchedules() {
    //TODO: Slight hack to use the same builder, the schedule is fed in later
    //TODO: Introduce builder for individual schedules
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(Period.months(1))
      .withName("One Month")
      .renewFromCurrentDueDate()
      .limitedBySchedule(UUID.randomUUID())
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder().create());

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 4, 3, 9, 25, 43, 0, UTC);

    Loan loan = loanFor(loanDate, loanDate, loanPolicy);

    RequestQueue requestQueue = new RequestQueue(Collections.emptyList());
    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, getZonedDateTime(), requestQueue, errorHandler);

    assertTrue(matchErrorReason(errorHandler, EXPECTED_REASON_DATE_FALLS_OTSIDE_DATE_RANGES));
  }

  @Test
  void multipleRenewalFailuresWhenDateFallsOutsideDateRangesAndItemHasOpenRecallRequest() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(Period.months(1))
      .withName("One Month")
      .renewFromCurrentDueDate()
      .limitedBySchedule(UUID.randomUUID())
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder().create());

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 3, 2, 9, 10, 45, 0, UTC);

    Loan loan = loanFor(loanDate, loanDate, loanPolicy);

    String requestId = UUID.randomUUID().toString();
    RequestQueue requestQueue = creteRequestQueue(requestId, RequestType.RECALL);
    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, getZonedDateTime(), requestQueue, errorHandler);

    assertEquals(2, errorHandler.getErrors().size());
    assertTrue(matchErrorReason(errorHandler, EXPECTED_REASON_DATE_FALLS_OTSIDE_DATE_RANGES));
    assertTrue(matchErrorReason(errorHandler, EXPECTED_REASON_OPEN_RECALL_REQUEST));
  }

  @Test
  void shouldFailWhenRenewalWouldNotChangeDueDate() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(Period.weeks(2))
      .withName("Example Rolling Loan Policy")
      .renewFromSystemDate()
      .renewWith(Period.days(3))
      .create());

    final ZonedDateTime initialDueDate = ZonedDateTime.of(2018, 1, 17, 13, 45, 21, 0, UTC);

    Loan loan = new LoanBuilder()
      .open()
      .withLoanDate(ZonedDateTime.of(2018, 1, 20, 13, 45, 21, 0, UTC))
      .withDueDate(initialDueDate)
      .asDomainObject()
      .withLoanPolicy(loanPolicy);

    ZonedDateTime renewalDate = initialDueDate.minusDays(3);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, renewalDate, new RequestQueue(Collections.emptyList()), errorHandler);

    assertTrue(matchErrorReason(errorHandler, RENEWAL_WOULD_NOT_CHANGE_THE_DUE_DATE));
  }

  @Test
  void shouldFailWhenRenewalWouldMeanEarlierDueDate() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(Period.weeks(2))
      .withName("Example Rolling Loan Policy")
      .renewFromSystemDate()
      .renewWith(Period.days(3))
      .create());

    final ZonedDateTime initialDueDate = ZonedDateTime.of(2018, 1, 17, 13, 45, 21, 0, UTC);

    Loan loan = new LoanBuilder()
      .open()
      .withLoanDate(ZonedDateTime.of(2018, 1, 20, 13, 45, 21, 0, UTC))
      .withDueDate(initialDueDate)
      .asDomainObject()
      .withLoanPolicy(loanPolicy);

    ZonedDateTime renewalDate = initialDueDate.minusDays(4);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, renewalDate, new RequestQueue(Collections.emptyList()), errorHandler);

    assertTrue(matchErrorReason(errorHandler, RENEWAL_WOULD_NOT_CHANGE_THE_DUE_DATE));
  }

  private Loan loanFor(ZonedDateTime loanDate, ZonedDateTime dueDate, LoanPolicy loanPolicy) {
    return new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .withDueDate(dueDate)
      .withItemId(ITEM_ID)
      .asDomainObject()
      .withLoanPolicy(loanPolicy);
  }

  private RequestQueue creteRequestQueue(String requestId, RequestType requestType) {
    JsonObject requestRepresentation = new JsonObject()
      .put("id", requestId)
      .put("itemId", ITEM_ID)
      .put("requestType", requestType.getValue());

    RequestQueue requestQueue = new RequestQueue(new ArrayList<>());
    requestQueue.add(Request.from(requestRepresentation));
    return requestQueue;
  }

  private Result<Loan> renew(Loan loan, ZonedDateTime renewalDate,
    RequestQueue requestQueue, CirculationErrorHandler errorHandler) {

    RenewalContext renewalContext = RenewalContext.create(loan, new JsonObject(), "no-user")
      .withRequestQueue(requestQueue);

    return new RenewByBarcodeResource(null)
      .regularRenew(renewalContext, errorHandler, renewalDate)
      .map(RenewalContext::getLoan);
  }

  private boolean matchErrorReason(CirculationErrorHandler errorHandler, String expectedReason) {
    return errorHandler.getErrors().keySet().stream()
      .map(ValidationErrorFailure.class::cast)
      .anyMatch(httpFailure -> httpFailure.hasErrorWithReason(expectedReason));
  }
}
