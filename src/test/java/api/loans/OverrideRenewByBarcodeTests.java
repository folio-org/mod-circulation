package api.loans;

import api.support.APITests;
import api.support.builders.FixedDueDateSchedulesBuilder;
import api.support.builders.LoanPolicyBuilder;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.ValidationError;
import org.hamcrest.Matcher;
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static api.support.builders.FixedDueDateSchedule.forDay;
import static api.support.builders.FixedDueDateSchedule.wholeMonth;
import static api.support.builders.ItemBuilder.CHECKED_OUT;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class OverrideRenewByBarcodeTests extends APITests {

  private static final String OVERRIDE_COMMENT = "Comment to override";

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
      new DateTime(2018, DateTimeConstants.APRIL, 21, 11, 21, 43));

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
      new DateTime(2018, DateTimeConstants.APRIL, 21, 11, 21, 43));

    final Response response =
      loansFixture.attemptOverride(smallAngryPlanet, james, OVERRIDE_COMMENT, null);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Cannot renew item checked out to different user"),
      hasUserRelatedParameter(james))));
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

  @Test
  public void canOverrideRenewalWhenItemIsNotRenewableAndNewDueDateIsNotSpecified() throws
    InterruptedException,
    ExecutionException,
    TimeoutException, MalformedURLException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    DateTime loanDueDate =
      new DateTime(2018, DateTimeConstants.APRIL, 21, 11, 21, 43);
    final IndividualResource loan = loansFixture.checkOutByBarcode(smallAngryPlanet, jessica, loanDueDate);

    FixedDueDateSchedulesBuilder fixedDueDateSchedules = new FixedDueDateSchedulesBuilder()
      .withName("Fixed Due Date Schedule")
      .addSchedule(wholeMonth(2018, DateTimeConstants.FEBRUARY));

    LoanPolicyBuilder nonRenewablePolicy = new LoanPolicyBuilder()
      .withName("Non Renewable Policy")
      .rolling(Period.days(2))
      .notRenewable();

    UUID dueDateLimitedPolicyId = loanPolicyClient.create(nonRenewablePolicy).getId();

    //Need to remember in order to delete after test
    policiesToDelete.add(dueDateLimitedPolicyId);

    useLoanPolicyAsFallback(dueDateLimitedPolicyId);

    Response response =
      loansFixture.attemptOverride(smallAngryPlanet, jessica, OVERRIDE_COMMENT, null);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("New due date must be specified when due date calculation fails"))));
  }

  @Test
  public void canOverrideRenewalWhenItemIsNotRenewableAndNewDueDateIsSpecified() throws
    InterruptedException,
    ExecutionException,
    TimeoutException, MalformedURLException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    DateTime loanDueDate =
      new DateTime(2018, DateTimeConstants.APRIL, 21, 11, 21, 43);
    final IndividualResource loan = loansFixture.checkOutByBarcode(smallAngryPlanet, jessica, loanDueDate);

    FixedDueDateSchedulesBuilder fixedDueDateSchedules = new FixedDueDateSchedulesBuilder()
      .withName("Fixed Due Date Schedule")
      .addSchedule(wholeMonth(2018, DateTimeConstants.FEBRUARY));


    LoanPolicyBuilder nonRenewablePolicy = new LoanPolicyBuilder()
      .withName("Non Renewable Policy")
      .rolling(Period.days(2))
      .notRenewable();

    UUID dueDateLimitedPolicyId = loanPolicyClient.create(nonRenewablePolicy).getId();

    //Need to remember in order to delete after test
    policiesToDelete.add(dueDateLimitedPolicyId);

    useLoanPolicyAsFallback(dueDateLimitedPolicyId);

    DateTime newDueDate = DateTime.now().plusWeeks(2);
    JsonObject renewedLoan =
      loansFixture.overrideRenewalByBarcode(smallAngryPlanet, jessica, OVERRIDE_COMMENT, newDueDate.toString()).getJson();

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

    assertThat("due date should be 2 weeks from now",
      renewedLoan.getString("dueDate"),
      isEquivalentTo(newDueDate));
  }

  @Test
  public void canOverrideRenewalWhenDateFallsOutsideOfTheDateRangesInTheFixedLoanPolicyAndDueDateIsNotSpecified() throws
    InterruptedException,
    ExecutionException,
    TimeoutException, MalformedURLException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    DateTime loanDueDate =
      new DateTime(2018, DateTimeConstants.APRIL, 21, 11, 21, 43);
    final IndividualResource loan = loansFixture.checkOutByBarcode(smallAngryPlanet, jessica, loanDueDate);

    FixedDueDateSchedulesBuilder fixedDueDateSchedules = new FixedDueDateSchedulesBuilder()
      .withName("Fixed Due Date Schedule")
      .addSchedule(wholeMonth(2018, DateTimeConstants.FEBRUARY));

    final UUID fixedDueDateSchedulesId = fixedDueDateScheduleClient.create(
      fixedDueDateSchedules).getId();

    //Need to remember in order to delete after test
    schedulesToDelete.add(fixedDueDateSchedulesId);

    LoanPolicyBuilder currentDueDateRollingPolicy = new LoanPolicyBuilder()
      .withName("Current Due Date Rolling Policy")
      .fixed(fixedDueDateSchedulesId)
      .renewFromCurrentDueDate();

    UUID dueDateLimitedPolicyId = loanPolicyClient.create(currentDueDateRollingPolicy).getId();

    //Need to remember in order to delete after test
    policiesToDelete.add(dueDateLimitedPolicyId);

    useLoanPolicyAsFallback(dueDateLimitedPolicyId);

    Response response =
      loansFixture.attemptOverride(smallAngryPlanet, jessica, OVERRIDE_COMMENT, null);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("New due date must be specified when due date calculation fails"))));
  }

  @Test
  public void canOverrideRenewalWhenDateFallsOutsideOfTheDateRangesInTheFixedLoanPolicyAndDueDateIsSpecified() throws
    InterruptedException,
    ExecutionException,
    TimeoutException, MalformedURLException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    DateTime loanDueDate =
      new DateTime(2018, DateTimeConstants.APRIL, 21, 11, 21, 43);
    final IndividualResource loan = loansFixture.checkOutByBarcode(smallAngryPlanet, jessica, loanDueDate);

    final UUID loanId = loan.getId();

    FixedDueDateSchedulesBuilder fixedDueDateSchedules = new FixedDueDateSchedulesBuilder()
      .withName("Fixed Due Date Schedule")
      .addSchedule(wholeMonth(2018, DateTimeConstants.FEBRUARY));

    final UUID fixedDueDateSchedulesId = fixedDueDateScheduleClient.create(
      fixedDueDateSchedules).getId();

    //Need to remember in order to delete after test
    schedulesToDelete.add(fixedDueDateSchedulesId);

    LoanPolicyBuilder currentDueDateRollingPolicy = new LoanPolicyBuilder()
      .withName("Current Due Date Rolling Policy")
      .fixed(fixedDueDateSchedulesId)
      .renewFromCurrentDueDate();

    UUID dueDateLimitedPolicyId = loanPolicyClient.create(currentDueDateRollingPolicy).getId();

    //Need to remember in order to delete after test
    policiesToDelete.add(dueDateLimitedPolicyId);

    useLoanPolicyAsFallback(dueDateLimitedPolicyId);

    DateTime newDueDate = DateTime.now().plusWeeks(1);
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

    assertThat("due date should be 2 months from previous due date",
      renewedLoan.getString("dueDate"),
      isEquivalentTo(newDueDate));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canOverrideRenewalWhenDateFallsOutsideOfTheDateRangesInTheRollingLoanPolicy() throws
    InterruptedException,
    ExecutionException,
    TimeoutException, MalformedURLException {

    final DateTime renewalDate = DateTime.now();

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    DateTime loanDueDate =
      new DateTime(2018, DateTimeConstants.APRIL, 21, 11, 21, 43);
    final IndividualResource loan = loansFixture.checkOutByBarcode(smallAngryPlanet, jessica, loanDueDate);

    final UUID loanId = loan.getId();

    FixedDueDateSchedulesBuilder fixedDueDateSchedules = new FixedDueDateSchedulesBuilder()
      .withName("Fixed Due Date Schedule")
      .addSchedule(wholeMonth(2018, DateTimeConstants.FEBRUARY))
      .addSchedule(forDay(renewalDate));

    final UUID fixedDueDateSchedulesId = fixedDueDateScheduleClient.create(
      fixedDueDateSchedules).getId();

    //Need to remember in order to delete after test
    schedulesToDelete.add(fixedDueDateSchedulesId);

    LoanPolicyBuilder currentDueDateRollingPolicy = new LoanPolicyBuilder()
      .withName("Current Due Date Rolling Policy")
      .rolling(Period.months(2))
      .limitedBySchedule(fixedDueDateSchedulesId)
      .renewFromCurrentDueDate();

    UUID dueDateLimitedPolicyId = loanPolicyClient.create(currentDueDateRollingPolicy).getId();

    //Need to remember in order to delete after test
    policiesToDelete.add(dueDateLimitedPolicyId);

    useLoanPolicyAsFallback(dueDateLimitedPolicyId);

    final JsonObject renewedLoan =
      loansFixture.overrideRenewalByBarcode(smallAngryPlanet, jessica, OVERRIDE_COMMENT, null)
        .getJson();

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

    DateTime expectedDueDate = loanDueDate.plusWeeks(3).plusMonths(2);
    assertThat("due date should be 1st of Feb 2019",
      renewedLoan.getString("dueDate"),
      isEquivalentTo(expectedDueDate));
  }

  @Test
  public void canOverrideRenewalWhenLoanReachedRenewalLimitAndDueDateIsNotSpecified() throws
    InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    LoanPolicyBuilder limitedRenewalsPolicy = new LoanPolicyBuilder()
      .withName("Limited Renewals policy")
      .rolling(Period.weeks(1))
      .limitedRenewals(1);

    UUID limitedRenewalsPolicyId = loanPolicyClient.create(limitedRenewalsPolicy).getId();

    //Need to remember in order to delete after test
    policiesToDelete.add(limitedRenewalsPolicyId);

    useLoanPolicyAsFallback(limitedRenewalsPolicyId);

    DateTime loanDate = DateTime.now();
    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica, loanDate).getJson();

    loansFixture.renewLoan(smallAngryPlanet, jessica);

    final JsonObject renewedLoan =
      loansFixture.overrideRenewalByBarcode(smallAngryPlanet, jessica, OVERRIDE_COMMENT, null)
        .getJson();

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
      renewedLoan.getInteger("renewalCount"), is(2));

    DateTime expectedDueDate = loanDate.plusWeeks(3);
    assertThat("due date should be 3 weeks later",
      renewedLoan.getString("dueDate"),
      isEquivalentTo(expectedDueDate));
  }

  @Test
  public void cannotOverrideRenewalWhenLoanDoesNotMatchAnyOfOverrideCases() throws
    InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource loan = loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43));

    LoanPolicyBuilder currentDueDateRollingPolicy = new LoanPolicyBuilder()
      .withName("Current Due Date Rolling Policy")
      .rolling(Period.months(2))
      .renewFromCurrentDueDate();

    UUID dueDateLimitedPolicyId = loanPolicyClient.create(currentDueDateRollingPolicy).getId();

    //Need to remember in order to delete after test
    policiesToDelete.add(dueDateLimitedPolicyId);

    useLoanPolicyAsFallback(dueDateLimitedPolicyId);

    final Response response =
      loansFixture.attemptOverride(smallAngryPlanet, jessica, OVERRIDE_COMMENT, null);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Override renewal does not match any of expected cases: " +
        "item is not renewable, " +
        "reached number of renewals limit or " +
        "renewal date falls outside of the date ranges in the loan policy"))));
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
