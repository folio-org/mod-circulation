package api.loans;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

import java.net.MalformedURLException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.awaitility.Awaitility;
import org.folio.circulation.support.http.client.IndividualResource;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.EndSessionBuilder;
import io.vertx.core.json.JsonObject;

public class EndExpiredPatronActionSessionTests extends APITests {

  @Before
  public void initSession() throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {
    IndividualResource james = usersFixture.james();
    loansFixture.checkOutByBarcode(itemsFixture.basedUponNod(), james);
    loansFixture.checkOutByBarcode(itemsFixture.basedUponInterestingTimes(), james);
  }

  @Test
  public void expiredEndSessionAfterCheckOut()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    List<JsonObject> sessions = patronSessionRecordsClient.getAll();
    assertThat(sessions, Matchers.hasSize(2));

    String patronId = sessions.stream()
      .findFirst()
      .map(session -> session.getString("patronId"))
      .orElse("");

    expiredEndSessionClient.create(new EndSessionBuilder()
      .withPatronId(patronId)
      .withActionType("Check-out"));

    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);
    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(patronSessionRecordsClient::getAll, empty());
  }

  @Test
  public void noExpiredEndSessionAfterCheckOut()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    List<JsonObject> sessions = patronSessionRecordsClient.getAll();
    assertThat(sessions, Matchers.hasSize(2));

    expiredEndSessionClient.create(new EndSessionBuilder());
    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(patronSessionRecordsClient::getAll, Matchers.hasSize(2));
  }

  @Test
  public void notFailEndSessionProcessingWhenServerIsNotResponding()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    List<JsonObject> sessions = patronSessionRecordsClient.getAll();
    assertThat(sessions, Matchers.hasSize(2));

    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(patronSessionRecordsClient::getAll, Matchers.hasSize(2));
  }
}
