package org.folio.circulation.resources.renewal;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.HttpFailure;

public record BulkRenewalPageContext(
  List<Loan> loans,
  Map<String, RequestQueue> requestQueuesByLoanId,
  ZoneId timeZone,
  String triggeringUserId,
  String jobId,
  int pageNumber,
  List<RenewalContext> successfulRenewalContexts,
  Map<String, HttpFailure> failedRenewalsByLoanId
) {
  public BulkRenewalPageContext {
    loans = loans == null ? List.of() : List.copyOf(loans);
    requestQueuesByLoanId = requestQueuesByLoanId == null
      ? Map.of()
      : Map.copyOf(requestQueuesByLoanId);
    successfulRenewalContexts = successfulRenewalContexts == null
      ? List.of()
      : List.copyOf(successfulRenewalContexts);
    failedRenewalsByLoanId = failedRenewalsByLoanId == null
      ? Map.of()
      : Map.copyOf(failedRenewalsByLoanId);
  }

  public int attemptedCount() {
    return loans.size();
  }

  public int successfulCount() {
    return successfulRenewalContexts.size();
  }

  public int failedCount() {
    return failedRenewalsByLoanId.size();
  }

  public List<Loan> successfulLoans() {
    return successfulRenewalContexts.stream()
      .map(RenewalContext::getLoan)
      .toList();
  }
}
