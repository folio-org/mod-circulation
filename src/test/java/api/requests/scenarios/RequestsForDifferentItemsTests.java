package api.requests.scenarios;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;

import api.support.http.IndividualResource;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.RequestBuilder;

class RequestsForDifferentItemsTests extends APITests {
  @Test
  void requestsCreatedForDifferentItemsAreInDifferentQueues() {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource nod = itemsFixture.basedUponNod();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    final IndividualResource steve = usersFixture.steve();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, steve);
    checkOutFixture.checkOutByBarcode(nod, steve);

    final IndividualResource firstRequestForSmallAngryPlanet = requestsClient.create(
      new RequestBuilder()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(smallAngryPlanet)
      .by(usersFixture.jessica())
      .create());

    final IndividualResource firstRequestForNod = requestsClient.create(
      new RequestBuilder()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(nod)
      .by(usersFixture.rebecca())
      .create());

    final IndividualResource secondRequestForNod = requestsClient.create(
      new RequestBuilder()
        .hold()
        .withPickupServicePointId(pickupServicePointId)
        .forItem(nod)
        .by(usersFixture.james())
        .create());

    final IndividualResource secondRequestForSmallAngryPlannet = requestsClient.create(
      new RequestBuilder()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(smallAngryPlanet)
      .by(usersFixture.charlotte())
      .create());

    assertThat(firstRequestForSmallAngryPlanet.getJson().getInteger("position"), is(1));
    assertThat(secondRequestForSmallAngryPlannet.getJson().getInteger("position"), is(2));
    assertThat(firstRequestForNod.getJson().getInteger("position"), is(1));
    assertThat(secondRequestForNod.getJson().getInteger("position"), is(2));

    assertThat("HoldingsRecordId field should not present",
      firstRequestForSmallAngryPlanet.getJson().getJsonObject("item").containsKey("holdingsRecordId"), is(false));
    assertThat("HoldingsRecordId field should not present",
      secondRequestForSmallAngryPlannet.getJson().getJsonObject("item").containsKey("holdingsRecordId"), is(false));
    assertThat("HoldingsRecordId field should not present",
      firstRequestForNod.getJson().getJsonObject("item").containsKey("holdingsRecordId"), is(false));
    assertThat("HoldingsRecordId field should not present",
      secondRequestForNod.getJson().getJsonObject("item").containsKey("holdingsRecordId"), is(false));
  }
}
