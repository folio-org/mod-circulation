package org.folio.circulation.api.requests.scenarios;

import org.folio.circulation.api.support.APITests;
import org.folio.circulation.api.support.builders.RequestBuilder;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.folio.circulation.api.support.builders.RequestBuilder.CLOSED_FILLED;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class SingleClosedRequestTests extends APITests {
  @Test
  public void closedRequestDoesNotStopCheckOutToRequester()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    IndividualResource loanToJames = loansFixture.checkOut(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsClient.create(new RequestBuilder()
      .hold()
      .fulfilToHoldShelf()
      .withRequestDate(new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC))
      .fulfilled() //TODO: Replace with closed cancelled when introduced
      .withItemId(smallAngryPlanet.getId())
      .withRequesterId(jessica.getId()));

    loansFixture.checkIn(loanToJames);

    loansFixture.checkOut(smallAngryPlanet, jessica);

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request.getJson().getString("status"), is(CLOSED_FILLED));

    Response item = itemsClient.getById(smallAngryPlanet.getId());

    assertThat(item.getStatusCode(), is(HTTP_OK));
    assertThat(item.getJson().getJsonObject("status").getString("name"), is("Checked out"));
  }

  @Test
  public void closedRequestDoesNotStopCheckOutToOtherPatron()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();

    IndividualResource loanToJames = loansFixture.checkOut(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsClient.create(new RequestBuilder()
      .hold()
      .fulfilToHoldShelf()
      .withRequestDate(new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC))
      .fulfilled() //TODO: Replace with closed cancelled when introduced
      .withItemId(smallAngryPlanet.getId())
      .withRequesterId(jessica.getId()));

    loansFixture.checkIn(loanToJames);

    loansFixture.checkOut(smallAngryPlanet, steve);

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request.getJson().getString("status"), is(CLOSED_FILLED));

    Response item = itemsClient.getById(smallAngryPlanet.getId());

    assertThat(item.getStatusCode(), is(HTTP_OK));
    assertThat(item.getJson().getJsonObject("status").getString("name"), is("Checked out"));
  }
}
