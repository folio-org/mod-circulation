package api.loans;

import static api.support.fixtures.TemplateContextMatchers.getLoanAdditionalInfoContextMatchers;
import static api.support.fixtures.TemplateContextMatchers.getLoanPolicyContextMatchersForUnlimitedRenewals;
import static api.support.fixtures.TemplateContextMatchers.getMultipleLoansContextMatcher;
import static api.support.matchers.JsonObjectMatcher.toStringMatcher;
import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfExistingActionSessions;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfPublishedEvents;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfSentNotices;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.ACTION_TYPE;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.LOAN_ID;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.PATRON_ID;
import static org.folio.circulation.domain.notice.session.PatronActionType.CHECK_IN;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE_ERROR;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import api.support.builders.AddInfoRequestBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.circulation.support.http.client.Response;
import org.hamcrest.Matcher;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.fakes.FakeModNotify;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.UserResource;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class PatronActionSessionTests extends APITests {

  private static final UUID CHECK_OUT_NOTICE_TEMPLATE_ID = UUID.fromString("72e7683b-76c2-4ee2-85c2-2fbca8fbcfd8");
  private static final UUID CHECK_IN_NOTICE_TEMPLATE_ID = UUID.fromString("72e7683b-76c2-4ee2-85c2-2fbca8fbcfd9");

  @BeforeEach
  public void beforeEach() {
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
  void cannotEndSessionWhenPatronIdIsNotSpecified() {
    JsonObject body = new JsonObject()
      .put(ACTION_TYPE, "Check-out");

    Response response = endPatronSessionClient.attemptEndPatronSession(wrapInObjectWithArray(body));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("End patron session request must have patron id"))));
  }

  @Test
  void cannotEndSessionWhenActionTypeIsNotSpecified() {
    JsonObject body = new JsonObject()
      .put(PATRON_ID, UUID.randomUUID().toString());

    Response response = endPatronSessionClient.attemptEndPatronSession(wrapInObjectWithArray(body));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("End patron session request must have action type"))));
  }

  @Test
  void cannotEndSessionWhenActionTypeIsNotValid() {
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
  void checkOutNoticeWithMultipleItemsIsSentWhenCorrespondingSessionIsEnded() {
    IndividualResource james = usersFixture.james();
    ItemResource nod = itemsFixture.basedUponNod();
    ItemResource interestingTimes = itemsFixture.basedUponInterestingTimes();
    IndividualResource nodToJamesLoan = checkOutFixture.checkOutByBarcode(nod, james);
    IndividualResource interestingTimesToJamesLoan = checkOutFixture.checkOutByBarcode(interestingTimes, james);
    String infoAdded = "testing patron info";
    addInfoFixture.addInfo(new AddInfoRequestBuilder(nodToJamesLoan.getId().toString(),
      "patronInfoAdded", infoAdded));
    addInfoFixture.addInfo(new AddInfoRequestBuilder(interestingTimesToJamesLoan.getId().toString(),
      "patronInfoAdded", infoAdded));

    verifyNumberOfExistingActionSessions(2);

    endPatronSessionClient.endCheckOutSession(james.getId());

    //Wait until session records are deleted
    verifyNumberOfExistingActionSessions(0);
    Map<String, Matcher<String>> noticeContextMatchers = getLoanPolicyContextMatchersForUnlimitedRenewals();
    noticeContextMatchers.putAll(getLoanAdditionalInfoContextMatchers(infoAdded));
    final var multipleLoansToJamesContextMatcher = getMultipleLoansContextMatcher(james,
      Arrays.asList(Pair.of(nodToJamesLoan, nod), Pair.of(interestingTimesToJamesLoan, interestingTimes)),
      toStringMatcher(noticeContextMatchers));

    assertThat(FakeModNotify.getSentPatronNotices(), hasItems(
      hasEmailNoticeProperties(james.getId(), CHECK_OUT_NOTICE_TEMPLATE_ID, multipleLoansToJamesContextMatcher)));

    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void checkOutSessionIsNotEndedSentWhenSessionEndsForDifferentUser() {
    IndividualResource patronForCheckOut = usersFixture.james();
    IndividualResource otherPatron = usersFixture.jessica();

    checkOutFixture.checkOutByBarcode(itemsFixture.basedUponNod(), patronForCheckOut);
    endPatronSessionClient.endCheckOutSession(otherPatron.getId());

    verifyNumberOfExistingActionSessions(1);
    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void checkOutSessionIsNotEndedWhenCheckInSessionEnds() {
    IndividualResource james = usersFixture.james();

    checkOutFixture.checkOutByBarcode(itemsFixture.basedUponNod(), james);
    assertThat(patronSessionRecordsClient.getAll(), hasSize(1));

    endPatronSessionClient.endCheckInSession(james.getId());

    //Waits to ensure check-out session records are not deleted and no notices are sent
    verifyNumberOfExistingActionSessions(1);
    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void checkInSessionShouldBeCreatedWhenLoanedItemIsCheckedInByBarcode() {
    IndividualResource james = usersFixture.james();
    UUID checkInServicePointId = servicePointsFixture.cd1().getId();
    ItemResource nod = itemsFixture.basedUponNod();

    IndividualResource loan = checkOutFixture.checkOutByBarcode(nod, james);

    checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePointId));

    verifyNumberOfExistingActionSessions(2);

    List<JsonObject> checkInSessions = verifyNumberOfExistingActionSessions(1, CHECK_IN);

    JsonObject checkInSession = checkInSessions.get(0);
    assertThat(checkInSession.getString(PATRON_ID), is(james.getId().toString()));
    assertThat(checkInSession.getString(LOAN_ID), is(loan.getId().toString()));
  }

  @Test
  void checkInSessionShouldNotBeCreatedWhenItemWithoutOpenLoanIsCheckedInByBarcode() {
    UUID checkInServicePointId = servicePointsFixture.cd1().getId();
    ItemResource nod = itemsFixture.basedUponNod();

    checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePointId));

    verifyNumberOfExistingActionSessions(0);
  }

  @Test
  void patronNoticesShouldBeSentWhenCheckInSessionIsEnded() {
    IndividualResource steve = usersFixture.steve();
    UUID checkInServicePointId = servicePointsFixture.cd1().getId();
    ItemResource nod = itemsFixture.basedUponNod();

    IndividualResource loan = checkOutFixture.checkOutByBarcode(nod, steve);
    checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePointId));

    verifyNumberOfExistingActionSessions(1, CHECK_IN);
    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    String infoAdded = "testing patron info";
    addInfoFixture.addInfo(new AddInfoRequestBuilder(loan.getId().toString(),
      "patronInfoAdded", infoAdded));

    endPatronSessionClient.endCheckInSession(steve.getId());

    //Wait until session records are deleted
    verifyNumberOfExistingActionSessions(0, CHECK_IN);
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    Map<String, Matcher<String>> noticeContextMatchers = new HashMap<>();
    noticeContextMatchers.put("loans[0].loan.additionalInfo", Is.is(infoAdded));
    assertThat(FakeModNotify.getSentPatronNotices(), hasItems(
      hasEmailNoticeProperties(steve.getId(), CHECK_IN_NOTICE_TEMPLATE_ID, noticeContextMatchers)));
  }

  @Test
  void checkOutSessionWithNonExistentLoanShouldBeEnded() {
    IndividualResource james = usersFixture.james();
    ItemResource nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod, james);
    List<JsonObject> sessions = verifyNumberOfExistingActionSessions(1);
    String loanId = sessions.get(0).getString(LOAN_ID);
    loansFixture.deleteLoan(UUID.fromString(loanId));
    endPatronSessionClient.endCheckOutSession(james.getId());

    verifyNumberOfExistingActionSessions(0);
    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  void checkOutSessionWithNonExistentItemShouldBeEnded() {
    IndividualResource james = usersFixture.james();
    ItemResource nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod, james);
    List<JsonObject> sessions = verifyNumberOfExistingActionSessions(1);
    UUID loanId = UUID.fromString(sessions.get(0).getString(LOAN_ID));
    IndividualResource loan = loansFixture.getLoanById(loanId);
    itemsClient.delete(UUID.fromString(loan.getJson().getString("itemId")));
    endPatronSessionClient.endCheckOutSession(james.getId());

    verifyNumberOfExistingActionSessions(0);
    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  void checkOutSessionWithNonExistentUserShouldBeEnded() {
    UserResource steve = usersFixture.steve();
    ItemResource nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod, steve);
    verifyNumberOfExistingActionSessions(1);
    usersFixture.remove(steve);
    endPatronSessionClient.endCheckOutSession(steve.getId());

    verifyNumberOfExistingActionSessions(0);
    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  void checkInSessionWithNonExistentLoanShouldBeEnded() {
    IndividualResource james = usersFixture.james();
    ItemResource nod = itemsFixture.basedUponNod();
    UUID checkInServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(nod, james);
    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(nod)
      .at(checkInServicePointId));
    List<JsonObject> sessions = verifyNumberOfExistingActionSessions(1, CHECK_IN);
    String loanId = sessions.get(0).getString(LOAN_ID);
    loansFixture.deleteLoan(UUID.fromString(loanId));
    endPatronSessionClient.endCheckInSession(james.getId());

    verifyNumberOfExistingActionSessions(0, CHECK_IN);
    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  void checkInSessionWithNonExistentItemShouldBeEnded() {
    IndividualResource james = usersFixture.james();
    ItemResource nod = itemsFixture.basedUponNod();
    UUID checkInServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(nod, james);
    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(nod)
      .at(checkInServicePointId));
    List<JsonObject> sessions = verifyNumberOfExistingActionSessions(1, CHECK_IN);
    UUID loanId = UUID.fromString(sessions.get(0).getString(LOAN_ID));
    IndividualResource loan = loansFixture.getLoanById(loanId);
    itemsClient.delete(UUID.fromString(loan.getJson().getString("itemId")));
    endPatronSessionClient.endCheckInSession(james.getId());

    verifyNumberOfExistingActionSessions(0, CHECK_IN);
    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  void checkInSessionWithNonExistentUserShouldBeEnded() {
    UserResource steve = usersFixture.steve();
    ItemResource nod = itemsFixture.basedUponNod();
    UUID checkInServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(nod, steve);
    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(nod)
      .at(checkInServicePointId));
    verifyNumberOfExistingActionSessions(1, CHECK_IN);
    usersFixture.remove(steve);
    endPatronSessionClient.endCheckInSession(steve.getId());

    verifyNumberOfExistingActionSessions(0, CHECK_IN);
    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  void checkInSessionShouldNotBeDeletedWhenPatronNoticeRequestFails() {
    UserResource steve = usersFixture.steve();
    ItemResource nod = itemsFixture.basedUponNod();
    UUID checkInServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(nod, steve);
    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(nod)
      .at(checkInServicePointId));

    verifyNumberOfExistingActionSessions(1, CHECK_IN);

    FakeModNotify.setFailPatronNoticesWithBadRequest(true);

    endPatronSessionClient.endCheckInSession(steve.getId());

    verifyNumberOfExistingActionSessions(0, CHECK_IN);
    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  void noticeForOneCheckInSessionIsSentWhenOtherCheckInSessionForSameUserIsInvalid() {
    UserResource steve = usersFixture.steve();
    ItemResource nod = itemsFixture.basedUponNod();
    ItemResource dunkirk = itemsFixture.basedUponDunkirk();
    UUID checkInServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(nod, steve);
    checkOutFixture.checkOutByBarcode(dunkirk, steve);

    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(nod)
      .at(checkInServicePointId));

    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(dunkirk)
      .at(checkInServicePointId));

    verifyNumberOfExistingActionSessions(2, CHECK_IN);
    itemsClient.delete(dunkirk);
    endPatronSessionClient.endCheckInSession(steve.getId());

    verifyNumberOfExistingActionSessions(0, CHECK_IN);
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  private JsonObject wrapInObjectWithArray(JsonObject body) {
    JsonArray jsonArray = new JsonArray().add(body);
    return new JsonObject().put("endSessions", jsonArray);
  }
}
