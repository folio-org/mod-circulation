package org.folio.circulation.domain.representations;

import io.vertx.core.json.JsonObject;

public class CheckOutByBarcodeRequest {

  public static final String ITEM_BARCODE = "itemBarcode";
  public static final String USER_BARCODE = "userBarcode";
  public static final String PROXY_USER_BARCODE = "proxyUserBarcode";
  public static final String SERVICE_POINT_ID = "servicePointId";
  public static final String LOAD_DATE = "loanDate";

  private String itemBarcode;
  private String userBarcode;
  private String proxyUserBarcode;
  private String servicePointId;
  private String loanDate;


  private CheckOutByBarcodeRequest(String itemBarcode, String userBarcode, String proxyUserBarcode, String servicePointId,
                                  String loanDate) {
    this.itemBarcode = itemBarcode;
    this.userBarcode = userBarcode;
    this.proxyUserBarcode = proxyUserBarcode;
    this.servicePointId = servicePointId;
    this.loanDate = loanDate;
  }

  public static CheckOutByBarcodeRequest from(JsonObject request) {
    String itemBarcode = request.getString(ITEM_BARCODE);
    String userBarcode = request.getString(USER_BARCODE);
    String proxyUserBarcode = request.getString(PROXY_USER_BARCODE);
    String servicePointId = request.getString(SERVICE_POINT_ID);
    String loanDate = request.getString(LOAD_DATE);

    return new CheckOutByBarcodeRequest(itemBarcode, userBarcode, proxyUserBarcode, servicePointId, loanDate);
  }

  public String getItemBarcode() {
    return itemBarcode;
  }

  public String getUserBarcode() {
    return userBarcode;
  }

  public String getProxyUserBarcode() {
    return proxyUserBarcode;
  }

  public String getServicePointId() {
    return servicePointId;
  }

  public String getLoanDate() {
    return loanDate;
  }
}
