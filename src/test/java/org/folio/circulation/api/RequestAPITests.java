package org.folio.circulation.api;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.support.ItemRequestExamples;
import org.folio.circulation.api.support.RequestRequestBuilder;
import org.folio.circulation.api.support.UserRequestBuilder;
import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.folio.circulation.api.support.TextDateTimeMatcher.isEquivalentTo;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class RequestAPITests {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  OkapiHttpClient client = APITestSuite.createClient(exception -> {
    log.error("Request to circulation module failed:", exception);
  });

  @Before
  public void beforeEach()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    deleteAllRequests();
    deleteAllLoans();
    deleteAllItems();
    deleteAllUsers();
  }

  @Test
  public void canCreateARequest()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    UUID itemId = createItem(
      ItemRequestExamples.smallAngryPlanet("036000291452")).getId();

    UUID requesterId = createUser(new UserRequestBuilder()
      .withName("Jones", "Steven")
      .create()).getId();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    JsonObject requestRequest = new RequestRequestBuilder()
      .recall()
      .withId(id)
      .withRequestDate(requestDate)
      .withItemId(itemId)
      .withRequesterId(requesterId)
      .fulfilToHoldShelf()
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31))
      .create();

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(requestsUrl(), requestRequest,
      ResponseHandler.json(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create request: %s", postResponse.getBody()),
      postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject representation = postResponse.getJson();

    assertThat(representation.getString("id"), is(id.toString()));
    assertThat(representation.getString("requestType"), is("Recall"));
    assertThat(representation.getString("requestDate"), isEquivalentTo(requestDate));
    assertThat(representation.getString("itemId"), is(itemId.toString()));
    assertThat(representation.getString("requesterId"), is(requesterId.toString()));
    assertThat(representation.getString("fulfilmentPreference"), is("Hold Shelf"));
    assertThat(representation.getString("requestExpirationDate"), is("2017-07-30"));
    assertThat(representation.getString("holdShelfExpirationDate"), is("2017-08-31"));

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
  }

  @Test
  public void creatingARequestDoesNotStoreItemInformationWhenItemNotFound()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();

    UUID requesterId = createUser(new UserRequestBuilder()
        .withName("Jones", "Steven")
        .create()).getId();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    JsonObject requestRequest = new RequestRequestBuilder()
      .recall()
      .withId(id)
      .withRequestDate(requestDate)
      .withItemId(itemId)
      .withRequesterId(requesterId)
      .fulfilToHoldShelf()
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31))
      .create();

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(requestsUrl(), requestRequest,
      ResponseHandler.json(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create request: %s", postResponse.getBody()),
      postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject representation = postResponse.getJson();

    assertThat("has no information for missing item",
      representation.containsKey("item"), is(false));
  }

  @Test
  public void creatingARequestIgnoresItemInformationProvidedByClient()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();

    UUID requesterId = createUser(new UserRequestBuilder()
      .withName("Jones", "Steven")
      .create()).getId();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    JsonObject requestRequest = new RequestRequestBuilder()
      .recall()
      .withId(id)
      .withRequestDate(requestDate)
      .withItemId(itemId)
      .withRequesterId(requesterId)
      .fulfilToHoldShelf()
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31))
      .create();

    requestRequest.put("item", new JsonObject()
      .put("title", "incorrect title information")
      .put("barcode", "753856498321"));

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(requestsUrl(), requestRequest,
      ResponseHandler.json(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create request: %s", postResponse.getBody()),
      postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject representation = postResponse.getJson();

    assertThat("has no information for missing item",
      representation.containsKey("item"), is(false));
  }

  @Test
  public void canGetARequestById()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();
    UUID itemId = createItem(ItemRequestExamples.smallAngryPlanet()).getId();

    UUID requesterId = createUser(new UserRequestBuilder()
      .withName("Jones", "Steven")
      .create()).getId();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    createRequest(new RequestRequestBuilder()
      .recall()
      .withId(id)
      .withRequestDate(requestDate)
      .withItemId(itemId)
      .withRequesterId(requesterId)
      .fulfilToHoldShelf()
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31))
      .create());

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(requestsUrl(String.format("/%s", id)),
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
  }

  @Test
  public void requestNotFoundForUnknownId()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    Response getResponse = getById(UUID.randomUUID());

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void canPageAllRequests()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    UUID requesterId = createUser(new UserRequestBuilder()
      .withName("Jones", "Steven")
      .create()).getId();

    createRequest(new RequestRequestBuilder()
      .withItemId(createItem(ItemRequestExamples.smallAngryPlanet()).getId())
      .withRequesterId(requesterId)
      .create());

    createRequest(new RequestRequestBuilder()
      .withItemId(createItem(ItemRequestExamples.nod()).getId())
      .withRequesterId(requesterId)
      .create());

    createRequest(new RequestRequestBuilder()
      .withItemId(createItem(ItemRequestExamples.interestingTimes()).getId())
      .withRequesterId(requesterId)
      .create());

    createRequest(new RequestRequestBuilder()
      .withItemId(createItem(ItemRequestExamples.temeraire()).getId())
      .withRequesterId(requesterId)
      .create());

    createRequest(new RequestRequestBuilder()
      .withItemId(createItem(ItemRequestExamples.nod()).getId())
      .withRequesterId(requesterId)
      .create());

    createRequest(new RequestRequestBuilder()
      .withItemId(createItem(ItemRequestExamples.uprooted()).getId())
      .withRequesterId(requesterId)
      .create());

    createRequest(new RequestRequestBuilder()
      .withItemId(createItem(ItemRequestExamples.temeraire()).getId())
      .withRequesterId(requesterId)
      .create());

    CompletableFuture<Response> getFirstPageCompleted = new CompletableFuture<>();

    client.get(requestsUrl() + "?limit=4",
      ResponseHandler.any(getFirstPageCompleted));

    CompletableFuture<Response> getSecondPageCompleted = new CompletableFuture<>();

    client.get(requestsUrl() + "?limit=4&offset=4",
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

    firstPageRequests.forEach(request -> requestHasExpectedProperties(request));
    secondPageRequests.forEach(request -> requestHasExpectedProperties(request));
  }

  @Test
  public void canSearchForRequestsByRequesterId()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    UUID firstRequester = createUser(new UserRequestBuilder()
      .withName("Jones", "Steven")
      .create()).getId();

    UUID secondRequester = createUser(new UserRequestBuilder()
      .withName("Norton", "Jessica")
      .create()).getId();

    createRequest(new RequestRequestBuilder()
      .withItemId(createItem(ItemRequestExamples.smallAngryPlanet()).getId())
      .withRequesterId(firstRequester)
      .create());

    createRequest(new RequestRequestBuilder()
      .withItemId(createItem(ItemRequestExamples.nod()).getId())
      .withRequesterId(firstRequester)
      .create());

    createRequest(new RequestRequestBuilder()
      .withItemId(createItem(ItemRequestExamples.interestingTimes()).getId())
      .withRequesterId(secondRequester)
      .create());

    createRequest(new RequestRequestBuilder()
      .withItemId(createItem(ItemRequestExamples.temeraire()).getId())
      .withRequesterId(firstRequester)
      .create());

    createRequest(new RequestRequestBuilder()
      .withItemId(createItem(ItemRequestExamples.nod()).getId())
      .withRequesterId(firstRequester)
      .create());

    createRequest(new RequestRequestBuilder()
      .withItemId(createItem(ItemRequestExamples.uprooted()).getId())
      .withRequesterId(secondRequester)
      .create());

    createRequest(new RequestRequestBuilder()
      .withItemId(createItem(ItemRequestExamples.temeraire()).getId())
      .withRequesterId(secondRequester)
      .create());

    CompletableFuture<Response> getRequestsCompleted = new CompletableFuture<>();

    client.get(requestsUrl() + String.format("?query=requesterId=%s", secondRequester),
      ResponseHandler.any(getRequestsCompleted));

    Response getRequestsResponse = getRequestsCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get requests: %s",
      getRequestsResponse.getBody()),
      getRequestsResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject wrappedRequests = getRequestsResponse.getJson();

    List<JsonObject> requests = getRequests(wrappedRequests);

    assertThat(requests.size(), is(3));
    assertThat(wrappedRequests.getInteger("totalRecords"), is(3));

    requests.forEach(request -> requestHasExpectedProperties(request));
  }

  @Test
  public void canSearchForRequestsByItemTitle()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    UUID requesterId = createUser(new UserRequestBuilder()
      .withName("Jones", "Steven")
      .create()).getId();

    createRequest(new RequestRequestBuilder()
      .withItemId(createItem(ItemRequestExamples.smallAngryPlanet()).getId())
      .withRequesterId(requesterId)
      .create());

    createRequest(new RequestRequestBuilder()
      .withItemId(createItem(ItemRequestExamples.nod()).getId())
      .withRequesterId(requesterId)
      .create());

    createRequest(new RequestRequestBuilder()
      .withItemId(createItem(ItemRequestExamples.interestingTimes()).getId())
      .withRequesterId(requesterId)
      .create());

    createRequest(new RequestRequestBuilder()
      .withItemId(createItem(ItemRequestExamples.temeraire()).getId())
      .withRequesterId(requesterId)
      .create());

    createRequest(new RequestRequestBuilder()
      .withItemId(createItem(ItemRequestExamples.nod()).getId())
      .withRequesterId(requesterId)
      .create());

    createRequest(new RequestRequestBuilder()
      .withItemId(createItem(ItemRequestExamples.uprooted()).getId())
      .withRequesterId(requesterId)
      .create());

    createRequest(new RequestRequestBuilder()
      .withItemId(createItem(ItemRequestExamples.temeraire()).getId())
      .withRequesterId(requesterId)
      .create());

    CompletableFuture<Response> getRequestsCompleted = new CompletableFuture<>();

    client.get(requestsUrl() + String.format("?query=item.title=Nod"),
      ResponseHandler.any(getRequestsCompleted));

    Response getRequestsResponse = getRequestsCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get requests: %s",
      getRequestsResponse.getBody()),
      getRequestsResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject wrappedRequests = getRequestsResponse.getJson();

    List<JsonObject> requests = getRequests(wrappedRequests);

    assertThat(requests.size(), is(2));
    assertThat(wrappedRequests.getInteger("totalRecords"), is(2));

    requests.forEach(request -> requestHasExpectedProperties(request));
  }

  @Test
  public void canReplaceAnExistingRequest()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();
    UUID itemId = createItem(ItemRequestExamples.temeraire("07295629642")).getId();

    UUID requesterId = createUser(new UserRequestBuilder()
      .withName("Norton", "Jessica")
      .create()).getId();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    JsonObject requestRequest = new RequestRequestBuilder()
      .recall()
      .withId(id)
      .withRequestDate(requestDate)
      .withItemId(itemId)
      .withRequesterId(requesterId)
      .fulfilToHoldShelf()
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31))
      .create();

    IndividualResource createdRequest = createRequest(requestRequest);

    JsonObject updatedRequest = createdRequest.copyJson();

    updatedRequest.put("requestType", "Hold");

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(requestsUrl(String.format("/%s", id)),
      updatedRequest, ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(requestsUrl(String.format("/%s", id)),
      ResponseHandler.any(getCompleted));

    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get request: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject representation = getResponse.getJson();

    assertThat(representation.getString("id"), is(id.toString()));
    assertThat(representation.getString("requestType"), is("Hold"));
    assertThat(representation.getString("requestDate"), isEquivalentTo(requestDate));
    assertThat(representation.getString("itemId"), is(itemId.toString()));
    assertThat(representation.getString("requesterId"), is(requesterId.toString()));
    assertThat(representation.getString("fulfilmentPreference"), is("Hold Shelf"));
    assertThat(representation.getString("requestExpirationDate"), is("2017-07-30"));
    assertThat(representation.getString("holdShelfExpirationDate"), is("2017-08-31"));

    assertThat("has information taken from item",
      representation.containsKey("item"), is(true));

    assertThat("title is taken from item",
      representation.getJsonObject("item").getString("title"),
      is("Temeraire"));

    assertThat("barcode is taken from item",
      representation.getJsonObject("item").getString("barcode"),
      is("07295629642"));

    assertThat("has information taken from requesting user",
      representation.containsKey("requester"), is(true));

    assertThat("last name is taken from requesting user",
      representation.getJsonObject("requester").getString("lastName"),
      is("Norton"));

    assertThat("first name is taken from requesting user",
      representation.getJsonObject("requester").getString("firstName"),
      is("Jessica"));
  }

  @Test
  public void replacingAnExistingRequestRemovesItemInformationWhenItemDoesNotExist()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();
    UUID itemId = createItem(ItemRequestExamples.temeraire("07295629642")).getId();

    UUID requesterId = createUser(new UserRequestBuilder()
      .withName("Norton", "Jessica")
      .create()).getId();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    JsonObject requestRequest = new RequestRequestBuilder()
      .recall()
      .withId(id)
      .withRequestDate(requestDate)
      .withItemId(itemId)
      .withRequesterId(requesterId)
      .fulfilToHoldShelf()
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31))
      .create();

    IndividualResource createdRequest = createRequest(requestRequest);

    JsonObject updatedRequest = createdRequest.copyJson();

    deleteItem(itemId);

    updatedRequest
      .put("requestType", "Hold");

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(requestsUrl(String.format("/%s", id)),
      updatedRequest, ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(requestsUrl(String.format("/%s", id)),
      ResponseHandler.any(getCompleted));

    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get request: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject representation = getResponse.getJson();

    assertThat(representation.getString("itemId"), is(itemId.toString()));

    assertThat("has no information for missing item",
      representation.containsKey("item"), is(false));
  }

  @Test
  public void canDeleteAllRequests()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    UUID requesterId = createUser(new UserRequestBuilder()
      .withName("Norton", "Jessica")
      .create()).getId();

    createRequest(new RequestRequestBuilder()
      .withItemId(createItem(ItemRequestExamples.smallAngryPlanet()).getId())
      .withRequesterId(requesterId)
      .create());

    createRequest(new RequestRequestBuilder()
      .withItemId(createItem(ItemRequestExamples.nod()).getId())
      .withRequesterId(requesterId)
      .create());

    createRequest(new RequestRequestBuilder()
      .withItemId(createItem(ItemRequestExamples.interestingTimes()).getId())
      .withRequesterId(requesterId)
      .create());

    createRequest(new RequestRequestBuilder()
      .withItemId(createItem(ItemRequestExamples.temeraire()).getId())
      .withRequesterId(requesterId)
      .create());

    createRequest(new RequestRequestBuilder()
      .withItemId(createItem(ItemRequestExamples.uprooted()).getId())
      .withRequesterId(requesterId)
      .create());

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    client.delete(requestsUrl(),
      ResponseHandler.any(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    CompletableFuture<Response> getAllCompleted = new CompletableFuture<>();

    client.get(requestsUrl(), ResponseHandler.any(getAllCompleted));

    Response getAllResponse = getAllCompleted.get(5, TimeUnit.SECONDS);

    assertThat(getAllResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject allRequests = getAllResponse.getJson();

    List<JsonObject> requests = getRequests(allRequests);

    assertThat(requests.size(), is(0));
    assertThat(allRequests.getInteger("totalRecords"), is(0));
  }

  @Test
  public void canDeleteAnIndividualRequest()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    UUID firstId = UUID.randomUUID();
    UUID secondId = UUID.randomUUID();
    UUID thirdId = UUID.randomUUID();

    UUID requesterId = createUser(new UserRequestBuilder()
      .withName("Norton", "Jessica")
      .create()).getId();

    createRequest(new RequestRequestBuilder()
      .withId(firstId)
      .withItemId(createItem(ItemRequestExamples.nod()).getId())
      .withRequesterId(requesterId)
      .create());

    createRequest(new RequestRequestBuilder()
      .withId(secondId)
      .withItemId(createItem(ItemRequestExamples.smallAngryPlanet()).getId())
      .withRequesterId(requesterId)
      .create());

    createRequest(new RequestRequestBuilder()
      .withId(thirdId)
      .withItemId(createItem(ItemRequestExamples.temeraire()).getId())
      .withRequesterId(requesterId)
      .create());

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    client.delete(requestsUrl(String.format("/%s", secondId)),
      ResponseHandler.any(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    assertThat(getById(firstId).getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(getById(secondId).getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
    assertThat(getById(thirdId).getStatusCode(), is(HttpURLConnection.HTTP_OK));

    CompletableFuture<Response> getAllCompleted = new CompletableFuture<>();

    client.get(requestsUrl(), ResponseHandler.any(getAllCompleted));

    Response getAllResponse = getAllCompleted.get(5, TimeUnit.SECONDS);

    assertThat(getAllResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject allRequests = getAllResponse.getJson();

    List<JsonObject> requests = getRequests(allRequests);

    assertThat(requests.size(), is(2));
    assertThat(allRequests.getInteger("totalRecords"), is(2));
  }

  private static URL itemsUrl(String subPath)
    throws MalformedURLException {

    return APITestSuite.viaOkapiModuleUrl("/item-storage/items" + subPath);
  }

  private static URL loansUrl(String subPath)
    throws MalformedURLException {

    return APITestSuite.viaOkapiModuleUrl("/loan-storage/loans" + subPath);
  }

  private static URL usersUrl(String subPath)
    throws MalformedURLException {

    return APITestSuite.viaOkapiModuleUrl("/users" + subPath);
  }

  private static URL requestsUrl()
    throws MalformedURLException {

    return requestsUrl("");
  }

  private static URL requestsUrl(String subPath)
    throws MalformedURLException {

    return APITestSuite.circulationModuleUrl("/circulation/requests" + subPath);
  }

  private IndividualResource createRequest(JsonObject requestRequest)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    return createResource(requestRequest, requestsUrl(), "request");
  }

  private IndividualResource createItem(JsonObject itemRequest)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    return createResource(itemRequest, itemsUrl(""), "item");
  }

  private IndividualResource createUser(JsonObject userRequest)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    return createResource(userRequest, usersUrl(""), "user");
  }

  private IndividualResource createResource(
    JsonObject request,
    URL url,
    String resourceName)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(url, request,
      ResponseHandler.json(createCompleted));

    Response response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create %s: %s", resourceName, response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    return new IndividualResource(response);
  }

  private void deleteAllRequests()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    APITestSuite.deleteAll(requestsUrl());
  }

  private void deleteAllLoans()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    APITestSuite.deleteAll(loansUrl(""));
  }

  private void deleteAllItems()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    APITestSuite.deleteAll(itemsUrl(""));
  }

  private void deleteAllUsers()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    APITestSuite.deleteAllIndividually(usersUrl(""), "users");
  }

  private Response getById(UUID id)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    URL getRequestUrl = requestsUrl(String.format("/%s", id));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(getRequestUrl,
      ResponseHandler.any(getCompleted));

    return getCompleted.get(5, TimeUnit.SECONDS);
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
  }

  private void hasProperty(String property, JsonObject resource, String type) {
    assertThat(String.format("%s should have %s: %s",
      type, property, resource),
      resource.containsKey(property), is(true));
  }

  private void deleteItem(UUID itemId)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> deleteFinished = new CompletableFuture<>();

    client.delete(itemsUrl(String.format("/%s", itemId)),
      ResponseHandler.any(deleteFinished));

    Response response = deleteFinished.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to delete item %s", itemId),
      response.getStatusCode(), is(204));
  }
}
