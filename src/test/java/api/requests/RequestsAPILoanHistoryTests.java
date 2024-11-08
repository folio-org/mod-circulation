package api.requests;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.util.UUID;

import api.support.http.IndividualResource;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import api.support.http.ItemResource;
import io.vertx.core.json.JsonObject;

class RequestsAPILoanHistoryTests extends APITests {
  @Test
  void creatingRecallRequestChangesTheOpenLoanForTheSameItem() {

    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    IndividualResource originalLoan = checkOutFixture.checkOutByBarcode(smallAngryPlanet);
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
  void checkOutShouldNotTruncateLoanIfRecallRequestExistsForAnotherItemOfTheSameInstanceIfTlrIsEnabled() {
    settingsFixture.enableTlrFeature();
    final var items = itemsFixture.createMultipleItemsForTheSameInstance(2);
    var steve = usersFixture.steve();
    var charlotte = usersFixture.charlotte();

    ItemResource firstItem = items.get(0);
    checkOutFixture.checkOutByBarcode(firstItem, steve);
    requestsFixture.recallItem(firstItem, charlotte);

    var secondItem = items.get(1);
    var loanAfterCheckOut = checkOutFixture.checkOutByBarcode(secondItem, charlotte);
    var loanAfterCheckOutJson = loanAfterCheckOut.getJson();
    assertThat("Action was not updated", loanAfterCheckOutJson.getString("action"),
      is("checkedout"));

    requestsFixture.recallItem(secondItem, steve);

    var loanAfterRecallJson = loansFixture.getLoanById(loanAfterCheckOut.getId()).getJson();
    assertThat("Due date was not updated", loanAfterCheckOutJson.getInstant("dueDate"),
      is(not(loanAfterRecallJson.getInstant("dueDate"))));
    assertThat("Action was not updated", loanAfterRecallJson.getString("action"),
      is("recallrequested"));
  }

  @Test
  void creatingHoldRequestDoesNotChangeClosedLoanForTheSameItem() {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    UUID closedLoanId = checkOutFixture.checkOutByBarcode(smallAngryPlanet).getId();

    checkInFixture.checkInByBarcode(smallAngryPlanet);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet);

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
  void creatingRecallRequestDoesNotChangeClosedLoanForTheSameItem() {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    UUID closedLoanId = checkOutFixture.checkOutByBarcode(smallAngryPlanet).getId();

    checkInFixture.checkInByBarcode(smallAngryPlanet);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet);

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
  void creatingHoldRequestDoesNotChangeOpenLoanForDifferentItem() {

    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final ItemResource nod = itemsFixture.basedUponNod();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet);

    UUID loanForOtherItemId = checkOutFixture.checkOutByBarcode(nod).getId();

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
  void creatingRecallRequestDoesNotChangeOpenLoanForDifferentItem() {

    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final ItemResource nod = itemsFixture.basedUponNod();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet);

    UUID loanForOtherItemId = checkOutFixture.checkOutByBarcode(nod).getId();

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
  void creatingHoldRequestStillSucceedsWhenThereIsNoLoan() {

    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    UUID loanId = checkOutFixture.checkOutByBarcode(smallAngryPlanet).getId();

    loansClient.delete(loanId);

    requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.charlotte()));
  }

  @Test
  void creatingRecallRequestStillSucceedsWhenThereIsNoLoan() {

    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    UUID loanId = checkOutFixture.checkOutByBarcode(smallAngryPlanet).getId();

    loansClient.delete(loanId);

    requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.charlotte()));
  }
}
