package org.folio.circulation.domain.representations;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.OkJsonHttpResult;
import org.folio.circulation.support.WritableHttpResult;

public class LoanResponse {
  private LoanResponse() {}

  public static WritableHttpResult<JsonObject> from(HttpResult<JsonObject> result) {
    if(result.failed()) {
      return HttpResult.failed(result.cause());
    }
    else {
      return new OkJsonHttpResult(result.value(),
        String.format("/circulation/loans/%s", result.value().getString("id")));
    }
  }
}
