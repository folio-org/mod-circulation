package api.requests;

import static api.support.http.InterfaceUrls.requestAnonymizationUrl;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;

import org.folio.circulation.support.http.client.Response;
import org.junit.Test;

import api.support.APITests;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Integration tests for Request Anonymization API
 *
 * NOTE: These tests validate the API endpoint routing and validation only.
 * Full end-to-end tests require mod-circulation-storage implementation.
 */
public class RequestAnonymizationAPITests extends APITests {

  /**
   * Test validation: null body should return 422
   */
  @Test
  public void shouldFailWhenRequestBodyIsMissing() {
    Response response = restAssuredClient.post(
      (JsonObject) null,
      requestAnonymizationUrl(),
      "anonymize-requests-null");

    assertThat("Null body should return validation error",
      response.getStatusCode(), is(422));
  }

  /**
   * Test validation: missing requestIds should return 422
   */
  @Test
  public void shouldFailWhenRequestIdsMissing() {
    JsonObject request = new JsonObject()
      .put("includeCirculationLogs", true);

    Response response = restAssuredClient.post(
      request,
      requestAnonymizationUrl(),
      "anonymize-requests-missing-ids");

    assertThat("Missing requestIds should return validation error",
      response.getStatusCode(), is(422));
  }

  /**
   * Test validation: empty requestIds array should return 422
   */
  @Test
  public void shouldFailWhenRequestIdsEmpty() {
    JsonObject request = new JsonObject()
      .put("requestIds", new JsonArray())
      .put("includeCirculationLogs", true);

    Response response = restAssuredClient.post(
      request,
      requestAnonymizationUrl(),
      "anonymize-requests-empty");

    assertThat("Empty requestIds should return validation error",
      response.getStatusCode(), is(422));
  }

  /**
   * Test that endpoint routing works
   * Validates the endpoint exists and accepts properly formatted requests
   */
  @Test
  public void shouldRouteToCorrectEndpoint() {
    JsonObject request = new JsonObject()
      .put("requestIds", new JsonArray().add(UUID.randomUUID().toString()))
      .put("includeCirculationLogs", true);

    Response response = restAssuredClient.post(
      request,
      requestAnonymizationUrl(),
      "anonymize-requests-routing");

    // Endpoint should exist (not 404)
    assertThat("Endpoint should be routed correctly (not 404)",
      response.getStatusCode() != 404, is(true));
  }
}
