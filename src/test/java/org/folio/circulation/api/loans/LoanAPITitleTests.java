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

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.folio.circulation.api.support.JsonCollectionAssistant.getRecordById;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class LoanAPITitleTests extends APITests {

  @Test
  public void titleIsFromInstanceWhenHoldingAndInstance()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID instanceId = instancesClient.create(
      InstanceRequestExamples.smallAngryPlanet()).getId();

    UUID holdingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(instanceId)
        .create())
      .getId();

    UUID itemId = itemsClient.create(
      ItemRequestExamples.basedUponSmallAngryPlanet()
        .withTitle("A different title") // deliberately different to demonstrate behaviour
        .forHolding(holdingId))
      .getId();

    UUID loanId = UUID.randomUUID();

    IndividualResource response = loansClient.create(new LoanRequestBuilder()
      .withId(loanId)
      .withItemId(itemId));

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
  public void titleIsFromItemWhenNoHolding()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID instanceId = instancesClient.create(
      InstanceRequestExamples.smallAngryPlanet()).getId();

    UUID holdingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(instanceId)
        .create())
      .getId();

    UUID itemId = itemsClient.create(
      ItemRequestExamples.basedUponSmallAngryPlanet()
        .withTitle("A different title") // deliberately different to demonstrate behaviour
        .forHolding(holdingId))
      .getId();

    holdingsClient.delete(holdingId);

    UUID loanId = UUID.randomUUID();

    IndividualResource response = loansClient.create(new LoanRequestBuilder()
      .withId(loanId)
      .withItemId(itemId));

    JsonObject createdLoan = response.getJson();

    assertThat("has item title",
      createdLoan.getJsonObject("item").containsKey("title"), is(true));

    assertThat("title is taken from item",
      createdLoan.getJsonObject("item").getString("title"),
      is("A different title"));

    Response fetchedLoanResponse = loansClient.getById(loanId);

    assertThat(fetchedLoanResponse.getStatusCode(), is(200));

    JsonObject fetchedLoan = fetchedLoanResponse.getJson();

    assertThat("has item title",
      fetchedLoan.getJsonObject("item").containsKey("title"), is(true));

    assertThat("title is taken from item",
      fetchedLoan.getJsonObject("item").getString("title"),
      is("A different title"));
  }

  @Test
  public void titleIsFromItemWhenNoInstance()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID instanceId = instancesClient.create(
      InstanceRequestExamples.smallAngryPlanet()).getId();

    UUID holdingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(instanceId)
        .create())
      .getId();

    UUID itemId = itemsClient.create(
      ItemRequestExamples.basedUponSmallAngryPlanet()
        .withTitle("A different title") // deliberately different to demonstrate behaviour
        .forHolding(holdingId))
      .getId();

    instancesClient.delete(instanceId);

    UUID loanId = UUID.randomUUID();

    IndividualResource response = loansClient.create(new LoanRequestBuilder()
      .withId(loanId)
      .withItemId(itemId));

    JsonObject createdLoan = response.getJson();

    assertThat("has item title",
      createdLoan.getJsonObject("item").containsKey("title"), is(true));

    assertThat("title is taken from item",
      createdLoan.getJsonObject("item").getString("title"),
      is("A different title"));

    Response fetchedLoanResponse = loansClient.getById(loanId);

    assertThat(fetchedLoanResponse.getStatusCode(), is(200));

    JsonObject fetchedLoan = fetchedLoanResponse.getJson();

    assertThat("has item title",
      fetchedLoan.getJsonObject("item").containsKey("title"), is(true));

    assertThat("title is taken from item",
      fetchedLoan.getJsonObject("item").getString("title"),
      is("A different title"));
  }

  @Test
  public void titlesComeFromMultipleInstancesForMultipleLoans()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID firstInstanceId = instancesClient.create(
      InstanceRequestExamples.smallAngryPlanet()).getId();

    UUID firstHoldingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(firstInstanceId)
        .create())
      .getId();

    UUID firstItemId = itemsClient.create(
      ItemRequestExamples.basedUponSmallAngryPlanet()
        .withTitle("A different title") // deliberately different to demonstrate behaviour
        .forHolding(firstHoldingId))
      .getId();

    UUID secondInstanceId = instancesClient.create(
      InstanceRequestExamples.temeraire()).getId();

    UUID secondHoldingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(secondInstanceId)
        .create())
      .getId();

    UUID secondItemId = itemsClient.create(
      ItemRequestExamples.basedUponTemeraire()
        .withTitle("Another different title") // deliberately different to demonstrate behaviour
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

  @Test
  public void titlesComeFromItemForMultipleLoansWhenHoldingOrInstanceNotFound()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID firstInstanceId = instancesClient.create(
      InstanceRequestExamples.smallAngryPlanet()).getId();

    UUID firstHoldingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(firstInstanceId)
        .create())
      .getId();

    UUID firstItemId = itemsClient.create(
      ItemRequestExamples.basedUponSmallAngryPlanet()
        .withTitle("A different title") // deliberately different to demonstrate behaviour
        .forHolding(firstHoldingId))
      .getId();

    UUID secondInstanceId = instancesClient.create(
      InstanceRequestExamples.temeraire()).getId();

    UUID secondHoldingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(secondInstanceId)
        .create())
      .getId();

    UUID secondItemId = itemsClient.create(
      ItemRequestExamples.basedUponTemeraire()
        .withTitle("Another different title") // deliberately different to demonstrate behaviour
        .forHolding(secondHoldingId))
      .getId();

    UUID firstLoanId = loansClient.create(new LoanRequestBuilder()
      .withItemId(firstItemId)).getId();

    UUID secondLoanId = loansClient.create(new LoanRequestBuilder()
      .withItemId(secondItemId)).getId();

    //Delete instance or holding
    instancesClient.delete(firstInstanceId);

    holdingsClient.delete(secondHoldingId);

    List<JsonObject> fetchedLoansResponse = loansClient.getAll();

    JsonObject firstFetchedLoan = getRecordById(
      fetchedLoansResponse, firstLoanId).get();

    JsonObject secondFetchedLoan = getRecordById(
      fetchedLoansResponse, secondLoanId).get();

    assertThat("has item title",
      firstFetchedLoan.getJsonObject("item").containsKey("title"), is(true));

    assertThat("title is taken from item",
      firstFetchedLoan.getJsonObject("item").getString("title"),
      is("A different title"));

    assertThat("has item title",
      secondFetchedLoan.getJsonObject("item").containsKey("title"), is(true));

    assertThat("title is taken from item",
      secondFetchedLoan.getJsonObject("item").getString("title"),
      is("Another different title"));
  }
}
