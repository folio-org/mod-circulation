package api.item;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.notNullValue;

import api.support.APITests;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class ItemStatusApiTests extends APITests {

  private static final String ITEM_STATUS = "status";
  private static final String ITEM_STATUS_DATE = "date";
  private static final String ITEM_BARCODE = "barcode";

  @Test
  public void itemStatusDateShouldExistsAfterCheckout() throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource user = usersFixture.jessica();

    loansFixture.checkOutByBarcode(item, user, new DateTime(DateTimeZone.UTC));

    JsonObject checkedOutItem = itemsClient.get(item.getId()).getJson();

    assertThat(checkedOutItem.getJsonObject(ITEM_STATUS)
        .getString(ITEM_STATUS_DATE), is(notNullValue()));
  }

  @Test
  public void itemStatusDateShouldNotChangedIfItemChanged() throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource user = usersFixture.jessica();

    loansFixture.checkOutByBarcode(item, user, new DateTime(DateTimeZone.UTC));

    JsonObject checkedOutItem = itemsClient.get(item.getId()).getJson();

    assertThat(checkedOutItem.getJsonObject(ITEM_STATUS)
      .getString(ITEM_STATUS_DATE), is(notNullValue()));

    String itemStatusDate = checkedOutItem.getJsonObject(ITEM_STATUS)
      .getString(ITEM_STATUS_DATE);

    JsonObject itemCopy = checkedOutItem.copy();
    itemCopy.remove(ITEM_BARCODE);
    itemCopy.put(ITEM_BARCODE, "1234567890");

    itemsClient.replace(item.getId(), itemCopy);

    JsonObject updatedItem = itemsClient.get(item.getId()).getJson();

    assertEquals(updatedItem.getJsonObject(ITEM_STATUS).getString(ITEM_STATUS_DATE), itemStatusDate);
    assertNotEquals(updatedItem.getString(ITEM_BARCODE), checkedOutItem.getString(ITEM_BARCODE));
  }
}
