package org.folio.circulation.domain.anonymization;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.circulation.domain.anonymization.config.ClosingType.IMMEDIATELY;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.anonymization.config.ClosingType;
import org.folio.circulation.domain.anonymization.config.LoanAnonymizationConfiguration;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
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

  @BeforeEach
  public void init() {
    initMocks(this);
  }

  @SneakyThrows
  @Test
  void shouldAnonymizeLoansImmediatelyWhenConfiguredToDoSo() {
    final var config = anonymizeLoans(IMMEDIATELY);

    final var service = new LoanAnonymization(clients, loanRepository)
      .byCurrentTenant(config);

    when(loanRepository.findLoansToAnonymize(any()))
      .thenReturn(completedFuture(Result.of(MultipleRecords::empty)));

    final var finished = service.anonymizeLoans();

    finished.get(1, SECONDS);
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
