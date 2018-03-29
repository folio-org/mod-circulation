package api.requests;

import io.vertx.core.json.JsonObject;
import api.support.APITests;
import api.support.builders.RequestBuilder;
import api.support.builders.UserBuilder;
import api.support.http.ResourceClient;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;


public class RequestsAPILoanHistoryTests extends APITests {
  private final ResourceClient loansStorageClient = ResourceClient.forLoansStorage(client);

  @Test
  public void creatingHoldRequestChangesTheOpenLoanForTheSameItem()
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

    JsonObject loanFromStorage = loansStorageClient.getById(loanId).getJson();

    assertThat("action snapshot in storage is not hold requested",
      loanFromStorage.getString("action"), is("holdrequested"));

    assertThat("item status snapshot in storage is not checked out - held",
      loanFromStorage.getString("itemStatus"), is("Checked out - Held"));
  }

  @Test
  public void creatingRecallRequestChangesTheOpenLoanForTheSameItem()
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

    JsonObject loanFromStorage = loansStorageClient.getById(loanId).getJson();

    assertThat("item status snapshot in storage is not checked out - recalled",
      loanFromStorage.getString("itemStatus"), is("Checked out - Recalled"));
  }

  @Test
  public void creatingPageRequestDoesNotChangeTheOpenLoanForSameItem()
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

    JsonObject loanFromStorage = loansStorageClient.getById(loanId).getJson();

    assertThat("action snapshot in storage is not still checked out",
      loanFromStorage.getString("action"), is("checkedout"));

    assertThat("item status snapshot in storage is not still checked out",
      loanFromStorage.getString("itemStatus"), is("Checked out"));
  }

  @Test
  public void creatingHoldRequestDoesNotChangeClosedLoanForTheSameItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    UUID closedLoanId = loansFixture.checkOutItem(itemId).getId();

    loansFixture.checkInLoan(closedLoanId);

    loansFixture.checkOutItem(itemId).getId();

    requestsClient.create(new RequestBuilder()
      .hold()
      .withId(id)
      .withItemId(itemId)
      .withRequesterId(usersClient.create(new UserBuilder()).getId()));

    JsonObject closedLoanFromStorage = loansStorageClient.getById(closedLoanId)
      .getJson();

    assertThat("action snapshot for closed loan should not change",
      closedLoanFromStorage.getString("action"), is("checkedin"));

    assertThat("item status snapshot for closed loan should not change",
      closedLoanFromStorage.getString("itemStatus"), is("Available"));
  }

  @Test
  public void creatingRecallRequestDoesNotChangeClosedLoanForTheSameItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    UUID closedLoanId = loansFixture.checkOutItem(itemId).getId();

    loansFixture.checkInLoan(closedLoanId);

    loansFixture.checkOutItem(itemId).getId();

    requestsClient.create(new RequestBuilder()
      .recall()
      .withId(id)
      .withItemId(itemId)
      .withRequesterId(usersClient.create(new UserBuilder()).getId()));

    JsonObject closedLoanFromStorage = loansStorageClient.getById(closedLoanId)
      .getJson();

    assertThat("action snapshot for closed loan should not change",
      closedLoanFromStorage.getString("action"), is("checkedin"));

    assertThat("item status snapshot for closed loan should not change",
      closedLoanFromStorage.getString("itemStatus"), is("Available"));
  }

  @Test
  public void creatingHoldRequestDoesNotChangeOpenLoanForDifferentItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    UUID otherItemId = itemsFixture.basedUponNod().getId();

    loansFixture.checkOutItem(itemId).getId();
    UUID loanForOtherItemId = loansFixture.checkOutItem(otherItemId).getId();

    requestsClient.create(new RequestBuilder()
      .hold()
      .withId(id)
      .withItemId(itemId)
      .withRequesterId(usersClient.create(new UserBuilder()).getId()));

    JsonObject storageLoanForOtherItem = loansStorageClient.getById(loanForOtherItemId)
      .getJson();

    assertThat("action snapshot for open loan for other item should not change",
      storageLoanForOtherItem.getString("action"), is("checkedout"));

    assertThat("item status snapshot for open loan for other item should not change",
      storageLoanForOtherItem.getString("itemStatus"), is("Checked out"));
  }

  @Test
  public void creatingRecallRequestDoesNotChangeOpenLoanForDifferentItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    UUID otherItemId = itemsFixture.basedUponNod().getId();

    loansFixture.checkOutItem(itemId).getId();
    UUID loanForOtherItemId = loansFixture.checkOutItem(otherItemId).getId();

    requestsClient.create(new RequestBuilder()
      .recall()
      .withId(id)
      .withItemId(itemId)
      .withRequesterId(usersClient.create(new UserBuilder()).getId()));

    JsonObject storageLoanForOtherItem = loansStorageClient.getById(loanForOtherItemId)
      .getJson();

    assertThat("action snapshot for open loan for other item should not change",
      storageLoanForOtherItem.getString("action"), is("checkedout"));

    assertThat("item status snapshot for open loan for other item should not change",
      storageLoanForOtherItem.getString("itemStatus"), is("Checked out"));
  }

  @Test
  public void creatingHoldRequestStillSucceedsWhenThereIsNoLoan()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    UUID loanId = loansFixture.checkOutItem(itemId).getId();

    loansClient.delete(loanId);

    requestsClient.create(new RequestBuilder()
      .hold()
      .withId(id)
      .withItemId(itemId)
      .withRequesterId(usersClient.create(new UserBuilder()).getId()));
  }

  @Test
  public void creatingRecallRequestStillSucceedsWhenThereIsNoLoan()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    UUID loanId = loansFixture.checkOutItem(itemId).getId();

    loansClient.delete(loanId);

    requestsClient.create(new RequestBuilder()
      .recall()
      .withId(id)
      .withItemId(itemId)
      .withRequesterId(usersClient.create(new UserBuilder()).getId()));
  }
}
