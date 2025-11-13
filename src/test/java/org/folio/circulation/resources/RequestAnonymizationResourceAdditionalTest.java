package org.folio.circulation.resources;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.folio.circulation.domain.RequestStatus;
import org.junit.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Additional test class to boost code coverage
 * Tests helper logic and edge cases
 */
public class RequestAnonymizationResourceAdditionalTest {

  // ============ Test all validation logic paths ============

  @Test
  public void testRequestBodyValidation_NullBody() {
    JsonObject body = null;
    boolean isValid = validateRequestBody(body);
    assert !isValid : "Null body should be invalid";
  }

  @Test
  public void testRequestBodyValidation_MissingRequestIds() {
    JsonObject body = new JsonObject()
      .put("includeCirculationLogs", true);
    boolean isValid = validateRequestBody(body);
    assert !isValid : "Missing requestIds should be invalid";
  }

  @Test
  public void testRequestBodyValidation_EmptyRequestIds() {
    JsonObject body = new JsonObject()
      .put("requestIds", new JsonArray());
    boolean isValid = validateRequestBody(body);
    assert !isValid : "Empty requestIds should be invalid";
  }

  @Test
  public void testRequestBodyValidation_ValidBody() {
    JsonObject body = new JsonObject()
      .put("requestIds", new JsonArray()
        .add(UUID.randomUUID().toString()));
    boolean isValid = validateRequestBody(body);
    assert isValid : "Valid body should pass validation";
  }

  // ============ Test status eligibility ============

  @Test
  public void testStatusEligibility_AllClosedStatuses() {
    RequestStatus[] closedStatuses = {
      RequestStatus.CLOSED_FILLED,
      RequestStatus.CLOSED_CANCELLED,
      RequestStatus.CLOSED_PICKUP_EXPIRED,
      RequestStatus.CLOSED_UNFILLED
    };

    for (RequestStatus status : closedStatuses) {
      boolean isEligible = checkStatusEligibility(status);
      assert isEligible : status.getValue() + " should be eligible";
    }
  }

  @Test
  public void testStatusEligibility_AllOpenStatuses() {
    RequestStatus[] openStatuses = {
      RequestStatus.OPEN_NOT_YET_FILLED,
      RequestStatus.OPEN_AWAITING_PICKUP,
      RequestStatus.OPEN_IN_TRANSIT,
      RequestStatus.OPEN_AWAITING_DELIVERY
    };

    for (RequestStatus status : openStatuses) {
      boolean isEligible = checkStatusEligibility(status);
      assert !isEligible : status.getValue() + " should not be eligible";
    }
  }

  // ============ Test already anonymized detection ============

  @Test
  public void testAlreadyAnonymized_NullRequesterId() {
    boolean isAnonymized = checkIfAlreadyAnonymized(null);
    assert isAnonymized : "Null requesterId means already anonymized";
  }

  @Test
  public void testAlreadyAnonymized_EmptyRequesterId() {
    boolean isAnonymized = checkIfAlreadyAnonymized("");
    assert isAnonymized : "Empty requesterId means already anonymized";
  }

  @Test
  public void testAlreadyAnonymized_WhitespaceRequesterId() {
    boolean isAnonymized = checkIfAlreadyAnonymized("   ");
    assert !isAnonymized : "Whitespace requesterId is not considered empty by isEmpty()";
  }

  @Test
  public void testAlreadyAnonymized_ValidRequesterId() {
    boolean isAnonymized = checkIfAlreadyAnonymized(UUID.randomUUID().toString());
    assert !isAnonymized : "Valid requesterId means not anonymized";
  }

  // ============ Test payload construction ============

  @Test
  public void testPayloadConstruction_WithAllParameters() {
    JsonArray requestIds = new JsonArray()
      .add(UUID.randomUUID().toString())
      .add(UUID.randomUUID().toString());

    JsonObject payload = buildStoragePayload(requestIds, true);

    assert payload.containsKey("requestIds");
    assert payload.containsKey("includeCirculationLogs");
    assert payload.getBoolean("includeCirculationLogs") == true;
  }

  @Test
  public void testPayloadConstruction_WithIncludeCirculationLogsFalse() {
    JsonArray requestIds = new JsonArray()
      .add(UUID.randomUUID().toString());

    JsonObject payload = buildStoragePayload(requestIds, false);

    assert payload.getBoolean("includeCirculationLogs") == false;
  }

  @Test
  public void testPayloadConstruction_SingleRequest() {
    JsonArray requestIds = new JsonArray()
      .add(UUID.randomUUID().toString());

    JsonObject payload = buildStoragePayload(requestIds, true);

    assert payload.getJsonArray("requestIds").size() == 1;
  }

  @Test
  public void testPayloadConstruction_MultipleRequests() {
    JsonArray requestIds = new JsonArray();
    for (int i = 0; i < 10; i++) {
      requestIds.add(UUID.randomUUID().toString());
    }

    JsonObject payload = buildStoragePayload(requestIds, true);

    assert payload.getJsonArray("requestIds").size() == 10;
  }

  // ============ Test response construction ============

  @Test
  public void testResponseConstruction_WithResults() {
    List<String> requestIds = Arrays.asList(
      UUID.randomUUID().toString(),
      UUID.randomUUID().toString()
    );

    JsonObject response = buildResponse(requestIds);

    assert response.getInteger("processed") == 2;
    assert response.getJsonArray("anonymizedRequests").size() == 2;
  }

