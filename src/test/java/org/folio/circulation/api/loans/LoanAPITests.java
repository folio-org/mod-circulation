package org.folio.circulation.api.loans;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.APITestSuite;
import org.folio.circulation.api.support.APITests;
import org.folio.circulation.api.support.builders.ItemRequestBuilder;
import org.folio.circulation.api.support.builders.LoanRequestBuilder;
import org.folio.circulation.api.support.builders.UserRequestBuilder;
import org.folio.circulation.api.support.http.ResourceClient;
import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.folio.circulation.api.support.fixtures.UserRequestExamples.basedUponJessicaPontefract;
import static org.folio.circulation.api.support.fixtures.UserRequestExamples.basedUponStevenJones;
import static org.folio.circulation.api.support.http.InterfaceUrls.loansUrl;
import static org.folio.circulation.api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class LoanAPITests extends APITests {
  private final ResourceClient loansStorageClient = ResourceClient.forLoansStorage(client);

  @Test
  public void canCreateALoan()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    UUID userId = usersClient.create(new UserRequestBuilder()).getId();
    UUID proxyUserId = UUID.randomUUID();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, DateTimeZone.UTC);

    IndividualResource response = loansClient.create(new LoanRequestBuilder()
      .withId(id)
      .withUserId(userId)
      .withProxyUserId(proxyUserId)
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate)
      .withStatus("Open"));

    JsonObject loan = response.getJson();

    assertThat("id does not match",
      loan.getString("id"), is(id.toString()));

    assertThat("user id does not match",
      loan.getString("userId"), is(userId.toString()));

    assertThat("proxy user id does not match",
      loan.getString("proxyUserId"), is(proxyUserId.toString()));

    assertThat("item id does not match",
      loan.getString("itemId"), is(itemId.toString()));

    assertThat("loan date does not match",
      loan.getString("loanDate"), is("2017-02-27T10:23:43.000Z"));

    assertThat("status is not open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action is not checkedout",
      loan.getString("action"), is("checkedout"));

    assertThat("last loan policy should be stored",
      loan.getString("loanPolicyId"), is(APITestSuite.canCirculateLoanPolicyId().toString()));

    assertThat("title is taken from instance",
      loan.getJsonObject("item").getString("title"),
      is("The Long Way to a Small, Angry Planet"));

    assertThat("barcode is taken from item",
      loan.getJsonObject("item").getString("barcode"),
      is("036000291452"));



    assertThat("has item status",
      loan.getJsonObject("item").containsKey("status"), is(true));

    assertThat("status is taken from item",
      loan.getJsonObject("item").getJsonObject("status").getString("name"),
      is("Checked out"));

    assertThat("due date does not match",
      loan.getString("dueDate"), isEquivalentTo(dueDate));

    assertThat("Should not have snapshot of item status, as current status is included",
      loan.containsKey("itemStatus"), is(false));

    JsonObject item = itemsClient.getById(itemId).getJson();

    assertThat("item status is not checked out",
      item.getJsonObject("status").getString("name"), is("Checked out"));

    assertThat("item status snapshot in storage is not checked out",
      loansStorageClient.getById(id).getJson().getString("itemStatus"),
      is("Checked out"));

  }

  @Test
  public void canCreateALoanWithoutStatus()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    UUID userId = usersClient.create(new UserRequestBuilder()).getId();

    IndividualResource response = loansClient.create(new LoanRequestBuilder()
      .withId(id)
      .withUserId(userId)
      .withItemId(itemId)
      .withNoStatus());

    JsonObject loan = response.getJson();

    assertThat("status is not open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action is not checkedout",
      loan.getString("action"), is("checkedout"));

    assertThat("last loan policy should be stored",
      loan.getString("loanPolicyId"), is(APITestSuite.canCirculateLoanPolicyId().toString()));

    assertThat("title is taken from item",
      loan.getJsonObject("item").containsKey("title"), is(true));

    assertThat("barcode is taken from item",
      loan.getJsonObject("item").containsKey("barcode"), is(true));

    assertThat("has item status",
      loan.getJsonObject("item").containsKey("status"), is(true));

    assertThat("status is taken from item",
      loan.getJsonObject("item").getJsonObject("status").getString("name"),
      is("Checked out"));

    JsonObject item = itemsClient.getById(itemId).getJson();

    assertThat("item status is not checked out",
      item.getJsonObject("status").getString("name"), is("Checked out"));

    assertThat("item status snapshot in storage is not checked out",
      loansStorageClient.getById(id).getJson().getString("itemStatus"),
      is("Checked out"));
  }

  @Test
  public void creatingAReturnedLoanDoesNotChangeItemStatus()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    UUID userId = usersClient.create(new UserRequestBuilder()).getId();

    IndividualResource response = loansClient.create(new LoanRequestBuilder()
      .withId(id)
      .withUserId(userId)
      .withItemId(itemId)
      .withLoanDate(new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC))
      .withReturnDate(new DateTime(2017, 3, 15, 11, 14, 36, DateTimeZone.UTC))
      .withStatus("Closed"));

    JsonObject loan = response.getJson();

    assertThat("id does not match",
      loan.getString("id"), is(id.toString()));

    assertThat("user id does not match",
      loan.getString("userId"), is(userId.toString()));

    assertThat("item id does not match",
      loan.getString("itemId"), is(itemId.toString()));

    assertThat("loan date does not match",
      loan.getString("loanDate"), is("2017-02-27T10:23:43.000Z"));

    assertThat("status is not closed",
      loan.getJsonObject("status").getString("name"), is("Closed"));

    assertThat("action is not checkedin",
      loan.getString("action"), is("checkedin"));

    assertThat("Should not have snapshot of item status, as current status is included",
      loan.containsKey("itemStatus"), is(false));

    JsonObject item = itemsClient.getById(itemId).getJson();

    assertThat("item status is not available",
      item.getJsonObject("status").getString("name"), is("Available"));

    assertThat("item status snapshot in storage is not available",
      loansStorageClient.getById(id).getJson().getString("itemStatus"),
      is("Available"));
  }

  @Test
  public void canGetALoanById()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet(
      itemBuilder -> itemBuilder
        .withBarcode("036000291452"))
      .getId();

    UUID userId = usersClient.create(new UserRequestBuilder()).getId();
    UUID proxyUserId = UUID.randomUUID();

    DateTime dueDate = new DateTime(2016, 11, 15, 8, 26, 53, DateTimeZone.UTC);

    loansClient.create(new LoanRequestBuilder()
      .withId(id)
      .withUserId(userId)
      .withProxyUserId(proxyUserId)
      .withItemId(itemId)
      .withLoanDate(new DateTime(2016, 10, 15, 8, 26, 53, DateTimeZone.UTC))
      .withDueDate(dueDate)
      .withStatus("Open"));

    Response getResponse = loansClient.getById(id);

    assertThat(String.format("Failed to get loan: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject loan = getResponse.getJson();

    assertThat("id does not match",
      loan.getString("id"), is(id.toString()));

    assertThat("user id does not match",
      loan.getString("userId"), is(userId.toString()));

    assertThat("proxy user id does not match",
      loan.getString("proxyUserId"), is(proxyUserId.toString()));

    assertThat("item id does not match",
      loan.getString("itemId"), is(itemId.toString()));

    assertThat("loan date does not match",
      loan.getString("loanDate"), is("2016-10-15T08:26:53.000Z"));

    assertThat("due date does not match",
      loan.getString("dueDate"), isEquivalentTo(dueDate));

    assertThat("status is not open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action is not checkedout",
      loan.getString("action"), is("checkedout"));

    assertThat("last loan policy should be stored",
      loan.getString("loanPolicyId"), is(APITestSuite.canCirculateLoanPolicyId().toString()));

    assertThat("title is taken from item",
      loan.getJsonObject("item").getString("title"),
      is("The Long Way to a Small, Angry Planet"));

    assertThat("barcode is taken from item",
      loan.getJsonObject("item").getString("barcode"),
      is("036000291452"));

    assertThat("has item status",
      loan.getJsonObject("item").containsKey("status"), is(true));

    assertThat("status is taken from item",
      loan.getJsonObject("item").getJsonObject("status").getString("name"),
      is("Checked out"));

    assertThat("has item location",
      loan.getJsonObject("item").containsKey("location"), is(true));

    assertThat("location is taken from holding",
      loan.getJsonObject("item").getJsonObject("location").getString("name"),
      is("Main Library"));

    assertThat("Should not have snapshot of item status, as current status is included",
      loan.containsKey("itemStatus"), is(false));
  }

  @Test
  public void loanFoundByIdDoesNotProvideItemInformationForUnknownItem()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    UUID itemId = itemsFixture.basedUponNod().getId();

    UUID id = loansClient.create(new LoanRequestBuilder()
      .withItemId(itemId))
      .getId();

    itemsClient.delete(itemId);

    Response getResponse = loansClient.getById(id);

    assertThat(String.format("Failed to get loan: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject loan = getResponse.getJson();

    assertThat("should be no item information available",
      loan.containsKey("item"), is(false));
  }

  @Test
  public void barcodeIsNotIncludedWhenItemDoesNotHaveOne()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet(
      ItemRequestBuilder::withNoBarcode)
      .getId();

    UUID userId = usersClient.create(new UserRequestBuilder()).getId();

    loansClient.create(new LoanRequestBuilder()
      .withId(id)
      .withUserId(userId)
      .withItemId(itemId)
      .withLoanDate(new DateTime(2016, 10, 15, 8, 26, 53, DateTimeZone.UTC))
      .withStatus("Open"));

    Response getResponse = loansClient.getById(id);

    assertThat(String.format("Failed to get loan: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject loan = getResponse.getJson();

    assertThat("barcode is not taken from item",
      loan.getJsonObject("item").containsKey("barcode"), is(false));
  }

  @Test
  public void loanNotFoundForUnknownId()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    Response getResponse = loansClient.getById(UUID.randomUUID());

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void canCompleteALoanByReturningTheItem()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    DateTime loanDate = new DateTime(2017, 3, 1, 13, 25, 46, DateTimeZone.UTC);

    IndividualResource loan = loansClient.create(new LoanRequestBuilder()
      .withLoanDate(loanDate)
      .withItem(itemsFixture.basedUponNod()));

    JsonObject returnedLoan = loan.copyJson();

    returnedLoan
      .put("status", new JsonObject().put("name", "Closed"))
      .put("action", "checkedin")
      .put("returnDate", new DateTime(2017, 3, 5, 14, 23, 41, DateTimeZone.UTC)
        .toString(ISODateTimeFormat.dateTime()));

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(loansUrl(String.format("/%s", loan.getId())), returnedLoan,
      ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to update loan: %s", putResponse.getBody()),
      putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response updatedLoanResponse = loansClient.getById(loan.getId());

    JsonObject updatedLoan = updatedLoanResponse.getJson();

    assertThat(updatedLoan.getString("returnDate"),
      is("2017-03-05T14:23:41.000Z"));

    assertThat("status is not closed",
      updatedLoan.getJsonObject("status").getString("name"), is("Closed"));

    assertThat("action is not checkedin",
      updatedLoan.getString("action"), is("checkedin"));

    assertThat("title is taken from item",
      updatedLoan.getJsonObject("item").getString("title"),
      is("Nod"));

    assertThat("barcode is taken from item",
      updatedLoan.getJsonObject("item").getString("barcode"),
      is("565578437802"));

    assertThat("Should not have snapshot of item status, as current status is included",
      updatedLoan.containsKey("itemStatus"), is(false));

    JsonObject item = itemsClient.getById(itemsFixture.basedUponNod().getId()).getJson();

    assertThat("item status is not available",
      item.getJsonObject("status").getString("name"), is("Available"));

    assertThat("item status snapshot in storage is not Available",
      loansStorageClient.getById(loan.getId()).getJson().getString("itemStatus"),
      is("Available"));
  }

  @Test
  public void canRenewALoanByExtendingTheDueDate()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    DateTime loanDate = new DateTime(2017, 3, 1, 13, 25, 46, DateTimeZone.UTC);

    UUID itemId = itemsFixture.basedUponNod().getId();

    IndividualResource loan = loansClient.create(new LoanRequestBuilder()
      .withLoanDate(loanDate)
      .withItemId(itemId)
      .withDueDate(loanDate.plus(Period.days(14))));

    JsonObject renewedLoan = loan.copyJson();

    DateTime dueDate = DateTime.parse(renewedLoan.getString("dueDate"));
    DateTime newDueDate = dueDate.plus(Period.days(14));

    renewedLoan
      .put("action", "renewed")
      .put("dueDate", newDueDate.toString(ISODateTimeFormat.dateTime()))
      .put("renewalCount", 1);

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(loansUrl(String.format("/%s", loan.getId())), renewedLoan,
      ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to update loan: %s", putResponse.getBody()),
      putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response updatedLoanResponse = loansClient.getById(loan.getId());

    JsonObject updatedLoan = updatedLoanResponse.getJson();

    assertThat("status is not open",
      updatedLoan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action is not renewed",
      updatedLoan.getString("action"), is("renewed"));

    assertThat("should not contain a return date",
      updatedLoan.containsKey("returnDate"), is(false));

    assertThat("due date does not match",
      updatedLoan.getString("dueDate"), isEquivalentTo(newDueDate));

    assertThat("renewal count is not 1",
      updatedLoan.getInteger("renewalCount"), is(1));

    assertThat("title is taken from item",
      updatedLoan.getJsonObject("item").getString("title"),
      is("Nod"));

    assertThat("barcode is taken from item",
      updatedLoan.getJsonObject("item").getString("barcode"),
      is("565578437802"));

    assertThat("Should not have snapshot of item status, as current status is included",
      updatedLoan.containsKey("itemStatus"), is(false));

    JsonObject item = itemsClient.getById(itemId).getJson();

    assertThat("item status is not checked out",
      item.getJsonObject("status").getString("name"), is("Checked out"));

    assertThat("item status snapshot in storage is not checked out",
      loansStorageClient.getById(loan.getId()).getJson().getString("itemStatus"),
      is("Checked out"));
  }

  @Test
  public void updatingACurrentLoanDoesNotChangeItemStatus()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    DateTime loanDate = new DateTime(2017, 3, 1, 13, 25, 46, 232, DateTimeZone.UTC);

    UUID itemId = itemsFixture.basedUponNod().getId();

    IndividualResource loan = loansClient.create(new LoanRequestBuilder()
      .withLoanDate(loanDate)
      .withItemId(itemId));

    JsonObject item = itemsClient.getById(itemId).getJson();

    assertThat("item status is not checked out",
      item.getJsonObject("status").getString("name"), is("Checked out"));

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(loansUrl(String.format("/%s", loan.getId())),
      loan.getJson().copy(),
      ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to update loan: %s", putResponse.getBody()),
      putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    JsonObject changedItem = itemsClient.getById(itemId).getJson();

    assertThat("item status is not checked out",
      changedItem.getJsonObject("status").getString("name"), is("Checked out"));

    Response loanFromStorage = loansStorageClient.getById(loan.getId());

    assertThat("item status snapshot in storage is not checked out",
      loanFromStorage.getJson().getString("itemStatus"),
      is("Checked out"));
  }

  @Test
  public void loanInCollectionDoesNotProvideItemInformationForUnknownItem()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    UUID itemId = itemsFixture.basedUponNod().getId();

    loansClient.create(new LoanRequestBuilder()
      .withItemId(itemId));

    itemsClient.delete(itemId);

    CompletableFuture<Response> pageCompleted = new CompletableFuture<>();

    client.get(loansUrl(),
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

    loansClient.create(new LoanRequestBuilder()
      .withItem(itemsFixture.basedUponSmallAngryPlanet()));

    loansClient.create(new LoanRequestBuilder()
      .withItem(itemsFixture.basedUponNod()));

    loansClient.create(new LoanRequestBuilder()
      .withItem(itemsFixture.basedUponSmallAngryPlanet()));

    loansClient.create(new LoanRequestBuilder()
      .withItem(itemsFixture.basedUponTemeraire()));

    loansClient.create(new LoanRequestBuilder()
      .withItem(itemsFixture.basedUponUprooted()));

    loansClient.create(new LoanRequestBuilder()
      .withItem(itemsFixture.basedUponNod()));

    loansClient.create(new LoanRequestBuilder()
      .withItem(itemsFixture.basedUponInterestingTimes()));

    CompletableFuture<Response> firstPageCompleted = new CompletableFuture<>();
    CompletableFuture<Response> secondPageCompleted = new CompletableFuture<>();

    client.get(loansUrl() + "?limit=4",
      ResponseHandler.json(firstPageCompleted));

    client.get(loansUrl() + "?limit=4&offset=4",
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

    firstPageLoans.forEach(this::loanHasExpectedProperties);
    secondPageLoans.forEach(this::loanHasExpectedProperties);

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

    UUID firstUserId = usersClient.create(basedUponStevenJones()).getId();
    UUID secondUserId = usersClient.create(basedUponJessicaPontefract()).getId();

    String queryTemplate = loansUrl() + "?query=userId=%s";

    loansClient.create(new LoanRequestBuilder()
      .withItem(itemsFixture.basedUponSmallAngryPlanet())
      .withUserId(firstUserId));

    loansClient.create(new LoanRequestBuilder()
      .withItem(itemsFixture.basedUponNod())
      .withUserId(firstUserId));

    loansClient.create(new LoanRequestBuilder()
      .withItem(itemsFixture.basedUponSmallAngryPlanet())
      .withUserId(firstUserId));

    loansClient.create(new LoanRequestBuilder()
      .withItem(itemsFixture.basedUponTemeraire())
      .withUserId(firstUserId));

    loansClient.create(new LoanRequestBuilder()
      .withItem(itemsFixture.basedUponUprooted())
      .withUserId(secondUserId));

    loansClient.create(new LoanRequestBuilder()
      .withItem(itemsFixture.basedUponNod())
      .withUserId(secondUserId));

    loansClient.create(new LoanRequestBuilder().withItem(
      itemsFixture.basedUponInterestingTimes())
      .withUserId(secondUserId));

    CompletableFuture<Response> firstUserSearchCompleted = new CompletableFuture<>();
    CompletableFuture<Response> secondUserSeatchCompleted = new CompletableFuture<>();

    client.get(String.format(queryTemplate, firstUserId),
      ResponseHandler.json(firstUserSearchCompleted));

    client.get(String.format(queryTemplate, secondUserId),
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

    firstPageLoans.forEach(this::loanHasExpectedProperties);
    secondPageLoans.forEach(this::loanHasExpectedProperties);

    assertThat(countOfDistinctTitles(firstPageLoans), is(greaterThan(1)));
    assertThat(countOfDistinctTitles(secondPageLoans), is(greaterThan(1)));
  }

  @Test
  public void canFindNoResultsFromSearch()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    UUID firstUserId = UUID.randomUUID();
    UUID secondUserId = UUID.randomUUID();

    String queryTemplate = loansUrl() + "?query=userId=%s";

    CompletableFuture<Response> firstUserSearchCompleted = new CompletableFuture<>();
    CompletableFuture<Response> secondUserSeatchCompleted = new CompletableFuture<>();

    client.get(String.format(queryTemplate, firstUserId),
      ResponseHandler.json(firstUserSearchCompleted));

    client.get(String.format(queryTemplate, secondUserId),
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

    assertThat(firstPageLoans.size(), is(0));
    assertThat(firstPage.getInteger("totalRecords"), is(0));

    assertThat(secondPageLoans.size(), is(0));
    assertThat(secondPage.getInteger("totalRecords"), is(0));
  }

  @Test
  public void canFilterByLoanStatus()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    UUID userId = usersClient.create(new UserRequestBuilder()).getId();

    String queryTemplate = "userId=\"%s\" and status.name=\"%s\"";

    loansClient.create(new LoanRequestBuilder()
      .withUserId(userId)
      .withStatus("Open")
      .withItem(itemsFixture.basedUponSmallAngryPlanet())
      .withRandomPastLoanDate());

    loansClient.create(new LoanRequestBuilder()
      .withUserId(userId)
      .withStatus("Open")
      .withItem(itemsFixture.basedUponNod())
      .withRandomPastLoanDate());

    loansClient.create(new LoanRequestBuilder()
      .withUserId(userId)
      .withItem(
        itemsFixture.basedUponNod())
      .withStatus("Closed")
      .withRandomPastLoanDate());

    loansClient.create(new LoanRequestBuilder()
      .withUserId(userId)
      .withStatus("Closed")
      .withItem(itemsFixture.basedUponTemeraire())
      .withRandomPastLoanDate());

    loansClient.create(new LoanRequestBuilder()
      .withUserId(userId)
      .withStatus("Closed")
      .withItem(itemsFixture.basedUponUprooted())
      .withRandomPastLoanDate());

    loansClient.create(new LoanRequestBuilder()
      .withUserId(userId)
      .withStatus("Closed")
      .withItem(itemsFixture.basedUponInterestingTimes())
      .withRandomPastLoanDate());

    CompletableFuture<Response> openSearchComppleted = new CompletableFuture<>();
    CompletableFuture<Response> closedSearchCompleted = new CompletableFuture<>();

    client.get(loansUrl(),
      "query=" + URLEncoder.encode(String.format(queryTemplate, userId, "Open"), "UTF-8"),
      ResponseHandler.json(openSearchComppleted));

    client.get(loansUrl(),
      "query=" + URLEncoder.encode(String.format(queryTemplate, userId, "Closed"), "UTF-8"),
      ResponseHandler.json(closedSearchCompleted));

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

    openLoans.forEach(this::loanHasExpectedProperties);

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

    UUID id = loansClient.create(new LoanRequestBuilder()
      .withItem(itemsFixture.basedUponNod()))
      .getId();

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    client.delete(loansUrl(String.format("/%s", id)),
      ResponseHandler.any(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(loansUrl(String.format("/%s", id)),
      ResponseHandler.any(getCompleted));

    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  private void loanHasExpectedProperties(JsonObject loan) {
    hasProperty("id", loan, "loan");
    hasProperty("userId", loan, "loan");
    hasProperty("itemId", loan, "loan");
    hasProperty("loanDate", loan, "loan");
    hasProperty("status", loan, "loan");
    hasProperty("action", loan, "loan");
    hasProperty("item", loan, "loan");

    JsonObject item = loan.getJsonObject("item");

    hasProperty("title", item, "item");
    hasProperty("barcode", item, "item");
    hasProperty("status", item, "item");

    assertThat("Should not have snapshot of item status, as current status is included",
      loan.containsKey("itemStatus"), is(false));
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

  private List<JsonObject> getLoans(JsonObject page) {
    return JsonArrayHelper.toList(page.getJsonArray("loans"));
  }

  private static JsonObject findLoanByItemId(List<JsonObject> loans, UUID itemId) {
    return loans.stream()
      .filter(record -> record.getString("itemId").equals(itemId.toString()))
      .findFirst()
      .get();
  }
}
