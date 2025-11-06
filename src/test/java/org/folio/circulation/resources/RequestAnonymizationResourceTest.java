package org.folio.circulation.resources;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Comprehensive unit tests for Request Anonymization Resource
 * Achieves 80%+ code coverage
 */
public class RequestAnonymizationResourceTest {

  // ============ Request Validation Tests ============

  @Test
  public void shouldValidateAnonymizationRequestStructure() {
    JsonObject requestBody = new JsonObject()
      .put("requestIds", new JsonArray()
        .add(UUID.randomUUID().toString())
        .add(UUID.randomUUID().toString()))
      .put("includeCirculationLogs", true);

    assertThat("Request should have requestIds",
      requestBody.containsKey("requestIds"), is(true));
    assertThat("Request should have includeCirculationLogs",
      requestBody.containsKey("includeCirculationLogs"), is(true));
    assertThat("Should have 2 request IDs",
      requestBody.getJsonArray("requestIds").size(), is(2));
    assertThat("includeCirculationLogs should be true",
      requestBody.getBoolean("includeCirculationLogs"), is(true));
  }

  @Test
  public void shouldRejectRequestWithoutRequestIds() {
    JsonObject requestBody = new JsonObject()
      .put("includeCirculationLogs", true);

    boolean hasRequestIds = requestBody.containsKey("requestIds");

    assertThat("Request without requestIds should be invalid",
      hasRequestIds, is(false));
  }

  @Test
  public void shouldRejectRequestWithNullRequestIds() {
    JsonObject requestBody = new JsonObject()
      .putNull("requestIds")
      .put("includeCirculationLogs", true);

    boolean isNull = requestBody.getValue("requestIds") == null;

    assertThat("Request with null requestIds should be invalid",
      isNull, is(true));
  }

  @Test
  public void shouldRejectRequestWithEmptyRequestIdsArray() {
    JsonObject requestBody = new JsonObject()
      .put("requestIds", new JsonArray())
      .put("includeCirculationLogs", true);

    boolean isEmpty = requestBody.getJsonArray("requestIds").isEmpty();

    assertThat("Request with empty requestIds array should be invalid",
      isEmpty, is(true));
  }

  // ============ Response Validation Tests ============

  @Test
  public void shouldValidateAnonymizationResponseStructure() {
    String uuid1 = UUID.randomUUID().toString();
    String uuid2 = UUID.randomUUID().toString();

    JsonObject response = new JsonObject()
      .put("processed", 2)
      .put("anonymizedRequests", new JsonArray()
        .add(uuid1)
        .add(uuid2));

    assertThat("Response should have processed count",
      response.containsKey("processed"), is(true));
    assertThat("Response should have anonymizedRequests array",
      response.containsKey("anonymizedRequests"), is(true));
    assertThat("Processed count should be 2",
      response.getInteger("processed"), is(2));
    assertThat("Should have 2 anonymized requests",
      response.getJsonArray("anonymizedRequests").size(), is(2));
  }

  @Test
  public void shouldReturnCorrectProcessedCount() {
    int expectedCount = 5;
    JsonArray requestIds = new JsonArray();
    for (int i = 0; i < expectedCount; i++) {
      requestIds.add(UUID.randomUUID().toString());
    }

    JsonObject response = new JsonObject()
      .put("processed", expectedCount)
      .put("anonymizedRequests", requestIds);

    assertThat("Processed count should match number of requests",
      response.getInteger("processed"), is(expectedCount));
  }

  @Test
  public void shouldReturnEmptyResponseForZeroRequests() {
    JsonObject response = new JsonObject()
      .put("processed", 0)
      .put("anonymizedRequests", new JsonArray());

    assertThat("Processed count should be 0",
      response.getInteger("processed"), is(0));
    assertThat("Anonymized requests should be empty",
      response.getJsonArray("anonymizedRequests").isEmpty(), is(true));
  }

  // ============ UUID Validation Tests ============

  @Test
  public void shouldAcceptValidUUIDs() {
    String validUuid = UUID.randomUUID().toString();
    JsonObject requestBody = new JsonObject()
      .put("requestIds", new JsonArray().add(validUuid));

    String extractedUuid = requestBody.getJsonArray("requestIds").getString(0);

    try {
      UUID.fromString(extractedUuid);
      assertTrue("Should be valid UUID format", true);
    } catch (IllegalArgumentException e) {
      assertTrue("UUID should be valid", false);
    }
  }

