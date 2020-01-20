package org.folio.circulation.domain;

import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.OverdueFinePolicyBuilder;
import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.http.entity.ContentType;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.OverdueFinePolicy;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.BadRequestFailure;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.RecordNotFoundFailure;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static api.support.fixtures.OpeningPeriodsExamples.CASE_ALL_DAY_OPENINGS_SERVICE_ID;
import static api.support.fixtures.OpeningPeriodsExamples.CASE_ERROR_400_SERVICE_ID;
import static api.support.fixtures.OpeningPeriodsExamples.CASE_ERROR_404_SERVICE_ID;
import static api.support.fixtures.OpeningPeriodsExamples.CASE_ERROR_500_SERVICE_ID;
import static api.support.fixtures.OpeningPeriodsExamples.CASE_NO_OPENING_DAYS_SERVICE_ID;
import static api.support.fixtures.OpeningPeriodsExamples.CASE_NO_OPENING_HOURS_SERVICE_ID;
import static api.support.fixtures.OpeningPeriodsExamples.CASE_TWO_OPENING_DAYS_SERVICE_ID;
import static api.support.fixtures.OpeningPeriodsExamples.getOpeningPeriodsById;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.OverduePeriodCalculator.countMinutes;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(JUnitParamsRunner.class)
public class OverduePeriodCalculatorTest {
  private static final String INTERVAL_MONTHS = "Months";
  private static final String INTERVAL_WEEKS = "Weeks";
  private static final String INTERVAL_DAYS = "Days";
  private static final String INTERVAL_HOURS = "Hours";
  private static final String INTERVAL_MINUTES = "Minutes";
  private static final String PERIODS_REQUEST_PARAMS_TEMPLATE =
    "servicePointId=%s&startDate=\\d{4}-\\d{2}-\\d{2}&endDate=\\d{4}-\\d{2}-\\d{2}&includeClosedDays=(true|false)";

  private Clients clients;

  @Before
  public void setUp() {
    this.clients = mock(Clients.class);
    CollectionResourceClient calendarClient = mock(CollectionResourceClient.class);
    when(clients.calendarStorageClient()).thenReturn(calendarClient);

    when(calendarClient.getManyWithRawQueryStringParameters(
      matches(patternFor(CASE_TWO_OPENING_DAYS_SERVICE_ID))))
      .thenAnswer(rq -> completedFuture(succeeded(
        new Response(200,
          getOpeningPeriodsById(CASE_TWO_OPENING_DAYS_SERVICE_ID).toString(),
          ContentType.APPLICATION_JSON.toString()))));

    when(calendarClient.getManyWithRawQueryStringParameters(
      matches(patternFor(CASE_NO_OPENING_HOURS_SERVICE_ID))))
      .thenAnswer(rq -> completedFuture(succeeded(
        new Response(200,
          getOpeningPeriodsById(CASE_NO_OPENING_HOURS_SERVICE_ID).toString(),
          ContentType.APPLICATION_JSON.toString()))));

    when(calendarClient.getManyWithRawQueryStringParameters(
      matches(patternFor(CASE_NO_OPENING_DAYS_SERVICE_ID))))
      .thenAnswer(rq -> completedFuture(succeeded(
        new Response(200,
          getOpeningPeriodsById(CASE_NO_OPENING_DAYS_SERVICE_ID).toString(),
          ContentType.APPLICATION_JSON.toString()))));

    when(calendarClient.getManyWithRawQueryStringParameters(
      matches(patternFor(CASE_ALL_DAY_OPENINGS_SERVICE_ID))))
      .thenAnswer(rq -> completedFuture(succeeded(
        new Response(200,
          getOpeningPeriodsById(CASE_ALL_DAY_OPENINGS_SERVICE_ID).toString(),
          ContentType.APPLICATION_JSON.toString()))));

    when(calendarClient.getManyWithRawQueryStringParameters(
      matches(patternFor(CASE_ERROR_400_SERVICE_ID))))
      .thenAnswer(rq -> completedFuture(failed(new BadRequestFailure("400"))));

    when(calendarClient.getManyWithRawQueryStringParameters(
      matches(patternFor(CASE_ERROR_404_SERVICE_ID))))
      .thenAnswer(rq -> completedFuture(failed(
        new RecordNotFoundFailure("period", UUID.randomUUID().toString()))));

    when(calendarClient.getManyWithRawQueryStringParameters(
      matches(patternFor(CASE_ERROR_500_SERVICE_ID))))
      .thenAnswer(rq -> completedFuture(failed(new ServerErrorFailure("500"))));
  }

