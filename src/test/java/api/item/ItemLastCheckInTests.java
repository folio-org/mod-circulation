package api.item;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import api.support.APITestContext;
import api.support.APITests;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

public class ItemLastCheckInTests extends APITests {

  @Override
  public void afterEach()
    throws InterruptedException, MalformedURLException, TimeoutException,
    ExecutionException {

    super.afterEach();
    APITestContext.defaultUserId();
  }

  @Test
  public void checkedInItemWithLoanShouldHaveLastCheckedInFields()
    throws InterruptedException, MalformedURLException, TimeoutException,
    ExecutionException {

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
  public void shouldNotFailCheckInWithEmptyLoggedInUserId()
    throws InterruptedException, MalformedURLException, TimeoutException,
    ExecutionException {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID servicePointId = servicePointsFixture.cd1().getId();
    DateTime checkInDate = DateTime.now();

    APITestContext.setUserId("");
    loansFixture.checkInByBarcode(item, checkInDate, servicePointId);
    JsonObject lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    assertThat(lastCheckIn.getString("dateTime"), is(checkInDate.toString()));
    assertThat(lastCheckIn.getString("servicePointId"),
      is(servicePointId.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is(nullValue()));
  }

  @Test
  public void shouldNotFailCheckInWithInvalidLoggedInUserId()
    throws InterruptedException, MalformedURLException, TimeoutException,
    ExecutionException {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID servicePointId = servicePointsFixture.cd1().getId();
    DateTime checkInDate = DateTime.now();

    APITestContext.setUserId("INVALID_UUID");
    loansFixture.checkInByBarcode(item, checkInDate, servicePointId);
    JsonObject lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    assertThat(lastCheckIn.getString("dateTime"), is(checkInDate.toString()));
    assertThat(lastCheckIn.getString("servicePointId"),
      is(servicePointId.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is(APITestContext.getUserId()));
  }

  @Test
  public void shouldBeAbleToCheckinItemWithoutLoan()
    throws InterruptedException, MalformedURLException, TimeoutException,
    ExecutionException {

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
  public void shouldBeAbleCheckinItemWithoutLoanMultipleTimes()
    throws InterruptedException, MalformedURLException, TimeoutException,
    ExecutionException {

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
    APITestContext.setUserId(UUID.randomUUID().toString());

    loansFixture.checkInByBarcode(item, secondCheckInDate, servicePointId2);
    lastCheckIn = itemsClient.get(item.getId()).getJson()
      .getJsonObject("lastCheckIn");

    assertThat(lastCheckIn.getString("dateTime"), is(secondCheckInDate.toString()));
    assertThat(lastCheckIn.getString("servicePointId"), is(servicePointId2.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is(APITestContext.getUserId()));
  }
}
