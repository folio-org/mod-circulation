package api.item;

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
import io.vertx.core.json.JsonObject;

public class ItemLastCheckInTests extends APITests {

  @Test
  public void checkedInItemWithLoanShouldHaveLastCheckedInFields() throws InterruptedException,
    MalformedURLException, TimeoutException, ExecutionException {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource user = usersFixture.jessica();
    UUID servicePointId = servicePointsFixture.cd1().getId();
    DateTime checkInDate = DateTime.now();

    loansFixture.checkOutByBarcode(item, user, new DateTime(DateTimeZone.UTC));

    loansFixture.checkInByBarcode(item, checkInDate, servicePointId);
    JsonObject actualItem = itemsClient.get(item.getId()).getJson();

    JsonObject lastCheckIn = actualItem.getJsonObject("lastCheckIn");

    assertThat(lastCheckIn.getString("dateTime"), is(checkInDate.toString()));
    assertThat(lastCheckIn.getString("servicePointId"), is(servicePointId.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is(APITestContext.getUserId()));
  }


  @Test
  public void shouldNotFailCheckInWhenInvalidOrEmptyLoggedInUserId() throws InterruptedException,
    MalformedURLException, TimeoutException, ExecutionException {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource user = usersFixture.jessica();
    UUID servicePointId = servicePointsFixture.cd1().getId();
    DateTime checkInDate = DateTime.now();
    String defaultUserId = APITestContext.getUserId();

    APITestContext.setUserId("null");
    loansFixture.checkOutByBarcode(item, user, new DateTime(DateTimeZone.UTC));

    loansFixture.checkInByBarcode(item, checkInDate, servicePointId);
    JsonObject lastCheckIn = itemsClient.get(item.getId()).getJson().getJsonObject("lastCheckIn");

    assertThat(lastCheckIn.getString("dateTime"), is(checkInDate.toString()));
    assertThat(lastCheckIn.getString("servicePointId"), is(servicePointId.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is(nullValue()));

    APITestContext.setUserId("");
    loansFixture.checkInByBarcode(item, checkInDate, servicePointId);
    lastCheckIn = itemsClient.get(item.getId()).getJson().getJsonObject("lastCheckIn");

    assertThat(lastCheckIn.getString("dateTime"), is(checkInDate.toString()));
    assertThat(lastCheckIn.getString("servicePointId"), is(servicePointId.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is(nullValue()));

    APITestContext.setUserId(defaultUserId);
  }


  @Test
  public void shouldBeAbleToCheckinItemWithoutLoan() throws InterruptedException,
    MalformedURLException, TimeoutException, ExecutionException {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID servicePointId = servicePointsFixture.cd1().getId();
    DateTime checkInDate = DateTime.now();

    loansFixture.checkInByBarcode(item, checkInDate, servicePointId);
    JsonObject actualItem = itemsClient.get(item.getId()).getJson();

    JsonObject lastCheckIn = actualItem.getJsonObject("lastCheckIn");

    assertThat(lastCheckIn.getString("dateTime"), is(checkInDate.toString()));
    assertThat(lastCheckIn.getString("servicePointId"), is(servicePointId.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is(APITestContext.getUserId()));
  }

  @Test
  public void shouldBeAbleCheckinItemWithoutLoanMultipleTimes() throws InterruptedException,
    MalformedURLException, TimeoutException, ExecutionException {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID servicePointId = servicePointsFixture.cd1().getId();
    DateTime firstCheckInDate = DateTime.now();

    loansFixture.checkInByBarcode(item, firstCheckInDate, servicePointId);
    JsonObject actualItem = itemsClient.get(item.getId()).getJson();

    JsonObject lastCheckIn = actualItem.getJsonObject("lastCheckIn");

    assertThat(lastCheckIn.getString("dateTime"), is(firstCheckInDate.toString()));
    assertThat(lastCheckIn.getString("servicePointId"), is(servicePointId.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is(APITestContext.getUserId()));

    loansFixture.checkInByBarcode(item, DateTime.now(), servicePointId);
    loansFixture.checkInByBarcode(item, DateTime.now(), servicePointId);
    loansFixture.checkInByBarcode(item, DateTime.now(), servicePointId);
    actualItem = itemsClient.get(item.getId()).getJson();

    lastCheckIn = actualItem.getJsonObject("lastCheckIn");

    assert (DateTime.parse(lastCheckIn.getString("dateTime"))
      .isAfter(firstCheckInDate));
    assertThat(lastCheckIn.getString("servicePointId"), is(servicePointId.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is(APITestContext.getUserId()));
  }
}