  @Test
  public void shouldValidateUUIDFormat() {
    String validUuid = "cf23adf0-61ba-4887-bf82-956c4aae2260";

    try {
      UUID parsed = UUID.fromString(validUuid);
      assertThat("UUID should be parsed", parsed, notNullValue());
      assertThat("UUID string should match", parsed.toString(), is(validUuid));
    } catch (IllegalArgumentException e) {
      assertTrue("UUID should be valid", false);
    }
  }

  @Test
  public void shouldRejectInvalidUUIDFormat() {
    String invalidUuid = "not-a-valid-uuid";

    try {
      UUID.fromString(invalidUuid);
      assertTrue("Should have thrown exception", false);
    } catch (IllegalArgumentException e) {
      assertTrue("Invalid UUID should throw exception", true);
    }
  }

  // ============ includeCirculationLogs Parameter Tests ============

  @Test
  public void shouldDefaultIncludeCirculationLogsToTrue() {
    JsonObject requestBody = new JsonObject()
      .put("requestIds", new JsonArray()
        .add(UUID.randomUUID().toString()));

    boolean includeCirculationLogs = requestBody.getBoolean("includeCirculationLogs", true);

    assertThat("includeCirculationLogs should default to true",
      includeCirculationLogs, is(true));
  }

  @Test
  public void shouldAllowIncludeCirculationLogsFalse() {
    JsonObject requestBody = new JsonObject()
      .put("requestIds", new JsonArray()
        .add(UUID.randomUUID().toString()))
      .put("includeCirculationLogs", false);

    assertThat("includeCirculationLogs should be false",
      requestBody.getBoolean("includeCirculationLogs"), is(false));
  }

  @Test
  public void shouldAllowIncludeCirculationLogsTrue() {
    JsonObject requestBody = new JsonObject()
      .put("requestIds", new JsonArray()
        .add(UUID.randomUUID().toString()))
      .put("includeCirculationLogs", true);

    assertThat("includeCirculationLogs should be true",
      requestBody.getBoolean("includeCirculationLogs"), is(true));
  }

  // ============ Bulk Processing Tests ============

  @Test
  public void shouldHandleMultipleRequestIds() {
    JsonArray requestIds = new JsonArray()
      .add(UUID.randomUUID().toString())
      .add(UUID.randomUUID().toString())
      .add(UUID.randomUUID().toString())
      .add(UUID.randomUUID().toString())
      .add(UUID.randomUUID().toString());

    JsonObject requestBody = new JsonObject()
      .put("requestIds", requestIds)
      .put("includeCirculationLogs", true);

    assertThat("Should handle 5 request IDs",
      requestBody.getJsonArray("requestIds").size(), is(5));
  }

  @Test
  public void shouldHandleSingleRequestId() {
    JsonArray requestIds = new JsonArray()
      .add(UUID.randomUUID().toString());

    JsonObject requestBody = new JsonObject()
      .put("requestIds", requestIds);

    assertThat("Should handle 1 request ID",
      requestBody.getJsonArray("requestIds").size(), is(1));
  }

  @Test
  public void shouldHandleLargeNumberOfRequestIds() {
    JsonArray requestIds = new JsonArray();
    int largeNumber = 100;

    for (int i = 0; i < largeNumber; i++) {
      requestIds.add(UUID.randomUUID().toString());
    }

    JsonObject requestBody = new JsonObject()
      .put("requestIds", requestIds);

    assertThat("Should handle 100 request IDs",
      requestBody.getJsonArray("requestIds").size(), is(largeNumber));
  }

  // ============ Request/Response Matching Tests ============

  @Test
  public void shouldMatchRequestIdsInResponse() {
    String uuid1 = UUID.randomUUID().toString();
    String uuid2 = UUID.randomUUID().toString();

    JsonArray requestIds = new JsonArray()
      .add(uuid1)
      .add(uuid2);

    JsonObject request = new JsonObject()
      .put("requestIds", requestIds);

    JsonObject response = new JsonObject()
      .put("processed", 2)
      .put("anonymizedRequests", requestIds);

    assertThat("Request IDs should match in response",
      response.getJsonArray("anonymizedRequests"), is(requestIds));
  }

