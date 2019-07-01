package org.folio.circulation.domain;

public class UpdateUponRequest {
  final UpdateItem updateItem;
  final UpdateLoan updateLoan;
  final UpdateLoanActionHistory updateLoanActionHistory;
  
  public UpdateUponRequest(UpdateItem updateItem, UpdateLoan updateLoan,
      UpdateLoanActionHistory updateLoanActionHistory) {
    this.updateItem = updateItem;
    this.updateLoan = updateLoan;
    this.updateLoanActionHistory = updateLoanActionHistory;
  }
}
