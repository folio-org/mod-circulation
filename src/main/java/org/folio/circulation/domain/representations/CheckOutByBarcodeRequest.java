package org.folio.circulation.domain.representations;

import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.folio.circulation.domain.representations.LoanProperties.CHECKOUT_SERVICE_POINT_ID;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTime;

import java.lang.invoke.MethodHandles;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.override.BlockOverrides;
import org.folio.circulation.support.utils.ClockUtil;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class CheckOutByBarcodeRequest {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public static final String ITEM_BARCODE = "itemBarcode";
  public static final String USER_BARCODE = "userBarcode";
  public static final String PROXY_USER_BARCODE = "proxyUserBarcode";
  public static final String SERVICE_POINT_ID = "servicePointId";
  public static final String LOAN_DATE = "loanDate";
  public static final String OVERRIDE_BLOCKS = "overrideBlocks";
  public static final String FORCE_LOAN_POLICY_ID = "forceLoanPolicyId";

  private final String loanDate;
  private final String itemBarcode;
  private final String userBarcode;
  private final String proxyUserBarcode;
  private final String checkoutServicePointId;
  private final BlockOverrides blockOverrides;
  private final String forceLoanPolicyId;

  public static CheckOutByBarcodeRequest fromJson(JsonObject request) {
    log.debug("fromJson:: parameters request: {}", request);

    final String loanDate = getProperty(request, LOAN_DATE);
    final String itemBarcode = getProperty(request, ITEM_BARCODE);
    final String userBarcode = getProperty(request, USER_BARCODE);
    final String proxyUserBarcode = getProperty(request, PROXY_USER_BARCODE);
    final String checkoutServicePointId = getProperty(request, SERVICE_POINT_ID);
    final BlockOverrides blockOverrides = BlockOverrides.from(
      getObjectProperty(request, OVERRIDE_BLOCKS));
    final String forceLoanPolicyId = getProperty(request, FORCE_LOAN_POLICY_ID);

    return new CheckOutByBarcodeRequest(defaultLoanDate(loanDate), itemBarcode, userBarcode,
      proxyUserBarcode, checkoutServicePointId, blockOverrides, forceLoanPolicyId);
  }

  private static String defaultLoanDate(String loanDate) {
    log.debug("defaultLoanDate:: parameters loanDate: {}", loanDate);

    String result = isNotBlank(loanDate)
      ? loanDate
      : formatDateTime(ClockUtil.getZonedDateTime());

    log.info("defaultLoanDate:: {}", result);
    return result;
  }

  public Loan toLoan() {
    log.debug("toLoan:: ");
    final JsonObject loanJson = new JsonObject();

    loanJson.put("id", UUID.randomUUID().toString());
    loanJson.put(CHECKOUT_SERVICE_POINT_ID, checkoutServicePointId);
    loanJson.put(LOAN_DATE, loanDate);

    Loan loan = Loan.from(loanJson);
    log.info("toLoan:: {}", loan);
    return loan;
  }
}
