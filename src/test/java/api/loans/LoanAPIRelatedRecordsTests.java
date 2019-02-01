package api.loans;

import static api.support.JsonCollectionAssistant.getRecordById;
import static api.support.matchers.UUIDMatcher.is;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.junit.Test;

import api.support.APITests;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;

public class LoanAPIRelatedRecordsTests extends APITests {
  @Test
  public void holdingIdAndInstanceIdIncludedWhenHoldingAndInstanceAreAvailable()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    final IndividualResource checkOutResponse = loansFixture.checkOut(
      smallAngryPlanet, usersFixture.jessica());

    UUID loanId = checkOutResponse.getId();
    JsonObject createdLoan = checkOutResponse.getJson();

    assertThat("has holdings record ID",
      createdLoan.getJsonObject("item").containsKey("holdingsRecordId"), is(true));

    assertThat("has correct holdings record ID",
      createdLoan.getJsonObject("item").getString("holdingsRecordId"),
      is(smallAngryPlanet.getHoldingsRecordId()));

    assertThat("has instance ID",
      createdLoan.getJsonObject("item").containsKey("instanceId"), is(true));

    assertThat("has correct instance ID",
      createdLoan.getJsonObject("item").getString("instanceId"),
      is(smallAngryPlanet.getInstanceId()));

    Response fetchedLoanResponse = loansClient.getById(loanId);

    assertThat(fetchedLoanResponse.getStatusCode(), is(200));

    JsonObject fetchedLoan = fetchedLoanResponse.getJson();

    assertThat("has holdings record ID",
      fetchedLoan.getJsonObject("item").containsKey("holdingsRecordId"), is(true));

    assertThat("has correct holdings record ID",
      fetchedLoan.getJsonObject("item").getString("holdingsRecordId"),
      is(smallAngryPlanet.getHoldingsRecordId()));

    assertThat("has instance ID",
      fetchedLoan.getJsonObject("item").containsKey("instanceId"), is(true));

    assertThat("has correct instance ID",
      fetchedLoan.getJsonObject("item").getString("instanceId"),
      is(smallAngryPlanet.getInstanceId()));
  }

  @Test
  public void holdingAndInstanceIdComesFromMultipleRecordsForMultipleLoans()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();

    UUID firstLoanId = loansFixture.checkOut(smallAngryPlanet,
      usersFixture.jessica()).getId();

    UUID secondLoanId = loansFixture.checkOut(temeraire,
      usersFixture.james()).getId();

    List<JsonObject> fetchedLoansResponse = loansClient.getAll();

    JsonObject firstFetchedLoan = getRecordById(
      fetchedLoansResponse, firstLoanId).get();

    JsonObject secondFetchedLoan = getRecordById(
      fetchedLoansResponse, secondLoanId).get();

    assertThat("has holdings record ID",
      firstFetchedLoan.getJsonObject("item").containsKey("holdingsRecordId"), is(true));

    assertThat("has correct holdings record ID",
      firstFetchedLoan.getJsonObject("item").getString("holdingsRecordId"),
      is(smallAngryPlanet.getHoldingsRecordId()));

    assertThat("has instance ID",
      firstFetchedLoan.getJsonObject("item").containsKey("instanceId"), is(true));

    assertThat("has correct instance ID",
      firstFetchedLoan.getJsonObject("item").getString("instanceId"),
      is(smallAngryPlanet.getInstanceId()));

    assertThat("has holdings record ID",
      secondFetchedLoan.getJsonObject("item").containsKey("holdingsRecordId"), is(true));

    assertThat("has correct holdings record ID",
      secondFetchedLoan.getJsonObject("item").getString("holdingsRecordId"),
      is(temeraire.getHoldingsRecordId()));

    assertThat("has instance ID",
      secondFetchedLoan.getJsonObject("item").containsKey("instanceId"), is(true));

    assertThat("has correct instance ID",
      secondFetchedLoan.getJsonObject("item").getString("instanceId"),
      is(temeraire.getInstanceId()));
  }
}
