package api.queue;

import static api.support.fakes.PublishedEvents.byLogEventType;
import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static api.support.matchers.UUIDMatcher.is;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static java.time.ZoneOffset.UTC;
import static java.util.Arrays.asList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static org.folio.circulation.domain.representations.logs.LogEventType.REQUEST_REORDERED;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.awaitility.Awaitility;
import org.folio.circulation.support.http.client.Response;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import api.support.APITests;
import api.support.TlrFeatureStatus;
import api.support.builders.ReorderQueueBuilder;
import api.support.builders.RequestBuilder;
import api.support.fakes.FakePubSub;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class RequestQueueResourceTest extends APITests {
  private static final String REQUESTS_KEY = "requests";
  public static final String ID_PROPERTY = "id";
  public static final String POSITION_PROPERTY = "position";
  private ItemResource item;

  private List<ItemResource> items;
  private UUID instanceId;

  private IndividualResource jessica;
  private IndividualResource steve;
  private IndividualResource james;
  private IndividualResource rebecca;
  private IndividualResource charlotte;

  @BeforeEach
  public void setUp() {
    item = itemsFixture.basedUponSmallAngryPlanet();

    items = itemsFixture.createMultipleItemsForTheSameInstance(3);
    instanceId = items.get(0).getInstanceId();

    jessica = usersFixture.jessica();
    steve = usersFixture.steve();
    james = usersFixture.james();
    rebecca = usersFixture.rebecca();
    charlotte = usersFixture.charlotte();
  }

  @Test
  void validationErrorWhenTlrEnabledAndReorderingQueueForItem() {
    reconfigureTlrFeature(TlrFeatureStatus.ENABLED);

    Response response = requestQueueFixture.attemptReorderQueueForItem(
      UUID.randomUUID().toString(),
      new ReorderQueueBuilder()
        .addReorderRequest(UUID.randomUUID().toString(), 1)
        .create());

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Refuse to reorder request queue, TLR feature status is ENABLED."))));
  }

  @ParameterizedTest
  @EnumSource(value = TlrFeatureStatus.class, names = {"DISABLED", "NOT_CONFIGURED"})
  void validationErrorWhenTlrDisabledAndReorderingQueueForInstance(
    TlrFeatureStatus tlrFeatureStatus) {

    reconfigureTlrFeature(tlrFeatureStatus);

    Response response = requestQueueFixture.attemptReorderQueueForInstance(
      UUID.randomUUID().toString(),
      new ReorderQueueBuilder()
        .addReorderRequest(UUID.randomUUID().toString(), 1)
        .create());

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Refuse to reorder request queue, TLR feature status is DISABLED."))));
  }

  @Test
  void notFoundErrorWhenItemDoesNotExists() {
    Response response = requestQueueFixture.attemptReorderQueueForItem(
      UUID.randomUUID().toString(),
      new ReorderQueueBuilder()
        .addReorderRequest(UUID.randomUUID().toString(), 1)
        .create());

    assertThat(response.getStatusCode(), is(404));
    assertTrue(response.getBody()
      .matches("Item record with ID .+ cannot be found"));
  }

  @Test
  void notFoundErrorWhenInstanceDoesNotExists() {
    reconfigureTlrFeature(TlrFeatureStatus.ENABLED);

    Response response = requestQueueFixture.attemptReorderQueueForInstance(
      UUID.randomUUID().toString(),
      new ReorderQueueBuilder()
        .addReorderRequest(UUID.randomUUID().toString(), 1)
        .create());

    assertThat(response.getStatusCode(), is(404));
    assertTrue(response.getBody()
      .matches("Instance record with ID .+ cannot be found"));
  }

  @Test
  void refuseAttemptToMovePageRequestFromFirstPosition() {
    IndividualResource pageRequest = pageRequestForDefaultItem(steve);
    IndividualResource recallRequest = recallRequestForDefaultItem(jessica);

    Response response = requestQueueFixture.attemptReorderQueueForItem(
      item.getId().toString(),
      new ReorderQueueBuilder()
        .addReorderRequest(recallRequest.getId().toString(), 1)
        .addReorderRequest(pageRequest.getId().toString(), 2)
        .create());

    verifyValidationFailure(response,
      is("Page requests can not be displaced from position 1."));
  }

  @Test
  void movePageRequestFromOneOfTheTopPositionsWhenTlrEnabled() {
    reconfigureTlrFeature(TlrFeatureStatus.ENABLED);

    // It is possible to have multiple page requests in the unified queue when TLR feature is
    // enabled
    IndividualResource pageRequestBySteve = pageRequestForItem(steve, items.get(0));
    IndividualResource pageRequestByJames = pageRequestForItem(james, items.get(1));
    IndividualResource pageRequestByCharlotte = pageTitleLevelRequestForItem(charlotte, items.get(2));
    IndividualResource recallRequestByJessica = recallRequestForItem(jessica, items.get(1));

    Response response = requestQueueFixture.attemptReorderQueueForInstance(
      instanceId.toString(),
      new ReorderQueueBuilder()
        .addReorderRequest(pageRequestBySteve.getId().toString(), 1)
        .addReorderRequest(pageRequestByJames.getId().toString(), 2)
        .addReorderRequest(recallRequestByJessica.getId().toString(), 3)
        .addReorderRequest(pageRequestByCharlotte.getId().toString(), 4)
        .create());

    assertThat(response.getStatusCode(), is(200));
    assertThat(response.getJson().getJsonArray(REQUESTS_KEY).getJsonObject(3),
      hasJsonPath(ID_PROPERTY, pageRequestByCharlotte.getId().toString()));
    assertThat(response.getJson().getJsonArray(REQUESTS_KEY).getJsonObject(3),
      hasJsonPath(POSITION_PROPERTY, 4));
  }

  @Test
  void refuseAttemptToMoveRequestBeingFulfilledFromFirstPosition() {
    checkOutFixture.checkOutByBarcode(item, usersFixture.rebecca());

    IndividualResource inFulfillmentRequest = inFulfillmentRecallRequestForDefaultItem(steve);
    IndividualResource recallRequest = recallRequestForDefaultItem(jessica);

    Response response = requestQueueFixture.attemptReorderQueueForItem(
      item.getId().toString(),
      new ReorderQueueBuilder()
        .addReorderRequest(recallRequest.getId().toString(), 1)
        .addReorderRequest(inFulfillmentRequest.getId().toString(), 2)
        .create());

    verifyValidationFailure(response,
      is("Requests can not be displaced from position 1 when fulfillment begun."));
  }

  @Test
  void refuseAttemptToMoveRequestBeingFulfilledFromOneOfTheTopPositionsWhenTlrEnabled() {
    reconfigureTlrFeature(TlrFeatureStatus.ENABLED);

    checkOutFixture.checkOutByBarcode(items.get(0), usersFixture.rebecca());
    checkOutFixture.checkOutByBarcode(items.get(1), usersFixture.rebecca());
    checkOutFixture.checkOutByBarcode(items.get(2), usersFixture.rebecca());

    // It is possible to have multiple requests in fulfillment process in the unified queue when
    // TLR feature is enabled
    IndividualResource inFulfillmentRequestBySteve = inFulfillmentRecallRequestForItem(steve,
      items.get(0));
    IndividualResource inFulfillmentRequestByJames = inFulfillmentRecallRequestForItem(james,
      items.get(1));
    IndividualResource inFulfillmentRequestByCharlotte = inFulfillmentRecallRequestForItem(
      charlotte, items.get(2));
    IndividualResource recallRequestByJessica = recallRequestForItem(jessica, items.get(1));

    Response response = requestQueueFixture.attemptReorderQueueForInstance(
      instanceId.toString(),
      new ReorderQueueBuilder()
        .addReorderRequest(inFulfillmentRequestBySteve.getId().toString(), 1)
        .addReorderRequest(inFulfillmentRequestByJames.getId().toString(), 2)
        .addReorderRequest(recallRequestByJessica.getId().toString(), 3)
        .addReorderRequest(inFulfillmentRequestByCharlotte.getId().toString(), 4)
        .create());

    verifyValidationFailure(response,
      is("Requests can not be displaced from top positions when fulfillment begun."));
  }

  @Test
  void shouldGetRequestQueueForItemSuccessfully() {
    UUID facultyGroupId = patronGroupsFixture.faculty().getId();
    UUID staffGroupId = patronGroupsFixture.staff().getId();
    UUID isbnIdentifierId = identifierTypesFixture.isbn().getId();

    final ItemResource smallAngryPlanet = itemsFixture
      .basedUponSmallAngryPlanet(
        identity(),
        instanceBuilder -> instanceBuilder.addIdentifier(isbnIdentifierId, "9780866989732").withId(instanceId),
        itemBuilder -> itemBuilder
          .withCallNumber("itCn", "itCnPrefix", "itCnSuffix")
          .withEnumeration("enumeration1")
          .withChronology("chronology")
          .withVolume("vol.1")
          .withCopyNumber("1"),
        "649113164644");

    final IndividualResource sponsor = usersFixture.rebecca(user -> user.withPatronGroupId(facultyGroupId));
    final IndividualResource proxy = usersFixture.undergradHenry(user -> user.withPatronGroupId(staffGroupId));
    proxyRelationshipsFixture.nonExpiringProxyFor(sponsor, proxy);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet);

    requestsFixture.place(
      new RequestBuilder()
        .recall()
        .itemRequestLevel()
        .withRequestDate(ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC))
        .forItem(smallAngryPlanet)
        .withInstanceId(smallAngryPlanet.getInstanceId())
        .by(sponsor)
        .proxiedBy(proxy)
        .fulfillToHoldShelf()
        .withRequestExpiration(LocalDate.of(2017, 7, 30))
        .withHoldShelfExpiration(LocalDate.of(2017, 8, 31))
        .withPickupServicePointId(servicePointsFixture.cd1().getId())
        .withTags(new RequestBuilder.Tags(asList("new", "important")))
        .withPatronComments("I need the book"));

    JsonObject request = requestQueueFixture.retrieveQueueForItem(smallAngryPlanet.getId().toString()).getJsonArray("requests").getJsonObject(0);

    assertThat(request.containsKey("instance"), is(true));
    JsonObject instance = request.getJsonObject("instance");
    assertThat(instance.fieldNames(), contains("title", "identifiers", "contributorNames", "publication", "editions"));

    assertThat(request.containsKey("item"), is(true));
    JsonObject item = request.getJsonObject("item");
    assertThat(item.fieldNames(), contains("barcode", "location",
      "enumeration", "volume", "chronology", "loanTypeId", "loanTypeName", "status", "callNumber",
      "callNumberComponents", "copyNumber", "itemEffectiveLocationId",
      "itemEffectiveLocationName", "retrievalServicePointId",
      "retrievalServicePointName"));

    assertThat(request.containsKey("loan"), is(true));
    JsonObject loan = request.getJsonObject("loan");
    assertThat(loan.containsKey("dueDate"), is(true));

    assertThat(request.containsKey("requester"), is(true));
    JsonObject requester = request.getJsonObject("requester");
    assertThat(requester.fieldNames(), contains("lastName", "firstName", "barcode", "patronGroup", "patronGroupId"));

    assertThat(request.containsKey("proxy"), is(true));
    final JsonObject proxySummary = request.getJsonObject("proxy");
    assertThat(proxySummary.fieldNames(), contains("lastName", "firstName", "barcode", "patronGroup", "patronGroupId"));
    assertThat(proxySummary.getString("patronGroupId"), is(staffGroupId));
    assertThat(proxySummary.getJsonObject("patronGroup").getString("id"),
      is(staffGroupId));

    assertThat(request.containsKey("pickupServicePoint"), is(true));
    final JsonObject pickupServicePoint = request.getJsonObject("pickupServicePoint");
    assertThat(pickupServicePoint.fieldNames(), contains("name", "code", "discoveryDisplayName", "description" ,"shelvingLagTime", "pickupLocation"));
  }

  @Test
  void shouldGetRequestQueueForInstanceSuccessfully() {
    settingsFixture.enableTlrFeature();

    UUID isbnIdentifierId = identifierTypesFixture.isbn().getId();
    String isbnValue = "9780866989427";
    ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      identity(),
      instanceBuilder -> instanceBuilder.addIdentifier(isbnIdentifierId, isbnValue).withId(instanceId),
      itemsFixture.addCallNumberStringComponents());

    requestsClient.create(new RequestBuilder()
      .page()
      .titleRequestLevel()
      .withInstanceId(instanceId)
      .withNoItemId()
      .withNoHoldingsRecordId()
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .withRequestDate(ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC))
      .withRequesterId(usersFixture.steve().getId()));

    JsonObject request = requestQueueFixture.retrieveQueueForInstance(
      smallAngryPlanet.getInstanceId().toString()).getJsonArray("requests").getJsonObject(0);

    assertThat(request.containsKey("instance"), is(true));
    JsonObject instance = request.getJsonObject("instance");
    assertThat(instance.containsKey("title"), is(true));
    assertThat(instance.containsKey("identifiers"), is(true));
    assertThat(instance.containsKey("contributorNames"), is(true));
    assertThat(instance.containsKey("publication"), is(true));
    assertThat(instance.containsKey("editions"), is(true));
  }

  @ParameterizedTest
  @EnumSource(TlrFeatureStatus.class)
  void refuseAttemptToTryingToAddRequestToQueueDuringReorder(TlrFeatureStatus tlrFeatureStatus) {
    reconfigureTlrFeature(tlrFeatureStatus);

    checkOutFixture.checkOutByBarcode(item, usersFixture.rebecca());

    IndividualResource firstRecallRequest = recallRequestForDefaultItem(steve);
    IndividualResource secondRecallRequest = recallRequestForDefaultItem(jessica);

    JsonObject reorderQueueBody = new ReorderQueueBuilder()
      .addReorderRequest(secondRecallRequest.getId().toString(), 1)
      .addReorderRequest(firstRecallRequest.getId().toString(), 2)
      .addReorderRequest(UUID.randomUUID().toString(), 3)
      .create();

    Response response;
    if (tlrFeatureStatus == TlrFeatureStatus.ENABLED) {
      response = requestQueueFixture.attemptReorderQueueForInstance(
        item.getInstanceId().toString(), reorderQueueBody);
    }
    else {
      response = requestQueueFixture.attemptReorderQueueForItem(
        item.getId().toString(), reorderQueueBody);
    }

    verifyValidationFailure(response,
      is("There is inconsistency between provided reordered queue and existing queue."));
  }

  @ParameterizedTest
  @EnumSource(TlrFeatureStatus.class)
  void refuseWhenNotAllRequestsProvidedInReorderedQueue(TlrFeatureStatus tlrFeatureStatus) {
    reconfigureTlrFeature(tlrFeatureStatus);

    checkOutFixture.checkOutByBarcode(item, usersFixture.rebecca());

    holdRequestForDefaultItem(steve);

    IndividualResource secondRecallRequest = recallRequestForDefaultItem(jessica);

    JsonObject reorderQueueBody = new ReorderQueueBuilder()
      .addReorderRequest(secondRecallRequest.getId().toString(), 1)
      .create();

    Response response;
    if (tlrFeatureStatus == TlrFeatureStatus.ENABLED) {
      response = requestQueueFixture.attemptReorderQueueForInstance(
        item.getInstanceId().toString(), reorderQueueBody);
    }
    else {
      response = requestQueueFixture.attemptReorderQueueForItem(
        item.getId().toString(), reorderQueueBody);
    }

    verifyValidationFailure(response,
      is("There is inconsistency between provided reordered queue and existing queue."));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "ENABLED, 1, 2, 5, 4",
    "ENABLED, 0, 2, 3, 4",
    "ENABLED, 6, 2, 3, 1",
    "ENABLED, 70, 0, 10, 2",
    "ENABLED, 1, 3, 4, 5",
    "ENABLED, 1, 2, 4, 5",
    "ENABLED, 1, 2, 3, 5",
    "DISABLED, 1, 2, 5, 4",
    "DISABLED, 0, 2, 3, 4",
    "DISABLED, 6, 2, 3, 1",
    "DISABLED, 70, 0, 10, 2",
    "DISABLED, 1, 3, 4, 5",
    "DISABLED, 1, 2, 4, 5",
    "DISABLED, 1, 2, 3, 5",
    "NOT_CONFIGURED, 1, 2, 5, 4",
    "NOT_CONFIGURED, 0, 2, 3, 4",
    "NOT_CONFIGURED, 6, 2, 3, 1",
    "NOT_CONFIGURED, 70, 0, 10, 2",
    "NOT_CONFIGURED, 1, 3, 4, 5",
    "NOT_CONFIGURED, 1, 2, 4, 5",
    "NOT_CONFIGURED, 1, 2, 3, 5",
  })
  void refuseWhenPositionsAreNotSequential(String tlrFeatureStatusString, int firstPosition,
    int secondPosition, int thirdPosition, int fourthPosition) {

    TlrFeatureStatus tlrFeatureStatus = TlrFeatureStatus.valueOf(tlrFeatureStatusString);
    reconfigureTlrFeature(tlrFeatureStatus);

    checkOutFixture.checkOutByBarcode(item, rebecca);

    IndividualResource firstHoldRequest = holdRequestForDefaultItem(steve);
    IndividualResource secondHoldRequest = holdRequestForDefaultItem(james);
    IndividualResource firstRecallRequest = recallRequestForDefaultItem(charlotte);
    IndividualResource secondRecallRequest = recallRequestForDefaultItem(jessica);

    JsonObject reorderQueueBody = new ReorderQueueBuilder()
      .addReorderRequest(firstHoldRequest.getId().toString(), firstPosition)
      .addReorderRequest(secondHoldRequest.getId().toString(), secondPosition)
      .addReorderRequest(firstRecallRequest.getId().toString(), thirdPosition)
      .addReorderRequest(secondRecallRequest.getId().toString(), fourthPosition)
      .create();

    Response response;
    if (tlrFeatureStatus == TlrFeatureStatus.ENABLED) {
      response = requestQueueFixture.attemptReorderQueueForInstance(
        item.getInstanceId().toString(), reorderQueueBody);
    }
    else {
      response = requestQueueFixture.attemptReorderQueueForItem(
        item.getId().toString(), reorderQueueBody);
    }

    verifyValidationFailure(response, is("Positions must have sequential order."));
  }

  @ParameterizedTest
  @EnumSource(TlrFeatureStatus.class)
  void refuseAttemptToReorderRequestsWithDuplicatedPositions(TlrFeatureStatus tlrFeatureStatus) {
    reconfigureTlrFeature(tlrFeatureStatus);

    checkOutFixture.checkOutByBarcode(item, usersFixture.rebecca());

    IndividualResource holdRequest = holdRequestForDefaultItem(steve);
    IndividualResource recallRequest = recallRequestForDefaultItem(jessica);

    JsonObject reorderQueueBody = new ReorderQueueBuilder()
      .addReorderRequest(recallRequest.getId().toString(), 1)
      .addReorderRequest(holdRequest.getId().toString(), 1)
      .create();

    Response response;
    if (tlrFeatureStatus == TlrFeatureStatus.ENABLED) {
      response = requestQueueFixture.attemptReorderQueueForInstance(
        item.getInstanceId().toString(), reorderQueueBody);
    }
    else {
      response = requestQueueFixture.attemptReorderQueueForItem(
        item.getId().toString(), reorderQueueBody);
    }

    verifyValidationFailure(response, is("Positions must have sequential order."));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "ENABLED, 1, 2, 3, 4",
    "ENABLED, 1, 2, 4, 3",
    "ENABLED, 2, 1, 3, 4",
    "ENABLED, 2, 1, 4, 3",
    "ENABLED, 4, 3, 2, 1",
    "ENABLED, 4, 3, 1, 2",
    "ENABLED, 3, 4, 2, 1",
    "ENABLED, 3, 4, 1, 2",
    "DISABLED, 1, 2, 3, 4",
    "DISABLED, 1, 2, 4, 3",
    "DISABLED, 2, 1, 3, 4",
    "DISABLED, 2, 1, 4, 3",
    "DISABLED, 4, 3, 2, 1",
    "DISABLED, 4, 3, 1, 2",
    "DISABLED, 3, 4, 2, 1",
    "DISABLED, 3, 4, 1, 2",
    "NOT_CONFIGURED, 1, 2, 3, 4",
    "NOT_CONFIGURED, 1, 2, 4, 3",
    "NOT_CONFIGURED, 2, 1, 3, 4",
    "NOT_CONFIGURED, 2, 1, 4, 3",
    "NOT_CONFIGURED, 4, 3, 2, 1",
    "NOT_CONFIGURED, 4, 3, 1, 2",
    "NOT_CONFIGURED, 3, 4, 2, 1",
    "NOT_CONFIGURED, 3, 4, 1, 2",
  })
  void shouldReorderQueueSuccessfully(String tlrFeatureStatusString, int firstPosition,
    int secondPosition, int thirdPosition, int fourthPosition) {

    TlrFeatureStatus tlrFeatureStatus = TlrFeatureStatus.valueOf(tlrFeatureStatusString);
    reconfigureTlrFeature(tlrFeatureStatus);

    checkOutFixture.checkOutByBarcode(item, rebecca);

    IndividualResource firstHoldRequest = holdRequestForDefaultItem(steve);
    IndividualResource secondHoldRequest = holdRequestForDefaultItem(james);
    IndividualResource firstRecallRequest = recallRequestForDefaultItem(charlotte);
    IndividualResource secondRecallRequest = recallRequestForDefaultItem(jessica);

    JsonObject reorderQueueBody = new ReorderQueueBuilder()
      .addReorderRequest(firstHoldRequest.getId().toString(), firstPosition)
      .addReorderRequest(secondHoldRequest.getId().toString(), secondPosition)
      .addReorderRequest(firstRecallRequest.getId().toString(), thirdPosition)
      .addReorderRequest(secondRecallRequest.getId().toString(), fourthPosition)
      .create();

    JsonObject response;
    if (tlrFeatureStatus == TlrFeatureStatus.ENABLED) {
      response = requestQueueFixture.reorderQueueForInstance(
        item.getInstanceId().toString(), reorderQueueBody);
    }
    else {
      response = requestQueueFixture.reorderQueueForItem(
        item.getId().toString(), reorderQueueBody);
    }

    verifyQueueUpdatedForItem(reorderQueueBody, response);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "1, 2, 3, 4",
    "1, 2, 4, 3",
    "2, 1, 3, 4",
    "2, 1, 4, 3",
    "4, 3, 2, 1",
    "4, 3, 1, 2",
    "3, 4, 2, 1",
    "3, 4, 1, 2"
  })
  void shouldReorderUnifiedQueueWithTitleLevelRequestsSuccessfully(int firstPosition,
    int secondPosition, int thirdPosition, int fourthPosition) {

    reconfigureTlrFeature(TlrFeatureStatus.ENABLED);

    checkOutFixture.checkOutByBarcode(items.get(0), rebecca);
    checkOutFixture.checkOutByBarcode(items.get(1), rebecca);
    checkOutFixture.checkOutByBarcode(items.get(2), rebecca);

    IndividualResource holdTlrBySteve = holdTitleLevelRequest(steve);
    IndividualResource holdIlrByJames = holdRequestForItem(james, items.get(0));
    IndividualResource recallIlrByCharlotte = recallRequestForItem(charlotte, items.get(1));
    IndividualResource recallTlrByJessica = recallTitleLevelRequest(jessica);

    JsonObject reorderQueueBody = new ReorderQueueBuilder()
      .addReorderRequest(holdTlrBySteve.getId().toString(), firstPosition)
      .addReorderRequest(holdIlrByJames.getId().toString(), secondPosition)
      .addReorderRequest(recallIlrByCharlotte.getId().toString(), thirdPosition)
      .addReorderRequest(recallTlrByJessica.getId().toString(), fourthPosition)
      .create();

    JsonObject response = requestQueueFixture.reorderQueueForInstance(
      instanceId.toString(), reorderQueueBody);

    verifyQueueUpdatedForInstance(reorderQueueBody, response);
  }


  @ParameterizedTest
  @EnumSource(TlrFeatureStatus.class)
  void logRecordEventIsPublished(TlrFeatureStatus tlrFeatureStatus) {
    reconfigureTlrFeature(tlrFeatureStatus);

    checkOutFixture.checkOutByBarcode(item, rebecca);

    IndividualResource firstHoldRequest = holdRequestForDefaultItem(steve);
    IndividualResource secondHoldRequest = holdRequestForDefaultItem(james);
    IndividualResource firstRecallRequest = recallRequestForDefaultItem(charlotte);
    IndividualResource secondRecallRequest = recallRequestForDefaultItem(jessica);

    JsonObject reorderQueue = new ReorderQueueBuilder()
      .addReorderRequest(firstHoldRequest.getId().toString(), 1)
      .addReorderRequest(secondHoldRequest.getId().toString(), 4)
      .addReorderRequest(firstRecallRequest.getId().toString(), 2)
      .addReorderRequest(secondRecallRequest.getId().toString(), 3)
      .create();

    JsonObject response;
    if (tlrFeatureStatus == TlrFeatureStatus.ENABLED) {
      response = requestQueueFixture.reorderQueueForInstance(item.getInstanceId().toString(),
        reorderQueue);
    }
    else {
      response = requestQueueFixture.reorderQueueForItem(item.getId().toString(),
        reorderQueue);
    }

    verifyQueueUpdatedForItem(reorderQueue, response);

    // TODO: understand why
    int numberOfPublishedEvents = 16;
    final var publishedEvents = Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(FakePubSub::getPublishedEvents, hasSize(numberOfPublishedEvents));

    final var reorderedLogEvents = publishedEvents.filterToList(
      byLogEventType(REQUEST_REORDERED.value()));

    assertThat(reorderedLogEvents, hasSize(1));

    List<JsonObject> reorderedRequests = new JsonObject(JsonObject.mapFrom(reorderedLogEvents.get(0))
      .getString("eventPayload"))
        .getJsonObject("payload")
        .getJsonObject("requests")
        .getJsonArray("reordered")
        .stream()
        .map(o -> (JsonObject) o)
        .collect(toList());

    assertThat(reorderedRequests, hasSize(3));

    reorderedRequests.forEach(r -> {
      assertNotNull(r.getInteger("position"));
      assertNotNull(r.getInteger("previousPosition"));
      assertNotEquals(r.getInteger("position"), r.getInteger("previousPosition"));
    });
  }

  @ParameterizedTest
  @ArgumentsSource(ReorderQueueTestDataSource.class)
  void canReorderQueueTwice(Integer[] initialState, Integer[] targetState) {
    checkOutFixture.checkOutByBarcode(item, rebecca);

    IndividualResource firstHoldRequest = holdRequestForDefaultItem(steve);
    IndividualResource secondHoldRequest = holdRequestForDefaultItem(james);
    IndividualResource firstRecallRequest = recallRequestForDefaultItem(charlotte);
    IndividualResource secondRecallRequest = recallRequestForDefaultItem(jessica);

    JsonObject initialReorder = new ReorderQueueBuilder()
      .addReorderRequest(firstHoldRequest.getId().toString(), initialState[0])
      .addReorderRequest(secondHoldRequest.getId().toString(), initialState[1])
      .addReorderRequest(firstRecallRequest.getId().toString(), initialState[2])
      .addReorderRequest(secondRecallRequest.getId().toString(), initialState[3])
      .create();

    JsonObject initialReorderResponse = requestQueueFixture
      .reorderQueueForItem(item.getId().toString(), initialReorder);

    verifyQueueUpdatedForItem(initialReorder, initialReorderResponse);

    JsonObject subsequentReorder = new ReorderQueueBuilder()
      .addReorderRequest(firstHoldRequest.getId().toString(), targetState[0])
      .addReorderRequest(secondHoldRequest.getId().toString(), targetState[1])
      .addReorderRequest(firstRecallRequest.getId().toString(), targetState[2])
      .addReorderRequest(secondRecallRequest.getId().toString(), targetState[3])
      .create();

    JsonObject subsequentReorderResponse = requestQueueFixture
      .reorderQueueForItem(item.getId().toString(), subsequentReorder);

    verifyQueueUpdatedForItem(subsequentReorder, subsequentReorderResponse);
  }

  @ParameterizedTest
  @ArgumentsSource(ReorderQueueTestDataSource.class)
  void canReorderUnifiedQueueTwice(Integer[] initialState, Integer[] targetState) {
    reconfigureTlrFeature(TlrFeatureStatus.ENABLED);

    checkOutFixture.checkOutByBarcode(items.get(0), rebecca);
    checkOutFixture.checkOutByBarcode(items.get(1), rebecca);
    checkOutFixture.checkOutByBarcode(items.get(2), rebecca);

    IndividualResource holdTlrBySteve = holdTitleLevelRequest(steve);
    IndividualResource holdIlrByJames = holdRequestForItem(james, items.get(0));
    IndividualResource recallIlrByCharlotte = recallRequestForItem(charlotte, items.get(1));
    IndividualResource recallTlrByJessica = recallTitleLevelRequest(jessica);

    JsonObject initialReorder = new ReorderQueueBuilder()
      .addReorderRequest(holdTlrBySteve.getId().toString(), initialState[0])
      .addReorderRequest(holdIlrByJames.getId().toString(), initialState[1])
      .addReorderRequest(recallIlrByCharlotte.getId().toString(), initialState[2])
      .addReorderRequest(recallTlrByJessica.getId().toString(), initialState[3])
      .create();

    JsonObject initialReorderResponse = requestQueueFixture
      .reorderQueueForInstance(instanceId.toString(), initialReorder);

    verifyQueueUpdatedForInstance(initialReorder, initialReorderResponse);

    JsonObject subsequentReorder = new ReorderQueueBuilder()
      .addReorderRequest(holdTlrBySteve.getId().toString(), targetState[0])
      .addReorderRequest(holdIlrByJames.getId().toString(), targetState[1])
      .addReorderRequest(recallIlrByCharlotte.getId().toString(), targetState[2])
      .addReorderRequest(recallTlrByJessica.getId().toString(), targetState[3])
      .create();

    JsonObject subsequentReorderResponse = requestQueueFixture
      .reorderQueueForInstance(instanceId.toString(), subsequentReorder);

    verifyQueueUpdatedForInstance(subsequentReorder, subsequentReorderResponse);
  }

  private IndividualResource pageRequestForDefaultItem(IndividualResource requester) {
    return pageRequestForItem(requester, item);
  }

  private IndividualResource pageRequestForItem(IndividualResource requester, ItemResource item) {
    return requestsFixture.place(new RequestBuilder()
      .open()
      .page()
      .forItem(item)
      .by(requester)
      .withPickupServicePoint(servicePointsFixture.cd1()));
  }

  private IndividualResource pageTitleLevelRequestForItem(IndividualResource requester,
    ItemResource item) {

    return requestsFixture.place(new RequestBuilder()
      .titleRequestLevel()
      .open()
      .page()
      .withNoItemId()
      .withNoHoldingsRecordId()
      .withInstanceId(item.getInstanceId())
      .by(requester)
      .withPickupServicePoint(servicePointsFixture.cd1()));
  }

  private IndividualResource holdRequestForDefaultItem(IndividualResource requester) {
    return holdRequestForItem(requester, item);
  }

  private IndividualResource holdRequestForItem(IndividualResource requester, ItemResource item) {
    return requestsFixture.place(new RequestBuilder()
      .open()
      .hold()
      .forItem(item)
      .by(requester)
      .withPickupServicePoint(servicePointsFixture.cd1()));
  }

  private IndividualResource holdTitleLevelRequest(IndividualResource requester) {
    return requestsFixture.place(new RequestBuilder()
      .titleRequestLevel()
      .open()
      .hold()
      .withInstanceId(instanceId)
      .withNoItemId()
      .withNoHoldingsRecordId()
      .by(requester)
      .withPickupServicePoint(servicePointsFixture.cd1()));
  }

  private IndividualResource recallRequestForDefaultItem(IndividualResource requester) {
    return recallRequestForItem(requester, item);
  }

  private IndividualResource recallRequestForItem(IndividualResource requester, ItemResource item) {
    return requestsFixture.place(new RequestBuilder()
      .open()
      .recall()
      .forItem(item)
      .by(requester)
      .withPickupServicePoint(servicePointsFixture.cd1()));
  }

  private IndividualResource recallTitleLevelRequest(IndividualResource requester) {

    return requestsFixture.place(new RequestBuilder()
      .titleRequestLevel()
      .open()
      .recall()
      .withInstanceId(instanceId)
      .withNoItemId()
      .withNoHoldingsRecordId()
      .by(requester)
      .withPickupServicePoint(servicePointsFixture.cd1()));
  }

  private IndividualResource inFulfillmentRecallRequestForDefaultItem(
    IndividualResource requester) {

    return inFulfillmentRecallRequestForItem(requester, item);
  }

  private IndividualResource inFulfillmentRecallRequestForItem(
    IndividualResource requester, ItemResource item) {

    return requestsFixture.place(new RequestBuilder()
      .open()
      .recall()
      .withStatus(RequestBuilder.OPEN_IN_TRANSIT)
      .forItem(item)
      .by(requester)
      .withPickupServicePoint(servicePointsFixture.cd1()));
  }

  private void verifyQueueUpdatedForItem(JsonObject initialQueue,
    JsonObject reorderedQueue) {

    verifyQueueUpdated(false, initialQueue, reorderedQueue);
  }

  private void verifyQueueUpdatedForInstance(JsonObject initialQueue,
    JsonObject reorderedQueue) {

    verifyQueueUpdated(true, initialQueue, reorderedQueue);
  }

  private void verifyQueueUpdated(boolean forInstance, JsonObject initialQueue,
    JsonObject reorderedQueue) {

    JsonArray reorderedRequests = reorderedQueue.getJsonArray("requests");

    List<JsonObject> expectedRequests = initialQueue.getJsonArray("reorderedQueue")
      .stream()
      .map(obj -> (JsonObject) obj)
      .sorted(Comparator.comparingInt(request -> request.getInteger("newPosition")))
      .collect(Collectors.toList());

    assertEquals(expectedRequests.size(), reorderedRequests.size(),
      "Expected number of requests and actual do not match");

    assertQueue(expectedRequests, reorderedRequests);

    JsonArray requestsFromDb;
    if (forInstance) {
      requestsFromDb = requestQueueFixture
        .retrieveQueueForInstance(instanceId.toString())
        .getJsonArray("requests");
    }
    else {
      requestsFromDb = requestQueueFixture
        .retrieveQueueForItem(item.getId().toString())
        .getJsonArray("requests");
    }

    assertEquals(reorderedRequests.size(), requestsFromDb.size(),
      "Requests in DB and actual do not match");

    assertQueue(expectedRequests, requestsFromDb);
  }

  private void assertQueue(List<JsonObject> expectedQueue, JsonArray actualQueue) {
    for (int i = 0; i < expectedQueue.size(); i++) {
      JsonObject currentExpectedRequest = expectedQueue.get(i);
      JsonObject currentActualRequest = actualQueue.getJsonObject(i);

      assertEquals(currentExpectedRequest.getString("id"),
        currentActualRequest.getString("id"));
      assertEquals(currentExpectedRequest.getInteger("newPosition"),
        currentActualRequest.getInteger("position"));
    }
  }

  private void verifyValidationFailure(
    Response response, Matcher<String> errorMessageMatcher) {

    assertThat(response.getStatusCode(), is(422));

    JsonArray errors = response.getJson().getJsonArray("errors");
    assertThat(errors.size(), is(1));
    assertThat(errors.getJsonObject(0).getString("message"),
      errorMessageMatcher);
  }
}
