package api.requests;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import api.support.http.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.RequestBuilder;

class RequestsAPICreateMultipleRequestsTests extends APITests {

  @Test
  void concurrentRequestsForSameItemGetConsecutivePositions() throws InterruptedException {
    final IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(item, usersFixture.steve());

    List<RequestBuilder> requests = List.of(
      new RequestBuilder().hold().forItem(item)
        .withPickupServicePointId(pickupServicePointId).by(usersFixture.jessica()),
      new RequestBuilder().hold().forItem(item)
        .withPickupServicePointId(pickupServicePointId).by(usersFixture.rebecca()),
      new RequestBuilder().hold().forItem(item)
        .withPickupServicePointId(pickupServicePointId).by(usersFixture.charlotte()));

    List<Integer> positions = createConcurrently(requests).stream()
      .map(request -> request.getJson().getInteger("position"))
      .sorted()
      .toList();

    assertThat(positions, is(List.of(1, 2, 3)));
  }

  @Test
  void concurrentTitleLevelRequestsForSameInstanceGetConsecutivePositions()
    throws InterruptedException {

    final var items = itemsFixture.createMultipleItemsForTheSameInstance(2);
    final UUID instanceId = items.get(0).getInstanceId();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    circulationSettingsFixture.enableTlrFeature();

    List<RequestBuilder> requests = List.of(
      titleLevelPage(instanceId, pickupServicePointId, usersFixture.jessica().getId()),
      titleLevelPage(instanceId, pickupServicePointId, usersFixture.rebecca().getId()),
      titleLevelPage(instanceId, pickupServicePointId, usersFixture.charlotte().getId()));

    List<Integer> positions = createConcurrently(requests).stream()
      .map(request -> request.getJson().getInteger("position"))
      .sorted()
      .toList();

    assertThat(positions, is(List.of(1, 2, 3)));
  }

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

  private RequestBuilder titleLevelPage(UUID instanceId, UUID pickupServicePointId,
    UUID requesterId) {

    return new RequestBuilder()
      .page()
      .titleRequestLevel()
      .withInstanceId(instanceId)
      .withNoItemId()
      .withNoHoldingsRecordId()
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId);
  }

  private List<IndividualResource> createConcurrently(List<RequestBuilder> requests)
    throws InterruptedException {

    CountDownLatch ready = new CountDownLatch(requests.size());
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch completed = new CountDownLatch(requests.size());
    List<IndividualResource> createdRequests = Collections.synchronizedList(new ArrayList<>());
    List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

    requests.forEach(request -> new Thread(() -> {
      ready.countDown();
      try {
        start.await();
        createdRequests.add(requestsClient.create(request));
      } catch (Throwable throwable) {
        failures.add(throwable);
      } finally {
        completed.countDown();
      }
    }).start());

    boolean allCreatorsReady = ready.await(5, TimeUnit.SECONDS);
    start.countDown();
    Assertions.assertTrue(allCreatorsReady);
    Assertions.assertTrue(completed.await(30, TimeUnit.SECONDS));
    Assertions.assertTrue(failures.isEmpty(), failures.toString());

    return createdRequests;
  }
}
