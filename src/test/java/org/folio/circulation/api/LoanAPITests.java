package org.folio.circulation.api;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.support.ItemRequestExamples;
import org.folio.circulation.api.support.LoanRequestBuilder;
import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.http.client.HttpClient;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.Matchers.greaterThan;
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
    MalformedURLException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();
    UUID itemId = createItem(ItemRequestExamples.smallAngryPlanet()).getId();
    UUID userId = UUID.randomUUID();

    IndividualResource response = createLoan(new LoanRequestBuilder()
      .withId(id)
      .withUserId(userId)
      .withItemId(itemId)
      .withLoanDate(new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC))
      .withStatus("Open")
      .create());

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

    JsonObject item = getItemById(itemId).getJson();

    assertThat("item status is not checked out",
      item.getJsonObject("status").getString("name"), is("Checked Out"));
  }

  @Test
  public void canGetALoanById()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();
    UUID itemId = createItem(ItemRequestExamples.smallAngryPlanet()).getId();
    UUID userId = UUID.randomUUID();

    createLoan(new LoanRequestBuilder()
      .withId(id)
      .withUserId(userId)
      .withItemId(itemId)
      .withLoanDate(new DateTime(2016, 10, 15, 8, 26, 53, DateTimeZone.UTC))
      .withStatus("Open")
      .create());

    Response getResponse = getById(id);

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

    UUID itemId = createItem(ItemRequestExamples.nod()).getId();

    UUID id = createLoan(new LoanRequestBuilder()
      .withItemId(itemId)
      .create()).getId();

    deleteItem(itemId);

    Response getResponse = getById(id);

    assertThat(String.format("Failed to get loan: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject loan = getResponse.getJson();

    assertThat("should be no item information available",
      loan.containsKey("item"), is(false));
  }

  @Test
  public void loanNotFoundForUnknownId()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    Response getResponse = getById(UUID.randomUUID());

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void canCompleteALoanByReturningTheItem()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    DateTime loanDate = new DateTime(2017, 3, 1, 13, 25, 46, 232, DateTimeZone.UTC);

    UUID itemId = createItem(ItemRequestExamples.nod()).getId();

    IndividualResource loan = createLoan(new LoanRequestBuilder()
      .withLoanDate(loanDate)
      .withItemId(itemId)
      .create());

    JsonObject returnedLoan = loan.copyJson();

    returnedLoan
      .put("status", new JsonObject().put("name", "Closed"))
      .put("returnDate", new DateTime(2017, 3, 5, 14, 23, 41, DateTimeZone.UTC)
        .toString(ISODateTimeFormat.dateTime()));

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(loansUrl(String.format("/%s", loan.getId())), returnedLoan,
      APITestSuite.TENANT_ID, ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to update loan: %s", putResponse.getBody()),
      putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response updatedLoanResponse = getById(loan.getId());

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

    JsonObject item = getItemById(itemId).getJson();

    assertThat("item status is not available",
      item.getJsonObject("status").getString("name"), is("Available"));
  }

  @Test
  public void updatingACurrentLoanDoesNotChangeItemStatus()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    DateTime loanDate = new DateTime(2017, 3, 1, 13, 25, 46, 232, DateTimeZone.UTC);

    UUID itemId = createItem(ItemRequestExamples.nod()).getId();

    IndividualResource loan = createLoan(new LoanRequestBuilder()
      .withLoanDate(loanDate)
      .withItemId(itemId)
      .create());

    JsonObject item = getItemById(itemId).getJson();

    assertThat("item status is not checked out",
      item.getJsonObject("status").getString("name"), is("Checked Out"));

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(loansUrl(String.format("/%s", loan.getId())),
      loan.getJson().copy(), APITestSuite.TENANT_ID,
      ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to update loan: %s", putResponse.getBody()),
      putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    JsonObject changedItem = getItemById(itemId).getJson();

    assertThat("item status is not checked out",
      changedItem.getJsonObject("status").getString("name"), is("Checked Out"));
  }

  @Test
  public void loanInCollectionDoesNotProvideItemInformationForUnknownItem()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    UUID itemId = createItem(ItemRequestExamples.nod()).getId();

    createLoan(new LoanRequestBuilder()
      .withItemId(itemId)
      .create());

    deleteItem(itemId);

    CompletableFuture<Response> pageCompleted = new CompletableFuture<>();

    client.get(loansUrl(), APITestSuite.TENANT_ID,
      ResponseHandler.json(pageCompleted));

    Response pageResponse = pageCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get page of loans: %s",
      pageResponse.getBody()),
      pageResponse.getStatusCode(), is(200));

    JsonObject firstPage = pageResponse.getJson();

    JsonObject loan = getLoans(firstPage).get(0);

    assertThat("should be no item information available",
      loan.containsKey("item"), is(false));
  }

  @Test
  public void canPageLoans()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    createLoan(new LoanRequestBuilder().withItemId(
      createItem(ItemRequestExamples.smallAngryPlanet()).getId()).create());

    createLoan(new LoanRequestBuilder().withItemId(
      createItem(ItemRequestExamples.nod()).getId()).create());

    createLoan(new LoanRequestBuilder().withItemId(createItem(
      ItemRequestExamples.smallAngryPlanet("764853217647")).getId())
      .create());

    createLoan(new LoanRequestBuilder().withItemId(
      createItem(ItemRequestExamples.temeraire()).getId()).create());

    createLoan(new LoanRequestBuilder().withItemId(
      createItem(ItemRequestExamples.uprooted()).getId()).create());

    createLoan(new LoanRequestBuilder().withItemId(
      createItem(ItemRequestExamples.nod("656450654364")).getId())
      .create());

    createLoan(new LoanRequestBuilder().withItemId(
      createItem(ItemRequestExamples.interestingTimes()).getId()).create());

    CompletableFuture<Response> firstPageCompleted = new CompletableFuture<>();
    CompletableFuture<Response> secondPageCompleted = new CompletableFuture<>();

    client.get(loansUrl() + "?limit=4", APITestSuite.TENANT_ID,
      ResponseHandler.json(firstPageCompleted));

    client.get(loansUrl() + "?limit=4&offset=4", APITestSuite.TENANT_ID,
      ResponseHandler.json(secondPageCompleted));

    Response firstPageResponse = firstPageCompleted.get(5, TimeUnit.SECONDS);
    Response secondPageResponse = secondPageCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get first page of loans: %s",
      firstPageResponse.getBody()),
      firstPageResponse.getStatusCode(), is(200));

    assertThat(String.format("Failed to get second page of loans: %s",
      secondPageResponse.getBody()),
      secondPageResponse.getStatusCode(), is(200));

    JsonObject firstPage = firstPageResponse.getJson();
    JsonObject secondPage = secondPageResponse.getJson();

    List<JsonObject> firstPageLoans = getLoans(firstPage);
    List<JsonObject> secondPageLoans = getLoans(secondPage);

    assertThat(firstPageLoans.size(), is(4));
    assertThat(firstPage.getInteger("totalRecords"), is(7));

    assertThat(secondPageLoans.size(), is(3));
    assertThat(secondPage.getInteger("totalRecords"), is(7));

    firstPageLoans.forEach(loan -> loanHasExpectedProperties(loan));
    secondPageLoans.forEach(loan -> loanHasExpectedProperties(loan));

    assertThat(countOfDistinctTitles(firstPageLoans), is(greaterThan(1)));
    assertThat(countOfDistinctTitles(secondPageLoans), is(greaterThan(1)));
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

    createLoan(new LoanRequestBuilder()
      .withItem(createItem(ItemRequestExamples.smallAngryPlanet()))
      .withUserId(firstUserId)
      .create());

    createLoan(new LoanRequestBuilder()
      .withItem(createItem(ItemRequestExamples.nod()))
      .withUserId(firstUserId)
      .create());

    createLoan(new LoanRequestBuilder()
      .withItem(
        createItem(ItemRequestExamples.smallAngryPlanet("764853217647")))
      .withUserId(firstUserId)
      .create());

    createLoan(new LoanRequestBuilder().withItem(
      createItem(ItemRequestExamples.temeraire()))
      .withUserId(firstUserId)
      .create());

    createLoan(new LoanRequestBuilder()
      .withItem(createItem(ItemRequestExamples.uprooted()))
      .withUserId(secondUserId)
      .create());

    createLoan(new LoanRequestBuilder()
      .withItem(createItem(ItemRequestExamples.nod("656450654364")))
      .withUserId(secondUserId)
      .create());

    createLoan(new LoanRequestBuilder().withItem(
      createItem(ItemRequestExamples.interestingTimes()))
      .withUserId(secondUserId)
      .create());

    CompletableFuture<Response> firstUserSearchCompleted = new CompletableFuture<>();
    CompletableFuture<Response> secondUserSeatchCompleted = new CompletableFuture<>();

    client.get(String.format(queryTemplate, firstUserId), APITestSuite.TENANT_ID,
      ResponseHandler.json(firstUserSearchCompleted));

    client.get(String.format(queryTemplate, secondUserId), APITestSuite.TENANT_ID,
      ResponseHandler.json(secondUserSeatchCompleted));

    Response firstPageResponse = firstUserSearchCompleted.get(5, TimeUnit.SECONDS);
    Response secondPageResponse = secondUserSeatchCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get loans for first user: %s",
      firstPageResponse.getBody()),
      firstPageResponse.getStatusCode(), is(200));

    assertThat(String.format("Failed to get loans for second user: %s",
      secondPageResponse.getBody()),
      secondPageResponse.getStatusCode(), is(200));

    JsonObject firstPage = firstPageResponse.getJson();
    JsonObject secondPage = secondPageResponse.getJson();

    List<JsonObject> firstPageLoans = getLoans(firstPage);
    List<JsonObject> secondPageLoans = getLoans(secondPage);

    assertThat(firstPageLoans.size(), is(4));
    assertThat(firstPage.getInteger("totalRecords"), is(4));

    assertThat(secondPageLoans.size(), is(3));
    assertThat(secondPage.getInteger("totalRecords"), is(3));

    firstPageLoans.forEach(loan -> loanHasExpectedProperties(loan));
    secondPageLoans.forEach(loan -> loanHasExpectedProperties(loan));

    assertThat(countOfDistinctTitles(firstPageLoans), is(greaterThan(1)));
    assertThat(countOfDistinctTitles(secondPageLoans), is(greaterThan(1)));
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

    createLoan(new LoanRequestBuilder()
      .withUserId(userId)
      .withStatus("Open")
      .withItem(createItem(ItemRequestExamples.smallAngryPlanet()))
      .withRandomPastLoanDate().create());

    createLoan(new LoanRequestBuilder()
      .withUserId(userId)
      .withStatus("Open")
      .withItem(createItem(ItemRequestExamples.nod()))
      .withRandomPastLoanDate().create());

    createLoan(new LoanRequestBuilder()
      .withUserId(userId)
      .withItem(
        createItem(ItemRequestExamples.nod("764853217647")))
      .withStatus("Closed")
      .withRandomPastLoanDate().create());

    createLoan(new LoanRequestBuilder()
      .withUserId(userId)
      .withStatus("Closed")
      .withItem(createItem(ItemRequestExamples.temeraire()))
      .withRandomPastLoanDate().create());

    createLoan(new LoanRequestBuilder()
      .withUserId(userId)
      .withStatus("Closed")
      .withItem(createItem(ItemRequestExamples.uprooted()))
      .withRandomPastLoanDate().create());

    createLoan(new LoanRequestBuilder()
      .withUserId(userId)
      .withStatus("Closed")
      .withItem(createItem(ItemRequestExamples.interestingTimes()))
      .withRandomPastLoanDate().create());

    CompletableFuture<Response> openSearchComppleted = new CompletableFuture<>();
    CompletableFuture<Response> closedSearchCompleted = new CompletableFuture<>();

    client.get(loansUrl(),
      "query=" + URLEncoder.encode(String.format(queryTemplate, userId, "Open"), "UTF-8"),
      APITestSuite.TENANT_ID, ResponseHandler.json(openSearchComppleted));

    client.get(loansUrl(),
      "query=" + URLEncoder.encode(String.format(queryTemplate, userId, "Closed"), "UTF-8"),
      APITestSuite.TENANT_ID, ResponseHandler.json(closedSearchCompleted));

    Response openLoansResponse = openSearchComppleted.get(5, TimeUnit.SECONDS);
    Response closedLoansResponse = closedSearchCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get open loans: %s",
      openLoansResponse.getBody()),
      openLoansResponse.getStatusCode(), is(200));

    assertThat(String.format("Failed to get closed loans: %s",
      closedLoansResponse.getBody()),
      closedLoansResponse.getStatusCode(), is(200));

    JsonObject openLoansPage = openLoansResponse.getJson();
    JsonObject closedLoansPage = closedLoansResponse.getJson();

    List<JsonObject> openLoans = getLoans(openLoansPage);
    List<JsonObject> closedLoans = getLoans(closedLoansPage);

    assertThat(openLoans.size(), is(2));
    assertThat(openLoansPage.getInteger("totalRecords"), is(2));

    assertThat(closedLoans.size(), is(4));
    assertThat(closedLoansPage.getInteger("totalRecords"), is(4));

    openLoans.forEach(loan -> loanHasExpectedProperties(loan));

    closedLoans.forEach(loan -> {
        loanHasExpectedProperties(loan);
        hasProperty("returnDate", loan, "loan");
      }
    );

    assertThat(countOfDistinctTitles(openLoans), is(greaterThan(1)));
    assertThat(countOfDistinctTitles(closedLoans), is(greaterThan(1)));
  }

  @Test
  public void canDeleteALoan()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    UUID itemId = createItem(ItemRequestExamples.nod()).getId();

    UUID id = createLoan(new LoanRequestBuilder()
      .withItemId(itemId).create()).getId();

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    client.delete(loansUrl(String.format("/%s", id)),
      APITestSuite.TENANT_ID, ResponseHandler.any(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(loansUrl(String.format("/%s", id)),
      APITestSuite.TENANT_ID, ResponseHandler.any(getCompleted));

    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  private Response getById(UUID id)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    URL getInstanceUrl = loansUrl(String.format("/%s", id));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(getInstanceUrl, APITestSuite.TENANT_ID,
      ResponseHandler.any(getCompleted));

    return getCompleted.get(5, TimeUnit.SECONDS);
  }

  private Response getItemById(UUID id)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    URL getInstanceUrl = itemsUrl(String.format("/%s", id));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(getInstanceUrl, APITestSuite.TENANT_ID,
      ResponseHandler.any(getCompleted));

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

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(url, request, APITestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create %s: %s", resourceName, response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    return new IndividualResource(response);
  }

  private static URL itemsUrl()
    throws MalformedURLException {

    return itemsUrl("");
  }

  private static URL itemsUrl(String subPath)
    throws MalformedURLException {

    return APITestSuite.viaOkapiModuleUrl("/item-storage/items" + subPath);
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
      ResponseHandler.any(deleteAllFinished));

    Response response = deleteAllFinished.get(5, TimeUnit.SECONDS);

    assertThat("WARNING!!!!! Delete all resources preparation failed",
      response.getStatusCode(), is(204));
  }

  private void loanHasExpectedProperties(JsonObject loan) {
    hasProperty("id", loan, "loan");
    hasProperty("userId", loan, "loan");
    hasProperty("itemId", loan, "loan");
    hasProperty("loanDate", loan, "loan");
    hasProperty("status", loan, "loan");
    hasProperty("item", loan, "loan");

    JsonObject item = loan.getJsonObject("item");

    hasProperty("title", item, "loan");
    hasProperty("barcode", item, "loan");
  }

  private void hasProperty(String property, JsonObject resource, String type) {
    assertThat(String.format("%s should have an %s: %s",
      type, property, resource),
      resource.containsKey(property), is(true));
  }

  private Integer countOfDistinctTitles(List<JsonObject> loans) {
    return new Long(loans.stream()
      .map(loan -> loan.getJsonObject("item").getString("title"))
      .distinct()
      .count()).intValue();
  }

  private List<JsonObject> getLoans(JsonObject firstPage) {
    return JsonArrayHelper.toList(firstPage.getJsonArray("loans"));
  }

  private void deleteItem(UUID itemId)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> deleteFinished = new CompletableFuture<>();

    client.delete(itemsUrl(String.format("/%s", itemId)),
      APITestSuite.TENANT_ID, ResponseHandler.any(deleteFinished));

    Response response = deleteFinished.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to delete item %s", itemId),
      response.getStatusCode(), is(204));
  }
}
