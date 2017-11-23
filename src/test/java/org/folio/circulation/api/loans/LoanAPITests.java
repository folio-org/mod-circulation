package org.folio.circulation.api.loans;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.APITestSuite;
import org.folio.circulation.api.support.LoanRequestBuilder;
import org.folio.circulation.api.support.ResourceClient;
import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.folio.circulation.api.support.InterfaceUrls.loansUrl;
import static org.folio.circulation.api.support.ItemRequestExamples.*;
import static org.folio.circulation.api.support.TextDateTimeMatcher.isEquivalentTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class LoanAPITests {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final OkapiHttpClient client = APITestSuite.createClient(exception -> {
    log.error("Request to circulation module failed:", exception);
  });

  private final ResourceClient loansClient = ResourceClient.forLoans(client);
  private final ResourceClient loansStorageClient = ResourceClient.forLoansStorage(client);
  private final ResourceClient itemsClient = ResourceClient.forItems(client);
  private final ResourceClient holdingsClient = ResourceClient.forHoldings(client);

  @Before
  public void beforeEach()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    loansClient.deleteAll();
    itemsClient.deleteAll();
    holdingsClient.deleteAll();
  }

  @Test
  public void canCreateALoan()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsClient.create(basedUponSmallAngryPlanet()
      .withBarcode("036000291452")
      .withPermanentLocation(APITestSuite.mainLibraryLocationId()))
      .getId();

    UUID userId = UUID.randomUUID();
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

    assertThat("location is taken from item",
      loan.getJsonObject("item").getJsonObject("location").getString("name"),
      is("Main Library"));

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
  public void canCreateALoanForItemWithAPermanentLocation()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsClient.create(basedUponSmallAngryPlanet()
      .withPermanentLocation(APITestSuite.mainLibraryLocationId())
      .withNoTemporaryLocation())
      .getId();

    IndividualResource response = loansClient.create(new LoanRequestBuilder()
      .withId(id)
      .withItemId(itemId));

    JsonObject createdLoan = response.getJson();

    assertThat(String.format("created loan has an item location (%s)",
      createdLoan.encodePrettily()),
      createdLoan.getJsonObject("item").containsKey("location"), is(true));

    assertThat("location is taken from item when created",
      createdLoan.getJsonObject("item").getJsonObject("location").getString("name"),
      is("Main Library"));

    Response fetchResponse = loansClient.getById(id);

    assertThat(String.format("Failed to get loan: %s", fetchResponse.getBody()),
      fetchResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject fetchedLoan = fetchResponse.getJson();

    assertThat(String.format("fetched loan has an item location (%s)",
      fetchedLoan.encodePrettily()),
      fetchedLoan.getJsonObject("item").containsKey("location"), is(true));

    assertThat("location is taken from item when fetched",
      fetchedLoan.getJsonObject("item").getJsonObject("location").getString("name"),
      is("Main Library"));
  }

  @Test
  public void canCreateALoanForItemWithoutAPermanentLocation()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsClient.create(basedUponSmallAngryPlanet()
      .withNoPermanentLocation())
      .getId();

    IndividualResource response = loansClient.create(new LoanRequestBuilder()
      .withId(id)
      .withItemId(itemId));

    JsonObject createdLoan = response.getJson();

    assertThat(String.format("created loan has no item location (%s)",
      createdLoan.encodePrettily()),
      createdLoan.getJsonObject("item").containsKey("location"), is(false));

    Response fetchResponse = loansClient.getById(id);

    assertThat(String.format("Failed to get loan: %s", fetchResponse.getBody()),
      fetchResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject fetchedLoan = fetchResponse.getJson();

    assertThat(String.format("fetched loan has no item location (%s)",
      fetchedLoan.encodePrettily()),
      fetchedLoan.getJsonObject("item").containsKey("location"), is(false));
  }

  @Test
  public void canCreateALoanForItemWithATemporaryLocation()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsClient.create(basedUponSmallAngryPlanet()
      .withPermanentLocation(APITestSuite.mainLibraryLocationId())
      .withTemporaryLocation(APITestSuite.annexLocationId()))
      .getId();

    IndividualResource response = loansClient.create(new LoanRequestBuilder()
      .withId(id)
      .withItemId(itemId));

    JsonObject createdLoan = response.getJson();

    assertThat(String.format("created loan has an item location (%s)",
      createdLoan.encodePrettily()),
      createdLoan.getJsonObject("item").containsKey("location"), is(true));

    assertThat("temporary location is taken from item when created",
      createdLoan.getJsonObject("item").getJsonObject("location").getString("name"),
      is("Annex"));

    Response fetchResponse = loansClient.getById(id);

    assertThat(String.format("Failed to get loan: %s", fetchResponse.getBody()),
      fetchResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject fetchedLoan = fetchResponse.getJson();

    assertThat(String.format("fetched loan has an item location (%s)",
      fetchedLoan.encodePrettily()),
      fetchedLoan.getJsonObject("item").containsKey("location"), is(true));

    assertThat("temporary location is taken from item when fetched",
      fetchedLoan.getJsonObject("item").getJsonObject("location").getString("name"),
      is("Annex"));
  }

  @Test
  public void creatingAReturnedLoanDoesNotChangeItemStatus()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsClient.create(basedUponSmallAngryPlanet()).getId();

    UUID userId = UUID.randomUUID();

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

    UUID itemId = itemsClient.create(basedUponSmallAngryPlanet()
      .withBarcode("036000291452"))
      .getId();

    UUID userId = UUID.randomUUID();
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

    assertThat("location is taken from item",
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

    UUID itemId = itemsClient.create(basedUponNod()).getId();

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

    UUID itemId = itemsClient.create(basedUponSmallAngryPlanet()
      .withNoBarcode())
      .getId();

    UUID userId = UUID.randomUUID();

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

    UUID itemId = itemsClient.create(basedUponNod()).getId();

    IndividualResource loan = loansClient.create(new LoanRequestBuilder()
      .withLoanDate(loanDate)
      .withItemId(itemId));

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

    JsonObject item = itemsClient.getById(itemId).getJson();

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

    UUID itemId = itemsClient.create(basedUponNod()).getId();

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

    UUID itemId = itemsClient.create(basedUponNod()).getId();

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

    UUID itemId = itemsClient.create(basedUponNod()).getId();

    loansClient.create(new LoanRequestBuilder().withItemId(itemId));

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
      .withItemId(itemsClient.create(basedUponSmallAngryPlanet()).getId()));

    loansClient.create(new LoanRequestBuilder()
      .withItemId(itemsClient.create(basedUponNod()).getId()));

    loansClient.create(new LoanRequestBuilder()
      .withItemId(itemsClient.create(basedUponSmallAngryPlanet()).getId()));

    loansClient.create(new LoanRequestBuilder()
      .withItemId(itemsClient.create(basedUponTemeraire()).getId()));

    loansClient.create(new LoanRequestBuilder()
      .withItemId(itemsClient.create(basedUponUprooted()).getId()));

    loansClient.create(new LoanRequestBuilder()
      .withItemId(itemsClient.create(basedUponNod()).getId()));

    loansClient.create(new LoanRequestBuilder()
      .withItemId(itemsClient.create(basedUponInterestingTimes()).getId()));

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
  public void locationIsIncludedWhenGettingAllLoans()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    UUID permanentLocationItemId = itemsClient.create(basedUponSmallAngryPlanet()
      .withPermanentLocation(APITestSuite.mainLibraryLocationId())
      .withNoTemporaryLocation())
      .getId();

    loansClient.create(new LoanRequestBuilder()
      .withItemId(permanentLocationItemId));

    UUID temporaryLocationItemId = itemsClient.create(basedUponNod()
      .withPermanentLocation(APITestSuite.mainLibraryLocationId())
      .withTemporaryLocation(APITestSuite.annexLocationId()))
      .getId();

    loansClient.create(new LoanRequestBuilder()
      .withItemId(temporaryLocationItemId));

    UUID noLocationItemId = itemsClient.create(basedUponTemeraire()
      .withNoPermanentLocation()
      .withNoTemporaryLocation())
      .getId();

    loansClient.create(new LoanRequestBuilder()
      .withItemId(noLocationItemId));

    CompletableFuture<Response> getLoansCompleted = new CompletableFuture<>();

    client.get(loansUrl(), ResponseHandler.json(getLoansCompleted));

    Response loansResponse = getLoansCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get loans: %s",
      loansResponse.getBody()),
      loansResponse.getStatusCode(), is(200));

    JsonObject firstPage = loansResponse.getJson();

    List<JsonObject> loans = getLoans(firstPage);

    assertThat(loans.size(), is(3));
    assertThat(firstPage.getInteger("totalRecords"), is(3));

    loans.forEach(this::loanHasExpectedProperties);

    JsonObject loanWithPermanentLocation = findLoanByItemId(loans, permanentLocationItemId);
    JsonObject loanWithTemporaryLocation = findLoanByItemId(loans, temporaryLocationItemId);
    JsonObject loanWithNoLocation = findLoanByItemId(loans, noLocationItemId);

    assertThat(String.format("Permanent location should be included: %s",
      loanWithPermanentLocation.encodePrettily()),
      loanWithPermanentLocation.getJsonObject("item").containsKey("location"), is(true));

    assertThat(String.format("Permanent location should be included: %s",
      loanWithPermanentLocation.encodePrettily()),
      loanWithPermanentLocation.getJsonObject("item").getJsonObject("location").getString("name"),
      is("Main Library"));

    assertThat(String.format("Temporary location should be included: %s",
      loanWithPermanentLocation.encodePrettily()),
      loanWithTemporaryLocation.getJsonObject("item").containsKey("location"), is(true));

    assertThat(String.format("Temporary location should be included: %s",
      loanWithPermanentLocation.encodePrettily()),
      loanWithTemporaryLocation.getJsonObject("item").getJsonObject("location").getString("name"),
      is("Annex"));

    assertThat(String.format("No location should be included: %s",
      loanWithPermanentLocation.encodePrettily()),
      loanWithNoLocation.getJsonObject("item").containsKey("location"), is(false));
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

    loansClient.create(new LoanRequestBuilder()
      .withItem(itemsClient.create(basedUponSmallAngryPlanet()))
      .withUserId(firstUserId));

    loansClient.create(new LoanRequestBuilder()
      .withItem(itemsClient.create(basedUponNod()))
      .withUserId(firstUserId));

    loansClient.create(new LoanRequestBuilder()
      .withItem(itemsClient.create(basedUponSmallAngryPlanet()))
      .withUserId(firstUserId));

    loansClient.create(new LoanRequestBuilder()
      .withItem(itemsClient.create(basedUponTemeraire()))
      .withUserId(firstUserId));

    loansClient.create(new LoanRequestBuilder()
      .withItem(itemsClient.create(basedUponUprooted()))
      .withUserId(secondUserId));

    loansClient.create(new LoanRequestBuilder()
      .withItem(itemsClient.create(basedUponNod()))
      .withUserId(secondUserId));

    loansClient.create(new LoanRequestBuilder().withItem(
      itemsClient.create(basedUponInterestingTimes()))
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
  public void canFilterByLoanStatus()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    UUID userId = UUID.randomUUID();

    String queryTemplate = "userId=\"%s\" and status.name=\"%s\"";

    loansClient.create(new LoanRequestBuilder()
      .withUserId(userId)
      .withStatus("Open")
      .withItem(itemsClient.create(basedUponSmallAngryPlanet()))
      .withRandomPastLoanDate());

    loansClient.create(new LoanRequestBuilder()
      .withUserId(userId)
      .withStatus("Open")
      .withItem(itemsClient.create(basedUponNod()))
      .withRandomPastLoanDate());

    loansClient.create(new LoanRequestBuilder()
      .withUserId(userId)
      .withItem(
        itemsClient.create(basedUponNod()))
      .withStatus("Closed")
      .withRandomPastLoanDate());

    loansClient.create(new LoanRequestBuilder()
      .withUserId(userId)
      .withStatus("Closed")
      .withItem(itemsClient.create(basedUponTemeraire()))
      .withRandomPastLoanDate());

    loansClient.create(new LoanRequestBuilder()
      .withUserId(userId)
      .withStatus("Closed")
      .withItem(itemsClient.create(basedUponUprooted()))
      .withRandomPastLoanDate());

    loansClient.create(new LoanRequestBuilder()
      .withUserId(userId)
      .withStatus("Closed")
      .withItem(itemsClient.create(basedUponInterestingTimes()))
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

    UUID itemId = itemsClient.create(basedUponNod()).getId();

    UUID id = loansClient.create(new LoanRequestBuilder()
      .withItemId(itemId))
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
//    hasProperty("location", item, "item");

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