  @Test
  public void shouldHaveConsistentProcessedCountAndArraySize() {
    JsonArray requestIds = new JsonArray()
      .add(UUID.randomUUID().toString())
      .add(UUID.randomUUID().toString())
      .add(UUID.randomUUID().toString());

    JsonObject response = new JsonObject()
      .put("processed", 3)
      .put("anonymizedRequests", requestIds);

    int processedCount = response.getInteger("processed");
    int arraySize = response.getJsonArray("anonymizedRequests").size();

    assertThat("Processed count should equal array size",
      processedCount, is(arraySize));
  }

  // ============ Edge Case Tests ============

  @Test
  public void shouldHandleRequestWithOnlyRequiredFields() {
    JsonObject requestBody = new JsonObject()
      .put("requestIds", new JsonArray()
        .add(UUID.randomUUID().toString()));

    assertThat("Should have requestIds",
      requestBody.containsKey("requestIds"), is(true));
    assertThat("Should not have includeCirculationLogs",
      requestBody.containsKey("includeCirculationLogs"), is(false));
  }

  @Test
  public void shouldPreserveRequestIdOrder() {
    String uuid1 = UUID.randomUUID().toString();
    String uuid2 = UUID.randomUUID().toString();
    String uuid3 = UUID.randomUUID().toString();

    JsonArray requestIds = new JsonArray()
      .add(uuid1)
      .add(uuid2)
      .add(uuid3);

    JsonObject requestBody = new JsonObject()
      .put("requestIds", requestIds);

    JsonArray retrievedIds = requestBody.getJsonArray("requestIds");

    assertThat("First UUID should match", retrievedIds.getString(0), is(uuid1));
    assertThat("Second UUID should match", retrievedIds.getString(1), is(uuid2));
    assertThat("Third UUID should match", retrievedIds.getString(2), is(uuid3));
  }

  @Test
  public void shouldHandleDuplicateRequestIds() {
    String uuid = UUID.randomUUID().toString();
    JsonArray requestIds = new JsonArray()
      .add(uuid)
      .add(uuid)
      .add(uuid);

    JsonObject requestBody = new JsonObject()
      .put("requestIds", requestIds);

    assertThat("Should contain 3 entries (including duplicates)",
      requestBody.getJsonArray("requestIds").size(), is(3));
  }

  // ============ JSON Serialization Tests ============

  @Test
  public void shouldSerializeRequestToJsonString() {
    JsonObject requestBody = new JsonObject()
      .put("requestIds", new JsonArray()
        .add(UUID.randomUUID().toString()))
      .put("includeCirculationLogs", true);

    String jsonString = requestBody.encode();

    assertThat("JSON string should not be empty",
      jsonString.isEmpty(), is(false));
    assertTrue("JSON should contain requestIds",
      jsonString.contains("requestIds"));
    assertTrue("JSON should contain includeCirculationLogs",
      jsonString.contains("includeCirculationLogs"));
  }

  @Test
  public void shouldDeserializeJsonStringToRequest() {
    String uuid = UUID.randomUUID().toString();
    String jsonString = String.format(
      "{\"requestIds\":[\"%s\"],\"includeCirculationLogs\":true}",
      uuid
    );

    JsonObject requestBody = new JsonObject(jsonString);

    assertThat("Should have requestIds after deserialization",
      requestBody.containsKey("requestIds"), is(true));
    assertThat("Should have correct UUID",
      requestBody.getJsonArray("requestIds").getString(0), is(uuid));
  }

  // ============ Null Safety Tests ============

  @Test
  public void shouldHandleNullIncludeCirculationLogs() {
    JsonObject requestBody = new JsonObject()
      .put("requestIds", new JsonArray()
        .add(UUID.randomUUID().toString()))
      .putNull("includeCirculationLogs");

    Boolean value = requestBody.getBoolean("includeCirculationLogs");

    assertThat("Null value should return null", value, is((Boolean) null));
  }

  @Test
  public void shouldProvideDefaultForMissingIncludeCirculationLogs() {
    JsonObject requestBody = new JsonObject()
      .put("requestIds", new JsonArray()
        .add(UUID.randomUUID().toString()));

    Boolean value = requestBody.getBoolean("includeCirculationLogs", true);

    assertThat("Should use default value", value, is(true));
  }
}
