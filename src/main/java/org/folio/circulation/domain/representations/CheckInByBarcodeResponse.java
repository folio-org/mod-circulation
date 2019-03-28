package org.folio.circulation.domain.representations;

import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.folio.circulation.support.Result.failed;

import org.folio.circulation.domain.CheckInProcessRecords;
import org.folio.circulation.domain.LoanRepresentation;
import org.folio.circulation.support.OkJsonResponseResult;
import org.folio.circulation.support.ResponseWritableResult;
import org.folio.circulation.support.Result;

import io.vertx.core.json.JsonObject;

public class CheckInByBarcodeResponse {
  private CheckInByBarcodeResponse() {}

  public static ResponseWritableResult<JsonObject> from(
    Result<CheckInProcessRecords> recordsResult) {

    //TODO: Rework Result and how writable results work in order to allow this
    //to be chained with map rather than a clunky if statement
    if(recordsResult.failed()) {
      return failed(recordsResult.cause());
    }
    else {
      return mapToResponse(recordsResult.value());
    }
  }

  private static ResponseWritableResult<JsonObject> mapToResponse(
    CheckInProcessRecords records) {

    final LoanRepresentation loanRepresentation = new LoanRepresentation();
    final ItemSummaryRepresentation itemRepresentation = new ItemSummaryRepresentation();

    final JsonObject checkInResponseBody = new JsonObject();

    write(checkInResponseBody, "loan",
      loanRepresentation.extendedLoan(records.getLoan()));

    write(checkInResponseBody, "item",
      itemRepresentation.createItemSummary(records.getItem()));

    return new OkJsonResponseResult(checkInResponseBody);
  }
}
