package org.folio.circulation.domain;

public class UpdateUponRequest {
  final UpdateItem updateItem;
  final UpdateLoan updateLoan;
  final UpdateRequestQueue updateRequestQueue;
  
  public UpdateUponRequest(UpdateItem updateItem, UpdateLoan updateLoan, UpdateRequestQueue updateRequestQueue) {
    this.updateItem = updateItem;
    this.updateLoan = updateLoan;
    this.updateRequestQueue = updateRequestQueue;
  }
}