  @Test
  public void dueDateIsInFuture() throws ExecutionException, InterruptedException {
    final int expectedOverdueMinutes = 0;
    LoanPolicy loanPolicy = createLoanPolicy(5, INTERVAL_DAYS);
    OverdueFinePolicy overdueFinePolicy = createOverdueFinePolicy(false, true);
    DateTime systemTime = DateTime.now(DateTimeZone.UTC);
    Loan loan =  new LoanBuilder()
      .withDueDate(systemTime.plusDays(1))
      .asDomainObject()
      .withLoanPolicy(loanPolicy)
      .withOverdueFinePolicy(overdueFinePolicy);

    CompletableFuture<Result<Integer>> future = countMinutes(loan, systemTime, null);
    int overdueMinutes = future.get().value();
    assertEquals(expectedOverdueMinutes, overdueMinutes);
  }

  @Test
  public void shouldCountClosedWithNoGracePeriod() throws ExecutionException, InterruptedException {
    final int expectedOverdueMinutes = 10;
    LoanPolicy loanPolicy = createLoanPolicy(null, null);
    OverdueFinePolicy overdueFinePolicy = createOverdueFinePolicy(false, true);
    DateTime systemTime = DateTime.now(DateTimeZone.UTC);
    Loan loan =  new LoanBuilder()
      .withDueDate(systemTime.minusMinutes(expectedOverdueMinutes))
      .asDomainObject()
      .withLoanPolicy(loanPolicy)
      .withOverdueFinePolicy(overdueFinePolicy);

    CompletableFuture<Result<Integer>> future = countMinutes(loan, systemTime, null);
    int overdueMinutes = future.get().value();
    assertEquals(expectedOverdueMinutes, overdueMinutes);
  }

  @Test
  public void shouldCountClosedWithMissingLoanPolicy() throws ExecutionException, InterruptedException {
    final int expectedOverdueMinutes = 10;
    DateTime systemTime = DateTime.now(DateTimeZone.UTC);
    OverdueFinePolicy overdueFinePolicy = createOverdueFinePolicy(false, true);
    Loan loan =  new LoanBuilder()
      .withDueDate(systemTime.minusMinutes(expectedOverdueMinutes))
      .asDomainObject()
      .withOverdueFinePolicy(overdueFinePolicy);

    CompletableFuture<Result<Integer>> future = countMinutes(loan, systemTime, null);
    int overdueMinutes = future.get().value();
    assertEquals(expectedOverdueMinutes, overdueMinutes);
  }

  @Test
  public void shouldCountClosedAndShouldIgnoreGracePeriod() throws ExecutionException, InterruptedException {
    final int expectedOverdueMinutes = 10;
    LoanPolicy loanPolicy = createLoanPolicy(5, INTERVAL_MINUTES);
    OverdueFinePolicy overdueFinePolicy = createOverdueFinePolicy(true, true);
    DateTime systemTime = DateTime.now(DateTimeZone.UTC);
    Loan loan =  new LoanBuilder()
      .withDueDate(systemTime.minusMinutes(expectedOverdueMinutes))
      .withDueDateChangedByRecall(true)
      .asDomainObject()
      .withLoanPolicy(loanPolicy)
      .withOverdueFinePolicy(overdueFinePolicy);

    CompletableFuture<Result<Integer>> future = countMinutes(loan, systemTime, null);
    int overdueMinutes = future.get().value();
    assertEquals(expectedOverdueMinutes, overdueMinutes);
  }

  @Test
  @Parameters({
    "0  | 10",
    "5  | 5",
    "10 | 0",
    "15 | 0"
      })
  public void shouldCountClosedWithVariousGracePeriodDurations(int gracePeriodDuration, int expectedResult)
    throws ExecutionException, InterruptedException {

    DateTime systemTime = DateTime.now(DateTimeZone.UTC);
    LoanPolicy loanPolicy = createLoanPolicy(gracePeriodDuration, INTERVAL_MINUTES);
    OverdueFinePolicy overdueFinePolicy = createOverdueFinePolicy(false, true);
    Loan loan =  new LoanBuilder()
      .withDueDate(systemTime.minusMinutes(10))
      .asDomainObject()
      .withLoanPolicy(loanPolicy)
      .withOverdueFinePolicy(overdueFinePolicy);

    CompletableFuture<Result<Integer>> future = countMinutes(loan, systemTime, null);
    int overdueMinutes = future.get().value();
    assertEquals(expectedResult, overdueMinutes);
  }

