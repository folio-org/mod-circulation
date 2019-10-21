package api.loans.anonymization;

import static api.support.http.InterfaceUrls.circulationAnonymizeLoansInTenantURL;
import static api.support.http.InterfaceUrls.circulationAnonymizeLoansURL;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.Before;

import api.support.APITests;
import api.support.builders.AccountBuilder;
import api.support.builders.FeefineActionsBuilder;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;

class LoanAnonymizationTests extends APITests {

  private static final int TIMEOUT_SECONDS = 20;
  protected static final int ONE_MINUTE_AND_ONE = 60001;
  protected InventoryItemResource item1;
  protected IndividualResource user;
  protected IndividualResource servicePoint;

  @Before
  public void setup() throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {
    item1 = itemsFixture.basedUponSmallAngryPlanet();
    user = usersFixture.charlotte();
    servicePoint = servicePointsFixture.cd1();
  }

  void anonymizeLoansInTenant() throws InterruptedException, ExecutionException, TimeoutException {
    anonymizeLoans(circulationAnonymizeLoansInTenantURL());
    DateTimeUtils.setCurrentMillisSystem();
  }

  void anonymizeLoansForUser(UUID userId) throws InterruptedException, ExecutionException, TimeoutException {
    anonymizeLoans(circulationAnonymizeLoansURL(userId.toString()));
  }

  private void anonymizeLoans(URL url) throws InterruptedException, ExecutionException, TimeoutException {
    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    client.post(url, null, ResponseHandler.any(createCompleted));
    Response response = createCompleted.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    response.getJson()
      .getJsonArray("anonymizedLoans")
      .forEach(this::fakeAnonymizeLoan);
  }

  private void fakeAnonymizeLoan(Object id) {
    try {
      JsonObject payload = loansStorageClient.get(UUID.fromString(id.toString()))
        .getJson();
      payload.remove("userId");
      payload.remove("borrower");
      loansStorageClient.attemptReplace(UUID.fromString(id.toString()), payload);

    } catch (MalformedURLException | InterruptedException | ExecutionException | TimeoutException e) {
      e.printStackTrace();
    }
  }

  void createOpenAccountWithFeeFines(IndividualResource loanResource)
      throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {
    IndividualResource account = accountsClient.create(new AccountBuilder().feeFineStatusOpen()
      .withLoan(loanResource)
      .feeFineStatusOpen()
      .withRemainingFeeFine(150));

    FeefineActionsBuilder builder = new FeefineActionsBuilder().forAccount(account.getId())
      .withBalance(150)
      .withDate(null);
    feefineActionsClient.create(builder);
  }

  void createClosedAccountWithFeeFines(IndividualResource loanResource, DateTime closedDate)
      throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    IndividualResource account = accountsClient.create(new AccountBuilder().feeFineStatusOpen()
      .withLoan(loanResource)
      .feeFineStatusClosed()
      .withRemainingFeeFine(0));

    FeefineActionsBuilder builder = new FeefineActionsBuilder().forAccount(account.getId())
      .withBalance(0)
      .withDate(closedDate);

    FeefineActionsBuilder builder1 = new FeefineActionsBuilder().forAccount(account.getId())
        .withBalance(0)
        .withDate(closedDate.minusDays(1));

    feefineActionsClient.create(builder);
    feefineActionsClient.create(builder1);
  }
}
