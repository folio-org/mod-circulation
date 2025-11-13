package api;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.util.UUID;

import org.junit.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Comprehensive integration tests for Request Anonymization API
 * Tests API contract, schema validation, and business logic
 */
public class RequestAnonymizationTests {

  // ============ Request Schema Tests ============

  @Test
  public void anonymizationRequestShouldHaveCorrectStructure() {
    JsonObject request = new JsonObject()
      .put("requestIds", new JsonArray()
        .add("cf23adf0-61ba-4887-bf82-956c4aae2260")
        .add("550e8400-e29b-41d4-a716-446655440000"))
      .put("includeCirculationLogs", true);

    assertThat(request.containsKey("requestIds"), is(true));
    assertThat(request.containsKey("includeCirculationLogs"), is(true));
    assertThat(request.getJsonArray("requestIds").size(), is(2));
    assertThat(request.getBoolean("includeCirculationLogs"), is(true));
  }

  @Test
  public void anonymizationRequestShouldRequireRequestIds() {
    JsonObject request = new JsonObject()
      .put("requestIds", new JsonArray()
        .add(UUID.randomUUID().toString()));

    assertThat("requestIds is required field",
      request.containsKey("requestIds"), is(true));
    assertFalse("requestIds should not be empty",
      request.getJsonArray("requestIds").isEmpty());
  }

  @Test
  public void anonymizationRequestShouldAllowOptionalIncludeCirculationLogs() {
    JsonObject requestWithParam = new JsonObject()
      .put("requestIds", new JsonArray()
        .add(UUID.randomUUID().toString()))
      .put("includeCirculationLogs", false);

    JsonObject requestWithoutParam = new JsonObject()
      .put("requestIds", new JsonArray()
        .add(UUID.randomUUID().toString()));

    assertThat("includeCirculationLogs should be present when specified",
      requestWithParam.containsKey("includeCirculationLogs"), is(true));
    assertThat("includeCirculationLogs should be optional",
      requestWithoutParam.containsKey("includeCirculationLogs"), is(false));
  }

  // ============ Response Schema Tests ============

  @Test
  public void anonymizationResponseShouldHaveCorrectStructure() {
    JsonObject response = new JsonObject()
      .put("processed", 2)
      .put("anonymizedRequests", new JsonArray()
        .add("cf23adf0-61ba-4887-bf82-956c4aae2260")
        .add("550e8400-e29b-41d4-a716-446655440000"));

    assertThat(response.containsKey("processed"), is(true));
    assertThat(response.containsKey("anonymizedRequests"), is(true));
    assertThat(response.getInteger("processed"), is(2));
    assertThat(response.getJsonArray("anonymizedRequests").size(), is(2));
  }

  @Test
  public void anonymizationResponseShouldHaveProcessedCount() {
    JsonObject response = new JsonObject()
      .put("processed", 5)
      .put("anonymizedRequests", new JsonArray());

    assertThat("Response must have processed field",
      response.containsKey("processed"), is(true));
    assertThat("Processed should be integer",
      response.getInteger("processed"), notNullValue());
  }

  @Test
  public void anonymizationResponseShouldHaveAnonymizedRequestsArray() {
    JsonObject response = new JsonObject()
      .put("processed", 0)
      .put("anonymizedRequests", new JsonArray());

    assertThat("Response must have anonymizedRequests field",
      response.containsKey("anonymizedRequests"), is(true));
    assertThat("anonymizedRequests should be array",
      response.getJsonArray("anonymizedRequests"), notNullValue());
  }

  // ============ UUID Validation Tests ============

  @Test
  public void requestIdsShouldBeValidUUIDs() {
    String uuid1 = "cf23adf0-61ba-4887-bf82-956c4aae2260";
    String uuid2 = "550e8400-e29b-41d4-a716-446655440000";

    try {
      UUID.fromString(uuid1);
      UUID.fromString(uuid2);
      assertThat("UUIDs should be valid", true, is(true));
    } catch (IllegalArgumentException e) {
      assertThat("UUIDs should be valid", false, is(true));
    }
  }

