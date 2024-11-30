package api.loans.scenarios;

import static api.support.PubsubPublisherTestUtils.assertThatPublishedLoanLogRecordEventsAreValid;
import static api.support.fakes.PublishedEvents.byEventType;
import static api.support.fixtures.TemplateContextMatchers.getItemContextMatchers;
import static api.support.fixtures.TemplateContextMatchers.getLoanAdditionalInfoContextMatchers;
import static api.support.fixtures.TemplateContextMatchers.getLoanContextMatchers;
import static api.support.fixtures.TemplateContextMatchers.getLoanPolicyContextMatchers;
import static api.support.fixtures.TemplateContextMatchers.getUserContextMatchers;
import static api.support.matchers.EventMatchers.isValidLoanDueDateChangedEvent;
import static api.support.matchers.EventTypeMatchers.LOAN_DUE_DATE_CHANGED;
import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasNullParameter;
import static api.support.matchers.ValidationErrorMatchers.hasUUIDParameter;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfPublishedEvents;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfSentNotices;
import static java.time.ZoneOffset.UTC;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.HttpStatus.HTTP_NOT_FOUND;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.circulation.domain.policy.Period.months;
import static org.folio.circulation.domain.policy.Period.weeks;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE_ERROR;
import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTime;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import api.support.builders.AddInfoRequestBuilder;
import org.awaitility.Awaitility;
import org.folio.circulation.support.http.client.Response;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import api.support.APITests;
import api.support.TlrFeatureStatus;
import api.support.builders.ChangeDueDateRequestBuilder;
import api.support.builders.ClaimItemReturnedRequestBuilder;
import api.support.builders.ItemBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.LostItemFeePolicyBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.builders.RequestBuilder;
import api.support.fakes.FakeModNotify;
import api.support.fakes.FakePubSub;
import api.support.fixtures.ItemExamples;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import io.vertx.core.json.JsonObject;

class ChangeDueDateAPITests extends APITests {
  private ItemResource item;
  private IndividualResource loan;
  private ZonedDateTime dueDate;

  @BeforeEach
  public void setUpItemAndLoan() {
    chargeFeesForLostItemToKeepLoanOpen();

    item = itemsFixture.basedUponNod();
    loan = checkOutFixture.checkOutByBarcode(item);
    dueDate = ZonedDateTime.parse(loan.getJson().getString("dueDate"));
  }

  @Test
  void canChangeTheDueDate() {
    final ZonedDateTime newDueDate = dueDate.plusDays(14);

    changeDueDateFixture.changeDueDate(new ChangeDueDateRequestBuilder()
      .forLoan(loan.getId())
      .withDueDate(newDueDate));

    Response response = loansClient.getById(loan.getId());

    JsonObject updatedLoan = response.getJson();

    var description = new JsonObject(FakePubSub.getPublishedEvents().stream()
      .filter(json -> json.getString("eventType").equals("LOG_RECORD")
      && json.getString("eventPayload").contains("LOAN")).collect(toList()).get(0).getString("eventPayload"))
      .getJsonObject("payload").getString("description");

    assertThat(description, notNullValue());
    assertThat(description, containsString(formatDateTime(dueDate)));
    assertThat(description, containsString(formatDateTime(newDueDate)));

    assertThat("due date is not updated",
      updatedLoan.getString("dueDate"), isEquivalentTo(newDueDate));
  }

