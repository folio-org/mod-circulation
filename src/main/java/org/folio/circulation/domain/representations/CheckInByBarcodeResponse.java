package org.folio.circulation.domain.representations;

import static org.folio.circulation.domain.notice.TemplateContextUtil.createCheckInContext;
import static org.folio.circulation.support.JsonPropertyWriter.write;

import org.folio.circulation.domain.CheckInProcessRecords;
import org.folio.circulation.domain.LoanRepresentation;
import org.folio.circulation.support.http.server.HttpResponse;
import org.folio.circulation.support.http.server.JsonHttpResponse;

import io.vertx.core.json.JsonObject;

public class CheckInByBarcodeResponse {
  private final CheckInProcessRecords records;

  public static CheckInByBarcodeResponse fromRecords(CheckInProcessRecords records) {
    return new CheckInByBarcodeResponse(records);
  }

  private CheckInByBarcodeResponse(CheckInProcessRecords records) {
    this.records = records;
  }

  public HttpResponse toHttpResponse() {
    return new JsonHttpResponse(200, this.toJson(), null);
  }

  private JsonObject toJson() {
    final LoanRepresentation loanRepresentation = new LoanRepresentation();
    final ItemSummaryRepresentation itemRepresentation = new ItemSummaryRepresentation();

    final JsonObject json = new JsonObject();

    write(json, "loan", loanRepresentation.extendedLoan(records.getLoan()));
    write(json, "item", itemRepresentation.createItemSummary(records.getItem()));
    write(json, "staffSlipContext", createCheckInContext(records));
    write(json, "inHouseUse", records.isInHouseUse());

    return json;
  }
}
