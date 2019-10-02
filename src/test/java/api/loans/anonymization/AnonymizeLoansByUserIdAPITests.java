package api.loans.anonymization;

import static api.support.http.InterfaceUrls.circulationAnonymizeLoansURL;
import static api.support.matchers.LoanMatchers.hasOpenStatus;
import static api.support.matchers.LoanMatchers.isAnonymized;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.junit.Before;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.AccountBuilder;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;

public class AnonymizeLoansByUserIdAPITests extends APITests {

  private static final int TIMEOUT_SECONDS = 10;
  private InventoryItemResource item1;
  private IndividualResource user;
  private IndividualResource servicePoint;

  @Before
  public void setup() throws InterruptedException, MalformedURLException,
      TimeoutException, ExecutionException {
    item1 = itemsFixture.basedUponSmallAngryPlanet();
    user = usersFixture.charlotte();
    servicePoint = servicePointsFixture.cd1();
  }

  @Test
  public void canNotAnonymizeNotClosedLoans()
      throws InterruptedException, ExecutionException, TimeoutException,
      MalformedURLException {

    IndividualResource loanResource = loansFixture.checkOutByBarcode(
        new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    assertThat(loanResource.getJson(), hasOpenStatus());

    anonymizeLoansForUser(user.getId());

    JsonObject storageLoan = loansStorageClient.getById(loanID)
      .getJson();
    assertThat(storageLoan, not(isAnonymized()));
  }

  @Test
  public void canAonymizeLoansForParticularUser()
      throws InterruptedException, ExecutionException, TimeoutException,
      MalformedURLException {

    IndividualResource loanResource1 = loansFixture.checkOutByBarcode(
        new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));

    IndividualResource loanResource2 = loansFixture
      .checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(itemsFixture.basedUponNod())
        .to(usersFixture.rebecca())
        .at(servicePoint.getId()));

    UUID loanId1 = loanResource1.getId();
    UUID loanId2 = loanResource2.getId();

    loansFixture.checkInByBarcode(item1);

    Response storageLoan = loansStorageClient.getById(loanId1);
    assertThat(storageLoan.getJson(), not(isAnonymized()));

    anonymizeLoansForUser(user.getId());

    storageLoan = loansStorageClient.getById(loanId1);

    assertThat(storageLoan.getJson(), isAnonymized());

    assertThat(loansStorageClient.getById(loanId2)
      .getJson(), not(isAnonymized()));
  }

  @Test
  public void canAnonymizeClosedLoansWithNoFeesAndFines()
      throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {

    IndividualResource loanResource = loansFixture.checkOutByBarcode(
        new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    loansFixture.checkInByBarcode(item1);

    anonymizeLoansForUser(user.getId());

    assertThat(loansStorageClient.getById(loanID)
      .getJson(), isAnonymized());
  }

  @Test
  public void canNotAnonymizeClosedLoansWithClosedFeesAndFines()
      throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {

    IndividualResource loanResource = loansFixture.checkOutByBarcode(
        new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    accountsClient.create(new AccountBuilder()
      .withLoan(loanResource)
      .feeFineStatusClosed()
      .withRemainingFeeFine(150));

    loansFixture.checkInByBarcode(item1);

    anonymizeLoansForUser(user.getId());

    assertThat(loansStorageClient.getById(loanID)
      .getJson(), not(isAnonymized()));
  }

  @Test
  public void canAnonymizeMultipleClosedLoansWithClosedFeesAndFines()
      throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {

    IndividualResource loanResource1 = loansFixture.checkOutByBarcode(
        new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));

    InventoryItemResource item2 = itemsFixture.basedUponNod();
    IndividualResource loanResource2 = loansFixture.checkOutByBarcode(
        new CheckOutByBarcodeRequestBuilder().forItem(item2)
      .to(user)
      .at(servicePoint.getId()));

    UUID loanID1 = loanResource1.getId();
    UUID loanID2 = loanResource2.getId();

    accountsClient.create(new AccountBuilder().feeFineStatusOpen()
      .withLoan(loanResource1)
      .feeFineStatusOpen()
      .withRemainingFeeFine(150));

    loansFixture.checkInByBarcode(item1);
    loansFixture.checkInByBarcode(item2);

    anonymizeLoansForUser(user.getId());

    assertThat(loansStorageClient.getById(loanID1)
      .getJson(), not(isAnonymized()));

    assertThat(loansStorageClient.getById(loanID2)
      .getJson(), isAnonymized());
  }

  @Test
  public void doesNotAnonymizeLoansWithOpenFeesAndFines()
      throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {

    IndividualResource loanResource = loansFixture.checkOutByBarcode
        (new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    accountsClient.create(new AccountBuilder().feeFineStatusOpen()
      .withLoan(loanResource)
      .feeFineStatusOpen()
      .withRemainingFeeFine(150));

    loansFixture.checkInByBarcode(item1);

    anonymizeLoansForUser(user.getId());

    JsonObject json = loansStorageClient.getById(loanID).getJson();

    assertThat(json, not(isAnonymized()));
  }

  private void anonymizeLoansForUser(UUID userId)
      throws InterruptedException, ExecutionException, TimeoutException {
    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    client.post(circulationAnonymizeLoansURL(userId.toString()), null, ResponseHandler.any(createCompleted));
    Response response = createCompleted.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    response.getJson().getJsonArray("anonymizedLoans").forEach(this::fakeAnonymizeLoan);
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

}
