package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.utils.LogUtil.resultAsString;

import java.lang.invoke.MethodHandles;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.results.Result;

public class MoreThanOneLoanValidator {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final Supplier<HttpFailure> failureSupplier;

  public MoreThanOneLoanValidator(Supplier<HttpFailure> failureSupplier) {
    this.failureSupplier = failureSupplier;
  }

  public Result<MultipleRecords<Loan>> failWhenMoreThanOneLoan(
    Result<MultipleRecords<Loan>> result) {

    log.debug("failWhenMoreThanOneLoan:: parameters result={}", () -> resultAsString(result));

    return result.failWhen(moreThanOneLoan(),
      loans -> failureSupplier.get());
  }

  private static Function<MultipleRecords<Loan>, Result<Boolean>> moreThanOneLoan() {
    return loans -> of(() -> loans.getTotalRecords() > 1);
  }
}