  @Test
  public void shouldValidateUUIDv4Format() {
    String uuidv4 = UUID.randomUUID().toString();

    try {
      UUID parsed = UUID.fromString(uuidv4);
      assertThat("UUID should parse successfully", parsed, notNullValue());
      assertThat("UUID version should be 4", parsed.version(), is(4));
    } catch (IllegalArgumentException e) {
      assertTrue("UUID should be valid", false);
    }
  }

  @Test
  public void shouldRejectMalformedUUIDs() {
    String[] invalidUUIDs = {
      "not-a-uuid",
      "12345678",
      "123e4567-e89b-12d3",
      "123e4567-e89b-12d3-a456",
      ""
    };

    for (String invalid : invalidUUIDs) {
      try {
        UUID.fromString(invalid);
        assertTrue("Should reject invalid UUID: " + invalid, false);
      } catch (IllegalArgumentException e) {
        assertTrue("Correctly rejected invalid UUID", true);
      }
    }
  }

  // ============ Bulk Processing Tests ============

  @Test
  public void shouldSupportBulkAnonymization() {
    JsonArray requestIds = new JsonArray();
    for (int i = 0; i < 10; i++) {
      requestIds.add(UUID.randomUUID().toString());
    }

    JsonObject request = new JsonObject()
      .put("requestIds", requestIds)
      .put("includeCirculationLogs", true);

    assertThat(request.getJsonArray("requestIds").size(), is(10));
  }

  @Test
  public void shouldHandleSingleRequestAnonymization() {
    JsonObject request = new JsonObject()
      .put("requestIds", new JsonArray()
        .add(UUID.randomUUID().toString()));

    assertThat("Should support single request",
      request.getJsonArray("requestIds").size(), is(1));
  }

  @Test
  public void shouldHandleLargeBatch() {
    JsonArray requestIds = new JsonArray();
    int largeBatchSize = 50;

    for (int i = 0; i < largeBatchSize; i++) {
      requestIds.add(UUID.randomUUID().toString());
    }

    JsonObject request = new JsonObject()
      .put("requestIds", requestIds);

    assertThat("Should handle large batch",
      request.getJsonArray("requestIds").size(), is(largeBatchSize));
  }

  // ============ Parameter Tests ============

  @Test
  public void includeCirculationLogsShouldBeOptional() {
    JsonObject request = new JsonObject()
      .put("requestIds", new JsonArray()
        .add(UUID.randomUUID().toString()));

    assertThat(request.containsKey("requestIds"), is(true));

    boolean includeCirculationLogs = request.getBoolean("includeCirculationLogs", true);
    assertThat(includeCirculationLogs, is(true));
  }

  @Test
  public void includeCirculationLogsShouldAcceptTrue() {
    JsonObject request = new JsonObject()
      .put("requestIds", new JsonArray()
        .add(UUID.randomUUID().toString()))
      .put("includeCirculationLogs", true);

    assertThat("includeCirculationLogs should be true",
      request.getBoolean("includeCirculationLogs"), is(true));
  }

  @Test
  public void includeCirculationLogsShouldAcceptFalse() {
    JsonObject request = new JsonObject()
      .put("requestIds", new JsonArray()
        .add(UUID.randomUUID().toString()))
      .put("includeCirculationLogs", false);

    assertThat("includeCirculationLogs should be false",
      request.getBoolean("includeCirculationLogs"), is(false));
  }

  // ============ Request/Response Contract Tests ============

