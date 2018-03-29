package api.loans;

import api.support.APITests;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static api.support.matchers.JsonObjectMatchers.hasSoleErrorMessageContaining;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class CheckOutByBarcodeTests extends APITests {
  @Test
  public void canCreateALoanUsingItemAndUserBarcode()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final IndividualResource response = loansFixture.checkOutByBarcode(smallAngryPlanet, steve);

    final JsonObject loan = response.getJson();

    assertThat(loan.getString("id"), is(notNullValue()));

    assertThat("status is not open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("user ID should match barcode",
      loan.getString("userId"), is(steve.getId().toString()));
  }

  @Test
  public void cannotCreateALoanForUnknownUser()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    usersClient.delete(steve.getId());

    final Response response = loansFixture.attemptCheckOutByBarcode(smallAngryPlanet, steve);

    assertThat(response.getJson(), hasSoleErrorMessageContaining(
      "Could not find user with matching barcode"));
  }
}
