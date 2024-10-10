package api.requests;

import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.time.ZoneOffset.UTC;
import static java.util.stream.Collectors.joining;
import static org.folio.circulation.domain.RequestType.HOLD;
import static org.folio.circulation.domain.RequestType.PAGE;
import static org.folio.circulation.domain.notice.TemplateContextUtil.CURRENT_DATE_TIME;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedStringProperty;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.collection.ArrayMatching.arrayContainingInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.folio.circulation.domain.CallNumberComponents;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.RequestTypeItemStatusWhiteList;
import org.folio.circulation.domain.User;
import org.folio.circulation.storage.mappers.InstanceMapper;
import org.folio.circulation.storage.mappers.LocationMapper;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.json.JsonObjectArrayPropertyFetcher;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import api.support.APITests;
import api.support.builders.Address;
import api.support.builders.RequestBuilder;
import api.support.fixtures.AddressExamples;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.ResourceClient;
import api.support.http.UserResource;
import api.support.matchers.UUIDMatcher;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.val;

class StaffSlipsTests extends APITests {
  private static final String TOTAL_RECORDS = "totalRecords";
  private static final String ITEM_KEY = "item";
  private static final String REQUEST_KEY = "request";
  private static final String REQUESTER_KEY = "requester";

  @ParameterizedTest
  @EnumSource(value = SlipsType.class)
  void responseContainsNoSlipsForNonExistentServicePointId(SlipsType slipsType) {
    UUID servicePointId = servicePointsFixture.cd1().getId();
    ItemResource item = itemsFixture.basedUponSmallAngryPlanet();

    requestsFixture.place(new RequestBuilder()
      .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
      .page()
      .withPickupServicePointId(servicePointId)
      .forItem(item)
      .by(usersFixture.james()));

    Response response = slipsType.get(UUID.randomUUID());
    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertResponseHasItems(response, 0, slipsType);
  }

  @ParameterizedTest
  @EnumSource(value = SlipsType.class)
  void responseContainsNoSlipsForWrongServicePointId(SlipsType slipsType) {
    UUID servicePointId = servicePointsFixture.cd1().getId();
    ItemResource item = itemsFixture.basedUponSmallAngryPlanet();

    requestsFixture.place(new RequestBuilder()
      .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
      .page()
      .withPickupServicePointId(servicePointId)
      .forItem(item)
      .by(usersFixture.james()));

    UUID differentServicePointId = servicePointsFixture.cd2().getId();
    Response response = slipsType.get(differentServicePointId);

    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertResponseHasItems(response, 0, slipsType);
  }

  @ParameterizedTest
  @EnumSource(value = SlipsType.class)
  void responseContainsNoSlipsWhenThereAreNoItems(SlipsType slipsType) {
    UUID servicePointId = servicePointsFixture.cd1().getId();
    Response response = slipsType.get(servicePointId);

    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertResponseHasItems(response, 0, slipsType);
  }

  @ParameterizedTest
  @EnumSource(value = SlipsType.class)
  void responseContainsNoPickSlipsWhenItemHasOpenRequestWithWrongStatus(SlipsType slipsType) {
    UUID servicePointId = servicePointsFixture.cd1().getId();
    ItemResource item = itemsFixture.basedUponSmallAngryPlanet();

    if (slipsType == SlipsType.SEARCH_SLIPS) {
      checkOutFixture.checkOutByBarcode(item);
    }

    requestsFixture.place(new RequestBuilder()
      .withRequestType(slipsType.getRequestType().getValue())
      .withStatus(RequestStatus.OPEN_AWAITING_PICKUP.getValue())
      .withPickupServicePointId(servicePointId)
      .forItem(item)
      .by(usersFixture.james()));

    Response response = slipsType.get(servicePointId);

    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertResponseHasItems(response, 0, slipsType);
  }

