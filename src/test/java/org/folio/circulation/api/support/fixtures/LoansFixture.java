package org.folio.circulation.api.support.fixtures;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.support.builders.LoanBuilder;
import org.folio.circulation.api.support.http.ResourceClient;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class LoansFixture {

  private final ResourceClient loansClient;

  public LoansFixture(ResourceClient loansClient) {
    this.loansClient = loansClient;
  }

  public IndividualResource checkOut(IndividualResource item)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    return checkOutItem(item.getId());
  }

  public IndividualResource checkOutItem(UUID itemId)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    return this.loansClient.create(new LoanBuilder()
      .open()
      .withItemId(itemId));
  }

  public void renewLoan(UUID loanId)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    Response getResponse = loansClient.getById(loanId);

    //TODO: Should also change the due date
    JsonObject renewedLoan = getResponse.getJson().copy()
      .put("action", "renewed")
      .put("renewalCount", 1);

    loansClient.replace(loanId, renewedLoan);
  }

  public void checkIn(IndividualResource loan)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    checkInLoan(loan.getId());
  }

  public void checkInLoan(UUID loanId)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    Response getResponse = loansClient.getById(loanId);

    //TODO: Should also have a return date
    JsonObject closedLoan = getResponse.getJson().copy()
      .put("status", new JsonObject().put("name", "Closed"))
      .put("action", "checkedin");

    loansClient.replace(loanId, closedLoan);
  }
}
