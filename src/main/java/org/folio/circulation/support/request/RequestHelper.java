package org.folio.circulation.support.request;

import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.RequestType;

public final class RequestHelper {

  private RequestHelper() {
    throw new UnsupportedOperationException("Do not instantiate");
  }

  public static boolean isRequestBeganFulfillment(Request request) {
    return request != null && request.getStatus() != RequestStatus.OPEN_NOT_YET_FILLED;
  }

  public static boolean isPageRequest(Request request) {
    return request != null && request.getRequestType() == RequestType.PAGE;
  }
}
