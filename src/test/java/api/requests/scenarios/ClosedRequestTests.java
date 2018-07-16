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
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static api.APITestSuite.courseReservesCancellationReasonId;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.UUIDMatcher.is;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.hamcrest.CoreMatchers.allOf;
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

  @Test
  public void cannotEditCancelledRequest()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    loansFixture.checkOut(smallAngryPlanet, usersFixture.jessica());

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    final IndividualResource request =
      requestsFixture.placeHoldShelfRequest(smallAngryPlanet,
        usersFixture.steve(), requestDate);

    requestsFixture.cancelRequest(request);

    Response response = requestsClient.attemptReplace(request.getId(),
      RequestBuilder.from(request)
        .open()
        .withRequestExpiration(new LocalDate(2018, 3, 14)));

    assertThat(response.getStatusCode(), is(422));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Cannot edit a closed request"),
      hasParameter("id", request.getId().toString()))));
  }
}
