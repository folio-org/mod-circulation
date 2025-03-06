package org.folio.circulation.domain.validation;

import java.util.Objects;

import org.folio.circulation.domain.User;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class RequestValidator {

  private static final String FAKE_PATRON_FIRST_NAME = "Secure";
  private static final String FAKE_PATRON_LAST_NAME = "Patron";

  public static boolean isMediatedRequest(User requester) {
  log.debug("isMediatedRequest:: Requester: {}", requester);

    if(Objects.isNull(requester)) {
      log.error("isMediatedRequest:: Requester is null");
      return false;
    }

    return FAKE_PATRON_FIRST_NAME.equals(requester.getFirstName())
      && FAKE_PATRON_LAST_NAME.equals(requester.getLastName());
  }

}
