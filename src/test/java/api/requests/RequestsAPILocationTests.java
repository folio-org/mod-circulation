package api.requests;

import static api.support.JsonCollectionAssistant.getRecordById;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;

public class RequestsAPILocationTests extends APITests {
  @Test
  public void locationIsIncludedForSingleRequest()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final IndividualResource thirdFloor = locationsFixture.thirdFloor();
    final IndividualResource secondFloorEconomics = locationsFixture.secondFloorEconomics();
    final IndividualResource mezzanineDisplayCase = locationsFixture.mezzanineDisplayCase();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      holdingBuilder -> holdingBuilder
        .withPermanentLocation(thirdFloor)
        .withTemporaryLocation(mezzanineDisplayCase),
      itemBuilder -> itemBuilder
        .withNoPermanentLocation()
        .withTemporaryLocation(secondFloorEconomics)
    );

    loansFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.jessica());

    IndividualResource requester = usersFixture.steve();

    IndividualResource request = requestsFixture.place(new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(smallAngryPlanet)
      .by(requester));

    JsonObject representation = request.getJson();

    assertThat("has item location",
      representation.getJsonObject("item").containsKey("location"), is(true));

    assertThat(
      representation.getJsonObject("item").getJsonObject("location").getString("name"),
      is("2nd Floor - Economics"));

    assertThat(
      representation.getJsonObject("item").getJsonObject("location").getString("code"),
      is("NU/JC/DL/2FE"));

    assertThat(
      representation.getJsonObject("item").getJsonObject("location").getString("libraryName"),
      is("Datalogisk Institut"));

    Response fetchedRequestResponse = requestsClient.getById(request.getId());

    assertThat(fetchedRequestResponse.getStatusCode(), is(200));

    JsonObject fetchedLoan = fetchedRequestResponse.getJson();

    assertThat("has item location",
      fetchedLoan.getJsonObject("item").containsKey("location"), is(true));

    assertThat(
      fetchedLoan.getJsonObject("item").getJsonObject("location").getString("name"),
      is("2nd Floor - Economics"));

    assertThat(
      fetchedLoan.getJsonObject("item").getJsonObject("location").getString("code"),
      is("NU/JC/DL/2FE"));

    assertThat(
      fetchedLoan.getJsonObject("item").getJsonObject("location").getString("libraryName"),
      is("Datalogisk Institut"));
  }

  @Test
  public void locationIncludedForMultipleRequests()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource thirdFloor = locationsFixture.thirdFloor();
    final IndividualResource secondFloorEconomics = locationsFixture.secondFloorEconomics();
    final IndividualResource mezzanineDisplayCase = locationsFixture.mezzanineDisplayCase();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      holdingBuilder -> holdingBuilder
        .withPermanentLocation(secondFloorEconomics),
      itemBuilder -> itemBuilder
        .withPermanentLocation(thirdFloor));

    loansFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.james());

    IndividualResource firstRequest = requestsFixture.place(new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(smallAngryPlanet)
      .by(usersFixture.rebecca()));

    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire(
      holdingBuilder -> holdingBuilder
        .withPermanentLocation(mezzanineDisplayCase)
        .withNoTemporaryLocation(),
      itemBuilder -> itemBuilder
        .withNoPermanentLocation()
        .withNoTemporaryLocation());

    loansFixture.checkOutByBarcode(temeraire, usersFixture.jessica());

    IndividualResource secondRequest = requestsFixture.place(new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(temeraire)
      .by(usersFixture.steve()));

    List<JsonObject> fetchedRequestsResponse = requestsClient.getAll();

    assertThat(fetchedRequestsResponse.size(), is(2));

    JsonObject firstFetchedRequest = getRecordById(
      fetchedRequestsResponse, firstRequest.getId()).get();

    assertThat("has item",
      firstFetchedRequest.containsKey("item"), is(true));

    assertThat("has item location",
      firstFetchedRequest.getJsonObject("item").containsKey("location"), is(true));

    assertThat(
      firstFetchedRequest.getJsonObject("item").getJsonObject("location").getString("name"),
      is("3rd Floor"));

    assertThat(
      firstFetchedRequest.getJsonObject("item").getJsonObject("location").getString("code"),
      is("NU/JC/DL/3F"));

    assertThat(
      firstFetchedRequest.getJsonObject("item").getJsonObject("location").getString("libraryName"),
      is("Djanogly Learning Resource Centre"));

    JsonObject secondFetchedRequest = getRecordById(
      fetchedRequestsResponse, secondRequest.getId()).get();

    assertThat("has item",
      secondFetchedRequest.containsKey("item"), is(true));

    assertThat("has item location",
      secondFetchedRequest.getJsonObject("item").containsKey("location"), is(true));

    assertThat(
      secondFetchedRequest.getJsonObject("item").getJsonObject("location").getString("name"),
      is("Display Case, Mezzanine"));

    assertThat(
      firstFetchedRequest.getJsonObject("item").getJsonObject("location").getString("code"),
      is("NU/JC/DL/3F"));

    assertThat(
      firstFetchedRequest.getJsonObject("item").getJsonObject("location").getString("libraryName"),
      is("Djanogly Learning Resource Centre"));
  }
}
