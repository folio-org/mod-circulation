package org.folio.circulation.domain.anonymization;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.circulation.domain.anonymization.config.ClosingType.IMMEDIATELY;
import static org.folio.circulation.domain.anonymization.config.ClosingType.NEVER;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.json.JsonPropertyWriter.writeByPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.anonymization.config.ClosingType;
import org.folio.circulation.domain.anonymization.config.LoanAnonymizationConfiguration;
import org.folio.circulation.domain.anonymization.service.AnonymizationCheckersService;
import org.folio.circulation.domain.anonymization.service.LoansForTenantFinder;
import org.folio.circulation.infrastructure.storage.loans.AnonymizeStorageLoansRepository;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;

class LoanAnonymizationServiceTests {
  @Mock
  AnonymizeStorageLoansRepository anonymizeStorageLoansRepository;
  @Mock
  EventPublisher eventPublisher;
  @Mock
  LoansForTenantFinder loansForTenantFinder;

  @BeforeEach
  public void init() {
    initMocks(this);
  }

  @SneakyThrows
  @Test
  void shouldAnonymizeLoansImmediatelyWhenConfiguredToDoSo() {
    final var config = anonymizeLoans(IMMEDIATELY);

    final var service = createService(config);

    final var loanToAnonymize = singleClosedLoanWithNoFeesFines();

    final var finished
      = service.anonymizeLoans(loansForTenantFinder::findLoansToAnonymize);

    finished.get(1, SECONDS);

    final var anonymizedLoansCaptor
      = ArgumentCaptor.forClass(LoanAnonymizationRecords.class);

    verify(anonymizeStorageLoansRepository, times(1))
      .postAnonymizeStorageLoans(anonymizedLoansCaptor.capture());

    assertThat(anonymizedLoansCaptor.getValue().getAnonymizedLoanIds(),
      containsInAnyOrder(loanToAnonymize.getId()));

    verify(loansForTenantFinder, times(1)).findLoansToAnonymize();

    verifyNoMoreInteractions(loansForTenantFinder);
    verifyNoMoreInteractions(anonymizeStorageLoansRepository);
  }

  @SneakyThrows
  @Test
  void shouldNeverAnonymizeLoans() {
    final var config = anonymizeLoans(NEVER);

    final var service = createService(config);

    singleClosedLoanWithNoFeesFines();

    final var finished
      = service.anonymizeLoans(loansForTenantFinder::findLoansToAnonymize);

    finished.get(1, SECONDS);

    verify(loansForTenantFinder, times(0)).findLoansToAnonymize();

    verify(anonymizeStorageLoansRepository, times(0))
      .postAnonymizeStorageLoans(any());

    verifyNoMoreInteractions(loansForTenantFinder);
    verifyNoMoreInteractions(anonymizeStorageLoansRepository);
  }

  private Loan singleClosedLoanWithNoFeesFines() {
    final var loan = fakeLoan();

    final CompletableFuture<Result<Collection<Loan>>> loans = completedFuture(
      Result.of(() -> List.of(loan)));

    when(loansForTenantFinder.findLoansToAnonymize()).thenReturn(loans);

    return loan;
  }

  private Loan fakeLoan() {
    final var json = new JsonObject();

    write(json, "id", UUID.randomUUID());
    writeByPath(json, "Closed", "status", "name");

    return Loan.from(json);
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

  private LoanAnonymizationService createService(LoanAnonymizationConfiguration config) {
    final var anonymizationCheckersService = new AnonymizationCheckersService(config,
      ClockUtil::getDateTime);

    return new DefaultLoanAnonymizationService(anonymizationCheckersService,
      anonymizeStorageLoansRepository, eventPublisher);
  }
}
