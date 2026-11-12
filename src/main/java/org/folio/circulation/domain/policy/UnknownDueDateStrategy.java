package org.folio.circulation.domain.policy;

import static java.lang.String.format;
import static org.folio.circulation.support.ErrorCode.LOAN_POLICY_PROFILE_NOT_RECOGNIZED;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import java.time.ZonedDateTime;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.ErrorCode;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;

class UnknownDueDateStrategy extends DueDateStrategy {
  private static final String CHECK_OUT_UNRECOGNISED_PROFILE_MESSAGE =
    "profile \"%s\" in the loan policy is not recognised";

  private static final String RENEWAL_UNRECOGNISED_PROFILE_MESSAGE =
    "profile \"%s\" in the loan policy is not recognised";

  private final String profileId;
  private final boolean isRenewal;

  UnknownDueDateStrategy(
    String loanPolicyId,
    String loanPolicyName,
    String profileId,
    boolean isRenewal, Function<String, ValidationError> errorForPolicy) {

    this(loanPolicyId, loanPolicyName, profileId, isRenewal,
      (message, errorCode) -> errorForPolicy.apply(message));
  }

  UnknownDueDateStrategy(
    String loanPolicyId,
    String loanPolicyName,
    String profileId,
    boolean isRenewal, BiFunction<String, ErrorCode, ValidationError> errorForPolicy) {

    super(loanPolicyId, loanPolicyName, errorForPolicy);
    this.profileId = profileId;
    this.isRenewal = isRenewal;
  }

  @Override
  public Result<ZonedDateTime> calculateDueDate(Loan loan) {
    if(isRenewal) {
      return failedValidation(errorForPolicy(
          format(RENEWAL_UNRECOGNISED_PROFILE_MESSAGE, profileId),
          LOAN_POLICY_PROFILE_NOT_RECOGNIZED));
    }
    else {
      return failedValidation(errorForPolicy(
        format(CHECK_OUT_UNRECOGNISED_PROFILE_MESSAGE, profileId)));
    }
  }
}
