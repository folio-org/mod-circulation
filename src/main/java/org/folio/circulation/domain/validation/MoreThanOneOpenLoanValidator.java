package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.HttpResult.of;

import java.util.Optional;
import java.util.function.Function;

import org.folio.circulation.domain.FindByBarcodeQuery;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;

public class MoreThanOneOpenLoanValidator {
  public HttpResult<MultipleRecords<Loan>> failWhenMoreThanOneOpenLoan(
    HttpResult<MultipleRecords<Loan>> result,
    FindByBarcodeQuery query) {

    return result.failWhen(moreThanOneOpenLoan(),
      loans -> new ServerErrorFailure(
        String.format("More than one open loan for item %s", query.getItemBarcode())));
  }

  private static Function<MultipleRecords<Loan>, HttpResult<Boolean>> moreThanOneOpenLoan() {
    return loans -> {
      final Optional<Loan> first = loans.getRecords().stream()
        .findFirst();

      return of(() -> loans.getTotalRecords() != 1 || !first.isPresent());
    };
  }
}
