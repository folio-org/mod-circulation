package api.requests;

import static api.support.PubsubPublisherTestUtils.assertThatPublishedNoticeLogRecordEventsAreValid;
import static api.support.builders.RequestBuilder.OPEN_AWAITING_PICKUP;
import static api.support.builders.RequestBuilder.OPEN_NOT_YET_FILLED;
import static api.support.fakes.FakePubSub.getPublishedEventsAsList;
import static api.support.fakes.PublishedEvents.byEventType;
import static api.support.fakes.PublishedEvents.byLogEventType;
import static api.support.fixtures.AutomatedPatronBlocksFixture.MAX_NUMBER_OF_ITEMS_CHARGED_OUT_MESSAGE;
import static api.support.fixtures.AutomatedPatronBlocksFixture.MAX_OUTSTANDING_FEE_FINE_BALANCE_MESSAGE;
import static api.support.http.CqlQuery.exactMatch;
import static api.support.http.CqlQuery.notEqual;
import static api.support.http.Limit.limit;
import static api.support.http.Offset.noOffset;
import static api.support.matchers.EventMatchers.isValidLoanDueDateChangedEvent;
import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static api.support.matchers.JsonObjectMatcher.hasNoJsonPath;
import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.UUIDMatcher.is;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasErrors;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasNullParameter;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static api.support.matchers.ValidationErrorMatchers.hasUUIDParameter;
import static api.support.matchers.ValidationErrorMatchers.isBlockRelatedError;
import static api.support.matchers.ValidationErrorMatchers.isInsufficientPermissionsToOverridePatronBlockError;
import static api.support.utl.BlockOverridesUtils.buildOkapiHeadersWithPermissions;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfPublishedEvents;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfSentNotices;
import static java.time.ZoneOffset.UTC;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.function.Function.identity;
import static org.folio.HttpStatus.HTTP_ACCEPTED;
import static org.folio.HttpStatus.HTTP_BAD_REQUEST;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.circulation.domain.ItemStatus.AVAILABLE;
import static org.folio.circulation.domain.ItemStatus.PAGED;
import static org.folio.circulation.domain.RequestType.RECALL;
import static org.folio.circulation.domain.policy.Period.hours;
import static org.folio.circulation.domain.representations.ItemProperties.CALL_NUMBER_COMPONENTS;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE_ERROR;
import static org.folio.circulation.domain.representations.logs.LogEventType.REQUEST_CREATED_THROUGH_OVERRIDE;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.awaitility.Awaitility;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.RequestLevel;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.override.BlockOverrides;
import org.folio.circulation.domain.override.PatronBlockOverride;
import org.folio.circulation.domain.policy.DueDateManagement;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.utils.ClockUtil;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import api.support.APITests;
import api.support.TlrFeatureStatus;
import api.support.builders.Address;
import api.support.builders.HoldingBuilder;
import api.support.builders.ItemBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.MoveRequestBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.builders.RequestBuilder;
import api.support.builders.UserBuilder;
import api.support.builders.UserManualBlockBuilder;
import api.support.fakes.FakeModNotify;
import api.support.fakes.FakePubSub;
import api.support.fakes.PublishedEvents;
import api.support.fixtures.CheckInFixture;
import api.support.fixtures.ItemExamples;
import api.support.fixtures.ItemsFixture;
import api.support.fixtures.RequestsFixture;
import api.support.fixtures.TemplateContextMatchers;
import api.support.fixtures.UsersFixture;
import api.support.http.CheckOutResource;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.OkapiHeaders;
import api.support.http.ResourceClient;
import api.support.http.UserResource;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class RequestsAPICreationTests extends APITests {
  private static final String PAGING_REQUEST_EVENT = "Paging request";
  private static final String HOLD_REQUEST_EVENT = "Hold request";
  private static final String RECALL_REQUEST_EVENT = "Recall request";
  private static final String ITEM_RECALLED = "Item recalled";

  public static final String CREATE_REQUEST_PERMISSION = "circulation.requests.item.post";
  public static final String OVERRIDE_PATRON_BLOCK_PERMISSION = "circulation.override-patron-block";
  public static final OkapiHeaders HEADERS_WITH_ALL_OVERRIDE_PERMISSIONS =
    buildOkapiHeadersWithPermissions(CREATE_REQUEST_PERMISSION, OVERRIDE_PATRON_BLOCK_PERMISSION);
  public static final OkapiHeaders HEADERS_WITHOUT_OVERRIDE_PERMISSIONS =
    buildOkapiHeadersWithPermissions();
  public static final BlockOverrides PATRON_BLOCK_OVERRIDE =
    new BlockOverrides(null, new PatronBlockOverride(true), null, null, null, null);
  public static final String PATRON_BLOCK_NAME = "patronBlock";

  @AfterEach
  public void afterEach() {
    mockClockManagerToReturnDefaultDateTime();
    configurationsFixture.deleteTlrFeatureConfig();
  }

  @Test
  void canCreateARequest() {
    UUID id = UUID.randomUUID();
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    UUID isbnIdentifierId = identifierTypesFixture.isbn().getId();
    String isbnValue = "9780866989732";

    ItemResource item = itemsFixture.basedUponSmallAngryPlanet(
      identity(),
      instanceBuilder -> instanceBuilder.addIdentifier(isbnIdentifierId, isbnValue),
      itemsFixture.addCallNumberStringComponents());

    checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

    IndividualResource requester = usersFixture.steve();

    ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);
    UUID instanceId = item.getInstanceId();

    IndividualResource request = requestsFixture.place(new RequestBuilder()
      .withId(id)
      .open()
      .recall()
      .forItem(item)
      .itemRequestLevel()
      .withInstanceId(instanceId)
      .by(requester)
      .withRequestDate(requestDate)
      .fulfilToHoldShelf()
      .withRequestExpiration(LocalDate.of(2017, 7, 30))
      .withHoldShelfExpiration(LocalDate.of(2017, 8, 31))
      .withPickupServicePointId(pickupServicePointId)
      .withTags(new RequestBuilder.Tags(asList("new", "important")))
      .withPatronComments("I need this book"));

    JsonObject representation = request.getJson();

    assertThat(representation.getString("id"), is(id.toString()));
    assertThat(representation.getString("requestType"), is("Recall"));
    assertThat(representation.getString("requestLevel"), is("Item"));
    assertThat(representation.getString("requestDate"), isEquivalentTo(requestDate));
    assertThat(representation.getString("itemId"), is(item.getId().toString()));
    assertThat(representation.getString("holdingsRecordId"), is(item.getHoldingsRecordId()));
    assertThat(representation.getString("instanceId"), is(instanceId));
    assertThat(representation.getString("requesterId"), is(requester.getId().toString()));
    assertThat(representation.getString("fulfilmentPreference"), is("Hold Shelf"));
    assertThat(representation.getString("requestExpirationDate"), is("2017-07-30T23:59:59.000Z"));
    assertThat(representation.getString("holdShelfExpirationDate"), is("2017-08-31"));
    assertThat(representation.getString("status"), is("Open - Not yet filled"));
    assertThat(representation.getString("pickupServicePointId"), is(pickupServicePointId.toString()));
    assertThat(representation.getString("patronComments"), is("I need this book"));

    assertThat("has information taken from item",
      representation.containsKey("item"), is(true));

    JsonObject requestItem = representation.getJsonObject("item");
    assertThat("barcode is taken from item",
      requestItem.getString("barcode"),
      is("036000291452"));

    JsonObject requestInstance = representation.getJsonObject("instance");
    assertThat("title is taken from instance",
      requestInstance.getString("title"),
      is("The Long Way to a Small, Angry Planet"));

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

    JsonArray identifiers = requestInstance.getJsonArray("identifiers");
    assertThat(identifiers, notNullValue());
    assertThat(identifiers.size(), is(1));
    assertThat(identifiers.getJsonObject(0).getString("identifierTypeId"),
      is(isbnIdentifierId.toString()));
    assertThat(identifiers.getJsonObject(0).getString("value"),
      is(isbnValue));
    JsonArray contributors = requestInstance.getJsonArray("contributorNames");
    assertThat(contributors, notNullValue());
    assertThat(contributors.size(), is(1));
    assertThat(contributors.getJsonObject(0).getString("name"), is("Chambers, Becky"));

    JsonArray editions = requestInstance.getJsonArray("editions");
    assertThat(editions, Matchers.notNullValue());
    assertThat(editions.size(), is(1));
    assertThat(editions.getString(0), is("First American Edition"));

    JsonArray publication = requestInstance.getJsonArray("publication");
    assertThat(publication, Matchers.notNullValue());
    assertThat(publication.size(), is(1));
    JsonObject firstPublication = publication.getJsonObject(0);
    assertThat(firstPublication.getString("publisher"), is("Alfred A. Knopf"));
    assertThat(firstPublication.getString("place"), is("New York"));
    assertThat(firstPublication.getString("dateOfPublication"), is("2016"));
  }

  @Test
  void canCreateARequestAtSpecificLocation() {
    UUID id = UUID.randomUUID();

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();

    checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

    IndividualResource requester = usersFixture.steve();

    ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);

    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    Response response = requestsClient.attemptCreateAtSpecificLocation(new RequestBuilder()
      .withId(id)
      .open()
      .recall()
      .forItem(item)
      .by(requester)
      .withRequestDate(requestDate)
      .fulfilToHoldShelf()
      .withRequestExpiration(LocalDate.of(2017, 7, 30))
      .withHoldShelfExpiration(LocalDate.of(2017, 8, 31))
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
    assertThat(representation.getString("requestExpirationDate"), is("2017-07-30T23:59:59.000Z"));
    assertThat(representation.getString("holdShelfExpirationDate"), is("2017-08-31"));
    assertThat(representation.getString("status"), is("Open - Not yet filled"));

    assertThat("has information taken from item",
      representation.containsKey("item"), is(true));

    assertThat("title is taken from instance",
      representation.getJsonObject("instance").getString("title"),
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

  @ParameterizedTest
  @CsvSource({
    "NOT_CONFIGURED, Page",
    "NOT_CONFIGURED, Hold",
    "NOT_CONFIGURED, Recall",
    "DISABLED, Page",
    "DISABLED, Hold",
    "DISABLED, Recall",
    "ENABLED, Page",
    "ENABLED, Hold",
    "ENABLED, Recall"
  })
  void cannotCreateItemLevelRequestForUnknownInstance(String tlrFeatureStatus,
    String requestType) {

    reconfigureTlrFeature(TlrFeatureStatus.valueOf(tlrFeatureStatus));

    UUID patronId = usersFixture.charlotte().getId();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    Response postResponse = requestsClient.attemptCreate(new RequestBuilder()
      .withRequestType(requestType)
      .itemRequestLevel()
      .withNoItemId()
      .withInstanceId(UUID.randomUUID())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(patronId));

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(postResponse.getJson(), hasErrorWith(hasMessage("Instance does not exist")));
  }

  @ParameterizedTest
  @CsvSource({"Page", "Hold", "Recall"})
  void cannotCreateTitleLevelRequestForUnknownInstance(String requestType) {
    configurationsFixture.enableTlrFeature();

    UUID patronId = usersFixture.charlotte().getId();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    Response postResponse = requestsClient.attemptCreate(new RequestBuilder()
      .withRequestType(requestType)
      .titleRequestLevel()
      .withNoItemId()
      .withInstanceId(UUID.randomUUID())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(patronId));

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(postResponse.getJson(), hasErrorWith(hasMessage("There are no holdings for this instance")));
  }

  @ParameterizedTest
  @CsvSource({
    "NOT_CONFIGURED, Page",
    "NOT_CONFIGURED, Hold",
    "NOT_CONFIGURED, Recall",
    "DISABLED, Page",
    "DISABLED, Hold",
    "DISABLED, Recall",
    "ENABLED, Page",
    "ENABLED, Hold",
    "ENABLED, Recall"
  })
  void cannotCreateRequestForUnknownItem(String tlrFeatureEnabledString, String requestType) {
    if (Boolean.parseBoolean(tlrFeatureEnabledString)) {
      configurationsFixture.enableTlrFeature();
    }

    IndividualResource instance = instancesFixture.basedUponDunkirk();
    UUID itemId = UUID.randomUUID();
    UUID patronId = usersFixture.charlotte().getId();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    Response postResponse = requestsClient.attemptCreate(new RequestBuilder()
      .withRequestType(requestType)
      .withInstanceId(instance.getId())
      .withItemId(itemId)
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(patronId));

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(postResponse.getJson(), hasErrorWith(hasMessage("Item does not exist")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Page", "Hold", "Recall"})
  void cannotCreateTitleLevelRequestWhenTlrDisabled(String requestType) {
    UUID patronId = usersFixture.charlotte().getId();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    Response postResponse = requestsClient.attemptCreate(new RequestBuilder()
      .withRequestType(requestType)
      .titleRequestLevel()
      .withItemId(null)
      .withInstanceId(UUID.randomUUID())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(patronId));

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(postResponse.getJson(), hasErrorWith(allOf(
      hasMessage("requestLevel must be one of the following: \"Item\""),
      hasParameter("requestLevel", "Title"))));
  }

  @Test
  void cannotCreateRequestWithNoItemReferenceWhenTlrDisabled() {
    UUID patronId = usersFixture.charlotte().getId();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    Response postResponse = requestsClient.attemptCreate(new RequestBuilder()
      .recall()
      .withInstanceId(instancesFixture.basedUponDunkirk().getId())
      .withNoItemId()
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(patronId));

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(postResponse.getJson(), hasErrors(1));
    assertThat(postResponse.getJson(), hasErrorWith(
      hasMessage("Cannot create an item level request with no item ID")));
  }

  @Test
  void cannotCreateRequestWithNonExistentRequestLevel() {
    UUID patronId = usersFixture.charlotte().getId();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    ItemResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID instanceId = item.getInstanceId();

    Response postResponse = requestsClient.attemptCreate(new RequestBuilder()
      .recall()
      .withItemId(item.getId())
      .withRequestLevel("invalid")
      .withInstanceId(instanceId)
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(patronId));

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(postResponse.getJson(), hasErrorWith(allOf(
      hasMessage("requestLevel must be one of the following: \"Item\""),
      hasParameter("requestLevel", "invalid"))));
  }

  @Test
  void canCreateTitleLevelRequestWhenTlrEnabled() {
    UUID patronId = usersFixture.charlotte().getId();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    ItemResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID instanceId = item.getInstanceId();

    configurationsFixture.enableTlrFeature();

    IndividualResource requestResource = requestsClient.create(new RequestBuilder()
      .page()
      .withNoHoldingsRecordId()
      .withNoItemId()
      .titleRequestLevel()
      .withInstanceId(instanceId)
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(patronId));

    JsonObject request = requestResource.getJson();
    assertThat(request.getString("requestLevel"), is("Title"));
  }

  @Test
  void cannotCreateRequestWithNonExistentRequestLevelWhenTlrEnabled() {
    UUID patronId = usersFixture.charlotte().getId();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    ItemResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID instanceId = item.getInstanceId();

    configurationsFixture.enableTlrFeature();

    Response postResponse = requestsClient.attemptCreate(new RequestBuilder()
      .recall()
      .withItemId(item.getId())
      .withRequestLevel("invalid")
      .withInstanceId(instanceId)
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(patronId));

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(postResponse.getJson(), hasErrorWith(allOf(
      hasMessage("requestLevel must be one of the following: \"Item\", \"Title\""),
      hasParameter("requestLevel", "invalid"))));
  }

  @Test
  void cannotCreateTlrRequestWhenUserHasLoanForSomeItemsOfInstance() {
    reconfigureTlrFeature(TlrFeatureStatus.ENABLED);

    IndividualResource instanceMultipleCopies = instancesFixture.basedUponDunkirk();
    IndividualResource holdings = holdingsFixture.defaultWithHoldings(
      instanceMultipleCopies.getId());
    IndividualResource locationsResource = locationsFixture.mainFloor();
    IndividualResource item = itemsFixture.createItemWithHoldingsAndLocation(holdings.getId(),
      locationsResource.getId());
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    JsonObject holdingsJson = holdings.getJson();

    checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

    Response postResponse = requestsClient.attemptCreate(
      new RequestBuilder()
        .hold()
        .withPickupServicePointId(pickupServicePointId)
        .titleRequestLevel()
        .withInstanceId(UUID.fromString(holdingsJson.getString("instanceId")))
        .withNoItemId()
        .withNoHoldingsRecordId()
        .by(usersFixture.jessica())
        .create());

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(postResponse.getJson(), hasErrorWith(allOf(
      hasMessage("One of the items of the requested title is already loaned to the requester"),
      hasParameter("itemId", item.getId().toString()),
      hasParameter("userId", usersFixture.jessica().getId().toString()))));
  }

  @Test
  void cannotCreateTlrWhenUserAlreadyRequestedTheSameTitle() {
    reconfigureTlrFeature(TlrFeatureStatus.ENABLED);

    final IndividualResource james = usersFixture.james();
    UUID instanceId = instancesFixture.basedUponDunkirk().getId();

    ItemResource itemResource = buildItem(instanceId, UUID.randomUUID().toString());
    checkOutFixture.checkOutByBarcode(itemResource, usersFixture.jessica());

    requestsFixture.placeTitleLevelHoldShelfRequest(instanceId, james);
    Response postResponse = requestsFixture.attemptPlaceTitleLevelHoldShelfRequest(instanceId,
      james);

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(postResponse.getJson(), hasErrorWith(allOf(
      hasMessage("This requester already has an open request for this instance"),
      hasParameter("requesterId", usersFixture.james().getId().toString()),
      hasParameter("instanceId", instanceId.toString()))));
  }

  @Test
  void cannotCreateTlrWhenUserAlreadyRequestedAnItemFromTheSameTitle() {
    reconfigureTlrFeature(TlrFeatureStatus.ENABLED);

    List<ItemResource> items = itemsFixture.createMultipleItemsForTheSameInstance(2);
    ItemResource item1 = items.get(0);
    ItemResource item2 = items.get(1);

    final IndividualResource charlotte = usersFixture.charlotte();
    final IndividualResource jessica = usersFixture.jessica();

    checkOutFixture.checkOutByBarcode(item1, charlotte);
    checkOutFixture.checkOutByBarcode(item2, charlotte);

    // Item-level request that should prevent a subsequent title-level request from being created
    requestsFixture.placeItemLevelHoldShelfRequest(item1, jessica);

    // Title-level request that should be refused because item-level request for one of the title's
    // items already exists
    final Response response = requestsFixture.attemptPlaceTitleLevelHoldShelfRequest(
      item1.getInstanceId(), jessica);

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(response.getJson(), hasErrors(1));
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("This requester already has an open request for one of the instance's items"),
      hasParameter("requesterId", jessica.getId().toString()),
      hasParameter("instanceId", item1.getInstanceId().toString()))));
  }

  @ParameterizedTest
  @EnumSource(value = RequestType.class, names = {"HOLD", "RECALL"})
  void cannotCreateHoldTlrWhenAvailableItemForInstance(RequestType requestType) {
    configurationsFixture.enableTlrFeature();

    List<ItemResource> items = itemsFixture.createMultipleItemsForTheSameInstance(2);
    ItemResource item = items.get(0);

    checkOutFixture.checkOutByBarcode(item, usersFixture.charlotte());

    // Hold TLR should be refused for the instance which has available item(s)
    final Response response = requestsFixture.attemptPlaceHoldOrRecallTLR(
      item.getInstanceId(), usersFixture.jessica(), requestType);

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(response.getJson(), hasErrors(1));
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Hold/Recall TLR not allowed: available item found for instance"),
      hasParameter("instanceId", item.getInstanceId().toString()),
      hasParameter("itemId", items.get(1).getId().toString()))));
  }

  @Test
  void cannotCreateRecallRequestWhenItemIsNotCheckedOut() {
    ItemResource item = itemsFixture.basedUponSmallAngryPlanet(
      ItemBuilder::available);

    Response postResponse = requestsClient.attemptCreate(new RequestBuilder()
      .recall()
      .withInstanceId(item.getInstanceId())
      .withItemId(item.getId())
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .withRequesterId(usersFixture.charlotte().getId()));

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(postResponse.getJson(), hasErrors(1));
    assertThat(postResponse.getJson(), hasErrorWith(
      hasMessage("Recall requests are not allowed for this patron and item combination")));
  }

  @Test
  void cannotCreateHoldRequestWhenItemIsNotCheckedOut() {
    ItemResource item = itemsFixture.basedUponSmallAngryPlanet(
      ItemBuilder::available);

    Response postResponse = requestsClient.attemptCreate(new RequestBuilder()
      .hold()
      .withInstanceId(item.getInstanceId())
      .withItemId(item.getId())
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .withRequesterId(usersFixture.charlotte().getId()));

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(postResponse.getJson(), hasErrors(1));
    assertThat(postResponse.getJson(), hasErrorWith(
      hasMessage("Hold requests are not allowed for this patron and item combination")));
  }

  @Test
  void cannotCreateRequestItemAlreadyCheckedOutToRequester() {
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
    assertThat(postResponse.getJson(), hasErrors(1));
    assertThat(postResponse.getJson(), hasErrorWith(allOf(
      hasMessage("This requester currently has this item on loan."),
      hasUUIDParameter("itemId", smallAngryPlanet.getId()),
      hasUUIDParameter("userId", rebecca.getId()))));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "Open - Not yet filled",
    "Open - Awaiting pickup",
    "Open - In transit",
    "Closed - Filled"
  })
  void canCreateARequestWithValidStatus(String status) {
    final ItemResource smallAngryPlanet =
      itemsFixture.basedUponSmallAngryPlanet(itemBuilder -> itemBuilder
        .withBarcode("036000291452"));
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    UUID itemId = smallAngryPlanet.getId();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet);

    UUID requesterId = usersFixture.steve().getId();

    final IndividualResource request = requestsFixture.place(new RequestBuilder()
      .recall()
      .fulfilToHoldShelf()
      .withInstanceId(smallAngryPlanet.getInstanceId())
      .withItemId(itemId)
      .withRequesterId(requesterId)
      .withPickupServicePointId(pickupServicePointId)
      .withStatus(status));

    JsonObject representation = request.getJson();

    assertThat(representation.getString("status"), is(status));
  }

  //TODO: Replace with validation error message
  @ParameterizedTest
  @EmptySource
  @ValueSource(strings = {
    "Non-existent status",
  })
  void cannotCreateARequestWithInvalidStatus(String status) {
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
  @ParameterizedTest
  @EmptySource
  @ValueSource(strings = {
    "Non-existent status",
  })
  void cannotCreateARequestAtASpecificLocationWithInvalidStatus(String status) {
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
  void canCreateARequestToBeFulfilledByDeliveryToAnAddress() {
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
  void requestStatusDefaultsToOpen() {
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
  void cannotCreateRequestWithUserBelongingToNoPatronGroup() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource noUserGroupBob = usersFixture.noUserGroupBob();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);

    final Response recallResponse = requestsClient.attemptCreate(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .withRequestDate(requestDate)
      .by(noUserGroupBob));

    assertThat(recallResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(recallResponse.getJson(), hasErrors(1));
    assertThat(recallResponse.getJson(), hasErrorWith(
      hasMessage("A valid patron group is required. PatronGroup ID is null")));
  }

  @Test
  void cannotCreateRequestWithoutValidUser() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, steve);

    UUID nonExistentRequesterId = UUID.randomUUID();

    ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);

    final Response recallResponse = requestsClient.attemptCreate(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .withRequestDate(requestDate)
      .withRequesterId(nonExistentRequesterId));

    assertThat(recallResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(recallResponse.getJson(), hasErrors(1));
    assertThat(recallResponse.getJson(), hasErrorWith(
      hasMessage("A valid user and patron group are required. User is null")));
  }

  @Test
  void cannotCreateRequestWithAnInactiveUser() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource inactiveCharlotte = usersFixture.charlotte(UserBuilder::inactive);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, steve);

    ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);

    final Response recallResponse = requestsClient.attemptCreate(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .withRequestDate(requestDate)
      .withRequesterId(inactiveCharlotte.getId()));

    assertThat(recallResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(recallResponse.getJson(), hasErrors(1));
    assertThat(recallResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Inactive users cannot make requests"),
      hasUUIDParameter("requesterId", inactiveCharlotte.getId()),
      hasUUIDParameter("itemId", smallAngryPlanet.getId()))));
  }

  @Test
  void cannotCreateRequestAtSpecificLocationWithAnInactiveUser() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource inactiveCharlotte = usersFixture.charlotte(UserBuilder::inactive);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, steve);

    ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);

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
    assertThat(recallResponse.getJson(), hasErrors(1));
    assertThat(recallResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Inactive users cannot make requests"),
      hasUUIDParameter("requesterId", inactiveCharlotte.getId()),
      hasUUIDParameter("itemId", smallAngryPlanet.getId()))));
  }

  @Test
  void canCreateARequestWithRequesterWithMiddleName() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    final IndividualResource steve = usersFixture.steve(
      b -> b.withName("Jones", "Steven", "Anthony"));

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);

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
  void canCreateARequestWithRequesterWithNoBarcode() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource james = usersFixture.james();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    final IndividualResource steveWithNoBarcode = usersFixture.steve(
      UserBuilder::withNoBarcode);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);

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
  void canCreateARequestForItemWithNoBarcode() {
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

    assertThat("title is taken from instance",
      representation.getJsonObject("instance").getString("title"),
      is("The Long Way to a Small, Angry Planet"));

    assertThat("barcode is not taken from item when none present",
      representation.getJsonObject("item").containsKey("barcode"), is(false));
  }

  @Test
  void creatingARequestIgnoresReadOnlyInformationProvidedByClient() {
    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();
    final IndividualResource steve = usersFixture.steve();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    UUID itemId = smallAngryPlanet.getId();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);

    JsonObject request = new RequestBuilder()
      .recall()
      .withRequestDate(requestDate)
      .withItemId(itemId)
      .withInstanceId(smallAngryPlanet.getInstanceId())
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

    assertThat("title is taken from instance",
      representation.getJsonObject("instance").getString("title"),
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
  void cannotCreateARequestWithoutAPickupLocationServicePoint() {
    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();

    checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

    IndividualResource requester = usersFixture.steve();

    ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);

    Response postResponse = requestsClient.attemptCreate(new RequestBuilder()
      .open()
      .recall()
      .forItem(item)
      .by(requester)
      .withRequestDate(requestDate)
      .fulfilToHoldShelf()
      .withRequestExpiration(LocalDate.of(2017, 7, 30))
      .withHoldShelfExpiration(LocalDate.of(2017, 8, 31)));

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(postResponse.getJson(), hasErrors(1));
    assertThat(postResponse.getJson(), hasErrorWith(
      hasMessage("Hold Shelf Fulfillment Requests require a Pickup Service Point")));
  }

  @Test
  void cannotCreateARequestWithANonPickupLocationServicePoint() {
    UUID pickupServicePointId = servicePointsFixture.cd3().getId();

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();

    checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

    IndividualResource requester = usersFixture.steve();

    ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);

    Response postResponse = requestsClient.attemptCreate(new RequestBuilder()
      .open()
      .recall()
      .forItem(item)
      .by(requester)
      .withRequestDate(requestDate)
      .fulfilToHoldShelf()
      .withRequestExpiration(LocalDate.of(2017, 7, 30))
      .withHoldShelfExpiration(LocalDate.of(2017, 8, 31))
      .withPickupServicePointId(pickupServicePointId));

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(postResponse.getJson(), hasErrors(1));
    assertThat(postResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Service point is not a pickup location"),
      hasUUIDParameter("pickupServicePointId", pickupServicePointId))));
  }

  @Test
  void cannotCreateARequestWithUnknownPickupLocationServicePoint() {
    UUID pickupServicePointId = UUID.randomUUID();

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();

    checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

    IndividualResource requester = usersFixture.steve();

    ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);

    Response postResponse = requestsClient.attemptCreate(new RequestBuilder()
      .open()
      .recall()
      .forItem(item)
      .by(requester)
      .withRequestDate(requestDate)
      .fulfilToHoldShelf()
      .withRequestExpiration(LocalDate.of(2017, 7, 30))
      .withHoldShelfExpiration(LocalDate.of(2017, 8, 31))
      .withPickupServicePointId(pickupServicePointId));

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(postResponse.getJson(), hasErrors(1));
    assertThat(postResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Pickup service point does not exist"),
      hasUUIDParameter("pickupServicePointId", pickupServicePointId))));
  }

  @Test
  void canCreatePagedRequestWhenItemStatusIsAvailable() {
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
  void cannotCreateTitleLevelPagedRequestIfThereAreNoAvailableItems() {
    UUID patronId = usersFixture.charlotte().getId();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    configurationsFixture.enableTlrFeature();

    UUID instanceId = instancesFixture.basedUponDunkirk().getId();
    IndividualResource defaultWithHoldings = holdingsFixture.defaultWithHoldings(instanceId);
    itemsClient.create(new ItemBuilder()
      .forHolding(defaultWithHoldings.getId())
      .checkOut()
      .withMaterialType(UUID.randomUUID())
      .withPermanentLoanType(UUID.randomUUID())
      .create());

    Response postResponse = requestsClient.attemptCreate(
      buildPageTitleLevelRequest(patronId, pickupServicePointId, instanceId));

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(postResponse.getJson(), hasErrors(1));
    assertThat(postResponse.getJson(), hasErrorWith(
      hasMessage("Cannot create page TLR for this instance ID - no available items found")));
    assertThat(postResponse.getJson(), hasErrorWith(hasParameter("instanceId",
      instanceId.toString())));
  }

  @Test
  void canCreateTitleLevelPagedRequest() {
    UUID patronId = usersFixture.charlotte().getId();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    configurationsFixture.enableTlrFeature();

    IndividualResource uponDunkirkInstance = instancesFixture.basedUponDunkirk();
    UUID instanceId = uponDunkirkInstance.getId();
    IndividualResource defaultWithHoldings = holdingsFixture.defaultWithHoldings(instanceId);
    IndividualResource item = itemsClient.create(new ItemBuilder()
      .forHolding(defaultWithHoldings.getId())
      .withMaterialType(UUID.randomUUID())
      .withPermanentLoanType(UUID.randomUUID())
      .create());

    IndividualResource pagedRequest = requestsClient.create(buildPageTitleLevelRequest(patronId,
      pickupServicePointId, instanceId));

    JsonObject json = pagedRequest.getJson();
    assertThat(json.getString("requestType"), is(RequestType.PAGE.getValue()));
    assertThat(json.getString("holdingsRecordId"), is(defaultWithHoldings.getId().toString()));
    assertThat(json.getString("itemId"), is(item.getId()));
    assertThat(json.getString("instanceId"), is(instanceId));
    assertThat(pagedRequest.getResponse(), hasStatus(HTTP_CREATED));
    assertThat(json.getJsonObject("item")
      .getString("status"), is(ItemStatus.PAGED.getValue()));
    assertThat(json.getString("requestLevel"), is(RequestLevel.TITLE.getValue()));
  }

  @Test
  void cannotCreateItemLevelRequestIfTitleLevelRequestForInstanceAlreadyCreated() {
    UUID patronId = usersFixture.charlotte().getId();
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    UUID instanceId = UUID.randomUUID();
    configurationsFixture.enableTlrFeature();

    buildItem(instanceId, "111");
    requestsClient.create(buildPageTitleLevelRequest(patronId, pickupServicePointId, instanceId));
    assertThat(requestsClient.getAll(), hasSize(1));

    ItemResource secondItem = buildItem(instanceId, "222");
    Response response = requestsClient.attemptCreate(buildItemLevelRequest(
      patronId, pickupServicePointId, instanceId, secondItem));

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(response.getJson(), hasErrorWith(hasMessage(
      "This requester already has an open request for this item")));
  }

  @Test
  void cannotCreateTitleLevelRequestIfItemLevelRequestAlreadyCreated() {
    UUID patronId = usersFixture.charlotte().getId();
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    UUID instanceId = UUID.randomUUID();
    configurationsFixture.enableTlrFeature();

    buildItem(instanceId, "111");
    ItemResource secondItem = buildItem(instanceId, "222");
    requestsClient.create(buildItemLevelRequest(patronId, pickupServicePointId,
      instanceId, secondItem));
    assertThat(requestsClient.getAll(), hasSize(1));

    Response response = requestsClient.attemptCreate(buildPageTitleLevelRequest(
      patronId, pickupServicePointId, instanceId));
    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(response.getJson(), hasErrorWith(hasMessage(
      "This requester already has an open request for one of the instance's items")));
  }

  @Test
  void cannotCreatePagedRequestWhenItemStatusIsCheckedOut() {
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

    assertThat(pagedRequest.getJson(), hasErrors(1));
    assertThat(pagedRequest.getJson(), hasErrorWith(allOf(
      hasMessage("Page requests are not allowed for this patron and item combination"),
      hasParameter("requestType", "Page"))));
  }

  @Test
  void cannotCreatePagedRequestWhenItemStatusIsAwaitingPickup() {
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

    assertThat(pagedRequest2.getJson(), hasErrors(1));
    assertThat(pagedRequest2.getJson(), hasErrorWith(allOf(
      hasMessage("Page requests are not allowed for this patron and item combination"),
      hasParameter("requestType", "Page"))));
  }

  @Test
  void cannotCreatePagedRequestWhenItemStatusIsPaged() {
    //Set up the item's initial status to be PAGED
    final IndividualResource servicePoint = servicePointsFixture.cd1();
    final IndividualResource pagedItem = setupPagedItem(servicePoint, itemsFixture, requestsClient, usersFixture);

    //Attempt to create a page request on it.
    final Response pagedRequest2 = requestsClient.attemptCreate(new RequestBuilder()
      .page()
      .forItem(pagedItem)
      .withPickupServicePointId(servicePoint.getId())
      .by(usersFixture.jessica()));

    assertThat(pagedRequest2.getJson(), hasErrors(1));
    assertThat(pagedRequest2.getJson(), hasErrorWith(allOf(
      hasMessage("Page requests are not allowed for this patron and item combination"),
      hasParameter("requestType", "Page"))));
  }

  @Test
  void cannotCreatePagedRequestWhenItemStatusIsInTransit() {
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

    assertThat(pagedRequest2.getJson(), hasErrors(1));
    assertThat(pagedRequest2.getJson(), hasErrorWith(allOf(
      hasMessage("Page requests are not allowed for this patron and item combination"),
      hasParameter("requestType", "Page"))));
  }

  @Test
  void canCreateRecallRequestWhenItemIsCheckedOut() {
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
  void canCreateTlrRecallForInstanceWithSingleItemAndTwoLoans() {
    reconfigureTlrFeature(TlrFeatureStatus.ENABLED);
    final ItemResource item = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestPickupServicePoint = servicePointsFixture.cd1();

    IndividualResource initialLoan = checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());
    checkInFixture.checkInByBarcode(item);
    IndividualResource dueDateChangeLoan = checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

    requestsClient.create(new RequestBuilder()
      .recall()
      .withNoHoldingsRecordId()
      .withNoItemId()
      .titleRequestLevel()
      .withInstanceId(item.getInstanceId())
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .withRequesterId(usersFixture.james().getId()));

    PublishedEvents publishedEvents = Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(FakePubSub::getPublishedEvents, hasSize(9));

    JsonObject loanWithoutChange = loansClient.get(initialLoan.getId()).getJson();
    assertNull(loanWithoutChange.getString("dueDateChangedByRecall"));
    JsonObject updatedLoan = loansClient.get(dueDateChangeLoan.getId()).getJson();
    assertThat(updatedLoan.getString("dueDateChangedByRecall"), true);

    JsonObject event = publishedEvents.findFirst(byEventType("LOAN_DUE_DATE_CHANGED"));
    assertThat(event, isValidLoanDueDateChangedEvent(updatedLoan));
    assertThat(new JsonObject(event.getString("eventPayload"))
      .getBoolean("dueDateChangedByRecall"), equalTo(true));
  }

  @Test
  void canCreateRecallRequestWhenItemIsAwaitingPickup() {
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
  void canCreateRecallRequestWhenItemIsInTransit() {
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
  void cannotCreateRecallRequestWhenItemIsAvailable() {
    final IndividualResource availableItem = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestPickupServicePoint = servicePointsFixture.cd1();

    final Response recallResponse = requestsClient.attemptCreate(new RequestBuilder()
      .recall()
      .forItem(availableItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.james()));

    assertThat(recallResponse.getJson(), hasErrors(1));
    assertThat(recallResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Recall requests are not allowed for this patron and item combination"),
      hasParameter("requestType", "Recall"))));
  }

  @Test
  void cannotCreateRecallRequestWhenItemIsMissing() {
    final IndividualResource missingItem = setupMissingItem(itemsFixture);

    final Response recallRequest = requestsClient.attemptCreate(new RequestBuilder()
      .recall()
      .forItem(missingItem)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.jessica()));

    assertThat(recallRequest.getJson(), hasErrors(1));
    assertThat(recallRequest.getJson(), hasErrorWith(allOf(
      hasMessage("Recall requests are not allowed for this patron and item combination"),
      hasParameter("requestType", "Recall"))));
  }

  @Test
  void canCreateRecallRequestWhenItemIsPaged() {
    final IndividualResource requestPickupServicePoint = servicePointsFixture.cd1();
    final ItemResource smallAngryPlanet = setupPagedItem(requestPickupServicePoint,
      itemsFixture, requestsClient, usersFixture);
    final IndividualResource pagedItem = itemsClient.get(smallAngryPlanet);

    final Response recallResponse = requestsClient.attemptCreate(new RequestBuilder()
      .recall()
      .forItem(pagedItem)
      .withInstanceId(smallAngryPlanet.getInstanceId())
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.jessica()));

    assertThat(recallResponse.getJson().getString("requestType"), is(RECALL.getValue()));
    assertThat(pagedItem.getResponse().getJson().getJsonObject("status").getString("name"), is(PAGED.getValue()));
    assertThat(recallResponse.getJson().getString("status"), is(RequestStatus.OPEN_NOT_YET_FILLED.getValue()));
  }

  @Test
  void canCreateHoldRequestWhenItemIsCheckedOut() {
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
  void canCreateHoldRequestWhenItemIsAwaitingPickup() {
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
  void canCreateHoldRequestWhenItemIsInTransit() {
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
  void canCreateHoldRequestWhenItemIsMissing() {
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
  void canCreateHoldRequestWhenItemIsPaged() {
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
  void cannotCreateHoldRequestWhenItemIsAvailable() {
    final IndividualResource availableItem = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestPickupServicePoint = servicePointsFixture.cd1();

    final Response holdResponse = requestsClient.attemptCreate(new RequestBuilder()
      .hold()
      .forItem(availableItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.james()));

    assertThat(holdResponse.getJson(), hasErrors(1));
    assertThat(holdResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Hold requests are not allowed for this patron and item combination"),
      hasParameter("requestType", "Hold"))));
  }

  @Test
  void cannotCreateTwoRequestsFromTheSameUserForTheSameItem() {
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
    assertThat(response.getJson(), hasErrors(1));
    assertThat(
      response.getJson(),
      hasErrorWith(hasMessage("This requester already has an open request for this item"))
    );
  }

  @Test
  void canCreateTwoRequestsFromDifferentUsersForTheSameItem() {
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
  void pageRequestNoticeIsSentWhenPolicyDefinesPageRequestNoticeConfiguration() {
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
    ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);
    IndividualResource request = requestsFixture.place(new RequestBuilder()
      .withId(id)
      .open()
      .page()
      .forItem(item)
      .by(requester)
      .withRequestDate(requestDate)
      .fulfilToHoldShelf()
      .withRequestExpiration(LocalDate.of(2017, 7, 30))
      .withHoldShelfExpiration(LocalDate.of(2017, 8, 31))
      .withPickupServicePointId(pickupServicePointId)
      .withTags(new RequestBuilder.Tags(asList("new", "important"))));

    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    Map<String, Matcher<String>> noticeContextMatchers = new HashMap<>();
    noticeContextMatchers.putAll(TemplateContextMatchers.getUserContextMatchers(requester));
    noticeContextMatchers.putAll(TemplateContextMatchers.getItemContextMatchers(item, true));
    noticeContextMatchers.putAll(TemplateContextMatchers.getRequestContextMatchers(request));

    assertThat(FakeModNotify.getSentPatronNotices(), hasItems(
      hasEmailNoticeProperties(requester.getId(), pageConfirmationTemplateId, noticeContextMatchers)));
  }

  @Test
  void pageRequestNoticeIsNotSentWhenPatronNoticeRequestFails() {
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
    ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);

    FakeModNotify.setFailPatronNoticesWithBadRequest(true);

    requestsFixture.place(new RequestBuilder()
      .withId(id)
      .open()
      .page()
      .forItem(item)
      .by(requester)
      .withRequestDate(requestDate)
      .fulfilToHoldShelf()
      .withRequestExpiration(LocalDate.of(2017, 7, 30))
      .withHoldShelfExpiration(LocalDate.of(2017, 8, 31))
      .withPickupServicePointId(pickupServicePointId)
      .withTags(new RequestBuilder.Tags(asList("new", "important"))));

    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  void holdRequestNoticeIsSentWhenPolicyDefinesHoldRequestNoticeConfiguration() {
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
    ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);

    checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

    IndividualResource request = requestsFixture.place(new RequestBuilder()
      .withId(id)
      .open()
      .hold()
      .forItem(item)
      .by(requester)
      .withRequestDate(requestDate)
      .fulfilToHoldShelf()
      .withRequestExpiration(LocalDate.of(2017, 7, 30))
      .withHoldShelfExpiration(LocalDate.of(2017, 8, 31))
      .withPickupServicePointId(pickupServicePointId)
      .withTags(new RequestBuilder.Tags(asList("new", "important"))));

    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    Map<String, Matcher<String>> noticeContextMatchers = new HashMap<>();
    noticeContextMatchers.putAll(TemplateContextMatchers.getUserContextMatchers(requester));
    noticeContextMatchers.putAll(TemplateContextMatchers.getItemContextMatchers(item, true));
    noticeContextMatchers.putAll(TemplateContextMatchers.getRequestContextMatchers(request));

    assertThat(FakeModNotify.getSentPatronNotices(), hasItems(
      hasEmailNoticeProperties(requester.getId(), holdConfirmationTemplateId, noticeContextMatchers)));
  }

  @Test
  void holdRequestNoticeIsNotSentWhenPatronNoticeRequestFails() {
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
    ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);

    checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

    FakeModNotify.setFailPatronNoticesWithBadRequest(true);

    requestsFixture.place(new RequestBuilder()
      .withId(id)
      .open()
      .hold()
      .forItem(item)
      .by(requester)
      .withRequestDate(requestDate)
      .fulfilToHoldShelf()
      .withRequestExpiration(LocalDate.of(2017, 7, 30))
      .withHoldShelfExpiration(LocalDate.of(2017, 8, 31))
      .withPickupServicePointId(pickupServicePointId)
      .withTags(new RequestBuilder.Tags(asList("new", "important"))));

    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  void recallRequestNoticeIsSentWhenPolicyDefinesRecallRequestNoticeConfiguration() {
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

    ZonedDateTime loanDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);
    IndividualResource loan = checkOutFixture.checkOutByBarcode(item, loanOwner, loanDate);

    ZonedDateTime requestDate = loanDate.plusDays(1);
    mockClockManagerToReturnFixedDateTime(requestDate);

    IndividualResource request = requestsFixture.place(new RequestBuilder()
      .withId(id)
      .open()
      .recall()
      .forItem(item)
      .by(requester)
      .withRequestDate(requestDate)
      .fulfilToHoldShelf()
      .withRequestExpiration(LocalDate.of(2017, 7, 30))
      .withHoldShelfExpiration(LocalDate.of(2017, 8, 31))
      .withPickupServicePointId(pickupServicePointId)
      .withTags(new RequestBuilder.Tags(asList("new", "important"))));
    IndividualResource loanAfterRecall = loansClient.get(loan.getId());

    verifyNumberOfSentNotices(2);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    Map<String, Matcher<String>> recallConfirmationContextMatchers = new HashMap<>();
    recallConfirmationContextMatchers.putAll(TemplateContextMatchers.getUserContextMatchers(requester));
    recallConfirmationContextMatchers.putAll(TemplateContextMatchers.getItemContextMatchers(item, false));
    recallConfirmationContextMatchers.putAll(TemplateContextMatchers.getLoanContextMatchers(loanAfterRecall));
    recallConfirmationContextMatchers.putAll(TemplateContextMatchers.getRequestContextMatchers(request));

    Map<String, Matcher<String>> recallNotificationContextMatchers = new HashMap<>();
    recallNotificationContextMatchers.putAll(TemplateContextMatchers.getUserContextMatchers(loanOwner));
    recallNotificationContextMatchers.putAll(TemplateContextMatchers.getItemContextMatchers(item, false));
    recallNotificationContextMatchers.putAll(TemplateContextMatchers.getLoanContextMatchers(loanAfterRecall));

    assertThat(FakeModNotify.getSentPatronNotices(), hasItems(
      hasEmailNoticeProperties(requester.getId(), recallConfirmationTemplateId,
        recallConfirmationContextMatchers),
      hasEmailNoticeProperties(loanOwner.getId(), recallToLoaneeTemplateId,
        recallNotificationContextMatchers)));
  }

  @Test
  void recallRequestNoticeIsNotSentWhenPatronNoticeRequestFails() {
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

    ZonedDateTime loanDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);
    IndividualResource loan = checkOutFixture.checkOutByBarcode(item, loanOwner, loanDate);

    ZonedDateTime requestDate = loanDate.plusDays(1);
    mockClockManagerToReturnFixedDateTime(requestDate);

    FakeModNotify.setFailPatronNoticesWithBadRequest(true);

    requestsFixture.place(new RequestBuilder()
      .withId(id)
      .open()
      .recall()
      .forItem(item)
      .by(requester)
      .withRequestDate(requestDate)
      .fulfilToHoldShelf()
      .withRequestExpiration(LocalDate.of(2017, 7, 30))
      .withHoldShelfExpiration(LocalDate.of(2017, 8, 31))
      .withPickupServicePointId(pickupServicePointId)
      .withTags(new RequestBuilder.Tags(asList("new", "important"))));
    loansClient.get(loan.getId());

    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 2);
  }

  @Test
  void recallNoticeToLoanOwnerIsSendWhenDueDateIsNotChanged() {
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

    ZonedDateTime loanDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);
    checkOutFixture.checkOutByBarcode(item, loanOwner, loanDate);

    ZonedDateTime requestDate = loanDate.plusDays(1);
    mockClockManagerToReturnFixedDateTime(requestDate);

    requestsFixture.place(new RequestBuilder()
      .withId(id)
      .open()
      .recall()
      .forItem(item)
      .by(requester)
      .withRequestDate(requestDate)
      .fulfilToHoldShelf()
      .withRequestExpiration(LocalDate.of(2017, 7, 30))
      .withHoldShelfExpiration(LocalDate.of(2017, 8, 31))
      .withPickupServicePointId(pickupServicePointId)
      .withTags(new RequestBuilder.Tags(asList("new", "important"))));

    // Recall notice to loan owner should be sent when due date hasn't been changed
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void canCreatePagedRequestWithNullProxyUser() {
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
  void requestCreationDoesNotFailWhenCirculationRulesReferenceInvalidNoticePolicyId() {
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
      .withRequestDate(ClockUtil.getZonedDateTime())
      .fulfilToHoldShelf()
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));

    assertThat(createdRequest.getResponse(), hasStatus(HTTP_CREATED));
  }

  @Test
  void cannotCreateRequestWhenRequesterHasActiveRequestManualBlocks() {
    final IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requester = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    final ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);
    final ZonedDateTime now = ClockUtil.getZonedDateTime();
    final ZonedDateTime expirationDate = now.plusDays(4);
    final UserManualBlockBuilder userManualBlockBuilder = getManualBlockBuilder()
      .withRequests(true)
      .withExpirationDate(expirationDate)
      .withUserId(String.valueOf(requester.getId()));
    final RequestBuilder requestBuilder = createRequestBuilder(item, requester, pickupServicePointId, requestDate);

    checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());
    userManualBlocksFixture.create(userManualBlockBuilder);

    Response postResponse = requestsClient.attemptCreate(requestBuilder);

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(postResponse.getJson(), hasErrors(1));
    assertThat(postResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Patron blocked from requesting"),
      hasParameter("reason", "Display description")))
    );
  }

  @Test
  void cannotCreateRequestWhenRequesterHasActiveRequestManualBlockWithoutExpirationDate() {
    final IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requester = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    userManualBlocksFixture.create(getManualBlockBuilder()
      .withRequests(true)
      .withExpirationDate(null) // no expiration date
      .withUserId(String.valueOf(requester.getId())));

    RequestBuilder requestBuilder = new RequestBuilder()
      .withId(UUID.randomUUID())
      .open()
      .page()
      .forItem(item)
      .by(requester)
      .fulfilToHoldShelf()
      .withPickupServicePointId(pickupServicePointId);

    Response postResponse = requestsClient.attemptCreate(requestBuilder);

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(postResponse.getJson(), hasErrors(1));
    assertThat(postResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Patron blocked from requesting"),
      hasParameter("reason", "Display description")))
    );
  }

  @Test
  void canCreateRequestWhenRequesterNotHaveActiveManualBlocks() {
    final IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requester = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    final ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);
    final ZonedDateTime now = ClockUtil.getZonedDateTime();
    final ZonedDateTime expirationDate = now.plusDays(4);
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
  void canCreateRequestWhenRequesterHasManualExpiredBlock() {
    final IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requester = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    final ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);
    final ZonedDateTime now = ClockUtil.getZonedDateTime();
    final ZonedDateTime expirationDate = now.minusDays(1);
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
  void canCreateRequestWhenRequesterNoHaveRequestBlockAndHaveBorrowingRenewalsBlock() {
    final IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requester = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    final ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);
    final ZonedDateTime now = ClockUtil.getZonedDateTime();
    final ZonedDateTime expirationDate = now.plusDays(7);
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
  void cannotCreateRequestWhenRequesterHasSomeActiveRequestManualBlocks() {
    final IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requester = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    final ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);
    final ZonedDateTime now = ClockUtil.getZonedDateTime();
    final ZonedDateTime expirationDate = now.plusDays(4);
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
    assertThat(postResponse.getJson(), hasErrors(1));
    assertThat(postResponse.getJson(), hasErrorWith(
      hasMessage("Patron blocked from requesting")));
  }

  @Test
  void canFetchHundredRequests() {
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
  void getManyRequestWithIdParamQueryShouldReturnRequestWithFetchedInstance() {
    IndividualResource request = requestsFixture.place(
      new RequestBuilder()
        .open()
        .page()
        .forItem(itemsFixture.basedUponSmallAngryPlanet())
        .by(usersFixture.charlotte())
        .fulfilToHoldShelf()
        .withPickupServicePointId(servicePointsFixture.cd1().getId()));
    JsonObject requestRepresentation = requestsClient.getMany(exactMatch("id", request.getId().toString())).getFirst();

    assertThat(requestRepresentation, notNullValue());
    JsonObject instanceRepresentation = requestRepresentation.getJsonObject("instance");
    assertThat(instanceRepresentation, notNullValue());
    validateInstanceRepresentation(instanceRepresentation);
  }

  @Test
  void requestRefusedWhenAutomatedBlockExistsForPatron() {
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
    assertThat(response.getJson(), hasErrors(2));
    assertThat(response.getJson(), hasErrorWith(
      hasMessage(MAX_NUMBER_OF_ITEMS_CHARGED_OUT_MESSAGE)));
    assertThat(response.getJson(), hasErrorWith(
      hasMessage(MAX_OUTSTANDING_FEE_FINE_BALANCE_MESSAGE)));
  }

  @Test
  void shouldNotCreateRequestForItemInDisallowedStatus() {
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
    assertThat(response.getJson(), hasErrors(1));
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Recall requests are not allowed for this patron and item combination"),
      hasParameter("requestType", "Recall"))));
  }

  @Test
  void recallNoticeToLoanOwnerIsSentForMovedRecallIfDueDateIsNotChanged() {
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

    UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    List<ItemResource> items = itemsFixture.createMultipleItemsForTheSameInstance(2);
    IndividualResource itemToMoveTo = items.get(0);
    IndividualResource itemToMoveFrom = items.get(1);
    IndividualResource requester = usersFixture.rebecca();
    IndividualResource loanOwner = usersFixture.jessica();

    ZonedDateTime loanDate = ZonedDateTime.of(2020, 7, 22, 10, 22, 54, 0, UTC);
    checkOutFixture.checkOutByBarcode(itemToMoveTo, loanOwner, loanDate);
    checkOutFixture.checkOutByBarcode(itemToMoveFrom, loanOwner, loanDate);

    ZonedDateTime requestDate = loanDate.plusDays(1);
    mockClockManagerToReturnFixedDateTime(requestDate);

    IndividualResource recallRequest = requestsFixture.place(new RequestBuilder()
      .withId(UUID.randomUUID())
      .open()
      .recall()
      .forItem(itemToMoveFrom)
      .by(requester)
      .withRequestDate(requestDate)
      .fulfilToHoldShelf()
      .withRequestExpiration(LocalDate.of(2020, 7, 30))
      .withHoldShelfExpiration(LocalDate.of(2020, 8, 31))
      .withPickupServicePointId(pickupServicePointId)
      .withTags(new RequestBuilder.Tags(asList("new", "important"))));

    requestsFixture.move(new MoveRequestBuilder(recallRequest.getId(), itemToMoveTo.getId(),
      RECALL.getValue()));

    // Recall notice to loan owner should be sent twice without changing due date
    verifyNumberOfSentNotices(2);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void shouldNotCreateRequestWhenInstanceItemRequesterAndPickupServicePointAreNotProvided() {
    Response postResponse = requestsClient.attemptCreate(new RequestBuilder()
      .recall()
      .withInstanceId(null)
      .withItemId(null)
      .withRequesterId(null)
      .withPickupServicePointId(null));

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));

    final JsonObject responseJson = postResponse.getJson();

    assertThat(responseJson, hasErrors(4));

    assertThat(responseJson, hasErrorWith(allOf(
      hasMessage("Cannot create a request with no instance ID"),
      hasNullParameter("instanceId"))));

    assertThat(responseJson, hasErrorWith(allOf(
      hasMessage("Cannot create an item level request with no item ID"),
      hasNullParameter("itemId"))));

    assertThat(responseJson, hasErrorWith(allOf(
      hasMessage("A valid user and patron group are required. User is null"),
      hasNullParameter("userId"))));

    assertThat(responseJson, hasErrorWith(
      hasMessage("Hold Shelf Fulfillment Requests require a Pickup Service Point")));
  }

  @Test
  void shouldNotCreateRequestWhenInstanceItemRequesterAndPickupServicePointCannotBeFound() {
    final UUID instanceId = UUID.randomUUID();
    final UUID itemId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();
    final UUID pickupServicePointId = UUID.randomUUID();

    Response postResponse = requestsClient.attemptCreate(new RequestBuilder()
      .recall()
      .withInstanceId(instanceId)
      .withItemId(itemId)
      .withRequesterId(userId)
      .withPickupServicePointId(pickupServicePointId));

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));

    final JsonObject responseJson = postResponse.getJson();

    assertThat(responseJson, hasErrors(4));

    assertThat(responseJson, hasErrorWith(allOf(
      hasMessage("Instance does not exist"),
      hasUUIDParameter("instanceId", instanceId))));

    assertThat(responseJson, hasErrorWith(allOf(
      hasMessage("A valid user and patron group are required. User is null"),
      hasUUIDParameter("userId", userId))));

    assertThat(responseJson, hasErrorWith(allOf(
      hasMessage("Pickup service point does not exist"),
      hasUUIDParameter("pickupServicePointId", pickupServicePointId))));
  }

  @Test
  void shouldOverrideManualPatronBlockWhenUserHasPermissions() {
    UUID userId = usersFixture.jessica().getId();
    userManualBlocksFixture.createRequestsManualPatronBlockForUser(userId);
    Response response = attemptCreateRequestThroughPatronBlockOverride(
      userId, HEADERS_WITH_ALL_OVERRIDE_PERMISSIONS);
    assertOverrideResponseSuccess(response);
  }

  @Test
  void shouldOverrideAutomatedPatronBlockWhenUserHasPermissions() {
    UUID userId = usersFixture.jessica().getId();
    createAutomatedPatronBlockForUser(userId);
    Response response = attemptCreateRequestThroughPatronBlockOverride(
      userId, HEADERS_WITH_ALL_OVERRIDE_PERMISSIONS);
    assertOverrideResponseSuccess(response);
  }

  @Test
  void shouldOverrideManualAndAutomatedPatronBlocksWhenUserHasPermissions() {
    UUID userId = usersFixture.jessica().getId();
    userManualBlocksFixture.createRequestsManualPatronBlockForUser(userId);
    createAutomatedPatronBlockForUser(userId);
    Response response = attemptCreateRequestThroughPatronBlockOverride(
      userId, HEADERS_WITH_ALL_OVERRIDE_PERMISSIONS);
    assertOverrideResponseSuccess(response);
  }

  @Test
  void shouldCreateRequestThroughPatronBlockOverrideWhenUserHasPermissionsButNoBlocksExist() {
    UUID userId = usersFixture.jessica().getId();
    Response response = attemptCreateRequestThroughPatronBlockOverride(
      userId, HEADERS_WITH_ALL_OVERRIDE_PERMISSIONS);
    assertOverrideResponseSuccess(response);
  }

  @Test
  void shouldFailToOverridePatronBlockWhenUserHasInsufficientPermissions() {
    shouldFailToOverridePatronBlockWithInsufficientPermissions(CREATE_REQUEST_PERMISSION);
  }

  @Test
  void shouldFailToOverridePatronBlockWhenUserHasNoPermissionsAtAll() {
    shouldFailToOverridePatronBlockWithInsufficientPermissions();
  }

  private void shouldFailToOverridePatronBlockWithInsufficientPermissions(String... permissions) {
    UUID userId = usersFixture.jessica().getId();
    userManualBlocksFixture.createRequestsManualPatronBlockForUser(userId);
    Response response = attemptCreateRequestThroughPatronBlockOverride(
      userId, buildOkapiHeadersWithPermissions(permissions));

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(response.getJson(), hasErrors(1));

    assertThat(getErrorsFromResponse(response),
      hasItem(isInsufficientPermissionsToOverridePatronBlockError()));
  }

  @Test
  void shouldFailToOverridePatronBlockWhenUserHasNoPermissionsAndNonOverridableErrorOccurs() {
    UserResource inactiveSteve = usersFixture.steve(UserBuilder::inactive);
    UUID userId = inactiveSteve.getId();
    userManualBlocksFixture.createRequestsManualPatronBlockForUser(userId);
    Response response = attemptCreateRequestThroughPatronBlockOverride(
      userId, buildOkapiHeadersWithPermissions(CREATE_REQUEST_PERMISSION));

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(response.getJson(), hasErrors(2));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Inactive users cannot make requests"),
      hasUUIDParameter("requesterId", userId))));

    assertThat(getErrorsFromResponse(response),
      hasItem(isInsufficientPermissionsToOverridePatronBlockError()));
  }

  @Test
  void shouldFailToCreateRequestWhenBlockExistsAndUserHasPermissionsButOverrideIsNotRequested() {
    UUID userId = usersFixture.steve().getId();
    userManualBlocksFixture.createRequestsManualPatronBlockForUser(userId);
    Response response = attemptCreateRequestThroughOverride(userId,
      HEADERS_WITH_ALL_OVERRIDE_PERMISSIONS, null);

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(response.getJson(), hasErrors(1));

    assertThat(getErrorsFromResponse(response),
      hasItem(isBlockRelatedError("Patron blocked from requesting",
        PATRON_BLOCK_NAME, emptyList())));
  }

  @Test
  void shouldFailToCreateRequestWhenBlockExistsButUserHasNoPermissionsAndOverrideIsNotRequested() {
    UUID userId = usersFixture.steve().getId();
    userManualBlocksFixture.createRequestsManualPatronBlockForUser(userId);
    Response response = attemptCreateRequestThroughOverride(userId,
      buildOkapiHeadersWithPermissions(CREATE_REQUEST_PERMISSION), null);

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(response.getJson(), hasErrors(1));

    assertThat(getErrorsFromResponse(response),
      hasItem(isBlockRelatedError("Patron blocked from requesting",
        PATRON_BLOCK_NAME, List.of(OVERRIDE_PATRON_BLOCK_PERMISSION))));
  }

  @Test
  void overrideResponseDoesNotContainDuplicateInsufficientOverridePermissionsErrors() {
    UUID userId = usersFixture.steve().getId();
    userManualBlocksFixture.createRequestsManualPatronBlockForUser(userId);
    createAutomatedPatronBlockForUser(userId);

    Response response = attemptCreateRequestThroughPatronBlockOverride(
      userId, HEADERS_WITHOUT_OVERRIDE_PERMISSIONS);

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(response.getJson(), hasErrors(1));

    assertThat(getErrorsFromResponse(response),
      hasItem(isInsufficientPermissionsToOverridePatronBlockError()));
  }

  @Test
  void requestProcessingParametersAreNotStoredInRequestRecord() {
    UUID userId = usersFixture.jessica().getId();
    Response response = attemptCreateRequestThroughPatronBlockOverride(
      userId, HEADERS_WITH_ALL_OVERRIDE_PERMISSIONS);
    assertOverrideResponseSuccess(response);

    assertThat(response.getJson(), hasNoJsonPath("requestProcessingParameters"));
  }

  @Test
  void titleLevelRequestConfirmationNoticeShouldBeSentWithEnabledTlr() {
    UUID templateId = UUID.randomUUID();
    templateFixture.createDummyNoticeTemplate(templateId);
    reconfigureTlrFeature(TlrFeatureStatus.ENABLED, templateId, null, null);

    requestsFixture.place(buildTitleLevelRequest());
    var notices = verifyNumberOfSentNotices(1);
    assertThatPublishedNoticeLogRecordEventsAreValid(notices.get(0));
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @ParameterizedTest
  @EnumSource(value = TlrFeatureStatus.class, names = {"DISABLED", "NOT_CONFIGURED"})
  void titleLevelRequestConfirmationNoticeShouldNotBeSentWithDisabledTlr(
    TlrFeatureStatus tlrFeatureStatus) {

    UUID templateId = UUID.randomUUID();
    templateFixture.createDummyNoticeTemplate(templateId);
    reconfigureTlrFeature(tlrFeatureStatus, templateId, null, null);

    Response response = requestsFixture.attemptPlace(buildTitleLevelRequest());
    assertThat(response.getStatusCode(), CoreMatchers.is(422));
    assertThat(response.getJson(), hasErrorWith(
      hasMessage("requestLevel must be one of the following: \"Item\"")));
    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void titleLevelRequestConfirmationNoticeShouldNotBeSentWithoutConfiguredNoticeTemplate() {
    reconfigureTlrFeature(TlrFeatureStatus.ENABLED, null, null, null);

    requestsFixture.place(buildTitleLevelRequest());
    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void cannotCreateItemLevelRequestWithoutInstanceId() {
    reconfigureTlrFeature(TlrFeatureStatus.ENABLED, null, null, null);

    ItemResource item = itemsFixture.basedUponNod();

    Response postResponse = requestsClient.attemptCreate(new RequestBuilder()
      .page()
      .itemRequestLevel()
      .forItem(item)
      .withNoInstanceId()
      .withRequesterId(usersFixture.steve().getId())
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(postResponse.getJson(), hasErrors(1));
    assertThat(postResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Cannot create a request with no instance ID"),
      hasNullParameter("instanceId")
    )));
  }

  @Test
  void cannotCreateTitleLevelRequestWithoutInstanceId() {
    configurationsFixture.enableTlrFeature();

    ItemResource item = itemsFixture.basedUponNod();

    Response postResponse = requestsClient.attemptCreate(new RequestBuilder()
      .page()
      .titleRequestLevel()
      .forItem(item)
      .withNoInstanceId()
      .withRequesterId(usersFixture.steve().getId())
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(postResponse.getJson(), hasErrors(1));
    assertThat(postResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Cannot create page TLR for this instance ID - no available items found"),
      hasNullParameter("instanceId")
    )));
  }

  @Test
  void cannotCreateItemLevelRequestWithoutItemId() {
    Response postResponse = requestsClient.attemptCreate(new RequestBuilder()
      .page()
      .itemRequestLevel()
      .forItem(itemsFixture.basedUponNod())
      .withItemId(null)
      .withRequesterId(usersFixture.steve().getId())
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(postResponse.getJson(), hasErrors(1));
    assertThat(postResponse.getJson(), hasErrorWith(
      hasMessage("Cannot create an item level request with no item ID")));
  }

  @ParameterizedTest
  @EnumSource(value = RequestLevel.class, names = {"ITEM", "TITLE"})
  void cannotCreateRequestWithItemIdButNoHoldingsRecordId(RequestLevel requestLevel) {
    reconfigureTlrFeature(TlrFeatureStatus.ENABLED, null, null, null);

    Response postResponse = requestsClient.attemptCreate(new RequestBuilder()
      .page()
      .withRequestLevel(requestLevel.getValue())
      .forItem(itemsFixture.basedUponNod())
      .withNoHoldingsRecordId()
      .withRequesterId(usersFixture.steve().getId())
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));

    assertThat(postResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(postResponse.getJson(), hasErrors(1));
    assertThat(postResponse.getJson(), hasErrorWith(
      hasMessage("Cannot create a request with item ID but no holdings record ID")));
  }

  @Test
  void recallTlrRequestShouldBeAppliedToLoanWithClosestDueDate() {
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    UUID instanceId = UUID.randomUUID();
    configurationsFixture.enableTlrFeature();
    ItemResource firstItem = buildItem(instanceId, "111");
    ItemResource secondItem = buildItem(instanceId, "222");

    updateCirculationRulesWithLoanPeriod("One day loan policy", Period.days(1));
    CheckOutResource firstLoan = checkOutFixture.checkOutByBarcode(firstItem,
      usersFixture.jessica());

    updateCirculationRulesWithLoanPeriod("Five days loan policy", Period.days(5));
    CheckOutResource secondLoan = checkOutFixture.checkOutByBarcode(secondItem,
      usersFixture.charlotte());

    IndividualResource requester = usersFixture.steve();
    ZonedDateTime requestDate = ZonedDateTime.of(2021, 7, 22, 10, 22, 54, 0, UTC);
    IndividualResource request = requestsFixture.place(new RequestBuilder()
      .withId(UUID.randomUUID())
      .open()
      .recall()
      .titleRequestLevel()
      .withInstanceId(instanceId)
      .withNoItemId()
      .withNoHoldingsRecordId()
      .by(requester)
      .withRequestDate(requestDate)
      .fulfilToHoldShelf()
      .withRequestExpiration(LocalDate.of(2021, 7, 30))
      .withHoldShelfExpiration(LocalDate.of(2021, 8, 31))
      .withPickupServicePointId(pickupServicePointId)
      .withTags(new RequestBuilder.Tags(asList("new", "important")))
      .withPatronComments("I need this book"));

    var recalledLoanJson = loansFixture.getLoanById(firstLoan.getId()).getJson();
    var notRecalledLoanJson = loansFixture.getLoanById(secondLoan.getId()).getJson();
    assertThat(recalledLoanJson.getBoolean("dueDateChangedByRecall"), is(true));
    assertThat(notRecalledLoanJson.getBoolean("dueDateChangedByRecall"), nullValue());

    var requestJson = request.getJson();
    assertThat(requestJson.getString("requestType"), is("Recall"));
    assertThat(requestJson.getString("requestLevel"), is("Title"));
    assertThat(requestJson.getString("requestDate"), isEquivalentTo(requestDate));
    assertThat(requestJson.getString("itemId"), is(firstItem.getId().toString()));
    assertThat(requestJson.getString("instanceId"), is(instanceId));
    assertThat(requestJson.getString("requesterId"), is(requester.getId().toString()));
    assertThat(requestJson.getString("requestExpirationDate"), is("2021-07-30T23:59:59.000Z"));
    assertThat(requestJson.getString("holdShelfExpirationDate"), is("2021-08-31"));
    assertThat(requestJson.getString("status"), is("Open - Not yet filled"));
    assertThat(requestJson.getString("pickupServicePointId"), is(pickupServicePointId.toString()));
  }

  @Test
  void statusOfTlrRequestShouldBeChangedIfAssociatedItemCheckedIn() {
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    UUID instanceId = UUID.randomUUID();
    configurationsFixture.enableTlrFeature();
    ItemResource firstItem = buildItem(instanceId, "111");
    ItemResource secondItem = buildItem(instanceId, "222");

    IndividualResource firstTlrRequest = requestsFixture.place(
      buildPageRequest(instanceId, pickupServicePointId, usersFixture.steve()));
    IndividualResource secondTlrRequest = requestsFixture.place(
      buildPageRequest(instanceId, pickupServicePointId, usersFixture.jessica()));

    assertThat(firstTlrRequest.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));
    assertThat(secondTlrRequest.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));

    checkInFixture.checkInByBarcode(secondItem);
    var requestAssociatedWithSecondItem = requestsFixture.getRequests(
      exactMatch("itemId", secondItem.getId().toString()), limit(1), noOffset()).getFirst();
    var requestAssociatedWithFirstItem = requestsFixture.getRequests(
      notEqual("itemId", secondItem.getId().toString()), limit(1), noOffset()).getFirst();
    assertThat(requestAssociatedWithSecondItem.getString("status"), is(OPEN_AWAITING_PICKUP));
    assertThat(requestAssociatedWithFirstItem.getString("status"), is(OPEN_NOT_YET_FILLED));

    checkInFixture.checkInByBarcode(firstItem);
    requestAssociatedWithFirstItem = requestsFixture.getRequests(
      notEqual("itemId", secondItem.getId().toString()), limit(1), noOffset()).getFirst();
    assertThat(requestAssociatedWithFirstItem.getString("status"), is(OPEN_AWAITING_PICKUP));
  }

  @Test
  void pageRequestShouldNotChangeItemStatusIfFailsWithoutRequestDate() {
    var item = itemsFixture.basedUponSmallAngryPlanet();

    Response response = requestsClient.attemptCreate(new RequestBuilder()
      .page()
      .withFulfilmentPreference("Hold Shelf")
      .withRequesterId(usersFixture.steve().getId())
      .withItemId(item.getId())
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .withInstanceId(item.getInstanceId())
      .itemRequestLevel()
      .withHoldingsRecordId(item.getHoldingsRecordId())
      .withRequestDate(null));

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Cannot create a request with no requestDate"),
      hasParameter("requestDate", null))));
    var itemById = itemsFixture.getById(item.getId());
    assertThat(itemById.getResponse().getJson().getJsonObject("status").getString("name"),
      is(AVAILABLE.getValue()));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {
    "Hold shelf",
    "Invalid Value"
  })
  void pageRequestShouldNotBeCreatedIfFulfilmentPreferenceIsNotValid(String fulfilmentPreference) {
    var item = itemsFixture.basedUponSmallAngryPlanet();

    Response response = requestsClient.attemptCreate(new RequestBuilder()
      .page()
      .withFulfilmentPreference(fulfilmentPreference)
      .withRequesterId(usersFixture.steve().getId())
      .withItemId(item.getId())
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .withInstanceId(item.getInstanceId())
      .itemRequestLevel()
      .withHoldingsRecordId(item.getHoldingsRecordId())
      .withRequestDate(ZonedDateTime.now()));

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("fulfilmentPreference must be one of the following: Hold Shelf, Delivery"),
      hasParameter("fulfilmentPreference", fulfilmentPreference))));
    var itemById = itemsFixture.getById(item.getId());
    assertThat(itemById.getResponse().getJson().getJsonObject("status").getString("name"),
      is(AVAILABLE.getValue()));
  }

  @Test
  void itemCheckOutShouldNotAffectRequestAssociatedWithAnotherItemOfInstance() {
    UUID instanceId = UUID.randomUUID();
    configurationsFixture.enableTlrFeature();
    ItemResource firstItem = buildItem(instanceId, "111");
    ItemResource secondItem = buildItem(instanceId, "222");
    ZonedDateTime requestDate = ZonedDateTime.of(2021, 7, 22, 10, 22, 54, 0, UTC);
    IndividualResource request = requestsFixture.place(new RequestBuilder()
      .withId(UUID.randomUUID())
      .open()
      .page()
      .titleRequestLevel()
      .withInstanceId(instanceId)
      .withNoItemId()
      .withNoHoldingsRecordId()
      .by(usersFixture.steve())
      .withRequestDate(requestDate)
      .fulfilToHoldShelf()
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .withTags(new RequestBuilder.Tags(asList("new", "important")))
      .withPatronComments("I need this book"));

    var firstItemUpdated = itemsFixture.getById(firstItem.getId());
    var secondItemUpdated = itemsFixture.getById(secondItem.getId());
    var availableItem = Stream.of(firstItemUpdated, secondItemUpdated)
      .filter(this::isNotPaged)
      .findFirst()
      .orElse(null);

    assertThat(requestsFixture.getById(request.getId()).getJson().getString("status"),
      is(RequestStatus.OPEN_NOT_YET_FILLED.getValue()));
    checkOutFixture.checkOutByBarcode(availableItem);
    assertThat(requestsFixture.getById(request.getId()).getJson().getString("status"),
      is(RequestStatus.OPEN_NOT_YET_FILLED.getValue()));
  }

  @Test
  void itemCheckOutRecallRequestCreationShouldProduceNotice() {
    configurationsFixture.enableTlrFeature();
    JsonObject recallToLoaneeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(UUID.randomUUID())
      .withEventType(NoticeEventType.ITEM_RECALLED.getRepresentation())
      .create();
    JsonObject recallRequestToRequesterConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(UUID.randomUUID())
      .withEventType(NoticeEventType.RECALL_REQUEST.getRepresentation())
      .create();

    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with recall notice")
      .withLoanNotices(List.of(recallToLoaneeConfiguration, recallRequestToRequesterConfiguration));

    useFallbackPolicies(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    ItemResource item = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource requester = usersFixture.steve();
    ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);

    checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());
    requestsFixture.placeItemLevelHoldShelfRequest(item, requester, requestDate, "Recall");

    // notice for the recall is expected
    verifyNumberOfSentNotices(2);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    List<JsonObject> noticeLogContextItemLogs = FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE));

    // verify noticeLogContextItemLogs
    validateNoticeLogContextItem(noticeLogContextItemLogs.get(0), item);
    validateNoticeLogContextItem(noticeLogContextItemLogs.get(1), item);
  }

  private void validateNoticeLogContextItem(JsonObject noticeLogContextItem, ItemResource item) {
    JsonObject itemJsonObject = new JsonObject(noticeLogContextItem.getString("eventPayload"))
      .getJsonObject("payload")
      .getJsonArray("items")
      .getJsonObject(0);

    assertThat(itemJsonObject.getString("itemBarcode"), is(item.getBarcode()));
    assertThat(itemJsonObject.getString("itemId"), is(item.getId()));
    assertThat(itemJsonObject.getString("instanceId"), is(item.getInstanceId()));
    assertThat(itemJsonObject.getString("holdingsRecordId"), is(item.getHoldingsRecordId()));
  }

  private boolean isNotPaged(IndividualResource item) {
    return !PAGED.getValue().equals(item.getJson().getJsonObject("status").getString("name"));
  }

  private RequestBuilder buildPageRequest(UUID instanceId, UUID pickupServicePointId,
    UserResource jessica) {

    return new RequestBuilder()
      .withId(UUID.randomUUID())
      .open()
      .page()
      .titleRequestLevel()
      .withInstanceId(instanceId)
      .withNoItemId()
      .withNoHoldingsRecordId()
      .by(jessica)
      .withRequestDate(ZonedDateTime.of(2021, 7, 22, 10, 22, 54, 0, UTC))
      .fulfilToHoldShelf()
      .withRequestExpiration(LocalDate.of(2021, 7, 30))
      .withHoldShelfExpiration(LocalDate.of(2021, 8, 31))
      .withPickupServicePointId(pickupServicePointId)
      .withTags(new RequestBuilder.Tags(asList("new", "important")))
      .withPatronComments("I need this book");
  }

  private void updateCirculationRulesWithLoanPeriod(String policyName, Period loanPeriod) {
    policiesActivation.use(new LoanPolicyBuilder()
      .withName(policyName)
      .withDescription("Can circulate item")
      .rolling(loanPeriod)
      .notRenewable()
      .withRecallsRecallReturnInterval(hours(1)));
  }

  private ItemResource buildItem(UUID instanceId, String barcode) {
    UUID isbnIdentifierId = identifierTypesFixture.isbn().getId();

    return itemsFixture.basedUponSmallAngryPlanet(
      holdingBuilder -> holdingBuilder.forInstance(instanceId),
      instanceBuilder -> instanceBuilder
        .addIdentifier(isbnIdentifierId, "9780866989732")
        .withId(instanceId),
      itemBuilder -> itemBuilder.withBarcode(barcode));
  }

  private static void assertOverrideResponseSuccess(Response response) {
    assertThat(response, hasStatus(HTTP_CREATED));
    assertThat(response.getJson(), hasErrors(0));

    Awaitility.await()
      .atMost(1, SECONDS)
      .until(() -> getPublishedEventsAsList(
        byLogEventType(REQUEST_CREATED_THROUGH_OVERRIDE.value())).size() == 1);
  }

  private Response attemptCreateRequestThroughPatronBlockOverride(UUID requesterId,
    OkapiHeaders okapiHeaders) {

    return attemptCreateRequestThroughOverride(requesterId, okapiHeaders, PATRON_BLOCK_OVERRIDE);
  }

  private Response attemptCreateRequestThroughOverride(UUID requesterId, OkapiHeaders okapiHeaders,
    BlockOverrides blockOverrides) {

    ItemResource item = itemsFixture.basedUponSmallAngryPlanet();

    RequestBuilder requestBuilder = new RequestBuilder()
      .page()
      .withItemId(item.getId())
      .withInstanceId(item.getInstanceId())
      .withRequesterId(requesterId)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .withBlockOverrides(blockOverrides);

    return requestsClient.attemptCreate(requestBuilder, okapiHeaders);
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
    ZonedDateTime requestDate) {

    return new RequestBuilder()
      .withId(UUID.randomUUID())
      .open()
      .recall()
      .forItem(item)
      .by(requester)
      .withRequestDate(requestDate)
      .fulfilToHoldShelf()
      .withRequestExpiration(LocalDate.of(2017, 7, 30))
      .withHoldShelfExpiration(LocalDate.of(2017, 8, 31))
      .withPickupServicePointId(pickupServicePointId)
      .withTags(new RequestBuilder.Tags(asList("new", "important")));
  }

  public static ItemResource setupPagedItem(IndividualResource requestPickupServicePoint,
    ItemsFixture itemsFixture, ResourceClient requestClient, UsersFixture usersFixture) {

    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

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

    checkInFixture.checkInByBarcode(smallAngryPlanet, ClockUtil.getZonedDateTime(), requestPickupServicePoint.getId());

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
    checkInFixture.checkInByBarcode(smallAngryPlanet, ClockUtil.getZonedDateTime(), pickupServicePoint.getId());

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

  private void createAutomatedPatronBlockForUser(UUID requesterId) {
    automatedPatronBlocksFixture.blockAction(requesterId.toString(), false, false, true);
  }

  private static JsonArray getErrorsFromResponse(Response response) {
    return response.getJson().getJsonArray("errors");
  }

  private RequestBuilder buildTitleLevelRequest() {
    ItemResource itemResource = itemsFixture.basedUponSmallAngryPlanet();
    return new RequestBuilder()
      .page()
      .titleRequestLevel()
      .withNoItemId()
      .withNoHoldingsRecordId()
      .withInstanceId(itemResource.getInstanceId())
      .withRequesterId(usersFixture.charlotte().getId())
      .withRequestDate(getZonedDateTime())
      .withStatus(OPEN_NOT_YET_FILLED)
      .withPickupServicePoint(servicePointsFixture.cd1());
  }

  private RequestBuilder buildPageTitleLevelRequest(UUID patronId, UUID pickupServicePointId,
    UUID instanceId) {
    return new RequestBuilder()
      .page()
      .titleRequestLevel()
      .withInstanceId(instanceId)
      .withNoItemId()
      .withNoHoldingsRecordId()
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(patronId);
  }

  private RequestBuilder buildItemLevelRequest(UUID patronId, UUID pickupServicePointId,
    UUID instanceId, ItemResource secondItem) {

    return new RequestBuilder()
      .page()
      .itemRequestLevel()
      .withInstanceId(instanceId)
      .withItemId(secondItem.getId())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(patronId);
  }

  private void validateInstanceRepresentation(JsonObject requestInstance) {
    JsonArray contributors = requestInstance.getJsonArray("contributorNames");
    assertThat(contributors, notNullValue());
    assertThat(contributors.size(), is(1));
    assertThat(contributors.getJsonObject(0).getString("name"), is("Chambers, Becky"));

    JsonArray editions = requestInstance.getJsonArray("editions");
    assertThat(editions, Matchers.notNullValue());
    assertThat(editions.size(), is(1));
    assertThat(editions.getString(0), is("First American Edition"));

    JsonArray publication = requestInstance.getJsonArray("publication");
    assertThat(publication, Matchers.notNullValue());
    assertThat(publication.size(), is(1));
    JsonObject firstPublication = publication.getJsonObject(0);
    assertThat(firstPublication.getString("publisher"), is("Alfred A. Knopf"));
    assertThat(firstPublication.getString("place"), is("New York"));
    assertThat(firstPublication.getString("dateOfPublication"), is("2016"));
  }
}
