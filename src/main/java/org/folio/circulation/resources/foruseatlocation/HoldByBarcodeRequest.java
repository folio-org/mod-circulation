package org.folio.circulation.resources.foruseatlocation;

import io.vertx.core.json.JsonObject;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.ServicePoint;
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
public class HoldByBarcodeRequest {
  private static final String ITEM_BARCODE = "itemBarcode";
  private static final String SERVICE_POINT_ID = "servicePointId";
  private final String itemBarcode;
  private Loan loan;
  private ServicePoint servicePoint;
  private ZonedDateTime holdShelfExpirationDate;
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

  public HoldByBarcodeRequest withLoan(Loan loan) {
    this.loan = loan;
    return this;
  }

  public HoldByBarcodeRequest withServicePoint(ServicePoint servicePoint) {
    this.servicePoint = servicePoint;
    return this;
  }

  public HoldByBarcodeRequest withTenantTimeZone(ZoneId tenantTimeZone) {
    this.tenantTimeZone = tenantTimeZone;
    return this;
  }

  public HoldByBarcodeRequest withHoldShelfExpirationDate(ZonedDateTime date) {
    this.holdShelfExpirationDate = date;
    return this;
  }

  static Result<Boolean> loanIsNull(HoldByBarcodeRequest request) {
    return Result.succeeded(request.getLoan() == null);
  }

  static Result<Boolean> loanIsNotForUseAtLocation(HoldByBarcodeRequest request) {
    return Result.succeeded(!request.getLoan().isForUseAtLocation());
  }

  static Supplier<HttpFailure> noOpenLoanFailure(HoldByBarcodeRequest request) {
    String message = "No open loan found for the item barcode.";
    log.warn(message);
    return () -> new BadRequestFailure(format(message + " (%s)", request.getItemBarcode()));
  }
  static Supplier<HttpFailure> loanIsNotForUseAtLocationFailure(HoldByBarcodeRequest request) {
    String message = "The loan is open but is not for use at location.";
    log.warn(message);
    return () -> new BadRequestFailure(format(message + ", item barcode (%s)", request.getItemBarcode()));
  }


}
