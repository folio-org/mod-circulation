package org.folio.circulation.domain.validation;

import org.folio.circulation.domain.User;

public class RequestValidator {

  private static final String FAKE_PATRON_FIRST_NAME = "Secure";
  private static final String FAKE_PATRON_LAST_NAME = "Patron";
  private static final String FAKE_PATRON_BARCODE = "securepatron";

  public static boolean isMediatedRequest(User requester) {
    return requester.getFirstName().equals(FAKE_PATRON_FIRST_NAME) &&
      requester.getLastName().equals(FAKE_PATRON_LAST_NAME) &&
      requester.getBarcode().equals(FAKE_PATRON_BARCODE);
  }

}
