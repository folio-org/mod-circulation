package org.folio.circulation.domain.representations;

import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.OkJsonHttpResult;
import org.folio.circulation.support.WritableHttpResult;

import io.vertx.core.json.JsonObject;

public class CheckInByBarcodeResponse {
  private CheckInByBarcodeResponse() {}

  public static WritableHttpResult<JsonObject> from(HttpResult<JsonObject> loanResult) {
    //TODO: Rework HttpResult and how writable results work in order to allow this
    //to be chained with map rather than a clunky if statement
    if(loanResult.failed()) {
      return HttpResult.failed(loanResult.cause());
    }
    else {
      return new OkJsonHttpResult(new JsonObject()
        .put("loan", loanResult.value()));
    }
  }
}
