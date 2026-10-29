package org.folio.circulation.domain.representations;

import static org.folio.circulation.domain.notice.TemplateContextUtil.createCheckInContext;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.http.server.JsonHttpResponse.ok;

import java.lang.invoke.MethodHandles;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.LoanRepresentation;
import org.folio.circulation.support.http.server.HttpResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class CheckInByBarcodeResponse {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final CheckInContext context;

  public static CheckInByBarcodeResponse fromRecords(CheckInContext records) {
    log.debug("fromRecords:: parameters records: {}", records);

    return new CheckInByBarcodeResponse(records);
  }

  private final List<CheckInByBarcodeResponse> subsequentCheckIns;

  private CheckInByBarcodeResponse(CheckInContext context) {
    this(context, List.of());
  }

  private CheckInByBarcodeResponse(CheckInContext context,
    List<CheckInByBarcodeResponse> subsequentCheckIns) {

    this.context = context;
    this.subsequentCheckIns = subsequentCheckIns;
  }

  /**
   * Adds the results of check-ins performed after this one. Subsequent check-ins
   * are not allowed to have subsequent check-ins themselves.
   */
  public CheckInByBarcodeResponse withSubsequentCheckIns(
    List<CheckInByBarcodeResponse> subsequentCheckIns) {

    return new CheckInByBarcodeResponse(context, List.copyOf(subsequentCheckIns));
  }

  public HttpResponse toHttpResponse() {
    return ok(this.toJson());
  }

  private JsonObject toJson() {
    log.debug("toJson:: ");

    final JsonObject json = toBaseJson();

    if (!subsequentCheckIns.isEmpty()) {
      json.put("subsequentCheckIns", new JsonArray(subsequentCheckIns.stream()
        .map(CheckInByBarcodeResponse::toBaseJson)
        .toList()));
    }

    log.info("toJson:: result {}", json);
    return json;
  }

  private JsonObject toBaseJson() {
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
