package api.loans;

import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.ACTION_TYPE;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.ID;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.LOAN_ID;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.PATRON_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.awaitility.Awaitility;
import org.folio.circulation.domain.notice.session.PatronActionType;
import org.folio.circulation.support.http.client.IndividualResource;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.EndSessionBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;

public class EndExpiredPatronActionSessionTests extends APITests {

  private static final String CHECK_OUT = "Check-out";
  private static final String CHECK_IN = "Check-in";

  @Before
  public void before() {

    JsonObject checkOutNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(UUID.randomUUID())
      .withCheckOutEvent()
      .create();

    JsonObject checkInNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(UUID.randomUUID())
      .withCheckInEvent()
      .create();

    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with check-in/check-out notice")
      .withLoanNotices(Arrays.asList(checkOutNoticeConfig, checkInNoticeConfig));
    useFallbackPolicies(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());
  }

  @Test
  public void expiredEndSessionAfterCheckOut() {

    IndividualResource james = usersFixture.james();
    checkOutFixture.checkOutByBarcode(itemsFixture.basedUponNod(), james);
    checkOutFixture.checkOutByBarcode(itemsFixture.basedUponInterestingTimes(), james);
    expiredEndSessionClient.deleteAll();

    List<JsonObject> sessions = patronSessionRecordsClient.getAll();
    assertThat(sessions, Matchers.hasSize(2));

    String patronId = sessions.stream()
      .findFirst()
      .map(session -> session.getString(PATRON_ID))
      .orElse("");

    createExpiredEndSession(patronId, CHECK_OUT);

    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);
    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(patronSessionRecordsClient::getAll, empty());
    assertThat(patronNoticesClient.getAll(), hasSize(1));
  }

  @Test
  public void patronHasSomeSessionsAndOnlySessionsWithSameActionTypeShouldBeExpiredByTimeout() {

    IndividualResource james = usersFixture.james();
    InventoryItemResource nod = itemsFixture.basedUponNod();
    InventoryItemResource interestingTimes = itemsFixture.basedUponInterestingTimes();

    checkOutFixture.checkOutByBarcode(nod, james);
    checkOutFixture.checkOutByBarcode(interestingTimes, james);
    checkInFixture.checkInByBarcode(nod);
    checkInFixture.checkInByBarcode(interestingTimes);
    expiredEndSessionClient.deleteAll();

    List<JsonObject> sessions = patronSessionRecordsClient.getAll();
    assertThat(sessions, Matchers.hasSize(4));

    String patronId = sessions.stream()
      .filter(session -> session.getMap().get(ACTION_TYPE)
        .equals(PatronActionType.CHECK_IN.getRepresentation()))
      .findFirst()
      .map(session -> session.getString(PATRON_ID))
      .orElse("");

    createExpiredEndSession(patronId, CHECK_IN);

    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);
    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(patronSessionRecordsClient::getAll,  Matchers.hasSize(2));
    assertThat(patronNoticesClient.getAll(), hasSize(1));
  }

  @Test
  public void patronHasSeveralSessionsAndOnlyOneShouldBeExpiredByTimeout() {

    IndividualResource james = usersFixture.james();
    InventoryItemResource nod = itemsFixture.basedUponNod();
    InventoryItemResource interestingTimes = itemsFixture.basedUponInterestingTimes();

    checkOutFixture.checkOutByBarcode(nod, james);
    checkOutFixture.checkOutByBarcode(interestingTimes, james);
    checkInFixture.checkInByBarcode(nod);
    expiredEndSessionClient.deleteAll();

    List<JsonObject> sessions = patronSessionRecordsClient.getAll();
    assertThat(sessions, Matchers.hasSize(3));

    String patronId = sessions.stream()
      .filter(session -> session.getMap().get(ACTION_TYPE)
        .equals(PatronActionType.CHECK_IN.getRepresentation()))
      .findFirst()
      .map(session -> session.getString(PATRON_ID))
      .orElse("");

    createExpiredEndSession(patronId, CHECK_IN);

    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);
    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(patronSessionRecordsClient::getAll,  Matchers.hasSize(2));
    assertThat(patronNoticesClient.getAll(), hasSize(1));
  }

  @Test
  public void noExpiredEndSessionAfterCheckOut() {

    IndividualResource james = usersFixture.james();
    checkOutFixture.checkOutByBarcode(itemsFixture.basedUponNod(), james);
    checkOutFixture.checkOutByBarcode(itemsFixture.basedUponInterestingTimes(), james);
    expiredEndSessionClient.deleteAll();

    List<JsonObject> sessions = patronSessionRecordsClient.getAll();
    assertThat(sessions, Matchers.hasSize(2));

    expiredEndSessionClient.create(new EndSessionBuilder());
    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(patronSessionRecordsClient::getAll, Matchers.hasSize(2));
  }

  @Test
  public void noExpiredEndSessionAfterCheckIn() {

    IndividualResource james = usersFixture.james();
    InventoryItemResource nod = itemsFixture.basedUponNod();
    InventoryItemResource interestingTimes = itemsFixture.basedUponInterestingTimes();

    checkOutFixture.checkOutByBarcode(nod, james);
    checkOutFixture.checkOutByBarcode(interestingTimes, james);
    checkInFixture.checkInByBarcode(nod);
    checkInFixture.checkInByBarcode(interestingTimes);
    expiredEndSessionClient.deleteAll();

    List<JsonObject> sessions = patronSessionRecordsClient.getAll();
    assertThat(sessions, Matchers.hasSize(4));

    expiredEndSessionClient.create(new EndSessionBuilder());
    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(patronSessionRecordsClient::getAll, Matchers.hasSize(4));
  }

  @Test
  public void notFailEndSessionProcessingWhenServerIsNotResponding() {

    IndividualResource james = usersFixture.james();
    InventoryItemResource nod = itemsFixture.basedUponNod();
    InventoryItemResource interestingTimes = itemsFixture.basedUponInterestingTimes();
    checkOutFixture.checkOutByBarcode(itemsFixture.basedUponNod(), james);
    checkOutFixture.checkOutByBarcode(itemsFixture.basedUponInterestingTimes(), james);
    checkInFixture.checkInByBarcode(nod);
    checkInFixture.checkInByBarcode(interestingTimes);
    expiredEndSessionClient.deleteAll();

    List<JsonObject> sessions = patronSessionRecordsClient.getAll();
    assertThat(sessions, Matchers.hasSize(4));

    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(patronSessionRecordsClient::getAll, Matchers.hasSize(4));
  }

  @Test
  public void patronsHaveSessionsAndAllShouldBeExpiredByTimeout() {

    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    InventoryItemResource nod = itemsFixture.basedUponNod();
    InventoryItemResource interestingTimes = itemsFixture.basedUponInterestingTimes();
    InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    checkOutFixture.checkOutByBarcode(nod, james);
    checkOutFixture.checkOutByBarcode(interestingTimes, jessica);
    checkOutFixture.checkOutByBarcode(smallAngryPlanet, steve);
    checkInFixture.checkInByBarcode(nod);
    checkInFixture.checkInByBarcode(interestingTimes);
    checkInFixture.checkInByBarcode(smallAngryPlanet);
    expiredEndSessionClient.deleteAll();

    List<JsonObject> sessions = patronSessionRecordsClient.getAll();
    assertThat(sessions, Matchers.hasSize(6));

    sessions.stream()
      .filter(session -> session.getMap().get(ACTION_TYPE)
        .equals(PatronActionType.CHECK_IN.getRepresentation()))
      .map(session -> session.getString(PATRON_ID))
      .forEach(patronId -> createExpiredEndSession(patronId, CHECK_IN));

    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);
    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(patronSessionRecordsClient::getAll,  Matchers.hasSize(3));

    expiredEndSessionClient.deleteAll();

    sessions.stream()
      .filter(session -> session.getMap().get(ACTION_TYPE)
        .equals(PatronActionType.CHECK_OUT.getRepresentation()))
      .map(session -> session.getString(PATRON_ID))
      .forEach(patronId -> createExpiredEndSession(patronId, CHECK_OUT));

    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);
    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(patronSessionRecordsClient::getAll,  Matchers.hasSize(0));
    assertThat(patronNoticesClient.getAll(), hasSize(6));
  }

  @Test
  public void expiredSessionWithNonExistentLoanShouldBeEnded() {
    IndividualResource james = usersFixture.james();
    InventoryItemResource nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod, james);
    expiredEndSessionClient.deleteAll();

    List<JsonObject> sessions = patronSessionRecordsClient.getAll();
    assertThat(sessions, Matchers.hasSize(1));

    String loanId = sessions.get(0).getString(LOAN_ID);
    String patronId = sessions.get(0).getString(PATRON_ID);

    loansFixture.deleteLoan(UUID.fromString(loanId));
    createExpiredEndSession(patronId, CHECK_OUT);

    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(patronSessionRecordsClient::getAll, empty());
    assertThat(patronNoticesClient.getAll(), hasSize(0));
  }

  @Test
  public void shouldNotFailIfSessionRecordsAreEmpty() {
    createExpiredEndSession(UUID.randomUUID().toString(), CHECK_OUT);
    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);

    assertThat(patronSessionRecordsClient.getAll(), hasSize(0));
    assertThat(patronNoticesClient.getAll(), hasSize(0));
  }

  @Test
  public void expiredSessionWithNonExistentUserShouldBeEnded() {
    IndividualResource steve = usersFixture.steve();
    InventoryItemResource nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod, steve);
    expiredEndSessionClient.deleteAll();

    List<JsonObject> sessions = patronSessionRecordsClient.getAll();
    assertThat(sessions, Matchers.hasSize(1));

    String patronId = sessions.get(0).getString(PATRON_ID);

    usersFixture.remove(steve);
    createExpiredEndSession(patronId, CHECK_OUT);

    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(patronSessionRecordsClient::getAll, empty());
    assertThat(patronNoticesClient.getAll(), hasSize(0));
  }

  @Test
  public void shouldNotFailWithUriTooLargeErrorDuringEndingExpiredCheckOutSessions() {
    checkThatBunchOfExpiredSessionsWereAddedAndRemovedByTimer(100, CHECK_OUT);
  }

  @Test
  public void shouldNotFailWithUriTooLargeErrorDuringEndingExpiredCheckInSessions() {
    checkThatBunchOfExpiredSessionsWereAddedAndRemovedByTimer(100, CHECK_IN);
  }

  @Test
  public void sessionsWithNotSpecifiedActionTypeShouldBeEnded() {
    checkThatBunchOfExpiredSessionsWereAddedAndRemovedByTimer(100, "");
  }

  private void checkThatBunchOfExpiredSessionsWereAddedAndRemovedByTimer(
    int numberOfSessions, String actionType) {

    IntStream.range(0, numberOfSessions).forEach(
      notUsed -> {
        String patronId = UUID.randomUUID().toString();
        patronSessionRecordsClient.create(
          new JsonObject()
            .put(ID, UUID.randomUUID().toString())
            .put(PATRON_ID, patronId)
            .put(LOAN_ID, UUID.randomUUID().toString())
            .put(ACTION_TYPE, actionType));
        expiredEndSessionClient.create(
          new JsonObject()
            .put(PATRON_ID, patronId)
            .put(ACTION_TYPE, actionType));
      });

    List<JsonObject> sessions = patronSessionRecordsClient.getAll();
    assertThat(sessions, Matchers.hasSize(numberOfSessions));

    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(patronSessionRecordsClient::getAll, empty());
  }

  private void createExpiredEndSession(String patronId, String actionType) {
    expiredEndSessionClient.create(new EndSessionBuilder()
      .withPatronId(patronId)
      .withActionType(actionType));
  }
}
