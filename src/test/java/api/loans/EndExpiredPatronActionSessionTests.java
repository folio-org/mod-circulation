package api.loans;

import static api.support.Wait.waitAtLeast;
import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfPublishedEvents;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfSentNotices;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.waitAtMost;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.ACTION_TYPE;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.ID;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.LOAN_ID;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.PATRON_ID;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE_ERROR;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import api.support.builders.AddInfoRequestBuilder;
import org.folio.circulation.domain.notice.session.PatronActionType;
import org.hamcrest.Matcher;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.EndSessionBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.fakes.FakeModNotify;
import api.support.fixtures.TemplateContextMatchers;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import io.vertx.core.json.JsonObject;
import lombok.val;

class EndExpiredPatronActionSessionTests extends APITests {
  private static final String CHECK_OUT = "Check-out";
  private static final String CHECK_IN = "Check-in";
  private static final String LOAN_INFO_ADDED = "testing patron info";
  private static final UUID CHECK_OUT_TEMPLATE_ID = UUID.randomUUID();
  private static final UUID CHECK_IN_TEMPLATE_ID = UUID.randomUUID();

  public EndExpiredPatronActionSessionTests() {
    super(true, true);
  }

  @BeforeEach
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
  void expiredEndSessionAfterCheckOut() {
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

    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void patronHasSomeSessionsAndOnlySessionsWithSameActionTypeShouldBeExpiredByTimeout() {
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
      .until(patronSessionRecordsClient::getAll, hasSize(2));

    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void patronHasSeveralSessionsAndOnlyOneShouldBeExpiredByTimeout() {
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
      .until(patronSessionRecordsClient::getAll, hasSize(2));

    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void noExpiredEndSessionAfterCheckOut() {
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
  void noExpiredEndSessionAfterCheckIn() {
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
  void notFailEndSessionProcessingWhenServerIsNotResponding() {
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
  void patronsHaveSessionsAndAllShouldBeExpiredByTimeout() {
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    ItemResource nod = itemsFixture.basedUponNod();
    ItemResource interestingTimes = itemsFixture.basedUponInterestingTimes();
    ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    checkOutAndAddPatronNotice(nod, james);
    checkOutAndAddPatronNotice(interestingTimes, jessica);
    checkOutAndAddPatronNotice(smallAngryPlanet, steve);
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
      .until(patronSessionRecordsClient::getAll, hasSize(3));

    expiredEndSessionClient.deleteAll();

    sessions.stream()
      .filter(session -> session.getMap().get(ACTION_TYPE)
        .equals(PatronActionType.CHECK_OUT.getRepresentation()))
      .map(session -> session.getString(PATRON_ID))
      .forEach(patronId -> createExpiredEndSession(patronId, CHECK_OUT));

    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);

    waitAtLeast(1, SECONDS)
      .until(patronSessionRecordsClient::getAll, empty());


    Stream.of(CHECK_OUT_TEMPLATE_ID, CHECK_IN_TEMPLATE_ID).forEach(templateId ->
      Stream.of(james, jessica, steve).forEach(patron ->
        FakeModNotify.getSentPatronNotices().stream()
          .filter(pn -> pn.getString("templateId").equals(templateId.toString()))
          .filter(pn -> pn.getString("recipientId").equals(patron.getId().toString()))
          .forEach(patronNotice -> {
            Map<String, Matcher<String>> matchers = TemplateContextMatchers.getUserContextMatchers(patron);
            matchers.put("loans[0].loan.additionalInfo", Is.is(LOAN_INFO_ADDED));
            assertThat(patronNotice, hasEmailNoticeProperties(patron.getId(), templateId, matchers));
          })));

    verifyNumberOfSentNotices(6);
    verifyNumberOfPublishedEvents(NOTICE, 6);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void expiredSessionWithNonExistentLoanShouldBeEnded() {
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

    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  void expiredSessionWithNonExistentItemShouldBeEnded() {
    IndividualResource james = usersFixture.james();
    ItemResource nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod, james);
    expiredEndSessionClient.deleteAll();

    List<JsonObject> sessions = patronSessionRecordsClient.getAll();
    assertThat(sessions, hasSize(1));

    sessions.get(0).getString(LOAN_ID);
    String patronId = sessions.get(0).getString(PATRON_ID);

    itemsClient.delete(nod);
    createExpiredEndSession(patronId, CHECK_OUT);

    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);

    waitAtLeast(1, SECONDS)
      .until(patronSessionRecordsClient::getAll, empty());

    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  void shouldNotFailIfSessionRecordsAreEmpty() {
    createExpiredEndSession(UUID.randomUUID().toString(), CHECK_OUT);

    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);

    assertThat(patronSessionRecordsClient.getAll(), empty());

    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void expiredSessionWithNonExistentUserShouldBeEnded() {
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

    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  void shouldNotFailWithUriTooLargeErrorDuringEndingExpiredCheckOutSessions() {
    checkThatBunchOfExpiredSessionsWereAddedAndRemovedByTimer(100, CHECK_OUT);
  }

  @Test
  void shouldNotFailWithUriTooLargeErrorDuringEndingExpiredCheckInSessions() {
    checkThatBunchOfExpiredSessionsWereAddedAndRemovedByTimer(100, CHECK_IN);
  }

  @Test
  void sessionsWithNotSpecifiedActionTypeShouldBeEnded() {
    checkThatBunchOfExpiredSessionsWereAddedAndRemovedByTimer(100, "");
  }

  @Test
  void patronNoticeContextContainsUserTokensWhenNoticeIsTriggeredByExpiredSession() {
    IndividualResource james = usersFixture.james();
    ItemResource nod = itemsFixture.basedUponNod();
    checkOutAndAddPatronNotice(nod, james);

    patronSessionRecordsClient.getAll().stream()
      .filter(session -> session.getMap().get(ACTION_TYPE)
        .equals(PatronActionType.CHECK_OUT.getRepresentation()))
      .map(session -> session.getString(PATRON_ID))
      .forEach(patronId -> createExpiredEndSession(patronId, CHECK_OUT));

    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);

    waitAtMost(1, SECONDS)
      .until(patronSessionRecordsClient::getAll, empty());

    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    Map<String, Matcher<String>> matchers = TemplateContextMatchers.getUserContextMatchers(james);
    matchers.put("loans[0].loan.additionalInfo", Is.is(LOAN_INFO_ADDED));
    assertThat(FakeModNotify.getFirstSentPatronNotice(),
      hasEmailNoticeProperties(james.getId(), CHECK_OUT_TEMPLATE_ID,
        matchers));
  }

  @Test
  void expiredSessionWithNonExistentItemIdShouldBeEnded() {
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

    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  void expiredSessionClosedWithSuccessWhenPatronGroupIdIsNull() {
    IndividualResource james = usersFixture.james();
    ItemResource nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod, james);
    JsonObject user = james.getJson();
    user.put("patronGroup", null);
    usersClient.replace(james.getId(), user);
    List<JsonObject> sessions = patronSessionRecordsClient.getAll();
    assertThat(sessions, hasSize(1));
    String patronId = sessions.get(0).getString(PATRON_ID);
    createExpiredEndSession(patronId, CHECK_OUT);
    expiredSessionProcessingClient.runRequestExpiredSessionsProcessing(204);
    assertThat(patronSessionRecordsClient.getAll(), empty());
  }

  private void checkOutAndAddPatronNotice(ItemResource item, IndividualResource user){
    IndividualResource loan = checkOutFixture.checkOutByBarcode(item, user);
    addInfoFixture.addInfo(new AddInfoRequestBuilder(loan.getId().toString(),
      "patronInfoAdded", LOAN_INFO_ADDED));
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
