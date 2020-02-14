package api.requests;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.stream.Collectors.joining;
import static org.folio.circulation.support.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.joda.time.DateTimeZone.UTC;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import api.support.APITests;
import api.support.builders.Address;
import api.support.builders.RequestBuilder;
import api.support.fixtures.AddressExamples;
import api.support.http.InventoryItemResource;
import api.support.http.ResourceClient;
import api.support.matchers.UUIDMatcher;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.CallNumberComponents;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.User;
import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Test;

import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.support.http.client.Response;

public class PickSlipsTests extends APITests {
  private static final String TOTAL_RECORDS = "totalRecords";
  private static final String PICK_SLIPS_KEY = "pickSlips";
  private static final String ITEM_KEY = "item";
  private static final String REQUEST_KEY = "request";
  private static final String REQUESTER_KEY = "requester";

  @Test
  public void responseContainsNoPickSlipsForNonExistentServicePointId() {
    UUID servicePointId = servicePointsFixture.cd1().getId();
    InventoryItemResource item = itemsFixture.basedUponSmallAngryPlanet();

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
  public void responseContainsNoPickSlipsForWrongServicePointId() {
    UUID servicePointId = servicePointsFixture.cd1().getId();
    InventoryItemResource item = itemsFixture.basedUponSmallAngryPlanet();

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
  public void responseContainsNoPickSlipsWhenThereAreNoPagedItems() {
    UUID servicePointId = servicePointsFixture.cd1().getId();
    Response response = ResourceClient.forPickSlips().getById(servicePointId);

    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertResponseHasItems(response, 0);
  }

  @Test
  public void responseContainsNoPickSlipsWhenItemHasOpenPageRequestWithWrongStatus() {
    UUID servicePointId = servicePointsFixture.cd1().getId();
    InventoryItemResource item = itemsFixture.basedUponSmallAngryPlanet();

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
  public void responseContainsPickSlipWithAllAvailableTokens() {
    IndividualResource servicePoint = servicePointsFixture.cd1();
    UUID servicePointId = servicePoint.getId();
    IndividualResource locationResource = locationsFixture.thirdFloor();
    IndividualResource addressTypeResource = addressTypesFixture.home();
    Address address = AddressExamples.mainStreet();
    IndividualResource requesterResource =
      usersFixture.steve(builder -> builder.withAddress(address));
    DateTime requestDate = new DateTime(2019, 7, 22, 10, 22, 54, DateTimeZone.UTC);
    LocalDate requestExpiration = new LocalDate(2019, 7, 30);
    LocalDate holdShelfExpiration = new LocalDate(2019, 8, 31);
    IndividualResource materialTypeResource = materialTypesFixture.book();
    IndividualResource loanTypeResource = loanTypesFixture.canCirculate();

    InventoryItemResource itemResource = itemsFixture.basedUponSmallAngryPlanet(
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

    DateTime now = DateTime.now(UTC);
    loansFixture.checkOutByBarcode(itemResource, requesterResource);
    loansFixture.checkInByBarcode(itemResource, now, servicePointId);
    JsonObject lastCheckIn = itemsClient.get(itemResource.getId())
      .getJson().getJsonObject("lastCheckIn");
    DateTime actualCheckinDateTime = getDateTimeProperty(lastCheckIn, "dateTime");

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
      .by(requesterResource));

    Response response = ResourceClient.forPickSlips().getById(servicePointId);

    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertResponseHasItems(response, 1);

    JsonObject pickSlip = getPickSlipsList(response).get(0);
    JsonObject itemContext = pickSlip.getJsonObject(ITEM_KEY);

    Item item = Item.from(itemResource.getJson())
      .withHoldingsRecord(itemResource.getHoldingsRecord().getJson())
      .withInstance(itemResource.getInstance().getJson());

    String contributorNames = JsonArrayHelper.toStream(item.getContributorNames())
      .map(this::getName)
      .collect(joining("; "));

    String yearCaptionsToken = String.join("; ", item.getYearCaption());
    String copyNumber = item.getCopyNumber() != null ? item.getCopyNumber() : "";
    String materialTypeName = getName(materialTypeResource.getJson());
    String loanTypeName = getName(loanTypeResource.getJson());
    Location location = Location.from(locationResource.getJson());

    assertEquals(item.getTitle(), itemContext.getString("title"));
    assertEquals(item.getBarcode(), itemContext.getString("barcode"));
    assertEquals(ItemStatus.PAGED.getValue(), itemContext.getString("status"));
    assertEquals(item.getPrimaryContributorName(),
      itemContext.getString("primaryContributor"));
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
    assertEquals(actualCheckinDateTime.toString(), itemContext.getString("lastCheckedInDateTime"));
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

    JsonObject requestContext = pickSlip.getJsonObject("request");

    assertThat(requestContext.getString("deliveryAddressType"),
      is(addressTypeResource.getJson().getString("addressType")));
    assertThat(requestContext.getString("requestExpirationDate"),
      is(requestExpiration.toDateTimeAtStartOfDay().toString()));
    assertThat(requestContext.getString("holdShelfExpirationDate"),
      is(holdShelfExpiration.toDateTimeAtStartOfDay().toString()));
    assertThat(requestContext.getString("requestID"),
      UUIDMatcher.is(requestResource.getId()));
    assertThat(requestContext.getString("servicePointPickup"),
      is(servicePoint.getJson().getString("name")));
  }

  @Test
  public void responseContainsPickSlipsForRequestsOfTypePageOnly() {
    UUID servicePointId = servicePointsFixture.cd1().getId();
    InventoryItemResource item = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();

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
  public void responseIncludesItemsFromDifferentLocationsForSameServicePoint() {
    UUID circDesk1 = servicePointsFixture.cd1().getId();

    // Circ desk 1: Second floor
    IndividualResource secondFloorCd1 = locationsFixture.secondFloorEconomics();
    final InventoryItemResource temeraireSecondFloorCd1 = itemsFixture.basedUponTemeraire(
      holdingBuilder -> holdingBuilder
        .withPermanentLocation(secondFloorCd1)
        .withNoTemporaryLocation(),
      itemBuilder -> itemBuilder
        .withNoPermanentLocation()
        .withNoTemporaryLocation());

    IndividualResource james = usersFixture.james();

    IndividualResource temeraireRequest = requestsFixture.place(new RequestBuilder()
      .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
      .page()
      .withPickupServicePointId(circDesk1)
      .forItem(temeraireSecondFloorCd1)
      .by(james));

    // Circ desk 1: Third floor
    IndividualResource thirdFloorCd1 = locationsFixture.thirdFloor();
    final InventoryItemResource planetThirdFloorCd1 = itemsFixture.basedUponSmallAngryPlanet(
      holdingBuilder -> holdingBuilder
        .withPermanentLocation(thirdFloorCd1)
        .withNoTemporaryLocation(),
      itemBuilder -> itemBuilder
        .withNoPermanentLocation()
        .withNoTemporaryLocation());

    IndividualResource charlotte = usersFixture.charlotte();

    IndividualResource planetRequest = requestsFixture.place(new RequestBuilder()
      .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
      .page()
      .withPickupServicePointId(circDesk1)
      .forItem(planetThirdFloorCd1)
      .by(charlotte));

    Response response = ResourceClient.forPickSlips().getById(circDesk1);

    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertResponseHasItems(response, 2);
    assertResponseContains(response, temeraireSecondFloorCd1, temeraireRequest, james);
    assertResponseContains(response, planetThirdFloorCd1, planetRequest, charlotte);
  }

  @Test
  public void responseDoesNotIncludePickSlipsFromDifferentServicePoint() {
    UUID circDesk1 = servicePointsFixture.cd1().getId();
    UUID circDesk4 = servicePointsFixture.cd4().getId();

    // Circ desk 1: Third floor
    IndividualResource thirdFloorCd1 = locationsFixture.thirdFloor();
    final InventoryItemResource planetThirdFloorCd1 = itemsFixture.basedUponSmallAngryPlanet(
      holdingBuilder -> holdingBuilder
        .withPermanentLocation(thirdFloorCd1)
        .withNoTemporaryLocation(),
      itemBuilder -> itemBuilder
        .withNoPermanentLocation()
        .withNoTemporaryLocation());

    IndividualResource charlotte = usersFixture.charlotte();

    IndividualResource requestForThirdFloorCd1 = requestsFixture.place(new RequestBuilder()
      .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
      .page()
      .withPickupServicePointId(circDesk1)
      .forItem(planetThirdFloorCd1)
      .by(charlotte));

    // Circ desk 4: Second floor
    IndividualResource secondFloorCd4 = locationsFixture.fourthServicePoint();
    final InventoryItemResource planetSecondFloorCd4 = itemsFixture.basedUponSmallAngryPlanet(
      holdingBuilder -> holdingBuilder
        .withPermanentLocation(secondFloorCd4)
        .withNoTemporaryLocation(),
      itemBuilder -> itemBuilder
        .withNoPermanentLocation()
        .withNoTemporaryLocation());

    IndividualResource jessica = usersFixture.jessica();

    IndividualResource requestForSecondFloorCd4 = requestsFixture.place(new RequestBuilder()
      .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
      .page()
      .withPickupServicePointId(circDesk1)
      .forItem(planetSecondFloorCd4)
      .by(jessica));

    // response for Circ Desk 1
    Response responseForCd1 = ResourceClient.forPickSlips().getById(circDesk1);
    assertThat(responseForCd1.getStatusCode(), is(HTTP_OK));
    assertResponseHasItems(responseForCd1, 1);
    assertResponseContains(responseForCd1, planetThirdFloorCd1, requestForThirdFloorCd1, charlotte);

    // response for Circ Desk 4
    Response responseForCd4 = ResourceClient.forPickSlips().getById(circDesk4);
    assertThat(responseForCd4.getStatusCode(), is(HTTP_OK));
    assertResponseHasItems(responseForCd4, 1);
    assertResponseContains(responseForCd4, planetSecondFloorCd4, requestForSecondFloorCd4, jessica);
  }

  private void assertResponseHasItems(Response response, int itemsCount) {
    JsonObject responseJson = response.getJson();
    assertThat(responseJson.getJsonArray(PICK_SLIPS_KEY).size(), is(itemsCount));
    assertThat(responseJson.getInteger(TOTAL_RECORDS), is(itemsCount));
  }

  private void assertResponseContains(Response response, InventoryItemResource item,
    IndividualResource request, IndividualResource requester) {

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
    return JsonArrayHelper.toStream(response.getJson(), PICK_SLIPS_KEY);
  }

  private List<JsonObject> getPickSlipsList(Response response) {
    return getPickSlipsStream(response)
      .collect(Collectors.toList());
  }

  private String getName(JsonObject jsonObject) {
    return jsonObject.getString("name");
  }

}
