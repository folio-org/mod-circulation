package org.folio.circulation.api.loans;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.support.APITests;
import org.folio.circulation.api.support.builders.LoanBuilder;
import org.folio.circulation.api.support.builders.UserBuilder;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.folio.circulation.api.support.http.InterfaceUrls.loansUrl;
import static org.folio.circulation.api.support.http.InterfaceUrls.usersProxyUrl;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class LoanAPIProxyTests extends APITests {
  @Test
  public void canCreateProxiedLoanWhenValidRelationship()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    //create item
    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    //create user
    UUID userId = usersClient.create(new UserBuilder()).getId();

    //create proxy that is valid with an expDate in the year 2999
    UUID proxyUserId = UUID.randomUUID();
    DateTime expDate = new DateTime(2999, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    UUID recordId = usersFixture.proxyFor(userId, proxyUserId, expDate).getId();

    //create loan with the proxy id annd user id

    //should pass, do same but create invalid proxy (not active) and check
    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, DateTimeZone.UTC);

    //create loan should be allowed as proxy is valid
    IndividualResource response = loansClient.create(new LoanBuilder()
      .withId(id)
      .withUserId(userId)
      .withProxyUserId(recordId)
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate)
      .withStatus("Open"));

    JsonObject loan = response.getJson();

    assertThat("user id does not match",
      loan.getString("userId"), is(userId.toString()));

    assertThat("proxy user id does not match",
      loan.getString("proxyUserId"), is(recordId.toString()));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(usersProxyUrl(""),
      ResponseHandler.any(getCompleted));

    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
  }

  @Test
  public void cannotCreateProxiedLoanWhenRelationshipHasExpired()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    UUID userId = usersClient.create(new UserBuilder()).getId();

    UUID proxyUserId = UUID.randomUUID();
    DateTime expDate = new DateTime(2000, 2, 27, 10, 23, 43, DateTimeZone.UTC);

    //create proxy that is invalid with an expDate in the year 2000
    UUID recordId = usersFixture.proxyFor(userId, proxyUserId, expDate).getId();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, DateTimeZone.UTC);

    //create loan should not be allowed as proxy is valid
    JsonObject loan = new LoanBuilder()
      .withId(id)
      .withUserId(userId)
      .withProxyUserId(recordId)
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate)
      .withStatus("Open").create();

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(loansUrl(), loan,
      ResponseHandler.any(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(postResponse.getBody(), postResponse.getStatusCode(), is(422));
  }

  @Test
  public void cannotCreateProxiedLoanWhenNoRelationship()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, DateTimeZone.UTC);

    JsonObject loan = new LoanBuilder()
      .withId(id)
      .withUserId(UUID.randomUUID())
      .withProxyUserId(UUID.randomUUID())
      .withItemId(UUID.randomUUID())
      .withLoanDate(loanDate)
      .withDueDate(dueDate)
      .withStatus("Open").create();

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(loansUrl(), loan,
      ResponseHandler.any(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(postResponse.getBody(), postResponse.getStatusCode(), is(422));
  }

  @Test
  public void canUpdateProxiedLoanWhenValidProxyRelationship()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();
    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();
    UUID userId = usersClient.create(new UserBuilder()).getId();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, DateTimeZone.UTC);

    loansClient.create(new LoanBuilder()
      .withId(id)
      .withUserId(userId)
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate)
      .withStatus("Open"));

    UUID proxyUserId = UUID.randomUUID();
    DateTime expDate = new DateTime(2999, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    UUID recordId = usersFixture.proxyFor(userId, proxyUserId, expDate).getId();

    JsonObject loan = new LoanBuilder()
      .withId(id)
      .withUserId(userId)
      .withProxyUserId(recordId)
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate)
      .withStatus("Open").create();

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(loansUrl(String.format("/%s", id)), loan,
      ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat("Valid proxy should allow updates to work but does not", putResponse.getStatusCode(),
      is(HttpURLConnection.HTTP_NO_CONTENT));
  }

  @Test
  public void cannotUpdateProxiedLoanWhenRelationshipHasExpired()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();
    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();
    UUID userId = usersClient.create(new UserBuilder()).getId();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, DateTimeZone.UTC);

    loansClient.create(new LoanBuilder()
      .withId(id)
      .withUserId(userId)
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate)
      .withStatus("Open"));

    UUID proxyUserId = UUID.randomUUID();
    DateTime expDate = new DateTime(1999, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    UUID recordId = usersFixture.proxyFor(userId, proxyUserId, expDate).getId();

    JsonObject loan = new LoanBuilder()
      .withId(id)
      .withUserId(userId)
      .withProxyUserId(recordId)
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate)
      .withStatus("Open").create();

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(loansUrl(String.format("/%s", id)), loan,
      ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat("Invalid proxy should fail loan update",
      putResponse.getStatusCode(), is(422));
  }

  @Test
  public void cannotUpdateProxiedLoanWhenNoRelationship()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();
    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();
    UUID userId = usersClient.create(new UserBuilder()).getId();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, DateTimeZone.UTC);

    loansClient.create(new LoanBuilder()
      .withId(id)
      .withUserId(userId)
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate)
      .withStatus("Open"));

    JsonObject loan = new LoanBuilder()
      .withId(id)
      .withUserId(userId)
      .withProxyUserId(UUID.randomUUID())
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate)
      .withStatus("Open").create();

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(loansUrl(String.format("/%s", id)), loan,
      ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat("Invalid proxyUserId is not allowed when updating a loan",
      putResponse.getStatusCode(), is(422));
  }
}
