package org.folio.circulation.domain.policy;

import static api.support.matchers.FailureMatcher.hasValidationFailure;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import api.support.builders.FixedDueDateSchedule;
import api.support.builders.FixedDueDateSchedulesBuilder;
import api.support.builders.ItemBuilder;
import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.RequestBuilder;
import io.vertx.core.json.JsonObject;

public class FixedLoanPolicyRenewalDueDateCalculationTests {

  private static final String EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES =
    "renewal date falls outside of date ranges in fixed loan policy";

  private static final String EXPECTED_REASON_OPEN_RECALL_REQUEST =
    "items cannot be renewed when there is an active recall request";
  private static final String RENEWAL_WOULD_NOT_CHANGE_THE_DUE_DATE =
    "renewal would not change the due date";
  private static final String LOAN_AT_MAXIMUM_RENEWAL_NUMBER = "loan at maximum renewal number";

  @Test
  public void shouldFailWhenLoanDateIsBeforeOnlyScheduleAvailable() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeYear(2018))
        .create());

    Loan loan = existingLoan(loanPolicy);

    DateTime renewalDate = new DateTime(2017, 12, 30, 14, 32, 21, DateTimeZone.UTC);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, renewalDate, new RequestQueue(Collections.emptyList()), errorHandler);

    assertEquals(1, errorHandler.getErrors().size());
    assertTrue(matchErrorReason(errorHandler, EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES));
  }

  @Test
  public void multipleRenewalFailuresWhenDateFallsOutsideDateRangesAndItemHasOpenRecallRequest() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeYear(2019))
        .create());

    Loan loan = existingLoan(loanPolicy);

    DateTime renewalDate = new DateTime(2018, 12, 15, 14, 32, 21, DateTimeZone.UTC);

    String requestId = UUID.randomUUID().toString();
    RequestQueue requestQueue = creteRequestQueue(requestId, RequestType.RECALL);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, renewalDate, requestQueue, errorHandler);

    assertEquals(2, errorHandler.getErrors().size());
    assertTrue(matchErrorReason(errorHandler, EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES));
    assertTrue(matchErrorReason(errorHandler, EXPECTED_REASON_OPEN_RECALL_REQUEST));
  }

  @Test
  public void shouldFailWhenLoanDateIsAfterOnlyScheduleAvailable() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeYear(2018))
        .create());

    Loan loan = existingLoan(loanPolicy);

    DateTime renewalDate = new DateTime(2019, 1, 1, 8, 10, 45, DateTimeZone.UTC);

    RequestQueue requestQueue =  new RequestQueue(Collections.emptyList());
    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, renewalDate, requestQueue, errorHandler);

    assertEquals(1, errorHandler.getErrors().size());
    assertTrue(matchErrorReason(errorHandler, EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES));
  }

  @Test
  public void shouldUseOnlyScheduleAvailableWhenLoanDateFits() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeYear(2018))
        .create());

    Loan loan = existingLoan(loanPolicy);

    DateTime renewalDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    String requestId = UUID.randomUUID().toString();
    RequestQueue requestQueue = creteRequestQueue(requestId, RequestType.PAGE);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    Result<Loan> result = renew(loan, renewalDate, requestQueue, errorHandler);

    assertThat(result.value().getDueDate(), is(
      new DateTime(2018, 12, 31, 23, 59, 59,
      DateTimeZone.UTC)));
  }

  @Test
  public void shouldUseFirstScheduleAvailableWhenLoanDateFits() {
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
      .withLoanDate(new DateTime(2018, 1, 20, 13, 45, 21, DateTimeZone.UTC))
      .withDueDate(new DateTime(2018, 1, 31, 23, 59, 59, DateTimeZone.UTC))
      .asDomainObject()
      .withLoanPolicy(loanPolicy);

    DateTime renewalDate = new DateTime(2018, 2, 8, 11, 14, 54, DateTimeZone.UTC);

    Result<Loan> result = renew(loan, renewalDate,
      new RequestQueue(Collections.emptyList()), new OverridingErrorHandler(null));

    assertThat(result.value().getDueDate(), is(expectedSchedule.due));
  }

  @Test
  public void shouldUseMiddleScheduleAvailableWhenLoanDateFits() {
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

    DateTime renewalDate = new DateTime(2018, 2, 27, 16, 23, 43, DateTimeZone.UTC);

    Result<Loan> result = renew(loan, renewalDate, new RequestQueue(Collections.emptyList()),
      new OverridingErrorHandler(null));

    assertThat(result.value().getDueDate(), is(expectedSchedule.due));
  }

  @Test
  public void shouldUseLastScheduleAvailableWhenLoanDateFits() {
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

    DateTime renewalDate = new DateTime(2018, 3, 12, 7, 15, 23, DateTimeZone.UTC);

   Result<Loan> result = renew(loan, renewalDate,
      new RequestQueue(Collections.emptyList()), new OverridingErrorHandler(null));

    assertThat(result.value().getDueDate(), is(expectedSchedule.due));
  }

  @Test
  public void shouldUseAlternateScheduleWhenAvailable() {
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

    DateTime renewalDate = new DateTime(2018, 2, 5, 14, 22, 32, DateTimeZone.UTC);

    Result<Loan> result = renew(loan, renewalDate, new RequestQueue(Collections.emptyList()),
      new OverridingErrorHandler(null));

    assertThat(result.value().getDueDate(), is(expectedSchedule.due));
  }

  @Test
  public void shouldApplyAlternateScheduleWhenQueuedRequestIsHoldAndFixed() {
    final Period alternateCheckoutLoanPeriod = Period.from(2, "Weeks");

    final DateTime systemTime = new DateTime(2019, 6, 14, 11, 23, 43, DateTimeZone.UTC);

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
    DateTime calculatedDueDate = loanPolicy.calculateInitialDueDate(loan, requestQueue).value();

    String key = "alternateCheckoutLoanPeriod";

    DateTime expectedDueDate = alternateCheckoutLoanPeriod.addTo(
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
  public void shouldFailWhenLoanDateIsBeforeAllSchedules() {
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

    DateTime renewalDate = new DateTime(2017, 12, 30, 14, 32, 21, DateTimeZone.UTC);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);

    renew(loan, renewalDate, new RequestQueue(Collections.emptyList()), errorHandler);

    assertEquals(1, errorHandler.getErrors().size());
    assertTrue(matchErrorReason(errorHandler, EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES));
  }

  @Test
  public void shouldFailWhenRenewalWouldNotChangeDueDate() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 1))
        .create());

    Loan loan = new LoanBuilder()
      .open()
      .withLoanDate(new DateTime(2018, 1, 20, 13, 45, 21, DateTimeZone.UTC))
      .withDueDate(new DateTime(2018, 1, 31, 23, 59, 59, DateTimeZone.UTC))
      .asDomainObject()
      .withLoanPolicy(loanPolicy);

    DateTime renewalDate = new DateTime(2018, 1, 3, 8, 12, 32, DateTimeZone.UTC);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, renewalDate, new RequestQueue(Collections.emptyList()), errorHandler);

    assertTrue(matchErrorReason(errorHandler, RENEWAL_WOULD_NOT_CHANGE_THE_DUE_DATE));
  }

  @Test
  public void shouldFailWhenRenewalWouldMeanEarlierDueDate() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 1))
        .create());

    Loan loan = new LoanBuilder()
      .open()
      .withLoanDate(new DateTime(2018, 1, 20, 13, 45, 21, DateTimeZone.UTC))
      .withDueDate(new DateTime(2018, 2, 28, 23, 59, 59, DateTimeZone.UTC))
      .asDomainObject()
      .withLoanPolicy(loanPolicy);

    DateTime renewalDate = new DateTime(2018, 1, 3, 8, 12, 32, DateTimeZone.UTC);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, renewalDate, new RequestQueue(Collections.emptyList()), errorHandler);

    assertTrue(matchErrorReason(errorHandler, RENEWAL_WOULD_NOT_CHANGE_THE_DUE_DATE));
  }

  @Test
  public void shouldFailWhenRenewalWouldMeanEarlierDueDateAndReachedRenewalLimit() {
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
      .withLoanDate(new DateTime(2018, 1, 20, 13, 45, 21, DateTimeZone.UTC))
      .withDueDate(new DateTime(2018, 1, 31, 23, 59, 59, DateTimeZone.UTC))
      .asDomainObject()
      .withLoanPolicy(loanPolicy);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);

    loan = renew(loan,
      new DateTime(2018, 2, 1, 11, 23, 43, DateTimeZone.UTC),
      new RequestQueue(Collections.emptyList()), errorHandler).value();

    DateTime renewalDate = new DateTime(2018, 3, 5, 8, 12, 32, DateTimeZone.UTC);

    renew(loan, renewalDate, new RequestQueue(Collections.emptyList()), errorHandler);

    assertTrue(matchErrorReason(errorHandler, EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES));
    assertTrue(matchErrorReason(errorHandler, LOAN_AT_MAXIMUM_RENEWAL_NUMBER));
    assertEquals(2, errorHandler.getErrors().size());
  }

  @Test
  public void multipleRenewalFailuresWhenLoanHasReachedMaximumNumberOfRenewalsAndOpenRecallRequest() {
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
      .withLoanDate(new DateTime(2018, 1, 20, 13, 45, 21, DateTimeZone.UTC))
      .withDueDate(new DateTime(2018, 1, 31, 23, 59, 59, DateTimeZone.UTC))
      .asDomainObject()
      .withLoanPolicy(loanPolicy);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    loan = renew(loan, new DateTime(2018, 2, 1, 11, 23, 43, DateTimeZone.UTC),
      new RequestQueue(Collections.emptyList()), errorHandler).value();

    DateTime renewalDate = new DateTime(2018, 3, 5, 8, 12, 32, DateTimeZone.UTC);

    String requestId = UUID.randomUUID().toString();
    RequestQueue requestQueue = creteRequestQueue(requestId, RequestType.RECALL);

    renew(loan, renewalDate, requestQueue, errorHandler);

    assertTrue(matchErrorReason(errorHandler, EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES));
    assertTrue(matchErrorReason(errorHandler, LOAN_AT_MAXIMUM_RENEWAL_NUMBER));
    assertTrue(matchErrorReason(errorHandler, LOAN_AT_MAXIMUM_RENEWAL_NUMBER));
  }

  @Test
  public void shouldFailWhenLoanDateIsAfterAllSchedules() {
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

    DateTime renewalDate = new DateTime(2018, 4, 1, 6, 34, 21, DateTimeZone.UTC);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, renewalDate, new RequestQueue(Collections.emptyList()), errorHandler);

    assertTrue(matchErrorReason(errorHandler, EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES));
  }

  @Test
  public void shouldFailWhenLoanDateIsInBetweenSchedules() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder()
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 1))
        .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3))
        .create());

    Loan loan = existingLoan(loanPolicy);

    DateTime renewalDate = new DateTime(2018, 2, 18, 6, 34, 21, DateTimeZone.UTC);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, renewalDate, new RequestQueue(Collections.emptyList()), errorHandler);

    assertTrue(matchErrorReason(errorHandler, EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES));
  }

  @Test
  public void shouldFailWhenNoSchedulesDefined() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withName("Example Fixed Schedule Loan Policy")
      .create())
      .withDueDateSchedules(new FixedDueDateSchedulesBuilder().create());


    Loan loan = existingLoan(loanPolicy);

    DateTime renewalDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, renewalDate, new RequestQueue(Collections.emptyList()), errorHandler);

    assertTrue(matchErrorReason(errorHandler, EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES));
  }

  @Test
  public void shouldFailWhenSchedulesCollectionIsNull() {
    DateTime renewalDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    final FixedScheduleRenewalDueDateStrategy calculator =
      new FixedScheduleRenewalDueDateStrategy(UUID.randomUUID().toString(),
        "Example Fixed Schedule Loan Policy", null, renewalDate,
        s -> new ValidationError(s, null, null));

    Loan loan = existingLoan();

    final Result<DateTime> result = calculator.calculateDueDate(loan);

    assertThat(result, hasValidationFailure(
      EXPECTED_REASON_DATE_FALLS_OUTSIDE_DATE_RANGES));
  }

  @Test
  public void shouldFailWhenNoSchedules() {
    DateTime renewalDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    final FixedScheduleRenewalDueDateStrategy calculator =
      new FixedScheduleRenewalDueDateStrategy(UUID.randomUUID().toString(),
        "Example Fixed Schedule Loan Policy", new NoFixedDueDateSchedules(),
        renewalDate, s -> new ValidationError(s, null, null));

    Loan loan = existingLoan();

    final Result<DateTime> result = calculator.calculateDueDate(loan);

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
      .withLoanDate(new DateTime(2018, 1, 20, 13, 45, 21, DateTimeZone.UTC))
      .withDueDate(new DateTime(2018, 1, 31, 23, 59, 59, DateTimeZone.UTC))
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

  private Result<Loan> renew(Loan loan, DateTime renewalDate,
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
