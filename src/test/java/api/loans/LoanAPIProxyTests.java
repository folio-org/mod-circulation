package api.loans;

import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.joda.time.DateTimeZone.UTC;

import java.util.UUID;

import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.LoanBuilder;
import api.support.http.IndividualResource;
import io.vertx.core.json.JsonObject;

class LoanAPIProxyTests extends APITests {
  @Test
  void canCreateProxiedLoanWhenCurrentActiveRelationship() {
    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    IndividualResource sponsor = usersFixture.jessica();
    IndividualResource proxy = usersFixture.james();

    proxyRelationshipsFixture.currentProxyFor(sponsor, proxy);

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, UTC);

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
  void canCreateProxiedLoanWhenNonExpiringRelationship() {
    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    IndividualResource sponsor = usersFixture.jessica();
    IndividualResource proxy = usersFixture.james();

    proxyRelationshipsFixture.nonExpiringProxyFor(sponsor, proxy);

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, UTC);

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
  void cannotCreateProxiedLoanWhenRelationshipIsInactive() {
    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    IndividualResource sponsor = usersFixture.jessica();
    IndividualResource proxy = usersFixture.james();

    proxyRelationshipsFixture.inactiveProxyFor(sponsor, proxy);

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, UTC);

    LoanBuilder loan = new LoanBuilder()
      .withId(id)
      .open()
      .withUserId(sponsor.getId())
      .withProxyUserId(proxy.getId())
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate);

    Response postResponse = loansFixture.attemptToCreateLoan(loan, 422);

    assertThat(postResponse.getJson(), hasErrorWith(
      hasMessage("proxyUserId is not valid")));
  }

  @Test
  void cannotCreateProxiedLoanWhenRelationshipHasExpired() {
    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    IndividualResource sponsor = usersFixture.jessica();
    IndividualResource proxy = usersFixture.james();

    proxyRelationshipsFixture.expiredProxyFor(sponsor, proxy);

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, UTC);

    LoanBuilder loan = new LoanBuilder()
      .withId(id)
      .open()
      .withUserId(sponsor.getId())
      .withProxyUserId(proxy.getId())
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate);

    Response postResponse = loansFixture.attemptToCreateLoan(loan, 422);

    assertThat(postResponse.getJson(), hasErrorWith(
      hasMessage("proxyUserId is not valid")));
  }

  @Test
  void cannotCreateProxiedLoanWhenRelationshipIsForOtherSponsor() {
    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    IndividualResource unexpectedSponsor = usersFixture.jessica();
    IndividualResource otherUser = usersFixture.charlotte();
    IndividualResource proxy = usersFixture.james();

    proxyRelationshipsFixture.currentProxyFor(unexpectedSponsor, proxy);

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, UTC);

    LoanBuilder loan = new LoanBuilder()
      .withId(id)
      .open()
      .withUserId(otherUser.getId())
      .withProxyUserId(proxy.getId())
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate);

    Response postResponse = loansFixture.attemptToCreateLoan(loan, 422);

    assertThat(postResponse.getJson(), hasErrorWith(
      hasMessage("proxyUserId is not valid")));
  }

  @Test
  void cannotCreateProxiedLoanWhenNoRelationship() {
    UUID id = UUID.randomUUID();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, UTC);

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    IndividualResource requestingUser = usersFixture.jessica();
    IndividualResource userAttemptingToProxy = usersFixture.james();

    LoanBuilder loan = new LoanBuilder()
      .withId(id)
      .open()
      .withUserId(requestingUser.getId())
      .withProxyUserId(userAttemptingToProxy.getId())
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate);

    Response postResponse = loansFixture.attemptToCreateLoan(loan, 422);

    assertThat(postResponse.getJson(), hasErrorWith(
      hasMessage("proxyUserId is not valid")));
  }

  @Test
  void canUpdateProxiedLoanWhenValidProxyRelationship() {
    UUID id = UUID.randomUUID();
    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    IndividualResource sponsor = usersFixture.jessica();
    IndividualResource proxy = usersFixture.james();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, UTC);

    loansFixture.createLoan(new LoanBuilder()
      .withId(id)
      .open()
      .withUserId(sponsor.getId())
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate));

    proxyRelationshipsFixture.currentProxyFor(sponsor, proxy);

    JsonObject updatedLoan = new LoanBuilder()
      .withId(id)
      .open()
      .withUserId(sponsor.getId())
      .withProxyUserId(proxy.getId())
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate).create();

    Response putResponse = loansFixture.attemptToReplaceLoan(id, updatedLoan);

    assertThat("Valid proxy should allow updates to work but does not",
      putResponse.getStatusCode(), is(HTTP_NO_CONTENT));
  }

  @Test
  void cannotUpdateProxiedLoanWhenRelationshipHasExpired() {
    UUID id = UUID.randomUUID();
    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    IndividualResource sponsor = usersFixture.jessica();
    IndividualResource proxy = usersFixture.james();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, UTC);

    loansFixture.createLoan(new LoanBuilder()
      .withId(id)
      .open()
      .withUserId(sponsor.getId())
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate));

    proxyRelationshipsFixture.expiredProxyFor(sponsor, proxy);

    JsonObject updatedLoan = new LoanBuilder()
      .withId(id)
      .open()
      .withUserId(sponsor.getId())
      .withProxyUserId(proxy.getId())
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate).create();

    Response putResponse = loansFixture.attemptToReplaceLoan(id, updatedLoan);

    assertThat("Invalid proxy should fail loan update",
      putResponse.getStatusCode(), is(422));

    assertThat(putResponse.getJson(), hasErrorWith(
      hasMessage("proxyUserId is not valid")));
  }

  @Test
  void cannotUpdateProxiedLoanWhenRelationshipIsForOtherSponsor() {
    UUID id = UUID.randomUUID();
    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    IndividualResource unexpectedSponsor = usersFixture.jessica();
    IndividualResource otherUser = usersFixture.charlotte();
    IndividualResource proxy = usersFixture.james();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, UTC);

    loansFixture.createLoan(new LoanBuilder()
      .withId(id)
      .open()
      .withUserId(otherUser.getId())
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate));

    proxyRelationshipsFixture.currentProxyFor(unexpectedSponsor, proxy);

    JsonObject updatedLoan = new LoanBuilder()
      .withId(id)
      .open()
      .withUserId(otherUser.getId())
      .withProxyUserId(proxy.getId())
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate).create();

    Response putResponse = loansFixture.attemptToReplaceLoan(id, updatedLoan);

    assertThat("Invalid proxy should fail loan update",
      putResponse.getStatusCode(), is(422));

    assertThat(putResponse.getJson(), hasErrorWith(
      hasMessage("proxyUserId is not valid")));
  }

  @Test
  void cannotUpdateProxiedLoanWhenNoRelationship() {
    UUID id = UUID.randomUUID();
    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    IndividualResource requestingUser = usersFixture.jessica();
    IndividualResource userAttemptingToProxy = usersFixture.james();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, UTC);

    loansFixture.createLoan(new LoanBuilder()
      .withId(id)
      .open()
      .withUserId(requestingUser.getId())
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate));

    JsonObject updatedLoan = new LoanBuilder()
      .withId(id)
      .open()
      .withUserId(requestingUser.getId())
      .withProxyUserId(userAttemptingToProxy.getId())
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate)
      .create();

    Response putResponse = loansFixture.attemptToReplaceLoan(id, updatedLoan);

    assertThat("Invalid proxyUserId is not allowed when updating a loan",
      putResponse.getStatusCode(), is(422));

    assertThat(putResponse.getJson(), hasErrorWith(
      hasMessage("proxyUserId is not valid")));
  }
}
