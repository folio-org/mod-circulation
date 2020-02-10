package api.loans.anonymization;

import static api.support.matchers.LoanMatchers.hasOpenStatus;
import static api.support.matchers.LoanMatchers.isAnonymized;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.junit.Test;

import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;

public class AnonymizeLoansByUserIdAPITests extends LoanAnonymizationTests {

  @Test
  public void canNotAnonymizeNotClosedLoans() {

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
  public void canAonymizeLoansForParticularUser() {

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
  public void canAnonymizeClosedLoansWithNoFeesAndFines() {

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
  public void canNotAnonymizeClosedLoansWithClosedFeesAndFines() {

    IndividualResource loanResource = loansFixture.checkOutByBarcode(
        new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, DateTime.now());

    loansFixture.checkInByBarcode(item1);

    anonymizeLoansForUser(user.getId());

    assertThat(loansStorageClient.getById(loanID)
      .getJson(), not(isAnonymized()));
  }

  @Test
  public void canAnonymizeMultipleClosedLoansWithClosedFeesAndFines() {

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

    createOpenAccountWithFeeFines(loanResource1);

    loansFixture.checkInByBarcode(item1);
    loansFixture.checkInByBarcode(item2);

    anonymizeLoansForUser(user.getId());

    assertThat(loansStorageClient.getById(loanID1)
      .getJson(), not(isAnonymized()));

    assertThat(loansStorageClient.getById(loanID2)
      .getJson(), isAnonymized());
  }

  @Test
  public void doesNotAnonymizeLoansWithOpenFeesAndFines() {

    IndividualResource loanResource = loansFixture.checkOutByBarcode
        (new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createOpenAccountWithFeeFines(loanResource);

    loansFixture.checkInByBarcode(item1);

    anonymizeLoansForUser(user.getId());

    JsonObject json = loansStorageClient.getById(loanID).getJson();

    assertThat(json, not(isAnonymized()));
  }


}
