package api.item;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasNullParameter;
import static java.time.ZoneOffset.UTC;
import static org.folio.circulation.support.http.OkapiHeader.USER_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.json.JsonPropertyFetcher;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import api.support.APITestContext;
import api.support.APITests;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.http.IndividualResource;
import api.support.http.OkapiHeaders;
import io.vertx.core.json.JsonObject;

class ItemLastCheckInTests extends APITests {

  private static final ZonedDateTime fixedCheckInDateTime = ZonedDateTime.of(2019, 4, 3, 2, 10, 0, 0, UTC);

  @BeforeEach
  public void beforeEach() {
    mockClockManagerToReturnFixedDateTime(fixedCheckInDateTime);
  }

  @AfterEach
  public void afterEach() {
    mockClockManagerToReturnDefaultDateTime();
  }

  @Test
  void checkedInItemWithLoanShouldHaveLastCheckedInFields() {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource user = usersFixture.jessica();
    UUID servicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(item, user);
    checkInFixture.checkInByBarcode(item, ClockUtil.getZonedDateTime(), servicePointId);
    JsonObject lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    ZonedDateTime actualCheckinDateTime = JsonPropertyFetcher
      .getDateTimeProperty(lastCheckIn, "dateTime");

    assertThat(actualCheckinDateTime, is(fixedCheckInDateTime));
    assertThat(lastCheckIn.getString("servicePointId"),
      is(servicePointId.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"),
      is(APITestContext.getUserId()));
  }

  @Test
  void cannotCheckInWhenNoLoggedInUser() {
    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID servicePointId = servicePointsFixture.cd1().getId();

    final OkapiHeaders okapiHeaders = getOkapiHeadersFromContext()
      .withRequestId("check-in-by-barcode-request")
      .withUserId("");

    Response response = checkInFixture.attemptCheckInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(item)
        .at(servicePointId)
        .on(fixedCheckInDateTime), okapiHeaders);

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson(), hasErrorWith(
      hasMessage("No logged-in user present")
    ));
    assertThat(response.getJson(), hasErrorWith(
      hasNullParameter(USER_ID)
    ));
  }

  @Test
  void shouldNotFailCheckInWithInvalidLoggedInUserId() {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID servicePointId = servicePointsFixture.cd1().getId();

    String invalidUuid = "a982bc60-c6e4-4159-b997-ad382a3cbd9b";
    final OkapiHeaders okapiHeaders = getOkapiHeadersFromContext()
      .withRequestId("check-in-by-barcode-request")
      .withUserId(invalidUuid);

    checkInFixture.checkInByBarcode(item, fixedCheckInDateTime, servicePointId, okapiHeaders);

    JsonObject lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    ZonedDateTime actualCheckinDateTime = JsonPropertyFetcher
      .getDateTimeProperty(lastCheckIn, "dateTime");

    assertThat(actualCheckinDateTime, is(fixedCheckInDateTime));
    assertThat(lastCheckIn.getString("servicePointId"),
      is(servicePointId.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is(invalidUuid));
  }

  @Test
  void shouldBeAbleToCheckinItemWithoutLoan() {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID servicePointId = servicePointsFixture.cd1().getId();

    checkInFixture.checkInByBarcode(item, fixedCheckInDateTime, servicePointId);

    JsonObject lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    ZonedDateTime actualCheckinDateTime = JsonPropertyFetcher
      .getDateTimeProperty(lastCheckIn, "dateTime");

    assertThat(actualCheckinDateTime, is(fixedCheckInDateTime));
    assertThat(lastCheckIn.getString("servicePointId"), is(servicePointId.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is(APITestContext.getUserId()));
  }

  @Test
  void shouldBeAbleCheckinItemWithoutLoanMultipleTimes() {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID servicePointId = servicePointsFixture.cd1().getId();
    ZonedDateTime firstCheckInDateTime = ClockUtil.getZonedDateTime();

    checkInFixture.checkInByBarcode(item, firstCheckInDateTime, servicePointId);
    JsonObject lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    ZonedDateTime actualCheckinDateTime = JsonPropertyFetcher
      .getDateTimeProperty(lastCheckIn, "dateTime");

    assertThat(actualCheckinDateTime, is(fixedCheckInDateTime));
    assertThat(lastCheckIn.getString("servicePointId"), is(servicePointId.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is(APITestContext.getUserId()));

    ZonedDateTime secondCheckInDateTime = ClockUtil.getZonedDateTime();
    UUID servicePointId2 = servicePointsFixture.cd2().getId();

    final String randomUserId = UUID.randomUUID().toString();

    final OkapiHeaders okapiHeaders = getOkapiHeadersFromContext()
      .withRequestId("check-in-by-barcode-request")
      .withUserId(randomUserId);

    checkInFixture.checkInByBarcode(item, secondCheckInDateTime, servicePointId2,
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
  void shouldDisplaySystemDateIfCheckinWasBackdated() {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID servicePointId = servicePointsFixture.cd1().getId();
    ZonedDateTime checkInDateTimeInPast = ClockUtil.getZonedDateTime()
      .minusHours(1);

    checkInFixture.checkInByBarcode(item, checkInDateTimeInPast, servicePointId);
    JsonObject lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    ZonedDateTime actualCheckinDateTime = JsonPropertyFetcher
      .getDateTimeProperty(lastCheckIn, "dateTime");

    assertThat(actualCheckinDateTime, is(fixedCheckInDateTime));
  }
}
