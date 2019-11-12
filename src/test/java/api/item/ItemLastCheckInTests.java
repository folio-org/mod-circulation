package api.item;

import api.support.APITests;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class ItemLastCheckInTests extends APITests {

  @Test
  public void itemHasLastCheckInFields() throws InterruptedException,
    MalformedURLException, TimeoutException, ExecutionException {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource user = usersFixture.jessica();

    loansFixture.checkOutByBarcode(item, user, new DateTime(DateTimeZone.UTC));
    loansFixture.checkInByBarcode(item);
    JsonObject itemRepresentation = itemsClient.get(item.getId()).getJson();

    JsonObject lastCheckInRepresentation = itemRepresentation.getJsonObject("lastCheckIn");
    assertThat(lastCheckInRepresentation.getString("staffMemberId"), is(notNullValue()));
    assertThat(lastCheckInRepresentation.getString("servicePointId"), is(notNullValue()));
    assertThat(lastCheckInRepresentation.getString("staffMemberId"), is(notNullValue()));
  }
}
