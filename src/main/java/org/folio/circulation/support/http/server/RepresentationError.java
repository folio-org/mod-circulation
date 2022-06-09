package org.folio.circulation.support.http.server;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RepresentationError {

  ITEM_NOT_AVAILABLE("Item is not loanable"),
  USER_BARCODE_NOT_FOUND("Could not find user with matching barcode"),
  PATRON_BLOCK_LIMIT_REACHED("Patron has reached maximum limit of"),
  ITEM_HAS_OPEN_LOAN("Cannot check out item that already has an open loan");

  private final String description;
}
