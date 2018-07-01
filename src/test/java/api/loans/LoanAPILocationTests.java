package api.loans;

import api.support.APITests;
import api.support.builders.HoldingBuilder;
import api.support.builders.LoanBuilder;
import api.support.fixtures.InstanceExamples;
import api.support.fixtures.ItemExamples;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static api.APITestSuite.mezzanineDisplayCaseLocationId;
import static api.APITestSuite.secondFloorEconomicsLocationId;
import static api.APITestSuite.thirdFloorLocationId;
import static api.support.JsonCollectionAssistant.getRecordById;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class LoanAPILocationTests extends APITests {
  @Test
  public void locationIsIncludedForSingleLoan()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID instanceId = instancesClient.create(
      InstanceExamples.basedUponSmallAngryPlanet()).getId();

    UUID holdingId = holdingsClient.create(
      new HoldingBuilder()
        .forInstance(instanceId)
        .withPermanentLocation(thirdFloorLocationId())
        .withTemporaryLocation(mezzanineDisplayCaseLocationId())
        .create())
      .getId();

    UUID itemId = itemsClient.create(
      ItemExamples.basedUponSmallAngryPlanet()
        .forHolding(holdingId)
        .withNoPermanentLocation()
        .withTemporaryLocation(secondFloorEconomicsLocationId()))
      .getId();

    UUID loanId = UUID.randomUUID();

    IndividualResource response = loansClient.create(new LoanBuilder()
      .withId(loanId)
      .withItemId(itemId));

    JsonObject createdLoan = response.getJson();

    assertThat("has item location",
      createdLoan.getJsonObject("item").containsKey("location"), is(true));

    assertThat("location is taken from holding",
      createdLoan.getJsonObject("item").getJsonObject("location").getString("name"),
      is("2nd Floor - Economics"));

    Response fetchedLoanResponse = loansClient.getById(loanId);

    assertThat(fetchedLoanResponse.getStatusCode(), is(200));

    JsonObject fetchedLoan = fetchedLoanResponse.getJson();

    assertThat("has item location",
      fetchedLoan.getJsonObject("item").containsKey("location"), is(true));

    assertThat("location is taken from holding",
      fetchedLoan.getJsonObject("item").getJsonObject("location").getString("name"),
      is("2nd Floor - Economics"));
  }

  @Test
  public void locationIncludedForMultipleLoans()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID firstInstanceId = instancesClient.create(
      InstanceExamples.basedUponSmallAngryPlanet()).getId();

    UUID firstHoldingId = holdingsClient.create(
      new HoldingBuilder()
        .forInstance(firstInstanceId)
        .withPermanentLocation(secondFloorEconomicsLocationId())
        .create())
      .getId();

    UUID firstItemId = itemsClient.create(
      ItemExamples.basedUponSmallAngryPlanet()
        .forHolding(firstHoldingId)
        .withPermanentLocation(thirdFloorLocationId()))
      .getId();

    UUID firstLoanId = loansClient.create(new LoanBuilder()
      .withItemId(firstItemId)).getId();

    UUID secondInstanceId = instancesClient.create(
      InstanceExamples.basedUponTemeraire()).getId();

    UUID secondHoldingId = holdingsClient.create(
      new HoldingBuilder()
        .forInstance(secondInstanceId)
        .withPermanentLocation(mezzanineDisplayCaseLocationId())
        .withNoTemporaryLocation()
        .create())
      .getId();

    UUID secondItemId = itemsClient.create(
      ItemExamples.basedUponTemeraire()
        .forHolding(secondHoldingId))
      .getId();

    UUID secondLoanId = loansClient.create(new LoanBuilder()
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
      is("3rd Floor"));

    JsonObject secondFetchedLoan = getRecordById(
      fetchedLoansResponse, secondLoanId).get();

    assertThat("has item",
      secondFetchedLoan.containsKey("item"), is(true));

    assertThat("has item location",
      secondFetchedLoan.getJsonObject("item").containsKey("location"), is(true));

    assertThat("location is taken from holding",
      secondFetchedLoan.getJsonObject("item").getJsonObject("location").getString("name"),
      is("Display Case, Mezzanine"));
  }
}
