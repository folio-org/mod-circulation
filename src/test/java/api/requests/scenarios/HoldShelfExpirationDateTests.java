package api.requests.scenarios;

import static api.support.builders.ItemBuilder.AWAITING_PICKUP;
import static api.support.builders.ItemBuilder.CHECKED_OUT;
import static api.support.builders.ItemBuilder.IN_TRANSIT;
import static api.support.builders.ItemBuilder.PAGED;
import static api.support.builders.RequestBuilder.OPEN_AWAITING_PICKUP;
import static api.support.builders.RequestBuilder.OPEN_IN_TRANSIT;
import static api.support.builders.RequestBuilder.OPEN_NOT_YET_FILLED;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static org.folio.HttpStatus.HTTP_OK;
import static org.folio.circulation.support.utils.DateTimeUtil.atEndOfTheDay;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import api.support.APITests;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class HoldShelfExpirationDateTests extends APITests{
  private static Clock clock;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    ClockManager.getClockManager().setClock(clock);
  }

  @Before
  public void setUp() throws Exception {
    // reset the clock before each test (just in case)
    ClockManager.getClockManager().setClock(clock);
  }

  @Test
  @Parameters({
    "cd5|MINUTES|42",
    "cd6|HOURS|9"
  })
  public void requestWithShelfExpirationDateForSpExpiryInHoursAndMinutes(
    String servicePoint, ChronoUnit interval, int amount) throws MalformedURLException,
    InterruptedException, TimeoutException, ExecutionException {

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

    loansFixture.checkOutByBarcode(nod, james);

    final IndividualResource request = requestsFixture.placeHoldShelfRequest(nod, jessica,
        DateTime.now(DateTimeZone.UTC), checkInServicePoint.getId());

    loansFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePoint.getId()));

    final JsonObject storedRequest = requestsClient.getById(request.getId()).getJson();

    assertThat("request status snapshot in storage is " + OPEN_AWAITING_PICKUP,
        storedRequest.getString("status"), is(OPEN_AWAITING_PICKUP));

    assertThat("request hold shelf expiration date is " + amount + " " + interval.toString() + " in the future",
      storedRequest.getString("holdShelfExpirationDate"),
      is(toIsoString(interval.addTo(ZonedDateTime.now(clock), amount))));
  }

  @Test
  @Parameters({
    "cd1|DAYS|30",
    "cd2|MONTHS|6",
    "cd4|WEEKS|2"
  })
  public void requestWithShelfExpirationDateForSpExpiryInDaysWeeksMonths(
    String servicePoint, ChronoUnit interval, int amount) throws MalformedURLException,
    InterruptedException, TimeoutException, ExecutionException {

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

    loansFixture.checkOutByBarcode(nod, james);

    final IndividualResource request = requestsFixture.placeHoldShelfRequest(nod, jessica,
      DateTime.now(DateTimeZone.UTC), checkInServicePoint.getId());

    loansFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePoint.getId()));

    final JsonObject storedRequest = requestsClient.getById(request.getId()).getJson();

    assertThat("request status snapshot in storage is " + OPEN_AWAITING_PICKUP,
      storedRequest.getString("status"), is(OPEN_AWAITING_PICKUP));

    ZonedDateTime expectedExpirationDate = atEndOfTheDay(interval.addTo(ZonedDateTime.now(clock), amount));

    assertThat("request hold shelf expiration date is " + amount + " " + interval.toString() + " in the future",
      storedRequest.getString("holdShelfExpirationDate"),
      is(toIsoString(expectedExpirationDate)));
  }

  @Test
  public void requestWithHoldShelfExpirationDateAlreadySet()
      throws MalformedURLException, InterruptedException, TimeoutException,
      ExecutionException {
    final IndividualResource checkInServicePoint = servicePointsFixture.cd1();

    final IndividualResource james = usersFixture.james();
    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource nod = itemsFixture.basedUponNod();

    loansFixture.checkOutByBarcode(nod, james);

    final IndividualResource request = requestsFixture.placeHoldShelfRequest(nod, jessica,
        DateTime.now(DateTimeZone.UTC), checkInServicePoint.getId());

    loansFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePoint.getId()));

    final Response fetchStoredRequestResponse = requestsClient.getById(request.getId());

    assertThat(fetchStoredRequestResponse, hasStatus(HTTP_OK));

    final JsonObject storedRequest = fetchStoredRequestResponse.getJson();

    assertThat("request status snapshot in storage is " + OPEN_AWAITING_PICKUP,
        storedRequest.getString("status"), is(OPEN_AWAITING_PICKUP));

    final ZonedDateTime expectedExpirationDate = atEndOfTheDay(
      ChronoUnit.DAYS.addTo(ZonedDateTime.now(clock), 30));

    assertThat("request hold shelf expiration date is 30 days in the future",
      storedRequest.getString("holdShelfExpirationDate"), is(
        expectedExpirationDate.format(DateTimeFormatter.ISO_INSTANT)));

    Clock not30Days = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);

    ClockManager.getClockManager().setClock(not30Days);

    loansFixture.checkInByBarcode(
        new CheckInByBarcodeRequestBuilder()
          .forItem(nod)
          .at(checkInServicePoint.getId()));

    final Response fetchStoredSecondRequestResponse = requestsClient.getById(request.getId());

    assertThat(fetchStoredSecondRequestResponse, hasStatus(HTTP_OK));

    final JsonObject storedSecondCheckInRequest = fetchStoredSecondRequestResponse.getJson();

    assertThat("request status snapshot in storage is " + OPEN_AWAITING_PICKUP,
        storedSecondCheckInRequest.getString("status"), is(OPEN_AWAITING_PICKUP));

    final ZonedDateTime expectedExpirationDateAfterUpdate = atEndOfTheDay(
      ChronoUnit.DAYS.addTo(ZonedDateTime.now(clock), 30));

    assertThat("request hold shelf expiration date is 30 days in the future and has not been updated",
        storedRequest.getString("holdShelfExpirationDate"),
      is(expectedExpirationDateAfterUpdate.format(DateTimeFormatter.ISO_INSTANT)));
  }

  @Test
  public void requestRemoveHoldShelfExpirationDateWhenItemIsInTransit()
      throws MalformedURLException, InterruptedException, TimeoutException,
      ExecutionException {
    final IndividualResource checkInServicePoint = servicePointsFixture.cd1();
    final IndividualResource alternateCheckInServicePoint = servicePointsFixture.cd2();

    final IndividualResource james = usersFixture.james();
    final IndividualResource jessica = usersFixture.jessica();

    IndividualResource nod = itemsFixture.basedUponNod();

    loansFixture.checkOutByBarcode(nod, james);

    final IndividualResource request = requestsFixture.placeHoldShelfRequest(nod, jessica,
        DateTime.now(DateTimeZone.UTC), checkInServicePoint.getId());

    loansFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePoint.getId()));

    nod = itemsClient.get(nod);

    JsonObject storedRequest = requestsClient.getById(request.getId()).getJson();

    assertThat("Item status snapshot in storage is " + AWAITING_PICKUP,
        nod, hasItemStatus(AWAITING_PICKUP));

    assertThat("request status snapshot in storage is " + OPEN_AWAITING_PICKUP,
        storedRequest.getString("status"), is(OPEN_AWAITING_PICKUP));

    final ZonedDateTime expectedExpirationDate = atEndOfTheDay(
      ChronoUnit.DAYS.addTo(ZonedDateTime.now(clock), 30));

    assertThat("request hold shelf expiration date is 30 days in the future",
      storedRequest.getString("holdShelfExpirationDate"),
      is(expectedExpirationDate.format(DateTimeFormatter.ISO_INSTANT)));

    loansFixture.checkInByBarcode(
        new CheckInByBarcodeRequestBuilder()
          .forItem(nod)
          .at(alternateCheckInServicePoint.getId()));

    final JsonObject storedSecondCheckInRequest = requestsClient.getById(request.getId()).getJson();

    assertThat("request status snapshot in storage is " + OPEN_IN_TRANSIT,
        storedSecondCheckInRequest.getString("status"), is(OPEN_IN_TRANSIT));

    assertThat("request hold shelf expiration date is not set",
        storedSecondCheckInRequest.getString("holdShelfExpirationDate"), emptyOrNullString());
  }

  @Test
  public void pageRequestWithHoldShelfExpirationDate()
      throws MalformedURLException, InterruptedException, TimeoutException,
      ExecutionException {
    final IndividualResource checkInServicePoint = servicePointsFixture.cd1();

    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource nod = itemsFixture.basedUponNod();

    final IndividualResource requestServicePoint = checkInServicePoint;

    final IndividualResource request = requestsFixture.placeHoldShelfRequest(nod, jessica,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Page");

    loansFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePoint.getId()));

    final Response getByIdResponse = requestsClient.getById(request.getId());

    assertThat(getByIdResponse, hasStatus(HTTP_OK));

    final JsonObject storedRequest = getByIdResponse.getJson();

    assertThat("request status snapshot in storage is " + OPEN_AWAITING_PICKUP,
        storedRequest.getString("status"), is(OPEN_AWAITING_PICKUP));

    final ZonedDateTime expectedExpirationDate = atEndOfTheDay(
      ChronoUnit.DAYS.addTo(ZonedDateTime.now(clock), 30));

    assertThat("request hold shelf expiration date is 30 days in the future",
      storedRequest.getString("holdShelfExpirationDate"),
      is(expectedExpirationDate.format(DateTimeFormatter.ISO_INSTANT)));
  }

  @Test
  public void requestHoldShelfExpirationDateWhenItemIsInTransit()
      throws MalformedURLException, InterruptedException, TimeoutException,
      ExecutionException {
    final IndividualResource checkInServicePoint = servicePointsFixture.cd1();
    final IndividualResource alternateCheckInServicePoint = servicePointsFixture.cd2();

    final IndividualResource james = usersFixture.james();
    final IndividualResource jessica = usersFixture.jessica();

    IndividualResource nod = itemsFixture.basedUponNod();

    loansFixture.checkOutByBarcode(nod, james);

    final IndividualResource request = requestsFixture.placeHoldShelfRequest(nod, jessica,
        DateTime.now(DateTimeZone.UTC), alternateCheckInServicePoint.getId());

    nod = itemsClient.get(nod);

    JsonObject storedRequest = requestsClient.getById(request.getId()).getJson();

    assertThat("Item status snapshot in storage is " + CHECKED_OUT,
        nod, hasItemStatus(CHECKED_OUT));

    assertThat("request status snapshot in storage is " + OPEN_NOT_YET_FILLED,
        storedRequest.getString("status"), is(OPEN_NOT_YET_FILLED));

    loansFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePoint.getId()));

    storedRequest = requestsClient.getById(request.getId()).getJson();

    assertThat("request status snapshot in storage is " + OPEN_IN_TRANSIT,
        storedRequest.getString("status"), is(OPEN_IN_TRANSIT));

    assertThat("request hold shelf expiration date is not set",
        storedRequest.getString("holdShelfExpirationDate"), emptyOrNullString());
  }

  @Test
  public void pageRequestInTransitWithHoldShelfExpirationDate()
      throws MalformedURLException, InterruptedException, TimeoutException,
      ExecutionException {
    final IndividualResource checkInServicePoint = servicePointsFixture.cd1();
    final IndividualResource alternateServicePoint = servicePointsFixture.cd2();

    final IndividualResource jessica = usersFixture.jessica();

    IndividualResource nod = itemsFixture.basedUponNod();

    final IndividualResource requestServicePoint = alternateServicePoint;

    final IndividualResource request = requestsFixture.placeHoldShelfRequest(nod, jessica,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId(), "Page");

    nod = itemsClient.get(nod);

    JsonObject storedRequest = requestsClient.getById(request.getId()).getJson();

    assertThat("Item status snapshot in storage is " + PAGED,
        nod, hasItemStatus(PAGED));

    assertThat("request status snapshot in storage is " + OPEN_NOT_YET_FILLED,
        storedRequest.getString("status"), is(OPEN_NOT_YET_FILLED));

    loansFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePoint.getId()));

    storedRequest = requestsClient.getById(request.getId()).getJson();

    assertThat("request status snapshot in storage is " + OPEN_IN_TRANSIT,
        storedRequest.getString("status"), is(OPEN_IN_TRANSIT));

    assertThat("request hold shelf expiration date is not set",
        storedRequest.getString("holdShelfExpirationDate"), emptyOrNullString());
  }

  @Test
  public void requestInTransitRemainsInTransit()
      throws MalformedURLException, InterruptedException, TimeoutException,
      ExecutionException {
    final IndividualResource checkInServicePoint = servicePointsFixture.cd1();
    final IndividualResource alternateCheckInServicePoint = servicePointsFixture.cd2();
    final IndividualResource transitCheckInServicePoint = servicePointsFixture.cd4();

    final IndividualResource james = usersFixture.james();
    final IndividualResource jessica = usersFixture.jessica();

    IndividualResource nod = itemsFixture.basedUponNod();

    loansFixture.checkOutByBarcode(nod, james);

    final IndividualResource request = requestsFixture.placeHoldShelfRequest(nod, jessica,
        DateTime.now(DateTimeZone.UTC), alternateCheckInServicePoint.getId());

    loansFixture.checkInByBarcode(
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
        storedRequest.getString("holdShelfExpirationDate"), emptyOrNullString());

    loansFixture.checkInByBarcode(
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
        storedRequest.getString("holdShelfExpirationDate"), emptyOrNullString());
  }

  private String toIsoString(ZonedDateTime dateTime) {
    return dateTime.format(DateTimeFormatter.ISO_INSTANT);
  }
}
