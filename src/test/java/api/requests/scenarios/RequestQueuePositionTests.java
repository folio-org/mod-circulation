package api.requests.scenarios;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static api.APITestSuite.courseReservesCancellationReasonId;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

//TODO: Maybe move these tests to scenarios which better describe the situation
public class RequestQueuePositionTests extends APITests {
  @Test
  public void cancelledRequestShouldBeRemovedFromQueue()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();

    loansFixture.checkOut(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, new DateTime(2017, 10, 27, 11, 54, 37, DateTimeZone.UTC));

    IndividualResource requestByCharlotte = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC));

    //Cancel one of the requests
    final RequestBuilder cancelledRequestBySteve = RequestBuilder.from(requestBySteve)
      .cancelled()
      .withCancellationReasonId(courseReservesCancellationReasonId());

    requestsClient.replace(requestBySteve.getId(), cancelledRequestBySteve);

    requestBySteve = requestsClient.get(requestBySteve);

    assertThat("Should not have a position",
      requestBySteve.getJson().containsKey("position"), is(false));
  }
}
