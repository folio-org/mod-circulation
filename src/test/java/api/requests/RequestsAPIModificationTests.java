/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package api.requests;

import static api.APITestSuite.courseReservesCancellationReasonId;
import api.support.APITests;
import api.support.builders.RequestBuilder;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import io.vertx.core.json.JsonObject;
import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import junitparams.JUnitParamsRunner;
import org.folio.circulation.support.http.client.IndividualResource;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith(JUnitParamsRunner.class)
public class RequestsAPIModificationTests extends APITests {
  
  //Create a request and then cancel it
  @Test
  public void canCreateAndCancelARequest() throws InterruptedException,
      MalformedURLException,
      TimeoutException, 
      ExecutionException {
    UUID requestId = UUID.randomUUID();
    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    loansFixture.checkOut(item, usersFixture.jessica());
    IndividualResource requester = usersFixture.steve();
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);
    
     IndividualResource request = requestsClient.create(new RequestBuilder()
      .withId(requestId)
      .open()
      .recall()
      .forItem(item)
      .by(requester)
      .withRequestDate(requestDate)
      .fulfilToHoldShelf()
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31)));

    JsonObject representation = request.getJson();
    
    assertThat(representation.getString("id"), is(requestId.toString()));
    assertThat(representation.getString("status"), is("Open - Not yet filled"));
    
    DateTime cancelDate = new DateTime(2018, 1, 14, 8, 30, 45, DateTimeZone.UTC);
    requestsClient.replace(requestId, new RequestBuilder()
      .withId(requestId)
      .cancelled()
      .recall()
      .forItem(item)
      .by(requester)
      .withRequestDate(requestDate)
      .fulfilToHoldShelf()
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31))
      .withCancellationReasonId(courseReservesCancellationReasonId())
      .withCancelledByUserId(requester.getId())
      .withCancelledDate(cancelDate));

    IndividualResource getRequest = requestsClient.get(requestId);
    JsonObject getRepresentation = getRequest.getJson();
    
    assertThat(getRepresentation.getString("id"), is(requestId.toString()));
    assertThat(getRepresentation.getString("status"), is("Closed - Cancelled"));
    assertThat(getRepresentation.getString("cancelledByUserId"), is(requester.getId().toString()));
    assertThat(getRepresentation.getString("cancelledDate"), isEquivalentTo(cancelDate));
    
  }
  
}
