package api.loans.scenarios;

import static api.support.matchers.ItemMatchers.available;
import static api.support.matchers.ItemMatchers.awaitingPickup;
import static api.support.matchers.ItemMatchers.inTransit;
import static api.support.matchers.ItemMatchers.withdrawn;
import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;

import org.folio.circulation.support.http.client.IndividualResource;
import org.junit.Test;

import api.support.APITests;
import api.support.CheckInByBarcodeResponse;
import api.support.builders.ItemBuilder;
import api.support.builders.RequestBuilder;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;

public class CheckInWithdrawnItemTest extends APITests {
  @Test
  public void canCheckInAtHomeLocation() {
    final InventoryItemResource item = itemsFixture
      .basedUponSmallAngryPlanet(ItemBuilder::withdrawn);

    assertThat(item.getJson(), withdrawn());

    final CheckInByBarcodeResponse response = loansFixture.checkInByBarcode(item);

    assertThat(response.getJson(), allOf(
      hasNoJsonPath("loan"),
      hasJsonPath("item.status.name", "Available")
    ));

    assertThat(itemsClient.getById(item.getId()).getJson(), available());
  }

  @Test
  public void canCheckInAtNonHomeLocation() {
    final InventoryItemResource item = itemsFixture
      .basedUponSmallAngryPlanet(ItemBuilder::withdrawn);

    assertThat(item.getJson(), withdrawn());

    final CheckInByBarcodeResponse response = loansFixture.checkInByBarcode(item,
      servicePointsFixture.cd2().getId());

    assertThat(response.getJson(), allOf(
      hasNoJsonPath("loan"),
      hasJsonPath("item.status.name", "In transit")
    ));

    assertThat(itemsClient.getById(item.getId()).getJson(), inTransit());
  }

  @Test
  public void shouldStartRequestFulfillmentIfCheckedInAtPickupLocation() {
    final InventoryItemResource item = itemsFixture.basedUponSmallAngryPlanet();

    final IndividualResource request = requestsFixture.place(new RequestBuilder()
      .forItem(item)
      .page()
      .by(usersFixture.jessica())
      .fulfilToHoldShelf()
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));

    itemsClient.replace(item.getId(), item.getJson().copy()
      .put("status", new JsonObject().put("name", "Withdrawn")));

    final CheckInByBarcodeResponse response = loansFixture.checkInByBarcode(item);

    assertThat(response.getJson(), allOf(
      hasNoJsonPath("loan"),
      hasJsonPath("item.status.name", "Awaiting pickup")
    ));

    assertThat(itemsClient.getById(item.getId()).getJson(), awaitingPickup());

    assertThat(requestsFixture.getById(request.getId()).getJson(),
      hasJsonPath("status", "Open - Awaiting pickup"));
  }

  @Test
  public void shouldStartRequestFulfillmentIfCheckedInAtNotPickupLocation() {
    final InventoryItemResource item = itemsFixture.basedUponSmallAngryPlanet();

    final IndividualResource request = requestsFixture.place(new RequestBuilder()
      .forItem(item)
      .page()
      .by(usersFixture.jessica())
      .fulfilToHoldShelf()
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));

    itemsClient.replace(item.getId(), item.getJson().copy()
      .put("status", new JsonObject().put("name", "Withdrawn")));

    final CheckInByBarcodeResponse response = loansFixture.checkInByBarcode(item,
      servicePointsFixture.cd2().getId());

    assertThat(response.getJson(), allOf(
      hasNoJsonPath("loan"),
      hasJsonPath("item.status.name", "In transit")
    ));

    assertThat(itemsClient.getById(item.getId()).getJson(), inTransit());

    assertThat(requestsFixture.getById(request.getId()).getJson(),
      hasJsonPath("status", "Open - In transit"));
  }
}
