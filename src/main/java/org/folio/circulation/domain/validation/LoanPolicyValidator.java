package org.folio.circulation.domain.validation;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.ITEM_BARCODE;
import static org.folio.circulation.resources.RenewalValidator.loanPolicyValidationError;
import static org.folio.circulation.support.http.server.ErrorCode.ITEM_NOT_AVAILABLE;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.representations.CheckOutByBarcodeRequest;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.results.Result;

public class LoanPolicyValidator {
  private final Function<LoanPolicy, ValidationErrorFailure> itemLimitErrorFunction;

  public LoanPolicyValidator(Function<LoanPolicy, ValidationErrorFailure> itemLimitErrorFunction) {
    this.itemLimitErrorFunction = itemLimitErrorFunction;
  }

  public LoanPolicyValidator(CheckOutByBarcodeRequest request) {
    this(loanPolicy -> new ValidationErrorFailure(
      loanPolicyValidationError(loanPolicy, "Item is not loanable",
        Map.of(ITEM_BARCODE, request.getItemBarcode()), ITEM_NOT_AVAILABLE)));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> refuseWhenItemIsNotLoanable(
    LoanAndRelatedRecords relatedRecords) {

    LoanPolicy loanPolicy = relatedRecords.getLoan().getLoanPolicy();
    if (loanPolicy.isNotLoanable()) {
      return completedFuture(failed(itemLimitErrorFunction.apply(loanPolicy)));
    }

    return ofAsync(() -> relatedRecords);
  }
}
