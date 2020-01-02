package api.requests;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;

public class RequestsAPILoanHistoryTests extends APITests {
  @Test
  public void creatingRecallRequestChangesTheOpenLoanForTheSameItem() {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    IndividualResource originalLoan = loansFixture.checkOutByBarcode(smallAngryPlanet);
    UUID loanId = originalLoan.getId();

    requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(usersFixture.charlotte().getId()));

    JsonObject loanFromStorage = loansStorageClient.getById(loanId).getJson();

    assertThat("item status snapshot in storage is not checked out",
      loanFromStorage.getString("itemStatus"), is("Checked out"));

    // Verify that loan update was executed only once - to update loan dueDate
    // So we will have correct loan history
    assertThat("Due date was not updated", loanFromStorage.getInstant("dueDate"),
      is(not(originalLoan.getJson().getInstant("dueDate"))));
    assertThat("Action was not updated", loanFromStorage.getString("action"),
      is("recallrequested"));
  }

  @Test
  public void creatingHoldRequestDoesNotChangeClosedLoanForTheSameItem() {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    UUID closedLoanId = loansFixture.checkOutByBarcode(smallAngryPlanet).getId();

    loansFixture.checkInByBarcode(smallAngryPlanet);

    loansFixture.checkOutByBarcode(smallAngryPlanet);

    requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.charlotte()));

    JsonObject closedLoanFromStorage = loansStorageClient.getById(closedLoanId)
      .getJson();

    assertThat("action snapshot for closed loan should not change",
      closedLoanFromStorage.getString("action"), is("checkedin"));

    assertThat("item status snapshot for closed loan should not change",
      closedLoanFromStorage.getString("itemStatus"), is("Available"));
  }

  @Test
  public void creatingRecallRequestDoesNotChangeClosedLoanForTheSameItem() {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    UUID closedLoanId = loansFixture.checkOutByBarcode(smallAngryPlanet).getId();

    loansFixture.checkInByBarcode(smallAngryPlanet);

    loansFixture.checkOutByBarcode(smallAngryPlanet);

    requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.charlotte()));

    JsonObject closedLoanFromStorage = loansStorageClient.getById(closedLoanId)
      .getJson();

    assertThat("action snapshot for closed loan should not change",
      closedLoanFromStorage.getString("action"), is("checkedin"));

    assertThat("item status snapshot for closed loan should not change",
      closedLoanFromStorage.getString("itemStatus"), is("Available"));
  }

  @Test
  public void creatingHoldRequestDoesNotChangeOpenLoanForDifferentItem() {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final InventoryItemResource nod = itemsFixture.basedUponNod();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(smallAngryPlanet);

    UUID loanForOtherItemId = loansFixture.checkOutByBarcode(nod).getId();

    requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
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
  public void creatingRecallRequestDoesNotChangeOpenLoanForDifferentItem() {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final InventoryItemResource nod = itemsFixture.basedUponNod();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(smallAngryPlanet);

    UUID loanForOtherItemId = loansFixture.checkOutByBarcode(nod).getId();

    requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
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
  public void creatingHoldRequestStillSucceedsWhenThereIsNoLoan() {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    UUID loanId = loansFixture.checkOutByBarcode(smallAngryPlanet).getId();

    loansClient.delete(loanId);

    requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.charlotte()));
  }

  @Test
  public void creatingRecallRequestStillSucceedsWhenThereIsNoLoan() {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    UUID loanId = loansFixture.checkOutByBarcode(smallAngryPlanet).getId();

    loansClient.delete(loanId);

    requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.charlotte()));
  }
}
