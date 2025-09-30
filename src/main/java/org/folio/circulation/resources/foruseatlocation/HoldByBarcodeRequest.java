package org.folio.circulation.resources.foruseatlocation;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import lombok.With;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.Environment;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.representations.ItemSummaryRepresentation;
import org.folio.circulation.support.BadRequestFailure;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.results.Result;

import java.lang.invoke.MethodHandles;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.function.Supplier;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.results.Result.succeeded;

@Getter
@AllArgsConstructor
@ToString
public class HoldByBarcodeRequest {
  private static final String ITEM_BARCODE = "itemBarcode";
  private static final String SERVICE_POINT_ID = "servicePointId";
  private final String itemBarcode;
  @With
  private Loan loan;
  @With
  private ServicePoint servicePoint;
  @With
  private ZonedDateTime holdShelfExpirationDate;
  @With
  private ZoneId tenantTimeZone;


  public HoldByBarcodeRequest(String itemBarcode) {
    this.itemBarcode = itemBarcode;
  }

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  static Result<HoldByBarcodeRequest> buildRequestFrom(JsonObject json) {
    final String itemBarcode = getProperty(json, ITEM_BARCODE);
    if (isBlank(itemBarcode)) {
      String message = "Request to put item on hold shelf must have an item barcode";
      log.warn("Missing information:: {}", message);
      return failedValidation(message, ITEM_BARCODE, null);
    }
    return succeeded(new HoldByBarcodeRequest(itemBarcode));
  }


  static Result<Boolean> loanIsNull(HoldByBarcodeRequest request) {
    return Result.succeeded(request.getLoan() == null);
  }

  static Result<Boolean> loanIsNotForUseAtLocation(HoldByBarcodeRequest request) {
    return Result.succeeded(!request.getLoan().isForUseAtLocation());
  }

  static Supplier<HttpFailure> noOpenLoanFailure(HoldByBarcodeRequest request) {
    String message = "No open loan found for the item barcode.";
    log.warn("noOpenLoanFailure:: {}", message);
    return () -> new BadRequestFailure(format(message + " (%s)", request.getItemBarcode()));
  }

  static Supplier<HttpFailure> loanIsNotForUseAtLocationFailure(HoldByBarcodeRequest request) {
    String message = "The loan is open but is not for use at location.";
    log.warn("loanIsNotForUseAtLocationFailure:: {}", message);
    return () -> new BadRequestFailure(format(message + ", item barcode (%s)", request.getItemBarcode()));
  }

  Result<Boolean> forUseAtLocationIsNotEnabled() {
    return Result.succeeded(!Environment.getForUseAtLocationEnabled());
  }

  static Supplier<HttpFailure> forUseAtLocationIsNotEnabledFailure() {
    String message = "For-use-at-location is not enabled for this tenant.";
    log.warn("forUseAtLocationIsNotEnabledFailure:: {}", message);
    return () -> new BadRequestFailure(format(message));
  }

  public JsonObject responseBody() {
    JsonObject body = new JsonObject();
    JsonObject loanJson = loan.asJson();
    JsonObject itemJson = new ItemSummaryRepresentation().createItemSummary(loan.getItem());

    loanJson.put("item", itemJson);
    body.put("loan", loanJson);

    return body;
  }

}
