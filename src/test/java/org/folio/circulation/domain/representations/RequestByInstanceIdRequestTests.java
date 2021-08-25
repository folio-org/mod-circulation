package org.folio.circulation.domain.representations;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class RequestByInstanceIdRequestTests {

  private static final String REQUEST_DATE = "requestDate";
  private static final String REQUESTER_ID = "requesterId";
  private static final String INSTANCE_ID = "instanceId";

  @Test
  void failedValidationWhenRequestMissingRequesterId() {
    JsonObject instanceRequest = new JsonObject();
    instanceRequest.put(INSTANCE_ID, getRandomUUIDString());
    instanceRequest.put(REQUEST_DATE, "2019-07-04");

    final Result<RequestByInstanceIdRequest> requestValidationResult = RequestByInstanceIdRequest.from(instanceRequest);
    assertTrue(requestValidationResult.failed());
  }

  @Test
  void failedValidationWhenRequestMissingRequestDate() {
    JsonObject instanceRequest = new JsonObject();
    instanceRequest.put(INSTANCE_ID, getRandomUUIDString());
    instanceRequest.put(REQUESTER_ID, getRandomUUIDString());

    final Result<RequestByInstanceIdRequest> requestValidationResult = RequestByInstanceIdRequest.from(instanceRequest);
    assertTrue(requestValidationResult.failed());
  }

  @Test
  void failedValidationWhenRequestMissingInstanceId() {
    JsonObject instanceRequest = new JsonObject();
    instanceRequest.put(REQUEST_DATE, "2041-11-11");
    instanceRequest.put(REQUESTER_ID, getRandomUUIDString());

    final Result<RequestByInstanceIdRequest> requestValidationResult = RequestByInstanceIdRequest.from(instanceRequest);
    assertTrue(requestValidationResult.failed());
  }

  @Test
  void passedValidationWhenRequestHasAllNecessaryFields() {
    JsonObject instanceRequest = new JsonObject();
    instanceRequest.put(REQUEST_DATE, "2041-11-11");
    instanceRequest.put(REQUESTER_ID, getRandomUUIDString());
    instanceRequest.put(INSTANCE_ID, getRandomUUIDString());

    final Result<RequestByInstanceIdRequest> requestValidationResult = RequestByInstanceIdRequest.from(instanceRequest);
    assertTrue(requestValidationResult.succeeded());
  }

  private String getRandomUUIDString(){
    return UUID.randomUUID().toString();
  }

}
