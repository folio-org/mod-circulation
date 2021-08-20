package api.requests;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;

import api.support.http.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.RequestBuilder;

class RequestsAPICreateMultipleRequestsTests extends APITests {

  @Test
  void canCreateMultipleRequestsOfSameTypeForSameItem() {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.steve());

    final IndividualResource firstRequest = requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.jessica()));

    final IndividualResource secondRequest = requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.rebecca()));

    final IndividualResource thirdRequest = requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.charlotte()));

    assertThat(firstRequest.getJson().getInteger("position"), is(1));
    assertThat(secondRequest.getJson().getInteger("position"), is(2));
    assertThat(thirdRequest.getJson().getInteger("position"), is(3));
  }

  @Test
  void canCreateMultipleRequestsOfDifferentTypeForSameItem() {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.rebecca());

    final IndividualResource firstRequest = requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.james()));

    final IndividualResource secondRequest = requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.charlotte()));

    final IndividualResource thirdRequest = requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.steve()));

    assertThat(firstRequest.getJson().getInteger("position"), is(1));
    assertThat(secondRequest.getJson().getInteger("position"), is(2));
    assertThat(thirdRequest.getJson().getInteger("position"), is(3));
  }

  @Test
  void cannotCreateMultipleRequestsWithPageRequestForSameItemWhenItIsCheckedOut() {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.rebecca());

    final IndividualResource firstRequest = requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.james()));

    final IndividualResource secondRequest = requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.steve()));

    assertThat(firstRequest.getJson().getInteger("position"), is(1));
    assertThat(secondRequest.getJson().getInteger("position"), is(2));

    //when an item is checked out, can't create a Page request for it
    final Response failedRequestResponse = requestsClient.attemptCreate(new RequestBuilder()
      .page()
      .forItem(smallAngryPlanet)
      .by(usersFixture.steve()));

    assertThat(
      String.format("Failed to create page request: %s",
        failedRequestResponse.getBody()), failedRequestResponse.getStatusCode(), Is.is(422));
  }

  @Test
  void canCreateMultipleRequestsAtSpecificLocation() {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.steve());

    final IndividualResource firstRequest = requestsClient.createAtSpecificLocation(
      new RequestBuilder()
        .open()
        .hold()
        .forItem(smallAngryPlanet)
        .withPickupServicePointId(pickupServicePointId)
        .by(usersFixture.jessica()));

    final IndividualResource secondRequest = requestsClient.createAtSpecificLocation(
      new RequestBuilder()
        .open()
        .hold()
        .forItem(smallAngryPlanet)
        .withPickupServicePointId(pickupServicePointId)
        .by(usersFixture.rebecca()));

    final IndividualResource thirdRequest = requestsClient.createAtSpecificLocation(
      new RequestBuilder()
        .open()
        .hold()
        .forItem(smallAngryPlanet)
        .withPickupServicePointId(pickupServicePointId)
        .by(usersFixture.charlotte()));

    assertThat("First request should have position",
      firstRequest.getJson().getInteger("position"), is(1));

    assertThat("Second request should have position",
      secondRequest.getJson().getInteger("position"), is(2));

    assertThat("Third request should have position",
      thirdRequest.getJson().getInteger("position"), is(3));
  }
}
