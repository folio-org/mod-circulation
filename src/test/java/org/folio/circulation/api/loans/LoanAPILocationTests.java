package org.folio.circulation.api.loans;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.APITestSuite;
import org.folio.circulation.api.support.APITests;
import org.folio.circulation.api.support.builders.HoldingRequestBuilder;
import org.folio.circulation.api.support.builders.LoanRequestBuilder;
import org.folio.circulation.api.support.fixtures.InstanceRequestExamples;
import org.folio.circulation.api.support.fixtures.ItemRequestExamples;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.folio.circulation.api.APITestSuite.annexLocationId;
import static org.folio.circulation.api.APITestSuite.mainLibraryLocationId;
import static org.folio.circulation.api.support.JsonCollectionAssistant.getRecordById;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.folio.circulation.api.APITestSuite.annexLocationId;
import static org.folio.circulation.api.APITestSuite.mainLibraryLocationId;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class LoanAPILocationTests extends APITests {
  @Test
  public void locationIsBasedUponHoldingWhenNoTemporaryLocation()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID instanceId = instancesClient.create(
      InstanceRequestExamples.basedUponSmallAngryPlanet()).getId();

    UUID holdingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(instanceId)
        .withPermanentLocation(mainLibraryLocationId())
        .create())
      .getId();

    UUID itemId = itemsClient.create(
      ItemRequestExamples.basedUponSmallAngryPlanet()
        .forHolding(holdingId)
        .withNoTemporaryLocation())
      .getId();

    UUID loanId = UUID.randomUUID();

    IndividualResource response = loansClient.create(new LoanRequestBuilder()
      .withId(loanId)
      .withItemId(itemId));

    JsonObject createdLoan = response.getJson();

    assertThat("has item location",
      createdLoan.getJsonObject("item").containsKey("location"), is(true));

    assertThat("location is taken from holding",
      createdLoan.getJsonObject("item").getJsonObject("location").getString("name"),
      is("Main Library"));

    Response fetchedLoanResponse = loansClient.getById(loanId);

    assertThat(fetchedLoanResponse.getStatusCode(), is(200));

    JsonObject fetchedLoan = fetchedLoanResponse.getJson();

    assertThat("has item location",
      fetchedLoan.getJsonObject("item").containsKey("location"), is(true));

    assertThat("location is taken from holding",
      fetchedLoan.getJsonObject("item").getJsonObject("location").getString("name"),
      is("Main Library"));
  }

  @Test
  public void locationIsTemporaryLocationWhenItemHasTemporaryLocation()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID instanceId = instancesClient.create(
      InstanceRequestExamples.basedUponSmallAngryPlanet()).getId();

    UUID holdingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(instanceId)
        .withPermanentLocation(mainLibraryLocationId())
        .create())
      .getId();

    UUID itemId = itemsClient.create(ItemRequestExamples.basedUponSmallAngryPlanet()
      .forHolding(holdingId)
      .withTemporaryLocation(annexLocationId()))
      .getId();

    UUID loanId = UUID.randomUUID();

    IndividualResource response = loansClient.create(new LoanRequestBuilder()
      .withId(loanId)
      .withItemId(itemId));

    JsonObject createdLoan = response.getJson();

    assertThat("has item location",
      createdLoan.getJsonObject("item").containsKey("location"), is(true));

    assertThat("location is taken from item",
      createdLoan.getJsonObject("item").getJsonObject("location").getString("name"),
      is("Annex"));

    Response fetchedLoanResponse = loansClient.getById(loanId);

    assertThat(fetchedLoanResponse.getStatusCode(), is(200));

    JsonObject fetchedLoan = fetchedLoanResponse.getJson();

    assertThat("has item location",
      fetchedLoan.getJsonObject("item").containsKey("location"), is(true));

    assertThat("location is taken from item",
      fetchedLoan.getJsonObject("item").getJsonObject("location").getString("name"),
      is("Annex"));
  }

  @Test
  public void locationsComeFromHoldingsForMultipleLoans()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID firstInstanceId = instancesClient.create(
      InstanceRequestExamples.basedUponSmallAngryPlanet()).getId();

    UUID firstHoldingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(firstInstanceId)
        .withPermanentLocation(APITestSuite.mainLibraryLocationId())
        .create())
      .getId();

    UUID firstItemId = itemsClient.create(
      ItemRequestExamples.basedUponSmallAngryPlanet()
        .forHolding(firstHoldingId))
      .getId();

    UUID firstLoanId = loansClient.create(new LoanRequestBuilder()
      .withItemId(firstItemId)).getId();

    UUID secondInstanceId = instancesClient.create(
      InstanceRequestExamples.basedUponTemeraire()).getId();

    UUID secondHoldingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(secondInstanceId)
        .withPermanentLocation(APITestSuite.annexLocationId())
        .create())
      .getId();

    UUID secondItemId = itemsClient.create(
      ItemRequestExamples.basedUponTemeraire()
        .forHolding(secondHoldingId))
      .getId();

    UUID secondLoanId = loansClient.create(new LoanRequestBuilder()
      .withItemId(secondItemId)).getId();

    List<JsonObject> fetchedLoansResponse = loansClient.getAll();

    assertThat(fetchedLoansResponse.size(), is(2));

    JsonObject firstFetchedLoan = getRecordById(
      fetchedLoansResponse, firstLoanId).get();

    assertThat("has item",
      firstFetchedLoan.containsKey("item"), is(true));

    assertThat("has item location",
      firstFetchedLoan.getJsonObject("item").containsKey("location"), is(true));

    assertThat("location is taken from holding",
      firstFetchedLoan.getJsonObject("item").getJsonObject("location").getString("name"),
      is("Main Library"));

    JsonObject secondFetchedLoan = getRecordById(
      fetchedLoansResponse, secondLoanId).get();

    assertThat("has item",
      secondFetchedLoan.containsKey("item"), is(true));

    assertThat("has item location",
      secondFetchedLoan.getJsonObject("item").containsKey("location"), is(true));

    assertThat("location is taken from holding",
      secondFetchedLoan.getJsonObject("item").getJsonObject("location").getString("name"),
      is("Annex"));
  }

  @Test
  public void noLocationsForMultipleLoansWhenNoHoldingAndNoTemporaryLocation()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID firstInstanceId = instancesClient.create(
      InstanceRequestExamples.basedUponSmallAngryPlanet()).getId();

    UUID firstHoldingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(firstInstanceId)
        .withPermanentLocation(APITestSuite.mainLibraryLocationId())
        .create())
      .getId();

    UUID firstItemId = itemsClient.create(
      ItemRequestExamples.basedUponSmallAngryPlanet()
        .forHolding(firstHoldingId))
      .getId();

    UUID firstLoanId = loansClient.create(new LoanRequestBuilder()
      .withItemId(firstItemId)).getId();

    UUID secondInstanceId = instancesClient.create(
      InstanceRequestExamples.basedUponTemeraire()).getId();

    UUID secondHoldingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(secondInstanceId)
        .withPermanentLocation(APITestSuite.annexLocationId())
        .create())
      .getId();

    UUID secondItemId = itemsClient.create(
      ItemRequestExamples.basedUponTemeraire()
        .forHolding(secondHoldingId))
      .getId();

    UUID secondLoanId = loansClient.create(new LoanRequestBuilder()
      .withItemId(secondItemId)).getId();

    //Delete holding or instance
    holdingsClient.delete(firstHoldingId);
    holdingsClient.delete(secondHoldingId);

    List<JsonObject> fetchedLoansResponse = loansClient.getAll();

    assertThat(fetchedLoansResponse.size(), is(2));

    JsonObject firstFetchedLoan = getRecordById(
      fetchedLoansResponse, firstLoanId).get();

    assertThat("has item",
      firstFetchedLoan.containsKey("item"), is(true));

    assertThat("has no item location",
      firstFetchedLoan.getJsonObject("item").containsKey("location"), is(false));

    JsonObject secondFetchedLoan = getRecordById(
      fetchedLoansResponse, secondLoanId).get();

    assertThat("has item",
      secondFetchedLoan.containsKey("item"), is(true));

    assertThat("has no location",
      secondFetchedLoan.getJsonObject("item").containsKey("location"), is(false));
  }

  @Test
  public void locationIsTemporaryItemLocationForMultipleLoans()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID firstInstanceId = instancesClient.create(
      InstanceRequestExamples.basedUponSmallAngryPlanet()).getId();

    UUID firstHoldingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(firstInstanceId)
        .withPermanentLocation(APITestSuite.mainLibraryLocationId())
        .create())
      .getId();

    UUID firstItemId = itemsClient.create(
      ItemRequestExamples.basedUponSmallAngryPlanet()
        .withTemporaryLocation(APITestSuite.annexLocationId())
        .forHolding(firstHoldingId))
      .getId();

    UUID firstLoanId = loansClient.create(new LoanRequestBuilder()
      .withItemId(firstItemId)).getId();

    List<JsonObject> fetchedLoansResponse = loansClient.getAll();

    assertThat(fetchedLoansResponse.size(), is(1));

    JsonObject firstFetchedLoan = getRecordById(
      fetchedLoansResponse, firstLoanId).get();

    assertThat("has item",
      firstFetchedLoan.containsKey("item"), is(true));

    assertThat("has item location",
      firstFetchedLoan.getJsonObject("item").containsKey("location"), is(true));

    assertThat("location is taken from item",
      firstFetchedLoan.getJsonObject("item").getJsonObject("location").getString("name"),
      is("Annex"));
  }
}
