package api.loans;

import static api.support.Wait.waitAtLeast;
import static api.support.fakes.PublishedEvents.byLogEventType;
import static api.support.fixtures.TemplateContextMatchers.getLoanPolicyContextMatchersForUnlimitedRenewals;
import static api.support.fixtures.TemplateContextMatchers.getMultipleLoansContextMatcher;
import static api.support.matchers.JsonObjectMatcher.toStringMatcher;
import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.waitAtMost;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.ACTION_TYPE;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.LOAN_ID;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.PATRON_ID;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import api.support.http.UserResource;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.circulation.support.http.client.Response;
import org.junit.Before;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.fakes.FakePubSub;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class PatronActionSessionTests extends APITests {

  private static final UUID CHECK_OUT_NOTICE_TEMPLATE_ID = UUID.fromString("72e7683b-76c2-4ee2-85c2-2fbca8fbcfd8");
  private static final UUID CHECK_IN_NOTICE_TEMPLATE_ID = UUID.fromString("72e7683b-76c2-4ee2-85c2-2fbca8fbcfd9");

  @Before
  public void before() {
    JsonObject checkOutNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(CHECK_OUT_NOTICE_TEMPLATE_ID)
      .withCheckOutEvent()
      .create();

    JsonObject checkInNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(CHECK_IN_NOTICE_TEMPLATE_ID)
      .withCheckInEvent()
      .create();

    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with check-out notice")
      .withLoanNotices(Arrays.asList(checkOutNoticeConfig, checkInNoticeConfig));

    useFallbackPolicies(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());
  }

  @Test
  public void cannotEndSessionWhenPatronIdIsNotSpecified() {
    JsonObject body = new JsonObject()
      .put(ACTION_TYPE, "Check-out");

    Response response = endPatronSessionClient.attemptEndPatronSession(wrapInObjectWithArray(body));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("End patron session request must have patron id"))));
  }

  @Test
  public void cannotEndSessionWhenActionTypeIsNotSpecified() {
    JsonObject body = new JsonObject()
      .put(PATRON_ID, UUID.randomUUID().toString());

    Response response = endPatronSessionClient.attemptEndPatronSession(wrapInObjectWithArray(body));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("End patron session request must have action type"))));
  }

  @Test
  public void cannotEndSessionWhenActionTypeIsNotValid() {
    String invalidActionType = "invalidActionType";

    JsonObject body = new JsonObject()
      .put(PATRON_ID, UUID.randomUUID().toString())
      .put(ACTION_TYPE, invalidActionType);

    Response response = endPatronSessionClient.attemptEndPatronSession(wrapInObjectWithArray(body));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Invalid patron action type value"),
      hasParameter("actionType", invalidActionType))));
  }

  @Test
  public void checkOutNoticeWithMultipleItemsIsSentWhenCorrespondingSessionIsEnded() {
    IndividualResource james = usersFixture.james();
    ItemResource nod = itemsFixture.basedUponNod();
    ItemResource interestingTimes = itemsFixture.basedUponInterestingTimes();
    IndividualResource nodToJamesLoan = checkOutFixture.checkOutByBarcode(nod, james);
    IndividualResource interestingTimesToJamesLoan = checkOutFixture.checkOutByBarcode(interestingTimes, james);

    assertThat(patronSessionRecordsClient.getAll(), hasSize(2));

    endPatronSessionClient.endCheckOutSession(james.getId());

    //Wait until session records are deleted
    waitAtLeast(1, SECONDS)
      .until(patronSessionRecordsClient::getAll, empty());

    final var sentNotices = patronNoticesClient.getAll();

    assertThat(sentNotices, hasSize(1));
    assertThat(FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE.value())), hasSize(1));

    final var multipleLoansToJamesContextMatcher = getMultipleLoansContextMatcher(james,
      Arrays.asList(Pair.of(nodToJamesLoan, nod), Pair.of(interestingTimesToJamesLoan, interestingTimes)),
      toStringMatcher(getLoanPolicyContextMatchersForUnlimitedRenewals()));

    assertThat(sentNotices, hasItems(
      hasEmailNoticeProperties(james.getId(), CHECK_OUT_NOTICE_TEMPLATE_ID, multipleLoansToJamesContextMatcher)));
  }

  @Test
  public void checkOutSessionIsNotEndedSentWhenSessionEndsForDifferentUser() {
    IndividualResource patronForCheckOut = usersFixture.james();
    IndividualResource otherPatron = usersFixture.jessica();

    checkOutFixture.checkOutByBarcode(itemsFixture.basedUponNod(), patronForCheckOut);
    endPatronSessionClient.endCheckOutSession(otherPatron.getId());

    waitAtMost(1, SECONDS)
      .until(patronSessionRecordsClient::getAll, hasSize(1));

    assertThat(patronNoticesClient.getAll(), empty());
    assertThat(FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE.value())), empty());
  }

  @Test
  public void checkOutSessionIsNotEndedWhenCheckInSessionEnds() {
    IndividualResource james = usersFixture.james();

    checkOutFixture.checkOutByBarcode(itemsFixture.basedUponNod(), james);
    assertThat(patronSessionRecordsClient.getAll(), hasSize(1));

    endPatronSessionClient.endCheckInSession(james.getId());

    //Waits to ensure check-out session records are not deleted and no notices are sent
    waitAtMost(1, SECONDS)
      .until(patronSessionRecordsClient::getAll, hasSize(1));

    assertThat(patronNoticesClient.getAll(), empty());
    assertThat(FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE.value())), empty());
  }

  @Test
  public void checkInSessionShouldBeCreatedWhenLoanedItemIsCheckedInByBarcode() {
    IndividualResource james = usersFixture.james();
    UUID checkInServicePointId = servicePointsFixture.cd1().getId();
    ItemResource nod = itemsFixture.basedUponNod();

    IndividualResource loan = checkOutFixture.checkOutByBarcode(nod, james);

    checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePointId));

    assertThat(patronSessionRecordsClient.getAll(), hasSize(2));

    List<JsonObject> checkInSessions = getCheckInSessions();
    assertThat(checkInSessions, hasSize(1));

    JsonObject checkInSession = checkInSessions.get(0);
    assertThat(checkInSession.getString(PATRON_ID), is(james.getId().toString()));
    assertThat(checkInSession.getString(LOAN_ID), is(loan.getId().toString()));
  }

  @Test
  public void checkInSessionShouldNotBeCreatedWhenItemWithoutOpenLoanIsCheckedInByBarcode() {
    UUID checkInServicePointId = servicePointsFixture.cd1().getId();
    ItemResource nod = itemsFixture.basedUponNod();

    checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePointId));

    assertThat(patronSessionRecordsClient.getAll(), empty());
  }

  @Test
  public void patronNoticesShouldBeSentWhenCheckInSessionIsEnded() {
    IndividualResource steve = usersFixture.steve();
    UUID checkInServicePointId = servicePointsFixture.cd1().getId();
    ItemResource nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod, steve);
    checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePointId));

    List<JsonObject> checkInSessions = getCheckInSessions();
    assertThat(checkInSessions, hasSize(1));

    assertThat(patronNoticesClient.getAll(), empty());
    assertThat(FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE.value())), empty());

    endPatronSessionClient.endCheckInSession(steve.getId());

    //Wait until session records are deleted
    waitAtLeast(1, SECONDS)
      .until(this::getCheckInSessions, empty());

    assertThat(patronNoticesClient.getAll(), hasSize(1));
    assertThat(FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE.value())), hasSize(1));
  }

  @Test
  public void checkOutSessionWithNonExistentLoanShouldBeEnded() {
    IndividualResource james = usersFixture.james();
    ItemResource nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod, james);
    List<JsonObject> sessions = patronSessionRecordsClient.getAll();
    assertThat(sessions, hasSize(1));
    String loanId = sessions.get(0).getString(LOAN_ID);
    loansFixture.deleteLoan(UUID.fromString(loanId));
    endPatronSessionClient.endCheckOutSession(james.getId());

    waitAtMost(1, SECONDS)
      .until(patronSessionRecordsClient::getAll, empty());
    assertThat(patronNoticesClient.getAll(), hasSize(0));
    assertThat(FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE.value())), hasSize(patronNoticesClient.getAll().size()));
  }

  @Test
  public void checkOutSessionWithNonExistentItemShouldBeEnded() {
    IndividualResource james = usersFixture.james();
    ItemResource nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod, james);
    List<JsonObject> sessions = patronSessionRecordsClient.getAll();
    assertThat(sessions, hasSize(1));
    UUID loanId = UUID.fromString(sessions.get(0).getString(LOAN_ID));
    IndividualResource loan = loansFixture.getLoanById(loanId);
    itemsClient.delete(UUID.fromString(loan.getJson().getString("itemId")));
    endPatronSessionClient.endCheckOutSession(james.getId());

    waitAtMost(1, SECONDS)
      .until(patronSessionRecordsClient::getAll, empty());
    assertThat(patronNoticesClient.getAll(), hasSize(0));
    assertThat(FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE.value())), hasSize(patronNoticesClient.getAll().size()));
  }

  @Test
  public void checkOutSessionWithNonExistentUserShouldBeEnded() {
    UserResource steve = usersFixture.steve();
    ItemResource nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod, steve);
    List<JsonObject> sessions = patronSessionRecordsClient.getAll();
    assertThat(sessions, hasSize(1));
    usersFixture.remove(steve);
    endPatronSessionClient.endCheckOutSession(steve.getId());

    waitAtMost(1, SECONDS)
      .until(patronSessionRecordsClient::getAll, empty());
    assertThat(patronNoticesClient.getAll(), hasSize(0));
    assertThat(FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE.value())), hasSize(patronNoticesClient.getAll().size()));
  }

  @Test
  public void checkInSessionWithNonExistentLoanShouldBeEnded() {
    IndividualResource james = usersFixture.james();
    ItemResource nod = itemsFixture.basedUponNod();
    UUID checkInServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(nod, james);
    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(nod)
      .at(checkInServicePointId));
    List<JsonObject> sessions = getCheckInSessions();
    assertThat(sessions, hasSize(1));
    String loanId = sessions.get(0).getString(LOAN_ID);
    loansFixture.deleteLoan(UUID.fromString(loanId));
    endPatronSessionClient.endCheckInSession(james.getId());

    waitAtMost(1, SECONDS)
      .until(this::getCheckInSessions, empty());
    assertThat(patronNoticesClient.getAll(), hasSize(0));
    assertThat(FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE.value())), hasSize(patronNoticesClient.getAll().size()));
  }

  @Test
  public void checkInSessionWithNonExistentItemShouldBeEnded() {
    IndividualResource james = usersFixture.james();
    ItemResource nod = itemsFixture.basedUponNod();
    UUID checkInServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(nod, james);
    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(nod)
      .at(checkInServicePointId));
    List<JsonObject> sessions = getCheckInSessions();
    assertThat(sessions, hasSize(1));
    UUID loanId = UUID.fromString(sessions.get(0).getString(LOAN_ID));
    IndividualResource loan = loansFixture.getLoanById(loanId);
    itemsClient.delete(UUID.fromString(loan.getJson().getString("itemId")));
    endPatronSessionClient.endCheckInSession(james.getId());

    waitAtMost(1, SECONDS)
      .until(this::getCheckInSessions, empty());
    assertThat(patronNoticesClient.getAll(), hasSize(0));
    assertThat(FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE.value())), hasSize(patronNoticesClient.getAll().size()));
  }

  @Test
  public void checkInSessionWithNonExistentUserShouldBeEnded() {
    UserResource steve = usersFixture.steve();
    ItemResource nod = itemsFixture.basedUponNod();
    UUID checkInServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(nod, steve);
    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(nod)
      .at(checkInServicePointId));
    List<JsonObject> sessions = getCheckInSessions();
    assertThat(sessions, hasSize(1));
    usersFixture.remove(steve);
    endPatronSessionClient.endCheckInSession(steve.getId());

    waitAtMost(1, SECONDS)
      .until(this::getCheckInSessions, empty());
    assertThat(patronNoticesClient.getAll(), hasSize(0));
    assertThat(FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE.value())), hasSize(patronNoticesClient.getAll().size()));
  }

  private List<JsonObject> getCheckInSessions() {
    Predicate<JsonObject> isCheckInSession = json -> json.getString(ACTION_TYPE).equals("Check-in");

    return patronSessionRecordsClient.getAll().stream()
      .filter(isCheckInSession)
      .collect(Collectors.toList());
  }

  private JsonObject wrapInObjectWithArray(JsonObject body) {
    JsonArray jsonArray = new JsonArray().add(body);
    return new JsonObject().put("endSessions", jsonArray);
  }
}
