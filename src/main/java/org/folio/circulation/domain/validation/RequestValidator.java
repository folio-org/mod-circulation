package org.folio.circulation.domain.validation;

import java.util.Objects;

import org.folio.circulation.domain.User;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class RequestValidator {

  private static final String SECURE_PATRON_FIRST_NAME = "Secure";
  private static final String SECURE_PATRON_LAST_NAME = "Patron";

  public static boolean isSecurePatron(User requester) {
  log.debug("isMediatedRequest:: Requester: {}", requester);

    if (Objects.isNull(requester)) {
      log.warn("isMediatedRequest:: Requester is null");
      return false;
    }

    return SECURE_PATRON_FIRST_NAME.equals(requester.getFirstName())
      && SECURE_PATRON_LAST_NAME.equals(requester.getLastName());
  }

}
