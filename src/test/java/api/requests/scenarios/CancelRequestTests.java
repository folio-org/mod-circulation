package api.requests.scenarios;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.joda.time.DateTimeZone.UTC;

import java.net.MalformedURLException;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.junit.Test;

import api.support.APITests;
import io.vertx.core.json.JsonObject;

public class CancelRequestTests extends APITests {
  @Test
  public void canCancelRequest()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

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
  public void canCancelRequestInMiddleOfTheQueue()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

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
  public void canCancelRequestAtTheBeginningOfTheQueue()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

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
  public void canCancelRequestAtTheEndOfTheQueue()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

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
}