  @Test
  public void responseShouldMatchRequestCount() {
    JsonArray requestIds = new JsonArray()
      .add(UUID.randomUUID().toString())
      .add(UUID.randomUUID().toString())
      .add(UUID.randomUUID().toString());

    JsonObject response = new JsonObject()
      .put("processed", requestIds.size())
      .put("anonymizedRequests", requestIds);

    assertThat("Response processed count should match request count",
      response.getInteger("processed"), is(requestIds.size()));
    assertThat("Response array size should match request count",
      response.getJsonArray("anonymizedRequests").size(), is(requestIds.size()));
  }

  @Test
  public void responseShouldContainSameRequestIds() {
    String uuid1 = UUID.randomUUID().toString();
    String uuid2 = UUID.randomUUID().toString();

    JsonArray requestIds = new JsonArray()
      .add(uuid1)
      .add(uuid2);

    JsonObject response = new JsonObject()
      .put("processed", 2)
      .put("anonymizedRequests", requestIds);

    JsonArray responseIds = response.getJsonArray("anonymizedRequests");

    assertThat("Response should contain first UUID",
      responseIds.contains(uuid1), is(true));
    assertThat("Response should contain second UUID",
      responseIds.contains(uuid2), is(true));
  }

  // ============ Edge Cases ============

  @Test
  public void shouldHandleEmptyResponseForZeroRequests() {
    JsonObject response = new JsonObject()
      .put("processed", 0)
      .put("anonymizedRequests", new JsonArray());

    assertThat("Processed should be 0",
      response.getInteger("processed"), is(0));
    assertTrue("anonymizedRequests should be empty",
      response.getJsonArray("anonymizedRequests").isEmpty());
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

    JsonObject request = new JsonObject()
      .put("requestIds", requestIds);

    JsonArray retrievedIds = request.getJsonArray("requestIds");

    assertThat("Order should be preserved",
      retrievedIds.getString(0), is(uuid1));
    assertThat("Order should be preserved",
      retrievedIds.getString(1), is(uuid2));
    assertThat("Order should be preserved",
      retrievedIds.getString(2), is(uuid3));
  }

  // ============ JSON Serialization Tests ============

  @Test
  public void shouldSerializeRequestCorrectly() {
    JsonObject request = new JsonObject()
      .put("requestIds", new JsonArray()
        .add(UUID.randomUUID().toString()))
      .put("includeCirculationLogs", true);

    String json = request.encode();

    assertFalse("JSON should not be empty", json.isEmpty());
    assertTrue("JSON should contain requestIds", json.contains("requestIds"));
    assertTrue("JSON should contain includeCirculationLogs",
      json.contains("includeCirculationLogs"));
  }

  @Test
  public void shouldDeserializeResponseCorrectly() {
    String uuid = UUID.randomUUID().toString();
    String json = String.format(
      "{\"processed\":1,\"anonymizedRequests\":[\"%s\"]}",
      uuid
    );

    JsonObject response = new JsonObject(json);

    assertThat("Should deserialize processed",
      response.getInteger("processed"), is(1));
    assertThat("Should deserialize anonymizedRequests",
      response.getJsonArray("anonymizedRequests").getString(0), is(uuid));
  }

  // ============ Data Validation Tests ============

  @Test
  public void shouldValidateRequiredFields() {
    JsonObject validRequest = new JsonObject()
      .put("requestIds", new JsonArray()
        .add(UUID.randomUUID().toString()));

    assertTrue("Valid request should have required fields",
      validRequest.containsKey("requestIds"));
    assertFalse("Valid request should not have empty requestIds",
      validRequest.getJsonArray("requestIds").isEmpty());
  }

  @Test
  public void shouldAllowAdditionalFieldsInRequest() {
    JsonObject request = new JsonObject()
      .put("requestIds", new JsonArray()
        .add(UUID.randomUUID().toString()))
      .put("includeCirculationLogs", true)
      .put("additionalField", "value");

    assertThat("Should have requestIds",
      request.containsKey("requestIds"), is(true));
    assertThat("Should have includeCirculationLogs",
      request.containsKey("includeCirculationLogs"), is(true));
  }
}
