package org.folio.circulation.domain.anonymization;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.AccountRepository;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.anonymization.checks.AnonymizationChecker;
import org.folio.circulation.domain.anonymization.checks.FeesAndFinesClosedAnonymizationChecker;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;

/**
 * Validates loan eligibility for anonymization. By default a loan
 * can only be anonymized if it's closed and there are no open fees
 * and fines associated with it.
 */
public class CheckingLoanAnonymizationService extends DefaultLoanAnonymizationService {

  private static final AnonymizationChecker checker = new FeesAndFinesClosedAnonymizationChecker();
  private final AccountRepository accountRepository;

  CheckingLoanAnonymizationService(Clients clients) {
    super(clients);
    accountRepository = new AccountRepository(clients);
  }

  @Override
  protected CompletableFuture<Result<Collection<Loan>>> populateLoanInformation(Result<MultipleRecords<Loan>> records) {
    return records.after(accountRepository::findOpenAccountsForLoans)
      .thenCompose(super::populateLoanInformation);
  }

  @Override
  protected CompletableFuture<Result<LoanAnonymizationRecords>> filterNotEligibleLoans(
      Result<LoanAnonymizationRecords> records) {

    return completedFuture(records.map(r -> {
      Map<Boolean, Set<String>> groupByAnonymizationEligibility = r.getInputLoans().stream()
        .collect(groupingBy(this::applyChecks, mapping(Loan::getId, toSet())));
      return r.withAnonymizedLoans(groupByAnonymizationEligibility.get(TRUE))
        .withNotAnonymizedLoans(groupByAnonymizationEligibility.get(FALSE));
    }));

  }

  private boolean applyChecks(Loan loan) {
    return checker.canBeAnonymized(loan);
  }

}
