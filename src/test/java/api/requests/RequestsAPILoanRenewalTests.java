package api.requests;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.joda.time.DateTimeConstants.APRIL;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.OverrideRenewalByBarcodeRequestBuilder;
import api.support.builders.RenewByBarcodeRequestBuilder;
import api.support.builders.RenewByIdRequestBuilder;
import api.support.builders.RequestBuilder;
import api.support.http.InventoryItemResource;

public class RequestsAPILoanRenewalTests extends APITests {

  @Test
  public void forbidRenewalLoanByBarcode_firstRequestIsRecall() throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.charlotte()));

    Response response = renewByBarcodeClient.attemptCreate(
      new RenewByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .forUser(rebecca));

    assertThat(response.getStatusCode(), is(422));

    String message = response.getJson()
      .getJsonArray("errors")
      .getJsonObject(0)
      .getString("message");

    assertThat(message, is("Items cannot be renewed when there is an active recall request"));
  }

  @Test
  public void allowRenewalLoanByBarcode_firstRequestIsHold() throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.charlotte()));

    Response response = renewByBarcodeClient.attemptCreate(
      new RenewByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .forUser(rebecca));

    assertThat(response.getStatusCode(), is(200));
    assertThat(response.getJson().getString("action"), is("renewed"));
  }

  @Test
  public void forbidRenewalLoanById_firstRequestIsRecall() throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.charlotte()));

    Response response = renewByIdClient.attemptCreate(
      new RenewByIdRequestBuilder()
        .forItem(smallAngryPlanet)
        .forUser(rebecca));

    assertThat(response.getStatusCode(), is(422));

    String message = response.getJson()
      .getJsonArray("errors")
      .getJsonObject(0)
      .getString("message");

    assertThat(message, is("Items cannot be renewed when there is an active recall request"));
  }

  @Test
  public void allowRenewalLoanById_firstRequestIsHold() throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.charlotte()));

    Response response = renewByIdClient.attemptCreate(
      new RenewByIdRequestBuilder()
        .forItem(smallAngryPlanet)
        .forUser(rebecca));

    assertThat(response.getStatusCode(), is(200));
    assertThat(response.getJson().getString("action"), is("renewed"));
  }

  @Test
  public void forbidOverrideRenewalLoanByBarcode_firstRequestIsRecall() throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.charlotte()));

    Response response = overrideRenewalByBarcodeClient.attemptCreate(
      new OverrideRenewalByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .forUser(rebecca)
        .withComment("Renewal override")
        .withDueDate("2018-12-21T13:30:00Z")
    );

    assertThat(response.getStatusCode(), is(422));

    String message = response.getJson()
      .getJsonArray("errors")
      .getJsonObject(0)
      .getString("message");

    assertThat(message, is("Items cannot be renewed when there is an active recall request"));
  }

  @Test
  public void allowOverrideRenewalLoanByBarcode_firstRequestIsHold() throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    DateTime loanDueDate = new DateTime(
      2018, APRIL, 21,
      11, 21, 43
    );

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca, loanDueDate);

    LoanPolicyBuilder nonRenewablePolicy = new LoanPolicyBuilder()
      .withName("Non Renewable Policy")
      .rolling(Period.days(2))
      .notRenewable();

    UUID nonRenewablePolicyId = loanPoliciesFixture.create(nonRenewablePolicy).getId();

    useLoanPolicyAsFallback(
      nonRenewablePolicyId,
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId()
    );
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.charlotte()));

    renewByIdClient.attemptCreate(
      new RenewByIdRequestBuilder()
        .forItem(smallAngryPlanet)
        .forUser(rebecca));

    Response response = overrideRenewalByBarcodeClient.attemptCreate(
      new OverrideRenewalByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .forUser(rebecca)
        .withComment("Renewal override")
        .withDueDate("2019-03-21T13:30:00Z")
    );

    assertThat(response.getStatusCode(), is(200));
    assertThat(response.getJson().getString("action"), is("Renewed through override"));
  }
}
