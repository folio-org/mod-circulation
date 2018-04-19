package api.requests;

import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import api.support.APITests;
import api.support.builders.ItemBuilder;
import api.support.builders.RequestBuilder;
import api.support.builders.UserBuilder;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.folio.HttpStatus.*;
import static api.support.builders.RequestBuilder.OPEN_NOT_YET_FILLED;
import static api.support.http.InterfaceUrls.requestsUrl;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

@RunWith(JUnitParamsRunner.class)
public class RequestsAPICreationTests extends APITests {

  @Test
  public void canCreateARequest()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();

    loansFixture.checkOut(item, usersFixture.jessica());

    IndividualResource requester = usersFixture.steve();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    IndividualResource request = requestsFixture.place(new RequestBuilder()
      .withId(id)
      .open()
      .recall()
      .forItem(item)
      .by(requester)
      .withRequestDate(requestDate)
      .fulfilToHoldShelf()
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31)));

    JsonObject representation = request.getJson();

    assertThat(representation.getString("id"), is(id.toString()));
    assertThat(representation.getString("requestType"), is("Recall"));
    assertThat(representation.getString("requestDate"), isEquivalentTo(requestDate));
    assertThat(representation.getString("itemId"), is(item.getId().toString()));
    assertThat(representation.getString("requesterId"), is(requester.getId().toString()));
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
      is("5694596854"));

    assertThat("does not have information taken from proxying user",
      representation.containsKey("proxy"), is(false));

    assertThat("should have change metadata",
      representation.containsKey("metaData"), is(true));

    JsonObject changeMetadata = representation.getJsonObject("metaData");

    assertThat("change metadata should have created date",
      changeMetadata.containsKey("createdDate"), is(true));

    assertThat("change metadata should have updated date",
      changeMetadata.containsKey("updatedDate"), is(true));
  }

  @Test
  public void cannotCreateRequestForUnknownItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();

    JsonObject requestRequest = new RequestBuilder()
      .recall()
      .withId(id)
      .withItemId(itemId)
      .withRequesterId(usersClient.create(new UserBuilder()).getId())
      .create();

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(requestsUrl(), requestRequest,
      ResponseHandler.json(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);
    assertThat(postResponse, hasStatus(HTTP_VALIDATION_ERROR));
  }

  @Test
  public void cannotCreateRecallRequestWhenItemIsNotCheckedOut()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();
    UUID itemId = itemsFixture.basedUponSmallAngryPlanet(
      ItemBuilder::available)
      .getId();

    JsonObject requestRequest = new RequestBuilder()
      .recall()
      .withId(id)
      .withItemId(itemId)
      .withRequesterId(usersClient.create(new UserBuilder()).getId())
      .create();

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(requestsUrl(), requestRequest,
      ResponseHandler.json(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);
    assertThat(postResponse, hasStatus(HTTP_VALIDATION_ERROR));
  }

  @Test
  public void cannotCreateHoldRequestWhenItemIsNotCheckedOut()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();
    UUID itemId = itemsFixture.basedUponSmallAngryPlanet(
      ItemBuilder::available)
      .getId();

    JsonObject requestRequest = new RequestBuilder()
      .hold()
      .withId(id)
      .withItemId(itemId)
      .withRequesterId(usersClient.create(new UserBuilder()).getId())
      .create();

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(requestsUrl(), requestRequest,
      ResponseHandler.json(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);
    assertThat(postResponse, hasStatus(HTTP_VALIDATION_ERROR));
  }

  //TODO: Remove this once sample data is updated, temporary to aid change of item status case
  @Test()
  public void canCreateARequestEvenWithDifferentCaseCheckedOutStatus()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    IndividualResource itemResponse = itemsFixture.basedUponSmallAngryPlanet();

    UUID itemId = itemResponse.getId();

    loansFixture.checkOutItem(itemId);

    JsonObject itemWithChangedStatus = itemResponse.copyJson()
      .put("status", new JsonObject().put("name", "Checked Out"));

    itemsClient.replace(itemId, itemWithChangedStatus);

    UUID requesterId = usersClient.create(new UserBuilder()
      .withName("Jones", "Steven")
      .withBarcode("564376549214"))
      .getId();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    JsonObject requestRequest = new RequestBuilder()
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
    assertThat(postResponse, hasStatus(HTTP_CREATED));
  }

  @Test
  public void canCreateAPageRequestForAvailableItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();
    UUID itemId = itemsFixture.basedUponSmallAngryPlanet(
      ItemBuilder::available)
      .getId();

    JsonObject requestRequest = new RequestBuilder()
      .page()
      .withId(id)
      .withItemId(itemId)
      .withRequesterId(usersClient.create(new UserBuilder()).getId())
      .create();

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(requestsUrl(), requestRequest,
      ResponseHandler.json(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);
    assertThat(postResponse, hasStatus(HTTP_CREATED));
  }

  @Test
  @Parameters({
    "Open - Not yet filled",
    "Open - Awaiting pickup",
    "Closed - Filled"
  })
  public void canCreateARequestWithValidStatus(String status)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet(
      itemBuilder -> itemBuilder
        .withBarcode("036000291452"))
      .getId();

    loansFixture.checkOutItem(itemId);

    UUID requesterId = usersClient.create(new UserBuilder()
      .withName("Jones", "Steven")
      .withBarcode("564376549214"))
      .getId();

    JsonObject requestRequest = new RequestBuilder()
      .recall()
      .toHoldShelf()
      .withItemId(itemId)
      .withRequesterId(requesterId)
      .withStatus(status)
      .create();

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(requestsUrl(), requestRequest,
      ResponseHandler.json(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);
    assertThat(postResponse, hasStatus(HTTP_CREATED));

    JsonObject representation = postResponse.getJson();

    assertThat(representation.getString("status"), is(status));
  }

  //TODO: Replace with validation error message
  @Test
  @Parameters({
    "Non-existent status",
    ""
  })
  public void cannotCreateARequestWithInvalidStatus(String status)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet(
      itemBuilder -> itemBuilder
        .withBarcode("036000291452"))
      .getId();

    loansFixture.checkOutItem(itemId);

    UUID requesterId = usersClient.create(new UserBuilder()
      .withName("Jones", "Steven")
      .withBarcode("564376549214"))
      .getId();

    JsonObject requestRequest = new RequestBuilder()
      .recall()
      .toHoldShelf()
      .withItemId(itemId)
      .withRequesterId(requesterId)
      .withStatus(status)
      .create();

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(requestsUrl(), requestRequest,
      ResponseHandler.any(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Should not create request: %s", postResponse.getBody()),
      postResponse, hasStatus(HTTP_BAD_REQUEST));

    assertThat(postResponse.getBody(),
      is("Request status must be \"Open - Not yet filled\", \"Open - Awaiting pickup\" or \"Closed - Filled\""));
  }

  @Test
  public void canCreateARequestToBeFulfilledByDeliveryToAnAddress()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet(
      ItemBuilder::available)
      .getId();

    loansFixture.checkOutItem(itemId);

    UUID deliveryAddressTypeId = UUID.randomUUID();

    IndividualResource createdRequest = requestsClient.create(new RequestBuilder()
      .recall()
      .withItemId(itemId)
      .deliverToAddress(deliveryAddressTypeId)
      .withRequesterId(usersClient.create(new UserBuilder()).getId()));

    JsonObject representation = createdRequest.getJson();

    assertThat(representation.getString("id"), is(not(emptyString())));
    assertThat(representation.getString("requestType"), is("Recall"));
    assertThat(representation.getString("fulfilmentPreference"), is("Delivery"));
    assertThat(representation.getString("deliveryAddressTypeId"),
      is(deliveryAddressTypeId.toString()));
  }

  @Test
  public void requestStatusDefaultsToOpen()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet(
      itemBuilder -> itemBuilder
        .withBarcode("036000291452"))
      .getId();

    loansFixture.checkOutItem(itemId);

    UUID requesterId = usersClient.create(new UserBuilder()
      .withName("Jones", "Steven")
      .withBarcode("564376549214"))
      .getId();

    JsonObject requestRequest = new RequestBuilder()
      .recall()
      .toHoldShelf()
      .withItemId(itemId)
      .withRequesterId(requesterId)
      .withNoStatus()
      .create();

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(requestsUrl(), requestRequest,
      ResponseHandler.json(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);
    assertThat(postResponse, hasStatus(HTTP_CREATED));

    JsonObject representation = postResponse.getJson();

    assertThat(representation.getString("status"), is(OPEN_NOT_YET_FILLED));
  }

  @Test
  public void creatingARequestDoesNotStoreRequesterInformationWhenUserNotFound()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    loansFixture.checkOutItem(itemId);

    UUID requesterId = UUID.randomUUID();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    JsonObject requestRequest = new RequestBuilder()
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

    assertThat(postResponse, hasStatus(HTTP_CREATED));

    JsonObject representation = postResponse.getJson();

    assertThat("has no information for missing requesting user",
      representation.containsKey("requester"), is(false));
  }

  @Test
  public void creatingARequestStoresItemInformationWhenRequestingUserNotFound()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    loansFixture.checkOutItem(itemId);

    UUID requesterId = UUID.randomUUID();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    JsonObject requestRequest = new RequestBuilder()
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

    assertThat(postResponse, hasStatus(HTTP_CREATED));

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
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    UUID requesterId = usersClient.create(new UserBuilder()
      .withName("Jones", "Steven", "Anthony")
      .withBarcode("564376549214"))
      .getId();

    loansFixture.checkOutItem(itemId);

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    JsonObject requestRequest = new RequestBuilder()
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

    assertThat(postResponse, hasStatus(HTTP_CREATED));

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
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    loansFixture.checkOutItem(itemId);

    UUID requesterId = usersClient.create(new UserBuilder()
      .withName("Jones", "Steven")
      .withNoBarcode())
      .getId();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    JsonObject requestRequest = new RequestBuilder()
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

    assertThat(postResponse, hasStatus(HTTP_CREATED));

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
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet(
      ItemBuilder::withNoBarcode)
      .getId();

    loansFixture.checkOutItem(itemId);

    JsonObject requestRequest = new RequestBuilder()
      .recall()
      .withId(id)
      .withItemId(itemId)
      .withRequesterId(usersClient.create(new UserBuilder()).getId())
      .create();

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(requestsUrl(), requestRequest,
      ResponseHandler.json(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(postResponse, hasStatus(HTTP_CREATED));

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
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    loansFixture.checkOutItem(itemId);

    UUID requesterId = usersClient.create(new UserBuilder()
      .withName("Jones", "Steven")
      .withBarcode("564376549214"))
      .getId();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    JsonObject requestRequest = new RequestBuilder()
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

    client.post(requestsUrl(), requestRequest,
      ResponseHandler.json(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(postResponse, hasStatus(HTTP_CREATED));

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
}
