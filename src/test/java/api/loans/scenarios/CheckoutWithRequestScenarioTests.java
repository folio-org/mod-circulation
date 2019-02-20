package api.loans.scenarios;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import api.support.http.InventoryItemResource;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class CheckoutWithRequestScenarioTests extends APITests {

  @Test
  public void canCheckoutPagedItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource charlotte = usersFixture.charlotte();

    requestsClient.create(new RequestBuilder()
      .page()
      .forItem(smallAngryPlanet)
      .by(charlotte));

    loansFixture.checkOutByBarcode(smallAngryPlanet, charlotte);

    Response changedItem = itemsClient.getById(smallAngryPlanet.getId());

    assertThat(changedItem.getJson().getJsonObject("status").getString("name"),
      is("Checked out"));
  }
}
