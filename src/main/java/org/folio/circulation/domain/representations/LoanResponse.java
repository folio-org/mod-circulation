package org.folio.circulation.domain.representations;

import org.folio.circulation.support.OkJsonResponseResult;
import org.folio.circulation.support.ResponseWritableResult;
import org.folio.circulation.support.Result;

import io.vertx.core.json.JsonObject;

public class LoanResponse {
  private LoanResponse() {}

  public static ResponseWritableResult<JsonObject> from(Result<JsonObject> result) {
    if(result.failed()) {
      return Result.failed(result.cause());
    }
    else {
      return new OkJsonResponseResult(result.value(),
        String.format("/circulation/loans/%s", result.value().getString("id")));
    }
  }
}
