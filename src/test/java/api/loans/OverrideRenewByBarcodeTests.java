package api.loans;

import api.APITestSuite;
import api.support.APITests;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.FixedDueDateSchedulesBuilder;
import api.support.builders.LoanPolicyBuilder;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.folio.circulation.support.http.server.ValidationError;
import org.hamcrest.Matcher;
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeZone;
import org.joda.time.Seconds;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static api.support.builders.FixedDueDateSchedule.forDay;
import static api.support.builders.FixedDueDateSchedule.wholeMonth;
import static api.support.builders.ItemBuilder.CHECKED_OUT;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.TextDateTimeMatcher.withinSecondsAfter;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class OverrideRenewByBarcodeTests extends APITests {

  private static final String OVERRIDE_COMMENT = "Comment to override";

  @Test
  public void canOverrideRenewalRollingLoanFromSystemDate()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    final UUID loanId = loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, DateTimeConstants.APRIL, 21, 11, 21, 43))
      .getId();

    //TODO: Renewal based upon system date,
    // needs to be approximated, at least until we introduce a calendar and clock
    DateTime approximateRenewalDate = DateTime.now();

    final JsonObject renewedLoan = loansFixture.overrideRenewalByBarcode(smallAngryPlanet, jessica, OVERRIDE_COMMENT, null).getJson();

    assertThat(renewedLoan.getString("id"), is(loanId.toString()));

    assertThat("user ID should match barcode",
      renewedLoan.getString("userId"), is(jessica.getId().toString()));

    assertThat("item ID should match barcode",
      renewedLoan.getString("itemId"), is(smallAngryPlanet.getId().toString()));

    assertThat("status should be open",
      renewedLoan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action should be renewed",
      renewedLoan.getString("action"), is("Renewed through override"));

    assertThat("'actionComment' field should contain comment specified for override",
      renewedLoan.getString("actionComment"), is(OVERRIDE_COMMENT));

    assertThat("renewal count should be incremented",
      renewedLoan.getInteger("renewalCount"), is(1));

    assertThat("last loan policy should be stored",
      renewedLoan.getString("loanPolicyId"),
      is(APITestSuite.canCirculateRollingLoanPolicyId().toString()));

    assertThat("due date should be approximately 3 weeks after renewal date, based upon loan policy",
      renewedLoan.getString("dueDate"),
      withinSecondsAfter(Seconds.seconds(10), approximateRenewalDate.plusWeeks(3)));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canOverrideRenewalRollingLoanFromCurrentDueDate()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource loan = loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43));

    final UUID loanId = loan.getId();

    LoanPolicyBuilder currentDueDateRollingPolicy = new LoanPolicyBuilder()
      .withName("Current Due Date Rolling Policy")
      .rolling(Period.months(2))
      .renewFromCurrentDueDate();

    UUID dueDateLimitedPolicyId = loanPolicyClient.create(currentDueDateRollingPolicy).getId();

    //Need to remember in order to delete after test
    policiesToDelete.add(dueDateLimitedPolicyId);

    useLoanPolicyAsFallback(dueDateLimitedPolicyId);

    final JsonObject renewedLoan =
      loansFixture.overrideRenewalByBarcode(smallAngryPlanet, jessica, OVERRIDE_COMMENT, null).getJson();

    assertThat(renewedLoan.getString("id"), is(loanId.toString()));

    assertThat("user ID should match barcode",
      renewedLoan.getString("userId"), is(jessica.getId().toString()));

    assertThat("item ID should match barcode",
      renewedLoan.getString("itemId"), is(smallAngryPlanet.getId().toString()));

    assertThat("status should be open",
      renewedLoan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action should be renewed",
      renewedLoan.getString("action"), is("Renewed through override"));

    assertThat("'actionComment' field should contain comment specified for override",
      renewedLoan.getString("actionComment"), is(OVERRIDE_COMMENT));

    assertThat("renewal count should be incremented",
      renewedLoan.getInteger("renewalCount"), is(1));

    assertThat("last loan policy should be stored",
      renewedLoan.getString("loanPolicyId"), is(dueDateLimitedPolicyId.toString()));

    assertThat("due date should be 2 months after initial due date date",
      renewedLoan.getString("dueDate"),
      isEquivalentTo(new DateTime(2018, 7, 12, 11, 21, 43)));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canOverrideRenewalUsingDueDateLimitedRollingLoanPolicy()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    FixedDueDateSchedulesBuilder dueDateLimitSchedule = new FixedDueDateSchedulesBuilder()
      .withName("March Only Due Date Limit")
      .addSchedule(wholeMonth(2018, 3));

    final UUID dueDateLimitScheduleId = fixedDueDateScheduleClient.create(
      dueDateLimitSchedule).getId();

    //Need to remember in order to delete after test
    schedulesToDelete.add(dueDateLimitScheduleId);

    LoanPolicyBuilder dueDateLimitedPolicy = new LoanPolicyBuilder()
      .withName("Due Date Limited Rolling Policy")
      .rolling(Period.weeks(2))
      .limitedBySchedule(dueDateLimitScheduleId)
      .renewFromCurrentDueDate();

    UUID dueDateLimitedPolicyId = loanPolicyClient.create(dueDateLimitedPolicy).getId();

    //Need to remember in order to delete after test
    policiesToDelete.add(dueDateLimitedPolicyId);

    useLoanPolicyAsFallback(dueDateLimitedPolicyId);

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate = new DateTime(2018, 3, 7, 11, 43, 54, DateTimeZone.UTC);

    loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(UUID.randomUUID()));

    final IndividualResource response =
      loansFixture.overrideRenewalByBarcode(smallAngryPlanet, steve, OVERRIDE_COMMENT, null);

    final JsonObject loan = response.getJson();

    assertThat("last loan policy should be stored",
      loan.getString("loanPolicyId"), is(dueDateLimitedPolicyId.toString()));

    assertThat("due date should be limited by schedule",
      loan.getString("dueDate"),
      isEquivalentTo(new DateTime(2018, 3, 31, 23, 59, 59, DateTimeZone.UTC)));
  }

  @Test
  public void canOverrideRenewalRollingLoanUsingDifferentPeriod()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource loan = loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43));

    final UUID loanId = loan.getId();

    LoanPolicyBuilder currentDueDateRollingPolicy = new LoanPolicyBuilder()
      .withName("Current Due Date Different Period Rolling Policy")
      .rolling(Period.months(2))
      .renewFromCurrentDueDate()
      .renewWith(Period.months(1));

    UUID dueDateLimitedPolicyId = loanPolicyClient.create(currentDueDateRollingPolicy).getId();

    //Need to remember in order to delete after test
    policiesToDelete.add(dueDateLimitedPolicyId);

    useLoanPolicyAsFallback(dueDateLimitedPolicyId);

    final JsonObject renewedLoan =
      loansFixture.overrideRenewalByBarcode(smallAngryPlanet, jessica, OVERRIDE_COMMENT, null).getJson();

    assertThat(renewedLoan.getString("id"), is(loanId.toString()));

    assertThat("user ID should match barcode",
      renewedLoan.getString("userId"), is(jessica.getId().toString()));

    assertThat("item ID should match barcode",
      renewedLoan.getString("itemId"), is(smallAngryPlanet.getId().toString()));

    assertThat("status should be open",
      renewedLoan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action should be renewed",
      renewedLoan.getString("action"), is("Renewed through override"));

    assertThat("'actionComment' field should contain comment specified for override",
      renewedLoan.getString("actionComment"), is(OVERRIDE_COMMENT));

    assertThat("renewal count should be incremented",
      renewedLoan.getInteger("renewalCount"), is(1));

    assertThat("last loan policy should be stored",
      renewedLoan.getString("loanPolicyId"), is(dueDateLimitedPolicyId.toString()));

    assertThat("due date should be 2 months after initial due date date",
      renewedLoan.getString("dueDate"),
      isEquivalentTo(new DateTime(2018, 6, 12, 11, 21, 43)));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canOverrideRenewalUsingAlternateDueDateLimitedRollingLoanPolicy()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    FixedDueDateSchedulesBuilder dueDateLimitSchedule = new FixedDueDateSchedulesBuilder()
      .withName("March Only Due Date Limit")
      .addSchedule(wholeMonth(2018, 3));

    final UUID dueDateLimitScheduleId = fixedDueDateScheduleClient.create(
      dueDateLimitSchedule).getId();

    //Need to remember in order to delete after test
    schedulesToDelete.add(dueDateLimitScheduleId);

    LoanPolicyBuilder dueDateLimitedPolicy = new LoanPolicyBuilder()
      .withName("Due Date Limited Rolling Policy")
      .rolling(Period.weeks(3))
      .renewFromCurrentDueDate()
      .renewWith(Period.days(8), dueDateLimitScheduleId);

    UUID dueDateLimitedPolicyId = loanPolicyClient.create(dueDateLimitedPolicy).getId();

    //Need to remember in order to delete after test
    policiesToDelete.add(dueDateLimitedPolicyId);

    useLoanPolicyAsFallback(dueDateLimitedPolicyId);

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate = new DateTime(2018, 3, 4, 11, 43, 54, DateTimeZone.UTC);

    loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(UUID.randomUUID()));

    final IndividualResource response =
      loansFixture.overrideRenewalByBarcode(smallAngryPlanet, steve, OVERRIDE_COMMENT, null);

    final JsonObject loan = response.getJson();

    assertThat("last loan policy should be stored",
      loan.getString("loanPolicyId"), is(dueDateLimitedPolicyId.toString()));

    assertThat("due date should be limited by schedule",
      loan.getString("dueDate"),
      isEquivalentTo(new DateTime(2018, 3, 31, 23, 59, 59, DateTimeZone.UTC)));
  }

  @Test
  public void canOverrideRenewalUsingLoanDueDateLimitSchedulesWhenDifferentPeriodAndNotAlternateLimits()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    FixedDueDateSchedulesBuilder dueDateLimitSchedule = new FixedDueDateSchedulesBuilder()
      .withName("March Only Due Date Limit")
      .addSchedule(wholeMonth(2018, 3));

    final UUID dueDateLimitScheduleId = fixedDueDateScheduleClient.create(
      dueDateLimitSchedule).getId();

    //Need to remember in order to delete after test
    schedulesToDelete.add(dueDateLimitScheduleId);

    LoanPolicyBuilder dueDateLimitedPolicy = new LoanPolicyBuilder()
      .withName("Due Date Limited Rolling Policy")
      .rolling(Period.weeks(3))
      .limitedBySchedule(dueDateLimitScheduleId)
      .renewFromCurrentDueDate()
      .renewWith(Period.days(8));

    UUID dueDateLimitedPolicyId = loanPolicyClient.create(dueDateLimitedPolicy).getId();

    //Need to remember in order to delete after test
    policiesToDelete.add(dueDateLimitedPolicyId);

    useLoanPolicyAsFallback(dueDateLimitedPolicyId);

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate = new DateTime(2018, 3, 4, 11, 43, 54, DateTimeZone.UTC);

    loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(UUID.randomUUID()));

    final IndividualResource response =
      loansFixture.overrideRenewalByBarcode(smallAngryPlanet, steve, OVERRIDE_COMMENT, null);

    final JsonObject loan = response.getJson();

    assertThat("last loan policy should be stored",
      loan.getString("loanPolicyId"), is(dueDateLimitedPolicyId.toString()));

    assertThat("due date should be limited by schedule",
      loan.getString("dueDate"),
      isEquivalentTo(new DateTime(2018, 3, 31, 23, 59, 59, DateTimeZone.UTC)));
  }

  @Test
  public void canOverrideRenewalUsingFixedDueDateLoanPolicy()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    //TODO: Need to be able to inject system date here
    final DateTime renewalDate = DateTime.now();
    //e.g. Clock.freeze(renewalDate)

    FixedDueDateSchedulesBuilder fixedDueDateSchedules = new FixedDueDateSchedulesBuilder()
      .withName("Kludgy Fixed Due Date Schedule")
      .addSchedule(wholeMonth(2018, 2))
      .addSchedule(forDay(renewalDate));

    final UUID fixedDueDateSchedulesId = fixedDueDateScheduleClient.create(
      fixedDueDateSchedules).getId();

    //Need to remember in order to delete after test
    schedulesToDelete.add(fixedDueDateSchedulesId);

    LoanPolicyBuilder dueDateLimitedPolicy = new LoanPolicyBuilder()
      .withName("Fixed Due Date Policy")
      .fixed(fixedDueDateSchedulesId)
      .renewFromSystemDate();

    UUID fixedDueDatePolicyId = loanPolicyClient.create(dueDateLimitedPolicy).getId();

    //Need to remember in order to delete after test
    policiesToDelete.add(fixedDueDatePolicyId);

    useLoanPolicyAsFallback(fixedDueDatePolicyId);

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate = new DateTime(2018, 2, 10, 11, 23, 12, DateTimeZone.UTC);

    loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(UUID.randomUUID()));

    final IndividualResource response = loansFixture.overrideRenewalByBarcode(smallAngryPlanet, steve, OVERRIDE_COMMENT, null);

    final JsonObject loan = response.getJson();

    assertThat("last loan policy should be stored",
      loan.getString("loanPolicyId"), is(fixedDueDatePolicyId.toString()));

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
  public void canOverrideRenewalMultipleTimesUpToRenewalLimit()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    LoanPolicyBuilder limitedRenewalsPolicy = new LoanPolicyBuilder()
      .withName("Limited Renewals Policy")
      .rolling(Period.days(2))
      .renewFromCurrentDueDate()
      .limitedRenewals(3);

    UUID limitedRenewalsPolicyId = loanPolicyClient.create(limitedRenewalsPolicy).getId();

    //Need to remember in order to delete after test
    policiesToDelete.add(limitedRenewalsPolicyId);

    useLoanPolicyAsFallback(limitedRenewalsPolicyId);

    final IndividualResource loan = loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43, DateTimeZone.UTC));

    final UUID loanId = loan.getId();

    loansFixture.overrideRenewalByBarcode(smallAngryPlanet, jessica, OVERRIDE_COMMENT, null);

    loansFixture.overrideRenewalByBarcode(smallAngryPlanet, jessica, OVERRIDE_COMMENT, null);

    final JsonObject renewedLoan =
      loansFixture.overrideRenewalByBarcode(smallAngryPlanet, jessica, OVERRIDE_COMMENT, null).getJson();

    assertThat(renewedLoan.getString("id"), is(loanId.toString()));

    assertThat("status should be open",
      renewedLoan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action should be renewed",
      renewedLoan.getString("action"), is("Renewed through override"));

    assertThat("'actionComment' field should contain comment specified for override",
      renewedLoan.getString("actionComment"), is(OVERRIDE_COMMENT));

    assertThat("renewal count should be incremented",
      renewedLoan.getInteger("renewalCount"), is(3));

    assertThat("last loan policy should be stored",
      renewedLoan.getString("loanPolicyId"), is(limitedRenewalsPolicyId.toString()));

    assertThat("due date should be 8 days after initial loan date date",
      renewedLoan.getString("dueDate"),
      isEquivalentTo(new DateTime(2018, 4, 29, 11, 21, 43, DateTimeZone.UTC)));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canGetOverrideRenewaledLoan()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    final UUID loanId = loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43))
      .getId();

    //TODO: Renewal based upon system date,
    // needs to be approximated, at least until we introduce a calendar and clock
    DateTime approximateRenewalDate = DateTime.now();

    final IndividualResource response =
      loansFixture.overrideRenewalByBarcode(smallAngryPlanet, jessica, OVERRIDE_COMMENT, null);

    final CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(APITestSuite.circulationModuleUrl(response.getLocation()),
      ResponseHandler.json(getCompleted));

    final Response getResponse = getCompleted.get(2, TimeUnit.SECONDS);

    assertThat(getResponse.getStatusCode(), is(HTTP_OK));

    JsonObject renewedLoan = getResponse.getJson();

    assertThat(renewedLoan.getString("id"), is(loanId.toString()));

    assertThat("user ID should match barcode",
      renewedLoan.getString("userId"), is(jessica.getId().toString()));

    assertThat("item ID should match barcode",
      renewedLoan.getString("itemId"), is(smallAngryPlanet.getId().toString()));

    assertThat("status should be open",
      renewedLoan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action should be renewed",
      renewedLoan.getString("action"), is("Renewed through override"));

    assertThat("'actionComment' field should contain comment specified for override",
      renewedLoan.getString("actionComment"), is(OVERRIDE_COMMENT));

    assertThat("renewal count should be incremented",
      renewedLoan.getInteger("renewalCount"), is(1));

    assertThat("last loan policy should be stored",
      renewedLoan.getString("loanPolicyId"),
      is(APITestSuite.canCirculateRollingLoanPolicyId().toString()));

    assertThat("due date should be approximately 3 weeks after renewal date, based upon loan policy",
      renewedLoan.getString("dueDate"),
      withinSecondsAfter(Seconds.seconds(10), approximateRenewalDate.plusWeeks(3)));
  }

  @Test
  public void cannotOverrideRenewalWhenLoanPolicyDoesNotExist()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    final UUID unknownLoanPolicyId = UUID.randomUUID();

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43));

    useLoanPolicyAsFallback(unknownLoanPolicyId);

    final Response response = loansFixture.attemptRenewal(500, smallAngryPlanet, jessica);

    assertThat(response.getBody(), is(String.format(
      "Loan policy %s could not be found, please check loan rules", unknownLoanPolicyId)));
  }

  @Test
  public void cannotOverrideRenewalWhenLoaneeCannotBeFound()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    loansFixture.checkOut(smallAngryPlanet, steve);

    usersClient.delete(steve.getId());

    final Response response =
      loansFixture.attemptOverride(smallAngryPlanet, steve, OVERRIDE_COMMENT, null);

    //Occurs when current loanee is not found, so relates to loan rather than user in request
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("user is not found"),
      hasParameter("userId", steve.getId().toString()))));
  }

  @Test
  public void cannotOverrideRenewalWhenItemCannotBeFound()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    loansFixture.checkOut(smallAngryPlanet, steve);

    itemsClient.delete(smallAngryPlanet.getId());

    final Response response =
      loansFixture.attemptOverride(smallAngryPlanet, steve, OVERRIDE_COMMENT, null);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasItemNotFoundMessage(smallAngryPlanet),
      hasItemRelatedParameter(smallAngryPlanet))));
  }

  @Test
  public void cannotOverrideRenewalLoanForDifferentUser()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    final IndividualResource jessica = usersFixture.jessica();

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43));

    final Response response =
      loansFixture.attemptOverride(smallAngryPlanet, james, OVERRIDE_COMMENT, null);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Cannot renew item checked out to different user"),
      hasUserRelatedParameter(james))));
  }

  @Test
  public void canOverrideRenewalToSpecifiedDateWhenDateFallsOutsideOfTheDateRangesInTheLoanPolicy() throws
    InterruptedException,
    ExecutionException,
    TimeoutException, MalformedURLException {

    final DateTime renewalDate = DateTime.now();

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource loan = loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43));

    final UUID loanId = loan.getId();

    FixedDueDateSchedulesBuilder fixedDueDateSchedules = new FixedDueDateSchedulesBuilder()
      .withName("Kludgy Fixed Due Date Schedule")
      .addSchedule(wholeMonth(2018, 2))
      .addSchedule(forDay(renewalDate));

    final UUID fixedDueDateSchedulesId = fixedDueDateScheduleClient.create(
      fixedDueDateSchedules).getId();

    //Need to remember in order to delete after test
    schedulesToDelete.add(fixedDueDateSchedulesId);

    LoanPolicyBuilder currentDueDateRollingPolicy = new LoanPolicyBuilder()
      .withName("Current Due Date Rolling Policy")
      .rolling(Period.months(2))
      .renewWith(fixedDueDateSchedulesId)
      .renewFromCurrentDueDate();

    UUID dueDateLimitedPolicyId = loanPolicyClient.create(currentDueDateRollingPolicy).getId();

    //Need to remember in order to delete after test
    policiesToDelete.add(dueDateLimitedPolicyId);

    useLoanPolicyAsFallback(dueDateLimitedPolicyId);

    DateTime newDueDate =
      new DateTime(2025, 1, 1, 1, 1, 1);
    final JsonObject renewedLoan =
      loansFixture.overrideRenewalByBarcode(smallAngryPlanet, jessica, OVERRIDE_COMMENT, newDueDate.toString()).getJson();

    assertThat(renewedLoan.getString("id"), is(loanId.toString()));

    assertThat("user ID should match barcode",
      renewedLoan.getString("userId"), is(jessica.getId().toString()));

    assertThat("item ID should match barcode",
      renewedLoan.getString("itemId"), is(smallAngryPlanet.getId().toString()));

    assertThat("status should be open",
      renewedLoan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action should be renewed",
      renewedLoan.getString("action"), is("Renewed through override"));

    assertThat("'actionComment' field should contain comment specified for override",
      renewedLoan.getString("actionComment"), is(OVERRIDE_COMMENT));

    assertThat("renewal count should be incremented",
      renewedLoan.getInteger("renewalCount"), is(1));

    assertThat("last loan policy should be stored",
      renewedLoan.getString("loanPolicyId"), is(dueDateLimitedPolicyId.toString()));

    assertThat("due date should be 2 months after initial due date date",
      renewedLoan.getString("dueDate"),
      isEquivalentTo(newDueDate));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void cannotOverrideRenewalWhenNewDueDateIsNotSpecifiedAndDateFallsOutsideOfTheDateRangesInTheLoanPolicy() throws
    InterruptedException,
    ExecutionException,
    TimeoutException, MalformedURLException {

    final DateTime renewalDate = DateTime.now();

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource loan = loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43));

    final UUID loanId = loan.getId();

    FixedDueDateSchedulesBuilder fixedDueDateSchedules = new FixedDueDateSchedulesBuilder()
      .withName("Kludgy Fixed Due Date Schedule")
      .addSchedule(wholeMonth(2018, 2))
      .addSchedule(forDay(renewalDate));

    final UUID fixedDueDateSchedulesId = fixedDueDateScheduleClient.create(
      fixedDueDateSchedules).getId();

    //Need to remember in order to delete after test
    schedulesToDelete.add(fixedDueDateSchedulesId);

    LoanPolicyBuilder currentDueDateRollingPolicy = new LoanPolicyBuilder()
      .withName("Current Due Date Rolling Policy")
      .rolling(Period.months(2))
      .renewWith(fixedDueDateSchedulesId)
      .renewFromCurrentDueDate();

    UUID dueDateLimitedPolicyId = loanPolicyClient.create(currentDueDateRollingPolicy).getId();

    //Need to remember in order to delete after test
    policiesToDelete.add(dueDateLimitedPolicyId);

    useLoanPolicyAsFallback(dueDateLimitedPolicyId);

    final Response response =
      loansFixture.attemptOverride(smallAngryPlanet, jessica, OVERRIDE_COMMENT, null);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("New due date must be specified when due date calculation fails"),
      hasParameter("dueDate", null))));
  }

  @Test
  public void cannotOverrideRenewalWhenCommentPropertyIsBlank() throws
    InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    final Response response =
      loansFixture.attemptOverride(smallAngryPlanet, jessica, StringUtils.EMPTY, null);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Override renewal request must have a comment"),
      hasParameter("comment", null))));
  }

  private Matcher<ValidationError> hasUserRelatedParameter(IndividualResource user) {
    return hasParameter("userBarcode", user.getJson().getString("barcode"));
  }

  private Matcher<ValidationError> hasItemRelatedParameter(IndividualResource item) {
    return hasParameter("itemBarcode", item.getJson().getString("barcode"));
  }

  private Matcher<ValidationError> hasItemNotFoundMessage(IndividualResource item) {
    return hasMessage(String.format("No item with barcode %s exists",
      item.getJson().getString("barcode")));
  }
}
