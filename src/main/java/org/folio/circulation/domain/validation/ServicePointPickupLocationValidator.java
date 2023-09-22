package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.ErrorCode.HOLD_SHELF_REQUESTS_REQUIRE_PICKUP_SERVICE_POINT;
import static org.folio.circulation.support.ErrorCode.SERVICE_POINT_IS_NOT_PICKUP_LOCATION;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.RequestFulfillmentPreference;
import org.folio.circulation.support.results.Result;

public class ServicePointPickupLocationValidator {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public Result<RequestAndRelatedRecords> refuseInvalidPickupServicePoint(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    log.debug("refuseInvalidPickupServicePoint:: parameters requestAndRelatedRecords: {}",
      requestAndRelatedRecords);

    Request request = null;

    if (requestAndRelatedRecords != null) {
      log.info("refuseInvalidPickupServicePoint:: requestAndRelatedRecords is null");
      request = requestAndRelatedRecords.getRequest();
    }

    if (request == null) {
      log.info("refuseInvalidLoanServicePoints:: No request present in RequestAndRelatedRecords " +
        "object");
      return succeeded(requestAndRelatedRecords);
    }

    if (request.getPickupServicePointId() == null) {

      log.info("refuseInvalidPickupServicePoint:: pickupServicePointId is null");
      if (request.getfulfillmentPreference() == RequestFulfillmentPreference.HOLD_SHELF) {
        log.info("refuseInvalidPickupServicePoint:: Hold Shelf Fulfillment Requests require a " +
          "Pickup Service Point");
        return failedValidation(
          "Hold shelf fulfillment requests require a Pickup service point",
          "id", request.getId(), HOLD_SHELF_REQUESTS_REQUIRE_PICKUP_SERVICE_POINT);
      } else {
        log.info("refuseInvalidPickupServicePoint:: No pickup service point specified for request");
        return succeeded(requestAndRelatedRecords);
      }
    }

    if (request.getPickupServicePointId() != null && request.getPickupServicePoint() == null) {
      log.info("refuseInvalidPickupServicePoint:: Pickup service point does not exist");
      return failedValidation("Pickup service point does not exist",
        "pickupServicePointId", request.getPickupServicePointId());
    }

    if (request.getPickupServicePoint() != null) {
      log.info("refuseInvalidPickupServicePoint:: Request {} has non-null pickup location",
        request.getId());

      if (request.getPickupServicePoint().isPickupLocation()) {
        return succeeded(requestAndRelatedRecords);
      } else {
        log.info("refuseInvalidPickupServicePoint:: Request {} has {} as a pickup location which " +
            "is an invalid service point",
            request.getId(), request.getPickupServicePointId());

        return failedValidation(
            "Service point is not a pickup location",
          "pickupServicePointId", request.getPickupServicePointId(),
          SERVICE_POINT_IS_NOT_PICKUP_LOCATION);
      }
    }
    else {
      log.info("refuseInvalidPickupServicePoint:: Request {} has null pickup location",
        request.getId());
      return succeeded(requestAndRelatedRecords);
    }
  }
}
