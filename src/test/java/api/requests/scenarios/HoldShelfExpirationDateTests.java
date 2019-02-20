package api.requests.scenarios;

import static api.support.builders.RequestBuilder.OPEN_AWAITING_PICKUP;
import static api.support.builders.RequestBuilder.OPEN_IN_TRANSIT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;
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
    "cd6|HOURS|9",
    "cd1|DAYS|30",
    "cd2|MONTHS|6",
    "cd4|WEEKS|2"
  })
  public void requestWithHoldShelfExpirationDateTest(String servicePoint, ChronoUnit interval, int amount)
      throws MalformedURLException, InterruptedException, TimeoutException,
      ExecutionException {
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

    final IndividualResource requestServicePoint = checkInServicePoint;

    loansFixture.checkOut(nod, james);

    final IndividualResource request = requestsFixture.placeHoldShelfRequest(nod, jessica,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId());

    loansFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePoint.getId()));

    final JsonObject storedRequest = requestsClient.getById(request.getId()).getJson();

    assertThat("request status snapshot in storage is " + OPEN_AWAITING_PICKUP,
        storedRequest.getString("status"), is(OPEN_AWAITING_PICKUP));

    assertThat("request hold shelf expiration date is " + amount + " " + interval.toString() + " in the future",
        storedRequest.getString("holdShelfExpirationDate"), is(new DateTime(interval.addTo(ZonedDateTime.now(clock), amount).toInstant().toEpochMilli(), DateTimeZone.UTC).toString(ISODateTimeFormat.dateTime())));
  }

  @Test
  public void requestWithHoldShelfExpirationDateAlreadySetTest()
      throws MalformedURLException, InterruptedException, TimeoutException,
      ExecutionException {
    final IndividualResource checkInServicePoint = servicePointsFixture.cd1();

    final IndividualResource james = usersFixture.james();
    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource nod = itemsFixture.basedUponNod();

    final IndividualResource requestServicePoint = checkInServicePoint;

    loansFixture.checkOut(nod, james);

    final IndividualResource request = requestsFixture.placeHoldShelfRequest(nod, jessica,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId());

    loansFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePoint.getId()));

    final JsonObject storedRequest = requestsClient.getById(request.getId()).getJson();

    assertThat("request status snapshot in storage is " + OPEN_AWAITING_PICKUP,
        storedRequest.getString("status"), is(OPEN_AWAITING_PICKUP));

    assertThat("request hold shelf expiration date is 30 days in the future",
        storedRequest.getString("holdShelfExpirationDate"), is(new DateTime(ChronoUnit.DAYS.addTo(ZonedDateTime.now(clock), 30).toInstant().toEpochMilli(), DateTimeZone.UTC).toString(ISODateTimeFormat.dateTime())));

    Clock not30Days = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
    ClockManager.getClockManager().setClock(not30Days);

    loansFixture.checkInByBarcode(
        new CheckInByBarcodeRequestBuilder()
          .forItem(nod)
          .at(checkInServicePoint.getId()));

    final JsonObject storedSecondCheckInRequest = requestsClient.getById(request.getId()).getJson();

    assertThat("request status snapshot in storage is " + OPEN_AWAITING_PICKUP,
        storedSecondCheckInRequest.getString("status"), is(OPEN_AWAITING_PICKUP));

    assertThat("request hold shelf expiration date is 30 days in the future and has not been updated",
        storedRequest.getString("holdShelfExpirationDate"), is(new DateTime(ChronoUnit.DAYS.addTo(ZonedDateTime.now(clock), 30).toInstant().toEpochMilli(), DateTimeZone.UTC).toString(ISODateTimeFormat.dateTime())));
  }
  @Test
  public void requestRemoveHoldShelfExpirationDateWhenItemIsInTransitTest()
      throws MalformedURLException, InterruptedException, TimeoutException,
      ExecutionException {
    final IndividualResource checkInServicePoint = servicePointsFixture.cd1();
    final IndividualResource alternateCheckInServicePoint = servicePointsFixture.cd2();

    final IndividualResource james = usersFixture.james();
    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource nod = itemsFixture.basedUponNod();

    final IndividualResource requestServicePoint = checkInServicePoint;

    loansFixture.checkOut(nod, james);

    final IndividualResource request = requestsFixture.placeHoldShelfRequest(nod, jessica,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId());

    loansFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePoint.getId()));

    final JsonObject storedRequest = requestsClient.getById(request.getId()).getJson();

    assertThat("request status snapshot in storage is " + OPEN_AWAITING_PICKUP,
        storedRequest.getString("status"), is(OPEN_AWAITING_PICKUP));

    assertThat("request hold shelf expiration date is 30 days in the future",
        storedRequest.getString("holdShelfExpirationDate"), is(new DateTime(ChronoUnit.DAYS.addTo(ZonedDateTime.now(clock), 30).toInstant().toEpochMilli(), DateTimeZone.UTC).toString(ISODateTimeFormat.dateTime())));

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
}
