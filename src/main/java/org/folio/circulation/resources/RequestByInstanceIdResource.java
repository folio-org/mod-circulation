package org.folio.circulation.resources;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.RandomStringUtils;
import org.folio.circulation.domain.RequestFulfilmentPreference;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.representations.RequestByInstanceIdRequest;
import org.folio.circulation.support.CreatedJsonResponseResult;
import org.folio.circulation.support.JsonPropertyWriter;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.joda.time.format.ISODateTimeFormat;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class RequestByInstanceIdResource extends Resource {
  public RequestByInstanceIdResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(
      "/circulation/requests/instances", router);

    routeRegistration.create(this::requestByInstanceId);
  }

  private void requestByInstanceId(RoutingContext routingContext) {
    final Result<RequestByInstanceIdRequest> requestResult =
        RequestByInstanceIdRequest.from(routingContext.getBodyAsJson());

    CompletableFuture.completedFuture(requestResult)
      .thenApply(r -> r.map(request -> {
        // Need to fill in the real implementation via CIRC-245.
        // For now, we'll return some sample data.
        final JsonObject responseBody = new JsonObject();
        responseBody.put("id", UUID.randomUUID().toString());
        responseBody.put("requestType", RequestType.HOLD.getValue());
        responseBody.put("requestDate",
            request.getRequestDate().toString(ISODateTimeFormat.dateTime()));
        responseBody.put("requesterId", request.getRequesterId().toString());
        responseBody.put("itemId", UUID.randomUUID().toString());
        responseBody.put("status", "Open - Not yet filled");
        responseBody.put("position", 1);

        final JsonObject item = new JsonObject();
        item.put("title", "Title Level Request");
        item.put("barcode", RandomStringUtils.random(12, false, true));
        item.put("holdingsRecordId", UUID.randomUUID().toString());
        item.put("instanceId", request.getInstanceId().toString());
        item.put("location", new JsonObject().put("name", "SECOND FLOOR"));
        item.put("contributorNames", new JsonArray().add(new JsonObject().put("name", "Guy, Some")));
        item.put("status", "Checked out");
        item.put("callNumber", "12345678");

        responseBody.put("item", item);

        final JsonObject requester = new JsonObject();
        requester.put("lastName", "Public");
        requester.put("firstName", "John");
        requester.put("middleName", "Q");
        requester.put("barcode", RandomStringUtils.random(15, false, true));
        requester.put("patronGroup", new JsonObject()
            .put("id", UUID.randomUUID().toString())
            .put("group", "student")
            .put("desc", "Student"));
        requester.put("patronGroupId", UUID.randomUUID().toString());

        responseBody.put("requester", requester);

        responseBody.put("fulfilmentPreference", RequestFulfilmentPreference.HOLD_SHELF.getValue());

        JsonPropertyWriter.write(responseBody, "requestExpirationDate",
            request.getRequestExpirationDate());
        responseBody.put("pickupServicePointId", request.getPickupServicePointId().toString());
        return responseBody;
      }))
      .thenApply(CreatedJsonResponseResult::from)
      .thenAccept(r -> r.writeTo(routingContext.response()));
  }
}
