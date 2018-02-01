package org.folio.circulation.api.loans;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.support.APITests;
import org.folio.circulation.api.support.builders.HoldingRequestBuilder;
import org.folio.circulation.api.support.builders.LoanRequestBuilder;
import org.folio.circulation.api.support.fixtures.InstanceRequestExamples;
import org.folio.circulation.api.support.fixtures.ItemRequestExamples;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.folio.circulation.api.support.JsonCollectionAssistant.getRecordById;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class LoanAPIRelatedRecordsTests extends APITests {

  @Test
  public void holdingIdIncludedWhenHoldingAndInstanceAreAvailable()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID instanceId = instancesClient.create(
      InstanceRequestExamples.basedUponSmallAngryPlanet()).getId();

    UUID holdingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(instanceId)
        .create())
      .getId();

    UUID itemId = itemsClient.create(
      ItemRequestExamples.basedUponSmallAngryPlanet()
        .forHolding(holdingId))
      .getId();

    UUID loanId = UUID.randomUUID();

    IndividualResource response = loansClient.create(new LoanRequestBuilder()
      .withId(loanId)
      .withItemId(itemId));

    JsonObject createdLoan = response.getJson();

    assertThat("has holdings record ID",
      createdLoan.getJsonObject("item").containsKey("holdingsRecordId"), is(true));

    assertThat("has correct holdings record ID",
      createdLoan.getJsonObject("item").getString("holdingsRecordId"),
      is(holdingId.toString()));

    Response fetchedLoanResponse = loansClient.getById(loanId);

    assertThat(fetchedLoanResponse.getStatusCode(), is(200));

    JsonObject fetchedLoan = fetchedLoanResponse.getJson();

    assertThat("has holdings record ID",
      fetchedLoan.getJsonObject("item").containsKey("holdingsRecordId"), is(true));

    assertThat("has correct holdings record ID",
      fetchedLoan.getJsonObject("item").getString("holdingsRecordId"),
      is(holdingId.toString()));
  }

  @Test
  public void holdingIdComesFromMultipleRecordsForMultipleLoans()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID firstInstanceId = instancesClient.create(
      InstanceRequestExamples.basedUponSmallAngryPlanet()).getId();

    UUID firstHoldingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(firstInstanceId)
        .create())
      .getId();

    UUID firstItemId = itemsClient.create(
      ItemRequestExamples.basedUponSmallAngryPlanet()
        .forHolding(firstHoldingId))
      .getId();

    UUID secondInstanceId = instancesClient.create(
      InstanceRequestExamples.basedUponTemeraire()).getId();

    UUID secondHoldingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(secondInstanceId)
        .create())
      .getId();

    UUID secondItemId = itemsClient.create(
      ItemRequestExamples.basedUponTemeraire()
        .forHolding(secondHoldingId))
      .getId();

    UUID firstLoanId = loansClient.create(new LoanRequestBuilder()
      .withItemId(firstItemId)).getId();

    UUID secondLoanId = loansClient.create(new LoanRequestBuilder()
      .withItemId(secondItemId)).getId();

    List<JsonObject> fetchedLoansResponse = loansClient.getAll();

    JsonObject firstFetchedLoan = getRecordById(
      fetchedLoansResponse, firstLoanId).get();

    JsonObject secondFetchedLoan = getRecordById(
      fetchedLoansResponse, secondLoanId).get();

    assertThat("has holdings record ID",
      firstFetchedLoan.getJsonObject("item").containsKey("holdingsRecordId"), is(true));

    assertThat("has correct holdings record ID",
      firstFetchedLoan.getJsonObject("item").getString("holdingsRecordId"),
      is(firstHoldingId.toString()));

    assertThat("has holdings record ID",
      secondFetchedLoan.getJsonObject("item").containsKey("holdingsRecordId"), is(true));

    assertThat("has correct holdings record ID",
      secondFetchedLoan.getJsonObject("item").getString("holdingsRecordId"),
      is(secondHoldingId.toString()));
  }
}
