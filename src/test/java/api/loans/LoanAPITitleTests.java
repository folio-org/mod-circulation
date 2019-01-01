package api.loans;

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
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;

public class LoanAPITitleTests extends APITests {

  @Test
  public void titleIsFromInstanceWhenHoldingAndInstanceAreAvailable()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    UUID loanId = UUID.randomUUID();

    IndividualResource response = loansClient.create(new LoanBuilder()
      .withId(loanId)
      .withItemId(smallAngryPlanet.getId()));

    JsonObject createdLoan = response.getJson();

    assertThat("has item title",
      createdLoan.getJsonObject("item").containsKey("title"), is(true));

    assertThat("title is taken from instance",
      createdLoan.getJsonObject("item").getString("title"),
      is("The Long Way to a Small, Angry Planet"));

    Response fetchedLoanResponse = loansClient.getById(loanId);

    assertThat(fetchedLoanResponse.getStatusCode(), is(200));

    JsonObject fetchedLoan = fetchedLoanResponse.getJson();

    assertThat("has item title",
      fetchedLoan.getJsonObject("item").containsKey("title"), is(true));

    assertThat("title is taken from instance",
      fetchedLoan.getJsonObject("item").getString("title"),
      is("The Long Way to a Small, Angry Planet"));
  }

  @Test
  public void titlesComeFromMultipleInstancesForMultipleLoans()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();

    UUID firstLoanId = loansClient.create(new LoanBuilder()
      .withItemId(smallAngryPlanet.getId())).getId();

    UUID secondLoanId = loansClient.create(new LoanBuilder()
      .withItemId(temeraire.getId())).getId();

    List<JsonObject> fetchedLoansResponse = loansClient.getAll();

    JsonObject firstFetchedLoan = getRecordById(
      fetchedLoansResponse, firstLoanId).get();

    JsonObject secondFetchedLoan = getRecordById(
      fetchedLoansResponse, secondLoanId).get();

    assertThat("has item title",
      firstFetchedLoan.getJsonObject("item").containsKey("title"), is(true));

    assertThat("title is taken from instance",
      firstFetchedLoan.getJsonObject("item").getString("title"),
      is("The Long Way to a Small, Angry Planet"));

    assertThat("has item title",
      secondFetchedLoan.getJsonObject("item").containsKey("title"), is(true));

    assertThat("title is taken from instance",
      secondFetchedLoan.getJsonObject("item").getString("title"),
      is("Temeraire"));
  }
}
