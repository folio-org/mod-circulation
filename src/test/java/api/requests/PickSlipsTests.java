package api.requests;

import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.time.ZoneOffset.UTC;
import static java.util.stream.Collectors.joining;
import static org.folio.circulation.domain.notice.TemplateContextUtil.CURRENT_DATE_TIME;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedStringProperty;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.collection.ArrayMatching.arrayContainingInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;


import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.vertx.core.json.JsonArray;
import org.folio.circulation.domain.CallNumberComponents;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.User;
import org.folio.circulation.storage.mappers.InstanceMapper;
import org.folio.circulation.storage.mappers.LocationMapper;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.json.JsonObjectArrayPropertyFetcher;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.Address;
import api.support.builders.RequestBuilder;
import api.support.fixtures.AddressExamples;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.ResourceClient;
import api.support.http.UserResource;
import api.support.matchers.UUIDMatcher;
import io.vertx.core.json.JsonObject;
import lombok.val;

class PickSlipsTests extends APITests {
  private static final String TOTAL_RECORDS = "totalRecords";
  private static final String PICK_SLIPS_KEY = "pickSlips";
  private static final String ITEM_KEY = "item";
  private static final String REQUEST_KEY = "request";
  private static final String REQUESTER_KEY = "requester";

  @Test
  void responseContainsNoPickSlipsForNonExistentServicePointId() {
    UUID servicePointId = servicePointsFixture.cd1().getId();
    ItemResource item = itemsFixture.basedUponSmallAngryPlanet();

    requestsFixture.place(new RequestBuilder()
      .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
      .page()
      .withPickupServicePointId(servicePointId)
      .forItem(item)
      .by(usersFixture.james()));

    Response response = ResourceClient.forPickSlips().getById(UUID.randomUUID());
    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertResponseHasItems(response, 0);
  }

  @Test
  void responseContainsNoPickSlipsForWrongServicePointId() {
    UUID servicePointId = servicePointsFixture.cd1().getId();
    ItemResource item = itemsFixture.basedUponSmallAngryPlanet();

    requestsFixture.place(new RequestBuilder()
      .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
      .page()
      .withPickupServicePointId(servicePointId)
      .forItem(item)
      .by(usersFixture.james()));

    UUID differentServicePointId = servicePointsFixture.cd2().getId();
    Response response = ResourceClient.forPickSlips()
      .getById(differentServicePointId);

    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertResponseHasItems(response, 0);
  }

  @Test
  void responseContainsNoPickSlipsWhenThereAreNoPagedItems() {
    UUID servicePointId = servicePointsFixture.cd1().getId();
    Response response = ResourceClient.forPickSlips().getById(servicePointId);

    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertResponseHasItems(response, 0);
  }

  @Test
  void responseContainsNoPickSlipsWhenItemHasOpenPageRequestWithWrongStatus() {
    UUID servicePointId = servicePointsFixture.cd1().getId();
    ItemResource item = itemsFixture.basedUponSmallAngryPlanet();

    requestsFixture.place(new RequestBuilder()
      .page()
      .withStatus(RequestStatus.OPEN_AWAITING_PICKUP.getValue())
      .withPickupServicePointId(servicePointId)
      .forItem(item)
      .by(usersFixture.james()));

    Response response = ResourceClient.forPickSlips().getById(servicePointId);

    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertResponseHasItems(response, 0);
  }

