package org.folio.circulation.resources.renewal;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT;
import static org.folio.circulation.resources.RenewalValidator.errorForDueDate;
import static org.folio.circulation.resources.RenewalValidator.errorForNotMatchingOverrideCases;
import static org.folio.circulation.resources.RenewalValidator.errorWhenEarlierOrSameDueDate;
import static org.folio.circulation.support.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import io.vertx.core.json.JsonObject;

public class OverrideRenewalStrategy implements RenewalStrategy {

  private static final String COMMENT = "comment";
  private static final String DUE_DATE = "dueDate";

  @Override
  public CompletableFuture<Result<RenewalContext>> renew(RenewalContext context,
    Clients clients) {

    final JsonObject requestBody = context.getRenewalRequest();
    final String comment = getProperty(requestBody, COMMENT);
    if (StringUtils.isBlank(comment)) {
      return completedFuture(failedValidation("Override renewal request must have a comment",
        COMMENT, null));
    }
    final DateTime overrideDueDate = getDateTimeProperty(requestBody, DUE_DATE);

    Loan loan = context.getLoan();
    boolean hasRecallRequest =
    context.getRequestQueue().getRequests().stream().findFirst()
      .map(r -> r.getRequestType() == RequestType.RECALL)
      .orElse(false);

    return completedFuture(overrideRenewal(loan, DateTime.now(DateTimeZone.UTC),
      overrideDueDate, comment, hasRecallRequest))
      .thenApply(mapResult(context::withLoan));
  }

  private Result<Loan> overrideRenewal(Loan loan, DateTime systemDate,
    DateTime overrideDueDate, String comment, boolean hasRecallRequest) {
    LoanPolicy loanPolicy = loan.getLoanPolicy();
    try {
      if (loanPolicy.isNotLoanable() || loanPolicy.isNotRenewable()) {
        return overrideRenewalForDueDate(loan, overrideDueDate, comment);
      }
      final Result<DateTime> proposedDueDateResult =
        loanPolicy.determineStrategy(null, true, false, systemDate).calculateDueDate(loan);

      if (proposedDueDateResult.failed()) {
        return overrideRenewalForDueDate(loan, overrideDueDate, comment);
      }

      if (loanPolicy.hasReachedRenewalLimit(loan)) {
        return processRenewal(proposedDueDateResult, loan, comment);
      }

      if (hasRecallRequest) {
        return processRenewal(proposedDueDateResult, loan, comment);
      }

      if (loan.isItemLost()) {
        return processRenewal(proposedDueDateResult, loan, comment)
          .map(dueDate -> loan.changeItemStatusForItemAndLoan(CHECKED_OUT));
      }

      return failedValidation(errorForNotMatchingOverrideCases(loanPolicy));

    } catch (Exception e) {
      return failedDueToServerError(e);
    }
  }

  private Result<Loan> overrideRenewalForDueDate(Loan loan, DateTime overrideDueDate, String comment) {
    if (overrideDueDate == null) {
      return failedValidation(errorForDueDate());
    }
    return succeeded(loan.overrideRenewal(overrideDueDate, loan.getLoanPolicyId(), comment));
  }

  private Result<Loan> processRenewal(Result<DateTime> calculatedDueDate, Loan loan, String comment) {
    return calculatedDueDate
      .next(dueDate -> errorWhenEarlierOrSameDueDate(loan, dueDate))
      .map(dueDate -> loan.overrideRenewal(dueDate, loan.getLoanPolicyId(), comment));
  }
}