  @Test
  void cannotChangeDueDateWhenDueDateIsNotProvided() {
    final Response response = changeDueDateFixture
      .attemptChangeDueDate(new ChangeDueDateRequestBuilder()
        .forLoan(loan.getId())
        .withDueDate(null));

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("A new due date is required in order to change the due date"),
      hasNullParameter("dueDate"))));
  }

  @Test
  void cannotChangeDueDateWhenLoanIsNotFound() {
    final String nonExistentLoanId = UUID.randomUUID().toString();
    final ZonedDateTime newDueDate = dueDate.plusDays(14);

    final Response response = changeDueDateFixture
      .attemptChangeDueDate(new ChangeDueDateRequestBuilder()
        .forLoan(nonExistentLoanId)
        .withDueDate(newDueDate));

    assertThat(response, hasStatus(HTTP_NOT_FOUND));
  }

  @Test
  void cannotChangeDueDateWhenLoanIsClosed() {
    final ZonedDateTime newDueDate = dueDate.plusDays(14);

    checkInFixture.checkInByBarcode(item);

    final Response response = changeDueDateFixture
      .attemptChangeDueDate(new ChangeDueDateRequestBuilder()
        .forLoan(loan.getId())
        .withDueDate(newDueDate));

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Loan is closed"),
      hasUUIDParameter("loanId", loan.getId()))));
  }

  @Test
  void shouldRejectDueDateChangeWhenItemIsInDisallowedStatus() {
    final ZonedDateTime newDueDate = dueDate.plusDays(14);

    claimItemReturnedFixture.claimItemReturned(new ClaimItemReturnedRequestBuilder()
      .forLoan(loan.getId().toString()));

    new ChangeDueDateRequestBuilder().forLoan(loan.getId().toString());

    final Response response = changeDueDateFixture
      .attemptChangeDueDate(new ChangeDueDateRequestBuilder()
        .forLoan(loan.getId())
        .withDueDate(newDueDate));

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("item is Claimed returned"),
      hasUUIDParameter("itemId", item.getId()))));
  }

  @Test
  void canChangeDueDateWithOpenRequest() {
    final ZonedDateTime newDueDate = dueDate.plusDays(14);

    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(item)
      .by(usersFixture.steve())
      .fulfillToHoldShelf(servicePointsFixture.cd1()));

    changeDueDateFixture.changeDueDate(new ChangeDueDateRequestBuilder()
      .forLoan(loan.getId())
      .withDueDate(newDueDate));

    Response response = loansClient.getById(loan.getId());

    JsonObject updatedLoan = response.getJson();

    assertThat("due date should have been updated",
      updatedLoan.getString("dueDate"), isEquivalentTo(newDueDate));
  }

  @Test
  void changeDueDateNoticeIsSentWhenPolicyIsDefined() {
    UUID templateId = UUID.randomUUID();

    JsonObject changeNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withManualDueDateChangeEvent()
      .create();

    JsonObject checkInNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(UUID.randomUUID())
      .withCheckInEvent()
      .create();

    IndividualResource noticePolicy = noticePoliciesFixture.create(
      new NoticePolicyBuilder()
        .withName("Policy with manual due date change notice")
        .withLoanNotices(Arrays.asList(
          changeNoticeConfiguration, checkInNoticeConfiguration)));

    int renewalLimit = 3;
    IndividualResource policyWithLimitedRenewals = loanPoliciesFixture.create(
      new LoanPolicyBuilder()
        .withName("Limited renewals loan policy")
        .rolling(months(1))
        .limitedRenewals(renewalLimit));

    useFallbackPolicies(
      policyWithLimitedRenewals.getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePolicy.getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    ItemBuilder itemBuilder = ItemExamples.basedUponSmallAngryPlanet(
      materialTypesFixture.book().getId(), loanTypesFixture.canCirculate().getId(),
      EMPTY, "ItemPrefix", "ItemSuffix", "");

    ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      itemBuilder, itemsFixture.thirdFloorHoldings());

    IndividualResource steve = usersFixture.steve();

    IndividualResource loan = checkOutFixture.checkOutByBarcode(smallAngryPlanet, steve);
    String infoAdded = "testing patron info";
    addInfoFixture.addInfo(new AddInfoRequestBuilder(loan.getId().toString(),
      "patronInfoAdded", infoAdded));

    ZonedDateTime newDueDate = dueDate.plusWeeks(2);

    changeDueDateFixture.changeDueDate(new ChangeDueDateRequestBuilder()
      .forLoan(loan.getId())
      .withDueDate(newDueDate));

    IndividualResource loanAfterUpdate = loansClient.get(loan);

    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    Map<String, Matcher<String>> matchers = new HashMap<>();

    matchers.putAll(getUserContextMatchers(steve));
    matchers.putAll(getItemContextMatchers(smallAngryPlanet, true));
    matchers.putAll(getLoanContextMatchers(loanAfterUpdate));
    matchers.putAll(getLoanPolicyContextMatchers(renewalLimit, renewalLimit));
    matchers.putAll(getLoanAdditionalInfoContextMatchers(infoAdded));

    assertThat(FakeModNotify.getSentPatronNotices(), hasItems(
      hasEmailNoticeProperties(steve.getId(), templateId, matchers)));
  }

  @Test
  void changeDueDateNoticeIsNotSentWhenPatronNoticeRequestFails() {
    UUID templateId = UUID.randomUUID();

    JsonObject changeNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withManualDueDateChangeEvent()
      .create();

    JsonObject checkInNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(UUID.randomUUID())
      .withCheckInEvent()
      .create();

    IndividualResource noticePolicy = noticePoliciesFixture.create(
      new NoticePolicyBuilder()
        .withName("Policy with manual due date change notice")
        .withLoanNotices(Arrays.asList(
          changeNoticeConfiguration, checkInNoticeConfiguration)));

    int renewalLimit = 3;
    IndividualResource policyWithLimitedRenewals = loanPoliciesFixture.create(
      new LoanPolicyBuilder()
        .withName("Limited renewals loan policy")
        .rolling(months(1))
        .limitedRenewals(renewalLimit));

    useFallbackPolicies(
      policyWithLimitedRenewals.getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePolicy.getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    ItemBuilder itemBuilder = ItemExamples.basedUponSmallAngryPlanet(
      materialTypesFixture.book().getId(), loanTypesFixture.canCirculate().getId(),
      EMPTY, "ItemPrefix", "ItemSuffix", "");

    ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      itemBuilder, itemsFixture.thirdFloorHoldings());

    IndividualResource steve = usersFixture.steve();

    IndividualResource loan = checkOutFixture.checkOutByBarcode(smallAngryPlanet, steve);

    ZonedDateTime newDueDate = dueDate.plusWeeks(2);

    FakeModNotify.setFailPatronNoticesWithBadRequest(true);

    changeDueDateFixture.changeDueDate(new ChangeDueDateRequestBuilder()
      .forLoan(loan.getId())
      .withDueDate(newDueDate));

    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  void dueDateChangedEventIsPublished() {
    final ZonedDateTime newDueDate = dueDate.plusDays(14);
    changeDueDateFixture.changeDueDate(new ChangeDueDateRequestBuilder()
      .forLoan(loan.getId())
      .withDueDate(newDueDate));

    Response response = loansClient.getById(loan.getId());
    JsonObject updatedLoan = response.getJson();

    // There should be three five published - first one for "check out",
    // second one for "log event", third one for "change due date"
    // and one "log record"
    final var publishedEvents = Awaitility.await()
      .atMost(1, SECONDS)
      .until(FakePubSub::getPublishedEvents, hasSize(4));

    final var event = publishedEvents.findFirst(byEventType(LOAN_DUE_DATE_CHANGED));

    assertThat(event, isValidLoanDueDateChangedEvent(updatedLoan));
    assertThatPublishedLoanLogRecordEventsAreValid(updatedLoan);
  }

  @Test
  void dueDateChangeShouldClearRenewalFlagWhenSetAndNoOpenRecallsInQueue() {
    IndividualResource loanPolicy = loanPoliciesFixture.create(
      new LoanPolicyBuilder()
        .withName("loan policy")
        .withRecallsMinimumGuaranteedLoanPeriod(weeks(2))
        .rolling(months(1)));

    useFallbackPolicies(loanPolicy.getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandardDoNotCountClosed().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    ItemBuilder itemBuilder = ItemExamples.basedUponSmallAngryPlanet(
      materialTypesFixture.book().getId(), loanTypesFixture.canCirculate().getId(),
      EMPTY, "ItemPrefix", "ItemSuffix", "");

    ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      itemBuilder, itemsFixture.thirdFloorHoldings());

    IndividualResource steve = usersFixture.steve();
    ZonedDateTime loanDate = ZonedDateTime.of(2021, 11, 20, 13, 25, 46, 0, UTC);
    IndividualResource initialLoan = checkOutFixture.checkOutByBarcode(smallAngryPlanet, steve, loanDate);

    ZonedDateTime initialDueDate = ZonedDateTime.parse(initialLoan.getJson().getString("dueDate"));

    assertThat(initialLoan.getJson().containsKey("dueDateChangedByRecall"), equalTo(false));

    IndividualResource recall = requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .by(usersFixture.charlotte())
      .fulfillToHoldShelf(servicePointsFixture.cd1()));

    Response recalledLoan = loansClient.getById(initialLoan.getId());

    requestsFixture.cancelRequest(recall);

    final ZonedDateTime newDueDate = initialDueDate.plusMonths(1);
    changeDueDateFixture.changeDueDate(new ChangeDueDateRequestBuilder()
      .forLoan(recalledLoan.getJson().getString("id"))
      .withDueDate(newDueDate));

    JsonObject dueDateChangedLoan = loansClient.getById(initialLoan.getId()).getJson();

    assertThat(dueDateChangedLoan.getBoolean("dueDateChangedByRecall"), equalTo(false));

    assertThat("due date should be provided new due date",
      dueDateChangedLoan.getString("dueDate"), isEquivalentTo(newDueDate));
  }

  @Test
  void dueDateChangeShouldNotUnsetRenewalFlagValueWhenTlrFeatureEnabled() {
    IndividualResource loanPolicy = loanPoliciesFixture.create(
      new LoanPolicyBuilder()
        .withName("loan policy")
        .withRecallsMinimumGuaranteedLoanPeriod(org.folio.circulation.domain.policy.Period.weeks(2))
        .rolling(org.folio.circulation.domain.policy.Period.months(1)));

    useFallbackPolicies(loanPolicy.getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandardDoNotCountClosed().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    ItemBuilder itemBuilder = ItemExamples.basedUponSmallAngryPlanet(
      materialTypesFixture.book().getId(), loanTypesFixture.canCirculate().getId(),
      EMPTY, "ItemPrefix", "ItemSuffix", "");

    ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      itemBuilder, itemsFixture.thirdFloorHoldings());
    IndividualResource steve = usersFixture.steve();
    IndividualResource initialLoan = checkOutFixture.checkOutByBarcode(smallAngryPlanet, steve);
    ZonedDateTime initialDueDate = ZonedDateTime.parse(initialLoan.getJson().getString("dueDate"));

    assertThat(initialLoan.getJson().containsKey("dueDateChangedByRecall"), equalTo(false));

    IndividualResource recall = requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .by(usersFixture.charlotte())
      .fulfillToHoldShelf(servicePointsFixture.cd1()));
    Response recalledLoan = loansClient.getById(initialLoan.getId());

    assertThat(recalledLoan.getJson().getBoolean("dueDateChangedByRecall"), equalTo(true));

    settingsFixture.enableTlrFeature();
    requestsClient.create(new RequestBuilder()
      .recall()
      .titleRequestLevel()
      .withInstanceId(smallAngryPlanet.getInstanceId())
      .withNoItemId()
      .withNoHoldingsRecordId()
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .withRequesterId(usersFixture.jessica().getId()));

    requestsFixture.cancelRequest(recall);
    final ZonedDateTime newDueDate = initialDueDate.plusMonths(1);
    changeDueDateFixture.changeDueDate(new ChangeDueDateRequestBuilder()
      .forLoan(recalledLoan.getJson().getString("id"))
      .withDueDate(newDueDate));
    JsonObject dueDateChangedLoan = loansClient.getById(initialLoan.getId()).getJson();

    assertThat(recalledLoan.getJson().containsKey("dueDateChangedByRecall"), equalTo(true));
    assertThat(dueDateChangedLoan.getBoolean("dueDateChangedByRecall"), equalTo(true));
  }

  @ParameterizedTest
  @EnumSource(value = TlrFeatureStatus.class, names = {"DISABLED", "NOT_CONFIGURED"})
  void dueDateChangeShouldUnsetRenewalFlagValueWhenTlrFeatureDisabledOrNotConfigured(TlrFeatureStatus tlrFeatureStatus) {
    IndividualResource loanPolicy = loanPoliciesFixture.create(
      new LoanPolicyBuilder()
        .withName("loan policy")
        .withRecallsMinimumGuaranteedLoanPeriod(org.folio.circulation.domain.policy.Period.weeks(2))
        .rolling(org.folio.circulation.domain.policy.Period.months(1)));

    useFallbackPolicies(loanPolicy.getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandardDoNotCountClosed().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    ItemBuilder itemBuilder = ItemExamples.basedUponSmallAngryPlanet(
      materialTypesFixture.book().getId(), loanTypesFixture.canCirculate().getId(),
      EMPTY, "ItemPrefix", "ItemSuffix", "");

    ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      itemBuilder, itemsFixture.thirdFloorHoldings());
    IndividualResource steve = usersFixture.steve();
    IndividualResource initialLoan = checkOutFixture.checkOutByBarcode(smallAngryPlanet, steve);
    ZonedDateTime initialDueDate = ZonedDateTime.parse(initialLoan.getJson().getString("dueDate"));
    UUID instanceId = smallAngryPlanet.getInstanceId();

    assertThat(initialLoan.getJson().containsKey("dueDateChangedByRecall"), equalTo(false));

    IndividualResource itemLevelRecall = requestsFixture.place(new RequestBuilder()
      .recall()
      .itemRequestLevel()
      .withInstanceId(instanceId)
      .forItem(smallAngryPlanet)
      .by(usersFixture.charlotte())
      .fulfillToHoldShelf(servicePointsFixture.cd1()));
    Response recalledLoan = loansClient.getById(initialLoan.getId());

    assertThat(recalledLoan.getJson().getBoolean("dueDateChangedByRecall"), equalTo(true));

    settingsFixture.enableTlrFeature();

    requestsClient.create(new RequestBuilder()
      .recall()
      .titleRequestLevel()
      .withInstanceId(instanceId)
      .withNoItemId()
      .withNoHoldingsRecordId()
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .withRequesterId(usersFixture.jessica().getId()));

    requestsFixture.cancelRequest(itemLevelRecall);
    reconfigureTlrFeature(tlrFeatureStatus);

    final ZonedDateTime newDueDate = initialDueDate.plusMonths(1);
    changeDueDateFixture.changeDueDate(new ChangeDueDateRequestBuilder()
      .forLoan(recalledLoan.getJson().getString("id"))
      .withDueDate(newDueDate));
    JsonObject dueDateChangedLoan = loansClient.getById(initialLoan.getId()).getJson();

    assertThat(recalledLoan.getJson().containsKey("dueDateChangedByRecall"), equalTo(true));
    assertThat(dueDateChangedLoan.getBoolean("dueDateChangedByRecall"), equalTo(false));
  }

  private void chargeFeesForLostItemToKeepLoanOpen() {
    feeFineTypeFixture.lostItemProcessingFee();
    feeFineTypeFixture.lostItemFee();
    feeFineOwnerFixture.cd1Owner();

    final LostItemFeePolicyBuilder lostItemPolicy = lostItemFeePoliciesFixture
      .facultyStandardPolicy()
      .withName("Declared lost fee test policy")
      .chargeProcessingFeeWhenDeclaredLost(5.00)
      .withSetCost(10.00);

    useLostItemPolicy(lostItemFeePoliciesFixture.create(lostItemPolicy).getId());
  }
}
