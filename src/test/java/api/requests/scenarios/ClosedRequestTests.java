/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package api.requests.scenarios;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static api.APITestSuite.courseReservesCancellationReasonId;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.UUIDMatcher.is;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class ClosedRequestTests extends APITests {
  
  @Test
  public void canCancelARequest()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    loansFixture.checkOut(smallAngryPlanet, usersFixture.jessica());

    IndividualResource requester = usersFixture.steve();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    final IndividualResource request =
      requestsFixture.placeHoldShelfRequest(smallAngryPlanet, requester, requestDate);

    DateTime cancelDate = new DateTime(2018, 1, 14, 8, 30, 45, DateTimeZone.UTC);

    requestsClient.replace(request.getId(),
      RequestBuilder.from(request)
        .cancelled()
        .withCancellationReasonId(courseReservesCancellationReasonId())
        .withCancelledByUserId(requester.getId())
        .withCancelledDate(cancelDate));

    IndividualResource getRequest = requestsClient.get(request.getId());

    JsonObject getRepresentation = getRequest.getJson();

    assertThat(getRepresentation.getString("id"), is(request.getId()));
    assertThat(getRepresentation.getString("status"), is("Closed - Cancelled"));
    assertThat(getRepresentation.getString("cancelledByUserId"), is(requester.getId().toString()));
    assertThat(getRepresentation.getString("cancelledDate"), isEquivalentTo(cancelDate));
  }
}