  @ParameterizedTest
  @MethodSource(value = "getAllowedStatusesForHoldRequest")
  void responseContainsSearchSlipsForItemWithAllowedStatus(ItemStatus itemStatus) {
    UUID servicePointId = servicePointsFixture.cd1().getId();
    ItemResource item = itemsFixture.basedUponNod(b -> b.withStatus(itemStatus.getValue()));

    requestsFixture.place(new RequestBuilder()
      .hold()
      .withStatus(RequestStatus.OPEN_AWAITING_PICKUP.getValue())
      .withPickupServicePointId(servicePointId)
      .forItem(item)
      .by(usersFixture.james()));

    Response response = SlipsType.SEARCH_SLIPS.get(servicePointId);

    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertResponseHasItems(response, 0, SlipsType.SEARCH_SLIPS);
  }

  private static Collection<ItemStatus> getAllowedStatusesForHoldRequest() {
    return RequestTypeItemStatusWhiteList.getItemStatusesAllowedForRequestType(HOLD)
      .stream()
      .filter(status -> status != ItemStatus.NONE)
      .toList();
  }

  @ParameterizedTest
  @CsvSource({
    "US, false, PICK_SLIPS",
    "US, false, SEARCH_SLIPS",
    ", false, PICK_SLIPS",
    ", false, SEARCH_SLIPS",
    "XX, false, PICK_SLIPS",
    "XX, false, SEARCH_SLIPS",
    "US, true, PICK_SLIPS",
    "US, true, SEARCH_SLIPS",
    ", true, PICK_SLIPS",
    ", true, SEARCH_SLIPS",
    "XX, true, PICK_SLIPS",
    "XX, true, SEARCH_SLIPS"
  })
  void responseContainsSlipWithAllAvailableTokens(String countryCode, String primaryAddress,
    String slipsTypeName) {
    SlipsType slipsType = SlipsType.valueOf(slipsTypeName);
    IndividualResource servicePoint = servicePointsFixture.cd1();
    UUID servicePointId = servicePoint.getId();
    IndividualResource locationResource = locationsFixture.thirdFloor();
    IndividualResource addressTypeResource = addressTypesFixture.home();
    Address address = AddressExamples.mainStreet(countryCode);
    var departmentId1 = UUID.randomUUID().toString();
    var departmentId2 = UUID.randomUUID().toString();
    IndividualResource requesterResource =
      usersFixture.steve(builder -> builder.withAddress(address).withDepartments(new JsonArray(List.of(departmentId1, departmentId2)))
        .withPrimaryAddress(primaryAddress));
    ZonedDateTime requestDate = ZonedDateTime.of(2019, 7, 22, 10, 22, 54, 0, UTC);
    final var requestExpiration = LocalDate.of(2019, 7, 30);
    final var holdShelfExpiration = LocalDate.of(2019, 8, 31);
    IndividualResource materialTypeResource = materialTypesFixture.book();
    IndividualResource loanTypeResource = loanTypesFixture.canCirculate();
    departmentFixture.department1(departmentId1);
    departmentFixture.department2(departmentId2);

    ItemResource itemResource = itemsFixture.basedUponSmallAngryPlanet(
      builder -> builder.withEnumeration("v.70:no.7-12")
        .withVolume("vol.1")
        .withChronology("1984:July-Dec.")
        .withYearCaption(Arrays.asList("1984", "1985"))
        .withCopyNumber("cp.2")
        .withNumberOfPieces("3")
        .withDescriptionOfPieces("Description of three pieces")
        .withPermanentLocation(locationResource)
        .withMaterialType(materialTypeResource.getId())
        .withPermanentLoanType(loanTypeResource.getId()));

    ZonedDateTime now = ClockUtil.getZonedDateTime();
    checkOutFixture.checkOutByBarcode(itemResource, requesterResource);
    checkInFixture.checkInByBarcode(itemResource, now, servicePointId);
    JsonObject lastCheckIn = itemsClient.get(itemResource.getId())
      .getJson().getJsonObject("lastCheckIn");
    ZonedDateTime actualCheckinDateTime = getDateTimeProperty(lastCheckIn, "dateTime");

    ItemStatus expectedItemStatus = ItemStatus.PAGED;
    if (slipsType == SlipsType.SEARCH_SLIPS) {
      checkOutFixture.checkOutByBarcode(itemResource);
      expectedItemStatus = ItemStatus.CHECKED_OUT;
    }

    IndividualResource requestResource = requestsFixture.place(new RequestBuilder()
      .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
      .open()
      .withRequestType(slipsType.getRequestType().getValue())
      .withRequestDate(requestDate)
      .withRequestExpiration(requestExpiration)
      .withHoldShelfExpiration(holdShelfExpiration)
      .withPickupServicePointId(servicePointId)
      .withDeliveryAddressType(addressTypeResource.getId())
      .forItem(itemResource)
      .withPatronComments("I need the book")
      .by(requesterResource));

    Response response = slipsType.get(servicePointId);

    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertResponseHasItems(response, 1, slipsType);

    JsonObject slip = getPickSlipsList(response, slipsType).get(0);
    JsonObject itemContext = slip.getJsonObject(ITEM_KEY);
    assertNotNull(slip.getString(CURRENT_DATE_TIME));

    ZonedDateTime requestCheckinDateTime = getDateTimeProperty(itemContext, "lastCheckedInDateTime");

    Item item = Item.from(itemResource.getJson())
      .withInstance(new InstanceMapper().toDomain(itemResource.getInstance().getJson()));

    String contributorNames = item.getContributorNames().collect(joining("; "));

    String yearCaptionsToken = String.join("; ", item.getYearCaption());
    String copyNumber = item.getCopyNumber() != null ? item.getCopyNumber() : "";
    String materialTypeName = getName(materialTypeResource.getJson());
    String loanTypeName = getName(loanTypeResource.getJson());
    Location location = new LocationMapper().toDomain(locationResource.getJson());

    assertEquals(item.getTitle(), itemContext.getString("title"));
    assertEquals(item.getBarcode(), itemContext.getString("barcode"));
    assertEquals(expectedItemStatus.getValue(), itemContext.getString("status"));
    assertEquals(item.getPrimaryContributorName(), itemContext.getString("primaryContributor"));
    assertEquals(contributorNames, itemContext.getString("allContributors"));
    assertEquals(item.getEnumeration(), itemContext.getString("enumeration"));
    assertEquals(item.getVolume(), itemContext.getString("volume"));
    assertEquals(item.getChronology(), itemContext.getString("chronology"));
    assertEquals(item.getDisplaySummary(), itemContext.getString("displaySummary"));
    assertEquals(yearCaptionsToken, itemContext.getString("yearCaption"));
    assertEquals(materialTypeName, itemContext.getString("materialType"));
    assertEquals(loanTypeName, itemContext.getString("loanType"));
    assertEquals(copyNumber, itemContext.getString("copy"));
    assertEquals(item.getNumberOfPieces(), itemContext.getString("numberOfPieces"));
    assertEquals(item.getDescriptionOfPieces(), itemContext.getString("descriptionOfPieces"));
    assertDatetimeEquivalent(actualCheckinDateTime, requestCheckinDateTime);
    assertEquals(location.getName(), itemContext.getString("effectiveLocationSpecific"));
    assertEquals(location.getPrimaryServicePoint().getName(), itemContext.getString("effectiveLocationPrimaryServicePointName"));
    CallNumberComponents callNumberComponents = item.getCallNumberComponents();
    assertEquals(callNumberComponents.getCallNumber(), itemContext.getString("callNumber"));
    assertEquals(callNumberComponents.getPrefix(), itemContext.getString("callNumberPrefix"));
    assertEquals(callNumberComponents.getSuffix(), itemContext.getString("callNumberSuffix"));

    User requester = new User(requesterResource.getJson());
    JsonObject requesterContext = slip.getJsonObject("requester");

    assertThat(requesterContext.getString("firstName"), is(requester.getFirstName()));
    assertThat(requesterContext.getString("lastName"), is(requester.getLastName()));
    assertThat(requesterContext.getString("middleName"), is(requester.getMiddleName()));
    assertThat(requesterContext.getString("barcode"), is(requester.getBarcode()));
    assertThat(requesterContext.getString("addressLine1"), is(address.getAddressLineOne()));
    assertThat(requesterContext.getString("addressLine2"), is(address.getAddressLineTwo()));
    assertThat(requesterContext.getString("city"), is(address.getCity()));
    assertThat(requesterContext.getString("region"), is(address.getRegion()));
    assertThat(requesterContext.getString("postalCode"), is(address.getPostalCode()));
    assertThat(requesterContext.getString("countryId"), is(address.getCountryId()));
    if(Boolean.valueOf(primaryAddress)) {
        assertThat(requesterContext.getString("primaryCountry"), is((countryCode!=null && countryCode.equalsIgnoreCase("US")) ?
          "United States" : null));
    }
    assertThat(requesterContext.getString("patronGroup"), is("Regular Group"));
    assertThat(requesterContext.getString("departments").split("; "),
      arrayContainingInAnyOrder(equalTo("test department1"),equalTo("test department2")));

    JsonObject requestContext = slip.getJsonObject("request");

    assertThat(requestContext.getString("deliveryAddressType"),
      is(addressTypeResource.getJson().getString("addressType")));
    assertThat(requestContext.getString("requestExpirationDate"),
      isEquivalentTo(requestExpiration.atTime(23, 59, 59).atZone(UTC)));
    assertThat(requestContext.getString("holdShelfExpirationDate"),
      isEquivalentTo(ZonedDateTime.of(
        holdShelfExpiration.atStartOfDay(), ZoneOffset.UTC)));
    assertThat(requestContext.getString("requestID"),
      UUIDMatcher.is(requestResource.getId()));
    assertThat(requestContext.getString("servicePointPickup"),
      is(servicePoint.getJson().getString("name")));
    assertThat(requestContext.getString("patronComments"), is("I need the book"));
    assertThat(requestContext.getString("requestDate"), isEquivalentTo(requestDate));
  }

