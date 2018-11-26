package org.folio.circulation.domain.validation;

import java.util.function.Supplier;

import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.ServerErrorFailure;

public class CommonFailures {
  private CommonFailures() { }

  public static Supplier<HttpFailure> moreThanOneOpenLoanFailure(String itemBarcode) {
    return () -> new ServerErrorFailure(
      String.format("More than one open loan for item %s", itemBarcode));
  }
}
