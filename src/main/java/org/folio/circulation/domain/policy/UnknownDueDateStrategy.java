package org.folio.circulation.domain.policy;

import static org.folio.circulation.support.HttpResult.failed;

import java.util.function.Function;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.http.server.ValidationError;
import org.joda.time.DateTime;

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

    super(loanPolicyId, loanPolicyName, errorForPolicy);
    this.profileId = profileId;
    this.isRenewal = isRenewal;
  }

  @Override
  HttpResult<DateTime> calculateDueDate(Loan loan) {
    if (isRenewal) {
      return failed(validationError(
        String.format(RENEWAL_UNRECOGNISED_PROFILE_MESSAGE, profileId)));
    } else {
      return failed(validationError(
        String.format(CHECK_OUT_UNRECOGNISED_PROFILE_MESSAGE, profileId)));
    }
  }
}
