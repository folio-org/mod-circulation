package api.item;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasNullParameter;
import static org.folio.circulation.support.http.OkapiHeader.USER_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.joda.time.DateTimeZone.UTC;

import java.util.UUID;

import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.JsonPropertyFetcher;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.junit.Test;

import api.support.APITestContext;
import api.support.APITests;
import api.support.builders.CheckInByBarcodeRequestBuilder;
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
  public void cannotCheckInWhenNoLoggedInUser() {
    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID servicePointId = servicePointsFixture.cd1().getId();

    final OkapiHeaders okapiHeaders = getOkapiHeadersFromContext()
      .withRequestId("check-in-by-barcode-request")
      .withUserId("");

    Response response = loansFixture.attemptCheckInByBarcode(
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
  public void shouldNotFailCheckInWithInvalidLoggedInUserId() {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID servicePointId = servicePointsFixture.cd1().getId();

    String invalidUuid = "a982bc60-c6e4-4159-b997-ad382a3cbd9b";
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
