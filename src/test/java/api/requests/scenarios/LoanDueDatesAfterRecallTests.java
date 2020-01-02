package api.requests.scenarios;

import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_SERVICE_POINT_NEXT_DAY;
import static api.support.fixtures.ConfigurationExample.timezoneConfigurationFor;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static java.lang.Boolean.TRUE;
import static org.folio.circulation.domain.policy.DueDateManagement.KEEP_THE_CURRENT_DUE_DATE;
import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.END_OF_A_DAY;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import java.net.MalformedURLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.domain.policy.DueDateManagement;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalTime;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import api.support.APITests;
import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.RequestBuilder;
import api.support.builders.ServicePointBuilder;
import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.converters.Nullable;
import junitparams.naming.TestCaseName;

/**
 * Notes:<br>
 *  MGD = Minimum guaranteed due date<br>
 *  RD = Recall due date<br>
 *
 * @see <a href="https://issues.folio.org/browse/CIRC-203">CIRC-203</a>
 */
@RunWith(JUnitParamsRunner.class)
public class LoanDueDatesAfterRecallTests extends APITests {
  private static Clock clock;

  @BeforeClass
  public static void setUpBeforeClass() {
    clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
  }

  @Before
  public void setUp() {
    // reset the clock before each test (just in case)
    ClockManager.getClockManager().setClock(clock);
  }

  @After
  public void after() {
    // reset the clock before each test (just in case)
    ClockManager.getClockManager().setClock(Clock.systemUTC());
  }

  @Test
  public void recallRequestWithNoPolicyValuesChangesDueDateToSystemDate() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestServicePoint = servicePointsFixture.cd1();
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource loan = loansFixture.checkOutByBarcode(
      smallAngryPlanet, steve, DateTime.now(DateTimeZone.UTC));

    final String originalDueDate = loan.getJson().getString("dueDate");

    requestsFixture.placeHoldShelfRequest(smallAngryPlanet, jessica,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Recall");

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date should not be the original due date",
        storedLoan.getString("dueDate"), not(originalDueDate));

