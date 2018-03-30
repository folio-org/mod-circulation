package api.loans;

import api.APITestSuite;
import api.support.APITests;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.joda.time.DateTime;
import org.joda.time.Seconds;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static api.support.matchers.JsonObjectMatchers.hasSoleErrorMessageContaining;
import static api.support.matchers.TextDateTimeMatcher.withinSecondsAfter;
import static java.net.HttpURLConnection.HTTP_OK;
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

    DateTime requestMade = DateTime.now();

    final IndividualResource response = loansFixture.checkOutByBarcode(smallAngryPlanet, steve);

    final JsonObject loan = response.getJson();

    assertThat(loan.getString("id"), is(notNullValue()));

    assertThat("user ID should match barcode",
      loan.getString("userId"), is(steve.getId().toString()));

    assertThat("item ID should match barcode",
      loan.getString("itemId"), is(smallAngryPlanet.getId().toString()));

    assertThat("status should be open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action should be checkedout",
      loan.getString("action"), is("checkedout"));

    assertThat("loan date should match when request was made",
      loan.getString("loanDate"), withinSecondsAfter(Seconds.seconds(5), requestMade));
  }

  @Test
  public void canGetLoanCreatedWhilstCheckingOut()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final IndividualResource response = loansFixture.checkOutByBarcode(smallAngryPlanet, steve);

    assertThat("Location header should be present",
      response.getLocation(), is(notNullValue()));

    final CompletableFuture<Response> completed = new CompletableFuture<>();

    client.get(APITestSuite.circulationModuleUrl(response.getLocation()),
      ResponseHandler.json(completed));

    final Response getResponse = completed.get(2, TimeUnit.SECONDS);

    assertThat(getResponse.getStatusCode(), is(HTTP_OK));
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

  @Test
  public void cannotCreateALoanForUnknownItem()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    itemsClient.delete(smallAngryPlanet.getId());

    final Response response = loansFixture.attemptCheckOutByBarcode(smallAngryPlanet, steve);

    assertThat(response.getJson(),
      hasSoleErrorMessageContaining("No item with barcode 036000291452 exists"));
  }
}
