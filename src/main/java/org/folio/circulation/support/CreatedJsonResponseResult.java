package org.folio.circulation.support;

import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;

public class CreatedJsonResponseResult extends JsonResponseResult {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public CreatedJsonResponseResult(JsonObject body, String location) {
    super(201, body, location);
  }

  public static ResponseWritableResult<JsonObject> from(Result<JsonObject> result) {
    if(result.failed()) {
      log.debug("CreatedJsonResponseResult: result failed");
      return Result.failed(result.cause());
    }
    else {
      log.debug("CreatedJsonResponseResult: result succeeded, creating Json Response result");
      return new CreatedJsonResponseResult(result.value(), null);
    }
  }
}




