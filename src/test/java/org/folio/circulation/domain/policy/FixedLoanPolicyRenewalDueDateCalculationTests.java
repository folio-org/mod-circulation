package org.folio.circulation.domain.policy;

import static api.support.matchers.FailureMatcher.hasValidationFailure;
import static java.lang.String.format;
import static java.time.ZoneOffset.UTC;
import static java.util.Arrays.asList;
import static org.folio.circulation.support.utils.DateTimeUtil.atStartOfDay;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.resources.handlers.error.CirculationErrorHandler;
import org.folio.circulation.resources.handlers.error.OverridingErrorHandler;
import org.folio.circulation.resources.renewal.RenewByBarcodeResource;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;

import api.support.builders.FixedDueDateSchedule;
import api.support.builders.FixedDueDateSchedulesBuilder;
import api.support.builders.ItemBuilder;
import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.RequestBuilder;
import io.vertx.core.json.JsonObject;

class FixedLoanPolicyRenewalDueDateCalculationTests {

  private static final String EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES =
    "renewal date falls outside of date ranges in fixed loan policy";

  private static final String EXPECTED_REASON_OPEN_RECALL_REQUEST =
    "items cannot be renewed when there is an active recall request";
  private static final String RENEWAL_WOULD_NOT_CHANGE_THE_DUE_DATE =
    "renewal would not change the due date";
  private static final String LOAN_AT_MAXIMUM_RENEWAL_NUMBER = "loan at maximum renewal number";

