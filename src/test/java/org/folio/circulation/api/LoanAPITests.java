package org.folio.circulation.api;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.HttpClient;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.JsonResponse;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class LoanAPITests {

  HttpClient client = APITestSuite.createHttpClient();

  @Test
  public void canCreateALoan()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    JsonObject loanRequest = loanRequest(id, itemId, userId,
      new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC), "Open");

    IndividualResource response = createLoan(loanRequest);

    JsonObject loan = response.getJson();

    assertThat("id does not match",
      loan.getString("id"), is(id.toString()));

    assertThat("user id does not match",
      loan.getString("userId"), is(userId.toString()));

    assertThat("item id does not match",
      loan.getString("itemId"), is(itemId.toString()));

    assertThat("loan date does not match",
      loan.getString("loanDate"), is("2017-02-27T10:23:43.000Z"));

    assertThat("status is not open",
      loan.getJsonObject("status").getString("name"), is("Open"));
  }

  @Test
  public void canGetALoanById()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    JsonObject loanRequest = loanRequest(id, itemId, userId,
      new DateTime(2016, 10, 15, 8, 26, 53, DateTimeZone.UTC), "Open");

    createLoan(loanRequest);

    JsonResponse getResponse = getById(id);

    assertThat(String.format("Failed to get loan: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject loan = getResponse.getJson();

    assertThat("id does not match",
      loan.getString("id"), is(id.toString()));

    assertThat("user id does not match",
      loan.getString("userId"), is(userId.toString()));

    assertThat("item id does not match",
      loan.getString("itemId"), is(itemId.toString()));

    assertThat("loan date does not match",
      loan.getString("loanDate"), is("2016-10-15T08:26:53.000Z"));

    assertThat("status is not open",
      loan.getJsonObject("status").getString("name"), is("Open"));
  }

  @Test
  public void canCompleteALoanByReturningTheItem()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    DateTime loanDate = new DateTime(2017, 3, 1, 13, 25, 46, 232, DateTimeZone.UTC);

    IndividualResource loan = createLoan(loanRequest(loanDate));

    JsonObject returnedLoan = loan.copyJson();

    returnedLoan
      .put("status", new JsonObject().put("name", "Closed"))
      .put("returnDate", new DateTime(2017, 3, 5, 14, 23, 41, DateTimeZone.UTC)
        .toString(ISODateTimeFormat.dateTime()));

    CompletableFuture<JsonResponse> putCompleted = new CompletableFuture<>();

    client.put(loanUrl(String.format("/%s", loan.getId())), returnedLoan,
      APITestSuite.TENANT_ID, ResponseHandler.json(putCompleted));

    JsonResponse putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to update loan: %s", putResponse.getBody()),
      putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    JsonResponse updatedLoanResponse = getById(UUID.fromString(loan.getId()));

    JsonObject updatedLoan = updatedLoanResponse.getJson();

    assertThat(updatedLoan.getString("returnDate"),
      is("2017-03-05T14:23:41.000Z"));

    assertThat("status is not closed",
      updatedLoan.getJsonObject("status").getString("name"), is("Closed"));
  }

  private JsonResponse getById(UUID id)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    URL getInstanceUrl = loanUrl(String.format("/%s", id));

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    client.get(getInstanceUrl, APITestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    return getCompleted.get(5, TimeUnit.SECONDS);
  }

  private IndividualResource createLoan(JsonObject loanRequest)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(loanUrl(), loanRequest, APITestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create loan: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    return new IndividualResource(response);
  }

  private JsonObject loanRequest() {
    return loanRequest(UUID.randomUUID(), UUID.randomUUID(),
      UUID.randomUUID(), DateTime.parse("2017-03-06T16:04:43.000+02:00",
        ISODateTimeFormat.dateTime()), "Open");
  }

  private JsonObject loanRequest(UUID userId, String statusName) {
    Random random = new Random();

    return loanRequest(UUID.randomUUID(), UUID.randomUUID(),
      userId, DateTime.now().minusDays(random.nextInt(10)), statusName);
  }

  private JsonObject loanRequest(DateTime loanDate) {
    return loanRequest(UUID.randomUUID(), UUID.randomUUID(),
      UUID.randomUUID(), loanDate, "Open");
  }

  private JsonObject loanRequest(
    UUID id,
    UUID itemId,
    UUID userId,
    DateTime loanDate,
    String statusName) {

    JsonObject loanRequest = new JsonObject();

    if(id != null) {
      loanRequest.put("id", id.toString());
    }

    loanRequest
      .put("userId", userId.toString())
      .put("itemId", itemId.toString())
      .put("loanDate", loanDate.toString(ISODateTimeFormat.dateTime()))
      .put("status", new JsonObject().put("name", statusName));

    if(statusName == "Closed") {
      loanRequest.put("returnDate",
        loanDate.plusDays(1).plusHours(4).toString(ISODateTimeFormat.dateTime()));
    }

    return loanRequest;
  }

  private static URL loanUrl() throws MalformedURLException {
    return loanUrl("");
  }

  private static URL loanUrl(String subPath)
    throws MalformedURLException {

    return APITestSuite.moduleUrl("/circulation/loans" + subPath);
  }
}
