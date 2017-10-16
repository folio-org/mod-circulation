package org.folio.circulation.api.support;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class LoanPreparation {
  public static IndividualResource checkOutItem(
    UUID itemId,
    ResourceClient loansClient)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    return loansClient.create(new LoanRequestBuilder()
      .open()
      .withItemId(itemId));
  }

  public static void checkInLoan(
    UUID loanId,
    ResourceClient loansClient)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    Response getResponse = loansClient.getById(loanId);

    //TODO: Should also have a return date
    JsonObject updatedLoan = getResponse.getJson().copy()
      .put("status", new JsonObject().put("name", "Closed"))
      .put("action", "checkedin");

    loansClient.replace(loanId, updatedLoan);
  }
}
