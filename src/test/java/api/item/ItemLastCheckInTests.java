package api.item;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import api.support.APITestContext;
import api.support.APITests;
import api.support.http.OkapiHeaders;
import io.vertx.core.json.JsonObject;

public class ItemLastCheckInTests extends APITests {

  @Override
  public void afterEach() {
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

    assertThat(lastCheckIn.getString("dateTime"), is(checkInDate.toString()));
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

    assertThat(lastCheckIn.getString("dateTime"), is(checkInDate.toString()));
    assertThat(lastCheckIn.getString("servicePointId"),
      is(servicePointId.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is(nullValue()));
  }

  @Test
  public void shouldNotFailCheckInWithInvalidLoggedInUserId() {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID servicePointId = servicePointsFixture.cd1().getId();
    DateTime checkInDate = DateTime.now();

    String invalidUuid = "00000000-0000-0000-0000-000000000000";
    final OkapiHeaders okapiHeaders = getOkapiHeadersFromContext()
      .withRequestId("check-in-by-barcode-request")
      .withUserId(invalidUuid);

    loansFixture.checkInByBarcode(item, checkInDate, servicePointId, okapiHeaders);

    JsonObject lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    assertThat(lastCheckIn.getString("dateTime"), is(checkInDate.toString()));
    assertThat(lastCheckIn.getString("servicePointId"),
      is(servicePointId.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is(invalidUuid));
  }

  @Test
  public void shouldBeAbleToCheckinItemWithoutLoan() {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID servicePointId = servicePointsFixture.cd1().getId();
    DateTime checkInDate = DateTime.now();

    loansFixture.checkInByBarcode(item, checkInDate, servicePointId);

    JsonObject lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    assertThat(lastCheckIn.getString("dateTime"), is(checkInDate.toString()));
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

    assertThat(lastCheckIn.getString("dateTime"), is(firstCheckInDate.toString()));
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

    assertThat(lastCheckIn.getString("dateTime"), is(secondCheckInDate.toString()));
    assertThat(lastCheckIn.getString("servicePointId"), is(servicePointId2.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is(randomUserId));
  }
}
