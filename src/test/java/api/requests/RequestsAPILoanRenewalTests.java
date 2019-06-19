package api.requests;

import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.joda.time.DateTimeConstants.APRIL;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import api.support.http.InventoryItemResource;

public class RequestsAPILoanRenewalTests extends APITests {

  private static final String ITEMS_CANNOT_BE_RENEWED_MSG = "items cannot be renewed when there is an active recall request";

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
  public void allowRenewalLoanByBarcodeWhenFirstRequestInQueueIsHold() throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

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
  public void allowRenewalLoanByIdWhenFirstRequestInQueueIsHold() throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

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

    DateTime loanDueDate = new DateTime(
      2018, APRIL, 21,
      11, 21, 43
    );

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca, loanDueDate);

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

    DateTime loanDueDate = new DateTime(
      2018, APRIL, 21,
      11, 21, 43
    );

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca, loanDueDate);

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

    loansFixture.attemptOverride(
      smallAngryPlanet,
      rebecca,
      "Renewal override",
      "2018-12-21T13:30:00Z");
  }
}
