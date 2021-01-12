package api.requests;

import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.UUIDMatcher.is;
import static org.folio.HttpStatus.HTTP_NOT_FOUND;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import org.folio.circulation.domain.MultipleRecords;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import api.support.http.ItemResource;
import api.support.http.UserResource;
import io.vertx.core.json.JsonObject;

public class RequestsAPIDeletionTests extends APITests {
  @Test
  public void canDeleteRequestInQueue() {
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
  public void canDeleteAllRequests() {
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

  private RequestBuilder requestFor(ItemResource item) {
    return requestFor(item, usersFixture.rebecca());
  }

  private RequestBuilder requestFor(ItemResource item, UserResource requester) {
    return new RequestBuilder()
      .withItemId(item.getId())
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
