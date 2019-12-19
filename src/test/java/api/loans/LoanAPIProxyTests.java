package api.loans;

import static api.support.http.InterfaceUrls.loansUrl;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.LoanBuilder;
import io.vertx.core.json.JsonObject;

public class LoanAPIProxyTests extends APITests {
  @Test
  public void canCreateProxiedLoanWhenCurrentActiveRelationship()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    IndividualResource sponsor = usersFixture.jessica();
    IndividualResource proxy = usersFixture.james();

    proxyRelationshipsFixture.currentProxyFor(sponsor, proxy);

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, DateTimeZone.UTC);

    IndividualResource response = loansFixture.createLoan(new LoanBuilder()
      .withId(id)
      .open()
      .withUserId(sponsor.getId())
      .withProxyUserId(proxy.getId())
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate));

    JsonObject loan = response.getJson();

    assertThat("user id does not match",
      loan.getString("userId"), is(sponsor.getId().toString()));

    assertThat("proxy user id does not match",
      loan.getString("proxyUserId"), is(proxy.getId().toString()));
  }

  @Test
  public void canCreateProxiedLoanWhenNonExpiringRelationship()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    IndividualResource sponsor = usersFixture.jessica();
    IndividualResource proxy = usersFixture.james();

    proxyRelationshipsFixture.nonExpiringProxyFor(sponsor, proxy);

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, DateTimeZone.UTC);

    IndividualResource response = loansFixture.createLoan(new LoanBuilder()
      .withId(id)
      .open()
      .withUserId(sponsor.getId())
      .withProxyUserId(proxy.getId())
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate));

    JsonObject loan = response.getJson();

    assertThat("user id does not match",
      loan.getString("userId"), is(sponsor.getId().toString()));

    assertThat("proxy user id does not match",
      loan.getString("proxyUserId"), is(proxy.getId().toString()));
  }

  @Test
  public void cannotCreateProxiedLoanWhenRelationshipIsInactive()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    IndividualResource sponsor = usersFixture.jessica();
    IndividualResource proxy = usersFixture.james();

    proxyRelationshipsFixture.inactiveProxyFor(sponsor, proxy);

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, DateTimeZone.UTC);

    JsonObject loan = new LoanBuilder()
      .withId(id)
      .open()
      .withUserId(sponsor.getId())
      .withProxyUserId(proxy.getId())
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate).create();

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(loansUrl(), loan,
      ResponseHandler.any(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(postResponse.getBody(), postResponse.getStatusCode(), is(422));
  }

  @Test
  public void cannotCreateProxiedLoanWhenRelationshipHasExpired()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    IndividualResource sponsor = usersFixture.jessica();
    IndividualResource proxy = usersFixture.james();

    proxyRelationshipsFixture.expiredProxyFor(sponsor, proxy);

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, DateTimeZone.UTC);

    JsonObject loan = new LoanBuilder()
      .withId(id)
      .open()
      .withUserId(sponsor.getId())
      .withProxyUserId(proxy.getId())
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate).create();

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(loansUrl(), loan,
      ResponseHandler.any(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(postResponse.getBody(), postResponse.getStatusCode(), is(422));
  }

  @Test
  public void cannotCreateProxiedLoanWhenRelationshipIsForOtherSponsor()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    IndividualResource unexpectedSponsor = usersFixture.jessica();
    IndividualResource otherUser = usersFixture.charlotte();
    IndividualResource proxy = usersFixture.james();

    proxyRelationshipsFixture.currentProxyFor(unexpectedSponsor, proxy);

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, DateTimeZone.UTC);

    JsonObject loan = new LoanBuilder()
      .withId(id)
      .open()
      .withUserId(otherUser.getId())
      .withProxyUserId(proxy.getId())
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate).create();

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

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    IndividualResource requestingUser = usersFixture.jessica();
    IndividualResource userAttemptingToProxy = usersFixture.james();

    JsonObject loan = new LoanBuilder()
      .withId(id)
      .open()
      .withUserId(requestingUser.getId())
      .withProxyUserId(userAttemptingToProxy.getId())
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate).create();

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

    IndividualResource sponsor = usersFixture.jessica();
    IndividualResource proxy = usersFixture.james();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, DateTimeZone.UTC);

    loansFixture.createLoan(new LoanBuilder()
      .withId(id)
      .open()
      .withUserId(sponsor.getId())
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate));

    proxyRelationshipsFixture.currentProxyFor(sponsor, proxy);

    JsonObject loan = new LoanBuilder()
      .withId(id)
      .open()
      .withUserId(sponsor.getId())
      .withProxyUserId(proxy.getId())
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate).create();

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(loansUrl(String.format("/%s", id)), loan,
      ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat("Valid proxy should allow updates to work but does not",
      putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
  }

  @Test
  public void cannotUpdateProxiedLoanWhenRelationshipHasExpired()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();
    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    IndividualResource sponsor = usersFixture.jessica();
    IndividualResource proxy = usersFixture.james();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, DateTimeZone.UTC);

    loansFixture.createLoan(new LoanBuilder()
      .withId(id)
      .open()
      .withUserId(sponsor.getId())
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate));

    proxyRelationshipsFixture.expiredProxyFor(sponsor, proxy);

    JsonObject loan = new LoanBuilder()
      .withId(id)
      .open()
      .withUserId(sponsor.getId())
      .withProxyUserId(proxy.getId())
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate).create();

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(loansUrl(String.format("/%s", id)), loan,
      ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat("Invalid proxy should fail loan update",
      putResponse.getStatusCode(), is(422));
  }

  @Test
  public void cannotUpdateProxiedLoanWhenRelationshipIsForOtherSponsor()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();
    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    IndividualResource unexpectedSponsor = usersFixture.jessica();
    IndividualResource otherUser = usersFixture.charlotte();
    IndividualResource proxy = usersFixture.james();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, DateTimeZone.UTC);

    loansFixture.createLoan(new LoanBuilder()
      .withId(id)
      .open()
      .withUserId(otherUser.getId())
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate));

    proxyRelationshipsFixture.currentProxyFor(unexpectedSponsor, proxy);

    JsonObject loan = new LoanBuilder()
      .withId(id)
      .open()
      .withUserId(otherUser.getId())
      .withProxyUserId(proxy.getId())
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate).create();

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

    IndividualResource requestingUser = usersFixture.jessica();
    IndividualResource userAttemptingToProxy = usersFixture.james();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, DateTimeZone.UTC);

    loansFixture.createLoan(new LoanBuilder()
      .withId(id)
      .open()
      .withUserId(requestingUser.getId())
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate));

    JsonObject loan = new LoanBuilder()
      .withId(id)
      .open()
      .withUserId(requestingUser.getId())
      .withProxyUserId(userAttemptingToProxy.getId())
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate)
      .create();

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(loansUrl(String.format("/%s", id)), loan,
      ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat("Invalid proxyUserId is not allowed when updating a loan",
      putResponse.getStatusCode(), is(422));
  }
}