  @Test
  void responseContainsPickSlipWithAllAvailableTokens() {
    IndividualResource servicePoint = servicePointsFixture.cd1();
    UUID servicePointId = servicePoint.getId();
    IndividualResource locationResource = locationsFixture.thirdFloor();
    IndividualResource addressTypeResource = addressTypesFixture.home();
    Address address = AddressExamples.mainStreet();
    var departmentId1 = UUID.randomUUID().toString();
    var departmentId2 = UUID.randomUUID().toString();
    IndividualResource requesterResource =
      usersFixture.steve(builder -> builder.withAddress(address).withDepartments(new JsonArray(List.of(departmentId1, departmentId2))));
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
    IndividualResource requestResource = requestsFixture.place(new RequestBuilder()
      .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
      .open()
      .page()
      .withRequestDate(requestDate)
      .withRequestExpiration(requestExpiration)
      .withHoldShelfExpiration(holdShelfExpiration)
      .withPickupServicePointId(servicePointId)
      .withDeliveryAddressType(addressTypeResource.getId())
      .forItem(itemResource)
      .withPatronComments("I need the book")
      .by(requesterResource));

    Response response = ResourceClient.forPickSlips().getById(servicePointId);

    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertResponseHasItems(response, 1);

    JsonObject pickSlip = getPickSlipsList(response).get(0);
    JsonObject itemContext = pickSlip.getJsonObject(ITEM_KEY);
    assertNotNull(pickSlip.getString(CURRENT_DATE_TIME));

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
    assertEquals(ItemStatus.PAGED.getValue(), itemContext.getString("status"));
    assertEquals(item.getPrimaryContributorName(), itemContext.getString("primaryContributor"));
    assertEquals(contributorNames, itemContext.getString("allContributors"));
    assertEquals(item.getEnumeration(), itemContext.getString("enumeration"));
    assertEquals(item.getVolume(), itemContext.getString("volume"));
    assertEquals(item.getChronology(), itemContext.getString("chronology"));
    assertEquals(yearCaptionsToken, itemContext.getString("yearCaption"));
    assertEquals(materialTypeName, itemContext.getString("materialType"));
    assertEquals(loanTypeName, itemContext.getString("loanType"));
    assertEquals(copyNumber, itemContext.getString("copy"));
    assertEquals(item.getNumberOfPieces(), itemContext.getString("numberOfPieces"));
    assertEquals(item.getDescriptionOfPieces(), itemContext.getString("descriptionOfPieces"));
    assertDatetimeEquivalent(actualCheckinDateTime, requestCheckinDateTime);
    assertEquals(location.getName(), itemContext.getString("effectiveLocationSpecific"));

    CallNumberComponents callNumberComponents = item.getCallNumberComponents();
    assertEquals(callNumberComponents.getCallNumber(), itemContext.getString("callNumber"));
    assertEquals(callNumberComponents.getPrefix(), itemContext.getString("callNumberPrefix"));
    assertEquals(callNumberComponents.getSuffix(), itemContext.getString("callNumberSuffix"));

    User requester = new User(requesterResource.getJson());
    JsonObject requesterContext = pickSlip.getJsonObject("requester");

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
    assertThat(requesterContext.getString("patronGroup"), is("Regular Group"));
    assertThat(requesterContext.getString("departments").split("; "),
      arrayContainingInAnyOrder(equalTo("test department1"),equalTo("test department2")));

    JsonObject requestContext = pickSlip.getJsonObject("request");

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

    Response response = ResourceClient.forPickSlips().getById(servicePointId);

    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertResponseHasItems(response, 1);
    assertResponseContains(response, item, firstRequest, james);
  }

  @Test
  void responseIncludesItemsFromDifferentLocationsForSameServicePoint() {
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

    val james = usersFixture.james();

    val temeraireRequest = requestsFixture.place(new RequestBuilder()
      .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
      .page()
      .withPickupServicePointId(circDesk1)
      .forItem(temeraireSecondFloorCd1)
      .by(james));

    // Circ desk 1: Third floor
    val thirdFloorCd1 = locationsFixture.thirdFloor();
    val planetThirdFloorCd1 = itemsFixture.basedUponSmallAngryPlanet(
      holdingBuilder -> holdingBuilder
        .withPermanentLocation(thirdFloorCd1)
        .withNoTemporaryLocation(),
      itemBuilder -> itemBuilder
        .withNoPermanentLocation()
        .withNoTemporaryLocation());

    val charlotte = usersFixture.charlotte();

    val planetRequest = requestsFixture.place(new RequestBuilder()
      .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
      .page()
      .withPickupServicePointId(circDesk1)
      .forItem(planetThirdFloorCd1)
      .by(charlotte));

    val response = ResourceClient.forPickSlips().getById(circDesk1);

    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertResponseHasItems(response, 2);
    assertResponseContains(response, temeraireSecondFloorCd1, temeraireRequest, james);
    assertResponseContains(response, planetThirdFloorCd1, planetRequest, charlotte);
  }

