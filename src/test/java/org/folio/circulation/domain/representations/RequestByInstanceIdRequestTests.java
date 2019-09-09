package org.folio.circulation.domain.representations;

import static org.junit.Assert.assertTrue;

import java.util.UUID;

import org.folio.circulation.domain.representations.RequestByInstanceIdRequest;
import org.folio.circulation.support.Result;
import org.junit.Test;

import io.vertx.core.json.JsonObject;

public class RequestByInstanceIdRequestTests {

  private static final String REQUEST_DATE = "requestDate";
  private static final String REQUESTER_ID = "requesterId";
  private static final String INSTANCE_ID = "instanceId";

  @Test
  public void failedValidationWhenRequestMissingRequesterId() {
    JsonObject instanceRequest = new JsonObject();
    instanceRequest.put(INSTANCE_ID, getRandomUUIDString());
    instanceRequest.put(REQUEST_DATE, "2019-07-04");

    final Result<RequestByInstanceIdRequest> requestValidationResult = RequestByInstanceIdRequest.from(instanceRequest);
    assertTrue(requestValidationResult.failed());
  }

  @Test
  public void failedValidationWhenRequestMissingRequestDate() {
    JsonObject instanceRequest = new JsonObject();
    instanceRequest.put(INSTANCE_ID, getRandomUUIDString());
    instanceRequest.put(REQUESTER_ID, getRandomUUIDString());

    final Result<RequestByInstanceIdRequest> requestValidationResult = RequestByInstanceIdRequest.from(instanceRequest);
    assertTrue(requestValidationResult.failed());
  }

  @Test
  public void failedValidationWhenRequestMissingInstanceId() {
    JsonObject instanceRequest = new JsonObject();
    instanceRequest.put(REQUEST_DATE, "2041-11-11");
    instanceRequest.put(REQUESTER_ID, getRandomUUIDString());

    final Result<RequestByInstanceIdRequest> requestValidationResult = RequestByInstanceIdRequest.from(instanceRequest);
    assertTrue(requestValidationResult.failed());
  }

  @Test
  public void passedValidationWhenRequestHasAllNecessaryFields() {
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
