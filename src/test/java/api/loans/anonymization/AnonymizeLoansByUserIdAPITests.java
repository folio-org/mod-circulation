package api.loans.anonymization;

import static api.support.matchers.LoanMatchers.isAnonymized;
import static api.support.matchers.LoanMatchers.isOpen;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;

import java.util.UUID;

import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.Test;

import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import io.vertx.core.json.JsonObject;

public class AnonymizeLoansByUserIdAPITests extends LoanAnonymizationTests {

  @Test
  void canNotAnonymizeNotClosedLoans() {

    IndividualResource loanResource = checkOutFixture.checkOutByBarcode(
        new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    assertThat(loanResource.getJson(), isOpen());

    anonymizeLoansForUser(user.getId());

    JsonObject storageLoan = loansStorageClient.getById(loanID)
      .getJson();
    assertThat(storageLoan, not(isAnonymized()));
  }

  @Test
  void canAonymizeLoansForParticularUser() {

    IndividualResource loanResource1 = checkOutFixture.checkOutByBarcode(
        new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));

    IndividualResource loanResource2 = checkOutFixture
      .checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(itemsFixture.basedUponNod())
        .to(usersFixture.rebecca())
        .at(servicePoint.getId()));

    UUID loanId1 = loanResource1.getId();
    UUID loanId2 = loanResource2.getId();

    checkInFixture.checkInByBarcode(item1);

    Response storageLoan = loansStorageClient.getById(loanId1);
    assertThat(storageLoan.getJson(), not(isAnonymized()));

    anonymizeLoansForUser(user.getId());

    storageLoan = loansStorageClient.getById(loanId1);

    assertThat(storageLoan.getJson(), isAnonymized());

    assertThat(loansStorageClient.getById(loanId2)
      .getJson(), not(isAnonymized()));
  }

  @Test
  void canAnonymizeClosedLoansWithNoFeesAndFines() {

    IndividualResource loanResource = checkOutFixture.checkOutByBarcode(
        new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    checkInFixture.checkInByBarcode(item1);

    anonymizeLoansForUser(user.getId());

    assertThat(loansStorageClient.getById(loanID)
      .getJson(), isAnonymized());
  }

  @Test
  void canNotAnonymizeClosedLoansWithClosedFeesAndFines() {

    IndividualResource loanResource = checkOutFixture.checkOutByBarcode(
        new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, ClockUtil.getZonedDateTime());

    checkInFixture.checkInByBarcode(item1);

    anonymizeLoansForUser(user.getId());

    assertThat(loansStorageClient.getById(loanID)
      .getJson(), not(isAnonymized()));
  }

  @Test
  void canAnonymizeMultipleClosedLoansWithClosedFeesAndFines() {

    IndividualResource loanResource1 = checkOutFixture.checkOutByBarcode(
        new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));

    ItemResource item2 = itemsFixture.basedUponNod();
    IndividualResource loanResource2 = checkOutFixture.checkOutByBarcode(
        new CheckOutByBarcodeRequestBuilder().forItem(item2)
      .to(user)
      .at(servicePoint.getId()));

    UUID loanID1 = loanResource1.getId();
    UUID loanID2 = loanResource2.getId();

    createOpenAccountWithFeeFines(loanResource1);

    checkInFixture.checkInByBarcode(item1);
    checkInFixture.checkInByBarcode(item2);

    anonymizeLoansForUser(user.getId());

    assertThat(loansStorageClient.getById(loanID1)
      .getJson(), not(isAnonymized()));

    assertThat(loansStorageClient.getById(loanID2)
      .getJson(), isAnonymized());
  }

  @Test
  void doesNotAnonymizeLoansWithOpenFeesAndFines() {

    IndividualResource loanResource = checkOutFixture.checkOutByBarcode
        (new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createOpenAccountWithFeeFines(loanResource);

    checkInFixture.checkInByBarcode(item1);

    anonymizeLoansForUser(user.getId());

    JsonObject json = loansStorageClient.getById(loanID).getJson();

    assertThat(json, not(isAnonymized()));
  }


}
