package org.folio.circulation.api.requests;

import org.folio.circulation.api.APITestSuite;
import org.folio.circulation.api.support.fixtures.ItemsFixture;
import org.folio.circulation.api.support.http.ResourceClient;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public abstract class RequestsAPITests {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  final OkapiHttpClient client = APITestSuite.createClient(exception -> {
    log.error("Request to circulation module failed:", exception);
  });

  final ResourceClient usersClient = ResourceClient.forUsers(client);
  final ResourceClient itemsClient = ResourceClient.forItems(client);
  final ResourceClient requestsClient = ResourceClient.forRequests(client);
  final ResourceClient loansClient = ResourceClient.forLoans(client);
  final ItemsFixture itemsFixture = new ItemsFixture(client);

  private final ResourceClient holdingsClient = ResourceClient.forHoldings(client);
  private final ResourceClient instancesClient = ResourceClient.forInstances(client);

  @Before
  public void beforeEach()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    requestsClient.deleteAll();
    loansClient.deleteAll();

    itemsClient.deleteAll();
    holdingsClient.deleteAll();
    instancesClient.deleteAll();

    usersClient.deleteAllIndividually();
  }
}
