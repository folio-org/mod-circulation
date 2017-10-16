package org.folio.circulation.api.support;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class LoanPreparation {
  public static void checkOutItem(UUID itemId, ResourceClient loansClient)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    loansClient.create(new LoanRequestBuilder()
      .open()
      .withItemId(itemId));
  }
}
