package org.folio.circulation.domain.validation;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.ITEM_BARCODE;
import static org.folio.circulation.domain.validation.ItemLimitValidationErrorCause.CAN_NOT_DETERMINE;
import static org.folio.circulation.domain.validation.ItemLimitValidationErrorCause.LOAN_TYPE;
import static org.folio.circulation.domain.validation.ItemLimitValidationErrorCause.MATERIAL_TYPE;
import static org.folio.circulation.domain.validation.ItemLimitValidationErrorCause.MATERIAL_TYPE_AND_LOAN_TYPE;
import static org.folio.circulation.domain.validation.ItemLimitValidationErrorCause.PATRON_GROUP_LOAN_TYPE;
import static org.folio.circulation.domain.validation.ItemLimitValidationErrorCause.PATRON_GROUP_MATERIAL_TYPE;
import static org.folio.circulation.domain.validation.ItemLimitValidationErrorCause.PATRON_GROUP_MATERIAL_TYPE_LOAN_TYPE;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.http.client.PageLimit.limit;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.representations.CheckOutByBarcodeRequest;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.rules.AppliedRuleConditions;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;

import io.netty.util.internal.StringUtil;

public class ItemLimitValidator {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final PageLimit LOANS_PAGE_LIMIT = limit(10000);
  private static final String ITEM_LIMIT = "itemLimit";
  private final Function<ItemLimitValidationErrorCause, ValidationErrorFailure>
    itemLimitErrorFunction;
  private final LoanRepository loanRepository;

  private ItemLimitValidator(
    Function<ItemLimitValidationErrorCause, ValidationErrorFailure> itemLimitErrorFunction,
    LoanRepository loanRepository) {

    this.itemLimitErrorFunction = itemLimitErrorFunction;
    this.loanRepository = loanRepository;
  }

  public ItemLimitValidator(CheckOutByBarcodeRequest request, LoanRepository loanRepository) {
    this(cause -> {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(ITEM_BARCODE, request.getItemBarcode());
        parameters.put(ITEM_LIMIT,
          cause.getItemLimit() != null ? cause.getItemLimit().toString() : null);

        return singleValidationError(new ValidationError(cause.formatMessage(),
          parameters, cause.getErrorCode()));
      }, loanRepository);
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> refuseWhenItemLimitIsReached(
    LoanAndRelatedRecords records) {

    Loan loan = records.getLoan();
    Integer itemLimit = loan.getLoanPolicy().getItemLimit();

    if (itemLimit == null) {
      return completedFuture(succeeded(records));
    }

    return ofAsync(() -> loan.getLoanPolicy().getRuleConditions())
      .thenComposeAsync(result -> result.failAfter(ruleConditions -> isLimitReached(ruleConditions, records),
        ruleConditions -> {
          ItemLimitValidationErrorCause cause = getValidationErrorCause(ruleConditions);

          if (cause == null) {
            String message = String.format("Can not determine item limit validation error cause " +
              "for item %s, patron %s", loan.getItemId(), loan.getUserId());
            log.warn(message);
            return itemLimitErrorFunction.apply(CAN_NOT_DETERMINE);
          }

          cause.setItemLimit(itemLimit);
          return itemLimitErrorFunction.apply(cause);
        }))
      .thenApply(result -> result.map(v -> records));
  }

  private CompletableFuture<Result<Boolean>> isLimitReached(
    AppliedRuleConditions ruleConditionsEntity, LoanAndRelatedRecords records) {

    if (!ruleConditionsEntity.isItemTypePresent() && !ruleConditionsEntity.isLoanTypePresent()) {
      return ofAsync(() -> false);
    }

    Item item = records.getLoan().getItem();
    String loanTypeId = item.getLoanTypeId();
    Integer itemLimit = records.getLoan().getLoanPolicy().getItemLimit();
    AppliedRuleConditions ruleConditions = records.getLoan().getLoanPolicy().getRuleConditions();

    return loanRepository.findOpenLoansByUserIdWithItem(LOANS_PAGE_LIMIT, records)
      .thenApply(r -> r.map(loanRecords -> loanRecords.getRecords().stream()
        .filter(loanRecord -> !loanRecord.getItem().isClaimedReturned())
        .filter(loanRecord -> isMaterialTypeMatchInRetrievedLoan(
          item.getMaterialTypeId(), loanRecord, ruleConditions))
        .filter(loanRecord -> isLoanTypeMatchInRetrievedLoan(
          loanTypeId, loanRecord, ruleConditions))
        .count()))
      .thenApply(r -> r.map(loansCount -> loansCount >= itemLimit));
  }

  private boolean isMaterialTypeMatchInRetrievedLoan(
    String expectedMaterialTypeId, Loan loanRecord, AppliedRuleConditions ruleConditions) {

    if (!ruleConditions.isItemTypePresent()) {
      return true;
    }

    return expectedMaterialTypeId != null
      && expectedMaterialTypeId.equals(loanRecord.getItem().getMaterialTypeId());
  }

  private boolean isLoanTypeMatchInRetrievedLoan(
    String expectedLoanType, Loan loanRecord, AppliedRuleConditions ruleConditions) {

    if (!ruleConditions.isLoanTypePresent()) {
      return true;
    }

    return expectedLoanType != null
      && expectedLoanType.equals(loanRecord.getItem().getLoanTypeId());
  }

  private ItemLimitValidationErrorCause getValidationErrorCause(AppliedRuleConditions ruleConditionsEntity) {
    boolean isRuleMaterialTypePresent = ruleConditionsEntity.isItemTypePresent();
    boolean isRuleLoanTypePresent = ruleConditionsEntity.isLoanTypePresent();
    boolean isRulePatronGroupPresent = ruleConditionsEntity.isPatronGroupPresent();

    if (isRulePatronGroupPresent && isRuleMaterialTypePresent && isRuleLoanTypePresent) {
      return PATRON_GROUP_MATERIAL_TYPE_LOAN_TYPE;
    } else if (isRulePatronGroupPresent && isRuleMaterialTypePresent) {
      return PATRON_GROUP_MATERIAL_TYPE;
    } else if (isRulePatronGroupPresent && isRuleLoanTypePresent) {
      return PATRON_GROUP_LOAN_TYPE;
    } else if (isRuleMaterialTypePresent && isRuleLoanTypePresent) {
      return MATERIAL_TYPE_AND_LOAN_TYPE;
    } else if (isRuleMaterialTypePresent) {
      return MATERIAL_TYPE;
    } else if (isRuleLoanTypePresent) {
      return LOAN_TYPE;
    }
    return null;
  }
}
