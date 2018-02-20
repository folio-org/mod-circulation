package org.folio.circulation.api.support.fixtures;

import org.folio.circulation.api.support.builders.RequestBuilder;
import org.folio.circulation.api.support.http.ResourceClient;
import org.folio.circulation.support.http.client.IndividualResource;

import java.net.MalformedURLException;
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
}
