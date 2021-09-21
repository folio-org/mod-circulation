package org.folio.circulation.domain.validation.overriding;

import static org.folio.circulation.domain.LoanAction.CHECKED_OUT_THROUGH_OVERRIDE;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.DateTimeUtil.isAfterMillis;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.override.BlockOverrides;
import org.folio.circulation.domain.override.ItemNotLoanableBlockOverride;
import org.folio.circulation.domain.override.OverridableBlockType;
import org.folio.circulation.support.http.OkapiPermissions;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;

public class OverridingLoanValidator extends OverridingBlockValidator<LoanAndRelatedRecords> {
  private static final String DUE_DATE_NOT_SPECIFIED_MESSAGE =
    "Override should be performed with due date specified";
  private static final String DUE_DATE_NOT_AFTER_LOAN_DATE =
    "Due date should be later than loan date";
  private static final String COMMENT_NOT_SPECIFIED_MESSAGE =
    "Override should be performed with the comment specified";

  private static final String DUE_DATE_PARAM_NAME = "dueDate";
  private static final String COMMENT_PARAM_NAME = "comment";

  public OverridingLoanValidator(OverridableBlockType blockType, BlockOverrides blockOverrides,
    OkapiPermissions permissions) {

    super(blockType, blockOverrides, permissions);
  }

  @Override
  public CompletableFuture<Result<LoanAndRelatedRecords>> validate(LoanAndRelatedRecords records) {
    return super.validate(records)
      .thenApply(r -> r.next(this::refuseWhenCommentIsMissing))
      .thenApply(r -> r.next(this::refuseWhenDueDateIsMissing))
      .thenApply(r -> r.next(this::refuseWhenDueDateIsNotAfterLoanDate))
      .thenApply(r -> r.map(this::setDueDateIfNotLoanableOverriding))
      .thenApply(r -> r.map(this::setLoanAction));
  }

  private Result<LoanAndRelatedRecords> refuseWhenCommentIsMissing(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    String comment = getBlockOverrides().getComment();

    if (comment == null) {
      return failed(singleValidationError(new ValidationError(
        COMMENT_NOT_SPECIFIED_MESSAGE, COMMENT_PARAM_NAME, null)));
    }

    return succeeded(loanAndRelatedRecords);
  }

  private Result<LoanAndRelatedRecords> refuseWhenDueDateIsMissing(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    ItemNotLoanableBlockOverride itemNotLoanableBlockOverride = getBlockOverrides()
      .getItemNotLoanableBlockOverride();

    if (itemNotLoanableBlockOverride.isRequested() &&
      itemNotLoanableBlockOverride.getDueDate() == null) {

      return failed(singleValidationError(new ValidationError(
        DUE_DATE_NOT_SPECIFIED_MESSAGE, DUE_DATE_PARAM_NAME, null)));
    }

    return succeeded(loanAndRelatedRecords);
  }

  private Result<LoanAndRelatedRecords> refuseWhenDueDateIsNotAfterLoanDate(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    ItemNotLoanableBlockOverride itemNotLoanableBlockOverride = getBlockOverrides()
      .getItemNotLoanableBlockOverride();
    DateTime loanDate = loanAndRelatedRecords.getLoan().getLoanDate();
    DateTime requestedDueDate = itemNotLoanableBlockOverride.getDueDate();

    if (itemNotLoanableBlockOverride.isRequested() && !isAfterMillis(requestedDueDate, loanDate)) {
      return failed(singleValidationError(new ValidationError(DUE_DATE_NOT_AFTER_LOAN_DATE,
        DUE_DATE_PARAM_NAME, itemNotLoanableBlockOverride.getDueDateRaw())));
    }

    return succeeded(loanAndRelatedRecords);
  }

  private LoanAndRelatedRecords setLoanAction(LoanAndRelatedRecords loanAndRelatedRecords) {
    Loan loan = loanAndRelatedRecords.getLoan();
    loan.changeAction(CHECKED_OUT_THROUGH_OVERRIDE);
    loan.changeActionComment(getBlockOverrides().getComment());

    return loanAndRelatedRecords;
  }

  private LoanAndRelatedRecords setDueDateIfNotLoanableOverriding(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    ItemNotLoanableBlockOverride itemNotLoanableBlockOverride = getBlockOverrides()
      .getItemNotLoanableBlockOverride();

    if (itemNotLoanableBlockOverride.isRequested()) {
      loanAndRelatedRecords.getLoan().changeDueDate(itemNotLoanableBlockOverride.getDueDate());
    }

    return loanAndRelatedRecords;
  }
}
