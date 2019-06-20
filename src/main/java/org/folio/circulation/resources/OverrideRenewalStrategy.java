package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.ResultBinding.mapResult;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import io.vertx.core.json.JsonObject;

public class OverrideRenewalStrategy implements RenewalStrategy {

  private static final String COMMENT = "comment";
  private static final String DUE_DATE = "dueDate";

  @Override
  public CompletableFuture<Result<LoanAndRelatedRecords>> renew(
    LoanAndRelatedRecords relatedRecords, JsonObject requestBody, Clients clients) {

    final String comment = getProperty(requestBody, COMMENT);
    if (StringUtils.isBlank(comment)) {
      return completedFuture(failedValidation("Override renewal request must have a comment",
        COMMENT, null));
    }
    final DateTime overrideDueDate = getDateTimeProperty(requestBody, DUE_DATE);

    Loan loan = relatedRecords.getLoan();
    LoanPolicy loanPolicy = loan.getLoanPolicy();
    boolean hasRecallRequest =
    relatedRecords.getRequestQueue().getRequests().stream().findFirst()
      .map(r -> r.getRequestType() == RequestType.RECALL)
      .orElse(false);

    return completedFuture(loanPolicy.overrideRenewal(
      loan, DateTime.now(DateTimeZone.UTC),
      overrideDueDate, comment, hasRecallRequest))
      .thenApply(mapResult(relatedRecords::withLoan));
  }
}
