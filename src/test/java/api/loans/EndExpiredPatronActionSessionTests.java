package api.loans;

import static api.support.PubsubPublisherTestUtils.assertThatPublishedLogRecordEventsAreValid;
import static api.support.Wait.waitAtLeast;
import static api.support.fakes.PublishedEvents.byLogEventType;
import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.waitAtMost;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.ACTION_TYPE;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.ID;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.LOAN_ID;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.PATRON_ID;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.folio.circulation.domain.notice.session.PatronActionType;
import org.junit.Before;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.EndSessionBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.fakes.FakePubSub;
import api.support.fixtures.TemplateContextMatchers;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import io.vertx.core.json.JsonObject;
import lombok.val;

public class EndExpiredPatronActionSessionTests extends APITests {
  private static final String CHECK_OUT = "Check-out";
  private static final String CHECK_IN = "Check-in";
  private static final UUID CHECK_OUT_TEMPLATE_ID = UUID.randomUUID();
  private static final UUID CHECK_IN_TEMPLATE_ID = UUID.randomUUID();

  @Before
  public void before() {
    JsonObject checkOutNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(CHECK_OUT_TEMPLATE_ID)
      .withCheckOutEvent()
      .create();

    JsonObject checkInNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(CHECK_IN_TEMPLATE_ID)
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
    assertThat(sessions, hasSize(2));

    String patronId = sessions.stream()
      .findFirst()
      .map(session -> session.getString(PATRON_ID))
      .orElse("");

    createExpiredEndSession(patronId, CHECK_OUT);

    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);

    waitAtLeast(1, SECONDS)
      .until(patronSessionRecordsClient::getAll, empty());

