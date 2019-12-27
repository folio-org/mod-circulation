package org.folio.circulation.resources;

import static org.folio.circulation.domain.representations.LoanProperties.ITEM_ID;
import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.ServicePointRepository;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.validation.ItemNotFoundValidator;
import org.folio.circulation.support.Result;

import java.util.concurrent.CompletableFuture;

public class LoanCollectionResourceHelper {

  private LoanCollectionResourceHelper(){

  }

  public static Result<LoanAndRelatedRecords> addItem(
    Result<LoanAndRelatedRecords> loanResult,
    Result<Item> item) {

    return Result.combine(loanResult, item,
      LoanAndRelatedRecords::withItem);
  }

  public static Result<LoanAndRelatedRecords> addRequestQueue(
    Result<LoanAndRelatedRecords> loanResult,
    Result<RequestQueue> requestQueueResult) {

    return Result.combine(loanResult, requestQueueResult,
      LoanAndRelatedRecords::withRequestQueue);
  }

  public static Result<LoanAndRelatedRecords> addUser(
    Result<LoanAndRelatedRecords> loanResult,
    Result<User> getUserResult) {

    return Result.combine(loanResult, getUserResult,
      LoanAndRelatedRecords::withRequestingUser);
  }

  public static Result<LoanAndRelatedRecords> refuseWhenClosedAndNoCheckInServicePointId(
    Result<LoanAndRelatedRecords> loanAndRelatedRecords) {

    return loanAndRelatedRecords
      .map(LoanAndRelatedRecords::getLoan)
      .next(Loan::closedLoanHasCheckInServicePointId)
      .next(v -> loanAndRelatedRecords);
  }

  public static Result<LoanAndRelatedRecords> refuseWhenNotOpenOrClosed(
    Result<LoanAndRelatedRecords> loanAndRelatedRecords) {

    return loanAndRelatedRecords
      .map(LoanAndRelatedRecords::getLoan)
      .next(Loan::isValidStatus)
      .next(v -> loanAndRelatedRecords);
  }

  public static Result<LoanAndRelatedRecords> refuseWhenOpenAndNoUserId(
    Result<LoanAndRelatedRecords> loanAndRelatedRecords) {

    return loanAndRelatedRecords
      .map(LoanAndRelatedRecords::getLoan)
      .next(Loan::openLoanHasUserId)
      .next(v -> loanAndRelatedRecords);
  }

  public static CompletableFuture<Result<LoanAndRelatedRecords>> getServicePointsForLoanAndRelated(
    Result<LoanAndRelatedRecords> larrResult,
    ServicePointRepository servicePointRepository) {

    return larrResult.combineAfter(loanAndRelatedRecords ->
        getServicePointsForLoan(loanAndRelatedRecords.getLoan(), servicePointRepository),
      LoanAndRelatedRecords::withLoan);
  }

  private static CompletableFuture<Result<Loan>> getServicePointsForLoan(
    Loan loan,
    ServicePointRepository servicePointRepository) {

    return servicePointRepository.findServicePointsForLoan(of(() -> loan));
  }

  public static ItemNotFoundValidator createItemNotFoundValidator(Loan loan) {
    return new ItemNotFoundValidator(
      () -> singleValidationError(
        String.format("No item with ID %s could be found", loan.getItemId()),
        ITEM_ID, loan.getItemId()));
  }
}
