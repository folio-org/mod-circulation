package org.folio.circulation.domain.anonymization;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.circulation.domain.anonymization.config.ClosingType.IMMEDIATELY;
import static org.folio.circulation.domain.anonymization.config.ClosingType.NEVER;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.ArrayList;
import java.util.UUID;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.anonymization.config.ClosingType;
import org.folio.circulation.domain.anonymization.config.LoanAnonymizationConfiguration;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.loans.AnonymizeStorageLoansRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;

public class LoanAnonymizationTests {
  @Mock
  Clients clients;
  @Mock
  LoanRepository loanRepository;
  @Mock
  AccountRepository accountRepository;
  @Mock
  AnonymizeStorageLoansRepository anonymizeStorageLoansRepository;
  @Mock
  EventPublisher eventPublisher;

  @BeforeEach
  public void init() {
    initMocks(this);
  }

  @SneakyThrows
  @Test
  void shouldAnonymizeLoansImmediatelyWhenConfiguredToDoSo() {
    final var config = anonymizeLoans(IMMEDIATELY);

    final var loanAnonymization = new LoanAnonymization(loanRepository,
      accountRepository, anonymizeStorageLoansRepository, eventPublisher);

    final var service = loanAnonymization.byCurrentTenant(config);

    singleLoanToAnonymize();

    final var finished = service.anonymizeLoans();

    finished.get(1, SECONDS);

    verify(loanRepository, times(1)).findLoansToAnonymize(any());
    verifyNoMoreInteractions(loanRepository);

    verify(accountRepository, times(1)).findAccountsForLoans(any());
    verifyNoMoreInteractions(accountRepository);
  }

  @SneakyThrows
  @Test
  void shouldNeverAnonymizeLoans() {
    final var config = anonymizeLoans(NEVER);

    final var loanAnonymization = new LoanAnonymization(loanRepository,
      accountRepository, anonymizeStorageLoansRepository, eventPublisher);

    final var service = loanAnonymization.byCurrentTenant(config);

    final var finished = service.anonymizeLoans();

    finished.get(1, SECONDS);

    verifyNoMoreInteractions(loanRepository);
    verifyNoMoreInteractions(accountRepository);
  }

  private void singleLoanToAnonymize() {
    final var loans = new ArrayList<Loan>();

    loans.add(fakeLoan());

    when(loanRepository.findLoansToAnonymize(any()))
      .thenReturn(completedFuture(Result.of(() -> new MultipleRecords<>(loans, 1))));
  }

  private Loan fakeLoan() {
    return Loan.from(new JsonObject().put("id", UUID.randomUUID().toString()));
  }

  private LoanAnonymizationConfiguration anonymizeLoans(ClosingType loansClosingType) {
    final var json = new JsonObject();

    final var closingType = new JsonObject();
    write(closingType, "loan", loansClosingType.getRepresentation());
    write(json, "closingType", closingType);

    //Whether to treat loans with fees/fines differently
    write(json, "treatEnabled", false);

    return LoanAnonymizationConfiguration.from(json);
  }
}
