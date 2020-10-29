package api.requests;

import static api.support.PubsubPublisherTestUtils.assertThatPublishedLogRecordEventsAreValid;
import static api.support.PubsubPublisherTestUtils.assertThatPublishedNoticeLogRecordEventsCountIsEqualTo;
import static api.support.builders.RequestBuilder.OPEN_NOT_YET_FILLED;
import static api.support.fixtures.AutomatedPatronBlocksFixture.MAX_NUMBER_OF_ITEMS_CHARGED_OUT_MESSAGE;
import static api.support.fixtures.AutomatedPatronBlocksFixture.MAX_OUTSTANDING_FEE_FINE_BALANCE_MESSAGE;
import static api.support.http.CqlQuery.exactMatch;
import static api.support.http.Limit.limit;
import static api.support.http.Offset.noOffset;
import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.UUIDMatcher.is;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static api.support.matchers.ValidationErrorMatchers.hasUUIDParameter;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.function.Function.identity;
import static org.awaitility.Awaitility.await;
import static org.folio.HttpStatus.HTTP_BAD_REQUEST;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.circulation.domain.ItemStatus.PAGED;
import static org.folio.circulation.domain.RequestType.RECALL;
import static org.folio.circulation.domain.representations.ItemProperties.CALL_NUMBER_COMPONENTS;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.policy.DueDateManagement;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.http.client.Response;
import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Test;
import org.junit.runner.RunWith;

