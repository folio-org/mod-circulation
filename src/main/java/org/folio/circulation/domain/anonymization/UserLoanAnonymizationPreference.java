package org.folio.circulation.domain.anonymization;

/**
 * Loan anonymization preference of a user who deviates from the library policy.
 */
public record UserLoanAnonymizationPreference(String userId, String preference) { }
