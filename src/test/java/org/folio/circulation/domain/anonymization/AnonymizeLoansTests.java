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
    private final AnonymizationCheckersService checker = checker();

    @Test
    public void anonymizeClosedLoanWithNoFees() {
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
      assertThat(segregatedLoans.get("anonymizeImmediately").size(), is(1));
    }

    @Test
    public void doNotAnonymizeClosedLoanWithOpenFees() {
      final var segregatedLoans = checker.segregateLoans(List.of(closedLoanWithOpenFee()));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(segregatedLoans.get("anonymizeImmediately").size(), is(1));
    }

    @Test
    public void doNotAnonymizeOpenLoanWithOpenFees() {
      final var segregatedLoans = checker.segregateLoans(List.of(openLoanWithOpenFee()));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(segregatedLoans.get("anonymizeImmediately").size(), is(1));
    }

    private AnonymizationCheckersService checker() {
      // Fee fines closing type is deliberately different to make sure that it is ignored when
      // loans with fees should not be treated differently
      return new AnonymizationCheckersService(
        new LoanAnonymizationConfiguration(ClosingType.IMMEDIATELY, ClosingType.NEVER,
          false, null, null));
    }
  }

  @Nested
  class WhenAnonymizingLoansWithFeesImmediately {
    private final AnonymizationCheckersService checker = checker();

    @Test
    public void anonymizeClosedLoanWithClosedFees() {
      final var segregatedLoans = checker.segregateLoans(List.of(closedLoanWithClosedFee()));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(segregatedLoans.get("_").size(), is(1));
    }

    @Test
    public void doNotAnonymizeClosedLoanWithOpenFees() {
      final var segregatedLoans = checker.segregateLoans(List.of(closedLoanWithOpenFee()));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(segregatedLoans.get("feesAndFinesOpen").size(), is(1));
    }

    @Test
    public void doNotAnonymizeOpenLoanWithOpenFees() {
      final var segregatedLoans = checker.segregateLoans(List.of(openLoanWithOpenFee()));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(segregatedLoans.get("feesAndFinesOpen").size(), is(1));
    }

    private AnonymizationCheckersService checker() {
      // General closing type is deliberately different to make sure that the
      // loans with fees closing type is definitely used
      return new AnonymizationCheckersService(
        new LoanAnonymizationConfiguration(ClosingType.NEVER, ClosingType.IMMEDIATELY,
          true, null, null));
    }
  }

  @Nested
  class WhenNeverAnonymizingLoans {
    private final AnonymizationCheckersService checker = checker();

    @Test
    public void doNotAnonymizeLoanClosedWithNoFees() {
      final var segregatedLoans = checker.segregateLoans(List.of(closedLoan()));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(segregatedLoans.get("neverAnonymizeLoans").size(), is(1));
    }

    @Test
    public void doNotAnonymizeOpenLoanWithNoFees() {
      final var segregatedLoans = checker.segregateLoans(List.of(openLoan()));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(segregatedLoans.get("neverAnonymizeLoans").size(), is(1));
    }

    @Test
    public void doNotAnonymizeClosedLoanWithClosedFees() {
      final var segregatedLoans = checker.segregateLoans(
        List.of(closedLoanWithClosedFee()));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(segregatedLoans.get("neverAnonymizeLoansWithFeesAndFines").size(),
        is(1));
    }

    @Test
    public void doNotAnonymizeClosedLoanWithOpenFees() {
      final var segregatedLoans = checker.segregateLoans(
        List.of(closedLoanWithOpenFee()));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(segregatedLoans.get("neverAnonymizeLoansWithFeesAndFines").size(),
        is(1));
    }

    @Test
    public void doNotAnonymizeOpenLoanWithOpenFees() {
      final var segregatedLoans = checker.segregateLoans(
        List.of(openLoanWithOpenFee()));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(segregatedLoans.get("neverAnonymizeLoansWithFeesAndFines").size(),
        is(1));
    }

    private AnonymizationCheckersService checker() {
      // Fee fines closing type is deliberately different to make sure that it is ignored when
      // loans with fees should not be treated differently
      return new AnonymizationCheckersService(
        new LoanAnonymizationConfiguration(ClosingType.NEVER, ClosingType.NEVER,
          true, null, null));
    }
  }

  @Nested
  class WhenManuallyAnonymizingLoans {
    private final AnonymizationCheckersService checker = checker();

    @Test
    public void anonymizeLoanClosedWithNoFees() {
      final var segregatedLoans = checker.segregateLoans(List.of(closedLoan()));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(segregatedLoans.get("_").size(), is(1));
    }

    @Test
    public void doNotAnonymizeOpenLoanWithNoFees() {
      final var segregatedLoans = checker.segregateLoans(List.of(openLoan()));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(segregatedLoans.get("_").size(), is(1));
    }

    @Test
    public void doNotAnonymizeClosedLoanWithClosedFees() {
      final var segregatedLoans = checker.segregateLoans(
        List.of(closedLoanWithClosedFee()));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(segregatedLoans.get("haveAssociatedFeesAndFines").size(),
        is(1));
    }

    @Test
    public void doNotAnonymizeClosedLoanWithOpenFees() {
      final var segregatedLoans = checker.segregateLoans(
        List.of(closedLoanWithOpenFee()));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(segregatedLoans.get("haveAssociatedFeesAndFines").size(),
        is(1));
    }

    @Test
    public void doNotAnonymizeOpenLoanWithOpenFees() {
      final var segregatedLoans = checker.segregateLoans(
        List.of(openLoanWithOpenFee()));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(segregatedLoans.get("haveAssociatedFeesAndFines").size(),
        is(1));
    }

    private AnonymizationCheckersService checker() {
      // Manual anonymization is triggered by providing no config
      return new AnonymizationCheckersService(null);
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
