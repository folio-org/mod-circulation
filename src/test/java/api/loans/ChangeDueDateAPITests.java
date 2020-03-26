package api.loans;

import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.folio.HttpStatus.HTTP_NOT_FOUND;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.circulation.domain.representations.LoanProperties.ITEM_ID;
import static org.folio.circulation.resources.ChangeDueDateResource.DUE_DATE;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.awaitility.Awaitility;
import org.folio.circulation.support.http.client.IndividualResource;
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
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.builders.RequestBuilder;
import api.support.fixtures.ItemExamples;
import api.support.fixtures.TemplateContextMatchers;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;

public class ChangeDueDateAPITests extends APITests {
  private InventoryItemResource item;
  private IndividualResource loan;
  private DateTime dueDate;

  @Before
  public void setUpItemAndLoan() {
    item = itemsFixture.basedUponNod();
    loan = loansFixture.checkOutByBarcode(item);
    dueDate = DateTime.parse(loan.getJson().getString("dueDate"));
  }

  @Test
  public void canChangeTheDueDate() {
    final DateTime newDueDate = dueDate.plus(Period.days(14));

    loansFixture.changeDueDate(new ChangeDueDateRequestBuilder()
      .forLoan(loan.getId())
      .withDueDate(newDueDate));

    Response response = loansClient.getById(loan.getId());

    JsonObject updatedLoan = response.getJson();

    assertThat("due date is not updated",
      updatedLoan.getString("dueDate"), isEquivalentTo(newDueDate));
  }

  @Test
  public void cannotChangeDueDateWhenDueDateIsNotProvided() {
    final Response response = loansFixture
      .attemptChangeDueDate(new ChangeDueDateRequestBuilder()
        .forLoan(loan.getId())
        .withDueDate(null));

    assertResponseOf(response, 422, DUE_DATE);
    assertResponseMessage(response, "Due date is a required field");
  }

  @Test
  public void cannotChangeDueDateWhenLoanIsNotFound() {
    final String nonExistentLoanId = UUID.randomUUID().toString();
    final DateTime newDueDate = dueDate.plus(Period.days(14));

    final Response response = loansFixture
      .attemptChangeDueDate(new ChangeDueDateRequestBuilder()
        .forLoan(nonExistentLoanId)
        .withDueDate(newDueDate));

    assertThat(response, hasStatus(HTTP_NOT_FOUND));
  }

  @Test
  public void cannotChangeDueDateWhenClosed() {
    final DateTime newDueDate = dueDate.plus(Period.days(14));

    loansFixture.checkInByBarcode(item);

    final Response response = loansFixture
      .attemptChangeDueDate(new ChangeDueDateRequestBuilder()
        .forLoan(loan.getId())
        .withDueDate(newDueDate));

    assertResponseOf(response, 422, "id", loan.getId());
    assertResponseMessage(response, "Loan is closed");
  }

  @Test
  public void cannotChangeDueDateWhenDeclaredLost() {
    final DateTime newDueDate = dueDate.plus(Period.days(14));

    assertThat(loansFixture.declareItemLost(loan.getJson()),
      hasStatus(HTTP_NO_CONTENT));

    final Response response = loansFixture
      .attemptChangeDueDate(new ChangeDueDateRequestBuilder()
        .forLoan(loan.getId())
        .withDueDate(newDueDate));

    assertResponseOf(response, 422, ITEM_ID, item.getId());
    assertResponseMessage(response, "item is Declared lost");
  }

  @Test
  public void cannotChangeDueDateWhenClaimedReturned() {
    final DateTime newDueDate = dueDate.plus(Period.days(14));

    assertThat(loansFixture.claimItemReturned(
      new ClaimItemReturnedRequestBuilder()
        .forLoan(loan.getId().toString())
       ), hasStatus(HTTP_NO_CONTENT));

    (new ChangeDueDateRequestBuilder()).forLoan(loan.getId().toString());

    final Response response = loansFixture
      .attemptChangeDueDate(new ChangeDueDateRequestBuilder()
        .forLoan(loan.getId())
        .withDueDate(newDueDate));

    assertResponseOf(response, 422, ITEM_ID, item.getId());
    assertResponseMessage(response, "item is Claimed returned");
  }

  @Test
  public void cannotReapplyDueDateWhenClaimedReturned() {
    assertThat(loansFixture.claimItemReturned(
      new ClaimItemReturnedRequestBuilder()
        .forLoan(loan.getId().toString())
      ), hasStatus(HTTP_NO_CONTENT));

    final Response response = loansFixture
      .attemptChangeDueDate(new ChangeDueDateRequestBuilder()
        .forLoan(loan.getId())
        .withDueDate(dueDate));

    assertResponseOf(response, 422, ITEM_ID, item.getId());
    assertResponseMessage(response, "item is Claimed returned");
  }

  @Test
  public void canChangeDueDateWithOpenRequest() {
    final DateTime newDueDate = dueDate.plus(Period.days(14));

    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(item)
      .by(usersFixture.steve())
      .fulfilToHoldShelf(servicePointsFixture.cd1()));

    loansFixture.changeDueDate(new ChangeDueDateRequestBuilder()
      .forLoan(loan.getId())
      .withDueDate(newDueDate));

    Response response = loansClient.getById(loan.getId());

    JsonObject updatedLoan = response.getJson();

    assertThat("due date is not updated",
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
      materialTypesFixture.book().getId(),
      loanTypesFixture.canCirculate().getId(),
      StringUtils.EMPTY,
      "ItemPrefix",
      "ItemSuffix",
      "");

    InventoryItemResource smallAngryPlanet =
      itemsFixture.basedUponSmallAngryPlanet(itemBuilder, itemsFixture.thirdFloorHoldings());

    IndividualResource steve = usersFixture.steve();

    IndividualResource loan = loansFixture.checkOutByBarcode(smallAngryPlanet, steve);

    DateTime newDueDate = dueDate.plus(Period.weeks(2));

    loansFixture.changeDueDate(new ChangeDueDateRequestBuilder()
      .forLoan(loan.getId())
      .withDueDate(newDueDate));

    IndividualResource loanAfterUpdate = loansClient.get(loan);

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(patronNoticesClient::getAll, Matchers.hasSize(1));

    List<JsonObject> sentNotices = patronNoticesClient.getAll();

    Map<String, Matcher<String>> matchers = new HashMap<>();
    matchers.putAll(TemplateContextMatchers.getUserContextMatchers(steve));
    matchers.putAll(TemplateContextMatchers.getItemContextMatchers(smallAngryPlanet, true));
    matchers.putAll(TemplateContextMatchers.getLoanContextMatchers(loanAfterUpdate));
    matchers.putAll(TemplateContextMatchers.getLoanPolicyContextMatchers(
      renewalLimit, renewalLimit));

    assertThat(sentNotices, hasItems(
      hasEmailNoticeProperties(steve.getId(), templateId, matchers)));
  }

  private void assertResponseOf(Response response, int code,
      String key) {

    assertResponseOf(response, code, key, (String) null);
  }

  private void assertResponseOf(Response response, int code,
      String key, UUID value) {

    assertResponseOf(response, code, key, value.toString());
  }

  private void assertResponseOf(Response response, int code,
      String key, String value) {

    assertThat(response.getStatusCode(), is(code));
    assertThat(response.getJson(), hasErrorWith(hasParameter(key, value)));
  }

  private void assertResponseMessage(Response response, String message) {
    assertThat(response.getJson(), hasErrorWith(hasMessage(message)));
  }
}
