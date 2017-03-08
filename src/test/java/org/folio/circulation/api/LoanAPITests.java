package org.folio.circulation.api;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.HttpClient;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.JsonResponse;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Test;

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

  HttpClient client = APITestSuite.createUsingVertx(
    (Vertx vertx) -> new HttpClient(vertx,
      APITestSuite.storageUrl(), exception -> {
        System.out.println(
          String.format("Request to circulation module failed: %s",
            exception.toString()));
    }));

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
