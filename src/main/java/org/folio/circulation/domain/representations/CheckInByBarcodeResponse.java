package org.folio.circulation.domain.representations;

import static org.folio.circulation.support.JsonPropertyWriter.write;

import org.folio.circulation.domain.CheckInProcessRecords;
import org.folio.circulation.domain.LoanRepresentation;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.OkJsonHttpResult;
import org.folio.circulation.support.WritableHttpResult;

import io.vertx.core.json.JsonObject;

public class CheckInByBarcodeResponse {
  private CheckInByBarcodeResponse() {}

  public static WritableHttpResult<JsonObject> from(
    HttpResult<CheckInProcessRecords> recordsResult) {

    //TODO: Rework HttpResult and how writable results work in order to allow this
    //to be chained with map rather than a clunky if statement
    if(recordsResult.failed()) {
      return HttpResult.failed(recordsResult.cause());
    }
    else {
      return mapToResponse(recordsResult.value());
    }
  }

  private static WritableHttpResult<JsonObject> mapToResponse(
    CheckInProcessRecords records) {

    final LoanRepresentation loanRepresentation = new LoanRepresentation();
    final ItemSummaryRepresentation itemRepresentation = new ItemSummaryRepresentation();

    final JsonObject checkInResponseBody = new JsonObject();

    write(checkInResponseBody, "loan",
      loanRepresentation.extendedLoan(records.getLoan()));

    write(checkInResponseBody, "item",
      itemRepresentation.createItemSummary(records.getItem()));

    return new OkJsonHttpResult(checkInResponseBody);
  }
}
