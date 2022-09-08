package api.requests;

import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.UUIDMatcher.is;
import static org.folio.HttpStatus.HTTP_NOT_FOUND;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import org.folio.circulation.domain.MultipleRecords;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import api.support.APITests;
import api.support.builders.ItemBuilder;
import api.support.builders.RequestBuilder;
import api.support.http.ItemResource;
import api.support.http.UserResource;
import io.vertx.core.json.JsonObject;

class RequestsAPIDeletionTests extends APITests {
  @Test
  void canDeleteRequestInQueue() {
    final var nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod);

    final var firstRequest = requestsFixture.place(requestFor(nod, usersFixture.rebecca()));
    final var secondRequest = requestsFixture.place(requestFor(nod, usersFixture.charlotte()));
    final var thirdRequest = requestsFixture.place(requestFor(nod, usersFixture.james()));

    requestsFixture.deleteRequest(secondRequest.getId());

    assertThat("deleted request cannot be fetched",
      requestsFixture.getById(secondRequest.getId()), hasStatus(HTTP_NOT_FOUND));

    final var changedQueue = requestsFixture.getQueueFor(nod);

    assertThat(changedQueue.size(), is(2));
    assertThat(changedQueue.getTotalRecords(), is(2));

    assertThat(first(changedQueue), hasJsonPath("position", is(1)));
    assertThat(first(changedQueue), hasJsonPath("id", is(firstRequest.getId())));
    assertThat(second(changedQueue), hasJsonPath("position", is(2)));
    assertThat(second(changedQueue), hasJsonPath("id", is(thirdRequest.getId())));
  }

  @Test
  void canDeleteAllRequests() {
    final var nod = itemsFixture.basedUponNod();
    final var smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final var temeraire = itemsFixture.basedUponTemeraire();

    checkOutFixture.checkOutByBarcode(nod);
    checkOutFixture.checkOutByBarcode(smallAngryPlanet);
    checkOutFixture.checkOutByBarcode(temeraire);

    requestsFixture.place(requestFor(nod));
    requestsFixture.place(requestFor(smallAngryPlanet));
    requestsFixture.place(requestFor(temeraire));

    requestsFixture.deleteAllRequests();

    final var allRequests = requestsFixture.getAllRequests();

    assertThat(allRequests.size(), is(0));
    assertThat(allRequests.totalRecords(), is(0));
  }

  @Test
  void pageRequestDeletionChangesItemStatusFromPagedToAvailable() {
    final var nod = itemsFixture.basedUponNod();

    final var request = requestsFixture.placeItemLevelPageRequest(nod,
      nod.getInstanceId(), usersFixture.jessica());

    var itemAfterRequestCreation = itemsFixture.getById(nod.getId());
    assertThat("item status is changed to Paged",
      itemAfterRequestCreation.getStatusName(), is(ItemBuilder.PAGED));

    requestsFixture.deleteRequest(request.getId());

    assertThat("deleted request cannot be fetched",
      requestsFixture.getById(request.getId()), hasStatus(HTTP_NOT_FOUND));

    var itemAfterRequestDeletion = itemsFixture.getById(nod.getId());
    assertThat("item status is changed back to Available",
      itemAfterRequestDeletion.getStatusName(), is(ItemBuilder.AVAILABLE));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "Hold",
    "Recall"
  })
  void holdAndRecallRequestsDeletionDoesNotChangeItemStatus(String requestType) {
    final var nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod);

    final var request = requestsFixture.place(requestFor(nod, usersFixture.charlotte())
        .withRequestType(requestType));

    var itemAfterRequestCreation = itemsFixture.getById(nod.getId());
    assertThat("item status is still Checked out",
      itemAfterRequestCreation.getStatusName(), is(ItemBuilder.CHECKED_OUT));

    requestsFixture.deleteRequest(request.getId());

    assertThat("deleted request cannot be fetched",
      requestsFixture.getById(request.getId()), hasStatus(HTTP_NOT_FOUND));

    var itemAfterRequestDeletion = itemsFixture.getById(nod.getId());
    assertThat("item status is still Checked out",
      itemAfterRequestDeletion.getStatusName(), is(ItemBuilder.CHECKED_OUT));
  }

  @Test
  void canDeletePageRequestAfterAssociatedItemIsDeleted() {
    final var nod = itemsFixture.basedUponNod();

    final var request = requestsFixture.placeItemLevelPageRequest(nod,
      nod.getInstanceId(), usersFixture.jessica());

    var itemAfterRequestCreation = itemsFixture.getById(nod.getId());
    assertThat("item status is changed back to Available",
      itemAfterRequestCreation.getStatusName(), is(ItemBuilder.PAGED));

    itemsClient.delete(nod.getId());
    requestsFixture.deleteRequest(request.getId());

    assertThat("deleted request cannot be fetched",
      requestsFixture.getById(request.getId()), hasStatus(HTTP_NOT_FOUND));
  }

  private RequestBuilder requestFor(ItemResource item) {
    return requestFor(item, usersFixture.rebecca());
  }

  private RequestBuilder requestFor(ItemResource item, UserResource requester) {
    return new RequestBuilder()
      .withItemId(item.getId())
      .withInstanceId(item.getInstanceId())
      .withPickupServicePoint(servicePointsFixture.cd1())
      .withRequesterId(requester.getId());
  }

  private JsonObject first(MultipleRecords<JsonObject> records) {
    return records.getRecords().stream().findFirst().orElse(null);
  }

  private JsonObject second(MultipleRecords<JsonObject> records) {
    return records.getRecords().stream().skip(1).findFirst().orElse(null);
  }
}
