package api.requests;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.hamcrest.core.Is;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;


public class RequestsAPILoanHistoryTests extends APITests {
  @Test
  public void creatingHoldRequestChangesTheOpenLoanForTheSameItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    UUID loanId = loansFixture.checkOutByBarcode(smallAngryPlanet).getId();

    requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withRequesterId(usersFixture.charlotte().getId()));

    JsonObject loanFromStorage = loansStorageClient.getById(loanId).getJson();

    assertThat("action snapshot in storage is not hold requested",
      loanFromStorage.getString("action"), is("holdrequested"));

    assertThat("item status snapshot in storage is not checked out",
      loanFromStorage.getString("itemStatus"), is("Checked out"));
  }

  @Test
  public void creatingRecallRequestChangesTheOpenLoanForTheSameItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    UUID loanId = loansFixture.checkOutByBarcode(smallAngryPlanet).getId();

    requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withRequesterId(usersFixture.charlotte().getId()));

    JsonObject loanFromStorage = loansStorageClient.getById(loanId).getJson();

    assertThat("item status snapshot in storage is not checked out",
      loanFromStorage.getString("itemStatus"), is("Checked out"));
  }

  @Test
  public void failureCreatingPageRequestDoesNotChangeTheOpenLoanForSameItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    //Cannot create a page request for open loan item anymore.
    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    UUID loanId = loansFixture.checkOutByBarcode(smallAngryPlanet).getId();

    Response pageRequestResponse = requestsClient.attemptCreate(new RequestBuilder()
      .page()
      .forItem(smallAngryPlanet)
      .withRequesterId(usersFixture.charlotte().getId()));

    assertThat(
      String.format("Failed to create page request: %s",
        pageRequestResponse.getBody()), pageRequestResponse.getStatusCode(), Is.is(422));

    JsonObject loanFromStorage = loansStorageClient.getById(loanId).getJson();

    assertThat("action snapshot in storage is not still checked out",
      loanFromStorage.getString("action"), is("checkedout"));

    assertThat("item status snapshot in storage is not still checked out",
      loanFromStorage.getString("itemStatus"), is("Checked out"));
  }

  @Test
  public void creatingHoldRequestDoesNotChangeClosedLoanForTheSameItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    UUID closedLoanId = loansFixture.checkOutByBarcode(smallAngryPlanet).getId();

    loansFixture.checkInByBarcode(smallAngryPlanet);

    loansFixture.checkOutByBarcode(smallAngryPlanet);

    requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .by(usersFixture.charlotte()));

    JsonObject closedLoanFromStorage = loansStorageClient.getById(closedLoanId)
      .getJson();

    assertThat("action snapshot for closed loan should not change",
      closedLoanFromStorage.getString("action"), is("checkedin"));

    assertThat("item status snapshot for closed loan should not change",
      closedLoanFromStorage.getString("itemStatus"), is("Available"));
  }

  @Test
  public void creatingRecallRequestDoesNotChangeClosedLoanForTheSameItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    UUID closedLoanId = loansFixture.checkOutByBarcode(smallAngryPlanet).getId();

    loansFixture.checkInByBarcode(smallAngryPlanet);

    loansFixture.checkOutByBarcode(smallAngryPlanet);

    requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .by(usersFixture.charlotte()));

    JsonObject closedLoanFromStorage = loansStorageClient.getById(closedLoanId)
      .getJson();

    assertThat("action snapshot for closed loan should not change",
      closedLoanFromStorage.getString("action"), is("checkedin"));

    assertThat("item status snapshot for closed loan should not change",
      closedLoanFromStorage.getString("itemStatus"), is("Available"));
  }

  @Test
  public void creatingHoldRequestDoesNotChangeOpenLoanForDifferentItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final InventoryItemResource nod = itemsFixture.basedUponNod();

    loansFixture.checkOutByBarcode(smallAngryPlanet);

    UUID loanForOtherItemId = loansFixture.checkOutByBarcode(nod).getId();

    requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .by(usersFixture.james()));

    JsonObject storageLoanForOtherItem = loansStorageClient
      .getById(loanForOtherItemId)
      .getJson();

    assertThat("action snapshot for open loan for other item should not change",
      storageLoanForOtherItem.getString("action"), is("checkedout"));

    assertThat("item status snapshot for open loan for other item should not change",
      storageLoanForOtherItem.getString("itemStatus"), is("Checked out"));
  }

  @Test
  public void creatingRecallRequestDoesNotChangeOpenLoanForDifferentItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final InventoryItemResource nod = itemsFixture.basedUponNod();

    loansFixture.checkOutByBarcode(smallAngryPlanet);

    UUID loanForOtherItemId = loansFixture.checkOutByBarcode(nod).getId();

    requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .by(usersFixture.james()));

    JsonObject storageLoanForOtherItem = loansStorageClient
      .getById(loanForOtherItemId)
      .getJson();

    assertThat("action snapshot for open loan for other item should not change",
      storageLoanForOtherItem.getString("action"), is("checkedout"));

    assertThat("item status snapshot for open loan for other item should not change",
      storageLoanForOtherItem.getString("itemStatus"), is("Checked out"));
  }

  @Test
  public void creatingHoldRequestStillSucceedsWhenThereIsNoLoan()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    UUID loanId = loansFixture.checkOutByBarcode(smallAngryPlanet).getId();

    loansClient.delete(loanId);

    requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .by(usersFixture.charlotte()));
  }

  @Test
  public void creatingRecallRequestStillSucceedsWhenThereIsNoLoan()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    UUID loanId = loansFixture.checkOutByBarcode(smallAngryPlanet).getId();

    loansClient.delete(loanId);

    requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .by(usersFixture.charlotte()));
  }
}
