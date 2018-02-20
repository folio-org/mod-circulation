package org.folio.circulation.api.requests;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.support.APITests;
import org.folio.circulation.api.support.builders.ItemBuilder;
import org.folio.circulation.api.support.builders.RequestBuilder;
import org.folio.circulation.api.support.builders.UserBuilder;
import org.folio.circulation.api.support.http.InterfaceUrls;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.folio.circulation.api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class RequestsAPIUpdatingTests extends APITests {
  @Test
  public void canReplaceAnExistingRequest()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponTemeraire(
      itemRequestBuilder -> itemRequestBuilder.withBarcode("07295629642"))
      .getId();

    loansFixture.checkOutItem(itemId);

    UUID originalRequesterId = usersClient.create(new UserBuilder()
      .withName("Norton", "Jessica")
      .withBarcode("764523186496"))
      .getId();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
      .recall()
      .withId(id)
      .withRequestDate(requestDate)
      .withItemId(itemId)
      .withRequesterId(originalRequesterId)
      .fulfilToHoldShelf()
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31)));

    UUID updatedRequester = usersClient.create(new UserBuilder()
      .withName("Campbell", "Fiona")
      .withBarcode("679231693475"))
      .getId();

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
    ExecutionException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponNod().getId();

    loansFixture.checkOutItem(itemId);

    UUID requesterId = usersClient.create(new UserBuilder()).getId();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .withId(id)
        .withRequestDate(requestDate)
        .withItemId(itemId)
        .withRequesterId(requesterId)
        .fulfilToHoldShelf()
        .withRequestExpiration(new LocalDate(2017, 7, 30))
        .withHoldShelfExpiration(new LocalDate(2017, 8, 31)));

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

    UUID itemId = itemsFixture.basedUponNod().getId();

    loansFixture.checkOutItem(itemId);

    UUID requester = usersClient.create(new UserBuilder()
      .withName("Norton", "Jessica")
      .withBarcode("679231693475"))
      .getId();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .withId(id)
        .withRequestDate(requestDate)
        .withItemId(itemId)
        .withRequesterId(requester)
        .fulfilToHoldShelf()
        .withRequestExpiration(new LocalDate(2017, 7, 30))
        .withHoldShelfExpiration(new LocalDate(2017, 8, 31)));

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

    UUID itemId = itemsFixture.basedUponNod().getId();

    loansFixture.checkOutItem(itemId);

    UUID originalRequesterId = usersClient.create(new UserBuilder()
      .withName("Norton", "Jessica")
      .withBarcode("764523186496"))
      .getId();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .withId(id)
        .withRequestDate(requestDate)
        .withItemId(itemId)
        .withRequesterId(originalRequesterId)
        .fulfilToHoldShelf()
        .withRequestExpiration(new LocalDate(2017, 7, 30))
        .withHoldShelfExpiration(new LocalDate(2017, 8, 31)));

    UUID updatedRequester = usersClient.create(new UserBuilder()
      .withName("Campbell", "Fiona")
      .withNoBarcode())
      .getId();

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

    UUID itemId = itemsFixture.basedUponTemeraire(
      itemRequestBuilder -> itemRequestBuilder.withBarcode("07295629642"))
      .getId();

    loansFixture.checkOutItem(itemId);

    UUID originalRequesterId = usersClient.create(new UserBuilder()
      .withName("Norton", "Jessica")
      .withBarcode("764523186496"))
      .getId();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
      .recall()
      .withId(id)
      .withRequestDate(requestDate)
      .withItemId(itemId)
      .withRequesterId(originalRequesterId)
      .fulfilToHoldShelf()
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31)));

    UUID updatedRequester = usersClient.create(new UserBuilder()
      .withName("Campbell", "Fiona", "Stella")
      .withBarcode("679231693475")).getId();

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

    UUID originalItemId = itemsFixture.basedUponTemeraire().getId();

    loansFixture.checkOutItem(originalItemId);

    UUID requesterId = usersClient.create(new UserBuilder()).getId();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
      .recall()
      .withId(id)
      .withRequestDate(requestDate)
      .withItemId(originalItemId)
      .withRequesterId(requesterId)
      .fulfilToHoldShelf()
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31)));

    UUID updatedItemId = itemsFixture.basedUponSmallAngryPlanet(ItemBuilder::withNoBarcode)
      .getId();

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
}
