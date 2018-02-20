package org.folio.circulation.api.requests;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.support.APITests;
import org.folio.circulation.api.support.builders.ItemBuilder;
import org.folio.circulation.api.support.builders.RequestBuilder;
import org.folio.circulation.api.support.builders.UserBuilder;
import org.folio.circulation.api.support.http.InterfaceUrls;
import org.folio.circulation.api.support.http.ResourceClient;
import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.folio.circulation.api.support.fixtures.LoansFixture.checkOutItem;
import static org.folio.circulation.api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class RequestsAPIRetrievalTests extends APITests {
  @Test
  public void canGetARequestById()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    checkOutItem(itemId, loansClient);

    UUID requesterId = usersClient.create(new UserBuilder()
      .withName("Jones", "Steven")
      .withBarcode("564376549214"))
      .getId();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    requestsClient.create(new RequestBuilder()
      .recall()
      .withId(id)
      .withRequestDate(requestDate)
      .withItemId(itemId)
      .withRequesterId(requesterId)
      .fulfilToHoldShelf()
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31)));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.requestsUrl(String.format("/%s", id)),
      ResponseHandler.any(getCompleted));

    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get request: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject representation = getResponse.getJson();

    assertThat(representation.getString("id"), is(id.toString()));
    assertThat(representation.getString("requestType"), is("Recall"));
    assertThat(representation.getString("requestDate"), isEquivalentTo(requestDate));
    assertThat(representation.getString("itemId"), is(itemId.toString()));
    assertThat(representation.getString("requesterId"), is(requesterId.toString()));
    assertThat(representation.getString("fulfilmentPreference"), is("Hold Shelf"));
    assertThat(representation.getString("requestExpirationDate"), is("2017-07-30"));
    assertThat(representation.getString("holdShelfExpirationDate"), is("2017-08-31"));
    assertThat(representation.getString("status"), is("Open - Not yet filled"));

    assertThat("has information taken from item",
      representation.containsKey("item"), is(true));

    assertThat("title is taken from item",
      representation.getJsonObject("item").getString("title"),
      is("The Long Way to a Small, Angry Planet"));

    assertThat("barcode is taken from item",
      representation.getJsonObject("item").getString("barcode"),
      is("036000291452"));

    assertThat("has information taken from requesting user",
      representation.containsKey("requester"), is(true));

    assertThat("last name is taken from requesting user",
      representation.getJsonObject("requester").getString("lastName"),
      is("Jones"));

    assertThat("first name is taken from requesting user",
      representation.getJsonObject("requester").getString("firstName"),
      is("Steven"));

    assertThat("middle name is not taken from requesting user",
      representation.getJsonObject("requester").containsKey("middleName"),
      is(false));

    assertThat("barcode is taken from requesting user",
      representation.getJsonObject("requester").getString("barcode"),
      is("564376549214"));
  }

  @Test
  public void requestNotFoundForUnknownId()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    Response getResponse = ResourceClient.forRequests(client).getById(UUID.randomUUID());

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void canPageAllRequests()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID requesterId = usersClient.create(new UserBuilder()).getId();

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponSmallAngryPlanet(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponNod(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponInterestingTimes(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponTemeraire(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponNod(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponUprooted(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponTemeraire(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId));

    CompletableFuture<Response> getFirstPageCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.requestsUrl() + "?limit=4",
      ResponseHandler.any(getFirstPageCompleted));

    CompletableFuture<Response> getSecondPageCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.requestsUrl() + "?limit=4&offset=4",
      ResponseHandler.any(getSecondPageCompleted));

    Response firstPageResponse = getFirstPageCompleted.get(5, TimeUnit.SECONDS);
    Response secondPageResponse = getSecondPageCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get first page of requests: %s",
      firstPageResponse.getBody()),
      firstPageResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    assertThat(String.format("Failed to get second page of requests: %s",
      secondPageResponse.getBody()),
      secondPageResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject firstPage = firstPageResponse.getJson();
    JsonObject secondPage = secondPageResponse.getJson();

    List<JsonObject> firstPageRequests = getRequests(firstPage);
    List<JsonObject> secondPageRequests = getRequests(secondPage);

    assertThat(firstPageRequests.size(), is(4));
    assertThat(firstPage.getInteger("totalRecords"), is(7));

    assertThat(secondPageRequests.size(), is(3));
    assertThat(secondPage.getInteger("totalRecords"), is(7));

    firstPageRequests.forEach(this::requestHasExpectedProperties);
    secondPageRequests.forEach(this::requestHasExpectedProperties);
  }

  @Test
  public void canSearchForRequestsByRequesterLastName()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID firstRequester = usersClient.create(new UserBuilder()
      .withName("Jones", "Steven"))
      .getId();

    UUID secondRequester = usersClient.create(new UserBuilder()
      .withName("Norton", "Jessica"))
      .getId();

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponSmallAngryPlanet(ItemBuilder::checkOut).getId())
      .withRequesterId(firstRequester));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponNod(ItemBuilder::checkOut).getId())
      .withRequesterId(firstRequester));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponInterestingTimes(ItemBuilder::checkOut).getId())
      .withRequesterId(secondRequester));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponTemeraire(ItemBuilder::checkOut).getId())
      .withRequesterId(firstRequester));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponNod(ItemBuilder::checkOut).getId())
      .withRequesterId(firstRequester));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponUprooted(ItemBuilder::checkOut).getId())
      .withRequesterId(secondRequester));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponTemeraire(ItemBuilder::checkOut).getId())
      .withRequesterId(secondRequester));

    CompletableFuture<Response> getRequestsCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.requestsUrl()
        + String.format("?query=requester.lastName=%s", "Norton"),
      ResponseHandler.any(getRequestsCompleted));

    Response getRequestsResponse = getRequestsCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get requests: %s",
      getRequestsResponse.getBody()),
      getRequestsResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject wrappedRequests = getRequestsResponse.getJson();

    List<JsonObject> requests = getRequests(wrappedRequests);

    assertThat(requests.size(), is(3));
    assertThat(wrappedRequests.getInteger("totalRecords"), is(3));

    requests.forEach(this::requestHasExpectedProperties);
  }

  @Test
  public void canSearchForRequestsByItemTitle()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID requesterId = usersClient.create(new UserBuilder()).getId();

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponSmallAngryPlanet(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponNod(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponInterestingTimes(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponTemeraire(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponNod(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponUprooted(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponTemeraire(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId));

    CompletableFuture<Response> getRequestsCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.requestsUrl() + "?query=item.title=Nod",
      ResponseHandler.any(getRequestsCompleted));

    Response getRequestsResponse = getRequestsCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get requests: %s",
      getRequestsResponse.getBody()),
      getRequestsResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject wrappedRequests = getRequestsResponse.getJson();

    List<JsonObject> requests = getRequests(wrappedRequests);

    assertThat(requests.size(), is(2));
    assertThat(wrappedRequests.getInteger("totalRecords"), is(2));

    requests.forEach(this::requestHasExpectedProperties);
  }

  private List<JsonObject> getRequests(JsonObject page) {
    return JsonArrayHelper.toList(page.getJsonArray("requests"));
  }

  private void requestHasExpectedProperties(JsonObject request) {
    hasProperty("id", request, "request");
    hasProperty("requestType", request, "request");
    hasProperty("requestDate", request, "request");
    hasProperty("requesterId", request, "request");
    hasProperty("itemId", request, "request");
    hasProperty("fulfilmentPreference", request, "request");
    hasProperty("item", request, "request");
    hasProperty("requester", request, "request");
    hasProperty("status", request, "request");
  }

  private void hasProperty(String property, JsonObject resource, String type) {
    assertThat(String.format("%s should have %s: %s",
      type, property, resource),
      resource.containsKey(property), is(true));
  }

}