import api.support.APITests;
import api.support.builders.Address;
import api.support.builders.HoldingBuilder;
import api.support.builders.ItemBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.builders.RequestBuilder;
import api.support.builders.UserBuilder;
import api.support.builders.UserManualBlockBuilder;
import api.support.fixtures.CheckInFixture;
import api.support.fixtures.ItemExamples;
import api.support.fixtures.ItemsFixture;
import api.support.fixtures.RequestsFixture;
import api.support.fixtures.TemplateContextMatchers;
import api.support.fixtures.UsersFixture;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class
RequestsAPICreationTests extends APITests {
  private static final String PAGING_REQUEST_EVENT = "Paging request";
  private static final String HOLD_REQUEST_EVENT = "Hold request";
  private static final String RECALL_REQUEST_EVENT = "Recall request";
  private static final String ITEM_RECALLED = "Item recalled";

  @Override
  public void afterEach() {
    super.afterEach();
    mockClockManagerToReturnDefaultDateTime();
  }

  @Test
  public void canCreateARequest() {
    UUID id = UUID.randomUUID();
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    UUID isbnIdentifierId = identifierTypesFixture.isbn().getId();
    String isbnValue = "9780866989732";

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet(
      identity(),
      instanceBuilder -> instanceBuilder.addIdentifier(isbnIdentifierId, isbnValue),
      itemsFixture.addCallNumberStringComponents());

    checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

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

    JsonObject requestItem = representation.getJsonObject("item");
    assertThat("title is taken from item",
      requestItem.getString("title"),
      is("The Long Way to a Small, Angry Planet"));

    assertThat("barcode is taken from item",
      requestItem.getString("barcode"),
      is("036000291452"));

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
      is("Jacob"));

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

    assertTrue(requestItem.containsKey(CALL_NUMBER_COMPONENTS));

    JsonObject callNumberComponents = requestItem
      .getJsonObject(CALL_NUMBER_COMPONENTS);

    assertThat(callNumberComponents.getString("callNumber"), is("itCn"));
    assertThat(callNumberComponents.getString("prefix"), is("itCnPrefix"));
    assertThat(callNumberComponents.getString("suffix"), is("itCnSuffix"));

    assertThat(requestItem.getString("enumeration"), is("enumeration1"));
    assertThat(requestItem.getString("chronology"), is("chronology"));
    assertThat(requestItem.getString("volume"), is("vol.1"));

    JsonArray identifiers = requestItem.getJsonArray("identifiers");
    assertThat(identifiers, notNullValue());
    assertThat(identifiers.size(), is(1));
    assertThat(identifiers.getJsonObject(0).getString("identifierTypeId"),
      is(isbnIdentifierId.toString()));
    assertThat(identifiers.getJsonObject(0).getString("value"),
      is(isbnValue));
  }

  @Test
  public void canCreateARequestAtSpecificLocation() {
    UUID id = UUID.randomUUID();

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();

    checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

    IndividualResource requester = usersFixture.steve();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    Response response = requestsClient.attemptCreateAtSpecificLocation(new RequestBuilder()
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

    assertThat("middle name is taken from requesting user",
      representation.getJsonObject("requester").getString("middleName"),
      is("Jacob"));

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
  public void cannotCreateRequestForUnknownItem() {
    UUID itemId = UUID.randomUUID();
    UUID patronId = usersFixture.charlotte().getId();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    //Check RECALL -- should give the same response when placing other types of request.
    Response postResponse = requestsClient.attemptCreate(new RequestBuilder()
      .recall()
      .withItemId(itemId)
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(patronId));

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(postResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Item does not exist"))));
  }

  @Test
  public void cannotCreateRequestWithNoItemReference() {
    UUID patronId = usersFixture.charlotte().getId();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    Response postResponse = requestsClient.attemptCreate(new RequestBuilder()
      .recall()
      .withNoItemId()
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(patronId));

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(postResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Cannot create a request with no item ID"))));
  }

  @Test
  public void cannotCreateRecallRequestWhenItemIsNotCheckedOut() {
    UUID itemId = itemsFixture.basedUponSmallAngryPlanet(
      ItemBuilder::available)
      .getId();

    Response postResponse = requestsClient.attemptCreate(new RequestBuilder()
      .recall()
      .withItemId(itemId)
      .withRequesterId(usersFixture.charlotte().getId()));

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateHoldRequestWhenItemIsNotCheckedOut() {
    UUID itemId = itemsFixture.basedUponSmallAngryPlanet(
      ItemBuilder::available)
      .getId();

    Response postResponse = requestsClient.attemptCreate(new RequestBuilder()
      .hold()
      .withItemId(itemId)
      .withRequesterId(usersFixture.charlotte().getId()));

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateRequestItemAlreadyCheckedOutToRequester() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    //Check RECALL -- should give the same response when placing other types of request.
    Response postResponse = requestsClient.attemptCreate(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(rebecca.getId()));

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(postResponse.getJson(), hasErrorWith(allOf(
      hasMessage("This requester currently has this item on loan."),
      hasUUIDParameter("itemId", smallAngryPlanet.getId()),
      hasUUIDParameter("userId", rebecca.getId()))));
  }

  @Test
  @Parameters({
    "Open - Not yet filled",
    "Open - Awaiting pickup",
    "Open - In transit",
    "Closed - Filled"
  })
  public void canCreateARequestWithValidStatus(String status) {
    final ItemResource smallAngryPlanet =
      itemsFixture.basedUponSmallAngryPlanet(itemBuilder -> itemBuilder
        .withBarcode("036000291452"));
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    UUID itemId = smallAngryPlanet.getId();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet);

    UUID requesterId = usersFixture.steve().getId();

    final IndividualResource request = requestsFixture.place(new RequestBuilder()
      .recall().fulfilToHoldShelf()
      .withItemId(itemId)
      .withRequesterId(requesterId)
      .withPickupServicePointId(pickupServicePointId)
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
  public void cannotCreateARequestWithInvalidStatus(String status) {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    final IndividualResource jessica = usersFixture.jessica();
    final IndividualResource steve = usersFixture.steve();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    Response response = requestsClient.attemptCreate(
      new RequestBuilder()
        .recall().fulfilToHoldShelf()
        .forItem(smallAngryPlanet)
        .by(steve)
        .withStatus(status));

    assertThat(String.format("Should not create request: %s", response.getBody()),
      response, hasStatus(HTTP_BAD_REQUEST));

    assertThat(response.getBody(),
      is("Request status must be one of the following: \"Open - Not yet filled\", " +
        "\"Open - Awaiting pickup\", \"Open - In transit\", \"Open - Awaiting delivery\", " +
        "\"Closed - Filled\", \"Closed - Cancelled\", \"Closed - Unfilled\", \"Closed - Pickup expired\""));
  }

  //TODO: Replace with validation error message
  @Test
  @Parameters({
    "Non-existent status",
    ""
  })
  public void cannotCreateARequestAtASpecificLocationWithInvalidStatus(String status) {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    final IndividualResource jessica = usersFixture.jessica();
    final IndividualResource steve = usersFixture.steve();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    Response response = requestsClient.attemptCreateAtSpecificLocation(
      new RequestBuilder()
        .recall().fulfilToHoldShelf()
        .forItem(smallAngryPlanet)
        .by(steve)
        .withStatus(status));

    assertThat(String.format("Should not create request: %s", response.getBody()),
      response, hasStatus(HTTP_BAD_REQUEST));

    assertThat(response.getBody(),
      containsString("Request status must be one of the following:"));
  }

  @Test
  public void canCreateARequestToBeFulfilledByDeliveryToAnAddress() {
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

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

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
  public void requestStatusDefaultsToOpen() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();
    final IndividualResource steve = usersFixture.steve();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    IndividualResource createdRequest = requestsFixture.place(new RequestBuilder()
      .recall().fulfilToHoldShelf()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(steve)
      .withNoStatus());

    JsonObject representation = createdRequest.getJson();

    assertThat(representation.getString("status"), is(OPEN_NOT_YET_FILLED));
  }

  @Test
  public void cannotCreateRequestWithUserBelongingToNoPatronGroup() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource noUserGroupBob = usersFixture.noUserGroupBob();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    final Response recallResponse = requestsClient.attemptCreate(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .withRequestDate(requestDate)
      .by(noUserGroupBob));

    assertThat(recallResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));

    assertThat(recallResponse.getJson(), hasErrorWith(allOf(
      hasMessage("A valid patron group is required. PatronGroup ID is null"))));
  }

  @Test
  public void cannotCreateRequestWithoutValidUser() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, steve);

    UUID nonExistentRequesterId = UUID.randomUUID();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    final Response recallResponse = requestsClient.attemptCreate(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .withRequestDate(requestDate)
      .withRequesterId(nonExistentRequesterId));

    assertThat(recallResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));

    assertThat(recallResponse.getJson(), hasErrorWith(allOf(
      hasMessage("A valid user and patron group are required. User is null"))));
  }

  @Test
  public void cannotCreateRequestWithAnInactiveUser() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource inactiveCharlotte = usersFixture.charlotte(UserBuilder::inactive);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, steve);

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    final Response recallResponse = requestsClient.attemptCreate(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .withRequestDate(requestDate)
      .withRequesterId(inactiveCharlotte.getId()));

    assertThat(recallResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));

    assertThat(recallResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Inactive users cannot make requests"),
      hasUUIDParameter("requesterId", inactiveCharlotte.getId()),
      hasUUIDParameter("itemId", smallAngryPlanet.getId()))));
  }

  @Test
  public void cannotCreateRequestAtSpecificLocationWithAnInactiveUser() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource inactiveCharlotte = usersFixture.charlotte(UserBuilder::inactive);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, steve);

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    final UUID requestId = UUID.randomUUID();

    final Response recallResponse = requestsClient.attemptCreateAtSpecificLocation(
      new RequestBuilder()
      .withId(requestId)
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .withRequestDate(requestDate)
      .withRequesterId(inactiveCharlotte.getId()));

    assertThat(recallResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));

    assertThat(recallResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Inactive users cannot make requests"),
      hasUUIDParameter("requesterId", inactiveCharlotte.getId()),
      hasUUIDParameter("itemId", smallAngryPlanet.getId()))));
  }

  @Test
  public void canCreateARequestWithRequesterWithMiddleName() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    final IndividualResource steve = usersFixture.steve(
      b -> b.withName("Jones", "Steven", "Anthony"));

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    IndividualResource createdRequest = requestsFixture.place(new RequestBuilder()
      .recall()
      .withRequestDate(requestDate)
      .withPickupServicePointId(pickupServicePointId)
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
  public void canCreateARequestWithRequesterWithNoBarcode() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource james = usersFixture.james();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    final IndividualResource steveWithNoBarcode = usersFixture.steve(
      UserBuilder::withNoBarcode);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    IndividualResource createdRequest = requestsFixture.place(new RequestBuilder()
      .recall()
      .withRequestDate(requestDate)
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
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
  public void canCreateARequestForItemWithNoBarcode() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      ItemBuilder::withNoBarcode);

    final IndividualResource rebecca = usersFixture.rebecca();
    final IndividualResource charlotte = usersFixture.charlotte();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.createLoan(smallAngryPlanet, rebecca);

    IndividualResource createdRequest = requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
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
  public void creatingARequestIgnoresReadOnlyInformationProvidedByClient() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();
    final IndividualResource steve = usersFixture.steve();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    UUID itemId = smallAngryPlanet.getId();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    JsonObject request = new RequestBuilder()
      .recall()
      .withRequestDate(requestDate)
      .withItemId(itemId)
      .withPickupServicePointId(pickupServicePointId)
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
  public void cannotCreateARequestWithoutAPickupLocationServicePoint() {
    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();

    checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

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
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31)));

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));

    assertThat(postResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Hold Shelf Fulfillment Requests require a Pickup Service Point"))));
  }

  @Test
  public void cannotCreateARequestWithANonPickupLocationServicePoint() {
    UUID pickupServicePointId = servicePointsFixture.cd3().getId();

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();

    checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

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

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));

    assertThat(postResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Service point is not a pickup location"),
      hasUUIDParameter("pickupServicePointId", pickupServicePointId))));
  }

  @Test
  public void cannotCreateARequestWithUnknownPickupLocationServicePoint() {
    UUID pickupServicePointId = UUID.randomUUID();

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();

    checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

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

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));

    assertThat(postResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Pickup service point does not exist"),
      hasUUIDParameter("pickupServicePointId", pickupServicePointId))));
  }

  @Test
  public void canCreatePagedRequestWhenItemStatusIsAvailable() {
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
  public void cannotCreatePagedRequestWhenItemStatusIsCheckedOut() {
    //Set up the item's initial status to be CHECKED OUT
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.jessica());

    //Attempt to create a page request on it.
    final IndividualResource servicePoint = servicePointsFixture.cd1();
    final Response pagedRequest = requestsClient.attemptCreate(new RequestBuilder()
      .page()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePoint.getId())
      .by(usersFixture.james()));

    assertThat(pagedRequest.getJson(), hasErrorWith(allOf(
      hasMessage("Page requests are not allowed for this patron and item combination"),
      hasParameter("requestType", "Page"))));
  }

  @Test
  public void cannotCreatePagedRequestWhenItemStatusIsAwaitingPickup() {
    //Setting up an item with AWAITING_PICKUP status
    final IndividualResource servicePoint = servicePointsFixture.cd1();

    final IndividualResource awaitingPickupItem = setupItemAwaitingPickup(servicePoint, requestsClient, itemsClient,
      itemsFixture, usersFixture, checkInFixture);
    //attempt to place a PAGED request
    final Response pagedRequest2 = requestsClient.attemptCreate(new RequestBuilder()
      .page()
      .forItem(awaitingPickupItem)
      .withPickupServicePointId(servicePoint.getId())
      .by(usersFixture.jessica()));

    assertThat(pagedRequest2.getJson(), hasErrorWith(allOf(
      hasMessage("Page requests are not allowed for this patron and item combination"),
      hasParameter("requestType", "Page"))));
  }

  @Test
  public void cannotCreatePagedRequestWhenItemStatusIsPaged() {
    //Set up the item's initial status to be PAGED
    final IndividualResource servicePoint = servicePointsFixture.cd1();
    final IndividualResource pagedItem = setupPagedItem(servicePoint, itemsFixture, requestsClient, usersFixture);

    //Attempt to create a page request on it.
    final Response pagedRequest2 = requestsClient.attemptCreate(new RequestBuilder()
      .page()
      .forItem(pagedItem)
      .withPickupServicePointId(servicePoint.getId())
      .by(usersFixture.jessica()));

    assertThat(pagedRequest2.getJson(), hasErrorWith(allOf(
      hasMessage("Page requests are not allowed for this patron and item combination"),
      hasParameter("requestType", "Page"))));
  }

  @Test
  public void cannotCreatePagedRequestWhenItemStatusIsInTransit() {
    final IndividualResource requestPickupServicePoint = servicePointsFixture.cd1();

    final IndividualResource intransitItem = setupItemInTransit(requestPickupServicePoint, servicePointsFixture.cd2(),
      itemsFixture, requestsClient,
      usersFixture, requestsFixture, checkInFixture);

    //attempt to create a Paged request for this IN_TRANSIT item
    final Response pagedRequest2 = requestsClient.attemptCreate(new RequestBuilder()
      .page()
      .forItem(intransitItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.jessica()));

    assertThat(pagedRequest2.getJson(), hasErrorWith(allOf(
      hasMessage("Page requests are not allowed for this patron and item combination"),
      hasParameter("requestType", "Page"))));
  }

  @Test
  public void canCreateRecallRequestWhenItemIsCheckedOut() {
    final IndividualResource checkedOutItem = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestPickupServicePoint = servicePointsFixture.cd1();

    checkOutFixture.checkOutByBarcode(checkedOutItem, usersFixture.jessica());

    final IndividualResource recallRequest = requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(checkedOutItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.james()));

    JsonObject requestedItem = recallRequest.getJson().getJsonObject("item");
    assertThat(recallRequest.getJson().getString("requestType"), is(RequestType.RECALL.getValue()));
    assertThat(requestedItem.getString("status"), is(ItemStatus.CHECKED_OUT.getValue()));
    assertThat(recallRequest.getJson().getString("status"), is(RequestStatus.OPEN_NOT_YET_FILLED.getValue()));
  }

  @Test
  public void canCreateRecallRequestWhenItemIsAwaitingPickup() {
    //Setting up an item with AWAITING_PICKUP status
    final IndividualResource servicePoint = servicePointsFixture.cd1();
    final IndividualResource awaitingPickupItem = setupItemAwaitingPickup(servicePoint, requestsClient, itemsClient,
      itemsFixture, usersFixture, checkInFixture);

    // create a recall request
    final IndividualResource recallRequest = requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(awaitingPickupItem)
      .withPickupServicePointId(servicePoint.getId())
      .by(usersFixture.steve()));

    assertThat(recallRequest.getJson().getString("requestType"), is(RequestType.RECALL.getValue()));
    assertThat(recallRequest.getJson().getJsonObject("item").getString("status"), is(ItemStatus.AWAITING_PICKUP.getValue()));
    assertThat(recallRequest.getJson().getString("status"), is(RequestStatus.OPEN_NOT_YET_FILLED.getValue()));
  }

  @Test
  public void canCreateRecallRequestWhenItemIsInTransit() {
    final IndividualResource requestPickupServicePoint = servicePointsFixture.cd1();

    final IndividualResource intransitItem = setupItemInTransit(requestPickupServicePoint, servicePointsFixture.cd2(),
      itemsFixture, requestsClient,
      usersFixture, requestsFixture, checkInFixture);
    //create a Recall request
    final IndividualResource recallRequest = requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(intransitItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.jessica()));

    JsonObject requestItem = recallRequest.getJson().getJsonObject("item");

    assertThat(recallRequest.getJson().getString("requestType"), is(RequestType.RECALL.getValue()));
    assertThat(requestItem.getString("status"), is(ItemStatus.IN_TRANSIT.getValue()));
    assertThat(recallRequest.getJson().getString("status"), is(RequestStatus.OPEN_NOT_YET_FILLED.getValue()));
  }

  @Test
  public void cannotCreateRecallRequestWhenItemIsAvailable() {
    final IndividualResource availableItem = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestPickupServicePoint = servicePointsFixture.cd1();

    final Response recallResponse = requestsClient.attemptCreate(new RequestBuilder()
      .recall()
      .forItem(availableItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.james()));

    assertThat(recallResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Recall requests are not allowed for this patron and item combination"),
      hasParameter("requestType", "Recall"))));
  }

  @Test
  public void cannotCreateRecallRequestWhenItemIsMissing() {
    final IndividualResource missingItem = setupMissingItem(itemsFixture);

    final Response recallRequest = requestsClient.attemptCreate(new RequestBuilder()
      .recall()
      .forItem(missingItem)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.jessica()));

    assertThat(recallRequest.getJson(), hasErrorWith(allOf(
      hasMessage("Recall requests are not allowed for this patron and item combination"),
      hasParameter("requestType", "Recall"))));
  }

  @Test
  public void canCreateRecallRequestWhenItemIsPaged() {
    final IndividualResource requestPickupServicePoint = servicePointsFixture.cd1();
    final IndividualResource smallAngryPlannet = setupPagedItem(requestPickupServicePoint, itemsFixture, requestsClient, usersFixture);
    final IndividualResource pagedItem = itemsClient.get(smallAngryPlannet);

    final Response recallResponse = requestsClient.attemptCreate(new RequestBuilder()
      .recall()
      .forItem(pagedItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.jessica()));

      assertThat(recallResponse.getJson().getString("requestType"), is(RECALL.getValue()));
      assertThat(pagedItem.getResponse().getJson().getJsonObject("status").getString("name"), is(PAGED.getValue()));
      assertThat(recallResponse.getJson().getString("status"), is(RequestStatus.OPEN_NOT_YET_FILLED.getValue()));
  }

  @Test
  public void canCreateHoldRequestWhenItemIsCheckedOut() {
    final IndividualResource checkedOutItem = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestPickupServicePoint = servicePointsFixture.cd1();

    checkOutFixture.checkOutByBarcode(checkedOutItem, usersFixture.jessica());

    final IndividualResource holdRequest = requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(checkedOutItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.james()));

    JsonObject requestedItem = holdRequest.getJson().getJsonObject("item");

    assertThat(holdRequest.getJson().getString("requestType"), is(RequestType.HOLD.getValue()));
    assertThat(requestedItem.getString("status"), is(ItemStatus.CHECKED_OUT.getValue()));
    assertThat(holdRequest.getJson().getString("status"), is(RequestStatus.OPEN_NOT_YET_FILLED.getValue()));
  }

  @Test
  public void canCreateHoldRequestWhenItemIsAwaitingPickup() {
    //Setting up an item with AWAITING_PICKUP status
    final IndividualResource servicePoint = servicePointsFixture.cd1();
    final IndividualResource awaitingPickupItem = setupItemAwaitingPickup(servicePoint, requestsClient, itemsClient,
      itemsFixture, usersFixture, checkInFixture);
    // create a hold request
    final IndividualResource holdRequest = requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(awaitingPickupItem)
      .withPickupServicePointId(servicePoint.getId())
      .by(usersFixture.steve()));

    assertThat(holdRequest.getJson().getString("requestType"), is(RequestType.HOLD.getValue()));
    assertThat(holdRequest.getJson().getJsonObject("item").getString("status"), is(ItemStatus.AWAITING_PICKUP.getValue()));
    assertThat(holdRequest.getJson().getString("status"), is(RequestStatus.OPEN_NOT_YET_FILLED.getValue()));
  }

  @Test
  public void canCreateHoldRequestWhenItemIsInTransit() {
    final IndividualResource requestPickupServicePoint = servicePointsFixture.cd1();

    final IndividualResource intransitItem = setupItemInTransit(requestPickupServicePoint, servicePointsFixture.cd2(),
      itemsFixture, requestsClient,
      usersFixture, requestsFixture, checkInFixture);

    //create a Hold request
    final IndividualResource holdRequest = requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(intransitItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.jessica()));

    JsonObject requestedItem = holdRequest.getJson().getJsonObject("item");

    assertThat(holdRequest.getJson().getString("requestType"), is(RequestType.HOLD.getValue()));
    assertThat(requestedItem.getString("status"), is(ItemStatus.IN_TRANSIT.getValue()));
    assertThat(holdRequest.getJson().getString("status"), is(RequestStatus.OPEN_NOT_YET_FILLED.getValue()));
  }

  @Test
  public void canCreateHoldRequestWhenItemIsMissing() {
    final IndividualResource missingItem = setupMissingItem(itemsFixture);

    //create a Hold request
    final IndividualResource holdRequest = requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(missingItem)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.jessica()));

    JsonObject requestedItem = holdRequest.getJson().getJsonObject("item");

    assertThat(holdRequest.getJson().getString("requestType"), is(RequestType.HOLD.getValue()));
    assertThat(requestedItem.getString("status"), is(ItemStatus.MISSING.getValue()));
    assertThat(holdRequest.getJson().getString("status"), is(RequestStatus.OPEN_NOT_YET_FILLED.getValue()));
  }

  @Test
  public void canCreateHoldRequestWhenItemIsPaged() {
    final IndividualResource requestPickupServicePoint = servicePointsFixture.cd1();
    final IndividualResource pagedItem = setupPagedItem(requestPickupServicePoint, itemsFixture, requestsClient, usersFixture);

    final IndividualResource holdRequest = requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(pagedItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.steve()));

    assertThat(holdRequest.getJson().getString("requestType"), is(RequestType.HOLD.getValue()));
    assertThat(holdRequest.getJson().getJsonObject("item").getString("status"), is(ItemStatus.PAGED.getValue()));
    assertThat(holdRequest.getJson().getString("status"), is(RequestStatus.OPEN_NOT_YET_FILLED.getValue()));
  }

  @Test
  public void cannotCreateHoldRequestWhenItemIsAvailable() {
    final IndividualResource availableItem = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestPickupServicePoint = servicePointsFixture.cd1();

    final Response holdResponse = requestsClient.attemptCreate(new RequestBuilder()
      .hold()
      .forItem(availableItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.james()));

    assertThat(holdResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Hold requests are not allowed for this patron and item combination"),
      hasParameter("requestType", "Hold"))));
  }

  @Test
  public void cannotCreateTwoRequestsFromTheSameUserForTheSameItem() {
    final IndividualResource checkedOutItem = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestPickupServicePoint = servicePointsFixture.cd1();
    final IndividualResource charlotte = usersFixture.charlotte();
    final IndividualResource jessica = usersFixture.jessica();

    checkOutFixture.checkOutByBarcode(checkedOutItem, charlotte);

    requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(checkedOutItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(jessica));

    final Response response = requestsClient.attemptCreate(new RequestBuilder()
      .recall()
      .forItem(checkedOutItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(jessica));

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(
      response.getJson(),
      hasErrorWith(hasMessage("This requester already has an open request for this item"))
    );
  }

  @Test
  public void canCreateTwoRequestsFromDifferentUsersForTheSameItem() {
    final IndividualResource checkedOutItem = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestPickupServicePoint = servicePointsFixture.cd1();
    final IndividualResource charlotte = usersFixture.charlotte();

    checkOutFixture.checkOutByBarcode(checkedOutItem, charlotte);

    requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(checkedOutItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.jessica()));

    final Response response = requestsClient.attemptCreate(new RequestBuilder()
      .recall()
      .forItem(checkedOutItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.james()));

    assertThat(response, hasStatus(HTTP_CREATED));
  }

  @Test
  public void pageRequestNoticeIsSentWhenPolicyDefinesPageRequestNoticeConfiguration() {
    UUID pageConfirmationTemplateId = UUID.randomUUID();
    JsonObject pageConfirmationConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(pageConfirmationTemplateId)
      .withEventType(PAGING_REQUEST_EVENT)
      .create();
    JsonObject holdConfirmationConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(UUID.randomUUID())
      .withEventType(HOLD_REQUEST_EVENT)
      .create();
    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with page notice")
      .withLoanNotices(Arrays.asList(pageConfirmationConfiguration, holdConfirmationConfiguration));
    useFallbackPolicies(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    UUID id = UUID.randomUUID();
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    ItemResource item = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource requester = usersFixture.steve();
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);
    IndividualResource request = requestsFixture.place(new RequestBuilder()
      .withId(id)
      .open()
      .page()
      .forItem(item)
      .by(requester)
      .withRequestDate(requestDate)
      .fulfilToHoldShelf()
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31))
      .withPickupServicePointId(pickupServicePointId)
      .withTags(new RequestBuilder.Tags(asList("new", "important"))));

    await()
      .atMost(1, SECONDS)
      .until(patronNoticesClient::getAll, Matchers.hasSize(1));
    List<JsonObject> sentNotices = patronNoticesClient.getAll();

    Map<String, Matcher<String>> noticeContextMatchers = new HashMap<>();
    noticeContextMatchers.putAll(TemplateContextMatchers.getUserContextMatchers(requester));
    noticeContextMatchers.putAll(TemplateContextMatchers.getItemContextMatchers(item, true));
    noticeContextMatchers.putAll(TemplateContextMatchers.getRequestContextMatchers(request));
    MatcherAssert.assertThat(sentNotices,
      hasItems(
        hasEmailNoticeProperties(requester.getId(), pageConfirmationTemplateId, noticeContextMatchers)));
    assertThatPublishedNoticeLogRecordEventsCountIsEqualTo(patronNoticesClient.getAll().size());
    assertThatPublishedLogRecordEventsAreValid();
  }

  @Test
  public void holdRequestNoticeIsSentWhenPolicyDefinesHoldRequestNoticeConfiguration() {
    UUID holdConfirmationTemplateId = UUID.randomUUID();
    JsonObject holdConfirmationConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(holdConfirmationTemplateId)
      .withEventType(HOLD_REQUEST_EVENT)
      .create();
    JsonObject recallConfirmationConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(UUID.randomUUID())
      .withEventType(RECALL_REQUEST_EVENT)
      .create();
    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with hold notice")
      .withLoanNotices(Arrays.asList(holdConfirmationConfiguration, recallConfirmationConfiguration));
    useFallbackPolicies(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());


    UUID id = UUID.randomUUID();
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    ItemBuilder itemBuilder = ItemExamples.basedUponSmallAngryPlanet(materialTypesFixture.book().getId(), loanTypesFixture.canCirculate().getId());
    HoldingBuilder holdingBuilder = itemsFixture.applyCallNumberHoldings(
      "CN",
      "Prefix",
      "Suffix",
      Collections.singletonList("CopyNumbers"));
    ItemResource item = itemsFixture.basedUponSmallAngryPlanet(itemBuilder, holdingBuilder);

    IndividualResource requester = usersFixture.steve();
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

    IndividualResource request = requestsFixture.place(new RequestBuilder()
      .withId(id)
      .open()
      .hold()
      .forItem(item)
      .by(requester)
      .withRequestDate(requestDate)
      .fulfilToHoldShelf()
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31))
      .withPickupServicePointId(pickupServicePointId)
      .withTags(new RequestBuilder.Tags(asList("new", "important"))));

    await()
      .atMost(1, SECONDS)
      .until(patronNoticesClient::getAll, Matchers.hasSize(1));
    List<JsonObject> sentNotices = patronNoticesClient.getAll();

    Map<String, Matcher<String>> noticeContextMatchers = new HashMap<>();
    noticeContextMatchers.putAll(TemplateContextMatchers.getUserContextMatchers(requester));
    noticeContextMatchers.putAll(TemplateContextMatchers.getItemContextMatchers(item, true));
    noticeContextMatchers.putAll(TemplateContextMatchers.getRequestContextMatchers(request));
    MatcherAssert.assertThat(sentNotices,
      hasItems(
        hasEmailNoticeProperties(requester.getId(), holdConfirmationTemplateId, noticeContextMatchers)));
    assertThatPublishedNoticeLogRecordEventsCountIsEqualTo(patronNoticesClient.getAll().size());
    assertThatPublishedLogRecordEventsAreValid();
  }

  @Test
  public void recallRequestNoticeIsSentWhenPolicyDefinesRecallRequestNoticeConfiguration() {
    UUID recallConfirmationTemplateId = UUID.randomUUID();
    UUID recallToLoaneeTemplateId = UUID.randomUUID();
    JsonObject recallConfirmationConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(recallConfirmationTemplateId)
      .withEventType(RECALL_REQUEST_EVENT)
      .create();
    JsonObject recallToLoaneeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(recallToLoaneeTemplateId)
      .withEventType(ITEM_RECALLED)
      .create();
    JsonObject pageConfirmationConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(UUID.randomUUID())
      .withEventType(PAGING_REQUEST_EVENT)
      .create();
    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with recall notice")
      .withLoanNotices(Arrays.asList(
        recallConfirmationConfiguration,
        recallToLoaneeConfiguration,
        pageConfirmationConfiguration));

    LoanPolicyBuilder loanPolicy = new LoanPolicyBuilder()
      .withName("Can Circulate Rolling With Recalls")
      .rolling(Period.weeks(3))
      .withRecallsMinimumGuaranteedLoanPeriod(Period.weeks(2))
      .withRecallsRecallReturnInterval(Period.weeks(1));

    useFallbackPolicies(
      loanPoliciesFixture.create(loanPolicy).getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    UUID id = UUID.randomUUID();
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    ItemBuilder itemBuilder = ItemExamples.basedUponSmallAngryPlanet(
      materialTypesFixture.book().getId(),
      loanTypesFixture.canCirculate().getId(),
      "ItemCN",
      "ItemPrefix",
      "ItemSuffix",
      "CopyNumber");

    ItemResource item = itemsFixture.basedUponSmallAngryPlanet(itemBuilder, itemsFixture.thirdFloorHoldings());
    IndividualResource requester = usersFixture.steve();
    IndividualResource loanOwner = usersFixture.jessica();

    DateTime loanDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);
    IndividualResource loan = checkOutFixture.checkOutByBarcode(item, loanOwner, loanDate);

    DateTime requestDate = loanDate.plusDays(1);
    mockClockManagerToReturnFixedDateTime(requestDate);

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
    IndividualResource loanAfterRecall = loansClient.get(loan.getId());

    await()
      .atMost(1, SECONDS)
      .until(patronNoticesClient::getAll, Matchers.hasSize(2));
    List<JsonObject> sentNotices = patronNoticesClient.getAll();

    Map<String, Matcher<String>> recallConfirmationContextMatchers = new HashMap<>();
    recallConfirmationContextMatchers.putAll(TemplateContextMatchers.getUserContextMatchers(requester));
    recallConfirmationContextMatchers.putAll(TemplateContextMatchers.getItemContextMatchers(item, false));
    recallConfirmationContextMatchers.putAll(TemplateContextMatchers.getLoanContextMatchers(loanAfterRecall));
    recallConfirmationContextMatchers.putAll(TemplateContextMatchers.getRequestContextMatchers(request));
    Map<String, Matcher<String>> recallNotificationContextMatchers = new HashMap<>();
    recallNotificationContextMatchers.putAll(TemplateContextMatchers.getUserContextMatchers(loanOwner));
    recallNotificationContextMatchers.putAll(TemplateContextMatchers.getItemContextMatchers(item, false));
    recallNotificationContextMatchers.putAll(TemplateContextMatchers.getLoanContextMatchers(loanAfterRecall));
    MatcherAssert.assertThat(sentNotices,
      hasItems(
        hasEmailNoticeProperties(requester.getId(), recallConfirmationTemplateId,
          recallConfirmationContextMatchers),
        hasEmailNoticeProperties(loanOwner.getId(), recallToLoaneeTemplateId,
          recallNotificationContextMatchers)));
    assertThatPublishedNoticeLogRecordEventsCountIsEqualTo(patronNoticesClient.getAll().size());
    assertThatPublishedLogRecordEventsAreValid();
  }

  @Test
  public void recallNoticeToLoanOwnerIsNotSendWhenDueDateIsNotChanged() {
    UUID recallToLoanOwnerTemplateId = UUID.randomUUID();
    JsonObject recallToLoanOwnerNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(recallToLoanOwnerTemplateId)
      .withEventType(ITEM_RECALLED)
      .create();
    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with recall notice")
      .withLoanNotices(Collections.singletonList(
        recallToLoanOwnerNoticeConfiguration));

    LoanPolicyBuilder loanPolicy = new LoanPolicyBuilder()
      .withName("Policy with recall notice")
      .withDescription("Recall configuration has no effect on due date")
      .rolling(Period.weeks(3))
      .withClosedLibraryDueDateManagement(DueDateManagement.KEEP_THE_CURRENT_DUE_DATE.getValue())
      .withRecallsMinimumGuaranteedLoanPeriod(Period.weeks(3))
      .withRecallsRecallReturnInterval(Period.weeks(1));

    useFallbackPolicies(
      loanPoliciesFixture.create(loanPolicy).getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    UUID id = UUID.randomUUID();
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource requester = usersFixture.steve();
    IndividualResource loanOwner = usersFixture.jessica();

    DateTime loanDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);
    checkOutFixture.checkOutByBarcode(item, loanOwner, loanDate);

    DateTime requestDate = loanDate.plusDays(1);
    mockClockManagerToReturnFixedDateTime(requestDate);

    requestsFixture.place(new RequestBuilder()
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

    // Recall notice to loan owner shouldn't be sent when due date hasn't been changed
    await()
      .pollDelay(1, SECONDS)
      .until(patronNoticesClient::getAll, empty());

    assertThatPublishedNoticeLogRecordEventsCountIsEqualTo(patronNoticesClient.getAll().size());
    assertThatPublishedLogRecordEventsAreValid();
  }

  @Test
  public void canCreatePagedRequestWithNullProxyUser() {
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
      .by(usersFixture.james())
      .withUserProxyId(null));

    String finalStatus = pagedRequest.getResponse().getJson().getJsonObject("item").getString("status");
    assertThat(pagedRequest.getJson().getString("requestType"), is(RequestType.PAGE.getValue()));
    assertThat(pagedRequest.getResponse(), hasStatus(HTTP_CREATED));
    assertThat(finalStatus, is(ItemStatus.PAGED.getValue()));
  }

  @Test
  public void requestCreationDoesNotFailWhenCirculationRulesReferenceInvalidNoticePolicyId() {
    UUID invalidNoticePolicyId = UUID.randomUUID();
    IndividualResource record = noticePoliciesFixture.create(new NoticePolicyBuilder()
      .withId(invalidNoticePolicyId)
      .withName("Example NoticePolicy"));
    setInvalidNoticePolicyReferenceInRules(invalidNoticePolicyId.toString());
    noticePoliciesFixture.delete(record);

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final IndividualResource createdRequest = requestsFixture.place(new RequestBuilder()
      .open()
      .page()
      .forItem(smallAngryPlanet)
      .by(steve)
      .withRequestDate(DateTime.now())
      .fulfilToHoldShelf()
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));

    assertThat(createdRequest.getResponse(), hasStatus(HTTP_CREATED));
  }

  @Test
  public void cannotCreateRequestWhenRequesterHasActiveRequestManualBlocks() {
    final IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requester = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    final DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);
    final DateTime now = ClockManager.getClockManager().getDateTime();
    final DateTime expirationDate = now.plusDays(4);
    final UserManualBlockBuilder userManualBlockBuilder = getManualBlockBuilder()
        .withRequests(true)
        .withExpirationDate(expirationDate)
        .withUserId(String.valueOf(requester.getId()));
    final RequestBuilder requestBuilder = createRequestBuilder(item, requester, pickupServicePointId, requestDate);

    checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());
    userManualBlocksFixture.create(userManualBlockBuilder);

    Response postResponse = requestsClient.attemptCreate(requestBuilder);

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(postResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Patron blocked from requesting"))));
      hasParameter("reason", "Display description");
  }

  @Test
  public void canCreateRequestWhenRequesterNotHaveActiveManualBlocks() {
    final IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requester = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    final DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);
    final DateTime now = ClockManager.getClockManager().getDateTime();
    final DateTime expirationDate = now.plusDays(4);
    final UserManualBlockBuilder userManualBlockBuilder = getManualBlockBuilder()
      .withExpirationDate(expirationDate)
      .withUserId(String.valueOf(requester.getId()));
    final RequestBuilder requestBuilder = createRequestBuilder(item, requester, pickupServicePointId, requestDate);

    checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

    userManualBlocksFixture.create(userManualBlockBuilder);

    Response postResponse = requestsClient.attemptCreate(requestBuilder);

    assertThat(postResponse, hasStatus(HTTP_CREATED));
  }

  @Test
  public void canCreateRequestWhenRequesterHasManualExpiredBlock() {
    final IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requester = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    final DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);
    final DateTime now = ClockManager.getClockManager().getDateTime();
    final DateTime expirationDate = now.minusDays(1);
    final UserManualBlockBuilder userManualBlockBuilder = getManualBlockBuilder()
      .withRequests(true)
      .withExpirationDate(expirationDate)
      .withUserId(String.valueOf(requester.getId()));
    final RequestBuilder requestBuilder = createRequestBuilder(item, requester, pickupServicePointId, requestDate);

    checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());
    userManualBlocksFixture.create(userManualBlockBuilder);

    Response postResponse = requestsClient.attemptCreate(requestBuilder);

    assertThat(postResponse, hasStatus(HTTP_CREATED));
  }

  @Test
  public void canCreateRequestWhenRequesterNoHaveRequestBlockAndHaveBorrowingRenewalsBlock() {
    final IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requester = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    final DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);
    final DateTime now = ClockManager.getClockManager().getDateTime();
    final DateTime expirationDate = now.plusDays(7);
    final UserManualBlockBuilder borrowingUserManualBlockBuilder = getManualBlockBuilder()
      .withBorrowing(true)
      .withExpirationDate(expirationDate)
      .withUserId(String.valueOf(requester.getId()));
    final UserManualBlockBuilder renewalsUserManualBlockBuilder =
      getManualBlockBuilder()
        .withRenewals(true)
        .withExpirationDate(expirationDate)
        .withUserId(String.valueOf(requester.getId()));
    final RequestBuilder requestBuilder = createRequestBuilder(item, requester, pickupServicePointId, requestDate);

    checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());
    userManualBlocksFixture.create(borrowingUserManualBlockBuilder);
    userManualBlocksFixture.create(renewalsUserManualBlockBuilder);

    Response postResponse = requestsClient.attemptCreate(requestBuilder);

    assertThat(postResponse, hasStatus(HTTP_CREATED));
  }

  @Test
  public void cannotCreateRequestWhenRequesterHasSomeActiveRequestManualBlocks() {
    final IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requester = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    final DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);
    final DateTime now = ClockManager.getClockManager().getDateTime();
    final DateTime expirationDate = now.plusDays(4);
    final UserManualBlockBuilder requestUserManualBlockBuilder1 = getManualBlockBuilder()
        .withRequests(true)
        .withExpirationDate(expirationDate)
        .withUserId(String.valueOf(requester.getId()));
    final UserManualBlockBuilder requestUserManualBlockBuilder2 = getManualBlockBuilder()
        .withBorrowing(true)
        .withRenewals(true)
        .withRequests(true)
        .withExpirationDate(expirationDate)
        .withUserId(String.valueOf(requester.getId()))
        .withDesc("Test");
    final RequestBuilder requestBuilder = createRequestBuilder(item, requester, pickupServicePointId, requestDate);

    checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());
    userManualBlocksFixture.create(requestUserManualBlockBuilder1);
    userManualBlocksFixture.create(requestUserManualBlockBuilder2);

    Response postResponse = requestsClient.attemptCreate(requestBuilder);

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(postResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Patron blocked from requesting"))));
  }

  @Test
  public void canFetchHundredRequests() {
    final List<IndividualResource> createdRequests = createOneHundredRequests();

    final Map<String, JsonObject> foundRequests = requestsFixture
      .getRequests(exactMatch("requestType", "Page"), limit(100), noOffset())
      .stream()
      .collect(Collectors.toMap(json -> json.getString("id"), identity()));

    createdRequests.forEach(expectedRequest -> {
      final JsonObject actualRequest = foundRequests.get(expectedRequest.getId().toString());

      assertThat(actualRequest, notNullValue());
      assertThat(actualRequest, hasJsonPath("requestType", "Page"));
      assertThat(actualRequest, hasJsonPath("status", "Open - Not yet filled"));
    });
  }

  @Test
  public void requestRefusedWhenAutomatedBlockExistsForPatron() {
    final IndividualResource steve = usersFixture.steve();
    final ItemResource item = itemsFixture.basedUponTemeraire();

    checkOutFixture.checkOutByBarcode(item);
    automatedPatronBlocksFixture.blockAction(steve.getId().toString(), false, false, true);

    final Response response = requestsClient.attemptCreate(new RequestBuilder()
      .open()
      .recall()
      .forItem(item)
      .by(steve)
      .fulfilToHoldShelf()
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage(MAX_NUMBER_OF_ITEMS_CHARGED_OUT_MESSAGE))));
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage(MAX_OUTSTANDING_FEE_FINE_BALANCE_MESSAGE))));
  }

  @Test
  public void shouldNotCreateRequestForItemInDisallowedStatus() {
    final IndividualResource withdrawnItem = itemsFixture
      .basedUponSmallAngryPlanet(ItemBuilder::agedToLost);

    final Response response = requestsClient.attemptCreate(new RequestBuilder()
      .open()
      .recall()
      .forItem(withdrawnItem)
      .by(usersFixture.steve())
      .fulfilToHoldShelf()
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Recall requests are not allowed for this patron and item combination"),
      hasParameter("requestType", "Recall"))));
  }

  private List<IndividualResource> createOneHundredRequests() {
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    return IntStream.range(0, 100).mapToObj(notUsed -> requestsFixture.place(
      new RequestBuilder()
        .open()
        .page()
        .forItem(itemsFixture.basedUponSmallAngryPlanet())
        .by(usersFixture.charlotte())
        .fulfilToHoldShelf()
        .withPickupServicePointId(pickupServicePointId)))
      .collect(Collectors.toList());
  }

  private UserManualBlockBuilder getManualBlockBuilder() {
    return new UserManualBlockBuilder()
      .withType("Manual")
      .withDesc("Display description")
      .withStaffInformation("Staff information")
      .withPatronMessage("Patron message")
      .withId(UUID.randomUUID());
  }

  private RequestBuilder createRequestBuilder(IndividualResource item,
    IndividualResource requester, UUID pickupServicePointId,
    DateTime requestDate) {

    return new RequestBuilder()
      .withId(UUID.randomUUID())
      .open()
      .recall()
      .forItem(item)
      .by(requester)
      .withRequestDate(requestDate)
      .fulfilToHoldShelf()
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31))
      .withPickupServicePointId(pickupServicePointId)
      .withTags(new RequestBuilder.Tags(asList("new", "important")));
  }

  public static IndividualResource setupPagedItem(IndividualResource requestPickupServicePoint,
    ItemsFixture itemsFixture, ResourceClient requestClient, UsersFixture usersFixture) {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    final IndividualResource pagedRequest = requestClient.create(new RequestBuilder()
      .page()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.james()));

    JsonObject requestedItem = pagedRequest.getJson().getJsonObject("item");
    assertThat(requestedItem.getString("status"), is(ItemStatus.PAGED.getValue()));

    return smallAngryPlanet;
  }

  public static IndividualResource setupItemAwaitingPickup(
    IndividualResource requestPickupServicePoint, ResourceClient requestsClient,
    ResourceClient itemsClient, ItemsFixture itemsFixture, UsersFixture usersFixture,
    CheckInFixture checkInFixture) {

    //Setting up an item with AWAITING_PICKUP status
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    requestsClient.create(new RequestBuilder()
      .page()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.james()));

    checkInFixture.checkInByBarcode(smallAngryPlanet, DateTime.now(DateTimeZone.UTC), requestPickupServicePoint.getId());

    Response pagedRequestRecord = itemsClient.getById(smallAngryPlanet.getId());
    assertThat(pagedRequestRecord.getJson().getJsonObject("status").getString("name"), is(ItemStatus.AWAITING_PICKUP.getValue()));

    return smallAngryPlanet;
  }

  public static IndividualResource setupItemInTransit(IndividualResource requestPickupServicePoint,
    IndividualResource pickupServicePoint, ItemsFixture itemsFixture, ResourceClient requestsClient,
    UsersFixture usersFixture, RequestsFixture requestsFixture, CheckInFixture checkInFixture) {

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
    checkInFixture.checkInByBarcode(smallAngryPlanet, DateTime.now(DateTimeZone.UTC), pickupServicePoint.getId());

    MultipleRecords<JsonObject> requests = requestsFixture.getQueueFor(smallAngryPlanet);
    JsonObject pagedRequestRecord = requests.getRecords().iterator().next();

    assertThat(pagedRequestRecord.getJsonObject("item").getString("status"), is(ItemStatus.IN_TRANSIT.getValue()));
    assertThat(pagedRequestRecord.getString("status"), is(RequestStatus.OPEN_IN_TRANSIT.getValue()));

    return smallAngryPlanet;
  }

  public static IndividualResource setupMissingItem(ItemsFixture itemsFixture) {
    IndividualResource missingItem = itemsFixture.basedUponSmallAngryPlanet(ItemBuilder::missing);
    assertThat(missingItem.getResponse().getJson().getJsonObject("status").getString("name"), is(ItemStatus.MISSING.getValue()));

    return missingItem;
  }
}