  @Test
  @Parameters({
    "Minutes   | 1",
    "Hours     | 60",
    "Days      | 1440",  // 60 * 24
    "Weeks     | 10080", // 60 * 24 * 7
    "Months    | 44640", // 60 * 24 * 31
    "Incorrect | 0",
    "Random    | 0"
  })
  public void shouldCountClosedWithVariousGracePeriodIntervals(String interval, int dueDateOffsetMinutes)
    throws ExecutionException, InterruptedException {

    int expectedOverdueMinutes = 10;
    DateTime systemTime = DateTime.now(DateTimeZone.UTC);
    LoanPolicy loanPolicy = createLoanPolicy(1, interval);
    OverdueFinePolicy overdueFinePolicy = createOverdueFinePolicy(false, true);
    Loan loan =  new LoanBuilder()
      .withDueDate(systemTime.minusMinutes(dueDateOffsetMinutes + expectedOverdueMinutes))
      .asDomainObject()
      .withLoanPolicy(loanPolicy)
      .withOverdueFinePolicy(overdueFinePolicy);

    CompletableFuture<Result<Integer>> future = countMinutes(loan, systemTime, null);

    int overdueMinutes = future.get().value();
    assertEquals(expectedOverdueMinutes, overdueMinutes);
  }

  @Test
  public void shouldNotCountClosedWithNoOpeningHours() throws ExecutionException, InterruptedException {
    final int expectedOverdueMinutes = 0;
    LoanPolicy loanPolicy = createLoanPolicy(1, INTERVAL_HOURS);
    OverdueFinePolicy overdueFinePolicy = createOverdueFinePolicy(false, false);
    DateTime systemTime = DateTime.now(DateTimeZone.UTC);
    Loan loan =  new LoanBuilder()
      .withDueDate(systemTime.minusMinutes(expectedOverdueMinutes))
      .withCheckoutServicePointId(UUID.fromString(CASE_NO_OPENING_HOURS_SERVICE_ID))
      .asDomainObject()
      .withLoanPolicy(loanPolicy)
      .withOverdueFinePolicy(overdueFinePolicy);

    CompletableFuture<Result<Integer>> future = countMinutes(loan, systemTime, clients);
    int overdueMinutes = future.get().value();
    assertEquals(expectedOverdueMinutes, overdueMinutes);
  }

  @Test
  public void shouldNotCountClosedWithNoOpeningDays() throws ExecutionException, InterruptedException {
    final int expectedOverdueMinutes = 0;
    LoanPolicy loanPolicy = createLoanPolicy(1, INTERVAL_HOURS);
    OverdueFinePolicy overdueFinePolicy = createOverdueFinePolicy(false, false);
    DateTime systemTime = DateTime.now(DateTimeZone.UTC);
    Loan loan =  new LoanBuilder()
      .withDueDate(systemTime.minusMinutes(expectedOverdueMinutes))
      .withCheckoutServicePointId(UUID.fromString(CASE_NO_OPENING_DAYS_SERVICE_ID))
      .asDomainObject()
      .withLoanPolicy(loanPolicy)
      .withOverdueFinePolicy(overdueFinePolicy);

    CompletableFuture<Result<Integer>> future = countMinutes(loan, systemTime, clients);
    int overdueMinutes = future.get().value();
    assertEquals(expectedOverdueMinutes, overdueMinutes);
  }

  @Test
  public void shouldNotCountClosedWithTwoOpeningDays() throws ExecutionException, InterruptedException {
    final int expectedOverdueMinutes = 60;
    LoanPolicy loanPolicy = createLoanPolicy(19, INTERVAL_HOURS);
    OverdueFinePolicy overdueFinePolicy = createOverdueFinePolicy(false, false);
    DateTime systemTime = DateTime.now(DateTimeZone.UTC);
    Loan loan =  new LoanBuilder()
      .withDueDate(systemTime.minusMinutes(expectedOverdueMinutes))
      .withCheckoutServicePointId(UUID.fromString(CASE_TWO_OPENING_DAYS_SERVICE_ID))
      .asDomainObject()
      .withLoanPolicy(loanPolicy)
      .withOverdueFinePolicy(overdueFinePolicy);

    CompletableFuture<Result<Integer>> future = countMinutes(loan, systemTime, clients);
    int overdueMinutes = future.get().value();
    assertEquals(expectedOverdueMinutes, overdueMinutes);
  }

