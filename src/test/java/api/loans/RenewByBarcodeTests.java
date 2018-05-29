package api.loans;

import api.APITestSuite;
import api.support.APITests;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.Seconds;
import org.junit.Ignore;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static api.support.builders.ItemBuilder.CHECKED_OUT;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.TextDateTimeMatcher.withinSecondsAfter;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class RenewByBarcodeTests extends APITests {

  @Test
  @Ignore
  public void canRenewRollingLoanFromSystemDate()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    final UUID loanId = loansFixture.checkOut(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43))
      .getId();

    //TODO: Renewal based upon system date,
    // needs to be approximated, at least until we introduce a calendar and clock
    DateTime approximateRenewalDate = DateTime.now();

    final JsonObject renewedLoan = loansFixture.renewLoan(smallAngryPlanet, jessica)
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

    assertThat("last loan policy should be stored",
      renewedLoan.getString("loanPolicyId"), is(APITestSuite.canCirculateRollingLoanPolicyId().toString()));

    assertThat("due date should be 3 weeks after loan date, based upon loan policy",
      renewedLoan.getString("dueDate"),
      withinSecondsAfter(Seconds.seconds(10), approximateRenewalDate.plusWeeks(3)));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }
}
