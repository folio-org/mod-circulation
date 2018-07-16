package api.support.fixtures;

import api.support.builders.RequestBuilder;
import api.support.http.ResourceClient;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static api.APITestSuite.courseReservesCancellationReasonId;

public class RequestsFixture {
  private final ResourceClient requestsClient;

  public RequestsFixture(ResourceClient requestsClient) {
    this.requestsClient = requestsClient;
  }

  public IndividualResource place(RequestBuilder requestToBuild)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return requestsClient.create(requestToBuild);
  }

  public IndividualResource placeHoldShelfRequest(
    IndividualResource item,
    IndividualResource by,
    DateTime on)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return place(new RequestBuilder()
      .hold()
      .fulfilToHoldShelf()
      .withItemId(item.getId())
      .withRequestDate(on)
      .withRequesterId(by.getId()));
  }

  public IndividualResource placeDeliveryRequest(
    IndividualResource item,
    IndividualResource by,
    DateTime on)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return place(new RequestBuilder()
      .hold()
      .deliverToAddress(UUID.randomUUID())
      .withRequestDate(on)
      .withItemId(item.getId())
      .withRequesterId(by.getId()));
  }

  public IndividualResource placeHoldShelfRequest(
    IndividualResource item,
    IndividualResource by,
    DateTime on,
    String type)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return place(new RequestBuilder()
      .withRequestType(type)
      .deliverToAddress(UUID.randomUUID())
      .withRequestDate(on)
      .withItemId(item.getId())
      .withRequesterId(by.getId()));
  }

  public void cancelRequest(IndividualResource request)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    final RequestBuilder cancelledRequestBySteve = RequestBuilder.from(request)
      .cancelled()
      .withCancellationReasonId(courseReservesCancellationReasonId());

    requestsClient.replace(request.getId(), cancelledRequestBySteve);
  }
}
