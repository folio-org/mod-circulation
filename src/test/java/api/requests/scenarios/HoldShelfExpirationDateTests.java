package api.requests.scenarios;

import static api.support.builders.ItemBuilder.AWAITING_PICKUP;
import static api.support.builders.ItemBuilder.CHECKED_OUT;
import static api.support.builders.ItemBuilder.IN_TRANSIT;
import static api.support.builders.ItemBuilder.PAGED;
import static api.support.builders.RequestBuilder.OPEN_AWAITING_PICKUP;
import static api.support.builders.RequestBuilder.OPEN_IN_TRANSIT;
import static api.support.builders.RequestBuilder.OPEN_NOT_YET_FILLED;
import static api.support.fixtures.CalendarExamples.CASE_CURRENT_DATE_CLOSE;
import static api.support.fixtures.CalendarExamples.CASE_NEXT_DATE_OPEN;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static java.time.ZoneOffset.UTC;
import static org.folio.HttpStatus.HTTP_OK;
import static org.folio.circulation.support.utils.ClockUtil.getInstant;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.folio.circulation.support.utils.ClockUtil.setClock;
import static org.folio.circulation.support.utils.ClockUtil.setDefaultClock;
import static org.folio.circulation.support.utils.DateTimeUtil.atEndOfDay;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import api.support.APITests;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.http.IndividualResource;
import io.vertx.core.json.JsonObject;

class HoldShelfExpirationDateTests extends APITests {

  @BeforeEach
  void setUp() {
    // reset the clock before each test (just in case)
    setClock(Clock .fixed(getInstant(), UTC));
  }

  @AfterEach
  void afterEach() {
    // The clock must be reset after each test.
    setDefaultClock();
  }

  @ParameterizedTest
  @CsvSource(value = {
    "cd5,MINUTES,42",
    "cd6,HOURS,9",
    "cd7,HOURS,5",
    "cd8,MINUTES,10"
  })
  void requestWithShelfExpirationDateForSpExpiryInHoursAndMinutes(
    String servicePoint, ChronoUnit interval, int amount) {

    final IndividualResource checkInServicePoint;
    try {
      Method m = servicePointsFixture.getClass().getMethod(servicePoint);
      checkInServicePoint = (IndividualResource) m.invoke(servicePointsFixture);
    } catch(Exception e) {
      throw new IllegalArgumentException("no such service point: " + servicePoint);
    }

    final IndividualResource james = usersFixture.james();
    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod, james);

    final IndividualResource request = requestsFixture.placeItemLevelHoldShelfRequest(nod, jessica,
        getZonedDateTime(), checkInServicePoint.getId());

    checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePoint.getId()));

    final JsonObject storedRequest = requestsClient.getById(request.getId()).getJson();

    assertThat("request status snapshot in storage is " + OPEN_AWAITING_PICKUP,
      storedRequest.getString("status"), is(OPEN_AWAITING_PICKUP));

    ZonedDateTime zdtWithZoneOffset  = ZonedDateTime.parse(storedRequest.getString("holdShelfExpirationDate"),
      DateTimeFormatter.ISO_ZONED_DATE_TIME);

    if (isSameDay(interval, amount, zdtWithZoneOffset)) {
      assertHoldShelfExpirationDateBasedOnStrategy(storedRequest, servicePoint, amount, interval);
   }
    else {
      assertThat("request hold shelf expiration date is " + amount + " " + interval.toString() + " in the future",
      storedRequest.getString("holdShelfExpirationDate"),
      isEquivalentTo(interval.addTo(ClockUtil.getZonedDateTime(), amount)));
    }
  }

  private boolean isSameDay(ChronoUnit interval, int amount, ZonedDateTime zdtWithZoneOffset) {
    return interval.addTo(getZonedDateTime(), amount).toLocalDate().equals(LocalDate.now(zdtWithZoneOffset.getZone()));
  }

  private void assertHoldShelfExpirationDateBasedOnStrategy(JsonObject storedRequest, String servicePoint, int amount, ChronoUnit interval) {
    switch (servicePoint) {
      case "cd4" :
        ZonedDateTime moveToEndOfPreviousOpenDay = atEndOfDay(interval.addTo(getZonedDateTime(), amount)).minusDays(1);
        assertThat("request hold shelf expiration date is " + amount + " " + interval.toString() + " in the future",
        storedRequest.getString("holdShelfExpirationDate"), isEquivalentTo(moveToEndOfPreviousOpenDay));
        break;
      case "cd5" :
        ZonedDateTime keepTheCurrentDueDateTime = getZonedDateTime().plusMinutes(42);
        assertThat("request hold shelf expiration date is " + amount + " " + interval.toString() + " in the future",
        storedRequest.getString("holdShelfExpirationDate"), isEquivalentTo(keepTheCurrentDueDateTime));
        break;
      case "cd6":
        ZonedDateTime endOfServicePointHours = getZonedDateTime().plusDays(2).with(LocalTime.of(23, 59, 59).plusSeconds(1));
        assertThat("request hold shelf expiration date is " + amount + " " + interval.toString() + " in the future",
        storedRequest.getString("holdShelfExpirationDate"), isEquivalentTo(endOfServicePointHours));
        break;
      case "cd7" :
        long spanToNextServicePointHours1 = Duration.between(CASE_CURRENT_DATE_CLOSE.atStartOfDay(),CASE_NEXT_DATE_OPEN.atStartOfDay()).toDays();
        ZonedDateTime moveToBeginningOfNextServicePointHours1 = getZonedDateTime().plusDays(spanToNextServicePointHours1).with(LocalTime.of(5, 0, 0));
        assertThat("request hold shelf expiration date is " + amount + " " + interval.toString() + " in the future",
        storedRequest.getString("holdShelfExpirationDate"), isEquivalentTo(moveToBeginningOfNextServicePointHours1));
        break;
      case "cd8" :
        long spanToNextServicePointHours2 = Duration.between(CASE_CURRENT_DATE_CLOSE.atStartOfDay(),CASE_NEXT_DATE_OPEN.atStartOfDay()).toDays();
        ZonedDateTime moveToBeginningOfNextServicePointHours2 = getZonedDateTime().plusDays(spanToNextServicePointHours2).with(LocalTime.of(0, 10, 0));
        assertThat("request hold shelf expiration date is " + amount + " " + interval.toString() + " in the future",
        storedRequest.getString("holdShelfExpirationDate"), isEquivalentTo(moveToBeginningOfNextServicePointHours2));
        break;
      case "cd9", "cd10" :
        ZonedDateTime moveToTheNextOpenDay = atEndOfDay(interval.addTo(getZonedDateTime(), amount)).plusDays(1);
        assertThat("request hold shelf expiration date is " + amount + " " + interval.toString() + " in the future",
        storedRequest.getString("holdShelfExpirationDate"), isEquivalentTo(moveToTheNextOpenDay));
        break;
    }
  }

  @ParameterizedTest
  @CsvSource(value = {
    "cd4,WEEKS,2",
    "cd9,DAYS,30",
    "cd10,MONTHS,6"
  })
  void requestWithShelfExpirationDateForSpExpiryInDaysWeeksMonths(
    String servicePoint, ChronoUnit interval, int amount) {

    final IndividualResource checkInServicePoint;
    try {
      Method m = servicePointsFixture.getClass().getMethod(servicePoint);
      checkInServicePoint = (IndividualResource) m.invoke(servicePointsFixture);
    } catch (Exception e) {
      throw new IllegalArgumentException("no such service point: " + servicePoint);
    }

    final IndividualResource james = usersFixture.james();
    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod, james);

    final IndividualResource request = requestsFixture.placeItemLevelHoldShelfRequest(nod, jessica,
      ClockUtil.getZonedDateTime(), checkInServicePoint.getId());

    checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePoint.getId()));

    final JsonObject storedRequest = requestsClient.getById(request.getId()).getJson();

    assertThat("request status snapshot in storage is " + OPEN_AWAITING_PICKUP,
      storedRequest.getString("status"), is(OPEN_AWAITING_PICKUP));

    assertHoldShelfExpirationDateBasedOnStrategy(storedRequest, servicePoint, amount, interval);
  }

  @Test
  void shouldUseTenantTimeZoneForLongTerm() {
    final ChronoUnit interval = ChronoUnit.DAYS;
    final int amount = 30;
    final ZoneId tenantTimeZone = ZoneId.of("America/New_York");

    localeFixture.createLocaleSettingsForTimezone(tenantTimeZone.getId());

    final IndividualResource checkInServicePoint = servicePointsFixture.cd1();
    final IndividualResource james = usersFixture.james();
    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod, james);

    final IndividualResource request = requestsFixture.placeItemLevelHoldShelfRequest(nod, jessica,
      ClockUtil.getZonedDateTime(), checkInServicePoint.getId());

    checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePoint.getId()));

    final JsonObject storedRequest = requestsClient.getById(request.getId()).getJson();

    ZonedDateTime expectedExpirationDate = atEndOfDay(
      interval.addTo(getInstant().atZone(tenantTimeZone), amount))
      .toInstant()
      .atZone(ZoneOffset.UTC);

    assertThat(storedRequest.getString("status"), is(OPEN_AWAITING_PICKUP));
    assertThat(storedRequest.getString("holdShelfExpirationDate"),
      isEquivalentTo(expectedExpirationDate));
  }

  @Test
  void shouldUseTenantTimeZoneForShortTerm() {
    final ChronoUnit interval = ChronoUnit.MINUTES;
    final int amount = 42;
    final ZoneId tenantTimeZone = ZoneId.of("America/New_York");

    localeFixture.createLocaleSettingsForTimezone(tenantTimeZone.getId());

    final IndividualResource checkInServicePoint = servicePointsFixture.cd5();
    final IndividualResource james = usersFixture.james();
    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod, james);

    final IndividualResource request = requestsFixture.placeItemLevelHoldShelfRequest(nod, jessica,
      ClockUtil.getZonedDateTime(), checkInServicePoint.getId());

    checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePoint.getId()));

    final JsonObject storedRequest = requestsClient.getById(request.getId()).getJson();

    ZonedDateTime expectedExpirationDate = interval
      .addTo(getInstant().atZone(tenantTimeZone), amount)
      .toInstant()
      .atZone(ZoneOffset.UTC);

    assertThat(storedRequest.getString("status"), is(OPEN_AWAITING_PICKUP));
    assertThat(storedRequest.getString("holdShelfExpirationDate"),
      isEquivalentTo(expectedExpirationDate));
  }

  @Test
  void requestWithHoldShelfExpirationDateAlreadySet() {
    final IndividualResource checkInServicePoint = servicePointsFixture.cd1();

    final IndividualResource james = usersFixture.james();
    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod, james);

    final IndividualResource request = requestsFixture.placeItemLevelHoldShelfRequest(nod, jessica,
      ClockUtil.getZonedDateTime(), checkInServicePoint.getId());

    checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePoint.getId()));

    final Response fetchStoredRequestResponse = requestsClient.getById(request.getId());

    assertThat(fetchStoredRequestResponse, hasStatus(HTTP_OK));

    final JsonObject storedRequest = fetchStoredRequestResponse.getJson();

    assertThat("request status snapshot in storage is " + OPEN_AWAITING_PICKUP,
        storedRequest.getString("status"), is(OPEN_AWAITING_PICKUP));

    final ZonedDateTime expectedExpirationDate = atEndOfDay(
      ChronoUnit.DAYS.addTo(ClockUtil.getZonedDateTime(), 30));

    assertThat("request hold shelf expiration date is 30 days in the future",
      storedRequest.getString("holdShelfExpirationDate"),
      isEquivalentTo(expectedExpirationDate));

    Clock not30Days = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);

    ClockUtil.setClock(not30Days);

    checkInFixture.checkInByBarcode(
        new CheckInByBarcodeRequestBuilder()
          .forItem(nod)
          .at(checkInServicePoint.getId()));

    final Response fetchStoredSecondRequestResponse = requestsClient.getById(request.getId());

    assertThat(fetchStoredSecondRequestResponse, hasStatus(HTTP_OK));

    final JsonObject storedSecondCheckInRequest = fetchStoredSecondRequestResponse.getJson();

    assertThat("request status snapshot in storage is " + OPEN_AWAITING_PICKUP,
        storedSecondCheckInRequest.getString("status"), is(OPEN_AWAITING_PICKUP));

    assertThat("request hold shelf expiration date is 30 days in the future and has not been updated",
      storedRequest.getString("holdShelfExpirationDate"),
      isEquivalentTo(expectedExpirationDate));
  }

  @Test
  void requestRemoveHoldShelfExpirationDateWhenItemIsInTransit() {
    final IndividualResource checkInServicePoint = servicePointsFixture.cd1();
    final IndividualResource alternateCheckInServicePoint = servicePointsFixture.cd2();

    final IndividualResource james = usersFixture.james();
    final IndividualResource jessica = usersFixture.jessica();

    IndividualResource nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod, james);

    final IndividualResource request = requestsFixture.placeItemLevelHoldShelfRequest(nod, jessica,
      ClockUtil.getZonedDateTime(), checkInServicePoint.getId());

    checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePoint.getId()));

    nod = itemsClient.get(nod);

    JsonObject storedRequest = requestsClient.getById(request.getId()).getJson();

    assertThat("Item status snapshot in storage is " + AWAITING_PICKUP,
      nod, hasItemStatus(AWAITING_PICKUP));

    assertThat("request status snapshot in storage is " + OPEN_AWAITING_PICKUP,
      storedRequest.getString("status"), is(OPEN_AWAITING_PICKUP));

    final ZonedDateTime expectedExpirationDate = atEndOfDay(
      ChronoUnit.DAYS.addTo(ClockUtil.getZonedDateTime(), 30));

    assertThat("request hold shelf expiration date is 30 days in the future",
      storedRequest.getString("holdShelfExpirationDate"),
      isEquivalentTo(expectedExpirationDate));

    checkInFixture.checkInByBarcode(
        new CheckInByBarcodeRequestBuilder()
          .forItem(nod)
          .at(alternateCheckInServicePoint.getId()));

    final JsonObject storedSecondCheckInRequest = requestsClient.getById(request.getId()).getJson();

    assertThat("request status snapshot in storage is " + OPEN_IN_TRANSIT,
      storedSecondCheckInRequest.getString("status"), is(OPEN_IN_TRANSIT));

    assertThat("request hold shelf expiration date is not set",
      storedSecondCheckInRequest.getString("holdShelfExpirationDate"), is(emptyOrNullString()));
  }

  @Test
  void pageRequestWithHoldShelfExpirationDate() {
    final IndividualResource checkInServicePoint = servicePointsFixture.cd1();

    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource nod = itemsFixture.basedUponNod();

    final IndividualResource requestServicePoint = checkInServicePoint;

    final IndividualResource request = requestsFixture.placeItemLevelHoldShelfRequest(nod, jessica,
      ClockUtil.getZonedDateTime(), requestServicePoint.getId(), "Page");

    checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePoint.getId()));

    final Response getByIdResponse = requestsClient.getById(request.getId());

    assertThat(getByIdResponse, hasStatus(HTTP_OK));

    final JsonObject storedRequest = getByIdResponse.getJson();

    assertThat("request status snapshot in storage is " + OPEN_AWAITING_PICKUP,
      storedRequest.getString("status"), is(OPEN_AWAITING_PICKUP));

    final ZonedDateTime expectedExpirationDate = atEndOfDay(
      ChronoUnit.DAYS.addTo(ClockUtil.getZonedDateTime(), 30));

    assertThat("request hold shelf expiration date is 30 days in the future",
      storedRequest.getString("holdShelfExpirationDate"),
      isEquivalentTo(expectedExpirationDate));
  }

  @Test
  void requestHoldShelfExpirationDateWhenItemIsInTransit() {
    final IndividualResource checkInServicePoint = servicePointsFixture.cd1();
    final IndividualResource alternateCheckInServicePoint = servicePointsFixture.cd2();

    final IndividualResource james = usersFixture.james();
    final IndividualResource jessica = usersFixture.jessica();

    IndividualResource nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod, james);

    final IndividualResource request = requestsFixture.placeItemLevelHoldShelfRequest(nod, jessica,
      ClockUtil.getZonedDateTime(), alternateCheckInServicePoint.getId());

    nod = itemsClient.get(nod);

    JsonObject storedRequest = requestsClient.getById(request.getId()).getJson();

    assertThat("Item status snapshot in storage is " + CHECKED_OUT,
      nod, hasItemStatus(CHECKED_OUT));

    assertThat("request status snapshot in storage is " + OPEN_NOT_YET_FILLED,
      storedRequest.getString("status"), is(OPEN_NOT_YET_FILLED));

    checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePoint.getId()));

    storedRequest = requestsClient.getById(request.getId()).getJson();

    assertThat("request status snapshot in storage is " + OPEN_IN_TRANSIT,
      storedRequest.getString("status"), is(OPEN_IN_TRANSIT));

    assertThat("request hold shelf expiration date is not set",
      storedRequest.getString("holdShelfExpirationDate"), is(emptyOrNullString()));
  }

  @Test
  void pageRequestInTransitWithHoldShelfExpirationDate() {
    final IndividualResource checkInServicePoint = servicePointsFixture.cd1();
    final IndividualResource alternateServicePoint = servicePointsFixture.cd2();

    final IndividualResource jessica = usersFixture.jessica();

    IndividualResource nod = itemsFixture.basedUponNod();

    final IndividualResource requestServicePoint = alternateServicePoint;

    final IndividualResource request = requestsFixture.placeItemLevelHoldShelfRequest(nod, jessica,
      ClockUtil.getZonedDateTime(), requestServicePoint.getId(), "Page");

    nod = itemsClient.get(nod);

    JsonObject storedRequest = requestsClient.getById(request.getId()).getJson();

    assertThat("Item status snapshot in storage is " + PAGED,
      nod, hasItemStatus(PAGED));

    assertThat("request status snapshot in storage is " + OPEN_NOT_YET_FILLED,
      storedRequest.getString("status"), is(OPEN_NOT_YET_FILLED));

    checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePoint.getId()));

    storedRequest = requestsClient.getById(request.getId()).getJson();

    assertThat("request status snapshot in storage is " + OPEN_IN_TRANSIT,
      storedRequest.getString("status"), is(OPEN_IN_TRANSIT));

    assertThat("request hold shelf expiration date is not set",
      storedRequest.getString("holdShelfExpirationDate"), is(emptyOrNullString()));
  }

  @Test
  void requestInTransitRemainsInTransit() {
    final IndividualResource checkInServicePoint = servicePointsFixture.cd1();
    final IndividualResource alternateCheckInServicePoint = servicePointsFixture.cd2();
    final IndividualResource transitCheckInServicePoint = servicePointsFixture.cd4();

    final IndividualResource james = usersFixture.james();
    final IndividualResource jessica = usersFixture.jessica();

    IndividualResource nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod, james);

    final IndividualResource request = requestsFixture.placeItemLevelHoldShelfRequest(nod, jessica,
      ClockUtil.getZonedDateTime(), alternateCheckInServicePoint.getId());

    checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePoint.getId()));

    nod = itemsClient.get(nod);

    JsonObject storedRequest = requestsClient.getById(request.getId()).getJson();

    assertThat("Item status snapshot in storage is " + IN_TRANSIT,
      nod, hasItemStatus(IN_TRANSIT));

    assertThat("request status snapshot in storage is " + OPEN_IN_TRANSIT,
      storedRequest.getString("status"), is(OPEN_IN_TRANSIT));

    assertThat("request hold shelf expiration date is not set",
      storedRequest.getString("holdShelfExpirationDate"), is(emptyOrNullString()));

    checkInFixture.checkInByBarcode(
        new CheckInByBarcodeRequestBuilder()
          .forItem(nod)
          .at(transitCheckInServicePoint.getId()));

    nod = itemsClient.get(nod);

    storedRequest = requestsClient.getById(request.getId()).getJson();

    assertThat("Item status snapshot in storage is " + IN_TRANSIT,
      nod, hasItemStatus(IN_TRANSIT));

    assertThat("request status snapshot in storage is " + OPEN_IN_TRANSIT,
      storedRequest.getString("status"), is(OPEN_IN_TRANSIT));

    assertThat("request hold shelf expiration date is not set",
      storedRequest.getString("holdShelfExpirationDate"), is(emptyOrNullString()));
  }
}
