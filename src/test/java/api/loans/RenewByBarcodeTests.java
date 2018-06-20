package api.loans;

import api.support.builders.FixedDueDateSchedule;
import api.support.builders.FixedDueDateSchedulesBuilder;
import api.support.builders.LoanPolicyBuilder;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.ValidationError;
import org.hamcrest.Matcher;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static api.support.matchers.JsonObjectMatchers.*;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class RenewByBarcodeTests extends RenewalTests {

  @Test
  public void cannotRenewWhenLoanPolicyDoesNotExist()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    final UUID nonExistentloanPolicyId = UUID.randomUUID();

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43));

    useLoanPolicyAsFallback(nonExistentloanPolicyId);

    final Response response = loansFixture.attemptRenewal(500, smallAngryPlanet, jessica);

    assertThat(response.getBody(), is(String.format(
      "Loan policy %s could not be found, please check loan rules", nonExistentloanPolicyId)));
  }

  @Test
  public void cannotRenewWhenRenewalLimitReached()
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

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43, DateTimeZone.UTC));

    renew(smallAngryPlanet, jessica);
    renew(smallAngryPlanet, jessica);
    renew(smallAngryPlanet, jessica);

    final Response response = attemptRenewal(smallAngryPlanet, jessica);

    assertThat(response.getJson(), hasSoleErrorFor(
      "loanPolicyId", limitedRenewalsPolicyId.toString()));

    assertThat(response.getJson(), hasSoleErrorMessageContaining(
      "loan has reached its maximum number of renewals"));
  }

  @Test
  public void multipleReasonsWhyCannotRenewWhenRenewalLimitReachedAndDueDateNotChanged()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    //TODO: Replace with better example when can fix system date
    FixedDueDateSchedulesBuilder yesterdayAndTodayOnlySchedules = new FixedDueDateSchedulesBuilder()
      .withName("Yesterday and Today Only Due Date Limit")
      .addSchedule(FixedDueDateSchedule.yesterdayOnly())
      .addSchedule(FixedDueDateSchedule.todayOnly());

    final UUID yesterdayAndTodayOnlySchedulesId = fixedDueDateScheduleClient.create(
      yesterdayAndTodayOnlySchedules).getId();

    //Need to remember in order to delete after test
    schedulesToDelete.add(yesterdayAndTodayOnlySchedulesId);

    LoanPolicyBuilder limitedRenewalsPolicy = new LoanPolicyBuilder()
      .withName("Limited Renewals And Limited Due Date Policy")
      .fixed(yesterdayAndTodayOnlySchedulesId)
      .limitedRenewals(1);

    UUID limitedRenewalsPolicyId = loanPolicyClient.create(limitedRenewalsPolicy).getId();

    //Need to remember in order to delete after test
    policiesToDelete.add(limitedRenewalsPolicyId);

    useLoanPolicyAsFallback(limitedRenewalsPolicyId);

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      DateTime.now(DateTimeZone.UTC).minusDays(1)).getJson();

    renew(smallAngryPlanet, jessica);

    final Response response = attemptRenewal(smallAngryPlanet, jessica);

    assertThat(response.getJson(), hasSoleErrorFor(
      "loanPolicyId", limitedRenewalsPolicyId.toString()));

    assertThat(response.getJson(), hasErrorMessageContaining(
      "loan has reached its maximum number of renewals"));

    assertThat(response.getJson(),
      hasErrorMessageContaining("renewal at this time would not change the due date"));
  }

  @Test
  public void cannotRenewWhenNonRenewableRollingPolicy()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    LoanPolicyBuilder limitedRenewalsPolicy = new LoanPolicyBuilder()
      .withName("Non Renewable Policy")
      .rolling(Period.days(2))
      .notRenewable();

    UUID notRenewablePolicyId = loanPolicyClient.create(limitedRenewalsPolicy).getId();

    //Need to remember in order to delete after test
    policiesToDelete.add(notRenewablePolicyId);

    useLoanPolicyAsFallback(notRenewablePolicyId);

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43, DateTimeZone.UTC));

    final Response response = attemptRenewal(smallAngryPlanet, jessica);

    assertThat(response.getJson(), hasSoleErrorMessageContaining(
      "items with this loan policy cannot be renewed"));
  }

  @Test
  public void cannotRenewWhenNonRenewableFixedPolicy()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    //TODO: Replace with better example when can fix system date
    FixedDueDateSchedulesBuilder todayOnlySchedules = new FixedDueDateSchedulesBuilder()
      .withName("Today Only Due Date Limit")
      .addSchedule(FixedDueDateSchedule.todayOnly());

    final UUID todayOnlySchedulesId = fixedDueDateScheduleClient.create(
      todayOnlySchedules).getId();

    //Need to remember in order to delete after test
    schedulesToDelete.add(todayOnlySchedulesId);

    LoanPolicyBuilder limitedRenewalsPolicy = new LoanPolicyBuilder()
      .withName("Non Renewable Policy")
      .fixed(todayOnlySchedulesId)
      .notRenewable();

    UUID notRenewablePolicyId = loanPolicyClient.create(limitedRenewalsPolicy).getId();

    //Need to remember in order to delete after test
    policiesToDelete.add(notRenewablePolicyId);

    useLoanPolicyAsFallback(notRenewablePolicyId);

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      DateTime.now(DateTimeZone.UTC));

    final Response response = attemptRenewal(smallAngryPlanet, jessica);

    assertThat(response.getJson(), hasSoleErrorMessageContaining(
      "items with this loan policy cannot be renewed"));
  }

  @Test
  public void cannotRenewWhenLoaneeCannotBeFound()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    loansFixture.checkOut(smallAngryPlanet, steve);

    usersClient.delete(steve.getId());

    Response response = attemptRenewal(smallAngryPlanet, steve);

    assertThat(response.getJson(), hasSoleErrorMessageContaining(
      "user is not found"));

    //Occurs when current loanee is not found, so relates to loan rather than user in request
    assertThat(response.getJson(), hasSoleErrorFor(
      "userId", steve.getId().toString()));
  }

  @Override
  Response attemptRenewal(IndividualResource user, IndividualResource item) {
    return loansFixture.attemptRenewal(user, item);
  }

  @Override
  IndividualResource renew(IndividualResource user, IndividualResource item) {
    return loansFixture.renewLoan(user, item);
  }

  @Override
  Matcher<ValidationError> hasUserRelatedParameter(IndividualResource user) {
    return hasParameter("userBarcode", user.getJson().getString("barcode"));
  }

  @Override
  Matcher<ValidationError> hasItemRelatedParameter(IndividualResource item) {
    return hasParameter("itemBarcode", item.getJson().getString("barcode"));
  }

  @Override
  Matcher<ValidationError> hasItemNotFoundMessage(IndividualResource item) {
    return hasMessage(String.format("No item with barcode %s exists",
      item.getJson().getString("barcode")));
  }
}
