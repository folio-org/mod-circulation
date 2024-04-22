package org.folio.circulation.resources;

import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.DateTimeUtil.isBeforeMillis;
import static org.folio.circulation.support.utils.DateTimeUtil.isSameMillis;
import static org.folio.circulation.support.utils.LogUtil.mapAsString;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.support.ErrorCode;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;

public final class RenewalValidator {
  public static final String RENEWAL_WOULD_NOT_CHANGE_THE_DUE_DATE = "renewal would not change the due date";
  public static final String CAN_NOT_RENEW_ITEM_ERROR =
    "Items with this loan policy cannot be renewed when there is an active, pending hold request";

  public static final String FIXED_POLICY_HAS_ALTERNATE_RENEWAL_PERIOD_FOR_HOLDS =
    "Item's loan policy has fixed profile but alternative renewal period for holds is specified";

  public static final String FIXED_POLICY_HAS_ALTERNATE_RENEWAL_PERIOD =
    "Item's loan policy has fixed profile but renewal period is specified";

  public static final String DECLARED_LOST_ITEM_RENEWED_ERROR = "item is Declared lost";
  public static final String CLAIMED_RETURNED_RENEWED_ERROR = "item is Claimed returned";
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private RenewalValidator() { }

  public static void errorWhenEarlierOrSameDueDate(Loan loan,
    ZonedDateTime proposedDueDate, List<ValidationError> errors) {

    if (isSameOrBefore(loan, proposedDueDate)) {
      log.info("errorWhenEarlierOrSameDueDate:: due date from loan: {}, proposedDueDate: {}",
        loan::getDueDate, () -> proposedDueDate);
      errors.add(loanPolicyValidationError(loan.getLoanPolicy(), RENEWAL_WOULD_NOT_CHANGE_THE_DUE_DATE));
    }
  }

  public static Result<ZonedDateTime> errorWhenEarlierOrSameDueDate(Loan loan, ZonedDateTime proposedDueDate) {
    if (isSameOrBefore(loan, proposedDueDate)) {
      log.info("errorWhenEarlierOrSameDueDate:: due date from loan: {}, proposedDueDate: {}",
        loan::getDueDate, () -> proposedDueDate);

      return failedValidation(loanPolicyValidationError(loan.getLoanPolicy(),
        RENEWAL_WOULD_NOT_CHANGE_THE_DUE_DATE));
    }

    return succeeded(proposedDueDate);
  }

  private static boolean isSameOrBefore(Loan loan, ZonedDateTime proposedDueDate) {
    return isSameMillis(proposedDueDate, loan.getDueDate())
      || isBeforeMillis(proposedDueDate, loan.getDueDate());
  }

  public static ValidationError loanPolicyValidationError(LoanPolicy loanPolicy,
    String message) {
    return loanPolicyValidationError(loanPolicy, message, Collections.emptyMap());
  }

  public static ValidationError loanPolicyValidationError(LoanPolicy loanPolicy,
    String message, Map<String, String> additionalParameters) {

    Map<String, String> parameters = buildLoanPolicyParameters(additionalParameters, loanPolicy);
    return new ValidationError(message, parameters);
  }

  public static ValidationError loanPolicyValidationError(LoanPolicy loanPolicy,
    String message, Map<String, String> additionalParameters, ErrorCode errorCode) {

    Map<String, String> parameters = buildLoanPolicyParameters(additionalParameters, loanPolicy);
    return new ValidationError(message, parameters, errorCode);
  }

  private static Map<String, String> buildLoanPolicyParameters(
    Map<String, String> additionalParameters, LoanPolicy loanPolicy) {

    log.debug("buildLoanPolicyParameters:: parameters additionalParameters: {}, loanPolicy: {}",
      () -> mapAsString(additionalParameters), () -> loanPolicy);
    Map<String, String> result = new HashMap<>(additionalParameters);
    result.put("loanPolicyId", loanPolicy.getId());
    result.put("loanPolicyName", loanPolicy.getName());
    return result;
  }

  public static ValidationError errorForNotMatchingOverrideCases(LoanPolicy loanPolicy) {
    String reason = "Override renewal does not match any of expected cases: " +
      "item is not loanable, " +
      "item is not renewable, " +
      "reached number of renewals limit," +
      "renewal date falls outside of the date ranges in the loan policy, " +
      "items cannot be renewed when there is an active recall request, " +
      DECLARED_LOST_ITEM_RENEWED_ERROR + ", item is Aged to lost, " +
      "renewal would not change the due date, " +
      "loan has reminder fees";

    return loanPolicyValidationError(loanPolicy, reason);
  }

  public static ValidationError errorForDueDate() {
    return new ValidationError(
      "New due date must be specified when due date calculation fails",
      "dueDate", "null");
  }

  public static ValidationError overrideDueDateIsRequiredError() {
    return new ValidationError(
      "New due date is required when renewal would not change the due date",
      "dueDate", "null");
  }

  public static ValidationError errorForRecallRequest(String reason, String requestId) {
    return new ValidationError(reason, "requestId", requestId);
  }

  public static ValidationError itemByIdValidationError(String reason, String itemId) {
    return new ValidationError(reason, "itemId", itemId);
  }
}
