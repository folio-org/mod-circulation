package api.requests;

import static api.support.JsonCollectionAssistant.getRecordById;
import static api.support.matchers.ItemMatchers.isInTransit;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static java.time.ZoneOffset.UTC;
import static org.folio.circulation.support.StreamToListMapper.toList;
import static org.folio.circulation.support.json.JsonStringArrayPropertyFetcher.toStream;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.services.ItemsInTransitReportService;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.json.JsonPropertyFetcher;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.ItemBuilder;
import api.support.builders.RequestBuilder;
import api.support.fixtures.ItemExamples;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class ItemsInTransitReportTests extends APITests {
  private static final String NAME = "name";
  private static final String CODE = "code";
  private static final String LIBRARY = "libraryName";
  private static final String STATUS_KEY = "status";
  private static final String BARCODE_KEY = "barcode";
  private static final String TITLE = "title";
  private static final String CONTRIBUTORS = "contributors";
  private static final String DESTINATION_SERVICE_POINT = "inTransitDestinationServicePointId";
  private static final String REQUEST_TYPE = "requestType";
  private static final String REQUEST_CREATION_DATE = "requestDate";
  private static final String REQUEST_EXPIRATION_DATE = "requestExpirationDate";
  private static final String REQUEST_PICKUP_SERVICE_POINT_NAME = "requestPickupServicePointName";
  private static final String REQUEST_PATRON_GROUP = "requestPatronGroup";
  private static final String TAGS = "tags";
  private static final String CHECK_IN_SERVICE_POINT = "checkInServicePoint";
  private static final String CHECK_IN_DATE_TIME = "checkInDateTime";
  private static final String DISCOVERY_DISPLAY_NAME = "discoveryDisplayName";
  private static final String PICKUP_LOCATION = "pickupLocation";
  private static final String REQUEST = "request";
  private static final String CALL_NUMBER = "callNumber";
  private static final String ITEM_LEVEL_CALL_NUMBER = "itemLevelCallNumber";
  private static final String ENUMERATION = "enumeration";
  private static final String VOLUME = "volume";
  private static final String YEAR_CAPTION = "yearCaption";
  private static final String SERVICE_POINT_NAME_1 = "Circ Desk 1";
  private static final String SERVICE_POINT_NAME_2 = "Circ Desk 2";
  private static final String REQUEST_PATRON_GROUP_DESCRIPTION = "Regular group";
  private static final String SERVICE_POINT_CODE_2 = "cd2";
  private static final String COPY_NUMBER = "copyNumber";
  private static final String EFFECTIVE_CALL_NUMBER_COMPONENTS = "effectiveCallNumberComponents";

  @AfterEach
  public void afterEach() {
    mockClockManagerToReturnDefaultDateTime();
  }

  @Test
  void reportIsEmptyWhenThereAreNoItemsInTransit() {
    List<JsonObject> items = ResourceClient.forItemsInTransitReport().getAll();

    assertTrue(items.isEmpty());
  }

  @Test
  void reportIncludesItemInTransit() {
    final ItemResource smallAngryPlanet = createSmallAngryPlanet();

    final IndividualResource steve = usersFixture.steve();
    final UUID firstServicePointId = servicePointsFixture.cd1().getId();
    final UUID secondServicePointId = servicePointsFixture.cd2().getId();
    final ZonedDateTime checkInDate = ZonedDateTime.of(2019, 8, 13, 5, 0, 0, 0, UTC);
    final ZonedDateTime requestDate = ZonedDateTime.of(2019, 7, 5, 10, 0, 0, 0, UTC);
    final LocalDate requestExpirationDate = LocalDate.of(2019, 7, 11);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet);
    createRequest(smallAngryPlanet, steve, secondServicePointId, requestDate, requestExpirationDate);

    mockClockManagerToReturnFixedDateTime(checkInDate);
    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(smallAngryPlanet)
      .on(checkInDate)
      .at(firstServicePointId));

    List<JsonObject> items = ResourceClient.forItemsInTransitReport().getAll();

    assertThat(items.size(), is(1));
    JsonObject itemJson = items.get(0);
    verifyItem(itemJson, smallAngryPlanet, secondServicePointId);
    verifyLocation(itemJson);
    verifyRequestWithSecondPickupServicePoint(itemJson, requestDate, requestExpirationDate);
    verifyLoanInFirstServicePoint(itemJson, checkInDate);
    verifyLastCheckIn(itemJson, checkInDate, SERVICE_POINT_NAME_1);
  }

  @Test
  void reportIncludesMultipleDifferentItemsInTransit() {
    final ItemResource smallAngryPlanet = createSmallAngryPlanet();
    final ItemResource nod = createNod();

    final IndividualResource steve = usersFixture.steve();
    final IndividualResource rebecca = usersFixture.rebecca();

    final UUID firsServicePointId = servicePointsFixture.cd1().getId();
    final UUID secondServicePointId = servicePointsFixture.cd2().getId();

    final ZonedDateTime checkInDate1 = ZonedDateTime.of(2019, 8, 13, 5, 0, 0, 0, UTC);
    final ZonedDateTime checkInDate2 = ZonedDateTime.of(2019, 4, 3, 2, 10, 0, 0, UTC);
    final ZonedDateTime requestDate1 = ZonedDateTime.of(2019, 7, 5, 10, 0, 0, 0, UTC);
    final LocalDate requestExpirationDate1 = LocalDate.of(2019, 7, 11);

    final ZonedDateTime requestDate2 = ZonedDateTime.of(2019, 10, 8, 11, 0, 0, 0, UTC);
    final LocalDate requestExpirationDate2 = LocalDate.of(2020, 1, 12);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet);
    checkOutFixture.checkOutByBarcode(nod);

    createRequest(smallAngryPlanet, steve, secondServicePointId, requestDate1, requestExpirationDate1);
    createRequest(nod, rebecca, secondServicePointId, requestDate2, requestExpirationDate2);

    mockClockManagerToReturnFixedDateTime(checkInDate1);
    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(smallAngryPlanet)
      .on(checkInDate1)
      .at(firsServicePointId));

    mockClockManagerToReturnFixedDateTime(checkInDate2);
    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(nod)
      .on(checkInDate2)
      .at(firsServicePointId));

    List<JsonObject> items = ResourceClient.forItemsInTransitReport().getAll();

    assertThat(items.size(), is(2));
    JsonObject firstItemJson = getRecordById(items, smallAngryPlanet.getId()).get();
    verifyItem(firstItemJson, smallAngryPlanet, secondServicePointId);
    verifyLocation(firstItemJson);
    verifyRequestWithSecondPickupServicePoint(firstItemJson, requestDate1, requestExpirationDate1);
    verifyLoanInFirstServicePoint(firstItemJson, checkInDate1);
    verifyLastCheckIn(firstItemJson, checkInDate1, SERVICE_POINT_NAME_1);

    JsonObject secondItemJson = getRecordById(items, nod.getId()).get();
    verifyItem(secondItemJson, nod, secondServicePointId);
    verifyLocation(secondItemJson);
    verifyRequest(secondItemJson, requestDate2, requestExpirationDate2,
      SERVICE_POINT_NAME_2);
    verifyLoanInFirstServicePoint(secondItemJson, checkInDate2);
    verifyLastCheckIn(secondItemJson, checkInDate2, SERVICE_POINT_NAME_1);
  }

  @Test
  void reportExcludesItemsThatAreNotInTransit() {
    final ItemResource smallAngryPlanet = createSmallAngryPlanet();
    final ItemResource nod = createNod();

    final ZonedDateTime checkInDate = ZonedDateTime.of(2019, 8, 13, 5, 0, 0, 0, UTC);
    final ZonedDateTime requestDate = ZonedDateTime.of(2019, 7, 5, 10, 0, 0, 0, UTC);
    final LocalDate requestExpirationDate = LocalDate.of(2019, 7, 11);

    final IndividualResource steve = usersFixture.steve();
    final UUID firstServicePointId = servicePointsFixture.cd1().getId();
    final UUID secondServicePointId = servicePointsFixture.cd2().getId();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet);
    checkOutFixture.checkOutByBarcode(nod);

    createRequest(smallAngryPlanet, steve, secondServicePointId, requestDate, requestExpirationDate);

    mockClockManagerToReturnFixedDateTime(checkInDate);
    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(smallAngryPlanet)
      .on(checkInDate)
      .at(firstServicePointId));

    List<JsonObject> items = ResourceClient.forItemsInTransitReport().getAll();

    assertThat(items.size(), is(1));
    JsonObject itemJson = items.get(0);
    verifyItem(itemJson, smallAngryPlanet, secondServicePointId);
    verifyLocation(itemJson);
    verifyRequestWithSecondPickupServicePoint(itemJson, requestDate, requestExpirationDate);
    verifyLoanInFirstServicePoint(itemJson, checkInDate);
    verifyLastCheckIn(itemJson, checkInDate, SERVICE_POINT_NAME_1);
  }

  @Test
  void reportIncludesItemsInTransitToDifferentServicePoints() {
    final ItemResource smallAngryPlanet = createSmallAngryPlanet();
    final ItemResource nod = createNod();

    final IndividualResource steve = usersFixture.steve();
    final IndividualResource rebecca = usersFixture.rebecca();

    final UUID firstServicePointId = servicePointsFixture.cd1().getId();
    final UUID secondServicePointId = servicePointsFixture.cd2().getId();

    final ZonedDateTime checkInDate1 = ZonedDateTime.of(2019, 8, 13, 5, 0, 0, 0, UTC);
    final ZonedDateTime checkInDate2 = ZonedDateTime.of(2019, 4, 3, 2, 10, 0, 0, UTC);
    final ZonedDateTime requestDate1 = ZonedDateTime.of(2019, 7, 5, 10, 0, 0, 0, UTC);
    final ZonedDateTime requestDate2 = ZonedDateTime.of(2019, 10, 8, 11, 0, 0, 0, UTC);
    final LocalDate requestExpirationDate1 = LocalDate.of(2019, 7, 11);
    final LocalDate requestExpirationDate2 = LocalDate.of(2020, 1, 12);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet);
    checkOutFixture.checkOutByBarcode(nod);

    createRequest(smallAngryPlanet, steve, firstServicePointId, requestDate1, requestExpirationDate1);
    createRequest(nod, rebecca, secondServicePointId, requestDate2, requestExpirationDate2);

    mockClockManagerToReturnFixedDateTime(checkInDate1);
    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(smallAngryPlanet)
      .on(checkInDate1)
      .at(secondServicePointId));

    mockClockManagerToReturnFixedDateTime(checkInDate2);
    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(nod)
      .on(checkInDate2)
      .at(firstServicePointId));

    List<JsonObject> items = ResourceClient.forItemsInTransitReport().getAll();

    assertThat(items.size(), is(2));
    JsonObject firstItemJson = getRecordById(items, smallAngryPlanet.getId()).get();
    verifyItem(firstItemJson, smallAngryPlanet, firstServicePointId);
    verifyLocation(firstItemJson);
    verifyRequest(firstItemJson, requestDate1, requestExpirationDate1,
      SERVICE_POINT_NAME_1);
    verifyLoan(firstItemJson, checkInDate1, SERVICE_POINT_NAME_2,
      SERVICE_POINT_CODE_2, "Circulation Desk -- Back Entrance");
    verifyLastCheckIn(firstItemJson, checkInDate1, SERVICE_POINT_NAME_2);

    JsonObject secondItemJson = getRecordById(items, nod.getId()).get();
    verifyItem(secondItemJson, nod, secondServicePointId);
    verifyLocation(secondItemJson);
    verifyRequest(secondItemJson, requestDate2, requestExpirationDate2,
      SERVICE_POINT_NAME_2);
    verifyLoanInFirstServicePoint(secondItemJson, checkInDate2);
    verifyLastCheckIn(secondItemJson, checkInDate2, SERVICE_POINT_NAME_1);
  }

  @Test
  void reportIncludesItemsInTransitWithMoreThanOneOpenRequestInQueue() {
    final ItemResource smallAngryPlanet = createSmallAngryPlanet();
    final ItemResource nod = createNod();

    final IndividualResource steve = usersFixture.steve();
    final IndividualResource rebecca = usersFixture.rebecca();

    final UUID firstServicePointId = servicePointsFixture.cd1().getId();
    final UUID secondServicePointId = servicePointsFixture.cd2().getId();

    final ZonedDateTime checkInDate1 = ZonedDateTime.of(2019, 8, 13, 5, 0, 0, 0, UTC);
    final ZonedDateTime checkInDate2 = ZonedDateTime.of(2019, 4, 3, 2, 10, 0, 0, UTC);

    final ZonedDateTime requestSmallAngryPlanetDate1 = ZonedDateTime.of(2019, 7, 5, 10, 0, 0, 0, UTC);
    final ZonedDateTime requestSmallAngryPlanetDate2 = ZonedDateTime.of(2019, 10, 1, 12, 0, 0, 0, UTC);
    final LocalDate requestSmallAngryPlanetExpirationDate1 = LocalDate.of(2019, 7, 11);
    final LocalDate requestSmallAngryPlanetExpirationDate2 = LocalDate.of(2019, 11, 12);

    final ZonedDateTime requestNodeDate1 = ZonedDateTime.of(2019, 5, 11, 1, 0, 0, 0, UTC);
    final ZonedDateTime requestNodeDate2 = ZonedDateTime.of(2019, 10, 8, 11, 0, 0, 0, UTC);
    final LocalDate requestNodeExpirationDate1 = LocalDate.of(2020, 1, 12);
    final LocalDate requestNodeExpirationDate2 = LocalDate.of(2020, 10, 10);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet);
    checkOutFixture.checkOutByBarcode(nod);

    createRequest(smallAngryPlanet, steve, firstServicePointId, requestSmallAngryPlanetDate1, requestSmallAngryPlanetExpirationDate1);
    createRequest(smallAngryPlanet, rebecca, firstServicePointId, requestSmallAngryPlanetDate2, requestSmallAngryPlanetExpirationDate2);

    createRequest(nod, rebecca, secondServicePointId, requestNodeDate1, requestNodeExpirationDate1);
    createRequest(nod, steve, secondServicePointId, requestNodeDate2, requestNodeExpirationDate2);

    mockClockManagerToReturnFixedDateTime(checkInDate1);
    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(smallAngryPlanet)
      .on(checkInDate1)
      .at(secondServicePointId));

    mockClockManagerToReturnFixedDateTime(checkInDate2);
    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(nod)
      .on(checkInDate2)
      .at(firstServicePointId));

    List<JsonObject> items = ResourceClient.forItemsInTransitReport().getAll();

    assertThat(items.size(), is(2));
    JsonObject firstItemJson = getRecordById(items, smallAngryPlanet.getId()).get();
    verifyItem(firstItemJson, smallAngryPlanet, firstServicePointId);
    verifyLocation(firstItemJson);
    verifyRequest(firstItemJson, requestSmallAngryPlanetDate1, requestSmallAngryPlanetExpirationDate1,
      SERVICE_POINT_NAME_1);
    verifyLoan(firstItemJson, checkInDate1, SERVICE_POINT_NAME_2,
      SERVICE_POINT_CODE_2, "Circulation Desk -- Back Entrance");
    verifyLastCheckIn(firstItemJson, checkInDate1, SERVICE_POINT_NAME_2);

    JsonObject secondItemJson = getRecordById(items, nod.getId()).get();
    verifyItem(secondItemJson, nod, secondServicePointId);
    verifyLocation(secondItemJson);
    verifyRequest(secondItemJson, requestNodeDate1, requestNodeExpirationDate1,
      SERVICE_POINT_NAME_2);
    verifyLoanInFirstServicePoint(secondItemJson, checkInDate2);
    verifyLastCheckIn(secondItemJson, checkInDate2, SERVICE_POINT_NAME_1);
  }

  @Test
  void reportIncludesItemsInTransitWithEmptyRequestQueue() {
    final ItemResource smallAngryPlanet = createSmallAngryPlanet();
    final ItemResource nod = createNod();

    final UUID firsServicePointId = servicePointsFixture.cd1().getId();
    final UUID secondServicePointId = servicePointsFixture.cd2().getId();

    final ZonedDateTime checkInDate1 = ZonedDateTime.of(2019, 8, 13, 5, 0, 0, 0, UTC);
    final ZonedDateTime checkInDate2 = ZonedDateTime.of(2019, 4, 3, 2, 10, 0, 0, UTC);

    final String checkInServicePointDiscoveryName = "Circulation Desk -- Back Entrance";

    checkOutFixture.checkOutByBarcode(smallAngryPlanet);
    checkOutFixture.checkOutByBarcode(nod);

    mockClockManagerToReturnFixedDateTime(checkInDate1);
    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(smallAngryPlanet)
      .on(checkInDate1)
      .at(secondServicePointId));

    mockClockManagerToReturnFixedDateTime(checkInDate2);
    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(nod)
      .on(checkInDate2)
      .at(secondServicePointId));

    List<JsonObject> items = ResourceClient.forItemsInTransitReport().getAll();

    assertThat(items.size(), is(2));
    JsonObject firstItemJson = getRecordById(items, smallAngryPlanet.getId()).get();
    verifyItem(firstItemJson, smallAngryPlanet, firsServicePointId);
    verifyLocation(firstItemJson);
    assertNull(firstItemJson.getMap().get(REQUEST));
    verifyLoan(firstItemJson, checkInDate1, SERVICE_POINT_NAME_2, SERVICE_POINT_CODE_2, checkInServicePointDiscoveryName);
    verifyLastCheckIn(firstItemJson, checkInDate1, SERVICE_POINT_NAME_2);

    JsonObject secondItemJson = getRecordById(items, nod.getId()).get();
    verifyItem(secondItemJson, nod, firsServicePointId);
    verifyLocation(secondItemJson);
    assertNull(secondItemJson.getMap().get(REQUEST));
    verifyLoan(secondItemJson, checkInDate2, SERVICE_POINT_NAME_2, SERVICE_POINT_CODE_2, checkInServicePointDiscoveryName);
    verifyLastCheckIn(secondItemJson, checkInDate2, SERVICE_POINT_NAME_2);
  }

  @Test
  void reportItemsInTransitSortedByCheckInServicePoint() {
    final ItemResource smallAngryPlanet = createSmallAngryPlanet();
    final ItemResource nod = createNod();
    final ItemResource smallAngryPlanetWithFourthCheckInServicePoint = itemsFixture
      .basedUponSmallAngryPlanet(createSmallAngryPlanetItemBuilder()
        .withBarcode("34"), itemsFixture.thirdFloorHoldings());

    final IndividualResource steve = usersFixture.steve();
    final IndividualResource rebecca = usersFixture.rebecca();

    final UUID firstServicePointId = servicePointsFixture.cd1().getId();
    final UUID secondServicePointId = servicePointsFixture.cd2().getId();
    final UUID fourthServicePointId = servicePointsFixture.cd4().getId();
    final ZonedDateTime checkInDate1 = ZonedDateTime.of(2019, 8, 13, 5, 0, 0, 0, UTC);
    final ZonedDateTime checkInDate2 = ZonedDateTime.of(2019, 4, 3, 2, 10, 0, 0, UTC);
    final ZonedDateTime checkInDate3 = ZonedDateTime.of(2019, 10, 10, 3, 0, 0, 0, UTC);
    final ZonedDateTime requestDate1 = ZonedDateTime.of(2019, 7, 5, 10, 0, 0, 0, UTC);
    final ZonedDateTime requestDate2 = ZonedDateTime.of(2019, 10, 8, 11, 0, 0, 0, UTC);
    final LocalDate requestExpirationDate1 = LocalDate.of(2019, 7, 11);
    final LocalDate requestExpirationDate2 = LocalDate.of(2020, 1, 12);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet);
    checkOutFixture.checkOutByBarcode(nod);
    checkOutFixture.checkOutByBarcode(smallAngryPlanetWithFourthCheckInServicePoint);

    createRequest(smallAngryPlanet, steve, firstServicePointId, requestDate1, requestExpirationDate1);
    createRequest(nod, rebecca, secondServicePointId, requestDate2, requestExpirationDate2);

    mockClockManagerToReturnFixedDateTime(checkInDate3);
    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(smallAngryPlanetWithFourthCheckInServicePoint)
      .on(checkInDate3)
      .at(fourthServicePointId));

    mockClockManagerToReturnFixedDateTime(checkInDate2);
    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(nod)
      .on(checkInDate2)
      .at(firstServicePointId));

    mockClockManagerToReturnFixedDateTime(checkInDate1);
    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(smallAngryPlanet)
      .on(checkInDate1)
      .at(secondServicePointId));

    List<JsonObject> items = ResourceClient.forItemsInTransitReport().getAll();

    assertThat(items.size(), is(3));

    JsonObject firstItemJson = items.get(0);
    verifyItem(firstItemJson, nod, secondServicePointId);
    verifyLocation(firstItemJson);
    verifyRequest(firstItemJson, requestDate2, requestExpirationDate2,
      SERVICE_POINT_NAME_2);
    verifyLoanInFirstServicePoint(firstItemJson, checkInDate2);
    verifyLastCheckIn(firstItemJson, checkInDate2, SERVICE_POINT_NAME_1);

    JsonObject secondItemJson = items.get(1);
    verifyItem(secondItemJson, smallAngryPlanet, firstServicePointId);
    verifyLocation(secondItemJson);
    verifyRequest(secondItemJson, requestDate1, requestExpirationDate1,
      SERVICE_POINT_NAME_1);
    verifyLoan(secondItemJson, checkInDate1, SERVICE_POINT_NAME_2,
      SERVICE_POINT_CODE_2, "Circulation Desk -- Back Entrance");
    verifyLastCheckIn(secondItemJson, checkInDate1, SERVICE_POINT_NAME_2);

    JsonObject thirdItemJson = items.get(2);
    verifyItem(thirdItemJson, smallAngryPlanetWithFourthCheckInServicePoint, firstServicePointId);
    verifyLocation(thirdItemJson);
    verifyLoan(thirdItemJson, checkInDate3, "Circ Desk 4",
      "cd4", "Circulation Desk -- Basement");
    verifyLastCheckIn(thirdItemJson, checkInDate3, "Circ Desk 4");
  }

  @Test
  void reportWillNotFailWithUriTooLargeError() {
    final UUID firstServicePointId = servicePointsFixture.cd1().getId();
    final UUID forthServicePointLocationId = locationsFixture.fourthServicePoint().getId();

    for (int i = 0; i < 200; i++) {
      ItemResource item = createSmallAngryPlanetCopy(forthServicePointLocationId,
        Integer.toString(i));

      checkOutFixture.checkOutByBarcode(item);

      checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
        .forItem(item)
        .on(ClockUtil.getZonedDateTime())
        .at(firstServicePointId));
    }

    List<JsonObject> itemsInTransitReport = ResourceClient.forItemsInTransitReport().getAll();

    assertThat(itemsInTransitReport.size(), is(200));
  }

  @Test
  void reportShouldNotFailWithoutLastCheckInServicePointId() {
    ItemResource item = checkOutAndCheckInItem(servicePointsFixture.cd1().getId());

    Response response = itemsClient.getById(item.getId());
    JsonObject checkedInItemJson = response.getJson();
    checkedInItemJson.getJsonObject("lastCheckIn").remove("servicePointId");
    itemsClient.replace(item.getId(), checkedInItemJson);

    List<JsonObject> itemsInTransitReport = ResourceClient.forItemsInTransitReport().getAll();

    assertThat(itemsInTransitReport.size(), is(1));
  }

  @Test
  void reportShouldNotFailWithoutPrimaryServicePointId() {
    ItemResource item = checkOutAndCheckInItem(servicePointsFixture.cd1().getId());

    Response response = itemsClient.getById(item.getId());
    JsonObject checkedInItemJson = response.getJson();
    UUID permanentLocationId = UUID.fromString(checkedInItemJson.getString("permanentLocationId"));
    JsonObject location = locationsClient.getById(permanentLocationId).getJson();
    location.putNull("primaryServicePoint");
    locationsClient.replace(permanentLocationId, location);

    List<JsonObject> itemsInTransitReport = ResourceClient.forItemsInTransitReport().getAll();

    assertThat(itemsInTransitReport.size(), is(1));
  }

  @Test
  void reportShouldNotFailWithoutLastCheckIn() {
    ItemResource item = checkOutAndCheckInItem(servicePointsFixture.cd1().getId());

    Response response = itemsClient.getById(item.getId());
    JsonObject checkedInItemJson = response.getJson();
    checkedInItemJson.remove("lastCheckIn");
    itemsClient.replace(item.getId(), checkedInItemJson);

    List<JsonObject> itemsInTransitReport = ResourceClient.forItemsInTransitReport().getAll();

    assertThat(itemsInTransitReport.size(), is(1));
  }

  @Test
  void reportShouldNotFailWithNonExistentServicePoint() {
    checkOutAndCheckInItem(UUID.randomUUID());
    List<JsonObject> itemsInTransitReport = ResourceClient.forItemsInTransitReport().getAll();

    assertThat(itemsInTransitReport.size(), is(1));
  }

  @Test
  void reportBuildShouldFailAndLogTheError() {
    new ItemsInTransitReportService(null, null, null, null, null, null, null, null, null)
      .buildReport();
    List<JsonObject> itemsInTransitReport = ResourceClient.forItemsInTransitReport().getAll();
    assertThat(itemsInTransitReport.size(), is(0));
  }

  private ItemResource checkOutAndCheckInItem(UUID checkInServicePointId) {
    final UUID forthServicePointLocationId = locationsFixture.fourthServicePoint().getId();

    ItemResource item = createSmallAngryPlanetCopy(forthServicePointLocationId, "111");

    checkOutFixture.checkOutByBarcode(item);
    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(item)
      .at(checkInServicePointId));

    assertThat(itemsClient.getById(item.getId()).getJson(), isInTransit());

    return item;
  }

  private void createRequest(ItemResource item, IndividualResource steve,
    UUID secondServicePointId, ZonedDateTime requestDate, LocalDate requestExpirationDate) {

    RequestBuilder.Tags tags = new RequestBuilder.Tags(Arrays.asList("tag1", "tag2"));
    RequestBuilder secondRequestBuilderOnItem = new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(secondServicePointId)
      .forItem(item)
      .withTags(tags)
      .withRequestDate(requestDate)
      .withRequestExpiration(requestExpirationDate)
      .by(steve);

    requestsClient.create(secondRequestBuilderOnItem);
  }

  private void verifyItem(JsonObject itemJson, ItemResource item,
    UUID secondServicePointId) {

    assertThat(itemJson.getString(BARCODE_KEY), is(item.getBarcode()));
    assertThat(itemJson.getJsonObject(STATUS_KEY).getMap().get(NAME),
      is(ItemStatus.IN_TRANSIT.getValue()));

    assertThat(itemJson.getString(DESTINATION_SERVICE_POINT), is(secondServicePointId.toString()));

    final JsonObject smallAngryPlanetInstance = item.getInstance().getJson();

    assertThat(itemJson.getString(TITLE), is(smallAngryPlanetInstance.getString(TITLE)));

    final String contributors = String.valueOf(((JsonArray) smallAngryPlanetInstance
      .getMap().get(CONTRIBUTORS)).getJsonObject(0).getMap().get(NAME));

    assertThat(itemJson.getJsonArray(CONTRIBUTORS)
      .getJsonObject(0).getMap().get(NAME), is(contributors));

    final JsonObject smallAngryPlanetResponse = item.getResponse().getJson();

    assertThat(itemJson.getString(CALL_NUMBER),
      is(smallAngryPlanetResponse.getString(ITEM_LEVEL_CALL_NUMBER)));

    assertThat(itemJson.getString(ENUMERATION),
      is(smallAngryPlanetResponse.getString(ENUMERATION)));

    assertThat(itemJson.getString(VOLUME),
      is(smallAngryPlanetResponse.getString(VOLUME)));

    assertThat(itemJson.getJsonArray(YEAR_CAPTION),
      is(smallAngryPlanetResponse.getJsonArray(YEAR_CAPTION)));

    assertThat(itemJson.getString(COPY_NUMBER),
      is(smallAngryPlanetResponse.getString(COPY_NUMBER)));

    assertThat(smallAngryPlanetResponse.getJsonObject(EFFECTIVE_CALL_NUMBER_COMPONENTS),
      is(itemJson.getJsonObject(EFFECTIVE_CALL_NUMBER_COMPONENTS)));
  }

  private void verifyLocation(JsonObject itemJson) {
    JsonObject actualLocation = itemJson.getJsonObject("location");

    assertThat(actualLocation.getString(NAME), is("3rd Floor"));
    assertThat(actualLocation.getString(CODE), is("NU/JC/DL/3F"));
    assertThat(actualLocation.getString(LIBRARY), is("Djanogly Learning Resource Centre"));
  }

  private void verifyRequest(JsonObject itemJson, ZonedDateTime requestDate,
    LocalDate requestExpirationDate, String pickupServicePoint) {

    JsonObject actualRequest = itemJson.getJsonObject(REQUEST);
    assertThat(actualRequest.getString(REQUEST_TYPE), is("Hold"));
    assertThat(actualRequest.getString(REQUEST_PATRON_GROUP), is(REQUEST_PATRON_GROUP_DESCRIPTION));
    assertThat(actualRequest.getString(REQUEST_CREATION_DATE), isEquivalentTo(requestDate));

    assertThat(actualRequest.getString(REQUEST_EXPIRATION_DATE),
      isEquivalentTo(ZonedDateTime.of(requestExpirationDate.atTime(23, 59, 59), ZoneOffset.UTC)));

    assertThat(actualRequest.getString(REQUEST_PICKUP_SERVICE_POINT_NAME), is(pickupServicePoint));
    assertThat(toList(toStream(actualRequest, TAGS)), hasItems("tag1", "tag2"));
  }

  private void verifyRequestWithSecondPickupServicePoint(JsonObject itemJson,
    ZonedDateTime requestDate, LocalDate requestExpirationDate) {

    verifyRequest(itemJson, requestDate, requestExpirationDate, SERVICE_POINT_NAME_2);
  }

  private void verifyLoan(JsonObject itemJson, ZonedDateTime checkInDate,
    String checkInServicePointName, String checkInServicePointCode,
    String checkInServicePointDiscoveryName) {

    JsonObject actualLoan = itemJson.getJsonObject("loan");

    assertThat(actualLoan.getString(CHECK_IN_DATE_TIME), isEquivalentTo(checkInDate));

    JsonObject actualCheckInServicePoint = actualLoan.getJsonObject(CHECK_IN_SERVICE_POINT);

    assertThat(actualCheckInServicePoint.getString(NAME), is(checkInServicePointName));
    assertThat(actualCheckInServicePoint.getString(CODE), is(checkInServicePointCode));
    assertThat(actualCheckInServicePoint.getString(DISCOVERY_DISPLAY_NAME),
      is(checkInServicePointDiscoveryName));

    assertThat(actualCheckInServicePoint.getBoolean(PICKUP_LOCATION), is(true));
  }

  private void verifyLoanInFirstServicePoint(JsonObject itemJson, ZonedDateTime checkInDate) {
    verifyLoan(itemJson, checkInDate, SERVICE_POINT_NAME_1,
      "cd1", "Circulation Desk -- Hallway");
  }

  private void verifyLastCheckIn(JsonObject itemJson, ZonedDateTime checkInDateTime, String servicePointName) {
    JsonObject actualLastCheckIn = itemJson.getJsonObject("lastCheckIn");
    ZonedDateTime actualCheckinDateTime = JsonPropertyFetcher
      .getDateTimeProperty(actualLastCheckIn, "dateTime");
    assertThat(actualCheckinDateTime, is(checkInDateTime));
    assertThat(actualLastCheckIn.getJsonObject("servicePoint").getString(NAME), is(servicePointName));
  }

  private ItemResource createNod() {
    final ItemBuilder nodItemBuilder = ItemExamples.basedUponNod(
      materialTypesFixture.book().getId(),
      loanTypesFixture.canCirculate().getId())
      .withEnumeration("nodeEnumeration")
      .withVolume("nodeVolume")
      .withYearCaption(Collections.singletonList("2017"))
      .withCallNumber("222245", "PREFIX", "SUFFIX");
    return itemsFixture.basedUponNod(builder -> nodItemBuilder);
  }

  private ItemResource createSmallAngryPlanet() {
    final ItemBuilder smallAngryPlanetItemBuilder = createSmallAngryPlanetItemBuilder();

    return itemsFixture.basedUponSmallAngryPlanet(smallAngryPlanetItemBuilder,
      itemsFixture.thirdFloorHoldings());
  }

  private ItemResource createSmallAngryPlanetCopy(UUID locationId, String barcode) {
    final ItemBuilder smallAngryPlanetItemBuilder = new ItemBuilder()
      .withPermanentLoanType(loanTypesFixture.canCirculate().getId())
      .withMaterialType(materialTypesFixture.book().getId())
      .withBarcode(barcode)
      .withPermanentLocation(locationId)
      .withTemporaryLocation(locationId);

    return itemsFixture.basedUponSmallAngryPlanet(smallAngryPlanetItemBuilder,
      itemsFixture.thirdFloorHoldings());
  }

  private ItemBuilder createSmallAngryPlanetItemBuilder() {
    return ItemExamples.basedUponSmallAngryPlanet(
      materialTypesFixture.book().getId(),
      loanTypesFixture.canCirculate().getId(),
      "55555", "PREFIX", "SUFFIX", "Copy 1")
      .withEnumeration("smallAngryPlanetEnumeration")
      .withVolume("smallAngryPlanetVolume")
      .withYearCaption(Collections.singletonList("2019"));
  }
}
