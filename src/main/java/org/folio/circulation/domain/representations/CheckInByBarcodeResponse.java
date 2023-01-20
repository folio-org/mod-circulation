package org.folio.circulation.domain.representations;

import static org.folio.circulation.domain.notice.TemplateContextUtil.createCheckInContext;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.http.server.JsonHttpResponse.ok;

import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.LoanRepresentation;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.User;
import org.folio.circulation.infrastructure.storage.users.PatronGroupRepository;
import org.folio.circulation.support.http.server.HttpResponse;

import io.vertx.core.json.JsonObject;

import java.util.concurrent.ExecutionException;

public class CheckInByBarcodeResponse {
  private final CheckInContext context;

  public static CheckInByBarcodeResponse fromRecords(CheckInContext records) {
    return new CheckInByBarcodeResponse(records);
  }

  private CheckInByBarcodeResponse(CheckInContext context) {
    this.context = context;
  }

  public HttpResponse toHttpResponse() {
    return ok(this.toJson());
  }

  private JsonObject toJson() {
    final LoanRepresentation loanRepresentation = new LoanRepresentation();
    final ItemSummaryRepresentation itemRepresentation = new ItemSummaryRepresentation();

    final JsonObject json = new JsonObject();
    write(json, "loan", loanRepresentation.extendedLoan(context.getLoan()));
    write(json, "item", itemRepresentation.createItemSummary(context.getItem()));
    write(json, "staffSlipContext", createCheckInContext(context));
    write(json, "inHouseUse", context.isInHouseUse());

    return json;
  }

}
