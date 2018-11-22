package org.folio.circulation.domain.validation;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.HttpResult.failed;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServicePointPickupLocationValidator {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  
  public CompletableFuture<HttpResult<RequestAndRelatedRecords>> checkServicePointPickupLocation(
      HttpResult<RequestAndRelatedRecords> requestResult) {
    
    Request request = null;
    if(requestResult.value() != null) {
      request = requestResult.value().getRequest();
    }
    if(request == null) {
      log.info("No request present in RequestAndRelatedRecords object");
      return completedFuture(requestResult);
    }
    if(request.getPickupServicePoint() != null) {
      log.info("Request {} has non-null pickup location", request.getId());
      if(request.getPickupServicePoint().isPickupLocation()) {
        return completedFuture(requestResult);
      } else {            
        log.info("Request {} has {} as a pickup location which is an invalid service point", 
            request.getId(), request.getPickupServicePointId());

        return completedFuture(failed(ValidationErrorFailure.failure(
            "Service point is not a pickup location", "pickupServicePointId",
            request.getPickupServicePointId())));
      }
    } else {
      log.info("Request {} has null pickup location", request.getId());
      return completedFuture(requestResult);
    }
    
  }
}
