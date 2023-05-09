package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.utils.LogUtil.resultAsString;

import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.results.Result;

public class NoLoanValidator {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final Supplier<HttpFailure> failureSupplier;

  public NoLoanValidator(Supplier<HttpFailure> failureSupplier) {
    this.failureSupplier = failureSupplier;
  }

  public Result<Optional<Loan>> failWhenNoLoan(Result<Optional<Loan>> result) {
    log.debug("failWhenNoLoan:: parameters result: {}", () -> resultAsString(result));

    return result.failWhen(loan -> of(loan::isEmpty),
      loans -> failureSupplier.get());
  }
}
