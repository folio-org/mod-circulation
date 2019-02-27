package api.requests;

import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.UUIDMatcher.is;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasUUIDParameter;
import static org.folio.HttpStatus.HTTP_VALIDATION_ERROR;
import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import api.support.APITests;
import api.support.builders.Address;
import api.support.builders.RequestBuilder;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Test;

public class RequestsAPIUpdatingTests extends APITests {
  @Test
  public void canReplaceAnExistingRequest()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();

    loansFixture.checkOutByBarcode(temeraire);

    final IndividualResource steve = usersFixture.steve();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    final IndividualResource exampleServicePoint = servicePointsFixture.cd1();

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
      .recall()
      .withRequestDate(requestDate)
      .forItem(temeraire)
      .by(steve)
      .fulfilToHoldShelf()
      .withPickupServicePointId(exampleServicePoint.getId())
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31)));

    final IndividualResource charlotte = usersFixture.charlotte();

    requestsClient.replace(createdRequest.getId(),
      RequestBuilder.from(createdRequest)
        .hold()
        .by(charlotte)
        .withTags(new RequestBuilder.Tags(Arrays.asList("new", "important")))
    );

    Response getResponse = requestsClient.getById(createdRequest.getId());

    assertThat(String.format("Failed to get request: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject representation = getResponse.getJson();

    assertThat(representation.getString("id"), is(createdRequest.getId()));
    assertThat(representation.getString("requestType"), is("Hold"));
    assertThat(representation.getString("requestDate"), isEquivalentTo(requestDate));
    assertThat(representation.getString("itemId"), is(temeraire.getId()));
    assertThat(representation.getString("requesterId"), is(charlotte.getId()));
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
      is("232142443432"));

    assertThat("has information taken from requesting user",
      representation.containsKey("requester"), is(true));

    assertThat("last name is taken from requesting user",
      representation.getJsonObject("requester").getString("lastName"),
      is("Broadwell"));

    assertThat("first name is taken from requesting user",
      representation.getJsonObject("requester").getString("firstName"),
      is("Charlotte"));

    assertThat("middle name is not taken from requesting user",
      representation.getJsonObject("requester").containsKey("middleName"),
      is(false));

    assertThat("barcode is taken from requesting user",
      representation.getJsonObject("requester").getString("barcode"),
      is("6430705932"));

    assertThat(representation.containsKey("tags"), is(true));
    final JsonObject tagsRepresentation = representation.getJsonObject("tags");

    assertThat(tagsRepresentation.containsKey("tagList"), is(true));
    assertThat(tagsRepresentation.getJsonArray("tagList"), contains("new", "important"));
  }

  //TODO: Check does not have pickup service point any more
  @Test
  public void canReplaceAnExistingRequestWithDeliveryAddress()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();

    loansFixture.checkOutByBarcode(temeraire);

    final IndividualResource workAddressType = addressTypesFixture.work();

    final IndividualResource charlotte = usersFixture.charlotte(userBuilder -> userBuilder
      .withAddress(
        new Address(workAddressType.getId(),
          "Fake first address line",
          "Fake second address line",
          "Fake city",
          "Fake region",
          "Fake postal code",
          "Fake country code")));

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    final IndividualResource exampleServicePoint = servicePointsFixture.cd1();

    IndividualResource createdRequest = requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(temeraire)
      .by(charlotte)
      .withRequestDate(requestDate)
      .fulfilToHoldShelf(exampleServicePoint.getId()));

    requestsClient.replace(createdRequest.getId(),
      RequestBuilder.from(createdRequest)
        .hold()
        .deliverToAddress(workAddressType.getId()));

    Response getResponse = requestsClient.getById(createdRequest.getId());

    assertThat(String.format("Failed to get request: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject representation = getResponse.getJson();

    assertThat(representation.getString("id"), is(createdRequest.getId()));

    assertThat("has information taken from requesting user",
      representation.containsKey("requester"), is(true));

    assertThat("Request should have a delivery address",
      representation.containsKey("deliveryAddress"), is(true));

    final JsonObject deliveryAddress = representation.getJsonObject("deliveryAddress");

    assertThat(deliveryAddress.getString("addressTypeId"), is(workAddressType.getId()));
    assertThat(deliveryAddress.getString("addressLine1"), is("Fake first address line"));
    assertThat(deliveryAddress.getString("addressLine2"), is("Fake second address line"));
    assertThat(deliveryAddress.getString("city"), is("Fake city"));
    assertThat(deliveryAddress.getString("region"), is("Fake region"));
    assertThat(deliveryAddress.getString("postalCode"), is("Fake postal code"));
    assertThat(deliveryAddress.getString("countryId"), is("Fake country code"));
  }

  @Test
  public void replacingAnExistingRequestRemovesItemInformationWhenItemDoesNotExist()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource nod = itemsFixture.basedUponNod();

    loansFixture.checkOutByBarcode(nod);

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .forItem(nod)
        .by(usersFixture.steve()));

    itemsClient.delete(nod.getId());

    requestsClient.replace(createdRequest.getId(),
      RequestBuilder.from(createdRequest)
        .hold());

    Response getResponse = requestsClient.getById(createdRequest.getId());

    assertThat(String.format("Failed to get request: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject representation = getResponse.getJson();

    assertThat(representation.getString("itemId"), is(nod.getId()));

    assertThat("has no item information when item no longer exists",
      representation.containsKey("item"), is(false));
  }

  @Test
  public void replacingAnExistingRequestRemovesRequesterInformationWhenUserDoesNotExist()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource nod = itemsFixture.basedUponNod();

    loansFixture.checkOutByBarcode(nod);

    IndividualResource requester = usersFixture.steve();

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .forItem(nod)
        .by(requester));

    usersFixture.remove(requester);

    requestsClient.replace(createdRequest.getId(), RequestBuilder.from(createdRequest));

    final IndividualResource fetchedRequest = requestsClient.get(createdRequest.getId());

    JsonObject representation = fetchedRequest.getJson();

    assertThat(representation.getString("requesterId"), is(requester.getId()));

    assertThat("has no requesting user information taken when user no longer exists",
      representation.containsKey("requester"), is(false));
  }

  @Test
  public void replacingAnExistingRequestRemovesRequesterBarcodeWhenNonePresent()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource nod = itemsFixture.basedUponNod();

    loansFixture.checkOutByBarcode(nod);

    final IndividualResource steve = usersFixture.steve();

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .forItem(nod)
        .by(steve));

    final JsonObject userToReplace = steve.copyJson();

    userToReplace.remove("barcode");

    usersClient.replace(steve.getId(), userToReplace);

    requestsClient.replace(createdRequest.getId(), RequestBuilder.from(createdRequest));

    final IndividualResource fetchedRequest = requestsClient.get(createdRequest.getId());

    JsonObject representation = fetchedRequest.getJson();

    assertThat(representation.getString("requesterId"), is(steve.getId()));

    assertThat("has information taken from requesting user",
      representation.containsKey("requester"), is(true));

    assertThat("last name is taken from requesting user",
      representation.getJsonObject("requester").getString("lastName"),
      is("Jones"));

    assertThat("first name is taken from requesting user",
      representation.getJsonObject("requester").getString("firstName"),
      is("Steven"));

    assertThat("barcode is not present when requesting user does not have one",
      representation.getJsonObject("requester").containsKey("barcode"),
      is(false));
  }

  @Test
  public void replacingAnExistingRequestIncludesRequesterMiddleNameWhenPresent()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource nod = itemsFixture.basedUponNod();

    loansFixture.checkOutByBarcode(nod);

    final IndividualResource steve = usersFixture.steve();

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .forItem(nod)
        .by(steve));

    final JsonObject userToReplace = steve.copyJson();

    final JsonObject personalDetails = userToReplace.getJsonObject("personal");

    write(personalDetails, "middleName", "Carter");

    usersClient.replace(steve.getId(), userToReplace);

    requestsClient.replace(createdRequest.getId(), RequestBuilder.from(createdRequest));

    final IndividualResource fetchedRequest = requestsClient.get(createdRequest.getId());

    JsonObject representation = fetchedRequest.getJson();

    assertThat(representation.getString("requesterId"), is(steve.getId()));

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
      is("Carter"));
  }

  @Test
  public void replacingAnExistingRequestRemovesItemBarcodeWhenNonePresent()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();

    loansFixture.checkOutByBarcode(temeraire);

    IndividualResource createdRequest = requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(temeraire)
      .by(usersFixture.steve()));

    final JsonObject itemToReplace = temeraire.copyJson();

    itemToReplace.remove("barcode");

    itemsClient.replace(temeraire.getId(), itemToReplace);

    requestsClient.replace(createdRequest.getId(), createdRequest.copyJson());

    IndividualResource fetchedRequest = requestsClient.get(createdRequest.getId());

    JsonObject representation = fetchedRequest.getJson();

    assertThat(representation.getString("itemId"), is(temeraire.getId()));

    assertThat("has information taken from item",
      representation.containsKey("item"), is(true));

    assertThat("title is taken from item",
      representation.getJsonObject("item").getString("title"),
      is("Temeraire"));

    assertThat("barcode is not taken from item",
      representation.getJsonObject("item").containsKey("barcode"),
      is(false));
  }

  @Test
  public void cannotReplaceAnExistingRequestWithServicePointThatIsNotForPickup()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();

    loansFixture.checkOutByBarcode(temeraire);

    final IndividualResource exampleServicePoint = servicePointsFixture.cd1();

    IndividualResource createdRequest = requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(temeraire)
      .by(usersFixture.steve())
      .fulfilToHoldShelf(exampleServicePoint.getId()));

     UUID badServicePointId = servicePointsFixture.cd3().getId();

    final Response putResponse = requestsClient.attemptReplace(createdRequest.getId(),
      RequestBuilder.from(createdRequest)
        .withPickupServicePointId(badServicePointId));

    assertThat(putResponse.getJson(), hasErrorWith(allOf(
     hasMessage("Service point is not a pickup location"),
     hasUUIDParameter("pickupServicePointId", badServicePointId))));
  }

  @Test
  public void cannotReplaceAnExistingRequestWithUnknownPickupLocation()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();

    loansFixture.checkOutByBarcode(temeraire);

    final IndividualResource exampleServicePoint = servicePointsFixture.cd1();

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .forItem(temeraire)
        .by(usersFixture.james())
        .fulfilToHoldShelf()
        .withPickupServicePointId(exampleServicePoint.getId()));

    UUID badServicePointId = UUID.randomUUID();

    final Response putResponse = requestsClient.attemptReplace(createdRequest.getId(),
      RequestBuilder.from(createdRequest)
        .withPickupServicePointId(badServicePointId));

    assertThat(putResponse, hasStatus(HTTP_VALIDATION_ERROR));

    assertThat(putResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Pickup service point does not exist"),
      hasUUIDParameter("pickupServicePointId", badServicePointId))));
  }
}