  @Test
  void responseContainsPickSlipsForRequestsOfTypePageOnly() {
    UUID servicePointId = servicePointsFixture.cd1().getId();
    val item = itemsFixture.basedUponSmallAngryPlanet();
    val james = usersFixture.james();

    RequestBuilder firstRequestBuilder = new RequestBuilder()
      .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
      .page()
      .withPickupServicePointId(servicePointId)
      .forItem(item)
      .by(james);

    RequestBuilder secondRequestBuilder = new RequestBuilder()
      .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
      .hold()
      .withPickupServicePointId(servicePointId)
      .forItem(item)
      .by(usersFixture.steve());

    IndividualResource firstRequest = requestsClient.create(firstRequestBuilder);
    requestsClient.create(secondRequestBuilder);

    Response response = SlipsType.PICK_SLIPS.get(servicePointId);

    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertResponseHasItems(response, 1, SlipsType.PICK_SLIPS);
    assertResponseContains(response, SlipsType.PICK_SLIPS, item, firstRequest, james);
  }

  @Test
  void responseContainsSearchSlipsForRequestsOfTypeHoldOnly() {
    UUID servicePointId = servicePointsFixture.cd1().getId();
    val item = itemsFixture.basedUponSmallAngryPlanet();
    UserResource steve = usersFixture.steve();

    RequestBuilder pageRequestBuilder = new RequestBuilder()
      .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
      .page()
      .withPickupServicePointId(servicePointId)
      .forItem(item)
      .by(usersFixture.james());

    RequestBuilder holdRequestBuilder = new RequestBuilder()
      .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
      .hold()
      .withPickupServicePointId(servicePointId)
      .forItem(item)
      .by(steve);

    requestsClient.create(pageRequestBuilder);
    IndividualResource holdRequest = requestsClient.create(holdRequestBuilder);

    Response response = SlipsType.SEARCH_SLIPS.get(servicePointId);

    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertResponseHasItems(response, 1, SlipsType.SEARCH_SLIPS);
    assertResponseContains(response, SlipsType.SEARCH_SLIPS, item, holdRequest, steve);
  }

