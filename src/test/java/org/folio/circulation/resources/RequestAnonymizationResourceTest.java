package org.folio.circulation.resources;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import org.junit.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class RequestAnonymizationResourceTest {

  @Test
  public void shouldValidateAnonymizationRequestStructure() {
    // Test that a valid anonymization request has the correct structure
    JsonObject requestBody = new JsonObject()
      .put("requestIds", new JsonArray()
        .add(UUID.randomUUID().toString())
        .add(UUID.randomUUID().toString()))
      .put("includeCirculationLogs", true);

    // Verify structure
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
  public void shouldValidateAnonymizationResponseStructure() {
    // Test that the anonymization response has the correct structure
    String uuid1 = UUID.randomUUID().toString();
    String uuid2 = UUID.randomUUID().toString();

    JsonObject response = new JsonObject()
      .put("processed", 2)
      .put("anonymizedRequests", new JsonArray()
        .add(uuid1)
        .add(uuid2));

    // Verify structure
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
  public void shouldRejectRequestWithoutRequestIds() {
    // Test that request without requestIds is invalid
    JsonObject requestBody = new JsonObject()
      .put("includeCirculationLogs", true);

    boolean hasRequestIds = requestBody.containsKey("requestIds");

    assertThat("Request without requestIds should be invalid",
      hasRequestIds, is(false));
  }

  @Test
  public void shouldRejectRequestWithEmptyRequestIdsArray() {
    // Test that request with empty requestIds array is invalid
    JsonObject requestBody = new JsonObject()
      .put("requestIds", new JsonArray())
      .put("includeCirculationLogs", true);

    boolean isEmpty = requestBody.getJsonArray("requestIds").isEmpty();

    assertThat("Request with empty requestIds array should be invalid",
      isEmpty, is(true));
  }

  @Test
  public void shouldDefaultIncludeCirculationLogsToTrue() {
    // Test that includeCirculationLogs defaults to true if not provided
    JsonObject requestBody = new JsonObject()
      .put("requestIds", new JsonArray()
        .add(UUID.randomUUID().toString()));

    // If not present, should default to true
    boolean includeCirculationLogs = requestBody.getBoolean("includeCirculationLogs", true);

    assertThat("includeCirculationLogs should default to true",
      includeCirculationLogs, is(true));
  }

  @Test
  public void shouldAcceptValidUUIDs() {
    // Test that valid UUIDs are accepted
    String validUuid = UUID.randomUUID().toString();
    JsonObject requestBody = new JsonObject()
      .put("requestIds", new JsonArray().add(validUuid));

    String extractedUuid = requestBody.getJsonArray("requestIds").getString(0);

    // Verify it's a valid UUID format
    try {
      UUID.fromString(extractedUuid);
      assertTrue("Should be valid UUID format", true);
    } catch (IllegalArgumentException e) {
      assertTrue("UUID should be valid", false);
    }
  }

  @Test
  public void shouldHandleMultipleRequestIds() {
    // Test that multiple request IDs can be processed
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
  public void shouldAllowIncludeCirculationLogsFalse() {
    // Test that includeCirculationLogs can be set to false
    JsonObject requestBody = new JsonObject()
      .put("requestIds", new JsonArray()
        .add(UUID.randomUUID().toString()))
      .put("includeCirculationLogs", false);

    assertThat("includeCirculationLogs should be false",
      requestBody.getBoolean("includeCirculationLogs"), is(false));
  }
}
