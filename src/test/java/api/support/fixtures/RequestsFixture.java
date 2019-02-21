package api.support.fixtures;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;

import api.support.RestAssuredClient;
import api.support.builders.RequestBuilder;
import api.support.http.InterfaceUrls;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class RequestsFixture {
  private final ResourceClient requestsClient;
  private final CancellationReasonsFixture cancellationReasonsFixture;
  private final ServicePointsFixture servicePointsFixture;

  public RequestsFixture(
    ResourceClient requestsClient,
    CancellationReasonsFixture cancellationReasonsFixture,
    ServicePointsFixture servicePointsFixture) {

    this.requestsClient = requestsClient;
    this.cancellationReasonsFixture = cancellationReasonsFixture;
    this.servicePointsFixture = servicePointsFixture;
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
      .withRequesterId(by.getId())
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));
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
      UUID pickupServicePointId)
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException {

    return place(new RequestBuilder()
        .hold()
        .fulfilToHoldShelf()
        .withItemId(item.getId())
        .withRequestDate(on)
        .withRequesterId(by.getId())
        .withPickupServicePointId(pickupServicePointId));
  }


  public IndividualResource placeHoldShelfRequest(
      IndividualResource item,
      IndividualResource by,
      DateTime on,
      UUID pickupServicePointId,
      String type)
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException {

    return place(new RequestBuilder()
        .hold()
        .withRequestType(type)
        .fulfilToHoldShelf()
        .withItemId(item.getId())
        .withRequestDate(on)
        .withRequesterId(by.getId())
        .withPickupServicePointId(pickupServicePointId));
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

    final IndividualResource courseReservesCancellationReason
      = cancellationReasonsFixture.courseReserves();

    final RequestBuilder cancelledRequestBySteve = RequestBuilder.from(request)
      .cancelled()
      .withCancellationReasonId(courseReservesCancellationReason.getId());

    requestsClient.replace(request.getId(), cancelledRequestBySteve);
  }

  public MultipleRecords<JsonObject> getQueueFor(IndividualResource item) {
    //TODO: Replace with better parsing
    return MultipleRecords.from(
      RestAssuredClient.from(
        RestAssuredClient.get(InterfaceUrls.requestQueueUrl(item.getId()),
      200, "request-queue-request")),
      Function.identity() ,"requests").value();
  }
}
