package org.folio.circulation.resources.renewal;

import java.util.List;
import java.util.Map;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.HttpFailure;

public record AutomaticRenewalEligibilityResult(
    List<Loan> eligible,
    Map<String, HttpFailure> holdRecallFailures) {
}
