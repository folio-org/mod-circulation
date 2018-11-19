/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.folio.circulation.domain.validation;

import java.util.concurrent.CompletableFuture;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.support.HttpResult;

/**
 *
 * @author kurt
 */
public class ServicePointPickupLocationValidator {
  public CompletableFuture<HttpResult<RequestAndRelatedRecords>> checkServicePointPickupLocation(
      HttpResult<RequestAndRelatedRecords> requestResult) {
    Request request = requestResult.value().getRequest();
    if(request.getPickupServicePoint() != null) {
      if(request.getPickupServicePoint().isPickupLocation()) {
        return CompletableFuture.completedFuture(requestResult);
      } else {
        /* some kind of failed return value here */
        return CompletableFuture.completedFuture(requestResult); //just so it doesn't break
      }
    }
    return CompletableFuture.completedFuture(requestResult);
  }
}
