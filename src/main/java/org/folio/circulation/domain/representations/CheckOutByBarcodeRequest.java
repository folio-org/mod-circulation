package org.folio.circulation.domain.representations;

public class CheckOutByBarcodeRequest {
  private CheckOutByBarcodeRequest() { }

  public static final String ITEM_BARCODE = "itemBarcode";
  public static final String USER_BARCODE = "userBarcode";
  public static final String PROXY_USER_BARCODE = "proxyUserBarcode";
	public static final String SERVICEPOINTOFCHECKOUT = "servicePointOfCheckout";
}
