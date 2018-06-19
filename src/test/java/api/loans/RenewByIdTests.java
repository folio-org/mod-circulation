package api.loans;

import api.APITestSuite;
import api.support.APITests;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.Seconds;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static api.support.builders.ItemBuilder.CHECKED_OUT;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.JsonObjectMatchers.hasSoleErrorFor;
import static api.support.matchers.JsonObjectMatchers.hasSoleErrorMessageContaining;
import static api.support.matchers.TextDateTimeMatcher.withinSecondsAfter;
import static api.support.matchers.ValidationErrorMatchers.*;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class RenewByIdTests extends APITests {
  @Test
  public void canRenewRollingLoanFromSystemDate()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    final UUID loanId = loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43))
      .getId();

    //TODO: Renewal based upon system date,
    // needs to be approximated, at least until we introduce a calendar and clock
    DateTime approximateRenewalDate = DateTime.now();

    final JsonObject renewedLoan = loansFixture
      .renewLoanById(smallAngryPlanet, jessica)
      .getJson();

    assertThat(renewedLoan.getString("id"), is(loanId.toString()));

    assertThat("user ID should match barcode",
      renewedLoan.getString("userId"), is(jessica.getId().toString()));

    assertThat("item ID should match barcode",
      renewedLoan.getString("itemId"), is(smallAngryPlanet.getId().toString()));

    assertThat("status should be open",
      renewedLoan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action should be renewed",
      renewedLoan.getString("action"), is("renewed"));

    assertThat("renewal count should be incremented",
      renewedLoan.getInteger("renewalCount"), is(1));

    assertThat("last loan policy should be stored",
      renewedLoan.getString("loanPolicyId"),
      is(APITestSuite.canCirculateRollingLoanPolicyId().toString()));

    assertThat("due date should be approximately 3 weeks after renewal date, based upon loan policy",
      renewedLoan.getString("dueDate"),
      withinSecondsAfter(Seconds.seconds(10), approximateRenewalDate.plusWeeks(3)));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void cannotRenewWhenItemCannotBeFound()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    loansFixture.checkOut(smallAngryPlanet, steve);

    itemsClient.delete(smallAngryPlanet.getId());

    Response response = loansFixture.attemptRenewalById(smallAngryPlanet, steve);

    assertThat(response.getJson(),
      hasSoleErrorMessageContaining(String.format("No item with ID %s exists",
        smallAngryPlanet.getId())));

    assertThat(response.getJson(), hasSoleErrorFor("itemId",
      smallAngryPlanet.getId().toString()));
  }

  @Test
  public void cannotRenewLoanForDifferentUser()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    final IndividualResource jessica = usersFixture.jessica();

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43));

    final Response response = loansFixture.attemptRenewalById(smallAngryPlanet, james);

    assertThat(response.getJson(), hasErrorWith(allOf(
        hasMessage("Cannot renew item checked out to different user"),
        hasParameter("userId", james.getId().toString()))));
  }
}
