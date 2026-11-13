package org.folio.circulation.resources.renewal;

import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.lang.invoke.MethodHandles;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.support.HttpFailure;

public final class BulkRenewalEligibilityFilter {

  private static final Logger log =
    LogManager.getLogger(MethodHandles.lookup().lookupClass());

  static final String RECALL_BLOCKS_REASON =
    "items cannot be renewed when there is an active recall request";
  static final String HOLD_BLOCKS_REASON =
    "Items with this loan policy cannot be renewed when there is an active, pending hold request";

  private BulkRenewalEligibilityFilter() {
  }

  /**
   * Filters a list of enriched loans to only those eligible for automatic renewal.
   *
   * @param loans             enriched loans for one page (item + user attached)
   * @param policiesByLoanId  resolved LoanPolicy per loan ID
   * @param queuesByLoanId    RequestQueue per loan ID (may be absent for loans with no requests)
   * @param timeZone          tenant time zone, used for window boundary calculation
   * @return result splitting loans into eligible and hold/recall-blocked
   */
  public static AutomaticRenewalEligibilityResult filter(
      List<Loan> loans,
      Map<String, LoanPolicy> policiesByLoanId,
      Map<String, RequestQueue> queuesByLoanId,
      ZoneId timeZone) {

    List<Loan> eligible = new ArrayList<>();
    Map<String, HttpFailure> holdRecallFailures = new LinkedHashMap<>();

    for (Loan loan : loans) {
      LoanPolicy policy = policiesByLoanId.get(loan.getId());

      if (policy == null || !policy.isAutomaticRenewalEnabled()) {
        log.debug("filter:: loanId={} excluded: automaticRenewal disabled or absent"
          + " in policyId={}", loan.getId(),
          policy != null ? policy.getId() : "null");
        continue;
      }

      Integer firstAttemptDays = policy.getFirstAutomaticRenewalAttemptDaysBeforeDueDate();
      if (firstAttemptDays == null) {
        log.debug("filter:: loanId={} excluded: outside eligibility window"
          + " (daysUntilDue=N/A, firstAttemptDays=null)", loan.getId());
        continue;
      }

      var dueDate = loan.getDueDate();
      if (dueDate == null) {
        log.debug("filter:: loanId={} excluded: due date is null", loan.getId());
        continue;
      }

      long daysUntilDue = ChronoUnit.DAYS.between(
        LocalDate.now(timeZone),
        dueDate.withZoneSameInstant(timeZone).toLocalDate());

      if (daysUntilDue > firstAttemptDays) {
        log.debug("filter:: loanId={} excluded: outside eligibility window"
          + " (daysUntilDue={}, firstAttemptDays={})",
          loan.getId(), daysUntilDue, firstAttemptDays);
        continue;
      }

      RequestQueue queue = queuesByLoanId.getOrDefault(loan.getId(),
        new RequestQueue(List.of()));
      String blockReason = findBlockingReason(loan, queue, policy);

      if (blockReason != null) {
        log.info("filter:: loanId={} excluded: blocked by hold/recall: {}",
          loan.getId(), blockReason);
        holdRecallFailures.put(loan.getId(),
          singleValidationError(blockReason, "loanId", loan.getId()));
        continue;
      }

      eligible.add(loan);
    }

    return new AutomaticRenewalEligibilityResult(eligible, holdRecallFailures);
  }

  private static String findBlockingReason(Loan loan, RequestQueue queue, LoanPolicy policy) {
    boolean recallBlocks = queue.getRequests().stream()
      .filter(r -> r.isFor(loan))
      .anyMatch(org.folio.circulation.domain.Request::isRecall);

    if (recallBlocks) {
      return RECALL_BLOCKS_REASON;
    }

    boolean holdBlocks = !policy.isHoldRequestRenewable()
      && firstOpenRequestIsHold(queue, loan);

    if (holdBlocks) {
      return HOLD_BLOCKS_REASON;
    }

    return null;
  }

  private static boolean firstOpenRequestIsHold(RequestQueue queue, Loan loan) {
    return queue.getRequests().stream()
      .filter(r -> r.isFor(loan))
      .anyMatch(org.folio.circulation.domain.Request::isHold);
  }
}
