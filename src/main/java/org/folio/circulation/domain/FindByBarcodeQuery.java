package org.folio.circulation.domain;

public interface FindByBarcodeQuery extends UserRelatedQuery {
  String getItemBarcode();
  String getUserBarcode();
}
