package api.item;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.joda.time.DateTimeZone.UTC;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;

import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.JsonPropertyFetcher;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.junit.Test;

import api.support.APITestContext;
import api.support.APITests;
import api.support.http.OkapiHeaders;
import io.vertx.core.json.JsonObject;

public class ItemLastCheckInTests extends APITests {

  private static final DateTime fixedCheckInDateTime = new DateTime(2019, 4, 3, 2, 10, UTC);

  @Override
  public void beforeEach() throws InterruptedException {

    super.beforeEach();
    mockClockManagerToReturnFixedDateTime(fixedCheckInDateTime);
  }

  @Override
  public void afterEach() {
    super.afterEach();
    mockClockManagerToReturnDefaultDateTime();
  }

  @Test
  public void checkedInItemWithLoanShouldHaveLastCheckedInFields() {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource user = usersFixture.jessica();
    UUID servicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(item, user);
    loansFixture.checkInByBarcode(item, DateTime.now(UTC), servicePointId);
    JsonObject lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    DateTime actualCheckinDateTime = JsonPropertyFetcher
      .getDateTimeProperty(lastCheckIn, "dateTime");

    assertThat(actualCheckinDateTime, is(fixedCheckInDateTime));
    assertThat(lastCheckIn.getString("servicePointId"),
      is(servicePointId.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"),
      is(APITestContext.getUserId()));
  }

  @Test
  public void shouldNotFailCheckInWithEmptyLoggedInUserId() {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID servicePointId = servicePointsFixture.cd1().getId();

    final OkapiHeaders okapiHeaders = getOkapiHeadersFromContext()
      .withRequestId("check-in-by-barcode-request")
      .withUserId("");

    loansFixture.checkInByBarcode(item, fixedCheckInDateTime, servicePointId, okapiHeaders);

    JsonObject lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    DateTime actualCheckinDateTime = JsonPropertyFetcher
      .getDateTimeProperty(lastCheckIn, "dateTime");

    assertThat(actualCheckinDateTime, is(fixedCheckInDateTime));
    assertThat(lastCheckIn.getString("servicePointId"),
      is(servicePointId.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is(nullValue()));
  }

  @Test
  public void shouldNotFailCheckInWithInvalidLoggedInUserId() {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID servicePointId = servicePointsFixture.cd1().getId();

    String invalidUuid = "00000000-0000-0000-0000-000000000000";
    final OkapiHeaders okapiHeaders = getOkapiHeadersFromContext()
      .withRequestId("check-in-by-barcode-request")
      .withUserId(invalidUuid);

    loansFixture.checkInByBarcode(item, fixedCheckInDateTime, servicePointId, okapiHeaders);

    JsonObject lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    DateTime actualCheckinDateTime = JsonPropertyFetcher
      .getDateTimeProperty(lastCheckIn, "dateTime");

    assertThat(actualCheckinDateTime, is(fixedCheckInDateTime));
    assertThat(lastCheckIn.getString("servicePointId"),
      is(servicePointId.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is(invalidUuid));
  }

  @Test
  public void shouldBeAbleToCheckinItemWithoutLoan() {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID servicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkInByBarcode(item, fixedCheckInDateTime, servicePointId);

    JsonObject lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    DateTime actualCheckinDateTime = JsonPropertyFetcher
      .getDateTimeProperty(lastCheckIn, "dateTime");

    assertThat(actualCheckinDateTime, is(fixedCheckInDateTime));
    assertThat(lastCheckIn.getString("servicePointId"), is(servicePointId.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is(APITestContext.getUserId()));
  }

  @Test
  public void shouldBeAbleCheckinItemWithoutLoanMultipleTimes() {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID servicePointId = servicePointsFixture.cd1().getId();
    DateTime firstCheckInDateTime = DateTime.now(UTC);

    loansFixture.checkInByBarcode(item, firstCheckInDateTime, servicePointId);
    JsonObject lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    DateTime actualCheckinDateTime = JsonPropertyFetcher
      .getDateTimeProperty(lastCheckIn, "dateTime");

    assertThat(actualCheckinDateTime, is(fixedCheckInDateTime));
    assertThat(lastCheckIn.getString("servicePointId"), is(servicePointId.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is(APITestContext.getUserId()));

    DateTime secondCheckInDateTime = ClockManager.getClockManager().getDateTime();
    UUID servicePointId2 = servicePointsFixture.cd2().getId();

    final String randomUserId = UUID.randomUUID().toString();

    final OkapiHeaders okapiHeaders = getOkapiHeadersFromContext()
      .withRequestId("check-in-by-barcode-request")
      .withUserId(randomUserId);

    loansFixture.checkInByBarcode(item, secondCheckInDateTime, servicePointId2,
      okapiHeaders);

    lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    actualCheckinDateTime = JsonPropertyFetcher
      .getDateTimeProperty(lastCheckIn, "dateTime");

    assertThat(actualCheckinDateTime, is(fixedCheckInDateTime));
    assertThat(lastCheckIn.getString("servicePointId"), is(servicePointId2.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is(randomUserId));
  }

  @Test
  public void shouldDisplaySystemDateIfCheckinWasBackdated() {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID servicePointId = servicePointsFixture.cd1().getId();
    DateTime checkInDateTimeInPast = ClockManager.getClockManager().getDateTime()
      .minusHours(1);

    loansFixture.checkInByBarcode(item, checkInDateTimeInPast, servicePointId);
    JsonObject lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    DateTime actualCheckinDateTime = JsonPropertyFetcher
      .getDateTimeProperty(lastCheckIn, "dateTime");

    assertThat(actualCheckinDateTime, is(fixedCheckInDateTime));
  }
}
