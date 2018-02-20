package org.folio.circulation.api.requests;

import org.folio.circulation.api.support.APITests;
import org.folio.circulation.api.support.builders.RequestBuilder;
import org.folio.circulation.api.support.builders.UserBuilder;
import org.folio.circulation.support.http.client.Response;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.folio.circulation.api.support.fixtures.LoansFixture.checkInLoan;
import static org.folio.circulation.api.support.fixtures.LoansFixture.checkOutItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class RequestsAPILoanCheckInTests extends APITests {
  @Test
  public void ItemIsAvailableAfterCheckingInLoanEvenWithOutstandingHoldRequest()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    UUID loanId = loansFixture.checkOutItem(itemId).getId();

    requestsClient.create(new RequestBuilder()
      .hold()
      .withId(id)
      .withItemId(itemId)
      .withRequesterId(usersClient.create(new UserBuilder()).getId()));

    checkInLoan(loanId, loansClient);

    Response changedItem = itemsClient.getById(itemId);

    assertThat(changedItem.getJson().getJsonObject("status").getString("name"),
      is("Available"));
  }

  @Test
  public void ItemIsAvailableAfterCheckingInLoanEvenWithOutstandingRecallRequest()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    UUID loanId = loansFixture.checkOutItem(itemId).getId();

    requestsClient.create(new RequestBuilder()
      .recall()
      .withId(id)
      .withItemId(itemId)
      .withRequesterId(usersClient.create(new UserBuilder()).getId()));

    checkInLoan(loanId, loansClient);

    Response changedItem = itemsClient.getById(itemId);

    assertThat(changedItem.getJson().getJsonObject("status").getString("name"),
      is("Available"));
  }

  @Test
  public void ItemIsAvailableAfterCheckingInLoanEvenWithOutstandingPageRequest()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    UUID loanId = loansFixture.checkOutItem(itemId).getId();

    requestsClient.create(new RequestBuilder()
      .page()
      .withId(id)
      .withItemId(itemId)
      .withRequesterId(usersClient.create(new UserBuilder()).getId()));

    checkInLoan(loanId, loansClient);

    Response changedItem = itemsClient.getById(itemId);

    assertThat(changedItem.getJson().getJsonObject("status").getString("name"),
      is("Available"));
  }
}
