package api.loans.scenarios;

import static api.support.PubsubPublisherTestUtils.assertThatPublishedNoticeLogRecordEventsCountIsEqualTo;
import static api.support.PubsubPublisherTestUtils.assertThatPublishedLogRecordEventsAreValid;
import static api.support.fixtures.TemplateContextMatchers.getItemContextMatchers;
import static api.support.fixtures.TemplateContextMatchers.getLoanContextMatchers;
import static api.support.fixtures.TemplateContextMatchers.getLoanPolicyContextMatchers;
import static api.support.fixtures.TemplateContextMatchers.getUserContextMatchers;
import static api.support.matchers.EventMatchers.isValidLoanDueDateChangedEvent;
import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasNullParameter;
import static api.support.matchers.ValidationErrorMatchers.hasUUIDParameter;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.HttpStatus.HTTP_NOT_FOUND;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.joda.time.Period.weeks;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import api.support.http.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.ChangeDueDateRequestBuilder;
import api.support.builders.ClaimItemReturnedRequestBuilder;
import api.support.builders.ItemBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.LostItemFeePolicyBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.builders.RequestBuilder;
import api.support.fakes.FakePubSub;
import api.support.fixtures.ItemExamples;
import api.support.http.ItemResource;
import io.vertx.core.json.JsonObject;

public class ChangeDueDateAPITests extends APITests {
  private ItemResource item;
  private IndividualResource loan;
  private DateTime dueDate;

  @Before
  public void setUpItemAndLoan() {
    chargeFeesForLostItemToKeepLoanOpen();

    item = itemsFixture.basedUponNod();
    loan = checkOutFixture.checkOutByBarcode(item);
    dueDate = DateTime.parse(loan.getJson().getString("dueDate"));
  }

  @Test
  public void canChangeTheDueDate() {
    final DateTime newDueDate = dueDate.plus(Period.days(14));

    changeDueDateFixture.changeDueDate(new ChangeDueDateRequestBuilder()
      .forLoan(loan.getId())
      .withDueDate(newDueDate));

    Response response = loansClient.getById(loan.getId());

    JsonObject updatedLoan = response.getJson();

    assertThat("due date is not updated",
      updatedLoan.getString("dueDate"), isEquivalentTo(newDueDate));
  }

  @Test
  public void cannotChangeDueDateWhenDueDateIsNotProvided() {
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
  public void cannotChangeDueDateWhenLoanIsNotFound() {
    final String nonExistentLoanId = UUID.randomUUID().toString();
    final DateTime newDueDate = dueDate.plus(Period.days(14));

    final Response response = changeDueDateFixture
      .attemptChangeDueDate(new ChangeDueDateRequestBuilder()
        .forLoan(nonExistentLoanId)
        .withDueDate(newDueDate));

    assertThat(response, hasStatus(HTTP_NOT_FOUND));
  }

  @Test
  public void cannotChangeDueDateWhenLoanIsClosed() {
    final DateTime newDueDate = dueDate.plus(Period.days(14));

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
  public void shouldRejectDueDateChangeWhenItemIsInDisallowedStatus() {
    final DateTime newDueDate = dueDate.plus(Period.days(14));

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
  public void canChangeDueDateWithOpenRequest() {
    final DateTime newDueDate = dueDate.plus(Period.days(14));

    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(item)
      .by(usersFixture.steve())
      .fulfilToHoldShelf(servicePointsFixture.cd1()));

    changeDueDateFixture.changeDueDate(new ChangeDueDateRequestBuilder()
      .forLoan(loan.getId())
      .withDueDate(newDueDate));

    Response response = loansClient.getById(loan.getId());

    JsonObject updatedLoan = response.getJson();

    assertThat("due date should have been updated",
      updatedLoan.getString("dueDate"), isEquivalentTo(newDueDate));
  }

  @Test
  public void changeDueDateNoticeIsSentWhenPolicyIsDefined() {
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
        .rolling(org.folio.circulation.domain.policy.Period.months(1))
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

    DateTime newDueDate = dueDate.plus(weeks(2));

    changeDueDateFixture.changeDueDate(new ChangeDueDateRequestBuilder()
      .forLoan(loan.getId())
      .withDueDate(newDueDate));

    IndividualResource loanAfterUpdate = loansClient.get(loan);

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(patronNoticesClient::getAll, Matchers.hasSize(1));

    List<JsonObject> sentNotices = patronNoticesClient.getAll();

    Map<String, Matcher<String>> matchers = new HashMap<>();

    matchers.putAll(getUserContextMatchers(steve));
    matchers.putAll(getItemContextMatchers(smallAngryPlanet, true));
    matchers.putAll(getLoanContextMatchers(loanAfterUpdate));
    matchers.putAll(getLoanPolicyContextMatchers(renewalLimit, renewalLimit));

    assertThat(sentNotices, hasItems(
      hasEmailNoticeProperties(steve.getId(), templateId, matchers)));
    assertThatPublishedNoticeLogRecordEventsCountIsEqualTo(patronNoticesClient.getAll().size());
    assertThatPublishedLogRecordEventsAreValid();
  }

  @Test
  public void dueDateChangedEventIsPublished() {
    final DateTime newDueDate = dueDate.plus(Period.days(14));
    changeDueDateFixture.changeDueDate(new ChangeDueDateRequestBuilder()
      .forLoan(loan.getId())
      .withDueDate(newDueDate));

    Response response = loansClient.getById(loan.getId());
    JsonObject updatedLoan = response.getJson();

    // There should be three events published - first one for "check out",
    // second one for "log event" and third one for "change due date"
    List<JsonObject> publishedEvents = Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(FakePubSub::getPublishedEvents, hasSize(3));

    JsonObject event = publishedEvents.get(2);

    assertThat(event, isValidLoanDueDateChangedEvent(updatedLoan));
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
