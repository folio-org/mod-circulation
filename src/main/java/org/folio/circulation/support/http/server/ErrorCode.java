package org.folio.circulation.support.http.server;

public enum ErrorCode {

  ITEM_NOT_AVAILABLE,
  USER_BARCODE_NOT_FOUND,
  PATRON_BLOCK_LIMIT_REACHED,
  ITEM_HAS_OPEN_LOAN,
  UNSEND_DEFAULT_VALUE
}