  @Test
  void shouldFailWhenLoanDateIsBeforeOnlyScheduleAvailable() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeYear(2018))
        .create());

    Loan loan = existingLoan(loanPolicy);

    ZonedDateTime renewalDate = ZonedDateTime.of(2017, 12, 30, 14, 32, 21, 0, UTC);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, renewalDate, new RequestQueue(Collections.emptyList()), errorHandler);

    assertEquals(1, errorHandler.getErrors().size());
    assertTrue(matchErrorReason(errorHandler, EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES));
  }

  @Test
  void multipleRenewalFailuresWhenDateFallsOutsideDateRangesAndItemHasOpenRecallRequest() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeYear(2019))
        .create());

    Loan loan = existingLoan(loanPolicy);

    ZonedDateTime renewalDate = ZonedDateTime.of(2018, 12, 15, 14, 32, 21, 0, UTC);

    String requestId = UUID.randomUUID().toString();
    RequestQueue requestQueue = creteRequestQueue(requestId, RequestType.RECALL);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, renewalDate, requestQueue, errorHandler);

    assertEquals(2, errorHandler.getErrors().size());
    assertTrue(matchErrorReason(errorHandler, EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES));
    assertTrue(matchErrorReason(errorHandler, EXPECTED_REASON_OPEN_RECALL_REQUEST));
  }

  @Test
  void shouldFailWhenLoanDateIsAfterOnlyScheduleAvailable() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeYear(2018))
        .create());

    Loan loan = existingLoan(loanPolicy);

    ZonedDateTime renewalDate = ZonedDateTime.of(2019, 1, 1, 8, 10, 45, 0, UTC);

    RequestQueue requestQueue =  new RequestQueue(Collections.emptyList());
    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, renewalDate, requestQueue, errorHandler);

    assertEquals(1, errorHandler.getErrors().size());
    assertTrue(matchErrorReason(errorHandler, EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES));
  }

  @Test
  void shouldUseOnlyScheduleAvailableWhenLoanDateFits() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeYear(2018))
        .create());

    Loan loan = existingLoan(loanPolicy);

    ZonedDateTime renewalDate = ZonedDateTime.of(2018, 3, 14, 11, 14, 54, 0, UTC);

    String requestId = UUID.randomUUID().toString();
    RequestQueue requestQueue = creteRequestQueue(requestId, RequestType.PAGE);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    Result<Loan> result = renew(loan, renewalDate, requestQueue, errorHandler);

    final ZonedDateTime expectedDate = atStartOfDay(LocalDate.of(2018, 12, 31), UTC)
      .plusDays(1)
      .minusSeconds(1);

    assertThat(result.value().getDueDate(), is(expectedDate));
  }

  @Test
  void shouldUseFirstScheduleAvailableWhenLoanDateFits() {
    final FixedDueDateSchedule expectedSchedule = FixedDueDateSchedule.wholeMonth(2018, 2);

    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(expectedSchedule)
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 4))
        .create());

    Loan loan = new LoanBuilder()
      .open()
      .withLoanDate(ZonedDateTime.of(2018, 1, 20, 13, 45, 21, 0, UTC))
      .withDueDate(ZonedDateTime.of(2018, 1, 31, 23, 59, 59, 0, UTC))
      .asDomainObject()
      .withLoanPolicy(loanPolicy);

    ZonedDateTime renewalDate = ZonedDateTime.of(2018, 2, 8, 11, 14, 54, 0, UTC);

    Result<Loan> result = renew(loan, renewalDate,
      new RequestQueue(Collections.emptyList()), new OverridingErrorHandler(null));

    assertThat(result.value().getDueDate(), is(expectedSchedule.due));
  }

  @Test
  void shouldUseMiddleScheduleAvailableWhenLoanDateFits() {
    final FixedDueDateSchedule expectedSchedule = FixedDueDateSchedule.wholeMonth(2018, 2);

    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 1))
        .addSchedule(expectedSchedule)
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3))
        .create());

    Loan loan = existingLoan(loanPolicy);

    ZonedDateTime renewalDate = ZonedDateTime.of(2018, 2, 27, 16, 23, 43, 0, UTC);

    Result<Loan> result = renew(loan, renewalDate, new RequestQueue(Collections.emptyList()),
      new OverridingErrorHandler(null));

    assertThat(result.value().getDueDate(), is(expectedSchedule.due));
  }

  @Test
  void shouldUseLastScheduleAvailableWhenLoanDateFits() {
    final FixedDueDateSchedule expectedSchedule = FixedDueDateSchedule.wholeMonth(2018, 3);

    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 1))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 2))
        .addSchedule(expectedSchedule)
        .create());

    Loan loan = existingLoan(loanPolicy);

    ZonedDateTime renewalDate = ZonedDateTime.of(2018, 3, 12, 7, 15, 23, 0, UTC);

    Result<Loan> result = renew(loan, renewalDate,
      new RequestQueue(Collections.emptyList()), new OverridingErrorHandler(null));

    assertThat(result.value().getDueDate(), is(expectedSchedule.due));
  }

  @Test
  void shouldUseAlternateScheduleWhenAvailable() {
    final FixedDueDateSchedule expectedSchedule = FixedDueDateSchedule.wholeYear(2018);

    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .renewWith(UUID.randomUUID())
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 1))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 2))
        .create())
      .withAlternateRenewalSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(expectedSchedule)
        .create());

    Loan loan = existingLoan(loanPolicy);

    ZonedDateTime renewalDate = ZonedDateTime.of(2018, 2, 5, 14, 22, 32, 0, UTC);

    Result<Loan> result = renew(loan, renewalDate, new RequestQueue(Collections.emptyList()),
      new OverridingErrorHandler(null));

    assertThat(result.value().getDueDate(), is(expectedSchedule.due));
  }

  @Test
  void shouldApplyAlternateScheduleWhenQueuedRequestIsHoldAndFixed() {
    final Period alternateCheckoutLoanPeriod = Period.from(2, "Weeks");

    final ZonedDateTime systemTime = ZonedDateTime.of(2019, 6, 14, 11, 23, 43, 0, UTC);

    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withAlternateCheckoutLoanPeriod(alternateCheckoutLoanPeriod)
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeYear(systemTime.getYear()))
        .create());

    Item item = Item.from(
      new ItemBuilder()
        .checkOut()
        .withId(UUID.randomUUID())
        .create());
    Loan loan = Loan.from(
      new LoanBuilder()
        .withItemId(UUID.fromString(item.getItemId()))
        .withLoanDate(systemTime)
        .create());

    Request requestOne = Request.from(new RequestBuilder()
      .withId(UUID.randomUUID())
      .withItemId(UUID.fromString(loan.getItemId()))
      .withPosition(1)
      .create());

    Request requestTwo = Request.from(new RequestBuilder()
      .withId(UUID.randomUUID())
      .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
      .hold()
      .withItemId(UUID.fromString(loan.getItemId()))
      .withPosition(2)
      .create());

    RequestQueue requestQueue = new RequestQueue(asList(requestOne, requestTwo));
    ZonedDateTime calculatedDueDate = loanPolicy.calculateInitialDueDate(loan, requestQueue).value();

    String key = "alternateCheckoutLoanPeriod";

    ZonedDateTime expectedDueDate = alternateCheckoutLoanPeriod.addTo(
        systemTime,
        () -> errorForLoanPeriod(format("the \"%s\" is not recognized", key)),
        interval -> errorForLoanPeriod(format("the interval \"%s\" in \"%s\" is not recognized", interval, key)),
        dur -> errorForLoanPeriod(format("the duration \"%s\" in \"%s\" is invalid", dur, key)))
          .value();

    assertThat(calculatedDueDate, is(expectedDueDate));
  }

  private ValidationError errorForLoanPeriod(String reason) {
    Map<String, String> parameters = new HashMap<>();
    return new ValidationError(reason, parameters);
  }

  @Test
  void shouldFailWhenLoanDateIsBeforeAllSchedules() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 1))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 2))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3))
        .create());

    Loan loan = existingLoan(loanPolicy);

    ZonedDateTime renewalDate = ZonedDateTime.of(2017, 12, 30, 14, 32, 21, 0, UTC);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);

    renew(loan, renewalDate, new RequestQueue(Collections.emptyList()), errorHandler);

    assertEquals(1, errorHandler.getErrors().size());
    assertTrue(matchErrorReason(errorHandler, EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES));
  }

  @Test
  void shouldFailWhenRenewalWouldNotChangeDueDate() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 1))
        .create());

    Loan loan = new LoanBuilder()
      .open()
      .withLoanDate(ZonedDateTime.of(2018, 1, 20, 13, 45, 21, 0, UTC))
      .withDueDate(ZonedDateTime.of(2018, 1, 31, 23, 59, 59, 0, UTC))
      .asDomainObject()
      .withLoanPolicy(loanPolicy);

    ZonedDateTime renewalDate = ZonedDateTime.of(2018, 1, 3, 8, 12, 32, 0, UTC);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, renewalDate, new RequestQueue(Collections.emptyList()), errorHandler);

    assertTrue(matchErrorReason(errorHandler, RENEWAL_WOULD_NOT_CHANGE_THE_DUE_DATE));
  }

  @Test
  void shouldFailWhenRenewalWouldMeanEarlierDueDate() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 1))
        .create());

    Loan loan = new LoanBuilder()
      .open()
      .withLoanDate(ZonedDateTime.of(2018, 1, 20, 13, 45, 21, 0, UTC))
      .withDueDate(ZonedDateTime.of(2018, 2, 28, 23, 59, 59, 0, UTC))
      .asDomainObject()
      .withLoanPolicy(loanPolicy);

    ZonedDateTime renewalDate = ZonedDateTime.of(2018, 1, 3, 8, 12, 32, 0, UTC);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, renewalDate, new RequestQueue(Collections.emptyList()), errorHandler);

    assertTrue(matchErrorReason(errorHandler, RENEWAL_WOULD_NOT_CHANGE_THE_DUE_DATE));
  }

  @Test
  void shouldFailWhenRenewalWouldMeanEarlierDueDateAndReachedRenewalLimit() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .limitedRenewals(1)
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 1))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 2))
        .create());

    Loan loan = new LoanBuilder()
      .open()
      .withLoanDate(ZonedDateTime.of(2018, 1, 20, 13, 45, 21, 0, UTC))
      .withDueDate(ZonedDateTime.of(2018, 1, 31, 23, 59, 59, 0, UTC))
      .asDomainObject()
      .withLoanPolicy(loanPolicy);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);

    loan = renew(loan,
      ZonedDateTime.of(2018, 2, 1, 11, 23, 43, 0, UTC),
      new RequestQueue(Collections.emptyList()), errorHandler).value();

    ZonedDateTime renewalDate = ZonedDateTime.of(2018, 3, 5, 8, 12, 32, 0, UTC);

    renew(loan, renewalDate, new RequestQueue(Collections.emptyList()), errorHandler);

    assertTrue(matchErrorReason(errorHandler, EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES));
    assertTrue(matchErrorReason(errorHandler, LOAN_AT_MAXIMUM_RENEWAL_NUMBER));
    assertEquals(2, errorHandler.getErrors().size());
  }

  @Test
  void multipleRenewalFailuresWhenLoanHasReachedMaximumNumberOfRenewalsAndOpenRecallRequest() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .limitedRenewals(1)
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 1))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 2))
        .create());

    Loan loan = new LoanBuilder()
      .open()
      .withLoanDate(ZonedDateTime.of(2018, 1, 20, 13, 45, 21, 0, UTC))
      .withDueDate(ZonedDateTime.of(2018, 1, 31, 23, 59, 59, 0, UTC))
      .asDomainObject()
      .withLoanPolicy(loanPolicy);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    loan = renew(loan, ZonedDateTime.of(2018, 2, 1, 11, 23, 43, 0, UTC),
      new RequestQueue(Collections.emptyList()), errorHandler).value();

    ZonedDateTime renewalDate = ZonedDateTime.of(2018, 3, 5, 8, 12, 32, 0, UTC);

    String requestId = UUID.randomUUID().toString();
    RequestQueue requestQueue = creteRequestQueue(requestId, RequestType.RECALL);

    renew(loan, renewalDate, requestQueue, errorHandler);

    assertTrue(matchErrorReason(errorHandler, EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES));
    assertTrue(matchErrorReason(errorHandler, LOAN_AT_MAXIMUM_RENEWAL_NUMBER));
    assertTrue(matchErrorReason(errorHandler, LOAN_AT_MAXIMUM_RENEWAL_NUMBER));
  }

  @Test
  void shouldFailWhenLoanDateIsAfterAllSchedules() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 1))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 2))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3))
        .create());

    Loan loan = existingLoan(loanPolicy);

    ZonedDateTime renewalDate = ZonedDateTime.of(2018, 4, 1, 6, 34, 21, 0, UTC);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, renewalDate, new RequestQueue(Collections.emptyList()), errorHandler);

    assertTrue(matchErrorReason(errorHandler, EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES));
  }

  @Test
  void shouldFailWhenLoanDateIsInBetweenSchedules() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 1))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3))
        .create());

    Loan loan = existingLoan(loanPolicy);

    ZonedDateTime renewalDate = ZonedDateTime.of(2018, 2, 18, 6, 34, 21, 0, UTC);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, renewalDate, new RequestQueue(Collections.emptyList()), errorHandler);

    assertTrue(matchErrorReason(errorHandler, EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES));
  }

  @Test
  void shouldFailWhenNoSchedulesDefined() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder().create());


    Loan loan = existingLoan(loanPolicy);

    ZonedDateTime renewalDate = ZonedDateTime.of(2018, 3, 14, 11, 14, 54, 0, UTC);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, renewalDate, new RequestQueue(Collections.emptyList()), errorHandler);

    assertTrue(matchErrorReason(errorHandler, EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES));
  }

  @Test
  void shouldFailWhenSchedulesCollectionIsNull() {
    ZonedDateTime renewalDate = ZonedDateTime.of(2018, 3, 14, 11, 14, 54, 0, UTC);

    final FixedScheduleRenewalDueDateStrategy calculator =
      new FixedScheduleRenewalDueDateStrategy(UUID.randomUUID().toString(),
        "Example Fixed Schedule Loan Policy", null, renewalDate,
        s -> new ValidationError(s, null, null));

    Loan loan = existingLoan();

    final Result<ZonedDateTime> result = calculator.calculateDueDate(loan);

    assertThat(result, hasValidationFailure(
      EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES));
  }

  @Test
  void shouldFailWhenNoSchedules() {
    ZonedDateTime renewalDate = ZonedDateTime.of(2018, 3, 14, 11, 14, 54, 0, UTC);

    final FixedScheduleRenewalDueDateStrategy calculator =
      new FixedScheduleRenewalDueDateStrategy(UUID.randomUUID().toString(),
        "Example Fixed Schedule Loan Policy", new NoFixedDueDateSchedules(),
        renewalDate, s -> new ValidationError(s, null, null));

    Loan loan = existingLoan();

    final Result<ZonedDateTime> result = calculator.calculateDueDate(loan);

    assertThat(result, hasValidationFailure(
      EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES));
  }

  UUID checkoutServicePointId = UUID.randomUUID();

  private Loan existingLoan(LoanPolicy loanPolicy) {
    return existingLoan()
      .withLoanPolicy(loanPolicy);
  }

  private Loan existingLoan() {
    return new LoanBuilder()
      .open()
      .withLoanDate(ZonedDateTime.of(2018, 1, 20, 13, 45, 21, 0, UTC))
      .withDueDate(ZonedDateTime.of(2018, 1, 31, 23, 59, 59, 0, UTC))
      .withCheckoutServicePointId(checkoutServicePointId)
      .asDomainObject();
  }

  private RequestQueue creteRequestQueue(String requestId, RequestType requestType) {
    JsonObject requestRepresentation = new JsonObject()
      .put("id", requestId)
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
