package org.folio.circulation.domain.representations;


import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.folio.circulation.domain.representations.LoanProperties.CHECKOUT_SERVICE_POINT_ID;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.joda.time.format.ISODateTimeFormat.dateTime;

import java.util.UUID;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.ClockManager;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class CheckOutByBarcodeRequest {
  public static final String ITEM_BARCODE = "itemBarcode";
  public static final String USER_BARCODE = "userBarcode";
  public static final String PROXY_USER_BARCODE = "proxyUserBarcode";
  public static final String SERVICE_POINT_ID = "servicePointId";
  public static final String LOAN_DATE = "loanDate";
  public static final String OVERRIDE_BLOCKS = "overrideBlocks";

  private final String loanDate;
  private final String itemBarcode;
  private final String userBarcode;
  private final String proxyUserBarcode;
  private final String checkoutServicePointId;
  private final OverrideBlocks overrideBlocks;

  public static CheckOutByBarcodeRequest fromJson(JsonObject request) {
    final String loanDate = getProperty(request, LOAN_DATE);
    final String itemBarcode = getProperty(request, ITEM_BARCODE);
    final String userBarcode = getProperty(request, USER_BARCODE);
    final String proxyUserBarcode = getProperty(request, PROXY_USER_BARCODE);
    final String checkoutServicePointId = getProperty(request, SERVICE_POINT_ID);
    final OverrideBlocks overrideBlocks = OverrideBlocks.from(
      getObjectProperty(request, OVERRIDE_BLOCKS));

    return new CheckOutByBarcodeRequest(defaultLoanDate(loanDate), itemBarcode, userBarcode,
      proxyUserBarcode, checkoutServicePointId, overrideBlocks);
  }

  private static String defaultLoanDate(String loanDate) {
    return isNotBlank(loanDate)
      ? loanDate
      : ClockManager.getClockManager().getDateTime().toString(dateTime());
  }

  public Loan toLoan() {
    final JsonObject loanJson = new JsonObject();

    loanJson.put("id", UUID.randomUUID().toString());
    loanJson.put(CHECKOUT_SERVICE_POINT_ID, checkoutServicePointId);
    loanJson.put(LOAN_DATE, loanDate);

    return Loan.from(loanJson);
  }
}
