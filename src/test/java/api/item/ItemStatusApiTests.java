package api.item;

import static api.support.matchers.TextDateTimeMatcher.withinSecondsAfter;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.joda.time.Seconds.seconds;
import static org.hamcrest.MatcherAssert.assertThat;

import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import api.support.APITests;
import io.vertx.core.json.JsonObject;

public class ItemStatusApiTests extends APITests {

  private static final String ITEM_STATUS = "status";
  private static final String ITEM_STATUS_DATE = "date";

  @Test
  public void itemStatusDateShouldExistsAfterCheckout() {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource user = usersFixture.jessica();
    final DateTime beforeCheckOutDatetime = DateTime.now(DateTimeZone.UTC);

    loansFixture.checkOutByBarcode(item, user, new DateTime(DateTimeZone.UTC));

    JsonObject checkedOutItem = itemsClient.get(item.getId()).getJson();

    assertThat(checkedOutItem.getJsonObject(ITEM_STATUS).getString(ITEM_STATUS_DATE),
      is(notNullValue()));
    assertThat(checkedOutItem.getJsonObject(ITEM_STATUS).getString(ITEM_STATUS_DATE),
      withinSecondsAfter(seconds(2), beforeCheckOutDatetime)
    );
  }
}
