package api.requests;

import static api.support.builders.RequestBuilder.OPEN_NOT_YET_FILLED;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.UUIDMatcher.is;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasUUIDParameter;
import static java.util.Arrays.asList;
import static org.folio.HttpStatus.HTTP_BAD_REQUEST;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_VALIDATION_ERROR;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Test;
import org.junit.runner.RunWith;

import api.support.APITests;
import api.support.builders.Address;
import api.support.builders.ItemBuilder;
import api.support.builders.RequestBuilder;
import api.support.builders.UserBuilder;
import api.support.fixtures.ItemsFixture;
import api.support.fixtures.LoansFixture;
import api.support.fixtures.RequestsFixture;
import api.support.fixtures.UsersFixture;
import api.support.http.InventoryItemResource;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class RequestsAPICreationTests extends APITests {

  @Test
  public void canCreateARequest()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();

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
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31))
      .withPickupServicePointId(pickupServicePointId)
      .withTags(new RequestBuilder.Tags(asList("new", "important"))));

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
    assertThat(representation.getString("pickupServicePointId"), is(pickupServicePointId.toString()));

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
      representation.containsKey("metadata"), is(true));

    JsonObject changeMetadata = representation.getJsonObject("metadata");

    assertThat("change metadata should have created date",
      changeMetadata.containsKey("createdDate"), is(true));

    assertThat("change metadata should have updated date",
      changeMetadata.containsKey("updatedDate"), is(true));

    assertThat(representation.containsKey("tags"), is(true));
    final JsonObject tagsRepresentation = representation.getJsonObject("tags");

    assertThat(tagsRepresentation.containsKey("tagList"), is(true));
    assertThat(tagsRepresentation.getJsonArray("tagList"), contains("new", "important"));
  }

  @Test
  public void canCreateARequestAtSpecificLocation()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();

    loansFixture.checkOut(item, usersFixture.jessica());

    IndividualResource requester = usersFixture.steve();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    Response response = requestsClient.attemptCreateAtSpecificLocation(new RequestBuilder()
      .withId(id)
      .open()
      .recall()
      .forItem(item)
      .by(requester)
      .withRequestDate(requestDate)
      .fulfilToHoldShelf()
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31)));

    assertThat(response.getStatusCode(), is(204));

    IndividualResource request = requestsClient.get(id);

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
      representation.containsKey("metadata"), is(true));

    JsonObject changeMetadata = representation.getJsonObject("metadata");

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

    UUID itemId = UUID.randomUUID();

    Response postResponse = requestsClient.attemptCreate(new RequestBuilder()
      .recall()
      .withItemId(itemId)
      .withRequesterId(usersFixture.charlotte().getId()));

    assertThat(postResponse, hasStatus(HTTP_VALIDATION_ERROR));
  }

  @Test
  public void cannotCreateRecallRequestWhenItemIsNotCheckedOut()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet(
      ItemBuilder::available)
      .getId();

    Response postResponse = requestsClient.attemptCreate(new RequestBuilder()
      .recall()
      .withItemId(itemId)
      .withRequesterId(usersFixture.charlotte().getId()));

    assertThat(postResponse, hasStatus(HTTP_VALIDATION_ERROR));
  }

  @Test
  public void cannotCreateHoldRequestWhenItemIsNotCheckedOut()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet(
      ItemBuilder::available)
      .getId();

    Response postResponse = requestsClient.attemptCreate(new RequestBuilder()
      .hold()
      .withItemId(itemId)
      .withRequesterId(usersFixture.charlotte().getId()));

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

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource rebecca = usersFixture.rebecca();

    UUID itemId = smallAngryPlanet.getId();

    loansFixture.checkOut(smallAngryPlanet, rebecca);

    JsonObject itemWithChangedStatus = smallAngryPlanet.copyJson()
      .put("status", new JsonObject().put("name", "Checked Out"));

    itemsClient.replace(itemId, itemWithChangedStatus);

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    requestsFixture.place(new RequestBuilder()
      .recall()
      .withId(id)
      .withRequestDate(requestDate)
      .forItem(smallAngryPlanet)
      .by(steve)
      .fulfilToHoldShelf()
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31)));
  }

  @Test
  @Parameters({
    "Open - Not yet filled",
    "Open - Awaiting pickup",
    "Open - In transit",
    "Closed - Filled"
  })
  public void canCreateARequestWithValidStatus(String status)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource smallAngryPlanet =
      itemsFixture.basedUponSmallAngryPlanet(itemBuilder -> itemBuilder
        .withBarcode("036000291452"));

    UUID itemId = smallAngryPlanet.getId();

    loansFixture.checkOutByBarcode(smallAngryPlanet);

    UUID requesterId = usersFixture.steve().getId();

    final IndividualResource request = requestsFixture.place(new RequestBuilder()
      .recall().fulfilToHoldShelf()
      .withItemId(itemId)
      .withRequesterId(requesterId)
      .withStatus(status));

    JsonObject representation = request.getJson();

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

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    final IndividualResource jessica = usersFixture.jessica();
    final IndividualResource steve = usersFixture.steve();

    loansFixture.checkOut(smallAngryPlanet, jessica);

    Response response = requestsClient.attemptCreate(
      new RequestBuilder()
        .recall().fulfilToHoldShelf()
        .forItem(smallAngryPlanet)
        .by(steve)
        .withStatus(status));

    assertThat(String.format("Should not create request: %s", response.getBody()),
      response, hasStatus(HTTP_BAD_REQUEST));

    assertThat(response.getBody(),
      is("Request status must be \"Open - Not yet filled\", " +
        "\"Open - Awaiting pickup\", \"Open - In transit\", " +
        "\"Closed - Filled\", \"Closed - Unfilled\" or \"Closed - Pickup expired\""));
  }

  //TODO: Replace with validation error message
  @Test
  @Parameters({
    "Non-existent status",
    ""
  })
  public void cannotCreateARequestAtASpecificLocationWithInvalidStatus(String status)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    final IndividualResource jessica = usersFixture.jessica();
    final IndividualResource steve = usersFixture.steve();

    loansFixture.checkOut(smallAngryPlanet, jessica);

    Response response = requestsClient.attemptCreateAtSpecificLocation(
      new RequestBuilder()
        .recall().fulfilToHoldShelf()
        .forItem(smallAngryPlanet)
        .by(steve)
        .withStatus(status));

    assertThat(String.format("Should not create request: %s", response.getBody()),
      response, hasStatus(HTTP_BAD_REQUEST));

    assertThat(response.getBody(),
      is("Request status must be \"Open - Not yet filled\", " +
        "\"Open - Awaiting pickup\", \"Open - In transit\", " +
        "\"Closed - Filled\", \"Closed - Unfilled\" or \"Closed - Pickup expired\""));
  }

  @Test
  public void canCreateARequestToBeFulfilledByDeliveryToAnAddress()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      ItemBuilder::available);

    final IndividualResource work = addressTypesFixture.work();

    final IndividualResource charlotte = usersFixture.charlotte(
      builder -> builder.withAddress(
        new Address(work.getId(),
          "Fake first address line",
          "Fake second address line",
          "Fake city",
          "Fake region",
          "Fake postal code",
          "Fake country code")));

    final IndividualResource james = usersFixture.james();

    loansFixture.checkOut(smallAngryPlanet, james);

    IndividualResource createdRequest = requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .deliverToAddress(work.getId())
      .by(charlotte));

    JsonObject representation = createdRequest.getJson();

    assertThat(representation.getString("id"), is(not(emptyString())));
    assertThat(representation.getString("requestType"), is("Recall"));
    assertThat(representation.getString("fulfilmentPreference"), is("Delivery"));
    assertThat(representation.getString("deliveryAddressTypeId"), is(work.getId()));

    assertThat("Request should have a delivery address",
      representation.containsKey("deliveryAddress"), is(true));

    final JsonObject deliveryAddress = representation.getJsonObject("deliveryAddress");

    assertThat(deliveryAddress.getString("addressTypeId"), is(work.getId()));
    assertThat(deliveryAddress.getString("addressLine1"), is("Fake first address line"));
    assertThat(deliveryAddress.getString("addressLine2"), is("Fake second address line"));
    assertThat(deliveryAddress.getString("city"), is("Fake city"));
    assertThat(deliveryAddress.getString("region"), is("Fake region"));
    assertThat(deliveryAddress.getString("postalCode"), is("Fake postal code"));
    assertThat(deliveryAddress.getString("countryId"), is("Fake country code"));
  }

  @Test
  public void requestStatusDefaultsToOpen()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();
    final IndividualResource steve = usersFixture.steve();

    loansFixture.checkOut(smallAngryPlanet, rebecca);

    IndividualResource createdRequest = requestsFixture.place(new RequestBuilder()
      .recall().fulfilToHoldShelf()
      .forItem(smallAngryPlanet)
      .by(steve)
      .withNoStatus());

    JsonObject representation = createdRequest.getJson();

    assertThat(representation.getString("status"), is(OPEN_NOT_YET_FILLED));
  }

  @Test
  public void creatingARequestDoesNotStoreRequesterInformationWhenUserNotFound()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    loansFixture.checkOut(smallAngryPlanet, jessica);

    UUID nonExistentRequester = UUID.randomUUID();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    IndividualResource createdRequest = requestsFixture.place(new RequestBuilder()
      .recall()
      .withRequestDate(requestDate)
      .forItem(smallAngryPlanet)
      .withRequesterId(nonExistentRequester));

    JsonObject representation = createdRequest.getJson();

    assertThat("has no information for missing requesting user",
      representation.containsKey("requester"), is(false));
  }

  @Test
  public void creatingARequestStoresItemInformationWhenRequestingUserNotFound()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    loansFixture.checkOut(smallAngryPlanet, steve);

    UUID nonExistentRequesterId = UUID.randomUUID();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    IndividualResource createdRequest = requestsFixture.place(new RequestBuilder()
      .recall()
      .withRequestDate(requestDate)
      .forItem(smallAngryPlanet)
      .withRequesterId(nonExistentRequesterId));

    JsonObject representation = createdRequest.getJson();

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

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource steve = usersFixture.steve(
      b -> b.withName("Jones", "Steven", "Anthony"));

    loansFixture.checkOut(smallAngryPlanet, jessica);

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    IndividualResource createdRequest = requestsFixture.place(new RequestBuilder()
      .recall()
      .withRequestDate(requestDate)
      .forItem(smallAngryPlanet)
      .by(steve));

    JsonObject representation = createdRequest.getJson();

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
      is("5694596854"));
  }

  @Test
  public void canCreateARequestWithRequesterWithNoBarcode()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource james = usersFixture.james();

    final IndividualResource steveWithNoBarcode = usersFixture.steve(
      UserBuilder::withNoBarcode);

    loansFixture.checkOut(smallAngryPlanet, james);

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    IndividualResource createdRequest = requestsFixture.place(new RequestBuilder()
      .recall()
      .withRequestDate(requestDate)
      .forItem(smallAngryPlanet)
      .by(steveWithNoBarcode));

    JsonObject representation = createdRequest.getJson();

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

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      ItemBuilder::withNoBarcode);

    final IndividualResource rebecca = usersFixture.rebecca();
    final IndividualResource charlotte = usersFixture.charlotte();

    loansFixture.checkOut(smallAngryPlanet, rebecca);

    IndividualResource createdRequest = requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .by(charlotte));

    JsonObject representation = createdRequest.getJson();

    assertThat(representation.getString("itemId"),
      is(smallAngryPlanet.getId().toString()));

    assertThat("has information taken from item",
      representation.containsKey("item"), is(true));

    assertThat("title is taken from item",
      representation.getJsonObject("item").getString("title"),
      is("The Long Way to a Small, Angry Planet"));

    assertThat("barcode is not taken from item when none present",
      representation.getJsonObject("item").containsKey("barcode"), is(false));
  }

  @Test
  public void creatingARequestIgnoresReadOnlyInformationProvidedByClient()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();
    final IndividualResource steve = usersFixture.steve();

    UUID itemId = smallAngryPlanet.getId();

    loansFixture.checkOut(smallAngryPlanet, rebecca);

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    JsonObject request = new RequestBuilder()
      .recall()
      .withRequestDate(requestDate)
      .withItemId(itemId)
      .by(steve)
      .create();

    request.put("item", new JsonObject()
      .put("title", "incorrect title information")
      .put("barcode", "753856498321"));

    request.put("requester", new JsonObject()
      .put("lastName", "incorrect")
      .put("firstName", "information")
      .put("middleName", "only")
      .put("barcode", "453956079534"));

    final IndividualResource createResponse = requestsClient.create(request);

    JsonObject representation = createResponse.getJson();

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
      is("5694596854"));
  }

  @Test
  public void cannotCreateARequestWithANonPickupLocationServicePoint()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID pickupServicePointId = servicePointsFixture.cd3().getId();

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();

    loansFixture.checkOut(item, usersFixture.jessica());

    IndividualResource requester = usersFixture.steve();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    Response postResponse = requestsClient.attemptCreate(new RequestBuilder()
      .open()
      .recall()
      .forItem(item)
      .by(requester)
      .withRequestDate(requestDate)
      .fulfilToHoldShelf()
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31))
      .withPickupServicePointId(pickupServicePointId));

    assertThat(postResponse, hasStatus(HTTP_VALIDATION_ERROR));

    assertThat(postResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Service point is not a pickup location"),
      hasUUIDParameter("pickupServicePointId", pickupServicePointId))));
  }

  @Test
  public void cannotCreateARequestWithUnknownPickupLocationServicePoint()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID pickupServicePointId = UUID.randomUUID();

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();

    loansFixture.checkOut(item, usersFixture.jessica());

    IndividualResource requester = usersFixture.steve();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    Response postResponse = requestsClient.attemptCreate(new RequestBuilder()
      .open()
      .recall()
      .forItem(item)
      .by(requester)
      .withRequestDate(requestDate)
      .fulfilToHoldShelf()
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31))
      .withPickupServicePointId(pickupServicePointId));

    assertThat(postResponse, hasStatus(HTTP_VALIDATION_ERROR));

    assertThat(postResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Pickup service point does not exist"),
      hasUUIDParameter("pickupServicePointId", pickupServicePointId))));
  }

  @Test
  public void canCreatePagedRequestWhenItemStatusIsAvailable()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    //Set up the item's initial status to be AVAILABLE
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final String itemInitialStatus = smallAngryPlanet.getResponse().getJson().getJsonObject("status").getString("name");
    assertThat(itemInitialStatus, is(ItemStatus.AVAILABLE.getValue()));

    //Attempt to create a page request on it.  Final expected status is PAGED
    final IndividualResource servicePoint = servicePointsFixture.cd1();
    final IndividualResource pagedRequest = requestsClient.create(new RequestBuilder()
      .page()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePoint.getId())
      .by(usersFixture.james()));

    String finalStatus = pagedRequest.getResponse().getJson().getJsonObject("item").getString("status");
    assertThat(pagedRequest.getJson().getString("requestType"), is(RequestType.PAGE.getValue()));
    assertThat(pagedRequest.getResponse(), hasStatus(HTTP_CREATED));
    assertThat(finalStatus, is(ItemStatus.PAGED.getValue()));
  }

  @Test
  public void cannotCreatePagedRequestWhenItemStatusIsCheckedOut()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    //Set up the item's initial status to be CHECKED OUT
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource checkedOutItem = loansFixture.checkOut(smallAngryPlanet, usersFixture.jessica());
    assertThat(checkedOutItem.getResponse().getJson().getJsonObject("item").getJsonObject("status").getString("name"), is(ItemStatus.CHECKED_OUT.getValue()));

    //Attempt to create a page request on it.
    final IndividualResource servicePoint = servicePointsFixture.cd1();
    final Response pagedRequest = requestsClient.attemptCreate(new RequestBuilder()
      .page()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePoint.getId())
      .by(usersFixture.jessica()));

    assertThat(pagedRequest, hasStatus(HTTP_VALIDATION_ERROR));
    JsonArray errors = pagedRequest.getJson().getJsonArray("errors");
    assertThat(errors.getJsonObject(0).getString("message").toLowerCase(), is("item is " + ItemStatus.CHECKED_OUT.toString().toLowerCase()));
  }

  @Test
  public void cannotCreatePagedRequestWhenItemStatusIsAwaitingPickup()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    //Setting up an item with AWAITING_PICKUP status
    final IndividualResource servicePoint = servicePointsFixture.cd1();

    final IndividualResource awaitingPickupItem = setupItemAwaitingPickup(servicePoint, requestsClient, itemsClient,
                                                                  itemsFixture, usersFixture, loansFixture);
    //attempt to place a PAGED request
    final Response pagedRequest2 = requestsClient.attemptCreate(new RequestBuilder()
      .page()
      .forItem(awaitingPickupItem)
      .withPickupServicePointId(servicePoint.getId())
      .by(usersFixture.jessica()));

    assertThat(pagedRequest2, hasStatus(HTTP_VALIDATION_ERROR));
    JsonArray errors = pagedRequest2.getJson().getJsonArray("errors");
    assertThat(errors.getJsonObject(0).getString("message").toLowerCase(), is("item is " + ItemStatus.AWAITING_PICKUP.toString().toLowerCase()));
  }

  @Test
  public void cannotCreatePagedRequestWhenItemStatusIsPaged()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    //Set up the item's initial status to be PAGED
    final IndividualResource servicePoint = servicePointsFixture.cd1();
    final IndividualResource pagedItem = setupPagedItem(servicePoint, itemsFixture, requestsClient, usersFixture);

    //Attempt to create a page request on it.
    final Response pagedRequest2 = requestsClient.attemptCreate(new RequestBuilder()
      .page()
      .forItem(pagedItem)
      .withPickupServicePointId(servicePoint.getId())
      .by(usersFixture.jessica()));

    assertThat(pagedRequest2, hasStatus(HTTP_VALIDATION_ERROR));
    JsonArray errors = pagedRequest2.getJson().getJsonArray("errors");
    assertThat(errors.getJsonObject(0).getString("message").toLowerCase(), is("item is "+ ItemStatus.PAGED.toString().toLowerCase()));
  }

  @Test
  public void cannotCreatePagedRequestWhenItemStatusIsIntransit()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException{

    final IndividualResource requestPickupServicePoint = servicePointsFixture.cd1();

    final IndividualResource intransitItem = setupItemInTransit(requestPickupServicePoint, servicePointsFixture.cd2(),
      itemsFixture, requestsClient,
      usersFixture, requestsFixture, loansFixture);

    //attempt to create a Paged request for this IN_TRANSIT item
    final Response pagedRequest2 = requestsClient.attemptCreate(new RequestBuilder()
      .page()
      .forItem(intransitItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.jessica()));

    assertThat(pagedRequest2, hasStatus(HTTP_VALIDATION_ERROR));
    JsonArray errors = pagedRequest2.getJson().getJsonArray("errors");
    assertThat(errors.getJsonObject(0).getString("message").toLowerCase(), is("item is "+ ItemStatus.IN_TRANSIT.toString().toLowerCase()));
  }

  @Test
  public void canCreateRecallRequestWhenItemIsCheckedOut()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException{

    final IndividualResource checkedOutItem = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestPickupServicePoint = servicePointsFixture.cd1();

    loansFixture.checkOut(checkedOutItem, usersFixture.jessica());

    final IndividualResource recallRequest = requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(checkedOutItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.james()));

    JsonObject requestedItem = recallRequest.getJson().getJsonObject("item");
    assertThat(recallRequest.getJson().getString("requestType"), is(RequestType.RECALL.getValue()));
    assertThat(requestedItem.getString("status"), is ( ItemStatus.CHECKED_OUT.getValue()));
    assertThat(recallRequest.getJson().getString("status"), is (RequestStatus.OPEN_NOT_YET_FILLED.getValue()));
  }

  @Test
  public void canCreateRecallRequestWhenItemIsAwaitingPickup()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException{

    //Setting up an item with AWAITING_PICKUP status
    final IndividualResource servicePoint = servicePointsFixture.cd1();
    final IndividualResource awaitingPickupItem = setupItemAwaitingPickup(servicePoint, requestsClient, itemsClient,
      itemsFixture, usersFixture, loansFixture);

    // create a recall request
    final IndividualResource recallRequest = requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(awaitingPickupItem)
      .withPickupServicePointId(servicePoint.getId())
      .by(usersFixture.steve()));

    assertThat(recallRequest.getJson().getString("requestType"), is(RequestType.RECALL.getValue()));
    assertThat(recallRequest.getJson().getJsonObject("item").getString("status"), is(ItemStatus.AWAITING_PICKUP.getValue()));
    assertThat(recallRequest.getJson().getString("status"), is (RequestStatus.OPEN_NOT_YET_FILLED.getValue()));
  }

  @Test
  public void canCreateRecallRequestWhenItemIsInTransit()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException{

    final IndividualResource requestPickupServicePoint = servicePointsFixture.cd1();

    final IndividualResource intransitItem = setupItemInTransit(requestPickupServicePoint, servicePointsFixture.cd2(),
                                                                    itemsFixture, requestsClient,
                                                                    usersFixture, requestsFixture, loansFixture);
    //create a Recall request
    final IndividualResource recallRequest = requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(intransitItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.jessica()));

    JsonObject requestItem = recallRequest.getJson().getJsonObject("item");

    assertThat(recallRequest.getJson().getString("requestType"), is(RequestType.RECALL.getValue()));
    assertThat(requestItem.getString("status"), is ( ItemStatus.IN_TRANSIT.getValue()));
    assertThat(recallRequest.getJson().getString("status"), is (RequestStatus.OPEN_NOT_YET_FILLED.getValue()));
  }

  @Test
  public void cannotCreateRecallRequestWhenItemIsAvailable()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException{

    final IndividualResource availableItem = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestPickupServicePoint = servicePointsFixture.cd1();

    final Response recallResponse = requestsClient.attemptCreate(new RequestBuilder()
      .recall()
      .forItem(availableItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.james()));

    assertThat(recallResponse, hasStatus(HTTP_VALIDATION_ERROR));
    JsonArray errors = recallResponse.getJson().getJsonArray("errors");
    assertThat(errors.getJsonObject(0).getString("message").toLowerCase(), is("item is "+ ItemStatus.AVAILABLE.toString().toLowerCase()));
  }

  @Test
  public void cannotCreateRecallRequestWhenItemIsMissing()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException{

    final IndividualResource missingItem = setupMissingItem(itemsFixture);

    //create a Recall request
    final Response holdRequest = requestsClient.attemptCreate(new RequestBuilder()
      .recall()
      .forItem(missingItem)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.jessica()));

    assertThat(holdRequest, hasStatus(HTTP_VALIDATION_ERROR));
    JsonArray errors = holdRequest.getJson().getJsonArray("errors");
    assertThat(errors.getJsonObject(0).getString("message").toLowerCase(), is("item is "+ ItemStatus.MISSING.toString().toLowerCase()));
  }

  @Test
  public void cannotCreateRecallRequestWhenItemIsPaged()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException{

    final IndividualResource requestPickupServicePoint = servicePointsFixture.cd1();
    final IndividualResource pagedItem = setupPagedItem(requestPickupServicePoint, itemsFixture, requestsClient, usersFixture);

    final Response recallResponse = requestsClient.attemptCreate(new RequestBuilder()
      .recall()
      .forItem(pagedItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.jessica()));

    assertThat(recallResponse, hasStatus(HTTP_VALIDATION_ERROR));
    JsonArray errors = recallResponse.getJson().getJsonArray("errors");
    assertThat(errors.getJsonObject(0).getString("message").toLowerCase(), is("item is "+ ItemStatus.PAGED.toString().toLowerCase()));
  }

  @Test
  public void canCreateHoldRequestWhenItemIsCheckedOut()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException{

    final IndividualResource checkedOutItem = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestPickupServicePoint = servicePointsFixture.cd1();

    loansFixture.checkOut(checkedOutItem, usersFixture.jessica());

    final IndividualResource holdRequest = requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(checkedOutItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.james()));

    JsonObject requestedItem = holdRequest.getJson().getJsonObject("item");

    assertThat(holdRequest.getJson().getString("requestType"), is(RequestType.HOLD.getValue()));
    assertThat(requestedItem.getString("status"), is ( ItemStatus.CHECKED_OUT.getValue()));
    assertThat(holdRequest.getJson().getString("status"), is (RequestStatus.OPEN_NOT_YET_FILLED.getValue()));
  }

  @Test
  public void canCreateHoldRequestWhenItemIsAwaitingPickup()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException{

    //Setting up an item with AWAITING_PICKUP status
    final IndividualResource servicePoint = servicePointsFixture.cd1();
    final IndividualResource awaitingPickupItem = setupItemAwaitingPickup(servicePoint, requestsClient, itemsClient,
                                                                        itemsFixture, usersFixture, loansFixture);
    // create a hold request
    final IndividualResource holdRequest = requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(awaitingPickupItem)
      .withPickupServicePointId(servicePoint.getId())
      .by( usersFixture.steve()));

    assertThat(holdRequest.getJson().getString("requestType"), is(RequestType.HOLD.getValue()));
    assertThat(holdRequest.getJson().getJsonObject("item").getString("status"), is(ItemStatus.AWAITING_PICKUP.getValue()));
    assertThat(holdRequest.getJson().getString("status"), is (RequestStatus.OPEN_NOT_YET_FILLED.getValue()));
  }

  @Test
  public void canCreateHoldRequestWhenItemIsInTransit()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException{

    final IndividualResource requestPickupServicePoint = servicePointsFixture.cd1();

    final IndividualResource intransitItem = setupItemInTransit(requestPickupServicePoint, servicePointsFixture.cd2(),
      itemsFixture, requestsClient,
      usersFixture, requestsFixture, loansFixture);

    //create a Hold request
    final IndividualResource holdRequest = requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(intransitItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.jessica()));

    JsonObject requestedItem = holdRequest.getJson().getJsonObject("item");

    assertThat(holdRequest.getJson().getString("requestType"), is(RequestType.HOLD.getValue()));
    assertThat(requestedItem.getString("status"), is ( ItemStatus.IN_TRANSIT.getValue()));
    assertThat(holdRequest.getJson().getString("status"), is (RequestStatus.OPEN_NOT_YET_FILLED.getValue()));
  }

  @Test
  public void canCreateHoldRequestWhenItemIsMissing()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException{

    final IndividualResource missingItem = setupMissingItem(itemsFixture);

    //create a Hold request
    final IndividualResource holdRequest = requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(missingItem)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.jessica()));

    JsonObject requestedItem = holdRequest.getJson().getJsonObject("item");

    assertThat(holdRequest.getJson().getString("requestType"), is(RequestType.HOLD.getValue()));
    assertThat(requestedItem.getString("status"), is ( ItemStatus.MISSING.getValue()));
    assertThat(holdRequest.getJson().getString("status"), is (RequestStatus.OPEN_NOT_YET_FILLED.getValue()));
  }
  @Test
  public void canCreateHoldRequestWhenItemIsPaged()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException{

    final IndividualResource requestPickupServicePoint = servicePointsFixture.cd1();
    final IndividualResource pagedItem = setupPagedItem(requestPickupServicePoint, itemsFixture, requestsClient, usersFixture);

    final IndividualResource holdRequest = requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(pagedItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by( usersFixture.steve()));

    assertThat(holdRequest.getJson().getString("requestType"), is(RequestType.HOLD.getValue()));
    assertThat(holdRequest.getJson().getJsonObject("item").getString("status"), is(ItemStatus.PAGED.getValue()));
    assertThat(holdRequest.getJson().getString("status"), is (RequestStatus.OPEN_NOT_YET_FILLED.getValue()));
  }

  @Test
  public void cannotCreateHoldRequestWhenItemIsAvailable()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException{

    final IndividualResource availableItem = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestPickupServicePoint = servicePointsFixture.cd1();

    final Response recallResponse = requestsClient.attemptCreate(new RequestBuilder()
      .hold()
      .forItem(availableItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.james()));

    assertThat(recallResponse, hasStatus(HTTP_VALIDATION_ERROR));
    JsonArray errors = recallResponse.getJson().getJsonArray("errors");
    assertThat(errors.getJsonObject(0).getString("message").toLowerCase(), is("item is "+ ItemStatus.AVAILABLE.toString().toLowerCase()));
  }

  public static IndividualResource setupPagedItem(IndividualResource requestPickupServicePoint, ItemsFixture itemsFixture,
                                                  ResourceClient requestClient, UsersFixture usersFixture)
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    final IndividualResource pagedRequest = requestClient.create(new RequestBuilder()
      .page()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.james()));

    JsonObject requestedItem = pagedRequest.getJson().getJsonObject("item");
    assertThat(requestedItem.getString("status"), is ( ItemStatus.PAGED.getValue()));

    return smallAngryPlanet;
  }

  public static IndividualResource setupItemAwaitingPickup(IndividualResource requestPickupServicePoint, ResourceClient requestsClient, ResourceClient itemsClient,
                                                           ItemsFixture itemsFixture, UsersFixture usersFixture, LoansFixture loansFixture)
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException{

    //Setting up an item with AWAITING_PICKUP status
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    requestsClient.create(new RequestBuilder()
      .page()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.james()));

    loansFixture.checkInByBarcode(smallAngryPlanet,DateTime.now(DateTimeZone.UTC),requestPickupServicePoint.getId());

    Response pagedRequestRecord = itemsClient.getById(smallAngryPlanet.getId());
    assertThat(pagedRequestRecord.getJson().getJsonObject("status").getString("name"), is(ItemStatus.AWAITING_PICKUP.getValue()));

    return smallAngryPlanet;
  }

  public static IndividualResource setupItemInTransit(IndividualResource requestPickupServicePoint, IndividualResource pickupServicePoint,
                                                      ItemsFixture itemsFixture, ResourceClient requestsClient,
                                                      UsersFixture usersFixture, RequestsFixture requestsFixture, LoansFixture loansFixture)
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    //In order to get the item into the IN_TRANSIT state, for now we need to go the round-about route of delivering it to the unintended pickup location first
    //then check it in at the intended pickup location.
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    final IndividualResource firstRequest = requestsClient.create(new RequestBuilder()
      .page()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.james()));

    JsonObject requestItem = firstRequest.getJson().getJsonObject("item");
    assertThat(requestItem.getString("status"), is(ItemStatus.PAGED.getValue()));
    assertThat(firstRequest.getJson().getString("status"), is(RequestStatus.OPEN_NOT_YET_FILLED.getValue()));

    //check it it at the "wrong" or unintended pickup location
    loansFixture.checkInByBarcode(smallAngryPlanet, DateTime.now(DateTimeZone.UTC), pickupServicePoint.getId());

    MultipleRecords<JsonObject> requests = requestsFixture.getQueueFor(smallAngryPlanet);
    JsonObject pagedRequestRecord = requests.getRecords().iterator().next();

    assertThat(pagedRequestRecord.getJsonObject("item").getString("status"), is(ItemStatus.IN_TRANSIT.getValue()));
    assertThat(pagedRequestRecord.getString("status"), is(RequestStatus.OPEN_IN_TRANSIT.getValue()));

    return smallAngryPlanet;
  }


  public static IndividualResource setupMissingItem(ItemsFixture itemsFixture)
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException{

    //There is no workflow to get an item into the MISSING status. For now assign the MISSING status to the item directly.
    IndividualResource missingItem = itemsFixture.basedUponSmallAngryPlanet(ItemBuilder::missing);
    assertThat( missingItem.getResponse().getJson().getJsonObject("status").getString("name"), is (ItemStatus.MISSING.getValue()));

    return missingItem;
  }
}