  @ParameterizedTest
  @EnumSource(value = SlipsType.class)
  void responseIncludesItemsFromDifferentLocationsForSameServicePoint(SlipsType slipsType) {
    UUID circDesk1 = servicePointsFixture.cd1().getId();

    // Circ desk 1: Second floor
    val secondFloorCd1 = locationsFixture.secondFloorEconomics();
    val temeraireSecondFloorCd1 = itemsFixture.basedUponTemeraire(
      holdingBuilder -> holdingBuilder
        .withPermanentLocation(secondFloorCd1)
        .withNoTemporaryLocation(),
      itemBuilder -> itemBuilder
        .withNoPermanentLocation()
        .withNoTemporaryLocation());

    // Circ desk 1: Third floor
    val thirdFloorCd1 = locationsFixture.thirdFloor();
    val planetThirdFloorCd1 = itemsFixture.basedUponSmallAngryPlanet(
      holdingBuilder -> holdingBuilder
        .withPermanentLocation(thirdFloorCd1)
        .withNoTemporaryLocation(),
      itemBuilder -> itemBuilder
        .withNoPermanentLocation()
        .withNoTemporaryLocation());

    val james = usersFixture.james();
    val charlotte = usersFixture.charlotte();

    if (slipsType == SlipsType.SEARCH_SLIPS) {
      checkOutFixture.checkOutByBarcode(temeraireSecondFloorCd1);
      checkOutFixture.checkOutByBarcode(planetThirdFloorCd1);
    }

    val temeraireRequest = requestsFixture.place(new RequestBuilder()
      .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
      .withRequestType(slipsType.getRequestType().getValue())
      .withPickupServicePointId(circDesk1)
      .forItem(temeraireSecondFloorCd1)
      .by(james));

    val planetRequest = requestsFixture.place(new RequestBuilder()
      .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
      .withRequestType(slipsType.getRequestType().getValue())
      .withPickupServicePointId(circDesk1)
      .forItem(planetThirdFloorCd1)
      .by(charlotte));

    val response = slipsType.get(circDesk1);

    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertResponseHasItems(response, 2, slipsType);
    assertResponseContains(response, slipsType, temeraireSecondFloorCd1, temeraireRequest, james);
    assertResponseContains(response, slipsType, planetThirdFloorCd1, planetRequest, charlotte);
  }

