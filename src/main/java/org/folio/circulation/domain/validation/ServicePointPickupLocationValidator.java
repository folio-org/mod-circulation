package org.folio.circulation.domain.validation;

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
      return CompletableFuture.completedFuture(requestResult);
    }
    if(request.getPickupServicePoint() != null) {
      log.info("Request {} has non-null pickup location", request.getId());
      if(request.getPickupServicePoint().isPickupLocation()) {
        return CompletableFuture.completedFuture(requestResult);
      } else {            
        log.info("Request {} has {} as a pickup location which is an invalid service point", 
            request.getId(), request.getPickupServicePointId());
        return CompletableFuture.completedFuture(HttpResult.failed(ValidationErrorFailure.failure(
            "Service Point is not a Pickup Location", "pickupLocation", "false")));
      }
    } else {
      log.info("Request {} has null pickup location", request.getId());
      return CompletableFuture.completedFuture(requestResult);
    }
    
  }
}