    final String expectedDueDate = ClockManager.getClockManager().getDateTime().toString(ISODateTimeFormat.dateTime());
    assertThat("due date should be the current system date",
        storedLoan.getString("dueDate"), is(expectedDueDate));
  }

  @Test
  public void recallRequestWithMGDAndRDValuesChangesDueDateToRD()
      throws InterruptedException,
      ExecutionException,
      TimeoutException {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestServicePoint = servicePointsFixture.cd1();
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource jessica = usersFixture.jessica();

    final LoanPolicyBuilder canCirculateRollingPolicy = new LoanPolicyBuilder()
        .withName("Can Circulate Rolling With Recalls")
        .withDescription("Can circulate item With Recalls")
        .rolling(Period.weeks(3))
        .unlimitedRenewals()
        .renewFromSystemDate()
        .withRecallsMinimumGuaranteedLoanPeriod(Period.weeks(2))
        .withRecallsRecallReturnInterval(Period.months(2));

    setFallbackPolicies(canCirculateRollingPolicy);

    final IndividualResource loan = loansFixture.checkOutByBarcode(
      smallAngryPlanet, steve, DateTime.now(DateTimeZone.UTC));

    final String originalDueDate = loan.getJson().getString("dueDate");

    requestsFixture.placeHoldShelfRequest(smallAngryPlanet, jessica,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Recall");

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date should not be the original date",
        storedLoan.getString("dueDate"), not(originalDueDate));

    final String expectedDueDate = ClockManager.getClockManager().getDateTime().plusMonths(2).toString(ISODateTimeFormat.dateTime());
    assertThat("due date should be in 2 months",
        storedLoan.getString("dueDate"), is(expectedDueDate));
  }

  @Test
  public void recallRequestWithMGDAndRDValuesChangesDueDateToMGD()
      throws InterruptedException,
      ExecutionException,
      TimeoutException {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestServicePoint = servicePointsFixture.cd1();
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource jessica = usersFixture.jessica();

    final LoanPolicyBuilder canCirculateRollingPolicy = new LoanPolicyBuilder()
        .withName("Can Circulate Rolling With Recalls")
        .withDescription("Can circulate item With Recalls")
        .rolling(Period.weeks(3))
        .unlimitedRenewals()
        .renewFromSystemDate()
        .withRecallsMinimumGuaranteedLoanPeriod(Period.weeks(2))
        .withRecallsRecallReturnInterval(Period.weeks(1));

    setFallbackPolicies(canCirculateRollingPolicy);

    // We use the loan date to calculate the MGD
    final DateTime loanDate = DateTime.now(DateTimeZone.UTC);

    final IndividualResource loan = loansFixture.checkOutByBarcode(
      smallAngryPlanet, steve, loanDate);

    final String originalDueDate = loan.getJson().getString("dueDate");

    requestsFixture.placeHoldShelfRequest(smallAngryPlanet, jessica,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Recall");

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date should not be the original due date",
        storedLoan.getString("dueDate"), not(originalDueDate));

    final String expectedDueDate = loanDate.plusWeeks(2).toString(ISODateTimeFormat.dateTime());
    assertThat("due date should be in 2 weeks (minumum guaranteed loan period)",
        storedLoan.getString("dueDate"), is(expectedDueDate));
  }

  @Test
  public void recallRequestWithRDAndNoMGDValuesChangesDueDateToRD()
      throws InterruptedException,
      ExecutionException,
      TimeoutException {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestServicePoint = servicePointsFixture.cd1();
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource jessica = usersFixture.jessica();

    final LoanPolicyBuilder canCirculateRollingPolicy = new LoanPolicyBuilder()
        .withName("Can Circulate Rolling With Recalls")
        .withDescription("Can circulate item With Recalls")
        .rolling(Period.weeks(3))
        .unlimitedRenewals()
        .renewFromSystemDate()
        .withRecallsRecallReturnInterval(Period.weeks(1));

    setFallbackPolicies(canCirculateRollingPolicy);

    // We use the loan date to calculate the minimum guaranteed due date (MGD)
    final DateTime loanDate = DateTime.now(DateTimeZone.UTC);

    final IndividualResource loan = loansFixture.checkOutByBarcode(
      smallAngryPlanet, steve, loanDate);

    final String originalDueDate = loan.getJson().getString("dueDate");

    requestsFixture.placeHoldShelfRequest(smallAngryPlanet, jessica,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Recall");

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date should not be the original due date",
        storedLoan.getString("dueDate"), not(originalDueDate));

    final String expectedDueDate = ClockManager.getClockManager().getDateTime().plusWeeks(1).toString(ISODateTimeFormat.dateTime());
    assertThat("due date should be in 1 week (recall return interval)",
        storedLoan.getString("dueDate"), is(expectedDueDate));
  }

  @Test
  public void recallRequestWithMGDAndNoRDValuesChangesDueDateToMGD()
      throws InterruptedException,
      ExecutionException,
      TimeoutException {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestServicePoint = servicePointsFixture.cd1();
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource jessica = usersFixture.jessica();

    final LoanPolicyBuilder canCirculateRollingPolicy = new LoanPolicyBuilder()
        .withName("Can Circulate Rolling With Recalls")
        .withDescription("Can circulate item With Recalls")
        .rolling(Period.weeks(3))
        .unlimitedRenewals()
        .renewFromSystemDate()
        .withRecallsMinimumGuaranteedLoanPeriod(Period.weeks(2));

    setFallbackPolicies(canCirculateRollingPolicy);

    // We use the loan date to calculate the minimum guaranteed due date (MGD)
    final DateTime loanDate = DateTime.now(DateTimeZone.UTC);

    final IndividualResource loan = loansFixture.checkOutByBarcode(
      smallAngryPlanet, steve, loanDate);

    final String originalDueDate = loan.getJson().getString("dueDate");

    requestsFixture.placeHoldShelfRequest(smallAngryPlanet, jessica,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Recall");

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date sholud not be the original due date",
        storedLoan.getString("dueDate"), not(originalDueDate));

    final String expectedDueDate = loanDate.plusWeeks(2).toString(ISODateTimeFormat.dateTime());
    assertThat("due date should be in 2 weeks (minimum guaranteed loan period)",
        storedLoan.getString("dueDate"), is(expectedDueDate));
  }

  @Test
  public void recallRequestWithMGDAndRDValuesChangesDueDateToMGDWithCLDDM()
      throws InterruptedException,
      ExecutionException,
      TimeoutException {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final UUID checkOutServicePointId = UUID.fromString(CASE_FRI_SAT_MON_SERVICE_POINT_ID);
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource jessica = usersFixture.jessica();

    final LoanPolicyBuilder canCirculateRollingPolicy = new LoanPolicyBuilder()
        .withName("Can Circulate Rolling With Recalls")
        .withDescription("Can circulate item With Recalls")
        .rolling(Period.weeks(3))
        .unlimitedRenewals()
        .renewFromSystemDate()
        .withRecallsMinimumGuaranteedLoanPeriod(Period.days(8))
        .withRecallsRecallReturnInterval(Period.weeks(1))
        .withClosedLibraryDueDateManagement(DueDateManagement.MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY.getValue());

    setFallbackPolicies(canCirculateRollingPolicy);

    servicePointsFixture.create(new ServicePointBuilder(checkOutServicePointId, "CLDDM Desk", "clddm", "CLDDM Desk Test", null, null, TRUE, null));

    // We use the loan date to calculate the minimum guaranteed due date (MGD)
    final DateTime loanDate =
        new DateTime(2019, DateTimeConstants.JANUARY, 25, 10, 0, DateTimeZone.UTC);

    mockClockManagerToReturnFixedDateTime(loanDate);

    final IndividualResource loan = loansFixture.createLoan(new LoanBuilder()
        .open()
        .withItemId(smallAngryPlanet.getId())
        .withUserId(steve.getId())
        .withLoanDate(loanDate)
        .withDueDate(loanDate.plusWeeks(3))
        .withCheckoutServicePointId(checkOutServicePointId));

    final String originalDueDate = loan.getJson().getString("dueDate");

    requestsFixture.placeHoldShelfRequest(smallAngryPlanet, jessica,
        DateTime.now(DateTimeZone.UTC), "Recall");

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date should not be the original due date",
        storedLoan.getString("dueDate"), not(originalDueDate));

    final String expectedDueDate =
        CASE_FRI_SAT_MON_SERVICE_POINT_NEXT_DAY
          .toDateTime(END_OF_A_DAY, DateTimeZone.UTC).toString(ISODateTimeFormat.dateTime());

    assertThat("due date should be moved to Monday",
        storedLoan.getString("dueDate"), is(expectedDueDate));
  }

  @Test
  @Parameters({
    // MGD duration|MGD interval|RD duration|RD interval|expected string
    "null|null|1|Months|the \"minimumGuaranteedLoanPeriod\" in the loan policy is not recognized",
    "1|Months|null|null|the \"recallReturnInterval\" in the loan policy is not recognized",
    "1|Years|1|Months|the interval \"Years\" in \"minimumGuaranteedLoanPeriod\" is not recognized",
    "1|Months|1|Years|the interval \"Years\" in \"recallReturnInterval\" is not recognized",
    "-100|Months|1|Months|the duration \"-100\" in \"minimumGuaranteedLoanPeriod\" is invalid",
    "1|Months|-100|Months|the duration \"-100\" in \"recallReturnInterval\" is invalid"
  })
  @TestCaseName("{method}: {params}")
  public void loanPolicyWithInvalidMGDOrRDPeriodValuesReturnsErrorOnRecallCreation(
      @Nullable Integer mgdDuration,
      @Nullable String mgdInterval,
      @Nullable Integer rdDuration,
      @Nullable String rdInterval,
      String expectedMessage)
          throws InterruptedException,
          ExecutionException,
          TimeoutException {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestServicePoint = servicePointsFixture.cd1();
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource jessica = usersFixture.jessica();

    final LoanPolicyBuilder canCirculateRollingPolicy = new LoanPolicyBuilder()
        .withName("Can Circulate Rolling With Recalls")
        .withDescription("Can circulate item With Recalls")
        .rolling(Period.weeks(3))
        .unlimitedRenewals()
        .renewFromSystemDate()
        .withRecallsMinimumGuaranteedLoanPeriod(Period.from(mgdDuration, mgdInterval))
        .withRecallsRecallReturnInterval(Period.from(rdDuration, rdInterval));

    setFallbackPolicies(canCirculateRollingPolicy);

    // We use the loan date to calculate the minimum guaranteed due date (MGD)
    final DateTime loanDate = DateTime.now(DateTimeZone.UTC);

    loansFixture.checkOutByBarcode(smallAngryPlanet, steve, loanDate);

    final Response response = requestsFixture.attemptPlaceHoldShelfRequest(smallAngryPlanet, jessica,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Recall");

    assertThat("Status code should be 422", response.getStatusCode(), is(422));
    assertThat("errors should be present", response.getJson().getJsonArray("errors"), notNullValue());
    assertThat("errors should be size 1", response.getJson().getJsonArray("errors").size(), is(1));
    assertThat("first error should have the expected message field",
        response.getJson().getJsonArray("errors").getJsonObject(0).getString("message"),
        is(expectedMessage));
  }

  @Test
  public void initialLoanDueDateOnCreateWithPrexistingRequests()
      throws
    InterruptedException,
      TimeoutException,
      ExecutionException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestServicePoint = servicePointsFixture.cd1();
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource jessica = usersFixture.jessica();
    final IndividualResource charlotte = usersFixture.charlotte();

    final LoanPolicyBuilder canCirculateRollingPolicy = new LoanPolicyBuilder()
        .withName("Can Circulate Rolling With Recalls")
        .withDescription("Can circulate item With Recalls")
        .rolling(Period.weeks(3))
        .unlimitedRenewals()
        .renewFromSystemDate()
        .withClosedLibraryDueDateManagement(KEEP_THE_CURRENT_DUE_DATE.getValue())
        .withRecallsMinimumGuaranteedLoanPeriod(Period.weeks(2))
        .withRecallsRecallReturnInterval(Period.months(2));

    setFallbackPolicies(canCirculateRollingPolicy);

    loansFixture.checkOutByBarcode(smallAngryPlanet, charlotte,
      DateTime.now(DateTimeZone.UTC));

    requestsFixture.placeHoldShelfRequest(smallAngryPlanet, steve,
      DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Recall");

    requestsFixture.placeHoldShelfRequest(smallAngryPlanet, jessica,
      DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Recall");

    loansFixture.checkInByBarcode(smallAngryPlanet);

    final IndividualResource loan = loansFixture.checkOutByBarcode(
      smallAngryPlanet, steve, DateTime.now(DateTimeZone.UTC));

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    final String expectedDueDate = ClockManager
      .getClockManager()
      .getDateTime()
      .plusMonths(2)
      .withTime(LocalTime.MIDNIGHT.minusSeconds(1))
      .toString(ISODateTimeFormat.dateTime());

    assertThat("due date should be in 2 months (recall return interval)",
        storedLoan.getString("dueDate"), is(expectedDueDate));
  }

  @Test
  public void changedDueDateAfterRecallingAnItemShouldRespectTenantTimezone()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    final String stockholmTimeZone = "Europe/Stockholm";

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestServicePoint = servicePointsFixture.cd1();
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource jessica = usersFixture.jessica();

    configClient.create(timezoneConfigurationFor(stockholmTimeZone));

    final LoanPolicyBuilder canCirculateRollingPolicy = new LoanPolicyBuilder()
      .withName("Can Circulate Rolling With Recalls")
      .withDescription("Can circulate item With Recalls")
      .rolling(Period.days(14))
      .unlimitedRenewals()
      .renewFromSystemDate()
      .withRecallsMinimumGuaranteedLoanPeriod(Period.days(5))
      .withClosedLibraryDueDateManagement(KEEP_THE_CURRENT_DUE_DATE.getValue());

    setFallbackPolicies(canCirculateRollingPolicy);

    final DateTime loanDate = DateTime.now(DateTimeZone.UTC).minusDays(3);

    final IndividualResource loan = loansFixture.checkOutByBarcode(
      smallAngryPlanet, steve, loanDate);

    final String originalDueDate = loan.getJson().getString("dueDate");

    final DateTime requestDate = DateTime.now(DateTimeZone.UTC);

    mockClockManagerToReturnFixedDateTime(requestDate);

    requestsFixture.place(
      new RequestBuilder()
        .recall()
        .forItem(smallAngryPlanet)
        .by(jessica)
        .withRequestDate(requestDate)
        .withPickupServicePoint(requestServicePoint));

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date should not be the original due date",
      storedLoan.getString("dueDate"), not(originalDueDate));

    final DateTime expectedDueDate = loanDate.toLocalDate()
      .toDateTime(END_OF_A_DAY, DateTimeZone.forID(stockholmTimeZone))
      .plusDays(5);

    assertThat("due date should be end of the day, 5 days from loan date",
      storedLoan.getString("dueDate"), isEquivalentTo(expectedDueDate));
  }

  @Test
  public void pagedItemRecalledThenLoanedAndNextRecallDoesNotChangeDueDate()
      throws InterruptedException,
      ExecutionException,
      TimeoutException {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestServicePoint = servicePointsFixture.cd1();
    final IndividualResource jessica = usersFixture.jessica();
    final IndividualResource charlotte = usersFixture.charlotte();
    final IndividualResource james = usersFixture.james();

    final LoanPolicyBuilder canCirculateRollingPolicy = new LoanPolicyBuilder()
        .withName("Can Circulate Rolling With Recalls")
        .withDescription("Can circulate item With Recalls")
        .rolling(Period.weeks(3))
        .unlimitedRenewals()
        .renewFromSystemDate()
        .withRecallsMinimumGuaranteedLoanPeriod(Period.weeks(2))
        .withRecallsRecallReturnInterval(Period.weeks(1));

    setFallbackPolicies(canCirculateRollingPolicy);

    requestsFixture.placeHoldShelfRequest(smallAngryPlanet, jessica,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Page");

    requestsFixture.placeHoldShelfRequest(smallAngryPlanet, james,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Recall");

    final IndividualResource loan = loansFixture.checkOutByBarcode(
      smallAngryPlanet, jessica, ClockManager.getClockManager().getDateTime());

    // Recalled is applied when loaned, so the due date should be 2 weeks, not 3 weeks
    final String expectedDueDate = ClockManager.getClockManager().getDateTime().plusWeeks(2).toString(ISODateTimeFormat.dateTime());

    JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    final String recalledDueDate = storedLoan.getString("dueDate");
    assertThat("due date after recall should be 2 weeks",
        recalledDueDate, is(expectedDueDate));

    ClockManager.getClockManager().setClock(Clock.offset(clock, Duration.ofDays(1)));

    requestsFixture.placeHoldShelfRequest(smallAngryPlanet, charlotte,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Recall");

    storedLoan = loansStorageClient.getById(loan.getId()).getJson();
    assertThat("second recall should not change the due date",
        storedLoan.getString("dueDate"), is(recalledDueDate));
  }

  @Test
  public void pagedItemRecalledThenLoanedBecomesOverdueAndNextRecallDoesNotChangeDueDate()
      throws InterruptedException,
      ExecutionException,
      TimeoutException {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestServicePoint = servicePointsFixture.cd1();
    final IndividualResource jessica = usersFixture.jessica();
    final IndividualResource charlotte = usersFixture.charlotte();
    final IndividualResource james = usersFixture.james();

    final LoanPolicyBuilder canCirculateRollingPolicy = new LoanPolicyBuilder()
        .withName("Can Circulate Rolling With Recalls")
        .withDescription("Can circulate item With Recalls")
        .rolling(Period.weeks(3))
        .unlimitedRenewals()
        .renewFromSystemDate()
        .withRecallsMinimumGuaranteedLoanPeriod(Period.weeks(2))
        .withRecallsRecallReturnInterval(Period.weeks(1));

     setFallbackPolicies(canCirculateRollingPolicy);

    requestsFixture.placeHoldShelfRequest(smallAngryPlanet, jessica,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Page");

    requestsFixture.placeHoldShelfRequest(smallAngryPlanet, james,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Recall");

    final IndividualResource loan = loansFixture.checkOutByBarcode(
      smallAngryPlanet, jessica, ClockManager.getClockManager().getDateTime());

    // Recalled is applied when loaned, so the due date should be 2 weeks, not 3 weeks
    final String expectedDueDate = ClockManager.getClockManager().getDateTime().plusWeeks(2).toString(ISODateTimeFormat.dateTime());

    JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    final String recalledDueDate = storedLoan.getString("dueDate");
    assertThat("due date after recall should be 2 weeks",
        recalledDueDate, is(expectedDueDate));

    // Move the fixed clock so that the loan is now overdue
    ClockManager.getClockManager().setClock(Clock.offset(clock, Duration.ofDays(15)));

    requestsFixture.placeHoldShelfRequest(smallAngryPlanet, charlotte,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Recall");

    storedLoan = loansStorageClient.getById(loan.getId()).getJson();
    assertThat("second recall should not change the due date",
        storedLoan.getString("dueDate"), is(recalledDueDate));
  }

  @Test
  public void secondRecallRequestWithMGDTruncationInPlaceDoesNotChangeDueDate()
      throws InterruptedException,
      ExecutionException,
      TimeoutException {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestServicePoint = servicePointsFixture.cd1();
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource jessica = usersFixture.jessica();
    final IndividualResource charlotte = usersFixture.charlotte();

    final LoanPolicyBuilder canCirculateRollingPolicy = new LoanPolicyBuilder()
        .withName("Can Circulate Rolling With Recalls")
        .withDescription("Can circulate item With Recalls")
        .rolling(Period.weeks(3))
        .unlimitedRenewals()
        .renewFromSystemDate()
        .withRecallsMinimumGuaranteedLoanPeriod(Period.weeks(2))
        .withRecallsRecallReturnInterval(Period.weeks(1));

    setFallbackPolicies(canCirculateRollingPolicy);

    final IndividualResource loan = loansFixture.checkOutByBarcode(
      smallAngryPlanet, steve, ClockManager.getClockManager().getDateTime());

    final String originalDueDate = loan.getJson().getString("dueDate");

    requestsFixture.placeHoldShelfRequest(smallAngryPlanet, jessica,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Recall");

    JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    final String recalledDueDate = storedLoan.getString("dueDate");
    assertThat("due date after recall should not be the original date",
        recalledDueDate, not(originalDueDate));

    final String expectedDueDate = ClockManager.getClockManager().getDateTime().plusWeeks(2).toString(ISODateTimeFormat.dateTime());
    assertThat("due date after recall should be in 2 weeks",
        storedLoan.getString("dueDate"), is(expectedDueDate));

    ClockManager.getClockManager().setClock(Clock.offset(clock, Duration.ofDays(7)));

    requestsFixture.placeHoldShelfRequest(smallAngryPlanet, charlotte,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Recall");

    storedLoan = loansStorageClient.getById(loan.getId()).getJson();
    assertThat("second recall should not change the due date (2 weeks)",
        storedLoan.getString("dueDate"), is(recalledDueDate));
  }

  @Test
  public void secondRecallRequestWithMGDTruncationInPlaceAndLoanOverdueDoesNotChangeDueDate()
      throws InterruptedException,
      ExecutionException,
      TimeoutException {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestServicePoint = servicePointsFixture.cd1();
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource jessica = usersFixture.jessica();
    final IndividualResource charlotte = usersFixture.charlotte();

    final LoanPolicyBuilder canCirculateRollingPolicy = new LoanPolicyBuilder()
        .withName("Can Circulate Rolling With Recalls")
        .withDescription("Can circulate item With Recalls")
        .rolling(Period.weeks(3))
        .unlimitedRenewals()
        .renewFromSystemDate()
        .withRecallsMinimumGuaranteedLoanPeriod(Period.weeks(2))
        .withRecallsRecallReturnInterval(Period.weeks(1));

    setFallbackPolicies(canCirculateRollingPolicy);

    final IndividualResource loan = loansFixture.checkOutByBarcode(
      smallAngryPlanet, steve, ClockManager.getClockManager().getDateTime());

    final String originalDueDate = loan.getJson().getString("dueDate");

    requestsFixture.placeHoldShelfRequest(smallAngryPlanet, jessica,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Recall");

    JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    final String recalledDueDate = storedLoan.getString("dueDate");
    assertThat("due date after recall should not be the original date",
        recalledDueDate, not(originalDueDate));

    final String expectedDueDate = ClockManager.getClockManager().getDateTime().plusWeeks(2).toString(ISODateTimeFormat.dateTime());
    assertThat("due date after recall should be in 2 weeks",
        storedLoan.getString("dueDate"), is(expectedDueDate));

    // Move the fixed clock so that the loan is now overdue
    ClockManager.getClockManager().setClock(Clock.offset(clock, Duration.ofDays(15)));

    requestsFixture.placeHoldShelfRequest(smallAngryPlanet, charlotte,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Recall");

    storedLoan = loansStorageClient.getById(loan.getId()).getJson();
    assertThat("second recall should not change the due date (2 weeks)",
        storedLoan.getString("dueDate"), is(recalledDueDate));
  }

  @Test
  public void secondRecallRequestWithRDTruncationInPlaceDoesNotChangeDueDate()
      throws InterruptedException,
      ExecutionException,
      TimeoutException {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestServicePoint = servicePointsFixture.cd1();
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource jessica = usersFixture.jessica();
    final IndividualResource charlotte = usersFixture.charlotte();

    final LoanPolicyBuilder canCirculateRollingPolicy = new LoanPolicyBuilder()
        .withName("Can Circulate Rolling With Recalls")
        .withDescription("Can circulate item With Recalls")
        .rolling(Period.weeks(3))
        .unlimitedRenewals()
        .renewFromSystemDate()
        .withRecallsRecallReturnInterval(Period.months(2));

    setFallbackPolicies(canCirculateRollingPolicy);

    final IndividualResource loan = loansFixture.checkOutByBarcode(
      smallAngryPlanet, steve, DateTime.now(DateTimeZone.UTC));

    final String originalDueDate = loan.getJson().getString("dueDate");

    requestsFixture.placeHoldShelfRequest(smallAngryPlanet, jessica,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Recall");

    JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    final String recalledDueDate = storedLoan.getString("dueDate");
    assertThat("due date after recall should not be  the original date",
        recalledDueDate, not(originalDueDate));

    final String expectedDueDate = ClockManager.getClockManager().getDateTime().plusMonths(2).toString(ISODateTimeFormat.dateTime());
    assertThat("due date after recall should be in 2 months",
        storedLoan.getString("dueDate"), is(expectedDueDate));

    ClockManager.getClockManager().setClock(Clock.offset(clock, Duration.ofDays(7)));

    requestsFixture.placeHoldShelfRequest(smallAngryPlanet, charlotte,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Recall");

    storedLoan = loansStorageClient.getById(loan.getId()).getJson();
    assertThat("second recall should not change the due date (2 months)",
        storedLoan.getString("dueDate"), is(recalledDueDate));
  }

  @Test
  public void secondRecallRequestWithRDTruncationInPlaceAndLoanOverdueDoesNotChangeDueDate()
      throws InterruptedException,
      ExecutionException,
      TimeoutException {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestServicePoint = servicePointsFixture.cd1();
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource jessica = usersFixture.jessica();
    final IndividualResource charlotte = usersFixture.charlotte();

    final LoanPolicyBuilder canCirculateRollingPolicy = new LoanPolicyBuilder()
        .withName("Can Circulate Rolling With Recalls")
        .withDescription("Can circulate item With Recalls")
        .rolling(Period.weeks(3))
        .unlimitedRenewals()
        .renewFromSystemDate()
        .withRecallsMinimumGuaranteedLoanPeriod(Period.weeks(2))
        .withRecallsRecallReturnInterval(Period.months(2));

    setFallbackPolicies(canCirculateRollingPolicy);

    final IndividualResource loan = loansFixture.checkOutByBarcode(
      smallAngryPlanet, steve, DateTime.now(DateTimeZone.UTC));

    final String originalDueDate = loan.getJson().getString("dueDate");

    requestsFixture.placeHoldShelfRequest(smallAngryPlanet, jessica,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Recall");

    JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    final String recalledDueDate = storedLoan.getString("dueDate");
    assertThat("due date after recall should not be the original date",
        recalledDueDate, not(originalDueDate));

    final String expectedDueDate = ClockManager.getClockManager().getDateTime().plusMonths(2).toString(ISODateTimeFormat.dateTime());
    assertThat("due date after recall should be in 2 months",
        storedLoan.getString("dueDate"), is(expectedDueDate));

    // Move the fixed clock so that the loan is now overdue
    ClockManager.getClockManager().setClock(Clock.offset(clock, Duration.ofDays(70)));

    requestsFixture.placeHoldShelfRequest(smallAngryPlanet, charlotte,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Recall");

    storedLoan = loansStorageClient.getById(loan.getId()).getJson();
    assertThat("second recall should not change the due date (2 months)",
        storedLoan.getString("dueDate"), is(recalledDueDate));
  }

  @Test
  public void itemRecalledThenCancelledAndNextRecallDoesNotChangeDueDate()
      throws InterruptedException,
      ExecutionException,
      TimeoutException {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestServicePoint = servicePointsFixture.cd1();
    final IndividualResource jessica = usersFixture.jessica();
    final IndividualResource charlotte = usersFixture.charlotte();
    final IndividualResource james = usersFixture.james();

    final LoanPolicyBuilder canCirculateRollingPolicy = new LoanPolicyBuilder()
        .withName("Can Circulate Rolling With Recalls")
        .withDescription("Can circulate item With Recalls")
        .rolling(Period.weeks(3))
        .unlimitedRenewals()
        .renewFromSystemDate()
        .withRecallsMinimumGuaranteedLoanPeriod(Period.weeks(1))
        .withRecallsRecallReturnInterval(Period.weeks(2));


    setFallbackPolicies(canCirculateRollingPolicy);

    final IndividualResource loan = loansFixture.checkOutByBarcode(
      smallAngryPlanet, jessica, ClockManager.getClockManager().getDateTime());

    final String originalDueDate = loan.getJson().getString("dueDate");

    JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    final IndividualResource request = requestsFixture.placeHoldShelfRequest(
        smallAngryPlanet, james, DateTime.now(DateTimeZone.UTC),
        requestServicePoint.getId(), "Recall");

    final String recalledDueDate = request.getJson().getString("dueDate");
    assertThat("due date after recall should not be the original date",
        recalledDueDate, not(originalDueDate));

    requestsFixture.cancelRequest(request);

    ClockManager.getClockManager().setClock(Clock.offset(clock, Duration.ofDays(7)));

    final IndividualResource renewal = loansFixture.renewLoan(smallAngryPlanet, jessica);

    final String renewalDueDate = renewal.getJson().getString("dueDate");

    storedLoan = loansStorageClient.getById(renewal.getId()).getJson();

    requestsFixture.placeHoldShelfRequest(smallAngryPlanet, charlotte,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Recall");

    storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    final String recalledRenewalDueDate = storedLoan.getString("dueDate");
    assertThat("due date after recall should not change the renewal due date",
        recalledRenewalDueDate, is(renewalDueDate));
  }
}
