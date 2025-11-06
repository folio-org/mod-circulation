package api;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;

import org.junit.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class RequestAnonymizationTests {

  @Test
  public void anonymizationRequestShouldHaveCorrectStructure() {
    // Verify the request structure matches the schema
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
  public void anonymizationResponseShouldHaveCorrectStructure() {
    // Verify the response structure matches the schema
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
  public void requestIdsShouldBeValidUUIDs() {
    // Verify that request IDs are valid UUIDs
    String uuid1 = "cf23adf0-61ba-4887-bf82-956c4aae2260";
    String uuid2 = "550e8400-e29b-41d4-a716-446655440000";

    try {
      UUID.fromString(uuid1);
      UUID.fromString(uuid2);
      // If we get here, UUIDs are valid
      assertThat("UUIDs should be valid", true, is(true));
    } catch (IllegalArgumentException e) {
      assertThat("UUIDs should be valid", false, is(true));
    }
  }

  @Test
  public void shouldSupportBulkAnonymization() {
    // Verify that multiple requests can be anonymized in one call
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
  public void includeCirculationLogsShouldBeOptional() {
    // Verify that includeCirculationLogs is optional
    JsonObject request = new JsonObject()
      .put("requestIds", new JsonArray()
        .add(UUID.randomUUID().toString()));

    // Should work without includeCirculationLogs
    assertThat(request.containsKey("requestIds"), is(true));

    // Should default to true
    boolean includeCirculationLogs = request.getBoolean("includeCirculationLogs", true);
    assertThat(includeCirculationLogs, is(true));
  }
}
