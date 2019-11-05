package api.requests;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import api.support.http.InventoryItemResource;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.support.http.client.IndividualResource;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

public class ItemsInTransitReportTests extends APITests {

  private static final String LOCATION_NAME = "name";
  private static final String LOCATION_CODE = "code";
  private static final String LIBRARY = "libraryName";
  private static final String STATUS_KEY = "status";
  private static final String BARCODE_KEY = "barcode";
  private static final String TITLE = "title";
  private static final String CONTRIBUTORS = "contributors";
  private static final String DESTINATION_SERVICE_POINT = "inTransitDestinationServicePointId";

  @Test
  public void reportIsEmptyWhenThereAreNoItemsInTransit()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    List<JsonObject> items = ResourceClient.forInventoryReport(client).getAll();

    assertTrue(items.isEmpty());
  }

  @Test
  public void reportWhenThereAreItemInTransit()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final JsonObject smallAngryPlanetInstance = smallAngryPlanet.getInstance().getJson();
    final String contributors = String.valueOf(((JsonArray) smallAngryPlanetInstance
      .getMap().get(CONTRIBUTORS)).getJsonObject(0).getMap().get("name"));

    // init for SP2
    final IndividualResource steve = usersFixture.steve();
    final UUID secondServicePointId = servicePointsFixture.cd2().getId();

    //#1 checkout item in SP1
    loansFixture.checkOutByBarcode(smallAngryPlanet);

    // #2 create the request in SP2
    RequestBuilder secondRequestBuilderOnItem = new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(secondServicePointId)
      .forItem(smallAngryPlanet)
      .by(steve);
    requestsClient.create(secondRequestBuilderOnItem);

    // #3 check-in item in SP1
    loansFixture.checkInByBarcode(smallAngryPlanet);

    // #4 get items report with in transit status
    List<JsonObject> items = ResourceClient.forInventoryReport(client).getAll();

    assertThat(items.size(), is(1));
    JsonObject itemJson = items.get(0);
    assertThat(itemJson.getString(BARCODE_KEY), is(smallAngryPlanet.getBarcode()));
    assertThat(itemJson.getJsonObject(STATUS_KEY).getMap().get("name"),
      is(ItemStatus.IN_TRANSIT.getValue()));
    assertThat(itemJson.getString(DESTINATION_SERVICE_POINT), is(String.valueOf(secondServicePointId)));
    assertThat(itemJson.getString(TITLE), is(smallAngryPlanetInstance.getString(TITLE)));
    assertThat(itemJson.getJsonArray(CONTRIBUTORS)
      .getJsonObject(0).getMap().get("name"), is(contributors));
    Map<String, String> actualLocation = (Map<String, String>) itemJson.getMap().get("location");
    assertThat(actualLocation.get(LOCATION_NAME), is("3rd Floor"));
    assertThat(actualLocation.get(LOCATION_CODE), is("NU/JC/DL/3F"));
    assertThat(actualLocation.get(LIBRARY), is("Djanogly Learning Resource Centre"));
  }
}
