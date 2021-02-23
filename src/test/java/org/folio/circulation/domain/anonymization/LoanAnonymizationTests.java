package org.folio.circulation.domain.anonymization;

import static org.folio.circulation.domain.anonymization.config.ClosingType.IMMEDIATELY;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;

import org.folio.circulation.domain.anonymization.config.ClosingType;
import org.folio.circulation.domain.anonymization.config.LoanAnonymizationConfiguration;
import org.folio.circulation.support.Clients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.vertx.core.json.JsonObject;

public class LoanAnonymizationTests {
  @Mock
  Clients clients;

  @BeforeEach
  public void init() {
    initMocks(this);
  }

  @Test
  void shouldAnonymizeLoansImmediatelyWhenConfiguredToDoSo() {
    final var config = anonymizeLoans(IMMEDIATELY);

    final var service = new LoanAnonymization(clients)
      .byCurrentTenant(config);

    assertThat(service, instanceOf(DefaultLoanAnonymizationService.class));
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