    assertThat(patronNoticesClient.getAll(), hasSize(1));
    assertThat(FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE.value())), hasSize(1));
    assertThatPublishedLogRecordEventsAreValid();
  }

  @Test
  public void patronHasSomeSessionsAndOnlySessionsWithSameActionTypeShouldBeExpiredByTimeout() {
    IndividualResource james = usersFixture.james();
    ItemResource nod = itemsFixture.basedUponNod();
    ItemResource interestingTimes = itemsFixture.basedUponInterestingTimes();

    checkOutFixture.checkOutByBarcode(nod, james);
    checkOutFixture.checkOutByBarcode(interestingTimes, james);
    checkInFixture.checkInByBarcode(nod);
    checkInFixture.checkInByBarcode(interestingTimes);
    expiredEndSessionClient.deleteAll();

    List<JsonObject> sessions = patronSessionRecordsClient.getAll();
    assertThat(sessions, hasSize(4));

    String patronId = sessions.stream()
      .filter(session -> session.getMap().get(ACTION_TYPE)
        .equals(PatronActionType.CHECK_IN.getRepresentation()))
      .findFirst()
      .map(session -> session.getString(PATRON_ID))
      .orElse("");

    createExpiredEndSession(patronId, CHECK_IN);

    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);

    waitAtMost(1, SECONDS)
      .until(patronSessionRecordsClient::getAll,  hasSize(2));

    assertThat(patronNoticesClient.getAll(), hasSize(1));
    assertThat(FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE.value())), hasSize(1));
    assertThatPublishedLogRecordEventsAreValid();
  }

  @Test
  public void patronHasSeveralSessionsAndOnlyOneShouldBeExpiredByTimeout() {
    IndividualResource james = usersFixture.james();
    ItemResource nod = itemsFixture.basedUponNod();
    ItemResource interestingTimes = itemsFixture.basedUponInterestingTimes();

    checkOutFixture.checkOutByBarcode(nod, james);
    checkOutFixture.checkOutByBarcode(interestingTimes, james);
    checkInFixture.checkInByBarcode(nod);
    expiredEndSessionClient.deleteAll();

    List<JsonObject> sessions = patronSessionRecordsClient.getAll();
    assertThat(sessions, hasSize(3));

    String patronId = sessions.stream()
      .filter(session -> session.getMap().get(ACTION_TYPE)
        .equals(PatronActionType.CHECK_IN.getRepresentation()))
      .findFirst()
      .map(session -> session.getString(PATRON_ID))
      .orElse("");

    createExpiredEndSession(patronId, CHECK_IN);

    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);

    waitAtMost(1, SECONDS)
      .until(patronSessionRecordsClient::getAll,  hasSize(2));

    assertThat(patronNoticesClient.getAll(), hasSize(1));
    assertThat(FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE.value())), hasSize(1));
    assertThatPublishedLogRecordEventsAreValid();
  }

  @Test
  public void noExpiredEndSessionAfterCheckOut() {
    IndividualResource james = usersFixture.james();
    checkOutFixture.checkOutByBarcode(itemsFixture.basedUponNod(), james);
    checkOutFixture.checkOutByBarcode(itemsFixture.basedUponInterestingTimes(), james);
    expiredEndSessionClient.deleteAll();

    List<JsonObject> sessions = patronSessionRecordsClient.getAll();
    assertThat(sessions, hasSize(2));

    expiredEndSessionClient.create(new EndSessionBuilder());
    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);

    waitAtMost(1, SECONDS)
      .until(patronSessionRecordsClient::getAll, hasSize(2));
  }

  @Test
  public void noExpiredEndSessionAfterCheckIn() {
    IndividualResource james = usersFixture.james();
    ItemResource nod = itemsFixture.basedUponNod();
    ItemResource interestingTimes = itemsFixture.basedUponInterestingTimes();

    checkOutFixture.checkOutByBarcode(nod, james);
    checkOutFixture.checkOutByBarcode(interestingTimes, james);
    checkInFixture.checkInByBarcode(nod);
    checkInFixture.checkInByBarcode(interestingTimes);
    expiredEndSessionClient.deleteAll();

    List<JsonObject> sessions = patronSessionRecordsClient.getAll();
    assertThat(sessions, hasSize(4));

    expiredEndSessionClient.create(new EndSessionBuilder());
    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);

    waitAtMost(1, SECONDS)
      .until(patronSessionRecordsClient::getAll, hasSize(4));
  }

  @Test
  public void notFailEndSessionProcessingWhenServerIsNotResponding() {
    IndividualResource james = usersFixture.james();
    ItemResource nod = itemsFixture.basedUponNod();
    ItemResource interestingTimes = itemsFixture.basedUponInterestingTimes();
    checkOutFixture.checkOutByBarcode(itemsFixture.basedUponNod(), james);
    checkOutFixture.checkOutByBarcode(itemsFixture.basedUponInterestingTimes(), james);
    checkInFixture.checkInByBarcode(nod);
    checkInFixture.checkInByBarcode(interestingTimes);
    expiredEndSessionClient.deleteAll();

    List<JsonObject> sessions = patronSessionRecordsClient.getAll();
    assertThat(sessions, hasSize(4));

    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);

    waitAtMost(1, SECONDS)
      .until(patronSessionRecordsClient::getAll, hasSize(4));
  }

  @Test
  public void patronsHaveSessionsAndAllShouldBeExpiredByTimeout() {
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    ItemResource nod = itemsFixture.basedUponNod();
    ItemResource interestingTimes = itemsFixture.basedUponInterestingTimes();
    ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    checkOutFixture.checkOutByBarcode(nod, james);
    checkOutFixture.checkOutByBarcode(interestingTimes, jessica);
    checkOutFixture.checkOutByBarcode(smallAngryPlanet, steve);
    checkInFixture.checkInByBarcode(nod);
    checkInFixture.checkInByBarcode(interestingTimes);
    checkInFixture.checkInByBarcode(smallAngryPlanet);
    expiredEndSessionClient.deleteAll();

    List<JsonObject> sessions = patronSessionRecordsClient.getAll();
    assertThat(sessions, hasSize(6));

    sessions.stream()
      .filter(session -> session.getMap().get(ACTION_TYPE)
        .equals(PatronActionType.CHECK_IN.getRepresentation()))
      .map(session -> session.getString(PATRON_ID))
      .forEach(patronId -> createExpiredEndSession(patronId, CHECK_IN));

    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);

    waitAtMost(1, SECONDS)
      .until(patronSessionRecordsClient::getAll,  hasSize(3));

    expiredEndSessionClient.deleteAll();

    sessions.stream()
      .filter(session -> session.getMap().get(ACTION_TYPE)
        .equals(PatronActionType.CHECK_OUT.getRepresentation()))
      .map(session -> session.getString(PATRON_ID))
      .forEach(patronId -> createExpiredEndSession(patronId, CHECK_OUT));

    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);

    waitAtLeast(1, SECONDS)
      .until(patronSessionRecordsClient::getAll, empty());

    List<JsonObject> patronNotices = patronNoticesClient.getAll();

    assertThat(patronNoticesClient.getAll(), hasSize(6));
    assertThat(FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE.value())), hasSize(6));
    assertThatPublishedLogRecordEventsAreValid();

    Stream.of(CHECK_OUT_TEMPLATE_ID, CHECK_IN_TEMPLATE_ID).forEach(templateId ->
      Stream.of(james, jessica, steve).forEach(patron ->
        patronNotices.stream()
          .filter(pn -> pn.getString("templateId").equals(templateId.toString()))
          .filter(pn -> pn.getString("recipientId").equals(patron.getId().toString()))
          .forEach(patronNotice ->
            assertThat(patronNotice, hasEmailNoticeProperties(patron.getId(), templateId,
              TemplateContextMatchers.getUserContextMatchers(patron))))));
  }

  @Test
  public void expiredSessionWithNonExistentLoanShouldBeEnded() {
    IndividualResource james = usersFixture.james();
    ItemResource nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod, james);
    expiredEndSessionClient.deleteAll();

    List<JsonObject> sessions = patronSessionRecordsClient.getAll();
    assertThat(sessions, hasSize(1));

    String loanId = sessions.get(0).getString(LOAN_ID);
    String patronId = sessions.get(0).getString(PATRON_ID);

    loansFixture.deleteLoan(UUID.fromString(loanId));
    createExpiredEndSession(patronId, CHECK_OUT);

    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);

    waitAtLeast(1, SECONDS)
      .until(patronSessionRecordsClient::getAll, empty());

    assertThat(patronNoticesClient.getAll(), empty());
    assertThat(FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE.value())), empty());
  }

  @Test
  public void shouldNotFailIfSessionRecordsAreEmpty() {
    createExpiredEndSession(UUID.randomUUID().toString(), CHECK_OUT);

    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);

    assertThat(patronSessionRecordsClient.getAll(), empty());
    assertThat(patronNoticesClient.getAll(), empty());
    assertThat(FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE.value())), empty());
  }

  @Test
  public void expiredSessionWithNonExistentUserShouldBeEnded() {
    val steve = usersFixture.steve();
    val nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod, steve);
    expiredEndSessionClient.deleteAll();

    List<JsonObject> sessions = patronSessionRecordsClient.getAll();
    assertThat(sessions, hasSize(1));

    String patronId = sessions.get(0).getString(PATRON_ID);

    usersFixture.remove(steve);
    createExpiredEndSession(patronId, CHECK_OUT);

    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);

    waitAtMost(1, SECONDS)
      .until(patronSessionRecordsClient::getAll, empty());

    assertThat(patronNoticesClient.getAll(), empty());
    assertThat(FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE.value())), empty());
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

  @Test
  public void patronNoticeContextContainsUserTokensWhenNoticeIsTriggeredByExpiredSession() {
    IndividualResource james = usersFixture.james();
    ItemResource nod = itemsFixture.basedUponNod();
    checkOutFixture.checkOutByBarcode(nod, james);

    patronSessionRecordsClient.getAll().stream()
      .filter(session -> session.getMap().get(ACTION_TYPE)
        .equals(PatronActionType.CHECK_OUT.getRepresentation()))
      .map(session -> session.getString(PATRON_ID))
      .forEach(patronId -> createExpiredEndSession(patronId, CHECK_OUT));

    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);

    waitAtMost(1, SECONDS)
      .until(patronSessionRecordsClient::getAll,  empty());

    assertThat(patronNoticesClient.getAll(), hasSize(1));
    assertThat(FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE.value())), hasSize(1));
    assertThatPublishedLogRecordEventsAreValid();

    assertThat(patronNoticesClient.getAll().get(0),
      hasEmailNoticeProperties(james.getId(), CHECK_OUT_TEMPLATE_ID,
        TemplateContextMatchers.getUserContextMatchers(james)));
  }

  @Test
  public void expiredSessionWithNonExistentItemIdShouldBeEnded() {
    IndividualResource james = usersFixture.james();
    ItemResource nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod, james);
    expiredEndSessionClient.deleteAll();

    List<JsonObject> sessions = patronSessionRecordsClient.getAll();
    assertThat(sessions, hasSize(1));

    UUID loanId = UUID.fromString(sessions.get(0).getString(LOAN_ID));
    String patronId = sessions.get(0).getString(PATRON_ID);
    IndividualResource loan = loansFixture.getLoanById(loanId);
    itemsClient.delete(UUID.fromString(loan.getJson().getString("itemId")));

    createExpiredEndSession(patronId, CHECK_OUT);
    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);

    waitAtMost(1, SECONDS)
      .until(patronSessionRecordsClient::getAll, empty());

    assertThat(patronNoticesClient.getAll(), empty());
    assertThat(FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE.value())), empty());
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
    assertThat(sessions, hasSize(numberOfSessions));

    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);

    waitAtMost(1, SECONDS)
      .until(patronSessionRecordsClient::getAll, empty());
  }

  private void createExpiredEndSession(String patronId, String actionType) {
    expiredEndSessionClient.create(new EndSessionBuilder()
      .withPatronId(patronId)
      .withActionType(actionType));
  }
}
