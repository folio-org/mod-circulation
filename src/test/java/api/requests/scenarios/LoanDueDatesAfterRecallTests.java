package api.requests.scenarios;

import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_SERVICE_POINT_NEXT_DAY;
import static java.lang.Boolean.TRUE;
import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.END_OF_A_DAY;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import java.net.MalformedURLException;
import java.time.Clock;
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
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import api.support.APITests;
import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;
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
  public static void setUpBeforeClass() throws Exception {
    clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
  }

  @Before
  public void setUp() throws Exception {
    // reset the clock before each test (just in case)
    ClockManager.getClockManager().setClock(clock);
  }

  @Test
  public void recallRequestWithNoPolicyValuesChangesDueDateToSystemDate()
      throws InterruptedException,
      ExecutionException,
      TimeoutException,
      MalformedURLException {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestServicePoint = servicePointsFixture.cd1();
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource loan = loansFixture.checkOut(smallAngryPlanet, steve, DateTime.now(DateTimeZone.UTC));

    final String originalDueDate = loan.getJson().getString("dueDate");

    requestsFixture.placeHoldShelfRequest(smallAngryPlanet, jessica,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Recall");

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date is the original date",
        storedLoan.getString("dueDate"), not(originalDueDate));

    final String expectedDueDate = ClockManager.getClockManager().getDateTime().toString(ISODateTimeFormat.dateTime());
    assertThat("due date is not the current date",
        storedLoan.getString("dueDate"), is(expectedDueDate));
  }

  @Test
  public void recallRequestWithMGDAndRDValuesChangesDueDateToRD()
      throws InterruptedException,
      ExecutionException,
      TimeoutException,
      MalformedURLException {
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

    final IndividualResource loanPolicy = loanPoliciesFixture.create(canCirculateRollingPolicy);

    useLoanPolicyAsFallback(loanPolicy.getId(),
        requestPoliciesFixture.noAllowedTypes().getId(),
        noticePoliciesFixture.activeNotice().getId());

    final IndividualResource loan = loansFixture.checkOut(smallAngryPlanet, steve, DateTime.now(DateTimeZone.UTC));

    final String originalDueDate = loan.getJson().getString("dueDate");

    requestsFixture.placeHoldShelfRequest(smallAngryPlanet, jessica,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Recall");

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date is the original date",
        storedLoan.getString("dueDate"), not(originalDueDate));

    final String expectedDueDate = ClockManager.getClockManager().getDateTime().plusMonths(2).toString(ISODateTimeFormat.dateTime());
    assertThat("due date is not the recall due date (2 months)",
        storedLoan.getString("dueDate"), is(expectedDueDate));
  }

  @Test
  public void recallRequestWithMGDAndRDValuesChangesDueDateToMGD()
      throws InterruptedException,
      ExecutionException,
      TimeoutException,
      MalformedURLException {
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

    final IndividualResource loanPolicy = loanPoliciesFixture.create(canCirculateRollingPolicy);

    useLoanPolicyAsFallback(loanPolicy.getId(),
        requestPoliciesFixture.noAllowedTypes().getId(),
        noticePoliciesFixture.activeNotice().getId());

    // We use the loan date to calculate the MGD
    final DateTime loanDate = DateTime.now(DateTimeZone.UTC);

    final IndividualResource loan =
        loansFixture.checkOut(smallAngryPlanet, steve, loanDate);

    final String originalDueDate = loan.getJson().getString("dueDate");

    requestsFixture.placeHoldShelfRequest(smallAngryPlanet, jessica,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Recall");

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date is the original date",
        storedLoan.getString("dueDate"), not(originalDueDate));

    final String expectedDueDate = loanDate.plusWeeks(2).toString(ISODateTimeFormat.dateTime());
    assertThat("due date is not the minimum guaranteeded due date (2 weeks)",
        storedLoan.getString("dueDate"), is(expectedDueDate));
  }

  @Test
  public void recallRequestWithRDAndNoMGDValuesChangesDueDateToRD()
      throws InterruptedException,
      ExecutionException,
      TimeoutException,
      MalformedURLException {
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

    final IndividualResource loanPolicy = loanPoliciesFixture.create(canCirculateRollingPolicy);

    useLoanPolicyAsFallback(loanPolicy.getId(),
        requestPoliciesFixture.noAllowedTypes().getId(),
        noticePoliciesFixture.activeNotice().getId());

    // We use the loan date to calculate the minimum guaranteed due date (MGD)
    final DateTime loanDate = DateTime.now(DateTimeZone.UTC);

    final IndividualResource loan =
        loansFixture.checkOut(smallAngryPlanet, steve, loanDate);

    final String originalDueDate = loan.getJson().getString("dueDate");

    requestsFixture.placeHoldShelfRequest(smallAngryPlanet, jessica,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Recall");

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date is the original date",
        storedLoan.getString("dueDate"), not(originalDueDate));

    final String expectedDueDate = ClockManager.getClockManager().getDateTime().plusWeeks(1).toString(ISODateTimeFormat.dateTime());
    assertThat("due date is not the recall due date (1 week)",
        storedLoan.getString("dueDate"), is(expectedDueDate));
  }

  @Test
  public void recallRequestWithMGDAndNoRDValuesChangesDueDateToMGD()
      throws InterruptedException,
      ExecutionException,
      TimeoutException,
      MalformedURLException {
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

    final IndividualResource loanPolicy = loanPoliciesFixture.create(canCirculateRollingPolicy);

    useLoanPolicyAsFallback(loanPolicy.getId(),
        requestPoliciesFixture.noAllowedTypes().getId(),
        noticePoliciesFixture.activeNotice().getId());

    // We use the loan date to calculate the minimum guaranteed due date (MGD)
    final DateTime loanDate = DateTime.now(DateTimeZone.UTC);

    final IndividualResource loan =
        loansFixture.checkOut(smallAngryPlanet, steve, loanDate);

    final String originalDueDate = loan.getJson().getString("dueDate");

    requestsFixture.placeHoldShelfRequest(smallAngryPlanet, jessica,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Recall");

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date is the original date",
        storedLoan.getString("dueDate"), not(originalDueDate));

    final String expectedDueDate = loanDate.plusWeeks(2).toString(ISODateTimeFormat.dateTime());
    assertThat("due date is not the minimum guaranteed due date (2 weeks)",
        storedLoan.getString("dueDate"), is(expectedDueDate));
  }

  @Test
  public void recallRequestWithMGDAndRDValuesChangesDueDateToMGDWithCLDDM()
      throws InterruptedException,
      ExecutionException,
      TimeoutException,
      MalformedURLException {
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

    final IndividualResource loanPolicy = loanPoliciesFixture.create(canCirculateRollingPolicy);

    useLoanPolicyAsFallback(loanPolicy.getId(),
        requestPoliciesFixture.noAllowedTypes().getId(),
        noticePoliciesFixture.activeNotice().getId());

    servicePointsFixture.create(new ServicePointBuilder(checkOutServicePointId, "CLDDM Desk", "clddm", "CLDDM Desk Test", null, null, TRUE, null));

    // We use the loan date to calculate the minimum guaranteed due date (MGD)
    final DateTime loanDate =
        new DateTime(2019, DateTimeConstants.JANUARY, 25, 10, 0, DateTimeZone.UTC);

    // Update the clock so this will all work
    ClockManager.getClockManager().setClock(Clock.fixed(Instant.ofEpochMilli(loanDate.getMillis()), ZoneOffset.UTC));

    final IndividualResource loan = loansClient.create(new LoanBuilder()
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

    assertThat("due date is the original date",
        storedLoan.getString("dueDate"), not(originalDueDate));

    final String expectedDueDate =
        CASE_FRI_SAT_MON_SERVICE_POINT_NEXT_DAY
          .toDateTime(END_OF_A_DAY, DateTimeZone.UTC).toString(ISODateTimeFormat.dateTime());

    assertThat("due date is not moved to Monday",
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
          TimeoutException,
          MalformedURLException {
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

    final IndividualResource loanPolicy = loanPoliciesFixture.create(canCirculateRollingPolicy);

    useLoanPolicyAsFallback(loanPolicy.getId(),
        requestPoliciesFixture.noAllowedTypes().getId(),
        noticePoliciesFixture.activeNotice().getId());

    // We use the loan date to calculate the minimum guaranteed due date (MGD)
    final DateTime loanDate = DateTime.now(DateTimeZone.UTC);

    loansFixture.checkOut(smallAngryPlanet, steve, loanDate);

    final Response response = requestsFixture.attemptPlaceHoldShelfRequest(smallAngryPlanet, jessica,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Recall");

    assertThat("Status code is not 422", response.getStatusCode(), is(422));
    assertThat("errors is not present", response.getJson().getJsonArray("errors"), notNullValue());
    assertThat("errors is not size 1", response.getJson().getJsonArray("errors").size(), is(1));
    assertThat("first error does not have the expected message field",
        response.getJson().getJsonArray("errors").getJsonObject(0).getString("message"),
        is(expectedMessage));
  }
}