  @ParameterizedTest
  @EnumSource(value = SlipsType.class)
  void responseDoesNotIncludeSlipsFromDifferentServicePoint(SlipsType slipsType) {
    UUID circDesk1 = servicePointsFixture.cd1().getId();
    UUID circDesk4 = servicePointsFixture.cd4().getId();

    // Circ desk 1: Third floor
    val thirdFloorCd1 = locationsFixture.thirdFloor();
    val temeraireThirdFloorCd1 = itemsFixture.basedUponTemeraire(
      holdingBuilder -> holdingBuilder
        .withPermanentLocation(thirdFloorCd1)
        .withNoTemporaryLocation(),
      itemBuilder -> itemBuilder
        .withNoPermanentLocation()
        .withNoTemporaryLocation());

    // Circ desk 4: Second floor
    val secondFloorCd4 = locationsFixture.fourthServicePoint();
    val planetSecondFloorCd4 = itemsFixture.basedUponSmallAngryPlanet(
      holdingBuilder -> holdingBuilder
        .withPermanentLocation(secondFloorCd4)
        .withNoTemporaryLocation(),
      itemBuilder -> itemBuilder
        .withNoPermanentLocation()
        .withNoTemporaryLocation());

    if (slipsType == SlipsType.SEARCH_SLIPS) {
      checkOutFixture.checkOutByBarcode(temeraireThirdFloorCd1);
      checkOutFixture.checkOutByBarcode(planetSecondFloorCd4);
    }

    val charlotte = usersFixture.charlotte();
    val steve = usersFixture.steve();

    val requestForThirdFloorCd1 = requestsFixture.place(new RequestBuilder()
      .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
      .withRequestType(slipsType.getRequestType().getValue())
      .withPickupServicePointId(circDesk1)
      .forItem(temeraireThirdFloorCd1)
      .by(charlotte));

    val requestForSecondFloorCd4 = requestsFixture.place(new RequestBuilder()
      .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
      .withRequestType(slipsType.getRequestType().getValue())
      .withPickupServicePointId(circDesk1)
      .forItem(planetSecondFloorCd4)
      .by(steve));

    // response for Circ Desk 1
    val responseForCd1 = slipsType.get(circDesk1);

    assertThat(responseForCd1.getStatusCode(), is(HTTP_OK));
    assertResponseHasItems(responseForCd1, 1, slipsType);
    assertResponseContains(responseForCd1, slipsType, temeraireThirdFloorCd1, requestForThirdFloorCd1, charlotte);

    // response for Circ Desk 4
    val responseForCd4 = slipsType.get(circDesk4);

    assertThat(responseForCd4.getStatusCode(), is(HTTP_OK));
    assertResponseHasItems(responseForCd4, 1, slipsType);
    assertResponseContains(responseForCd4, slipsType, planetSecondFloorCd4, requestForSecondFloorCd4, steve);
  }

