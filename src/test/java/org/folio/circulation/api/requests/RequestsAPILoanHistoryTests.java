package org.folio.circulation.api.requests;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.APITestSuite;
import org.folio.circulation.api.support.RequestRequestBuilder;
import org.folio.circulation.api.support.ResourceClient;
import org.folio.circulation.api.support.UserRequestBuilder;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.folio.circulation.api.support.ItemRequestExamples.basedUponNod;
import static org.folio.circulation.api.support.ItemRequestExamples.basedUponSmallAngryPlanet;
import static org.folio.circulation.api.support.LoanPreparation.checkInLoan;
import static org.folio.circulation.api.support.LoanPreparation.checkOutItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;


public class RequestsAPILoanHistoryTests {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final OkapiHttpClient client = APITestSuite.createClient(exception -> {
    log.error("Request to circulation module failed:", exception);
  });

  private final ResourceClient usersClient = ResourceClient.forUsers(client);
  private final ResourceClient requestsClient = ResourceClient.forRequests(client);
  private final ResourceClient itemsClient = ResourceClient.forItems(client);
  private final ResourceClient loansClient = ResourceClient.forLoans(client);
  private final ResourceClient loansStorageClient = ResourceClient.forLoansStorage(client);

  @Before
  public void beforeEach()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    requestsClient.deleteAll();
    usersClient.deleteAllIndividually("users");
    itemsClient.deleteAll();
    loansClient.deleteAll();
  }

  @Test
  public void creatingHoldRequestChangesTheOpenLoanForTheSameItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsClient.create(basedUponSmallAngryPlanet()
      .withBarcode("036000291452"))
      .getId();

    UUID loanId = checkOutItem(itemId, loansClient).getId();

    requestsClient.create(new RequestRequestBuilder()
      .hold()
      .withId(id)
      .withItemId(itemId)
      .withRequesterId(usersClient.create(new UserRequestBuilder()).getId()));

    JsonObject loanFromStorage = loansStorageClient.getById(loanId).getJson();

    assertThat("action snapshot in storage is not hold requested",
      loanFromStorage.getString("action"), is("holdrequested"));
  }

  @Test
  public void creatingRecallRequestChangesTheOpenLoanForTheSameItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsClient.create(basedUponSmallAngryPlanet()
      .withBarcode("6540962174061"))
      .getId();

    UUID loanId = checkOutItem(itemId, loansClient).getId();

    requestsClient.create(new RequestRequestBuilder()
      .recall()
      .withId(id)
      .withItemId(itemId)
      .withRequesterId(usersClient.create(new UserRequestBuilder()).getId()));

    JsonObject loanFromStorage = loansStorageClient.getById(loanId).getJson();

    assertThat("action snapshot in storage is not recall requested",
      loanFromStorage.getString("action"), is("recallrequested"));
  }

  @Test
  public void creatingPageRequestDoesNotChangeTheOpenLoanForSameItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsClient.create(basedUponSmallAngryPlanet()
      .withBarcode("6540962174061"))
      .getId();

    UUID loanId = checkOutItem(itemId, loansClient).getId();

    requestsClient.create(new RequestRequestBuilder()
      .page()
      .withId(id)
      .withItemId(itemId)
      .withRequesterId(usersClient.create(new UserRequestBuilder()).getId()));

    JsonObject loanFromStorage = loansStorageClient.getById(loanId).getJson();

    assertThat("action snapshot in storage is not still checked out",
      loanFromStorage.getString("action"), is("checkedout"));
  }

  @Test
  public void creatingHoldRequestDoesNotChangeClosedLoanForTheSameItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsClient.create(basedUponSmallAngryPlanet()
      .withBarcode("036000291452"))
      .getId();

    UUID closedLoanId = checkOutItem(itemId, loansClient).getId();

    checkInLoan(closedLoanId, loansClient);

    checkOutItem(itemId, loansClient).getId();

    requestsClient.create(new RequestRequestBuilder()
      .hold()
      .withId(id)
      .withItemId(itemId)
      .withRequesterId(usersClient.create(new UserRequestBuilder()).getId()));

    JsonObject closedLoanFromStorage = loansStorageClient.getById(closedLoanId)
      .getJson();

    assertThat("action snapshot for closed loan should not change",
      closedLoanFromStorage.getString("action"), is("checkedin"));
  }

  @Test
  public void creatingRecallRequestDoesNotChangeClosedLoanForTheSameItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsClient.create(basedUponSmallAngryPlanet()
      .withBarcode("036000291452"))
      .getId();

    UUID closedLoanId = checkOutItem(itemId, loansClient).getId();

    checkInLoan(closedLoanId, loansClient);

    checkOutItem(itemId, loansClient).getId();

    requestsClient.create(new RequestRequestBuilder()
      .recall()
      .withId(id)
      .withItemId(itemId)
      .withRequesterId(usersClient.create(new UserRequestBuilder()).getId()));

    JsonObject closedLoanFromStorage = loansStorageClient.getById(closedLoanId)
      .getJson();

    assertThat("action snapshot for closed loan should not change",
      closedLoanFromStorage.getString("action"), is("checkedin"));
  }

  @Test
  public void creatingHoldRequestDoesNotChangeOpenLoanForDifferentItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsClient.create(basedUponSmallAngryPlanet()
      .withBarcode("036000291452"))
      .getId();

    UUID otherItemId = itemsClient.create(basedUponNod()
      .withBarcode("750453962104"))
      .getId();

    checkOutItem(itemId, loansClient).getId();
    UUID loanForOtherItemId = checkOutItem(otherItemId, loansClient).getId();

    requestsClient.create(new RequestRequestBuilder()
      .hold()
      .withId(id)
      .withItemId(itemId)
      .withRequesterId(usersClient.create(new UserRequestBuilder()).getId()));

    JsonObject storageLoanForOtherItem = loansStorageClient.getById(loanForOtherItemId)
      .getJson();

    assertThat("action snapshot for open loan for other item should not change",
      storageLoanForOtherItem.getString("action"), is("checkedout"));
  }

  @Test
  public void creatingRecallRequestDoesNotChangeOpenLoanForDifferentItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsClient.create(basedUponSmallAngryPlanet()
      .withBarcode("036000291452"))
      .getId();

    UUID otherItemId = itemsClient.create(basedUponNod()
      .withBarcode("750453962104"))
      .getId();

    checkOutItem(itemId, loansClient).getId();
    UUID loanForOtherItemId = checkOutItem(otherItemId, loansClient).getId();

    requestsClient.create(new RequestRequestBuilder()
      .recall()
      .withId(id)
      .withItemId(itemId)
      .withRequesterId(usersClient.create(new UserRequestBuilder()).getId()));

    JsonObject storageLoanForOtherItem = loansStorageClient.getById(loanForOtherItemId)
      .getJson();

    assertThat("action snapshot for open loan for other item should not change",
      storageLoanForOtherItem.getString("action"), is("checkedout"));
  }
}
