package org.folio.circulation.domain.representations;

public class CheckOutByBarcodeRequest {
  private CheckOutByBarcodeRequest() { }

  public static final String ITEM_BARCODE_PROPERTY_NAME = "itemBarcode";
  public static final String USER_BARCODE_PROPERTY_NAME = "userBarcode";
  public static final String PROXY_USER_BARCODE_PROPERTY_NAME = "proxyUserBarcode";
}
