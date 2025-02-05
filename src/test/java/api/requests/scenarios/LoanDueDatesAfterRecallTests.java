package api.requests.scenarios;

import static api.support.fakes.FakePubSub.getPublishedEventsAsList;
import static api.support.fakes.PublishedEvents.byLogEventType;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_SERVICE_POINT_NEXT_DAY;
import static api.support.fixtures.ConfigurationExample.timezoneConfigurationFor;
import static api.support.http.CqlQuery.queryFromTemplate;
import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.TextDateTimeMatcher.withinSecondsBefore;
import static java.lang.Boolean.TRUE;
import static java.time.Clock.fixed;
import static java.time.Clock.offset;
import static java.time.Duration.ofDays;
import static java.time.ZoneOffset.UTC;
import static org.folio.circulation.domain.policy.DueDateManagement.KEEP_THE_CURRENT_DUE_DATE;
import static org.folio.circulation.domain.representations.logs.LogEventType.LOAN;
import static org.folio.circulation.support.utils.ClockUtil.getClock;
import static org.folio.circulation.support.utils.ClockUtil.getInstant;
import static org.folio.circulation.support.utils.ClockUtil.getZoneId;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.folio.circulation.support.utils.ClockUtil.setClock;
import static org.folio.circulation.support.utils.ClockUtil.setDefaultClock;
import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTime;
import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTimeOptional;
import static org.folio.circulation.support.utils.DateFormatUtil.parseDateTime;
import static org.folio.circulation.support.utils.DateTimeUtil.atEndOfDay;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.folio.circulation.domain.policy.DueDateManagement;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import api.support.APITests;
import api.support.MultipleJsonRecords;
import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.RequestBuilder;
import api.support.builders.ServicePointBuilder;
import api.support.http.IndividualResource;
import io.vertx.core.json.JsonObject;

/**
 * Notes:<br>
 *  MGD = Minimum guaranteed due date<br>
 *  RD = Recall due date<br>
 *
 * @see <a href="https://issues.folio.org/browse/CIRC-203">CIRC-203</a>
 */
class LoanDueDatesAfterRecallTests extends APITests {

  public static final String DUE_DATE = "dueDate";

  public LoanDueDatesAfterRecallTests() {
    super(true, true);
  }

  @BeforeEach
  public void setUp() {
    setClock(Clock.fixed(getInstant(), ZoneOffset.UTC));
  }

  @AfterEach
  public void afterEach() {
    // The clock must be reset after each test.
    setDefaultClock();
  }

  @Test
  void recallRequestWithNoPolicyValuesChangesDueDateToSystemDate() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestServicePoint = servicePointsFixture.cd1();
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(
      smallAngryPlanet, steve, getZonedDateTime());

    final String originalDueDate = loan.getJson().getString("dueDate");

