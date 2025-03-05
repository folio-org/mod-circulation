package org.folio.circulation.domain.representations;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class CheckOutByBarcodeDryRunRequest {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public static final String ITEM_BARCODE = "itemBarcode";
  public static final String USER_BARCODE = "userBarcode";
  public static final String PROXY_USER_BARCODE = "proxyUserBarcode";

  private final String itemBarcode;
  private final String userBarcode;
  private final String proxyUserBarcode;

  public static CheckOutByBarcodeDryRunRequest fromJson(JsonObject request) {
    log.debug("fromJson:: parameters request: {}", request);

    final String itemBarcode = getProperty(request, ITEM_BARCODE);
    final String userBarcode = getProperty(request, USER_BARCODE);
    final String proxyUserBarcode = getProperty(request, PROXY_USER_BARCODE);

    return new CheckOutByBarcodeDryRunRequest(itemBarcode, userBarcode, proxyUserBarcode);
  }
}
