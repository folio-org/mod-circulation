package org.folio.circulation.api.requests;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.APITestSuite;
import org.folio.circulation.api.support.*;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.folio.circulation.api.support.ItemRequestExamples.*;
import static org.folio.circulation.api.support.TextDateTimeMatcher.isEquivalentTo;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class RequestAPITests {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final OkapiHttpClient client = APITestSuite.createClient(exception -> {
    log.error("Request to circulation module failed:", exception);
  });

  private final ResourceClient usersClient = ResourceClient.forUsers(client);
  private final ResourceClient requestsClient = ResourceClient.forRequests(client);
  private final ResourceClient itemsClient = ResourceClient.forItems(client);
  private final ResourceClient loansClient = ResourceClient.forLoans(client);

  @Before
  public void beforeEach()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    requestsClient.deleteAll();
    usersClient.deleteAllIndividually("users");
    itemsClient.deleteAll();
    loansClient.deleteAll();
  }

  @Test
  public void canCreateARequest()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsClient.create(basedUponSmallAngryPlanet()
      .withBarcode("036000291452")
      .create()).getId();

    UUID requesterId = usersClient.create(new UserRequestBuilder()
      .withName("Jones", "Steven")
      .withBarcode("564376549214")
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

    client.post(InterfaceUrls.requestsUrl(), requestRequest,
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

    assertThat("middle name is not taken from requesting user",
      representation.getJsonObject("requester").containsKey("middleName"),
      is(false));

    assertThat("barcode is taken from requesting user",
      representation.getJsonObject("requester").getString("barcode"),
      is("564376549214"));
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

    UUID requesterId = usersClient.create(new UserRequestBuilder().create()).getId();

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

    client.post(InterfaceUrls.requestsUrl(), requestRequest,
      ResponseHandler.json(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create request: %s", postResponse.getBody()),
      postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject representation = postResponse.getJson();

    assertThat("has no information for missing item",
      representation.containsKey("item"), is(false));
  }

  @Test
  public void creatingARequestDoesNotStoreRequesterInformationWhenUserNotFound()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsClient.create(basedUponSmallAngryPlanet()
      .withBarcode("036000291452")
      .create()).getId();

    UUID requesterId = UUID.randomUUID();

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

    client.post(InterfaceUrls.requestsUrl(), requestRequest,
      ResponseHandler.json(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create request: %s", postResponse.getBody()),
      postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject representation = postResponse.getJson();

    assertThat("has no information for missing requesting user",
      representation.containsKey("requester"), is(false));
  }

  @Test()
  public void creatingARequestStoresRequestingUserInformationWhenItemNotFound()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();

    UUID requesterId = usersClient.create(new UserRequestBuilder()
      .withName("Jones", "Steven")
      .withBarcode("564376549214")
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

    client.post(InterfaceUrls.requestsUrl(), requestRequest,
      ResponseHandler.json(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create request: %s", postResponse.getBody()),
      postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject representation = postResponse.getJson();

    assertThat("has no information for missing item",
      representation.containsKey("item"), is(false));

    assertThat("has information for requesting user",
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
  public void creatingARequestStoresItemInformationWhenRequestingUserNotFound()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsClient.create(basedUponSmallAngryPlanet()
      .withBarcode("036000291452")
      .create()).getId();

    UUID requesterId = UUID.randomUUID();

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

    client.post(InterfaceUrls.requestsUrl(), requestRequest,
      ResponseHandler.json(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create request: %s", postResponse.getBody()),
      postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject representation = postResponse.getJson();

    assertThat("has information taken from item",
      representation.containsKey("item"), is(true));

    assertThat("title is taken from item",
      representation.getJsonObject("item").getString("title"),
      is("The Long Way to a Small, Angry Planet"));

    assertThat("barcode is taken from item",
      representation.getJsonObject("item").getString("barcode"),
      is("036000291452"));

    assertThat("has no information for missing requesting user",
      representation.containsKey("requester"), is(false));
  }

  @Test
  public void canCreateARequestWithRequesterWithMiddleName()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsClient.create(basedUponSmallAngryPlanet()
      .withBarcode("036000291452")
      .create()).getId();

    UUID requesterId = usersClient.create(new UserRequestBuilder()
      .withName("Jones", "Steven", "Anthony")
      .withBarcode("564376549214")
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

    client.post(InterfaceUrls.requestsUrl(), requestRequest,
      ResponseHandler.json(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create request: %s", postResponse.getBody()),
      postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject representation = postResponse.getJson();

    assertThat("has information taken from requesting user",
      representation.containsKey("requester"), is(true));

    assertThat("last name is taken from requesting user",
      representation.getJsonObject("requester").getString("lastName"),
      is("Jones"));

    assertThat("first name is taken from requesting user",
      representation.getJsonObject("requester").getString("firstName"),
      is("Steven"));

    assertThat("middle name is taken from requesting user",
      representation.getJsonObject("requester").getString("middleName"),
      is("Anthony"));

    assertThat("barcode is taken from requesting user",
      representation.getJsonObject("requester").getString("barcode"),
      is("564376549214"));
  }

  @Test
  public void canCreateARequestWithRequesterWithNoBarcode()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsClient.create(basedUponSmallAngryPlanet()
      .withBarcode("036000291452")
      .create()).getId();

    UUID requesterId = usersClient.create(new UserRequestBuilder()
      .withName("Jones", "Steven")
      .withNoBarcode().create()).getId();

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

    client.post(InterfaceUrls.requestsUrl(), requestRequest,
      ResponseHandler.json(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create request: %s", postResponse.getBody()),
      postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject representation = postResponse.getJson();

    assertThat("has information taken from requesting user",
      representation.containsKey("requester"), is(true));

    assertThat("last name is taken from requesting user",
      representation.getJsonObject("requester").getString("lastName"),
      is("Jones"));

    assertThat("first name is taken from requesting user",
      representation.getJsonObject("requester").getString("firstName"),
      is("Steven"));

    assertThat("barcode is not taken from requesting user",
      representation.getJsonObject("requester").containsKey("barcode"),
      is(false));
  }

  @Test
  public void canCreateARequestForItemWithNoBarcode()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsClient.create(basedUponSmallAngryPlanet()
      .withNoBarcode()
      .create()).getId();

    UUID requesterId = usersClient.create(new UserRequestBuilder().create()).getId();

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

    client.post(InterfaceUrls.requestsUrl(), requestRequest,
      ResponseHandler.json(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create request: %s", postResponse.getBody()),
      postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject representation = postResponse.getJson();

    assertThat(representation.getString("itemId"), is(itemId.toString()));

    assertThat("has information taken from item",
      representation.containsKey("item"), is(true));

    assertThat("title is taken from item",
      representation.getJsonObject("item").getString("title"),
      is("The Long Way to a Small, Angry Planet"));

    assertThat("barcode is not taken from item when none present",
      representation.getJsonObject("item").containsKey("barcode"),
      is(false));
  }

  @Test
  public void creatingARequestIgnoresReadOnlyInformationProvidedByClient()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsClient.create(basedUponSmallAngryPlanet()
      .withBarcode("036000291452")
      .create()).getId();


    UUID requesterId = usersClient.create(new UserRequestBuilder()
      .withName("Jones", "Steven")
      .withBarcode("564376549214")
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

    requestRequest.put("requester", new JsonObject()
      .put("lastName", "incorrect")
      .put("firstName", "information")
      .put("middleName", "only")
      .put("barcode", "453956079534"));

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(InterfaceUrls.requestsUrl(), requestRequest,
      ResponseHandler.json(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create request: %s", postResponse.getBody()),
      postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject representation = postResponse.getJson();

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

    assertThat("barcode is taken from requesting user",
      representation.getJsonObject("requester").getString("barcode"),
      is("564376549214"));
  }

  @Test
  public void canGetARequestById()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsClient.create(basedUponSmallAngryPlanet()
      .withBarcode("036000291452")
      .create()).getId();

    UUID requesterId = usersClient.create(new UserRequestBuilder()
      .withName("Jones", "Steven")
      .withBarcode("564376549214")
      .create()).getId();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    requestsClient.create(new RequestRequestBuilder()
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
    TimeoutException,
    UnsupportedEncodingException {

    Response getResponse = ResourceClient.forRequests(client).getById(UUID.randomUUID());

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void canPageAllRequests()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    UUID requesterId = usersClient.create(new UserRequestBuilder()
      .create()).getId();

    requestsClient.create(new RequestRequestBuilder()
      .withItemId(itemsClient.create(basedUponSmallAngryPlanet().create()).getId())
      .withRequesterId(requesterId)
      .create());

    requestsClient.create(new RequestRequestBuilder()
      .withItemId(itemsClient.create(basedUponNod().create()).getId())
      .withRequesterId(requesterId)
      .create());

    requestsClient.create(new RequestRequestBuilder()
      .withItemId(itemsClient.create(basedUponInterestingTimes().create()).getId())
      .withRequesterId(requesterId)
      .create());

    requestsClient.create(new RequestRequestBuilder()
      .withItemId(itemsClient.create(basedUponTemeraire().create()).getId())
      .withRequesterId(requesterId)
      .create());

    requestsClient.create(new RequestRequestBuilder()
      .withItemId(itemsClient.create(basedUponNod().create()).getId())
      .withRequesterId(requesterId)
      .create());

    requestsClient.create(new RequestRequestBuilder()
      .withItemId(itemsClient.create(basedUponUprooted().create()).getId())
      .withRequesterId(requesterId)
      .create());

    requestsClient.create(new RequestRequestBuilder()
      .withItemId(itemsClient.create(basedUponTemeraire().create()).getId())
      .withRequesterId(requesterId)
      .create());

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

    firstPageRequests.forEach(request -> requestHasExpectedProperties(request));
    secondPageRequests.forEach(request -> requestHasExpectedProperties(request));
  }

  @Test
  public void canSearchForRequestsByRequesterLastName()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    UUID firstRequester = usersClient.create(new UserRequestBuilder()
      .withName("Jones", "Steven")
      .create()).getId();

    UUID secondRequester = usersClient.create(new UserRequestBuilder()
      .withName("Norton", "Jessica")
      .create()).getId();

    requestsClient.create(new RequestRequestBuilder()
      .withItemId(itemsClient.create(basedUponSmallAngryPlanet().create()).getId())
      .withRequesterId(firstRequester)
      .create());

    requestsClient.create(new RequestRequestBuilder()
      .withItemId(itemsClient.create(basedUponNod()
        .create()).getId())
      .withRequesterId(firstRequester)
      .create());

    requestsClient.create(new RequestRequestBuilder()
      .withItemId(itemsClient.create(basedUponInterestingTimes()
        .create()).getId())
      .withRequesterId(secondRequester)
      .create());

    requestsClient.create(new RequestRequestBuilder()
      .withItemId(itemsClient.create(basedUponTemeraire()
        .create()).getId())
      .withRequesterId(firstRequester)
      .create());

    requestsClient.create(new RequestRequestBuilder()
      .withItemId(itemsClient.create(basedUponNod()
        .create()).getId())
      .withRequesterId(firstRequester)
      .create());

    requestsClient.create(new RequestRequestBuilder()
      .withItemId(itemsClient.create(basedUponUprooted()
        .create()).getId())
      .withRequesterId(secondRequester)
      .create());

    requestsClient.create(new RequestRequestBuilder()
      .withItemId(itemsClient.create(basedUponTemeraire()
        .create()).getId())
      .withRequesterId(secondRequester)
      .create());

    CompletableFuture<Response> getRequestsCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.requestsUrl() + String.format("?query=requester.lastName=%s", "Norton"),
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

    UUID requesterId = usersClient.create(new UserRequestBuilder().create()).getId();

    requestsClient.create(new RequestRequestBuilder()
      .withItemId(itemsClient.create(basedUponSmallAngryPlanet().create()).getId())
      .withRequesterId(requesterId)
      .create());

    requestsClient.create(new RequestRequestBuilder()
      .withItemId(itemsClient.create(basedUponNod().create()).getId())
      .withRequesterId(requesterId)
      .create());

    requestsClient.create(new RequestRequestBuilder()
      .withItemId(itemsClient.create(basedUponInterestingTimes().create()).getId())
      .withRequesterId(requesterId)
      .create());

    requestsClient.create(new RequestRequestBuilder()
      .withItemId(itemsClient.create(basedUponTemeraire().create()).getId())
      .withRequesterId(requesterId)
      .create());

    requestsClient.create(new RequestRequestBuilder()
      .withItemId(itemsClient.create(basedUponNod().create()).getId())
      .withRequesterId(requesterId)
      .create());

    requestsClient.create(new RequestRequestBuilder()
      .withItemId(itemsClient.create(basedUponUprooted().create()).getId())
      .withRequesterId(requesterId)
      .create());

    requestsClient.create(new RequestRequestBuilder()
      .withItemId(itemsClient.create(basedUponTemeraire().create()).getId())
      .withRequesterId(requesterId)
      .create());

    CompletableFuture<Response> getRequestsCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.requestsUrl() + String.format("?query=item.title=Nod"),
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

    UUID itemId = itemsClient.create(basedUponTemeraire()
      .withBarcode("07295629642")
      .create()).getId();

    UUID originalRequesterId = usersClient.create(new UserRequestBuilder()
      .withName("Norton", "Jessica")
      .withBarcode("764523186496")
      .create()).getId();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    JsonObject requestRequest = new RequestRequestBuilder()
      .recall()
      .withId(id)
      .withRequestDate(requestDate)
      .withItemId(itemId)
      .withRequesterId(originalRequesterId)
      .fulfilToHoldShelf()
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31))
      .create();

    IndividualResource createdRequest = requestsClient.create(requestRequest);

    UUID updatedRequester = usersClient.create(new UserRequestBuilder()
      .withName("Campbell", "Fiona")
      .withBarcode("679231693475")
      .create()).getId();

    JsonObject updatedRequest = createdRequest.copyJson();

    updatedRequest
      .put("requestType", "Hold")
      .put("requesterId", updatedRequester.toString());

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(InterfaceUrls.requestsUrl(String.format("/%s", id)),
      updatedRequest, ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.requestsUrl(String.format("/%s", id)),
      ResponseHandler.any(getCompleted));

    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get request: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject representation = getResponse.getJson();

    assertThat(representation.getString("id"), is(id.toString()));
    assertThat(representation.getString("requestType"), is("Hold"));
    assertThat(representation.getString("requestDate"), isEquivalentTo(requestDate));
    assertThat(representation.getString("itemId"), is(itemId.toString()));
    assertThat(representation.getString("requesterId"), is(updatedRequester.toString()));
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
      is("Campbell"));

    assertThat("first name is taken from requesting user",
      representation.getJsonObject("requester").getString("firstName"),
      is("Fiona"));

    assertThat("middle name is not taken from requesting user",
      representation.getJsonObject("requester").containsKey("middleName"),
      is(false));

    assertThat("barcode is taken from requesting user",
      representation.getJsonObject("requester").getString("barcode"),
      is("679231693475"));
  }

  @Test
  public void replacingAnExistingRequestRemovesItemInformationWhenItemDoesNotExist()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsClient.create(basedUponTemeraire()
      .withBarcode("07295629642")
      .create()).getId();

    UUID requesterId = usersClient.create(new UserRequestBuilder().create()).getId();

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

    IndividualResource createdRequest = requestsClient.create(requestRequest);

    JsonObject updatedRequest = createdRequest.copyJson();

    itemsClient.delete(itemId);

    updatedRequest
      .put("requestType", "Hold");

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(InterfaceUrls.requestsUrl(String.format("/%s", id)),
      updatedRequest, ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.requestsUrl(String.format("/%s", id)),
      ResponseHandler.any(getCompleted));

    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get request: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject representation = getResponse.getJson();

    assertThat(representation.getString("itemId"), is(itemId.toString()));

    assertThat("has no item information when item no longer exists",
      representation.containsKey("item"), is(false));
  }

  @Test
  public void replacingAnExistingRequestRemovesRequesterInformationWhenUserDoesNotExist()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsClient.create(basedUponTemeraire()
      .withBarcode("07295629642")
      .create()).getId();

    UUID requester = usersClient.create(new UserRequestBuilder()
      .withName("Norton", "Jessica")
      .withBarcode("679231693475")
      .create()).getId();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    JsonObject requestRequest = new RequestRequestBuilder()
      .recall()
      .withId(id)
      .withRequestDate(requestDate)
      .withItemId(itemId)
      .withRequesterId(requester)
      .fulfilToHoldShelf()
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31))
      .create();

    IndividualResource createdRequest = requestsClient.create(requestRequest);

    usersClient.delete(requester);

    JsonObject updatedRequest = createdRequest.copyJson();

    updatedRequest
      .put("requestType", "Hold");

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(InterfaceUrls.requestsUrl(String.format("/%s", id)),
      updatedRequest, ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.requestsUrl(String.format("/%s", id)),
      ResponseHandler.any(getCompleted));

    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get request: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject representation = getResponse.getJson();

    assertThat(representation.getString("requesterId"), is(requester.toString()));

    assertThat("has no requesting user information taken when user no longer exists",
      representation.containsKey("requester"), is(false));
  }

  @Test
  public void replacingAnExistingRequestRemovesRequesterBarcodeWhenNonePresent()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsClient.create(basedUponTemeraire()
      .withBarcode("07295629642")
      .create()).getId();

    UUID originalRequesterId = usersClient.create(new UserRequestBuilder()
      .withName("Norton", "Jessica")
      .withBarcode("764523186496")
      .create()).getId();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    JsonObject requestRequest = new RequestRequestBuilder()
      .recall()
      .withId(id)
      .withRequestDate(requestDate)
      .withItemId(itemId)
      .withRequesterId(originalRequesterId)
      .fulfilToHoldShelf()
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31))
      .create();

    IndividualResource createdRequest = requestsClient.create(requestRequest);

    UUID updatedRequester = usersClient.create(new UserRequestBuilder()
      .withName("Campbell", "Fiona")
      .withNoBarcode()
      .create()).getId();

    JsonObject updatedRequest = createdRequest.copyJson();

    updatedRequest
      .put("requestType", "Hold")
      .put("requesterId", updatedRequester.toString());

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(InterfaceUrls.requestsUrl(String.format("/%s", id)),
      updatedRequest, ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.requestsUrl(String.format("/%s", id)),
      ResponseHandler.any(getCompleted));

    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get request: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject representation = getResponse.getJson();

    assertThat(representation.getString("requesterId"), is(updatedRequester.toString()));

    assertThat("has information taken from requesting user",
      representation.containsKey("requester"), is(true));

    assertThat("last name is taken from requesting user",
      representation.getJsonObject("requester").getString("lastName"),
      is("Campbell"));

    assertThat("first name is taken from requesting user",
      representation.getJsonObject("requester").getString("firstName"),
      is("Fiona"));

    assertThat("barcode is not present when requesting user does not have one",
      representation.getJsonObject("requester").containsKey("barcode"),
      is(false));
  }

  @Test
  public void replacingAnExistingRequestIncludesRequesterMiddleNameWhenPresent()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsClient.create(basedUponTemeraire()
      .withBarcode("07295629642")
      .create()).getId();

    UUID originalRequesterId = usersClient.create(new UserRequestBuilder()
      .withName("Norton", "Jessica")
      .withBarcode("764523186496")
      .create()).getId();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    JsonObject requestRequest = new RequestRequestBuilder()
      .recall()
      .withId(id)
      .withRequestDate(requestDate)
      .withItemId(itemId)
      .withRequesterId(originalRequesterId)
      .fulfilToHoldShelf()
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31))
      .create();

    IndividualResource createdRequest = requestsClient.create(requestRequest);

    UUID updatedRequester = usersClient.create(new UserRequestBuilder()
      .withName("Campbell", "Fiona", "Stella")
      .withBarcode("679231693475")
      .create()).getId();

    JsonObject updatedRequest = createdRequest.copyJson();

    updatedRequest
      .put("requestType", "Hold")
      .put("requesterId", updatedRequester.toString());

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(InterfaceUrls.requestsUrl(String.format("/%s", id)),
      updatedRequest, ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.requestsUrl(String.format("/%s", id)),
      ResponseHandler.any(getCompleted));

    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get request: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject representation = getResponse.getJson();

    assertThat(representation.getString("id"), is(id.toString()));
    assertThat(representation.getString("requestType"), is("Hold"));
    assertThat(representation.getString("requestDate"), isEquivalentTo(requestDate));
    assertThat(representation.getString("itemId"), is(itemId.toString()));
    assertThat(representation.getString("requesterId"), is(updatedRequester.toString()));
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
      is("Campbell"));

    assertThat("first name is taken from requesting user",
      representation.getJsonObject("requester").getString("firstName"),
      is("Fiona"));

    assertThat("middle name is taken from requesting user",
      representation.getJsonObject("requester").getString("middleName"),
      is("Stella"));

    assertThat("barcode is taken from requesting user",
      representation.getJsonObject("requester").getString("barcode"),
      is("679231693475"));
  }

  @Test
  public void replacingAnExistingRequestRemovesItemBarcodeWhenNonePresent()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    UUID originalItemId = itemsClient.create(basedUponTemeraire()
      .withBarcode("07295629642")
      .create()).getId();

    UUID requesterId = usersClient.create(new UserRequestBuilder().create()).getId();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    JsonObject requestRequest = new RequestRequestBuilder()
      .recall()
      .withId(id)
      .withRequestDate(requestDate)
      .withItemId(originalItemId)
      .withRequesterId(requesterId)
      .fulfilToHoldShelf()
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31))
      .create();

    IndividualResource createdRequest = requestsClient.create(requestRequest);

    UUID updatedItemId = itemsClient.create(basedUponSmallAngryPlanet()
      .withNoBarcode()
      .create()).getId();

    JsonObject updatedRequest = createdRequest.copyJson();

    updatedRequest
      .put("itemId", updatedItemId.toString());

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(InterfaceUrls.requestsUrl(String.format("/%s", id)),
      updatedRequest, ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.requestsUrl(String.format("/%s", id)),
      ResponseHandler.any(getCompleted));

    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get request: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject representation = getResponse.getJson();

    assertThat(representation.getString("itemId"), is(updatedItemId.toString()));

    assertThat("has information taken from item",
      representation.containsKey("item"), is(true));

    assertThat("title is taken from item",
      representation.getJsonObject("item").getString("title"),
      is("The Long Way to a Small, Angry Planet"));

    assertThat("barcode is not taken from item",
      representation.getJsonObject("item").containsKey("barcode"),
      is(false));
  }

  @Test
  public void canDeleteAllRequests()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    UUID requesterId = usersClient.create(new UserRequestBuilder().create()).getId();

    requestsClient.create(new RequestRequestBuilder()
      .withItemId(itemsClient.create(basedUponSmallAngryPlanet().create()).getId())
      .withRequesterId(requesterId)
      .create());

    requestsClient.create(new RequestRequestBuilder()
      .withItemId(itemsClient.create(basedUponNod().create()).getId())
      .withRequesterId(requesterId)
      .create());

    requestsClient.create(new RequestRequestBuilder()
      .withItemId(itemsClient.create(basedUponInterestingTimes().create()).getId())
      .withRequesterId(requesterId)
      .create());

    requestsClient.create(new RequestRequestBuilder()
      .withItemId(itemsClient.create(basedUponTemeraire().create()).getId())
      .withRequesterId(requesterId)
      .create());

    requestsClient.create(new RequestRequestBuilder()
      .withItemId(itemsClient.create(basedUponUprooted().create()).getId())
      .withRequesterId(requesterId)
      .create());

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    client.delete(InterfaceUrls.requestsUrl(),
      ResponseHandler.any(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    CompletableFuture<Response> getAllCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.requestsUrl(), ResponseHandler.any(getAllCompleted));

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

    UUID requesterId = usersClient.create(new UserRequestBuilder().create()).getId();

    requestsClient.create(new RequestRequestBuilder()
      .withId(firstId)
      .withItemId(itemsClient.create(basedUponNod().create()).getId())
      .withRequesterId(requesterId)
      .create());

    requestsClient.create(new RequestRequestBuilder()
      .withId(secondId)
      .withItemId(itemsClient.create(basedUponSmallAngryPlanet().create()).getId())
      .withRequesterId(requesterId)
      .create());

    requestsClient.create(new RequestRequestBuilder()
      .withId(thirdId)
      .withItemId(itemsClient.create(basedUponTemeraire().create()).getId())
      .withRequesterId(requesterId)
      .create());

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    client.delete(InterfaceUrls.requestsUrl(String.format("/%s", secondId)),
      ResponseHandler.any(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    assertThat(ResourceClient.forRequests(client).getById(firstId).getStatusCode(), is(HttpURLConnection.HTTP_OK));

    assertThat(ResourceClient.forRequests(client).getById(secondId).getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));

    assertThat(ResourceClient.forRequests(client).getById(thirdId).getStatusCode(), is(HttpURLConnection.HTTP_OK));

    CompletableFuture<Response> getAllCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.requestsUrl(), ResponseHandler.any(getAllCompleted));

    Response getAllResponse = getAllCompleted.get(5, TimeUnit.SECONDS);

    assertThat(getAllResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject allRequests = getAllResponse.getJson();

    List<JsonObject> requests = getRequests(allRequests);

    assertThat(requests.size(), is(2));
    assertThat(allRequests.getInteger("totalRecords"), is(2));
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
}