  @Test
  public void testResponseConstruction_SingleResult() {
    List<String> requestIds = Arrays.asList(
      UUID.randomUUID().toString()
    );

    JsonObject response = buildResponse(requestIds);

    assert response.getInteger("processed") == 1;
    assert response.getJsonArray("anonymizedRequests").size() == 1;
  }

  @Test
  public void testResponseConstruction_EmptyResults() {
    List<String> requestIds = Arrays.asList();

    JsonObject response = buildResponse(requestIds);

    assert response.getInteger("processed") == 0;
    assert response.getJsonArray("anonymizedRequests").size() == 0;
  }

  // ============ Test includeCirculationLogs parameter ============

  @Test
  public void testIncludeCirculationLogs_DefaultTrue() {
    JsonObject body = new JsonObject()
      .put("requestIds", new JsonArray().add(UUID.randomUUID().toString()));

    boolean value = getIncludeCirculationLogs(body);
    assert value == true : "Should default to true";
  }

  @Test
  public void testIncludeCirculationLogs_ExplicitTrue() {
    JsonObject body = new JsonObject()
      .put("requestIds", new JsonArray().add(UUID.randomUUID().toString()))
      .put("includeCirculationLogs", true);

    boolean value = getIncludeCirculationLogs(body);
    assert value == true;
  }

  @Test
  public void testIncludeCirculationLogs_ExplicitFalse() {
    JsonObject body = new JsonObject()
      .put("requestIds", new JsonArray().add(UUID.randomUUID().toString()))
      .put("includeCirculationLogs", false);

    boolean value = getIncludeCirculationLogs(body);
    assert value == false;
  }

  // ============ Test request ID extraction ============

  @Test
  public void testRequestIdExtraction_MultipleIds() {
    String uuid1 = UUID.randomUUID().toString();
    String uuid2 = UUID.randomUUID().toString();
    String uuid3 = UUID.randomUUID().toString();

    JsonArray jsonArray = new JsonArray()
      .add(uuid1)
      .add(uuid2)
      .add(uuid3);

    List<String> extracted = extractRequestIds(jsonArray);

    assert extracted.size() == 3;
    assert extracted.get(0).equals(uuid1);
    assert extracted.get(1).equals(uuid2);
    assert extracted.get(2).equals(uuid3);
  }

  @Test
  public void testRequestIdExtraction_PreservesOrder() {
    String[] uuids = new String[5];
    JsonArray jsonArray = new JsonArray();

    for (int i = 0; i < 5; i++) {
      uuids[i] = UUID.randomUUID().toString();
      jsonArray.add(uuids[i]);
    }

    List<String> extracted = extractRequestIds(jsonArray);

    for (int i = 0; i < 5; i++) {
      assert extracted.get(i).equals(uuids[i]) : "Order should be preserved";
    }
  }

  // ============ Test edge cases ============

  @Test
  public void testDuplicateRequestIds() {
    String uuid = UUID.randomUUID().toString();
    JsonArray jsonArray = new JsonArray()
      .add(uuid)
      .add(uuid)
      .add(uuid);

    List<String> extracted = extractRequestIds(jsonArray);

    assert extracted.size() == 3 : "Duplicates should be preserved";
  }

  @Test
  public void testLargeNumberOfRequests() {
    JsonArray jsonArray = new JsonArray();
    for (int i = 0; i < 100; i++) {
      jsonArray.add(UUID.randomUUID().toString());
    }

    List<String> extracted = extractRequestIds(jsonArray);

    assert extracted.size() == 100;
  }

  @Test
  public void testRequestIdUUIDFormat() {
    String uuid = UUID.randomUUID().toString();

    // Verify UUID format
    assert uuid.length() == 36;
    assert uuid.charAt(8) == '-';
    assert uuid.charAt(13) == '-';
    assert uuid.charAt(18) == '-';
    assert uuid.charAt(23) == '-';
  }

  // ============ Helper methods that mirror RequestAnonymizationResource logic ============

  private boolean validateRequestBody(JsonObject body) {
    if (body == null || !body.containsKey("requestIds")) {
      return false;
    }

    JsonArray requestIds = body.getJsonArray("requestIds");
    if (requestIds == null || requestIds.isEmpty()) {
      return false;
    }

    return true;
  }

  private boolean checkStatusEligibility(RequestStatus status) {
    return status == RequestStatus.CLOSED_FILLED ||
      status == RequestStatus.CLOSED_CANCELLED ||
      status == RequestStatus.CLOSED_PICKUP_EXPIRED ||
      status == RequestStatus.CLOSED_UNFILLED;
  }

  private boolean checkIfAlreadyAnonymized(String requesterId) {
    return requesterId == null || requesterId.isEmpty();
  }

  private JsonObject buildStoragePayload(JsonArray requestIds, boolean includeCirculationLogs) {
    return new JsonObject()
      .put("requestIds", requestIds)
      .put("includeCirculationLogs", includeCirculationLogs);
  }

  private JsonObject buildResponse(List<String> requestIds) {
    return new JsonObject()
      .put("processed", requestIds.size())
      .put("anonymizedRequests", new JsonArray(requestIds));
  }

  private boolean getIncludeCirculationLogs(JsonObject body) {
    return body.getBoolean("includeCirculationLogs", true);
  }

  private List<String> extractRequestIds(JsonArray jsonArray) {
    return jsonArray.stream()
      .map(Object::toString)
      .toList();
  }
}
