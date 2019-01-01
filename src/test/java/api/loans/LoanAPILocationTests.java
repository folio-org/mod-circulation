package api.loans;

import static api.APITestSuite.mezzanineDisplayCaseLocationId;
import static api.APITestSuite.secondFloorEconomicsLocationId;
import static api.APITestSuite.thirdFloorLocationId;
import static api.support.JsonCollectionAssistant.getRecordById;
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
import api.support.builders.LoanBuilder;
import io.vertx.core.json.JsonObject;

public class LoanAPILocationTests extends APITests {
  @Test
  public void locationIsIncludedForSingleLoan()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final IndividualResource item = itemsFixture.basedUponSmallAngryPlanet(
      holdingsRecordBuilder -> holdingsRecordBuilder
        .withPermanentLocation(thirdFloorLocationId()),
      itemBuilder -> itemBuilder
        .withNoPermanentLocation()
        .withTemporaryLocation(secondFloorEconomicsLocationId())
    );

    UUID loanId = UUID.randomUUID();

    IndividualResource response = loansClient.create(new LoanBuilder()
      .withId(loanId)
      .withItemId(item.getId()));

    JsonObject createdLoan = response.getJson();

    assertThat("has item location",
      createdLoan.getJsonObject("item").containsKey("location"), is(true));

    assertThat(
      createdLoan.getJsonObject("item").getJsonObject("location").getString("name"),
      is("2nd Floor - Economics"));

    Response fetchedLoanResponse = loansClient.getById(loanId);

    assertThat(fetchedLoanResponse.getStatusCode(), is(200));

    JsonObject fetchedLoan = fetchedLoanResponse.getJson();

    assertThat("has item location",
      fetchedLoan.getJsonObject("item").containsKey("location"), is(true));

    assertThat(
      fetchedLoan.getJsonObject("item").getJsonObject("location").getString("name"),
      is("2nd Floor - Economics"));
  }

  @Test
  public void locationIncludedForMultipleLoans()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource firstItem = itemsFixture.basedUponSmallAngryPlanet(
      holdingsRecordBuilder -> holdingsRecordBuilder
        .withPermanentLocation(secondFloorEconomicsLocationId()),
      itemBuilder -> itemBuilder
        .withPermanentLocation(thirdFloorLocationId())
    );

    UUID firstLoanId = loansClient.create(new LoanBuilder()
      .withItemId(firstItem.getId())).getId();

    final IndividualResource secondItem = itemsFixture.basedUponTemeraire(
      holdingsRecordBuilder -> holdingsRecordBuilder
        .withPermanentLocation(mezzanineDisplayCaseLocationId())
        .withNoTemporaryLocation(),
      itemBuilder -> itemBuilder
        .withNoPermanentLocation()
        .withNoTemporaryLocation()
    );

    UUID secondLoanId = loansClient.create(new LoanBuilder()
      .withItemId(secondItem.getId())).getId();

    List<JsonObject> fetchedLoansResponse = loansClient.getAll();

    assertThat(fetchedLoansResponse.size(), is(2));

    JsonObject firstFetchedLoan = getRecordById(
      fetchedLoansResponse, firstLoanId).get();

    assertThat("has item",
      firstFetchedLoan.containsKey("item"), is(true));

    assertThat("has item location",
      firstFetchedLoan.getJsonObject("item").containsKey("location"), is(true));

    assertThat(
      firstFetchedLoan.getJsonObject("item").getJsonObject("location").getString("name"),
      is("3rd Floor"));

    JsonObject secondFetchedLoan = getRecordById(
      fetchedLoansResponse, secondLoanId).get();

    assertThat("has item",
      secondFetchedLoan.containsKey("item"), is(true));

    assertThat("has item location",
      secondFetchedLoan.getJsonObject("item").containsKey("location"), is(true));

    assertThat(
      secondFetchedLoan.getJsonObject("item").getJsonObject("location").getString("name"),
      is("Display Case, Mezzanine"));
  }
}
