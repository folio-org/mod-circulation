package org.folio.circulation.domain.anonymization;

import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.json.JsonPropertyWriter.writeByPath;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.List;
import java.util.UUID;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.anonymization.config.ClosingType;
import org.folio.circulation.domain.anonymization.config.LoanAnonymizationConfiguration;
import org.folio.circulation.domain.anonymization.service.AnonymizationCheckersService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class AnonymizeLoansTests {
  @Nested
  class WhenAnonymizingAllLoansImmediately {
    private final AnonymizationCheckersService checker = anonymizeLoansImmediatelyChecker();

    @Test
    public void anonymizeLoanWithoutFeesWhenClosed() {
      final var segregatedLoans = checker.segregateLoans(List.of(closedLoan()));

      assertThat(segregatedLoans.size(), is(1));
      // Partition for loans that should be annonymized
      assertThat(segregatedLoans.get("_").size(), is(1));
    }

    @Test
    public void anonymizeClosedLoanWithClosedFees() {
      final var segregatedLoans = checker.segregateLoans(List.of(closedLoanWithClosedFee()));

      assertThat(segregatedLoans.size(), is(1));
      // Partition for loans that should be annonymized
      assertThat(segregatedLoans.get("_").size(), is(1));
    }

    @Test
    public void doNotAnonymizeOpenLoanWithNoFees() {
      final var segregatedLoans = checker.segregateLoans(List.of(openLoan()));

      assertThat(segregatedLoans.size(), is(1));
      // Partition for loans that should not be annonymized immediately
      assertThat(segregatedLoans.get("anonymizeImmediately").size(), is(1));
    }

    @Test
    public void doNotAnonymizeClosedLoanWithOpenFees() {
      final var segregatedLoans = checker.segregateLoans(List.of(closedLoanWithOpenFee()));

      assertThat(segregatedLoans.size(), is(1));
      // Partition for loans that should be annonymized
      assertThat(segregatedLoans.get("anonymizeImmediately").size(), is(1));
    }

    @Test
    public void doNotAnonymizeOpenLoanWithOpenFees() {
      final var segregatedLoans = checker.segregateLoans(List.of(openLoanWithOpenFee()));

      assertThat(segregatedLoans.size(), is(1));
      // Partition for loans that should not be annonymized immediately
      assertThat(segregatedLoans.get("anonymizeImmediately").size(), is(1));
    }

    private AnonymizationCheckersService anonymizeLoansImmediatelyChecker() {
      // Fee fines closing type is deliberately different to make sure that it is ignored when
      // loans with fees should not be treated differently
      return new AnonymizationCheckersService(
        new LoanAnonymizationConfiguration(null, ClosingType.IMMEDIATELY,
          ClosingType.NEVER, false, null, null));
    }
  }

  private Loan openLoan() {
    return loan("Open");
  }

  private Loan openLoanWithOpenFee() {
    return loan("Open")
      .withAccounts(List.of(openFee()));
  }

  private Loan closedLoan() {
    return loan("Closed");
  }

  private Loan closedLoanWithClosedFee() {
    return loan("Closed")
      .withAccounts(List.of(closedFee()));
  }

  private Loan closedLoanWithOpenFee() {
    return loan("Open")
      .withAccounts(List.of(openFee()));
  }

  private Loan loan(String status) {
    final var json = new JsonObject();

    write(json, "id", UUID.randomUUID());
    writeByPath(json, status, "status", "name");

    return Loan.from(json);
  }

  private Account openFee() {
    return fee("Open");
  }

  private Account closedFee() {
    return fee("Closed");
  }

  private Account fee(String status) {
    return new Account(null, null, null, null, status, null, null, null);
  }
}
