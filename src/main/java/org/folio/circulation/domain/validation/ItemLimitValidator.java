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
import org.folio.circulation.domain.representations.CheckOutByBarcodeRequest;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.rules.AppliedRuleConditions;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;

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

    log.debug("refuseWhenItemLimitIsReached:: parameters records: {}", records);

    Loan loan = records.getLoan();
    Integer itemLimit = loan.getLoanPolicy().getItemLimit();

    if (itemLimit == null) {
      log.info("refuseWhenItemLimitIsReached:: itemLimit is null");
      return completedFuture(succeeded(records));
    }

    return ofAsync(() -> loan.getLoanPolicy().getRuleConditions())
      .thenComposeAsync(result -> result.failAfter(ruleConditions -> isLimitReached(ruleConditions, records),
        ruleConditions -> {
          log.info("refuseWhenItemLimitIsReached:: ruleConditions: {}", ruleConditions);
          ItemLimitValidationErrorCause cause = getValidationErrorCause(ruleConditions);

          if (cause == null) {
            log.info("refuseWhenItemLimitIsReached:: cause is null");
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

    log.debug("isLimitReached:: parameters ruleConditionsEntity: {}, records: {}",
      ruleConditionsEntity, records);

    if (!ruleConditionsEntity.isItemTypePresent() && !ruleConditionsEntity.isLoanTypePresent()) {
      log.info("refuseWhenItemLimitIsReached:: " +
        "item type and loan type are missing from rule conditions");
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

  private boolean isMaterialTypeMatchInRetrievedLoan(String expectedMaterialTypeId,
    Loan loanRecord, AppliedRuleConditions ruleConditions) {

    log.debug("isMaterialTypeMatchInRetrievedLoan:: parameters expectedMaterialTypeId: {}, " +
        "loanRecord: {}, ruleConditions: {}", expectedMaterialTypeId, loanRecord, ruleConditions);

    if (!ruleConditions.isItemTypePresent()) {
      log.info("isMaterialTypeMatchInRetrievedLoan:: item type is missing from rule conditions");
      return true;
    }

    var result = expectedMaterialTypeId != null
      && expectedMaterialTypeId.equals(loanRecord.getItem().getMaterialTypeId());
    log.info("isMaterialTypeMatchInRetrievedLoan:: result {}", result);
    return result;
  }

  private boolean isLoanTypeMatchInRetrievedLoan(String expectedLoanType, Loan loanRecord,
    AppliedRuleConditions ruleConditions) {

    log.debug("isLoanTypeMatchInRetrievedLoan:: parameters expectedLoanType: {}, " +
      "loanRecord: {}, ruleConditions: {}", expectedLoanType, loanRecord, ruleConditions);

    if (!ruleConditions.isLoanTypePresent()) {
      log.info("isMaterialTypeMatchInRetrievedLoan:: loan type is missing from rule conditions");
      return true;
    }

    var result = expectedLoanType != null
      && expectedLoanType.equals(loanRecord.getItem().getLoanTypeId());
    log.info("isLoanTypeMatchInRetrievedLoan:: result {}", result);
    return result;
  }

  private ItemLimitValidationErrorCause getValidationErrorCause(AppliedRuleConditions ruleConditionsEntity) {
    log.debug("getValidationErrorCause:: parameters ruleConditionsEntity: {}", ruleConditionsEntity);

    boolean isRuleMaterialTypePresent = ruleConditionsEntity.isItemTypePresent();
    boolean isRuleLoanTypePresent = ruleConditionsEntity.isLoanTypePresent();
    boolean isRulePatronGroupPresent = ruleConditionsEntity.isPatronGroupPresent();

    log.info("getValidationErrorCause:: isRuleMaterialTypePresent: {}, isRuleLoanTypePresent: {}, " +
      "isRulePatronGroupPresent: {}", isRuleMaterialTypePresent, isRuleLoanTypePresent,
      isRulePatronGroupPresent);

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
