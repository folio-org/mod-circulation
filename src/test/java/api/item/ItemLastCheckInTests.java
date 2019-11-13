package api.item;

import api.support.APITestContext;
import api.support.APITests;
import io.vertx.core.json.JsonObject;
import java.util.UUID;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class ItemLastCheckInTests extends APITests {

  @Test
  public void itemHasLastCheckInFields() throws InterruptedException,
    MalformedURLException, TimeoutException, ExecutionException {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource user = usersFixture.jessica();
    UUID servicePointId = servicePointsFixture.cd1().getId();
    DateTime now = DateTime.now();

    loansFixture.checkOutByBarcode(item, user, new DateTime(DateTimeZone.UTC));

    loansFixture.checkInByBarcode(item, now, servicePointId);
    JsonObject actualItem = itemsClient.get(item.getId()).getJson();

    JsonObject lastCheckIn = actualItem.getJsonObject("lastCheckIn");

    assertThat(lastCheckIn.getString("dateTime"), is(now.toString()));
    assertThat(lastCheckIn.getString("servicePointId"), is(servicePointId.toString()));
    assertThat(lastCheckIn.getString("staffMemberId"), is(APITestContext.USER_ID));
  }
}
