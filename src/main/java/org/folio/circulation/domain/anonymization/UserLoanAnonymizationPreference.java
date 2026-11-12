package org.folio.circulation.domain.anonymization;

import org.folio.circulation.domain.User;

/**
 * Loan anonymization preference of a user who deviates from the library policy.
 */
public record UserLoanAnonymizationPreference(String userId, String preference) {
  public static UserLoanAnonymizationPreference from(User user) {
    return new UserLoanAnonymizationPreference(user.getId(), user.getLoanAnonymizationPreference());
  }
}
