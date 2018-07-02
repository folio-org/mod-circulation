package api.requests;

import api.support.APITests;
import api.support.builders.HoldingBuilder;
import api.support.builders.RequestBuilder;
import api.support.fixtures.InstanceExamples;
import api.support.fixtures.ItemExamples;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static api.APITestSuite.*;
import static api.support.JsonCollectionAssistant.getRecordById;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class RequestsAPILocationTests extends APITests {
  @Test
  public void locationIsIncludedForSingleRequest()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID instanceId = instancesClient.create(
      InstanceExamples.basedUponSmallAngryPlanet()).getId();

    UUID holdingId = holdingsClient.create(
      new HoldingBuilder()
        .forInstance(instanceId)
        .withPermanentLocation(thirdFloorLocationId())
        .withTemporaryLocation(mezzanineDisplayCaseLocationId())
        .create())
      .getId();

    IndividualResource item = itemsClient.create(
      ItemExamples.basedUponSmallAngryPlanet()
        .forHolding(holdingId)
        .withNoPermanentLocation()
        .withTemporaryLocation(secondFloorEconomicsLocationId()));

    loansFixture.checkOut(item, usersFixture.jessica());

    IndividualResource requester = usersFixture.steve();

    IndividualResource request = requestsFixture.place(new RequestBuilder()
      .open()
      .hold()
      .forItem(item)
      .by(requester));

    JsonObject representation = request.getJson();

    assertThat("has item location",
      representation.getJsonObject("item").containsKey("location"), is(true));

    assertThat(
      representation.getJsonObject("item").getJsonObject("location").getString("name"),
      is("2nd Floor - Economics"));

    Response fetchedRequestResponse = requestsClient.getById(request.getId());

    assertThat(fetchedRequestResponse.getStatusCode(), is(200));

    JsonObject fetchedLoan = fetchedRequestResponse.getJson();

    assertThat("has item location",
      fetchedLoan.getJsonObject("item").containsKey("location"), is(true));

    assertThat(
      fetchedLoan.getJsonObject("item").getJsonObject("location").getString("name"),
      is("2nd Floor - Economics"));
  }

  @Test
  public void locationIncludedForMultipleRequests()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID firstInstanceId = instancesClient.create(
      InstanceExamples.basedUponSmallAngryPlanet()).getId();

    UUID firstHoldingId = holdingsClient.create(
      new HoldingBuilder()
        .forInstance(firstInstanceId)
        .withPermanentLocation(secondFloorEconomicsLocationId())
        .create())
      .getId();

    final IndividualResource firstItem = itemsClient.create(
      ItemExamples.basedUponSmallAngryPlanet()
        .forHolding(firstHoldingId)
        .withPermanentLocation(thirdFloorLocationId()));

    loansFixture.checkOut(firstItem, usersFixture.james());

    IndividualResource firstRequest = requestsFixture.place(new RequestBuilder()
      .open()
      .hold()
      .forItem(firstItem)
      .by(usersFixture.rebecca()));

    UUID secondInstanceId = instancesClient.create(
      InstanceExamples.basedUponTemeraire()).getId();

    UUID secondHoldingId = holdingsClient.create(
      new HoldingBuilder()
        .forInstance(secondInstanceId)
        .withPermanentLocation(mezzanineDisplayCaseLocationId())
        .withNoTemporaryLocation()
        .create())
      .getId();

    IndividualResource secondItem = itemsClient.create(
      ItemExamples.basedUponTemeraire()
        .forHolding(secondHoldingId));

    loansFixture.checkOut(secondItem, usersFixture.jessica());

    IndividualResource secondRequest = requestsFixture.place(new RequestBuilder()
      .open()
      .hold()
      .forItem(secondItem)
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

    JsonObject secondFetchedRequest = getRecordById(
      fetchedRequestsResponse, secondRequest.getId()).get();

    assertThat("has item",
      secondFetchedRequest.containsKey("item"), is(true));

    assertThat("has item location",
      secondFetchedRequest.getJsonObject("item").containsKey("location"), is(true));

    assertThat(
      secondFetchedRequest.getJsonObject("item").getJsonObject("location").getString("name"),
      is("Display Case, Mezzanine"));
  }
}
