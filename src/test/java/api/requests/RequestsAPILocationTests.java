package api.requests;

import static api.support.JsonCollectionAssistant.getRecordById;
import static api.support.matchers.RequestItemMatcher.hasItemLocationProperties;
import static api.support.matchers.RequestItemMatcher.hasLibraryName;
import static api.support.matchers.RequestItemMatcher.hasLocationCode;
import static api.support.matchers.RequestItemMatcher.hasLocationName;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.util.List;
import java.util.UUID;

import api.support.http.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import api.support.http.ItemResource;
import io.vertx.core.json.JsonObject;

public class RequestsAPILocationTests extends APITests {
  @Test
  public void locationIsIncludedForSingleRequest() {

    final IndividualResource thirdFloor = locationsFixture.thirdFloor();
    final IndividualResource secondFloorEconomics = locationsFixture.secondFloorEconomics();
    final IndividualResource mezzanineDisplayCase = locationsFixture.mezzanineDisplayCase();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      holdingBuilder -> holdingBuilder
        .withPermanentLocation(thirdFloor)
        .withTemporaryLocation(mezzanineDisplayCase),
      itemBuilder -> itemBuilder
        .withNoPermanentLocation()
        .withTemporaryLocation(secondFloorEconomics)
    );

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.jessica());

    IndividualResource requester = usersFixture.steve();

    IndividualResource request = requestsFixture.place(new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(smallAngryPlanet)
      .by(requester));

    JsonObject createdRequest = request.getJson();

    assertThat(createdRequest, hasItemLocationProperties(allOf(
      hasLibraryName("Djanogly Learning Resource Centre"),
      hasLocationName("2nd Floor - Economics"),
      hasLocationCode("NU/JC/DL/2FE")
    )));

    Response fetchedRequestResponse = requestsClient.getById(request.getId());

    assertThat(fetchedRequestResponse.getStatusCode(), is(200));

    JsonObject fetchRequest = fetchedRequestResponse.getJson();

    assertThat("has item location",
      fetchRequest.getJsonObject("item").containsKey("location"), is(true));

    assertThat(fetchRequest, hasItemLocationProperties(allOf(
      hasLibraryName("Djanogly Learning Resource Centre"),
      hasLocationName("2nd Floor - Economics"),
      hasLocationCode("NU/JC/DL/2FE")
    )));
  }

  @Test
  public void locationIncludedForMultipleRequests() {

    final IndividualResource thirdFloor = locationsFixture.thirdFloor();
    final IndividualResource secondFloorEconomics = locationsFixture.secondFloorEconomics();
    final IndividualResource mezzanineDisplayCase = locationsFixture.mezzanineDisplayCase();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      holdingBuilder -> holdingBuilder
        .withPermanentLocation(secondFloorEconomics),
      itemBuilder -> itemBuilder
        .withPermanentLocation(thirdFloor));

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.james());

    IndividualResource firstRequest = requestsFixture.place(new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(smallAngryPlanet)
      .by(usersFixture.rebecca()));

    final ItemResource temeraire = itemsFixture.basedUponTemeraire(
      holdingBuilder -> holdingBuilder
        .withPermanentLocation(mezzanineDisplayCase)
        .withNoTemporaryLocation(),
      itemBuilder -> itemBuilder
        .withNoPermanentLocation()
        .withNoTemporaryLocation());

    checkOutFixture.checkOutByBarcode(temeraire, usersFixture.jessica());

    IndividualResource secondRequest = requestsFixture.place(new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(temeraire)
      .by(usersFixture.steve()));

    List<JsonObject> fetchedRequestsResponse = requestsClient.getAll();

    JsonObject firstFetchedRequest = getRecordById(
            fetchedRequestsResponse, firstRequest.getId()).get();

    JsonObject secondFetchedRequest = getRecordById(
            fetchedRequestsResponse, secondRequest.getId()).get();

    assertThat(firstFetchedRequest, hasItemLocationProperties(allOf(
      hasLibraryName("Djanogly Learning Resource Centre"),
      hasLocationName("3rd Floor"),
      hasLocationCode("NU/JC/DL/3F")
    )));

    assertThat(secondFetchedRequest, hasItemLocationProperties(allOf(
      hasLibraryName("Business Library"),
      hasLocationName("Display Case, Mezzanine"),
      hasLocationCode("NU/JC/BL/DM")
    )));

  }
}
