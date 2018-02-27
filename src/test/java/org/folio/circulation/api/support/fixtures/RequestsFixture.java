package org.folio.circulation.api.support.fixtures;

import org.folio.circulation.api.support.builders.RequestBuilder;
import org.folio.circulation.api.support.http.ResourceClient;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class RequestsFixture {
  private final ResourceClient requestsClient;

  public RequestsFixture(ResourceClient requestsClient) {
    this.requestsClient = requestsClient;
  }

  public IndividualResource placeHoldShelfRequest(
    IndividualResource item,
    IndividualResource by)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return requestsClient.create(new RequestBuilder()
      .hold()
      .fulfilToHoldShelf()
      .withItemId(item.getId())
      .withRequesterId(by.getId()));
  }

  public IndividualResource placeHoldShelfRequest(
    IndividualResource item,
    IndividualResource by,
    DateTime on)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return requestsClient.create(new RequestBuilder()
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

    return requestsClient.create(new RequestBuilder()
      .hold()
      .deliverToAddress(UUID.randomUUID())
      .withRequestDate(on)
      .withItemId(item.getId())
      .withRequesterId(by.getId()));
  }
}
