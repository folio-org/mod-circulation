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
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

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
      InstanceRequestExamples.smallAngryPlanet()).getId();

    UUID holdingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(instanceId)
        .withPermanentLocation(mainLibraryLocationId())
        .create())
      .getId();

    UUID itemId = itemsClient.create(
      ItemRequestExamples.basedUponSmallAngryPlanet()
        .forHolding(holdingId)
        .withPermanentLocation(annexLocationId()) //Deliberately different to demonstrate precedence
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
      InstanceRequestExamples.smallAngryPlanet()).getId();

    UUID holdingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(instanceId)
        .withPermanentLocation(mainLibraryLocationId())
        .create())
      .getId();

    UUID itemId = itemsClient.create(ItemRequestExamples.basedUponSmallAngryPlanet()
      .forHolding(holdingId)
      .withPermanentLocation(mainLibraryLocationId())
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
  public void locationIsBasedUponItemPermanentLocationWhenNoHolding()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID itemId = itemsClient.create(
      ItemRequestExamples.basedUponSmallAngryPlanet()
        .forHolding(null)
        .withPermanentLocation(mainLibraryLocationId())
        .withNoTemporaryLocation())
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
      is("Main Library"));

    Response fetchedLoanResponse = loansClient.getById(loanId);

    assertThat(fetchedLoanResponse.getStatusCode(), is(200));

    JsonObject fetchedLoan = fetchedLoanResponse.getJson();

    assertThat("has item location",
      fetchedLoan.getJsonObject("item").containsKey("location"), is(true));

    assertThat("location is taken from item",
      fetchedLoan.getJsonObject("item").getJsonObject("location").getString("name"),
      is("Main Library"));
  }

  @Test
  public void noLocationNoHoldingAndNoLocationsOnItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID itemId = itemsClient.create(
      ItemRequestExamples.basedUponSmallAngryPlanet()
        .forHolding(null)
        .withNoPermanentLocation()
        .withNoTemporaryLocation())
      .getId();

    UUID loanId = UUID.randomUUID();

    IndividualResource response = loansClient.create(new LoanRequestBuilder()
      .withId(loanId)
      .withItemId(itemId));

    JsonObject createdLoan = response.getJson();

    assertThat("has item location",
      createdLoan.getJsonObject("item").containsKey("location"), is(false));

    Response fetchedLoanResponse = loansClient.getById(loanId);

    assertThat(fetchedLoanResponse.getStatusCode(), is(200));

    JsonObject fetchedLoan = fetchedLoanResponse.getJson();

    assertThat("has item location",
      fetchedLoan.getJsonObject("item").containsKey("location"), is(false));
  }
}
