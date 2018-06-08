package api.loans;

import api.support.APITests;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class RenewByIdTests extends APITests {
  @Test
  public void isNotImplementedYet()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43))
      .getId();

    loansFixture.attemptRenewalById(500, smallAngryPlanet, jessica);
  }
}