  @Test
  void responseDoesNotIncludePickSlipsFromDifferentServicePoint() {
    UUID circDesk1 = servicePointsFixture.cd1().getId();
    UUID circDesk4 = servicePointsFixture.cd4().getId();

    // Circ desk 1: Third floor
    val thirdFloorCd1 = locationsFixture.thirdFloor();
    val planetThirdFloorCd1 = itemsFixture.basedUponSmallAngryPlanet(
      holdingBuilder -> holdingBuilder
        .withPermanentLocation(thirdFloorCd1)
        .withNoTemporaryLocation(),
      itemBuilder -> itemBuilder
        .withNoPermanentLocation()
        .withNoTemporaryLocation());

    val charlotte = usersFixture.charlotte();

    val requestForThirdFloorCd1 = requestsFixture.place(new RequestBuilder()
      .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
      .page()
      .withPickupServicePointId(circDesk1)
      .forItem(planetThirdFloorCd1)
      .by(charlotte));

    // Circ desk 4: Second floor
    val secondFloorCd4 = locationsFixture.fourthServicePoint();
    val planetSecondFloorCd4 = itemsFixture.basedUponSmallAngryPlanet(
      holdingBuilder -> holdingBuilder
        .withPermanentLocation(secondFloorCd4)
        .withNoTemporaryLocation(),
      itemBuilder -> itemBuilder
        .withNoPermanentLocation()
        .withNoTemporaryLocation());

    val jessica = usersFixture.jessica();

    val requestForSecondFloorCd4 = requestsFixture.place(new RequestBuilder()
      .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
      .page()
      .withPickupServicePointId(circDesk1)
      .forItem(planetSecondFloorCd4)
      .by(jessica));

    // response for Circ Desk 1
    val responseForCd1 = ResourceClient.forPickSlips().getById(circDesk1);

    assertThat(responseForCd1.getStatusCode(), is(HTTP_OK));
    assertResponseHasItems(responseForCd1, 1);
    assertResponseContains(responseForCd1, planetThirdFloorCd1, requestForThirdFloorCd1, charlotte);

    // response for Circ Desk 4
    val responseForCd4 = ResourceClient.forPickSlips().getById(circDesk4);

    assertThat(responseForCd4.getStatusCode(), is(HTTP_OK));
    assertResponseHasItems(responseForCd4, 1);
    assertResponseContains(responseForCd4, planetSecondFloorCd4, requestForSecondFloorCd4, jessica);
  }

  @Test
  void responseContainsPickSlipsWhenServicePointHasManyLocations() {
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
      .page()
      .withPickupServicePointId(servicePointId)
      .forItem(item)
      .by(james);

    val pageRequest = requestsClient.create(pageRequestBuilder);

    val response = ResourceClient.forPickSlips().getById(servicePointId);

    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertResponseHasItems(response, 1);
    assertResponseContains(response, item, pageRequest, james);
  }

  private void assertDatetimeEquivalent(ZonedDateTime firstDateTime, ZonedDateTime secondDateTime) {
    assertThat(firstDateTime.compareTo(secondDateTime), is(0));
  }

  private void assertResponseHasItems(Response response, int itemsCount) {
    JsonObject responseJson = response.getJson();
    assertThat(responseJson.getJsonArray(PICK_SLIPS_KEY).size(), is(itemsCount));
    assertThat(responseJson.getInteger(TOTAL_RECORDS), is(itemsCount));
  }

  private void assertResponseContains(Response response, ItemResource item,
    IndividualResource request, UserResource requester) {

    long count = getPickSlipsStream(response)
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

  private Stream<JsonObject> getPickSlipsStream(Response response) {
    return JsonObjectArrayPropertyFetcher.toStream(response.getJson(), PICK_SLIPS_KEY);
  }

  private List<JsonObject> getPickSlipsList(Response response) {
    return getPickSlipsStream(response)
      .collect(Collectors.toList());
  }

  private String getName(JsonObject jsonObject) {
    return jsonObject.getString("name");
  }
}
