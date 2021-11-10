package api.requests.scenarios;

import static java.time.ZoneOffset.UTC;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

import java.net.HttpURLConnection;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.http.IndividualResource;
import io.vertx.core.json.JsonObject;

//TODO: Maybe move these tests to scenarios which better describe the situation
class RequestQueueTests extends APITests {
  @Test
  void fulfilledRequestShouldBeRemovedFromQueue() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    IndividualResource charlotte = usersFixture.charlotte();
    IndividualResource rebecca = usersFixture.rebecca();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, jessica, ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC));

    IndividualResource requestBySteve = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, steve, ZonedDateTime.of(2017, 10, 27, 11, 54, 37, 0, UTC));

    IndividualResource requestByCharlotte = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, charlotte, ZonedDateTime.of(2018, 1, 10, 15, 34, 21, 0, UTC));

    IndividualResource requestByRebecca = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, rebecca, ZonedDateTime.of(2018, 2, 4, 7, 4, 53, 0, UTC));

      checkInFixture.checkInByBarcode(smallAngryPlanet);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    requestByJessica = requestsClient.get(requestByJessica);

    assertThat("Should not have a position",
      requestByJessica.getJson().containsKey("position"), is(false));

    requestBySteve = requestsClient.get(requestBySteve);


    assertThat(requestBySteve.getJson().getInteger("position"), is(1));

    retainsStoredSummaries(requestBySteve);

    requestByCharlotte = requestsClient.get(requestByCharlotte);

    assertThat(requestByCharlotte.getJson().getInteger("position"), is(2));

    retainsStoredSummaries(requestByCharlotte);

    requestByRebecca = requestsClient.get(requestByRebecca);

    assertThat(requestByRebecca.getJson().getInteger("position"), is(3));

    retainsStoredSummaries(requestByRebecca);
  }

  private void retainsStoredSummaries(IndividualResource request) {
    assertThat("Updated request in queue should retain stored item summary",
      request.getJson().containsKey("item"), is(true));

    assertThat("Updated request in queue should retain stored requester summary",
      request.getJson().containsKey("requester"), is(true));
  }

  @Test
  void deletedRequestShouldBeRemovedFromQueue() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    IndividualResource charlotte = usersFixture.charlotte();
    IndividualResource rebecca = usersFixture.rebecca();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, jessica, ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC));

    IndividualResource requestBySteve = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, steve, ZonedDateTime.of(2017, 10, 27, 11, 54, 37, 0, UTC));

    IndividualResource requestByCharlotte = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, charlotte, ZonedDateTime.of(2018, 1, 10, 15, 34, 21, 0, UTC));

    IndividualResource requestByRebecca = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, rebecca, ZonedDateTime.of(2018, 2, 4, 7, 4, 53, 0, UTC));

    requestsClient.delete(requestByCharlotte);

    Response getResponse = requestsClient.attemptGet(requestByCharlotte);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));

    requestByJessica = requestsClient.get(requestByJessica);

    assertThat(requestByJessica.getJson().getInteger("position"), is(1));

    retainsStoredSummaries(requestByJessica);

    requestBySteve = requestsClient.get(requestBySteve);

    assertThat(requestBySteve.getJson().getInteger("position"), is(2));

    retainsStoredSummaries(requestBySteve);

    requestByRebecca = requestsClient.get(requestByRebecca);

    assertThat(requestByRebecca.getJson().getInteger("position"), is(3));

    retainsStoredSummaries(requestByRebecca);
  }

  @Test
  void canFetchTheRequestQueueForAnItem() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    IndividualResource charlotte = usersFixture.charlotte();
    IndividualResource rebecca = usersFixture.rebecca();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, jessica, ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC));

    IndividualResource requestBySteve = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, steve, ZonedDateTime.of(2017, 10, 27, 11, 54, 37, 0, UTC));

    IndividualResource requestByCharlotte = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, charlotte, ZonedDateTime.of(2018, 1, 10, 15, 34, 21, 0, UTC));

    IndividualResource requestByRebecca = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, rebecca, ZonedDateTime.of(2018, 2, 4, 7, 4, 53, 0, UTC));

    MultipleRecords<JsonObject> queue = requestsFixture.getQueueFor(smallAngryPlanet);

    //Correct size
    assertThat(queue.getTotalRecords(), is(4));
    assertThat(queue.getRecords().size(), is(4));

    final List<Integer> positions = queue.getRecords().stream()
      .map(request -> request.getInteger("position"))
      .collect(Collectors.toList());

    //In position order
    assertThat(positions, contains(1, 2, 3, 4));

    //Correct requests
    final List<UUID> ids = queue.getRecords().stream()
      .map(request -> request.getString("id"))
      .map(UUID::fromString)
      .collect(Collectors.toList());

    //In position order
    assertThat(ids, contains(
      requestByJessica.getId(),
      requestBySteve.getId(),
      requestByCharlotte.getId(),
      requestByRebecca.getId()));

    //Has extended item properties
    queue.getRecords().forEach(request -> {
      assertThat(String.format("request has an item summary: %s",
        request.encodePrettily()),
        request.containsKey("item"), is(true));

      JsonObject item = request.getJsonObject("item");

      assertThat(String.format("item summary has a holdings record ID: %s",
        request.encodePrettily()),
        item.containsKey("holdingsRecordId"), is(true));

      assertThat(String.format("item summary has an instance ID: %s",
        request.encodePrettily()),
        request.containsKey("instanceId"), is(true));

      assertThat(String.format("item summary has a location: %s",
        request.encodePrettily()),
        item.containsKey("location"), is(true));

      JsonObject location = item.getJsonObject("location");

      assertThat(String.format("location has a name: %s", request.encodePrettily()),
        location.containsKey("name"), is(true));
    });
  }
}
