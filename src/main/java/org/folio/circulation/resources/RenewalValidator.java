package org.folio.circulation.resources;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.server.ValidationError;
import org.joda.time.DateTime;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

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

  private RenewalValidator() {

  }

  public static void errorWhenEarlierOrSameDueDate(
    Loan loan, DateTime proposedDueDate, List<ValidationError> errors) {

    if(isSameOrBefore(loan, proposedDueDate)) {
      errors.add(loanPolicyValidationError(loan.getLoanPolicy(), RENEWAL_WOULD_NOT_CHANGE_THE_DUE_DATE));
    }
  }

  public static Result<DateTime> errorWhenEarlierOrSameDueDate(Loan loan, DateTime proposedDueDate) {
    if (isSameOrBefore(loan, proposedDueDate)) {
      return failedValidation(loanPolicyValidationError(loan.getLoanPolicy(),
        RENEWAL_WOULD_NOT_CHANGE_THE_DUE_DATE));
    }
    return Result.succeeded(proposedDueDate);
  }

  private static boolean isSameOrBefore(Loan loan, DateTime proposedDueDate) {
    return proposedDueDate.isEqual(loan.getDueDate())
      || proposedDueDate.isBefore(loan.getDueDate());
  }

  public static ValidationError loanPolicyValidationError(LoanPolicy loanPolicy,
    String message) {
    return loanPolicyValidationError(loanPolicy, message, Collections.emptyMap());
  }

  public static ValidationError loanPolicyValidationError(LoanPolicy loanPolicy,
    String message, Map<String, String> additionalParameters) {

    Map<String, String> parameters = new HashMap<>(additionalParameters);
    parameters.put("loanPolicyId", loanPolicy.getId());
    parameters.put("loanPolicyName", loanPolicy.getName());
    return new ValidationError(message, parameters);
  }

  public static ValidationError errorForNotMatchingOverrideCases(LoanPolicy loanPolicy) {
    String reason = "Override renewal does not match any of expected cases: " +
      "item is not loanable, " +
      "item is not renewable, " +
      "reached number of renewals limit," +
      "renewal date falls outside of the date ranges in the loan policy, " +
      "items cannot be renewed when there is an active recall request, " +
      "item is Declared lost";

    return loanPolicyValidationError(loanPolicy, reason);
  }

  public static ValidationError errorForDueDate() {
    return new ValidationError(
      "New due date must be specified when due date calculation fails",
      "dueDate", "null");
  }

  public static ValidationError errorForRecallRequest(String reason, String requestId) {
    return new ValidationError(reason, "request id", requestId);
  }

  public static ValidationError itemByIdValidationError(String reason, String itemId) {
    return new ValidationError(reason, "itemId", itemId);
  }
}
