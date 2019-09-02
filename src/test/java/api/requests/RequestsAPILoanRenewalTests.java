package api.requests;

import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.joda.time.DateTimeConstants.APRIL;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import api.support.builders.LoanPolicyBuilder;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import api.support.http.InventoryItemResource;

public class RequestsAPILoanRenewalTests extends APITests {

  private static final String ITEMS_CANNOT_BE_RENEWED_MSG = "items cannot be renewed when there is an active recall request";
  private static final String EXPECTED_REASON_LOAN_IS_NOT_RENEWABLE = "loan is not renewable";
  private static final String EXPECTED_REASON_LOAN_IS_NOT_LOANABLE = "item is not loanable";

  @Test
  public void forbidRenewalLoanByBarcodeWhenFirstRequestInQueueIsRecall() throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.charlotte()));

    Response response = loansFixture.attemptRenewal(smallAngryPlanet, rebecca);

    assertThat(response.getJson(), hasErrorWith(hasMessage(ITEMS_CANNOT_BE_RENEWED_MSG)));
  }

  @Test
  public void allowRenewalLoanByBarcodeWhenFirstRequestInQueueIsHoldAndRenewingIsAllowedInLoanPolicy()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.charlotte()));

    Response response = loansFixture.attemptRenewal(200, smallAngryPlanet, rebecca);
    assertThat(response.getJson().getString("action"), is("renewed"));
  }

  @Test
  public void forbidRenewalLoanByIdWhenFirstRequestInQueueIsRecall() throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.charlotte()));

    Response response = loansFixture.attemptRenewal(smallAngryPlanet, rebecca);

    assertThat(response.getJson(), hasErrorWith(hasMessage(ITEMS_CANNOT_BE_RENEWED_MSG)));
  }

  @Test
  public void allowRenewalLoanByIdWhenFirstRequestInQueueIsHoldAndRenewingIsAllowedInLoanPolicy()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.charlotte()));


    IndividualResource response = loansFixture.renewLoanById(smallAngryPlanet, rebecca);

    assertThat(response.getJson().getString("action"), is("renewed"));
  }

  @Test
  public void allowRenewalOverrideWhenFirstRequestIsRecall()
    throws MalformedURLException,
    InterruptedException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    DateTime loanDate = new DateTime(2018, APRIL, 21, 11, 21, 43);
    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca, loanDate);

    requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.charlotte()));

    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.steve()));

    loansFixture.attemptRenewalById(smallAngryPlanet, rebecca);

    IndividualResource response = loansFixture.overrideRenewalByBarcode(
      smallAngryPlanet,
      rebecca,
      "Renewal override",
      "2018-12-21T13:30:00Z");

    assertThat(response.getJson().getString("action"), is("renewedThroughOverride"));
  }

  @Test
  public void forbidRenewalOverrideWhenFirstRequestIsNotRecall()
    throws MalformedURLException,
    InterruptedException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    DateTime loanDate = new DateTime(2018, APRIL, 21, 11, 21, 43);
    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca, loanDate);

    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.steve()));

    requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.charlotte()));

    Response overrideResponse = loansFixture.attemptOverride(
      smallAngryPlanet,
      rebecca,
      "Renewal override",
      "2018-12-21T13:30:00Z");

    assertThat(overrideResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Override renewal does not match any of expected cases: " +
        "item is not loanable, " +
        "item is not renewable, " +
        "reached number of renewals limit," +
        "renewal date falls outside of the date ranges in the loan policy, " +
        "items cannot be renewed when there is an active recall request"))));
  }

  @Test
  public void multipleRenewalFailuresWhenItemHasOpenRecallRequestAndLoanIsNotRenewable()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    LoanPolicyBuilder limitedRenewalsPolicy = new LoanPolicyBuilder()
      .withName("Non Renewable Policy")
      .rolling(Period.days(2))
      .notRenewable();

    UUID notRenewablePolicyId = loanPoliciesFixture
      .create(limitedRenewalsPolicy).getId();

    useLoanPolicyAsFallback(
      notRenewablePolicyId,
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId()
    );

    requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.charlotte()));

    Response response = loansFixture.attemptRenewal(smallAngryPlanet, rebecca);

    assertThat(response.getJson(), hasErrorWith(hasMessage(ITEMS_CANNOT_BE_RENEWED_MSG)));
    assertThat(response.getJson(), hasErrorWith(hasMessage(EXPECTED_REASON_LOAN_IS_NOT_RENEWABLE)));
  }

  @Test
  public void multipleRenewalFailuresWhenItemHasOpenRecallRequestAndLoanIsNotLoanable()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.charlotte()));

    LoanPolicyBuilder limitedRenewalsPolicy = new LoanPolicyBuilder()
      .withName("Non Renewable Policy")
      .rolling(Period.days(2))
      .withLoanable(false);

    UUID notRenewablePolicyId = loanPoliciesFixture
      .create(limitedRenewalsPolicy).getId();

    useLoanPolicyAsFallback(
      notRenewablePolicyId,
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId()
    );

    Response response = loansFixture.attemptRenewal(smallAngryPlanet, rebecca);

    assertThat(response.getJson(), hasErrorWith(hasMessage(ITEMS_CANNOT_BE_RENEWED_MSG)));
    assertThat(response.getJson(), hasErrorWith(hasMessage(EXPECTED_REASON_LOAN_IS_NOT_LOANABLE)));
  }
}