  @ParameterizedTest
  @EnumSource(value = SlipsType.class)
  void responseContainsSlipsWhenServicePointHasManyLocations(SlipsType slipsType) {
    final UUID servicePointId = servicePointsFixture.cd1().getId();
    final int numberOfLocations = 100;

    IndividualResource location = null;
    for (int i = 0; i < numberOfLocations; i++) {
      final int currentIndex = i;
      location = locationsFixture.basedUponExampleLocation(
        builder -> builder
          .withName("Test location " + currentIndex)
          .withCode("LOC_" + currentIndex)
          .withPrimaryServicePoint(servicePointId));
    }

    val lastLocation = location;

    val item = itemsFixture.basedUponSmallAngryPlanet(builder -> builder
      .withPermanentLocation(lastLocation.getId())
      .withNoTemporaryLocation());

    val james = usersFixture.james();

    RequestBuilder pageRequestBuilder = new RequestBuilder()
      .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
      .withRequestType(slipsType.getRequestType().getValue())
      .withPickupServicePointId(servicePointId)
      .forItem(item)
      .by(james);

    if (slipsType == SlipsType.SEARCH_SLIPS) {
      checkOutFixture.checkOutByBarcode(item);
    }

    val pageRequest = requestsClient.create(pageRequestBuilder);

    val response = slipsType.get(servicePointId);

    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertResponseHasItems(response, 1, slipsType);
    assertResponseContains(response, slipsType, item, pageRequest, james);
  }

