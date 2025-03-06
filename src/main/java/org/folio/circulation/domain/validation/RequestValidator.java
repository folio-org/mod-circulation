package org.folio.circulation.domain.validation;

import java.util.Objects;

import org.folio.circulation.domain.User;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RequestValidator {

  private static final String FAKE_PATRON_FIRST_NAME = "Secure";
  private static final String FAKE_PATRON_LAST_NAME = "Patron";
  private static final String FAKE_PATRON_BARCODE = "securepatron";

  public static boolean isMediatedRequest(User requester) {
  log.debug("isMediatedRequest:: Requester: {}", requester);

    if(Objects.isNull(requester)) {
      log.error("isMediatedRequest:: Requester is null");
      return false;
    }

    return requester.getFirstName().equals(FAKE_PATRON_FIRST_NAME)
      && requester.getLastName().equals(FAKE_PATRON_LAST_NAME)
      && requester.getBarcode().startsWith(FAKE_PATRON_BARCODE);
  }

}