    requestsFixture.placeItemLevelHoldShelfRequest(smallAngryPlanet, jessica,
        getZonedDateTime(), requestServicePoint.getId(), "Recall");

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date should not be the original due date",
        storedLoan.getString("dueDate"), not(originalDueDate));

    final String expectedDueDate = formatDateTime(getZonedDateTime());
    assertThat("due date should be the current system date",
        storedLoan.getString("dueDate"), is(expectedDueDate));
  }

  @Test
  void recallRequestWithMGDAndRDValuesShouldNotChangeDueDateToRD() {
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

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(
      smallAngryPlanet, steve, getZonedDateTime());

    final String originalDueDate = loan.getJson().getString("dueDate");

    requestsFixture.placeItemLevelHoldShelfRequest(smallAngryPlanet, jessica,
        getZonedDateTime(), requestServicePoint.getId(), "Recall");

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date should be the original date", storedLoan.getString("dueDate"),
      is(originalDueDate));

    final String expectedDueDate = formatDateTime(getZonedDateTime().plusWeeks(3));
    assertThat("due date should be in 3 weeks", storedLoan.getString("dueDate"),
      is(expectedDueDate));
  }

  @Test
  void recallRequestWithMGDAndRDValuesChangesDueDateToMGD() {
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
    final ZonedDateTime loanDate = getZonedDateTime();

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(
      smallAngryPlanet, steve, loanDate);

    final String originalDueDate = loan.getJson().getString("dueDate");

    requestsFixture.placeItemLevelHoldShelfRequest(smallAngryPlanet, jessica,
        getZonedDateTime(), requestServicePoint.getId(), "Recall");

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date should not be the original due date",
        storedLoan.getString("dueDate"), not(originalDueDate));

    final String expectedDueDate = formatDateTime(loanDate.plusWeeks(2));
    assertThat("due date should be in 2 weeks (minumum guaranteed loan period)",
        storedLoan.getString("dueDate"), is(expectedDueDate));
  }

  @Test
  void recallRequestWithRDAndNoMGDValuesChangesDueDateToRD() {
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
    final ZonedDateTime loanDate = getZonedDateTime();

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(
      smallAngryPlanet, steve, loanDate);

    final String originalDueDate = loan.getJson().getString("dueDate");

    requestsFixture.placeItemLevelHoldShelfRequest(smallAngryPlanet, jessica,
        getZonedDateTime(), requestServicePoint.getId(), "Recall");

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date should not be the original due date",
        storedLoan.getString("dueDate"), not(originalDueDate));

    final String expectedDueDate = formatDateTime(getZonedDateTime().plusWeeks(1));
    assertThat("due date should be in 1 week (recall return interval)",
        storedLoan.getString("dueDate"), is(expectedDueDate));
  }

  @Test
  void recallRequestWithMGDAndNoRDValuesChangesDueDateToMGD() {
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
    final ZonedDateTime loanDate = getZonedDateTime();

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(
      smallAngryPlanet, steve, loanDate);

    final String originalDueDate = loan.getJson().getString("dueDate");

    requestsFixture.placeItemLevelHoldShelfRequest(smallAngryPlanet, jessica,
        getZonedDateTime(), requestServicePoint.getId(), "Recall");

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date sholud not be the original due date",
        storedLoan.getString("dueDate"), not(originalDueDate));

    final String expectedDueDate = formatDateTime(loanDate.plusWeeks(2));
    assertThat("due date should be in 2 weeks (minimum guaranteed loan period)",
        storedLoan.getString("dueDate"), is(expectedDueDate));
  }

  @Test
  void recallRequestWithMGDAndRDValuesChangesDueDateToMGDWithCLDDM() {
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

    servicePointsFixture.create(new ServicePointBuilder(checkOutServicePointId, "CLDDM Desk", "clddm", "CLDDM Desk Test", null, null, TRUE, null, null, null));

    // We use the loan date to calculate the minimum guaranteed due date (MGD)
    final ZonedDateTime loanDate =
        ZonedDateTime.of(2019, 1, 25, 10, 0, 0, 0, UTC);

    mockClockManagerToReturnFixedDateTime(loanDate);

    final IndividualResource loan = loansFixture.createLoan(new LoanBuilder()
        .open()
        .withItemId(smallAngryPlanet.getId())
        .withUserId(steve.getId())
        .withLoanDate(loanDate)
        .withDueDate(loanDate.plusWeeks(3))
        .withCheckoutServicePointId(checkOutServicePointId));

    final String originalDueDate = loan.getJson().getString("dueDate");

    requestsFixture.placeItemLevelHoldShelfRequest(smallAngryPlanet, jessica,
        getZonedDateTime(), "Recall");

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date should not be the original due date",
        storedLoan.getString("dueDate"), not(originalDueDate));

    final String expectedDueDate = formatDateTime(
      atEndOfDay(CASE_FRI_SAT_MON_SERVICE_POINT_NEXT_DAY, UTC));

    assertThat("due date should be moved to Monday",
        storedLoan.getString("dueDate"), is(expectedDueDate));
  }

  @ParameterizedTest(name = "{index}: {0} {1} {2} {3} {4}")
  @CsvSource(value = {
    // MGD duration|MGD interval|RD duration|RD interval|expected string
    "null,null,1,Months,the \"minimumGuaranteedLoanPeriod\" in the loan policy is not recognized",
    "1,Months,null,null,the \"recallReturnInterval\" in the loan policy is not recognized",
    "1,Years,1,Months,the interval \"Years\" in \"minimumGuaranteedLoanPeriod\" is not recognized",
    "1,Months,1,Years,the interval \"Years\" in \"recallReturnInterval\" is not recognized",
    "-100,Months,1,Months,the duration \"-100\" in \"minimumGuaranteedLoanPeriod\" is invalid",
    "1,Months,-100,Months,the duration \"-100\" in \"recallReturnInterval\" is invalid"
  }, nullValues={"null"})
  void loanPolicyWithInvalidMGDOrRDPeriodValuesReturnsErrorOnRecallCreation(
      Integer mgdDuration,
      String mgdInterval,
      Integer rdDuration,
      String rdInterval,
      String expectedMessage) {
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
    final ZonedDateTime loanDate = getZonedDateTime();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, steve, loanDate);

    final Response response = requestsFixture.attemptPlaceItemLevelHoldShelfRequest(smallAngryPlanet, jessica,
        getZonedDateTime(), requestServicePoint.getId(), "Recall");

    assertThat("Status code should be 422", response.getStatusCode(), is(422));
    assertThat("errors should be present", response.getJson().getJsonArray("errors"), notNullValue());
    assertThat("errors should be size 1", response.getJson().getJsonArray("errors").size(), is(1));
    assertThat("first error should have the expected message field",
        response.getJson().getJsonArray("errors").getJsonObject(0).getString("message"),
        is(expectedMessage));
  }

  @Test
  void initialLoanDueDateOnCreateWithPrexistingRequestsIsBeforeRecallInterval() {

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

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, charlotte,
      getZonedDateTime());

    requestsFixture.placeItemLevelHoldShelfRequest(smallAngryPlanet, steve,
      getZonedDateTime(), requestServicePoint.getId(), "Recall");

    requestsFixture.placeItemLevelHoldShelfRequest(smallAngryPlanet, jessica,
      getZonedDateTime(), requestServicePoint.getId(), "Recall");

    checkInFixture.checkInByBarcode(smallAngryPlanet);

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(
      smallAngryPlanet, steve, getZonedDateTime());

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    final String expectedDueDate = formatDateTime(ZonedDateTime.of(
      getZonedDateTime().plusWeeks(3).toLocalDate(), LocalTime.MIDNIGHT.minusSeconds(1),
      getZoneId()));

    assertThat("due date should be in 3 weeks (loan period) since it is before the recall " +
        "return interval", storedLoan.getString("dueDate"), is(expectedDueDate));
  }

  @Test
  void changedDueDateAfterRecallingAnItemShouldRespectTenantTimezone() {
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

    setClock(fixed(Instant.parse("2020-01-24T08:34:21Z"), ZoneId.of("UTC")));

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(smallAngryPlanet, steve);

    //3 days later
    setClock(offset(getClock(), ofDays(3)));

    requestsFixture.place(
      new RequestBuilder()
        .recall()
        .forItem(smallAngryPlanet)
        .by(jessica)
        .withRequestDate(getZonedDateTime())
        .withPickupServicePoint(requestServicePoint));

    final var storedLoan = loansFixture.getLoanById(loan.getId()).getJson();

    assertThat("due date should be end of the day, 5 days from loan date",
      storedLoan.getString("dueDate"), isEquivalentTo(
        parseDateTime("2020-01-29T23:59:59+01:00")));
  }

  @Test
  void pagedItemRecalledThenLoanedAndNextRecallDoesNotChangeDueDate() {
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

    requestsFixture.placeItemLevelHoldShelfRequest(smallAngryPlanet, jessica,
        getZonedDateTime(), requestServicePoint.getId(), "Page");

    requestsFixture.placeItemLevelHoldShelfRequest(smallAngryPlanet, james,
        getZonedDateTime(), requestServicePoint.getId(), "Recall");

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(
      smallAngryPlanet, jessica, getZonedDateTime());

    // Recalled is applied when loaned, so the due date should be 2 weeks, not 3 weeks
    final String expectedDueDate = formatDateTime(getZonedDateTime().plusWeeks(2));

    JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    final String recalledDueDate = storedLoan.getString("dueDate");
    assertThat("due date after recall should be 2 weeks",
        recalledDueDate, is(expectedDueDate));

    setClock(Clock.offset(getClock(), Duration.ofDays(1)));

    requestsFixture.placeItemLevelHoldShelfRequest(smallAngryPlanet, charlotte,
        getZonedDateTime(), requestServicePoint.getId(), "Recall");

    storedLoan = loansStorageClient.getById(loan.getId()).getJson();
    assertThat("second recall should not change the due date",
        storedLoan.getString("dueDate"), is(recalledDueDate));
  }

  @Test
  void pagedItemRecalledThenLoanedBecomesOverdueAndNextRecallDoesNotChangeDueDate() {
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

    requestsFixture.placeItemLevelHoldShelfRequest(smallAngryPlanet, jessica,
        getZonedDateTime(), requestServicePoint.getId(), "Page");

    requestsFixture.placeItemLevelHoldShelfRequest(smallAngryPlanet, james,
        getZonedDateTime(), requestServicePoint.getId(), "Recall");

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(
      smallAngryPlanet, jessica, getZonedDateTime());

    // Recalled is applied when loaned, so the due date should be 2 weeks, not 3 weeks
    final String expectedDueDate = formatDateTime(getZonedDateTime().plusWeeks(2));

    JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    final String recalledDueDate = storedLoan.getString("dueDate");
    assertThat("due date after recall should be 2 weeks",
        recalledDueDate, is(expectedDueDate));

    // Move the fixed clock so that the loan is now overdue
    setClock(Clock.offset(getClock(), Duration.ofDays(15)));

    requestsFixture.placeItemLevelHoldShelfRequest(smallAngryPlanet, charlotte,
        getZonedDateTime(), requestServicePoint.getId(), "Recall");

    storedLoan = loansStorageClient.getById(loan.getId()).getJson();
    assertThat("second recall should not change the due date",
        storedLoan.getString("dueDate"), is(recalledDueDate));
  }

  @Test
  void secondRecallRequestWithMGDTruncationInPlaceDoesNotChangeDueDate() {
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

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(
      smallAngryPlanet, steve, getZonedDateTime());

    final String originalDueDate = loan.getJson().getString("dueDate");

    requestsFixture.placeItemLevelHoldShelfRequest(smallAngryPlanet, jessica,
        getZonedDateTime(), requestServicePoint.getId(), "Recall");

    JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    final String recalledDueDate = storedLoan.getString("dueDate");
    assertThat("due date after recall should not be the original date",
        recalledDueDate, not(originalDueDate));

    final String expectedDueDate = formatDateTime(getZonedDateTime().plusWeeks(2));
    assertThat("due date after recall should be in 2 weeks",
        storedLoan.getString("dueDate"), is(expectedDueDate));

    setClock(Clock.offset(getClock(), Duration.ofDays(7)));

    requestsFixture.placeItemLevelHoldShelfRequest(smallAngryPlanet, charlotte,
        getZonedDateTime(), requestServicePoint.getId(), "Recall");

    storedLoan = loansStorageClient.getById(loan.getId()).getJson();
    assertThat("second recall should not change the due date (2 weeks)",
        storedLoan.getString("dueDate"), is(recalledDueDate));
  }

  @Test
  void secondRecallRequestWithMGDTruncationInPlaceAndLoanOverdueDoesNotChangeDueDate() {
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

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(
      smallAngryPlanet, steve, getZonedDateTime());

    final String originalDueDate = loan.getJson().getString("dueDate");

    requestsFixture.placeItemLevelHoldShelfRequest(smallAngryPlanet, jessica,
        getZonedDateTime(), requestServicePoint.getId(), "Recall");

    JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    final String recalledDueDate = storedLoan.getString("dueDate");
    assertThat("due date after recall should not be the original date",
        recalledDueDate, not(originalDueDate));

    final String expectedDueDate = formatDateTime(getZonedDateTime().plusWeeks(2));
    assertThat("due date after recall should be in 2 weeks",
        storedLoan.getString("dueDate"), is(expectedDueDate));

    // Move the fixed clock so that the loan is now overdue
    setClock(Clock.offset(getClock(), Duration.ofDays(15)));

    requestsFixture.placeItemLevelHoldShelfRequest(smallAngryPlanet, charlotte,
        getZonedDateTime(), requestServicePoint.getId(), "Recall");

    storedLoan = loansStorageClient.getById(loan.getId()).getJson();
    assertThat("second recall should not change the due date (2 weeks)",
        storedLoan.getString("dueDate"), is(recalledDueDate));
  }

  @Test
  void secondRecallRequestWithRDTruncationInPlaceDoesNotChangeDueDate() {
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

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(
      smallAngryPlanet, steve, getZonedDateTime());

    final String originalDueDate = loan.getJson().getString("dueDate");

    requestsFixture.placeItemLevelHoldShelfRequest(smallAngryPlanet, jessica,
        getZonedDateTime(), requestServicePoint.getId(), "Recall");

    JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    final String recalledDueDate = storedLoan.getString("dueDate");
    assertThat("due date after recall should be the original date",
      recalledDueDate, is(originalDueDate));

    final String expectedDueDate = formatDateTime(getZonedDateTime().plusWeeks(3));
    assertThat("due date after recall should be in 3 weeks",
        storedLoan.getString("dueDate"), is(expectedDueDate));

    setClock(Clock.offset(getClock(), Duration.ofDays(7)));

    requestsFixture.placeItemLevelHoldShelfRequest(smallAngryPlanet, charlotte,
        getZonedDateTime(), requestServicePoint.getId(), "Recall");

    storedLoan = loansStorageClient.getById(loan.getId()).getJson();
    assertThat("second recall should not change the due date (3 weeks)",
        storedLoan.getString("dueDate"), is(recalledDueDate));
  }

  @Test
  void secondRecallRequestWithRDTruncationInPlaceAndLoanOverdueDoesNotChangeDueDate() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestServicePoint = servicePointsFixture.cd1();
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource jessica = usersFixture.jessica();
    final IndividualResource charlotte = usersFixture.charlotte();

    final LoanPolicyBuilder canCirculateRollingPolicy = new LoanPolicyBuilder()
        .withName("Can Circulate Rolling With Recalls")
        .withDescription("Can circulate item With Recalls")
        .rolling(Period.days(65))
        .unlimitedRenewals()
        .renewFromSystemDate()
        .withAllowRecallsToExtendOverdueLoans(false)
        .withRecallsMinimumGuaranteedLoanPeriod(Period.days(15))
        .withRecallsRecallReturnInterval(Period.days(60));

    setFallbackPolicies(canCirculateRollingPolicy);

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(
      smallAngryPlanet, steve, getZonedDateTime());

    final String originalDueDate = loan.getJson().getString("dueDate");

    requestsFixture.placeItemLevelHoldShelfRequest(smallAngryPlanet, jessica,
        getZonedDateTime(), requestServicePoint.getId(), "Recall");

    JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    final String recalledDueDate = storedLoan.getString("dueDate");
    assertThat("due date after recall should not be the original date",
        recalledDueDate, not(originalDueDate));

    final String expectedDueDate = formatDateTime(getZonedDateTime().plusDays(60));
    assertThat("due date after recall should be in 60 days",
        storedLoan.getString("dueDate"), is(expectedDueDate));

    // Move the fixed clock so that the loan is now overdue
    setClock(Clock.offset(getClock(), Duration.ofDays(70)));

    requestsFixture.placeItemLevelHoldShelfRequest(smallAngryPlanet, charlotte,
        getZonedDateTime(), requestServicePoint.getId(), "Recall");

    storedLoan = loansStorageClient.getById(loan.getId()).getJson();
    assertThat("second recall should not change the due date (60 days)",
        storedLoan.getString("dueDate"), is(recalledDueDate));
  }

  @Test
  void itemRecalledThenCancelledAndNextRecallDoesChangeDueDate() {
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

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(
      smallAngryPlanet, jessica, getZonedDateTime());

    final String originalDueDate = loan.getJson().getString("dueDate");

    JsonObject storedLoan;

    final IndividualResource request = requestsFixture.placeItemLevelHoldShelfRequest(
        smallAngryPlanet, james, getZonedDateTime(),
        requestServicePoint.getId(), "Recall");

    final String recalledDueDate = request.getJson().getString("dueDate");
    assertThat("due date after recall should not be the original date",
        recalledDueDate, not(originalDueDate));

    requestsFixture.cancelRequest(request);

    setClock(Clock.offset(getClock(), Duration.ofDays(7)));

    final IndividualResource renewal = loansFixture.renewLoan(smallAngryPlanet, jessica);

    final String renewalDueDate = renewal.getJson().getString("dueDate");

    requestsFixture.placeItemLevelHoldShelfRequest(smallAngryPlanet, charlotte,
        getZonedDateTime(), requestServicePoint.getId(), "Recall");

    storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    final String recalledRenewalDueDate = storedLoan.getString("dueDate");
    assertThat("due date after recall should change the renewal due date",
        recalledRenewalDueDate, not(renewalDueDate));
  }

  @Test
  void shouldNotExtendLoanDueDateIfOverdueLoanIsRecalled() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    final Period loanPeriod = Period.weeks(3);
    setFallbackPolicies(new LoanPolicyBuilder()
      .withName("Can Circulate Rolling With Recalls")
      .withDescription("Can circulate item With Recalls")
      .rolling(loanPeriod)
      .unlimitedRenewals()
      .renewFromSystemDate()
      .withRecallsMinimumGuaranteedLoanPeriod(Period.weeks(2))
      .withRecallsRecallReturnInterval(Period.months(2)));

    final ZonedDateTime loanCreateDate =  loanPeriod.minusDate(getZonedDateTime()).minusMinutes(1);
    final ZonedDateTime expectedLoanDueDate = loanPeriod.plusDate(loanCreateDate);

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(
      smallAngryPlanet, usersFixture.steve(), loanCreateDate);

    requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .fulfillToHoldShelf()
      .by(usersFixture.jessica())
      .fulfillToHoldShelf()
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));

    assertThat(loansStorageClient.getById(loan.getId()).getJson(),
      hasJsonPath("dueDate", isEquivalentTo(expectedLoanDueDate)));

    // verify that loan action is recorder even though due date is not changed
    final MultipleJsonRecords loanHistory = loanHistoryClient
      .getMany(queryFromTemplate("loan.id==%s and operation==U", loan.getId()));

    assertThat(loanHistory, hasItem(allOf(
      hasJsonPath("loan.action", "recallrequested"),
      hasJsonPath("loan.itemStatus", "Checked out"))
    ));
  }

  @Test
  void shouldExtendLoanDueDateByAlternatePeriodWhenOverdueLoanIsRecalledAndPolicyAllowsExtension() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    final Period alternateLoanPeriod = Period.weeks(3);

    final Period loanPeriod = Period.weeks(1);
    setFallbackPolicies(new LoanPolicyBuilder()
      .withName("Can Circulate Rolling With Recalls")
      .withDescription("Can circulate item With Recalls")
      .rolling(loanPeriod)
      .unlimitedRenewals()
      .renewFromSystemDate()
      .withAllowRecallsToExtendOverdueLoans(true)
      .withAlternateRecallReturnInterval(alternateLoanPeriod));

    final ZonedDateTime loanCreateDate = loanPeriod.minusDate(getZonedDateTime()).minusMinutes(1);
    final ZonedDateTime expectedLoanDueDate = alternateLoanPeriod
      .plusDate(loanPeriod.plusDate(loanCreateDate))
      .plusMinutes(1);

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(
      smallAngryPlanet, usersFixture.steve(), loanCreateDate);

    requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .fulfillToHoldShelf()
      .by(usersFixture.jessica())
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat(storedLoan.getString(DUE_DATE), withinSecondsBefore(30, expectedLoanDueDate));
    // Verify published loan event type description
    List<JsonObject> publishedLoanLogEvents = Awaitility.waitAtMost(5, TimeUnit.SECONDS)
      .until(() -> getPublishedEventsAsList(byLogEventType(LOAN)), hasSize(2));

    assertThat(publishedLoanLogEvents.size(), is(2));
    verifyLogEventDueDateChangedMessage(publishedLoanLogEvents.get(0), loan, expectedLoanDueDate);
    verifyLogEventDueDateChangedMessage(publishedLoanLogEvents.get(1), loan, expectedLoanDueDate);
  }

  @Test
  void shouldNotExtendLoanDueDateWhenCurrentDueDateIsBeforeRecallDueDate() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    final Period loanPeriod = Period.days(2);
    setFallbackPolicies(new LoanPolicyBuilder()
      .withName("Can Circulate Rolling With Recalls")
      .withDescription("Can circulate item With Recalls")
      .rolling(loanPeriod)
      .unlimitedRenewals()
      .renewFromSystemDate()
      .withRecallsMinimumGuaranteedLoanPeriod(Period.days(10))
      .withRecallsRecallReturnInterval(Period.days(5)));

    final ZonedDateTime loanCreateDate = getZonedDateTime().minusDays(1);
    final ZonedDateTime expectedLoanDueDate = loanPeriod.plusDate(loanCreateDate);

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(
      smallAngryPlanet, usersFixture.steve(), loanCreateDate);

    requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .fulfillToHoldShelf()
      .by(usersFixture.jessica())
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));

    final var updatedLoan = loansStorageClient.getById(loan.getId());

    // verify that loan due date hasn't changed
    assertThat(updatedLoan.getJson(),
      hasJsonPath("dueDate", isEquivalentTo(expectedLoanDueDate)));
  }

  private void verifyLogEventDueDateChangedMessage(JsonObject eventLogJsonObject,
    IndividualResource loan, ZonedDateTime expectedLoanDueDate) {

    var expectedDescription = String.format("New due date: %s (from %s)",
      formatDateTimeOptional(expectedLoanDueDate), loan.getJson().getString(DUE_DATE));
    var actualDescription = new JsonObject(eventLogJsonObject.getString("eventPayload"))
      .getJsonObject("payload")
      .getString("description");

    assertThat(actualDescription, is(expectedDescription));
  }

  public void shouldExtendLoanDueDateByRecallReturnIntervalForOverdueLoansIsRecalledAndAlternateRecallReturnIntervalForOverdueLoansIsEmpty() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    final Period recalReturnInterval = Period.weeks(3);

    final Period loanPeriod = Period.weeks(1);
    setFallbackPolicies(new LoanPolicyBuilder()
      .withName("Can Circulate Rolling With Recalls")
      .withDescription("Can circulate item With Recalls")
      .rolling(loanPeriod)
      .unlimitedRenewals()
      .renewFromSystemDate()
      .withAllowRecallsToExtendOverdueLoans(true)
      .withRecallsRecallReturnInterval(recalReturnInterval));

    final ZonedDateTime loanCreateDate = loanPeriod.plusDate(getZonedDateTime()).minusMinutes(1);

    final ZonedDateTime expectedLoanDueDate = recalReturnInterval
      .plusDate(loanPeriod.plusDate(loanCreateDate))
      .plusMinutes(1);

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(
      smallAngryPlanet, usersFixture.steve(), loanCreateDate);

    requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .fulfillToHoldShelf()
      .by(usersFixture.jessica())
      .fulfillToHoldShelf()
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat(storedLoan.getString("dueDate"), withinSecondsBefore(30, expectedLoanDueDate));
  }

  @Test
  void loanDueDateTruncatedOnCheckoutWhenRecallAnywhereInQueue() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestServicePoint = servicePointsFixture.cd1();
    final IndividualResource jessica = usersFixture.jessica();
    final IndividualResource james = usersFixture.james();
    final IndividualResource rebecca = usersFixture.rebecca();
    final IndividualResource steve = usersFixture.steve();

    final Period loanPeriod = Period.weeks(4);
    setFallbackPolicies(new LoanPolicyBuilder()
      .withName("Can Circulate Rolling With Recalls")
      .withDescription("Can circulate item With Recalls")
      .rolling(loanPeriod)
      .unlimitedRenewals()
      .renewFromSystemDate()
      .withRecallsMinimumGuaranteedLoanPeriod(Period.weeks(2))
      .withRecallsRecallReturnInterval(Period.weeks(2)));

      checkOutFixture.checkOutByBarcode(smallAngryPlanet, steve, getZonedDateTime());

      requestsFixture.placeItemLevelHoldShelfRequest(
        smallAngryPlanet, james, getZonedDateTime(),
        requestServicePoint.getId(), "Hold");

      requestsFixture.placeItemLevelHoldShelfRequest(
        smallAngryPlanet, jessica, getZonedDateTime(),
        requestServicePoint.getId(), "Hold");

      requestsFixture.place(new RequestBuilder()
        .recall()
        .forItem(smallAngryPlanet)
        .fulfillToHoldShelf()
        .by(rebecca)
        .fulfillToHoldShelf()
        .withPickupServicePointId(requestServicePoint.getId()));

      checkInFixture.checkInByBarcode(smallAngryPlanet);

      final ZonedDateTime checkOutDate = getZonedDateTime();
      final ZonedDateTime truncatedLoanDate = checkOutDate.plusWeeks(2);

      final IndividualResource loan = checkOutFixture.checkOutByBarcode(
        smallAngryPlanet, james, checkOutDate);

      String loanDueDate = loan.getJson().getString("dueDate");
      assertThat(loanDueDate, is(truncatedLoanDate.toString()));
  }
}