  @Test
  void responseContainsSearchSlipsForTLR() {
    configurationsFixture.enableTlrFeature();
    var servicePointId = servicePointsFixture.cd1().getId();
    var steve = usersFixture.steve();
    var instance = instancesFixture.basedUponDunkirk();
    var location = locationsFixture.mainFloor();
    var item = buildItem(instance.getId(), location);
    checkOutFixture.checkOutByBarcode(item);
    var holdRequestBuilder = new RequestBuilder()
      .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
      .hold()
      .titleRequestLevel()
      .withNoItemId()
      .withNoHoldingsRecordId()
      .withPickupServicePointId(servicePointId)
      .withInstanceId(instance.getId())
      .by(steve);

    var holdRequest = requestsClient.create(holdRequestBuilder);
    assertThat(requestsClient.getAll(), hasSize(1));

    Response response = SlipsType.SEARCH_SLIPS.get(servicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertResponseHasItems(response, 1, SlipsType.SEARCH_SLIPS);
    assertResponseContains(response, SlipsType.SEARCH_SLIPS, holdRequest, steve);
  }

  private void assertDatetimeEquivalent(ZonedDateTime firstDateTime, ZonedDateTime secondDateTime) {
    assertThat(firstDateTime.compareTo(secondDateTime), is(0));
  }

  private void assertResponseHasItems(Response response, int itemsCount, SlipsType slipsType) {
    JsonObject responseJson = response.getJson();
    assertThat(responseJson.getJsonArray(slipsType.getCollectionName()).size(), is(itemsCount));
    assertThat(responseJson.getInteger(TOTAL_RECORDS), is(itemsCount));
  }

  private void assertResponseContains(Response response, SlipsType slipsType, ItemResource item,
    IndividualResource request, UserResource requester) {

    long count = getSlipsStream(response, slipsType)
      .filter(ps ->
        item.getBarcode().equals(
          getNestedStringProperty(ps, ITEM_KEY, "barcode"))
        && request.getId().toString().equals(
          getNestedStringProperty(ps, REQUEST_KEY, "requestID"))
        && requester.getBarcode().equals(
          getNestedStringProperty(ps, REQUESTER_KEY, "barcode")))
      .count();

    if (count == 0) {
      fail("Response does not contain a pick slip with expected combination" +
        " of item, request and requester");
    }

    if (count > 1) {
      fail("Response contains multiple pick slips with expected combination" +
        " of item, request and requester: " + count);
    }
  }

  private void assertResponseContains(Response response, SlipsType slipsType,
    IndividualResource request, UserResource requester) {

    long count = getSlipsStream(response, slipsType)
      .filter(ps ->
        request.getId().toString().equals(
          getNestedStringProperty(ps, REQUEST_KEY, "requestID"))
          && requester.getBarcode().equals(
            getNestedStringProperty(ps, REQUESTER_KEY, "barcode")))
      .count();

    if (count == 0) {
      fail("Response does not contain a pick slip with expected combination" +
        " of item, request and requester");
    }

    if (count > 1) {
      fail("Response contains multiple pick slips with expected combination" +
        " of item, request and requester: " + count);
    }
  }

  private Stream<JsonObject> getSlipsStream(Response response, SlipsType slipsType) {
    return JsonObjectArrayPropertyFetcher.toStream(response.getJson(), slipsType.getCollectionName());
  }

  private List<JsonObject> getPickSlipsList(Response response, SlipsType slipsType) {
    return getSlipsStream(response, slipsType)
      .collect(Collectors.toList());
  }

  private String getName(JsonObject jsonObject) {
    return jsonObject.getString("name");
  }

  private ItemResource buildItem(UUID instanceId, IndividualResource location) {
    UUID isbnIdentifierId = identifierTypesFixture.isbn().getId();

    return itemsFixture.basedUponSmallAngryPlanet(
      holdingBuilder -> holdingBuilder.forInstance(instanceId)
        .withEffectiveLocationId(location.getId()),
      instanceBuilder -> instanceBuilder
        .addIdentifier(isbnIdentifierId, "9780866989732")
        .withId(instanceId),
      itemBuilder -> itemBuilder.withBarcode("test")
        .withMaterialType(materialTypesFixture.book().getId()));
  }

  @AllArgsConstructor
  private enum SlipsType {
    PICK_SLIPS(ResourceClient.forPickSlips(), "pickSlips", PAGE),
    SEARCH_SLIPS(ResourceClient.forSearchSlips(), "searchSlips", HOLD);

    private final ResourceClient client;
    @Getter
    private final String collectionName;
    @Getter
    private final RequestType requestType;

    private Response get(UUID servicePointId) {
      return client.getById(servicePointId);
    }

  }
}
