package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ValidationErrorFailure;

public class ChangeDueDateValidator {
  private final LoanRepository loanRepository;
  private final ItemStatusValidator itemStatusValidator;

  public ChangeDueDateValidator(LoanRepository loanRepository) {
    this.loanRepository = loanRepository;
    this.itemStatusValidator = new ItemStatusValidator(this::dueDateChangeFailedForItem);
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> refuseChangeDueDateForItemInDisallowedStatus(
    Result<LoanAndRelatedRecords> loanAndRelatedRecordsResult) {

    return loanAndRelatedRecordsResult.after(relatedRecords -> {
      final Loan changedLoan = relatedRecords.getLoan();

      return getExistingLoan(changedLoan)
        .thenApply(r -> r.next(existingLoan -> {
          if (dueDateHasChanged(existingLoan, changedLoan)) {
            return itemStatusValidator
              .refuseWhenItemStatusDoNotAllowDueDateChange(loanAndRelatedRecordsResult);
          }

          return loanAndRelatedRecordsResult;
        }));
    });
  }

  private boolean dueDateHasChanged(Loan existingLoan, Loan changedLoan) {
    return existingLoan != null
        && !existingLoan.getDueDate().equals(changedLoan.getDueDate());
  }

  private CompletableFuture<Result<Loan>> getExistingLoan(Loan loan) {
    return loanRepository.getById(loan.getId());
  }

  private ValidationErrorFailure dueDateChangeFailedForItem(Item item) {
    return singleValidationError("item is " + item.getStatusName(),
      "itemId", item.getItemId());
  }
}
