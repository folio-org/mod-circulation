package api.requests.scenarios;

import static api.support.http.InterfaceUrls.requestsUrl;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static org.folio.circulation.domain.RequestStatus.CLOSED_CANCELLED;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.joda.time.DateTimeZone.UTC;

import java.util.Collection;
import java.util.UUID;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.junit.Test;

import api.support.APITests;
import api.support.MultipleJsonRecords;
import api.support.builders.RequestBuilder;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;

public class CancelRequestTests extends APITests {
  @Test
  public void canCancelRequest() {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource james = usersFixture.james();
    final IndividualResource jessica = usersFixture.jessica();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    final IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, DateTime.now(UTC).minusHours(5));

    requestsFixture.cancelRequest(requestByJessica);

    final JsonObject cancelledRequest = requestsClient.get(requestByJessica).getJson();

    assertThat("Should be cancelled",
      cancelledRequest.getString("status"), is("Closed - Cancelled"));

    assertThat("Should not have a position",
      cancelledRequest.containsKey("position"), is(false));

    assertThat("Should retain stored item summary",
      cancelledRequest.containsKey("item"), is(true));

    assertThat("Should retain stored requester summary",
      cancelledRequest.containsKey("requester"), is(true));
  }

  @Test
  public void canCancelRequestInMiddleOfTheQueue() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    IndividualResource charlotte = usersFixture.charlotte();
    IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    final IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, DateTime.now(UTC).minusHours(5));

    final IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, DateTime.now(UTC).minusHours(4));

    final IndividualResource requestByCharlotte = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, charlotte, DateTime.now(UTC).minusHours(3));

    final IndividualResource requestByRebecca = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, rebecca, DateTime.now(UTC).minusHours(2));

    requestsFixture.cancelRequest(requestBySteve);

    MultipleRecords<JsonObject> queue = requestsFixture.getQueueFor(smallAngryPlanet);

    assertThat(queue.getTotalRecords(), is(3));

    final Collection<Integer> positions = queue
      .mapRecords(request -> request.getInteger("position"))
      .getRecords();

    assertThat("Should have contiguous positions", positions, contains(1, 2, 3));

    final Collection<UUID> requestIds = queue
      .mapRecords(request -> UUID.fromString(request.getString("id")))
      .getRecords();

    assertThat("Should be in same order as before cancellation", requestIds, contains(
      requestByJessica.getId(), requestByCharlotte.getId(), requestByRebecca.getId()));
  }

  @Test
  public void canCancelRequestAtTheBeginningOfTheQueue() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    IndividualResource charlotte = usersFixture.charlotte();
    IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    final IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, DateTime.now(UTC).minusHours(5));

    final IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, DateTime.now(UTC).minusHours(4));

    final IndividualResource requestByCharlotte = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, charlotte, DateTime.now(UTC).minusHours(3));

    final IndividualResource requestByRebecca = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, rebecca, DateTime.now(UTC).minusHours(2));

    requestsFixture.cancelRequest(requestByJessica);

    MultipleRecords<JsonObject> queue = requestsFixture.getQueueFor(smallAngryPlanet);

    assertThat(queue.getTotalRecords(), is(3));

    final Collection<Integer> positions = queue
      .mapRecords(request -> request.getInteger("position"))
      .getRecords();

    assertThat("Should be in contiguous positions", positions, contains(1, 2, 3));

    final Collection<UUID> requestIds = queue
      .mapRecords(request -> UUID.fromString(request.getString("id")))
      .getRecords();

    assertThat("Should be in same order as before cancellation", requestIds, contains(
      requestBySteve.getId(), requestByCharlotte.getId(), requestByRebecca.getId()));
  }

  @Test
  public void canCancelRequestAtTheEndOfTheQueue() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    IndividualResource charlotte = usersFixture.charlotte();
    IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    final IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, DateTime.now(UTC).minusHours(5));

    final IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, DateTime.now(UTC).minusHours(4));

    final IndividualResource requestByCharlotte = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, charlotte, DateTime.now(UTC).minusHours(3));

    final IndividualResource requestByRebecca = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, rebecca, DateTime.now(UTC).minusHours(2));

    requestsFixture.cancelRequest(requestByRebecca);

    MultipleRecords<JsonObject> queue = requestsFixture.getQueueFor(smallAngryPlanet);

    assertThat(queue.getTotalRecords(), is(3));

    final Collection<Integer> positions = queue
      .mapRecords(request -> request.getInteger("position"))
      .getRecords();

    assertThat("Should be in contiguous positions", positions, contains(1, 2, 3));

    final Collection<UUID> requestIds = queue
      .mapRecords(request -> UUID.fromString(request.getString("id")))
      .getRecords();

    assertThat("Should be in same order as before cancellation", requestIds, contains(
      requestByJessica.getId(), requestBySteve.getId(), requestByCharlotte.getId()));
  }

  /**
   * In order for items to appear on the hold shelf clearance report they need
   * to retain the request fulfilment related status after being cancelled
   */
  @Test
  public void cancellingAPartiallyFulfilledPageRequestShouldNotChangeItemStatus() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource jessica = usersFixture.jessica();

    final IndividualResource requestByJessica = requestsFixture.place(
      new RequestBuilder()
      .page()
      .forItem(smallAngryPlanet)
      .by(jessica)
      .fulfilToHoldShelf(servicePointsFixture.cd1()));

    loansFixture.checkInByBarcode(smallAngryPlanet);

    requestsFixture.cancelRequest(requestByJessica);

    final IndividualResource itemAfterCancellation = itemsClient.get(smallAngryPlanet);

    assertThat(itemAfterCancellation, hasItemStatus("Awaiting pickup"));
  }

  @Test
  public void shouldAllowToCancelRequestWithNoPosition() {
    IndividualResource requesterId = usersFixture.rebecca();
    final InventoryItemResource nod = itemsFixture.basedUponNod();

    loansFixture.checkOutByBarcode(nod, requesterId);

    IndividualResource firstHoldRequest = holdRequestWithNoPosition(nod,
      usersFixture.steve());
    IndividualResource secondHoldRequest = holdRequestWithNoPosition(nod,
      usersFixture.charlotte());

    requestsFixture.cancelRequest(firstHoldRequest);
    requestsFixture.cancelRequest(secondHoldRequest);

    MultipleJsonRecords allRequests = requestsFixture.getAllRequests();

    assertThat(allRequests.totalRecords(), is(2));

    JsonObject firstRequest = allRequests.getById(firstHoldRequest.getId());
    JsonObject secondRequest = allRequests.getById(secondHoldRequest.getId());

    assertThat(firstRequest.getString("status"), is(CLOSED_CANCELLED.getValue()));
    assertThat(secondRequest.getString("status"), is(CLOSED_CANCELLED.getValue()));
  }

  private IndividualResource holdRequestWithNoPosition(
    IndividualResource item, IndividualResource requester) {

    JsonObject request = new RequestBuilder()
      .open()
      .hold()
      .forItem(item)
      .by(requester)
      .withPickupServicePoint(servicePointsFixture.cd1())
      .create();

    request.remove("position");

    return requestsStorageClient.create(request);
  }
}
