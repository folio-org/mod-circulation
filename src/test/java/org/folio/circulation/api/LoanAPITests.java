package org.folio.circulation.api;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.support.ItemExamples;
import org.folio.circulation.api.support.LoanRequest;
import org.folio.circulation.support.http.client.*;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Before;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class LoanAPITests {

  HttpClient client = APITestSuite.createHttpClient(exception -> {
    System.out.println(
      String.format("Request to circulation module failed: %s",
        exception.toString()));
  });

  @Before
  public void beforeEach()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    deleteAllLoans();
    deleteAllItems();
  }

  @Test
  public void canCreateALoan()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    JsonObject loanRequest = LoanRequest.create(id, itemId, userId,
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
    UUID itemId = createItem(ItemExamples.smallAngryPlanet()).getId();
    UUID userId = UUID.randomUUID();

    JsonObject loanRequest = LoanRequest.create(id, itemId, userId,
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

    assertThat("title is taken from item",
      loan.getJsonObject("item").getString("title"),
      is("The Long Way to a Small, Angry Planet"));

    assertThat("barcode is taken from item",
      loan.getJsonObject("item").getString("barcode"),
      is("036000291452"));
  }

  @Test
  public void loanFoundByIdDoesNotProvideItemInformationForUnknownItem()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    JsonObject loanRequest = LoanRequest.create(id, itemId, userId,
      new DateTime(2016, 10, 15, 8, 26, 53, DateTimeZone.UTC), "Open");

    createLoan(loanRequest);

    JsonResponse getResponse = getById(id);

    assertThat(String.format("Failed to get loan: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject loan = getResponse.getJson();

    assertThat("no item information available",
      loan.containsKey("item"), is(false));
  }

  @Test
  public void loanNotFoundForUnknownId()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    JsonResponse getResponse = getById(UUID.randomUUID());

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void canPageLoans()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    createLoan(LoanRequest.create());
    createLoan(LoanRequest.create());
    createLoan(LoanRequest.create());
    createLoan(LoanRequest.create());
    createLoan(LoanRequest.create());
    createLoan(LoanRequest.create());
    createLoan(LoanRequest.create());

    CompletableFuture<JsonResponse> firstPageCompleted = new CompletableFuture<>();
    CompletableFuture<JsonResponse> secondPageCompleted = new CompletableFuture<>();

    client.get(loansUrl() + "?limit=4", APITestSuite.TENANT_ID,
      ResponseHandler.json(firstPageCompleted));

    client.get(loansUrl() + "?limit=4&offset=4", APITestSuite.TENANT_ID,
      ResponseHandler.json(secondPageCompleted));

    JsonResponse firstPageResponse = firstPageCompleted.get(5, TimeUnit.SECONDS);
    JsonResponse secondPageResponse = secondPageCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get first page of loans: %s",
      firstPageResponse.getBody()),
      firstPageResponse.getStatusCode(), is(200));

    assertThat(String.format("Failed to get second page of loans: %s",
      secondPageResponse.getBody()),
      secondPageResponse.getStatusCode(), is(200));

    JsonObject firstPage = firstPageResponse.getJson();
    JsonObject secondPage = secondPageResponse.getJson();

    JsonArray firstPageLoans = firstPage.getJsonArray("loans");
    JsonArray secondPageLoans = secondPage.getJsonArray("loans");

    assertThat(firstPageLoans.size(), is(4));
    assertThat(firstPage.getInteger("totalRecords"), is(7));

    assertThat(secondPageLoans.size(), is(3));
    assertThat(secondPage.getInteger("totalRecords"), is(7));
  }

  @Test
  public void canCompleteALoanByReturningTheItem()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    DateTime loanDate = new DateTime(2017, 3, 1, 13, 25, 46, 232, DateTimeZone.UTC);

    UUID itemId = createItem(ItemExamples.nod()).getId();

    IndividualResource loan = createLoan(LoanRequest.create(
      UUID.randomUUID(), itemId,
      UUID.randomUUID(), loanDate, "Open"));

    JsonObject returnedLoan = loan.copyJson();

    returnedLoan
      .put("status", new JsonObject().put("name", "Closed"))
      .put("returnDate", new DateTime(2017, 3, 5, 14, 23, 41, DateTimeZone.UTC)
        .toString(ISODateTimeFormat.dateTime()));

    CompletableFuture<JsonResponse> putCompleted = new CompletableFuture<>();

    client.put(loansUrl(String.format("/%s", loan.getId())), returnedLoan,
      APITestSuite.TENANT_ID, ResponseHandler.json(putCompleted));

    JsonResponse putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to update loan: %s", putResponse.getBody()),
      putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    JsonResponse updatedLoanResponse = getById(loan.getId());

    JsonObject updatedLoan = updatedLoanResponse.getJson();

    assertThat(updatedLoan.getString("returnDate"),
      is("2017-03-05T14:23:41.000Z"));

    assertThat("status is not closed",
      updatedLoan.getJsonObject("status").getString("name"), is("Closed"));

    assertThat("title is taken from item",
      updatedLoan.getJsonObject("item").getString("title"),
      is("Nod"));

    assertThat("barcode is taken from item",
      updatedLoan.getJsonObject("item").getString("barcode"),
      is("565578437802"));
  }

  @Test
  public void canSearchByUserId()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    UUID firstUserId = UUID.randomUUID();
    UUID secondUserId = UUID.randomUUID();

    String queryTemplate = loansUrl() + "?query=userId=%s";

    createLoan(LoanRequest.create().put("userId", firstUserId.toString()));
    createLoan(LoanRequest.create().put("userId", firstUserId.toString()));
    createLoan(LoanRequest.create().put("userId", firstUserId.toString()));
    createLoan(LoanRequest.create().put("userId", firstUserId.toString()));
    createLoan(LoanRequest.create().put("userId", secondUserId.toString()));
    createLoan(LoanRequest.create().put("userId", secondUserId.toString()));
    createLoan(LoanRequest.create().put("userId", secondUserId.toString()));

    CompletableFuture<JsonResponse> firstUserSearchCompleted = new CompletableFuture<>();
    CompletableFuture<JsonResponse> secondUserSeatchCompleted = new CompletableFuture<>();

    client.get(String.format(queryTemplate, firstUserId), APITestSuite.TENANT_ID,
      ResponseHandler.json(firstUserSearchCompleted));

    client.get(String.format(queryTemplate, secondUserId), APITestSuite.TENANT_ID,
      ResponseHandler.json(secondUserSeatchCompleted));

    JsonResponse firstPageResponse = firstUserSearchCompleted.get(5, TimeUnit.SECONDS);
    JsonResponse secondPageResponse = secondUserSeatchCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get loans for first user: %s",
      firstPageResponse.getBody()),
      firstPageResponse.getStatusCode(), is(200));

    assertThat(String.format("Failed to get loans for second user: %s",
      secondPageResponse.getBody()),
      secondPageResponse.getStatusCode(), is(200));

    JsonObject firstPage = firstPageResponse.getJson();
    JsonObject secondPage = secondPageResponse.getJson();

    JsonArray firstPageLoans = firstPage.getJsonArray("loans");
    JsonArray secondPageLoans = secondPage.getJsonArray("loans");

    assertThat(firstPageLoans.size(), is(4));
    assertThat(firstPage.getInteger("totalRecords"), is(4));

    assertThat(secondPageLoans.size(), is(3));
    assertThat(secondPage.getInteger("totalRecords"), is(3));
  }

  @Test
  public void canFilterByLoanStatus()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    UUID userId = UUID.randomUUID();

    String queryTemplate = "userId=\"%s\" and status.name=\"%s\"";

    createLoan(LoanRequest.loanRequest(userId, "Open"));
    createLoan(LoanRequest.loanRequest(userId, "Open"));
    createLoan(LoanRequest.loanRequest(userId, "Closed"));
    createLoan(LoanRequest.loanRequest(userId, "Closed"));
    createLoan(LoanRequest.loanRequest(userId, "Closed"));
    createLoan(LoanRequest.loanRequest(userId, "Closed"));

    CompletableFuture<JsonResponse> openSearchComppleted = new CompletableFuture<>();
    CompletableFuture<JsonResponse> closedSearchCompleted = new CompletableFuture<>();

    client.get(loansUrl(),
      "query=" + URLEncoder.encode(String.format(queryTemplate, userId, "Open"), "UTF-8"),
      APITestSuite.TENANT_ID, ResponseHandler.json(openSearchComppleted));

    client.get(loansUrl(),
      "query=" + URLEncoder.encode(String.format(queryTemplate, userId, "Closed"), "UTF-8"),
      APITestSuite.TENANT_ID, ResponseHandler.json(closedSearchCompleted));

    JsonResponse openLoansResponse = openSearchComppleted.get(5, TimeUnit.SECONDS);
    JsonResponse closedLoansResponse = closedSearchCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get open loans: %s",
      openLoansResponse.getBody()),
      openLoansResponse.getStatusCode(), is(200));

    assertThat(String.format("Failed to get closed loans: %s",
      closedLoansResponse.getBody()),
      closedLoansResponse.getStatusCode(), is(200));

    JsonObject openLoans = openLoansResponse.getJson();
    JsonObject closedLoans = closedLoansResponse.getJson();

    JsonArray firstPageLoans = openLoans.getJsonArray("loans");
    JsonArray secondPageLoans = closedLoans.getJsonArray("loans");

    assertThat(firstPageLoans.size(), is(2));
    assertThat(openLoans.getInteger("totalRecords"), is(2));

    assertThat(secondPageLoans.size(), is(4));
    assertThat(closedLoans.getInteger("totalRecords"), is(4));
  }

  @Test
  public void canDeleteALoan()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    createLoan(LoanRequest.create(id, UUID.randomUUID(), UUID.randomUUID(),
      DateTime.now(), "Open"));

    CompletableFuture<TextResponse> deleteCompleted = new CompletableFuture<>();

    client.delete(loansUrl(String.format("/%s", id)),
      APITestSuite.TENANT_ID, ResponseHandler.text(deleteCompleted));

    TextResponse deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(loansUrl(String.format("/%s", id)),
      APITestSuite.TENANT_ID, ResponseHandler.empty(getCompleted));

    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  private JsonResponse getById(UUID id)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    URL getInstanceUrl = loansUrl(String.format("/%s", id));

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

    return createResource(loanRequest, loansUrl(),"loan");
  }

  private IndividualResource createItem(JsonObject itemRequest)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    return createResource(itemRequest, itemsUrl(), "item");
  }

  private IndividualResource createResource(
    JsonObject request,
    URL url,
    String resourceName)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(url, request, APITestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create %s: %s", resourceName, response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    return new IndividualResource(response);
  }

  private static URL itemsUrl()
    throws MalformedURLException {

    return APITestSuite.viaOkapiModuleUrl("/item-storage/items");
  }

  private static URL loansUrl()
    throws MalformedURLException {

    return loansUrl("");
  }

  private static URL loansUrl(String subPath)
    throws MalformedURLException {

    return APITestSuite.circulationModuleUrl("/circulation/loans" + subPath);
  }

  private void deleteAllLoans()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    deleteAll(loansUrl());
  }

  private void deleteAllItems()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    deleteAll(itemsUrl());
  }

  private void deleteAll(URL collectionResourceUrl)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> deleteAllFinished = new CompletableFuture<>();

    client.delete(collectionResourceUrl, APITestSuite.TENANT_ID,
      ResponseHandler.empty(deleteAllFinished));

    Response response = deleteAllFinished.get(5, TimeUnit.SECONDS);

    assertThat("WARNING!!!!! Delete all resources preparation failed",
      response.getStatusCode(), is(204));
  }
}
