package org.folio.circulation.domain;

public class UpdateUponRequest {
  final UpdateItem updateItem;
  final UpdateLoan updateLoan;
  final UpdateLoanActionHistory updateLoanActionHistory;
  final UpdateRequestQueue updateRequestQueue;
  
  public UpdateUponRequest(UpdateItem updateItem, UpdateLoan updateLoan,
      UpdateLoanActionHistory updateLoanActionHistory, UpdateRequestQueue updateRequestQueue) {
    this.updateItem = updateItem;
    this.updateLoan = updateLoan;
    this.updateLoanActionHistory = updateLoanActionHistory;
    this.updateRequestQueue = updateRequestQueue;
  }
}
