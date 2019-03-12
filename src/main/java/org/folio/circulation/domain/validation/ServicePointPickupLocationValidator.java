package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.succeeded;

import java.lang.invoke.MethodHandles;

import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.RequestFulfilmentPreference;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServicePointPickupLocationValidator {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public HttpResult<RequestAndRelatedRecords> checkServicePointPickupLocation(
      HttpResult<RequestAndRelatedRecords> requestAndRelatedRecordsResult) {

    return requestAndRelatedRecordsResult.next(
      this::refuseInvalidPickupServicePoint);
  }

  private HttpResult<RequestAndRelatedRecords> refuseInvalidPickupServicePoint(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    Request request = null;

    if(requestAndRelatedRecords != null) {
      request = requestAndRelatedRecords.getRequest();
    }

    if(request == null) {
      log.info("No request present in RequestAndRelatedRecords object");
      return succeeded(requestAndRelatedRecords);
    }

    if(request.getPickupServicePointId() == null) {
      if(request.getFulfilmentPreference() == RequestFulfilmentPreference.HOLD_SHELF) {
        log.info("Hold Shelf Fulfilment Requests require a Pickup Service Point");
        return failed(ValidationErrorFailure.failure(
              "Hold Shelf Fulfilment Requests require a Pickup Service Point", "id",
              request.getId()));
      } else {
        log.info("No pickup service point specified for request");
        return succeeded(requestAndRelatedRecords);
      }
    }

    if(request.getPickupServicePointId() != null && request.getPickupServicePoint() == null) {
      return failed(ValidationErrorFailure.failure(
        "Pickup service point does not exist", "pickupServicePointId",
        request.getPickupServicePointId()));
    }

    if(request.getPickupServicePoint() != null) {
      log.info("Request {} has non-null pickup location", request.getId());

      if(request.getPickupServicePoint().isPickupLocation()) {
        return succeeded(requestAndRelatedRecords);
      } else {
        log.info("Request {} has {} as a pickup location which is an invalid service point",
            request.getId(), request.getPickupServicePointId());

        return failed(ValidationErrorFailure.failure(
            "Service point is not a pickup location", "pickupServicePointId",
            request.getPickupServicePointId()));
      }
    }
    else {
      log.info("Request {} has null pickup location", request.getId());
      return succeeded(requestAndRelatedRecords);
    }
  }
}
