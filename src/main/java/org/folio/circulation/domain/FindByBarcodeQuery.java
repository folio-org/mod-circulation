package org.folio.circulation.domain;

import org.folio.circulation.support.ValidationErrorFailure;

public interface FindByBarcodeQuery {
  String getItemBarcode();
  String getUserBarcode();
  boolean userMatches(User user);
  ValidationErrorFailure userDoesNotMatchError();
}
