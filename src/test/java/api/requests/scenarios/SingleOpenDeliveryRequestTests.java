package api.requests.scenarios;

import api.support.APITests;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static api.support.builders.ItemBuilder.AVAILABLE;
import static api.support.builders.ItemBuilder.CHECKED_OUT_HELD;
import static api.support.builders.RequestBuilder.OPEN_NOT_YET_FILLED;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class SingleOpenDeliveryRequestTests extends APITests {
  @Test
  public void statusDoesNotChangesWhenItemCheckedIn()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    IndividualResource loanToJames = loansFixture.checkOut(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeDeliveryRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    loansFixture.checkIn(loanToJames);

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));
    assertThat(request.getJson().getInteger("position"), is(1));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(AVAILABLE));
  }

  @Test
  public void statusDoesNotChangeWhenItemCheckedOutToRequester()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    IndividualResource loanToJames = loansFixture.checkOut(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeDeliveryRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    loansFixture.checkIn(loanToJames);

    loansFixture.checkOut(smallAngryPlanet, jessica);

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    //As this request is still in the queue (as it cannot currently be fulfilled)
    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT_HELD));
  }

  @Test
  public void itemCanBeCheckedOutToAnotherPatron()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource rebecca = usersFixture.rebecca();

    IndividualResource loanToJames = loansFixture.checkOut(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeDeliveryRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    loansFixture.checkIn(loanToJames);

    loansFixture.checkOut(smallAngryPlanet, rebecca);

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    //As this request is still in the queue (as it cannot currently be fulfilled)
    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT_HELD));
  }
}
