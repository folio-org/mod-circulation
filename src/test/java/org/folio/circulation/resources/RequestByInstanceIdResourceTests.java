package org.folio.circulation.resources;

import static java.time.ZoneOffset.UTC;
import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.folio.circulation.domain.InstanceRequestRelatedRecords;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.representations.RequestByInstanceIdRequest;
import org.folio.circulation.support.BadRequestFailure;
import org.folio.circulation.support.ForwardOnFailure;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;

import api.support.fixtures.ItemExamples;
import io.vertx.core.json.JsonObject;

public class RequestByInstanceIdResourceTests {
  @Test
  void canTransformInstanceToItemRequests(){

    UUID loanTypeId = UUID.randomUUID();
    RequestByInstanceIdRequest requestByInstanceIdRequest = RequestByInstanceIdRequest.from(getJsonInstanceRequest(null)).value();
    List<Item > items = getItems(2, loanTypeId);

    InstanceRequestRelatedRecords records = new InstanceRequestRelatedRecords();
    records.setSortedAvailableItems(items);
    records.setInstanceLevelRequest(requestByInstanceIdRequest);

    final Result<List<JsonObject>> collectionResult = RequestByInstanceIdResource.instanceToItemRequests(records);
    assertTrue(collectionResult.succeeded());

    Collection<JsonObject> requestRepresentations = collectionResult.value();
    assertEquals(6, requestRepresentations.size());

    int i = 0;
    int j = 0;
    Item item = items.get(j);
    for (JsonObject itemRequestJson: requestRepresentations) {
      assertEquals(item.getItemId(), itemRequestJson.getString("itemId"));
      if (i == 0)
        assertEquals(RequestType.PAGE.getValue(), itemRequestJson.getString("requestType"));
      if (i == 1)
        assertEquals(RequestType.RECALL.getValue(), itemRequestJson.getString("requestType"));
      if (i == 2)
        assertEquals(RequestType.HOLD.getValue(), itemRequestJson.getString("requestType"));
      i++;

      if (i > 2) {
        i = 0;
        j++;
        if (j < 2) {
          item = items.get(j);
        }
      }
    }
  }

  @Test
  void getExpectedErrorMessages() {
    HttpFailure validationError = ValidationErrorFailure.singleValidationError
      (new ValidationError("validationError", "someParam", "null"));
    String errorMessage = RequestByInstanceIdResource.getErrorMessage(validationError);
    assertTrue(errorMessage.contains("validationError"));

    HttpFailure serverErrorFailure = new ServerErrorFailure("serverError");
    errorMessage = RequestByInstanceIdResource.getErrorMessage(serverErrorFailure);
    assertEquals("serverError", errorMessage);

    HttpFailure badRequestFailure = new BadRequestFailure("badRequestFailure");
    errorMessage = RequestByInstanceIdResource.getErrorMessage(badRequestFailure);
    assertEquals("badRequestFailure", errorMessage);

    Response fakeResponse = new Response(500, "fakeResponseFailure", "text/javascript");

    HttpFailure forwardOnFailure = new ForwardOnFailure(fakeResponse);
    errorMessage = RequestByInstanceIdResource.getErrorMessage(forwardOnFailure);
    assertEquals("fakeResponseFailure", errorMessage);
  }

  @Test
  void shouldConvertToValidationErrorForServerErrorFailure() {
    ServerErrorFailure serverErrorFailure = new ServerErrorFailure("Server failed");
    Collection<ValidationError> errors = RequestByInstanceIdResource.convertToValidationErrors(serverErrorFailure);

    assertEquals(1, errors.size());
    assertTrue(errors.contains(new ValidationError("Server failed")));
  }

  @Test
  void shouldConvertToValidationErrorForBadRequestFailure() {
    BadRequestFailure badRequestFailure = new BadRequestFailure("Bad request");
    Collection<ValidationError> errors = RequestByInstanceIdResource.convertToValidationErrors(badRequestFailure);

    assertEquals(1, errors.size());
    assertTrue(errors.contains(new ValidationError("Bad request")));
  }

  @Test
  void shouldConvertToValidationErrorForForwardOnFailure() {
    Response failureResponse = new Response(422, "test body", "json");
    ForwardOnFailure forwardFailure = new ForwardOnFailure(failureResponse);
    Collection<ValidationError> errors = RequestByInstanceIdResource.convertToValidationErrors(forwardFailure);

    assertEquals(1, errors.size());
    assertEquals("test body", errors.stream().toList().get(0).getMessage());
  }

  public static JsonObject getJsonInstanceRequest(UUID pickupServicePointId) {
    ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);
    ZonedDateTime requestExpirationDate = requestDate.plusDays(30);

    JsonObject instanceRequest = new JsonObject();
    instanceRequest.put("instanceId", UUID.randomUUID().toString());
    instanceRequest.put("requestDate", formatDateTime(requestDate));
    instanceRequest.put("requesterId", UUID.randomUUID().toString());
    instanceRequest.put("pickupServicePointId", pickupServicePointId == null ? UUID.randomUUID().toString() : pickupServicePointId.toString());
    instanceRequest.put("fulfillmentPreference", "Hold Shelf");
    instanceRequest.put("requestExpirationDate", formatDateTime(requestExpirationDate));

    return instanceRequest;
  }

  private static List<Item> getItems(int totalItems, UUID loanTypeId){
    LinkedList<Item> items = new LinkedList<>();
    for (int i = 0; i< totalItems; i++){
      JsonObject itemJsonObject = ItemExamples.basedUponSmallAngryPlanet(UUID.randomUUID(), loanTypeId).create();
      items.add(Item.from(itemJsonObject));
    }
    return items;
  }
}
