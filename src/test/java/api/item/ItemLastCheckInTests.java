package api.item;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
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
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.junit.Test;

public class ItemLastCheckInTests extends APITests {

  @Override
  public void beforeEach()
    throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {
    super.beforeEach();
    DateTimeUtils.setCurrentMillisFixed(1577457923195L);
  }

  @Override
  public void afterEach()
    throws InterruptedException, MalformedURLException, TimeoutException,
    ExecutionException {
    super.afterEach();
    DateTimeUtils.setCurrentMillisSystem();
  }

  @Test
  public void checkedInItemWithLoanShouldHaveLastCheckedInFields() {

    DateTime checkInDate = DateTime.now(DateTimeZone.UTC);
    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource user = usersFixture.jessica();
    UUID servicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(item, user, new DateTime(DateTimeZone.UTC));
    loansFixture.checkInByBarcode(item, checkInDate, servicePointId);
    JsonObject lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    DateTime actualCheckinDateTime = DateTime
      .parse(lastCheckIn.getString("dateTime"));

    assertThat(actualCheckinDateTime, is(checkInDate));
    assertThat(lastCheckIn.getString("servicePointId"),
      is(servicePointId.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"),
      is(APITestContext.getUserId()));
  }

  @Test
  public void shouldNotFailCheckInWithEmptyLoggedInUserId() {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID servicePointId = servicePointsFixture.cd1().getId();
    DateTime checkInDate = DateTime.now(DateTimeZone.UTC);

    final OkapiHeaders okapiHeaders = getOkapiHeadersFromContext()
      .withRequestId("check-in-by-barcode-request")
      .withUserId("");

    loansFixture.checkInByBarcode(item, checkInDate, servicePointId, okapiHeaders);

    JsonObject lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    DateTime actualCheckinDateTime = DateTime
      .parse(lastCheckIn.getString("dateTime"));

    assertThat(actualCheckinDateTime, is(checkInDate));
    assertThat(lastCheckIn.getString("servicePointId"),
      is(servicePointId.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is(nullValue()));
  }

  @Test
  public void shouldNotFailCheckInWithInvalidLoggedInUserId() {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID servicePointId = servicePointsFixture.cd1().getId();
    DateTime checkInDate = DateTime.now(DateTimeZone.UTC);

    final OkapiHeaders okapiHeaders = getOkapiHeadersFromContext()
      .withRequestId("check-in-by-barcode-request")
      .withUserId("INVALID_UUID");

    loansFixture.checkInByBarcode(item, checkInDate, servicePointId, okapiHeaders);

    JsonObject lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    DateTime actualCheckinDateTime = DateTime
      .parse(lastCheckIn.getString("dateTime"));

    assertThat(actualCheckinDateTime, is(checkInDate));
    assertThat(lastCheckIn.getString("servicePointId"),
      is(servicePointId.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is("INVALID_UUID"));
  }

  @Test
  public void shouldBeAbleToCheckinItemWithoutLoan() {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID servicePointId = servicePointsFixture.cd1().getId();
    DateTime checkInDate = DateTime.now(DateTimeZone.UTC);

    loansFixture.checkInByBarcode(item, checkInDate, servicePointId);

    JsonObject lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    DateTime actualCheckinDateTime = DateTime
      .parse(lastCheckIn.getString("dateTime"));

    assertThat(actualCheckinDateTime, is(checkInDate));
    assertThat(lastCheckIn.getString("servicePointId"), is(servicePointId.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is(APITestContext.getUserId()));
  }

  @Test
  public void shouldBeAbleCheckinItemWithoutLoanMultipleTimes() {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID servicePointId = servicePointsFixture.cd1().getId();
    DateTime firstCheckInDate = DateTime.now(DateTimeZone.UTC);

    loansFixture.checkInByBarcode(item, firstCheckInDate, servicePointId);
    JsonObject lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    DateTime actualCheckinDateTime = DateTime
      .parse(lastCheckIn.getString("dateTime"));

    assertThat(actualCheckinDateTime, is(firstCheckInDate));
    assertThat(lastCheckIn.getString("servicePointId"), is(servicePointId.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is(APITestContext.getUserId()));

    DateTime secondCheckInDate = DateTime.now(DateTimeZone.UTC);
    UUID servicePointId2 = servicePointsFixture.cd2().getId();

    final String randomUserId = UUID.randomUUID().toString();

    final OkapiHeaders okapiHeaders = getOkapiHeadersFromContext()
      .withRequestId("check-in-by-barcode-request")
      .withUserId(randomUserId);

    loansFixture.checkInByBarcode(item, secondCheckInDate, servicePointId2,
      okapiHeaders);

    lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    actualCheckinDateTime = DateTime
      .parse(lastCheckIn.getString("dateTime"));

    assertThat(actualCheckinDateTime, is(secondCheckInDate));
    assertThat(lastCheckIn.getString("servicePointId"), is(servicePointId2.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is(randomUserId));
  }

  @Test
  public void shouldDisplaySystemDateIfCheckinWasBackdated()
    throws InterruptedException, MalformedURLException, TimeoutException,
    ExecutionException {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID servicePointId = servicePointsFixture.cd1().getId();
    DateTime checkInDate = DateTime.now(DateTimeZone.UTC).minusHours(1);

    loansFixture.checkInByBarcode(item, checkInDate, servicePointId);
    JsonObject lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    DateTime actualCheckinDateTime = DateTime
      .parse(lastCheckIn.getString("dateTime"));

    assertTrue(actualCheckinDateTime.isAfter(checkInDate));
    assertThat(actualCheckinDateTime, is(DateTime.now(DateTimeZone.UTC)));
  }
}
