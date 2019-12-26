package api.item;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.closeTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.folio.circulation.support.http.client.IndividualResource;

import api.support.APITestContext;
import api.support.APITests;
import api.support.http.OkapiHeaders;
import io.vertx.core.json.JsonObject;
import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

public class ItemLastCheckInTests extends APITests {

  private final int CURRENT_TIME_MARGIN_MILLIS = 3000;

  @Override
  public void afterEach()
    throws InterruptedException, MalformedURLException, TimeoutException,
    ExecutionException {

    super.afterEach();
    APITestContext.defaultUserId();
  }

  @Test
  public void checkedInItemWithLoanShouldHaveLastCheckedInFields() {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource user = usersFixture.jessica();
    UUID servicePointId = servicePointsFixture.cd1().getId();
    DateTime checkInDate = DateTime.now();

    loansFixture.checkOutByBarcode(item, user, new DateTime(DateTimeZone.UTC));
    loansFixture.checkInByBarcode(item, checkInDate, servicePointId);
    JsonObject lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    DateTime actualCheckinDateTime = DateTime.parse(lastCheckIn.getString("dateTime"));

    assertThat((double) checkInDate.getMillis(),
      is(closeTo((double) actualCheckinDateTime.getMillis(),
        CURRENT_TIME_MARGIN_MILLIS)));
    assertThat(lastCheckIn.getString("servicePointId"),
      is(servicePointId.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"),
      is(APITestContext.getUserId()));
  }

  @Test
  public void shouldNotFailCheckInWithEmptyLoggedInUserId() {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID servicePointId = servicePointsFixture.cd1().getId();
    DateTime checkInDate = DateTime.now();

    final OkapiHeaders okapiHeaders = getOkapiHeadersFromContext()
      .withRequestId("check-in-by-barcode-request")
      .withUserId("");

    loansFixture.checkInByBarcode(item, checkInDate, servicePointId, okapiHeaders);

    JsonObject lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    DateTime actualCheckinDateTime = DateTime.parse(lastCheckIn.getString("dateTime"));

    assertThat((double) checkInDate.getMillis(),
      is(closeTo((double) actualCheckinDateTime.getMillis(),
        CURRENT_TIME_MARGIN_MILLIS)));
    assertThat(lastCheckIn.getString("servicePointId"),
      is(servicePointId.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is(nullValue()));
  }

  @Test
  public void shouldNotFailCheckInWithInvalidLoggedInUserId() {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID servicePointId = servicePointsFixture.cd1().getId();
    DateTime checkInDate = DateTime.now();

    final OkapiHeaders okapiHeaders = getOkapiHeadersFromContext()
      .withRequestId("check-in-by-barcode-request")
      .withUserId("INVALID_UUID");

    loansFixture.checkInByBarcode(item, checkInDate, servicePointId, okapiHeaders);

    JsonObject lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    DateTime actualCheckinDateTime = DateTime.parse(lastCheckIn.getString("dateTime"));

    assertThat((double) checkInDate.getMillis(),
      is(closeTo((double) actualCheckinDateTime.getMillis(),
        CURRENT_TIME_MARGIN_MILLIS)));
    assertThat(lastCheckIn.getString("servicePointId"),
      is(servicePointId.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is("INVALID_UUID"));
  }

  @Test
  public void shouldBeAbleToCheckinItemWithoutLoan() {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID servicePointId = servicePointsFixture.cd1().getId();
    DateTime checkInDate = DateTime.now();

    loansFixture.checkInByBarcode(item, checkInDate, servicePointId);

    JsonObject lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    DateTime actualCheckinDateTime = DateTime.parse(lastCheckIn.getString("dateTime"));

    assertThat((double) checkInDate.getMillis(),
      is(closeTo((double) actualCheckinDateTime.getMillis(),
        CURRENT_TIME_MARGIN_MILLIS)));
    assertThat(lastCheckIn.getString("servicePointId"), is(servicePointId.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is(APITestContext.getUserId()));
  }

  @Test
  public void shouldBeAbleCheckinItemWithoutLoanMultipleTimes() {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID servicePointId = servicePointsFixture.cd1().getId();
    DateTime firstCheckInDate = DateTime.now();

    loansFixture.checkInByBarcode(item, firstCheckInDate, servicePointId);
    JsonObject lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    DateTime actualCheckinDateTime = DateTime.parse(lastCheckIn.getString("dateTime"));

    assertThat((double) firstCheckInDate.getMillis(),
      is(closeTo((double) actualCheckinDateTime.getMillis(),
        CURRENT_TIME_MARGIN_MILLIS)));
    assertThat(lastCheckIn.getString("servicePointId"), is(servicePointId.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is(APITestContext.getUserId()));

    DateTime secondCheckInDate = DateTime.now();
    UUID servicePointId2 = servicePointsFixture.cd2().getId();

    final String randomUserId = UUID.randomUUID().toString();

    final OkapiHeaders okapiHeaders = getOkapiHeadersFromContext()
      .withRequestId("check-in-by-barcode-request")
      .withUserId(randomUserId);

    loansFixture.checkInByBarcode(item, secondCheckInDate, servicePointId2,
      okapiHeaders);

    lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    actualCheckinDateTime = DateTime.parse(lastCheckIn.getString("dateTime"));

    assertThat((double) secondCheckInDate.getMillis(),
      is(closeTo((double) actualCheckinDateTime.getMillis(),
        CURRENT_TIME_MARGIN_MILLIS)));
    assertThat(lastCheckIn.getString("servicePointId"), is(servicePointId2.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is(randomUserId));
  }

  @Test
  public void shouldDisplaySystemDateIfCheckinWasBackdated()
    throws InterruptedException, MalformedURLException, TimeoutException,
    ExecutionException {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID servicePointId = servicePointsFixture.cd1().getId();
    DateTime checkInDate = DateTime.now().minusHours(1);

    loansFixture.checkInByBarcode(item, checkInDate, servicePointId);
    JsonObject lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    DateTime actualCheckinTime = DateTime.parse(lastCheckIn.getString("dateTime"));

    assertTrue(actualCheckinTime.isAfter(checkInDate));

    assertThat((double) DateTime.now().getMillis(),
      is(closeTo((double) actualCheckinTime.getMillis(),
        CURRENT_TIME_MARGIN_MILLIS)));
  }
}