  @Test
  public void shouldNotCountClosedWithTwoAllDayOpenings() throws ExecutionException, InterruptedException {
    final int expectedOverdueMinutes = 60 * 24 * 2;
    LoanPolicy loanPolicy = createLoanPolicy(0, INTERVAL_DAYS);
    OverdueFinePolicy overdueFinePolicy = createOverdueFinePolicy(false, false);
    DateTime systemTime = DateTime.now(DateTimeZone.UTC);
    Loan loan =  new LoanBuilder()
      .withDueDate(systemTime.minusMinutes(expectedOverdueMinutes))
      .withCheckoutServicePointId(UUID.fromString(CASE_ALL_DAY_OPENINGS_SERVICE_ID))
      .asDomainObject()
      .withLoanPolicy(loanPolicy)
      .withOverdueFinePolicy(overdueFinePolicy);

    CompletableFuture<Result<Integer>> future = countMinutes(loan, systemTime, clients);
    int overdueMinutes = future.get().value();
    assertEquals(expectedOverdueMinutes, overdueMinutes);
  }

  @Test
  public void shouldNotCountClosedWithError400() throws ExecutionException, InterruptedException {
    LoanPolicy loanPolicy = createLoanPolicy(0, INTERVAL_DAYS);
    OverdueFinePolicy overdueFinePolicy = createOverdueFinePolicy(false, false);
    DateTime systemTime = DateTime.now(DateTimeZone.UTC);
    Loan loan =  new LoanBuilder()
      .withDueDate(systemTime.minusMinutes(10))
      .withCheckoutServicePointId(UUID.fromString(CASE_ERROR_400_SERVICE_ID))
      .asDomainObject()
      .withLoanPolicy(loanPolicy)
      .withOverdueFinePolicy(overdueFinePolicy);

    CompletableFuture<Result<Integer>> future = countMinutes(loan, systemTime, clients);
    Result<Integer> result = future.get();
    assertTrue(result.failed());
    assertTrue(result.cause() instanceof BadRequestFailure);
  }

  @Test
  public void shouldNotCountClosedWithError404() throws ExecutionException, InterruptedException {
    LoanPolicy loanPolicy = createLoanPolicy(0, INTERVAL_DAYS);
    OverdueFinePolicy overdueFinePolicy = createOverdueFinePolicy(false, false);
    DateTime systemTime = DateTime.now(DateTimeZone.UTC);
    Loan loan =  new LoanBuilder()
      .withDueDate(systemTime.minusMinutes(10))
      .withCheckoutServicePointId(UUID.fromString(CASE_ERROR_404_SERVICE_ID))
      .asDomainObject()
      .withLoanPolicy(loanPolicy)
      .withOverdueFinePolicy(overdueFinePolicy);

    CompletableFuture<Result<Integer>> future = countMinutes(loan, systemTime, clients);
    Result<Integer> result = future.get();
    assertTrue(result.failed());
    assertTrue(result.cause() instanceof RecordNotFoundFailure);
  }

  @Test
  public void shouldNotCountClosedWithError500() throws ExecutionException, InterruptedException {
    LoanPolicy loanPolicy = createLoanPolicy(0, INTERVAL_DAYS);
    OverdueFinePolicy overdueFinePolicy = createOverdueFinePolicy(false, false);
    DateTime systemTime = DateTime.now(DateTimeZone.UTC);
    Loan loan =  new LoanBuilder()
      .withDueDate(systemTime.minusMinutes(10))
      .withCheckoutServicePointId(UUID.fromString(CASE_ERROR_500_SERVICE_ID))
      .asDomainObject()
      .withLoanPolicy(loanPolicy)
      .withOverdueFinePolicy(overdueFinePolicy);

    CompletableFuture<Result<Integer>> future = countMinutes(loan, systemTime, clients);
    Result<Integer> result = future.get();
    assertTrue(result.failed());
    assertTrue(result.cause() instanceof ServerErrorFailure);
  }

  private LoanPolicy createLoanPolicy(Integer gracePeriodDuration, String gracePeriodInterval) {
    LoanPolicyBuilder builder = new LoanPolicyBuilder();
    if (ObjectUtils.allNotNull(gracePeriodDuration, gracePeriodInterval)) {
      Period gracePeriod = Period.from(gracePeriodDuration, gracePeriodInterval);
      builder = builder.withGracePeriod(gracePeriod);
    }
    return LoanPolicy.from(builder.create());
  }

  private OverdueFinePolicy createOverdueFinePolicy(boolean gracePeriodRecall, boolean countClosed) {
    JsonObject json = new OverdueFinePolicyBuilder()
      .withGracePeriodRecall(gracePeriodRecall)
      .withCountClosed(countClosed)
      .create();

    return OverdueFinePolicy.from(json);
  }

  private static String patternFor(String servicePointId) {
    return String.format(PERIODS_REQUEST_PARAMS_TEMPLATE, servicePointId);
  }
}
