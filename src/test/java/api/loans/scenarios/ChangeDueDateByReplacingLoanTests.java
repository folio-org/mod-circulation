package api.loans.scenarios;

import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasUUIDParameter;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfPublishedEvents;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfSentNotices;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE_ERROR;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.utils.DateFormatUtil.parseDateTime;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import api.support.builders.AddInfoRequestBuilder;
import org.folio.circulation.support.http.client.Response;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.ClaimItemReturnedRequestBuilder;
import api.support.builders.ItemBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.builders.RequestBuilder;
import api.support.fakes.FakeModNotify;
import api.support.fixtures.ItemExamples;
import api.support.fixtures.TemplateContextMatchers;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import io.vertx.core.json.JsonObject;

class ChangeDueDateByReplacingLoanTests extends APITests {

  public ChangeDueDateByReplacingLoanTests() {
    super(true, true);
  }

  @Test
  void canManuallyChangeTheDueDateOfLoan() {
    final ItemResource item = itemsFixture.basedUponNod();

    IndividualResource loan = checkOutFixture.checkOutByBarcode(item);

    Response fetchedLoan = loansClient.getById(loan.getId());

    ZonedDateTime dueDate = parseDateTime(fetchedLoan.getJson().getString("dueDate"));
    ZonedDateTime newDueDate = dueDate.plusDays(14);

    JsonObject loanToChange = loanForDueDateChangeRequest(fetchedLoan, newDueDate);

    loansFixture.replaceLoan(loan.getId(), loanToChange);

    Response updatedLoanResponse = loansClient.getById(loan.getId());

    JsonObject updatedLoan = updatedLoanResponse.getJson();

    assertThat("status is not open",
      updatedLoan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action is not change due date",
      updatedLoan.getString("action"), is("dueDateChange"));

    assertThat("should not contain a return date",
      updatedLoan.containsKey("returnDate"), is(false));

    assertThat("due date does not match",
      updatedLoan.getString("dueDate"), isEquivalentTo(newDueDate));

    assertThat("renewal count should not have changed",
      updatedLoan.containsKey("renewalCount"), is(false));

    JsonObject fetchedItem = itemsClient.getById(item.getId()).getJson();

    assertThat("item status is not checked out",
      fetchedItem.getJsonObject("status").getString("name"), is("Checked out"));

    final JsonObject loanInStorage = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("item status snapshot in storage is not checked out",
      loanInStorage.getString("itemStatus"), is("Checked out"));

    assertThat("Should not contain check in service point summary",
      loanInStorage.containsKey("checkinServicePoint"), is(false));

    assertThat("Should not contain check out service point summary",
      loanInStorage.containsKey("checkoutServicePoint"), is(false));
  }

  @Test
  void canManuallyReapplyTheDueDateOfClaimedReturnedLoan() {
    final ItemResource item = itemsFixture.basedUponNod();

    IndividualResource loan = checkOutFixture.checkOutByBarcode(item);

    final ClaimItemReturnedRequestBuilder claimedItemBuilder =
     (new ClaimItemReturnedRequestBuilder()).forLoan(loan.getId().toString());

    Response claimedLoan = claimItemReturnedFixture.claimItemReturned(claimedItemBuilder);
    assertThat(claimedLoan, hasStatus(HTTP_NO_CONTENT));

    Response fetchedLoan = loansClient.getById(loan.getId());

    JsonObject loanToChange = fetchedLoan.getJson().copy();

    final ZonedDateTime dueDate = parseDateTime(loanToChange.getString("dueDate"));

    write(loanToChange, "action", "dueDateChange");
    write(loanToChange, "dueDate", dueDate);

    loansFixture.replaceLoan(loan.getId(), loanToChange);
  }

  @Test
  void canChangeDueDateOfLoanWithOpenRequest() {
    final ItemResource item = itemsFixture.basedUponNod();

    IndividualResource loan = checkOutFixture.checkOutByBarcode(item);

    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(item)
      .by(usersFixture.steve())
      .fulfillToHoldShelf(servicePointsFixture.cd1()));

    Response fetchedLoan = loansClient.getById(loan.getId());

    JsonObject loanToChange = fetchedLoan.getJson().copy();

    ZonedDateTime dueDate = parseDateTime(loanToChange.getString("dueDate"));
    ZonedDateTime newDueDate = dueDate.plusDays(14);

    write(loanToChange, "action", "dueDateChange");
    write(loanToChange, "dueDate", newDueDate);

    loansFixture.replaceLoan(loan.getId(), loanToChange);

    Response updatedLoanResponse = loansClient.getById(loan.getId());

    JsonObject updatedLoan = updatedLoanResponse.getJson();

    assertThat("status is not open",
      updatedLoan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action is not change due date",
      updatedLoan.getString("action"), is("dueDateChange"));

    assertThat("should not contain a return date",
      updatedLoan.containsKey("returnDate"), is(false));

    assertThat("due date does not match",
      updatedLoan.getString("dueDate"), isEquivalentTo(newDueDate));

    assertThat("renewal count should not have changed",
      updatedLoan.containsKey("renewalCount"), is(false));

    JsonObject fetchedItem = itemsClient.getById(item.getId()).getJson();

    assertThat("item status is not checked out",
      fetchedItem.getJsonObject("status").getString("name"), is("Checked out"));

    final JsonObject loanInStorage = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("item status snapshot in storage is not checked out",
      loanInStorage.getString("itemStatus"), is("Checked out"));

    assertThat("Should not contain check in service point summary",
      loanInStorage.containsKey("checkinServicePoint"), is(false));

    assertThat("Should not contain check out service point summary",
      loanInStorage.containsKey("checkoutServicePoint"), is(false));
  }


  @Test
  void manualDueDateChangeNoticeIsSentWhenPolicyDefinesManualDueDateChangeNoticeConfiguration() {
    UUID manualDueDateChangeTemplateId = UUID.randomUUID();
    JsonObject manualDueDateChangeNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(manualDueDateChangeTemplateId)
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
          manualDueDateChangeNoticeConfiguration, checkInNoticeConfiguration)));

    int renewalLimit = 3;
    IndividualResource loanPolicyWithLimitedRenewals = loanPoliciesFixture.create(
      new LoanPolicyBuilder()
        .withName("Limited renewals loan policy")
        .rolling(org.folio.circulation.domain.policy.Period.months(1))
        .limitedRenewals(renewalLimit));

    useFallbackPolicies(
      loanPolicyWithLimitedRenewals.getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePolicy.getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    ItemBuilder itemBuilder = ItemExamples.basedUponSmallAngryPlanet(
      materialTypesFixture.book().getId(),
      loanTypesFixture.canCirculate().getId(),
      EMPTY,
      "ItemPrefix",
      "ItemSuffix",
      "");
    ItemResource smallAngryPlanet =
      itemsFixture.basedUponSmallAngryPlanet(itemBuilder, itemsFixture.thirdFloorHoldings());

    IndividualResource steve = usersFixture.steve();


    IndividualResource loan = checkOutFixture.checkOutByBarcode(smallAngryPlanet, steve);
    String infoAdded = "testing patron info";
    addInfoFixture.addInfo(new AddInfoRequestBuilder(loan.getId().toString(),
      "patronInfoAdded", infoAdded));
    JsonObject loanToChange = loan.getJson().copy();

    ZonedDateTime dueDate = parseDateTime(loanToChange.getString("dueDate"));
    ZonedDateTime newDueDate = dueDate.plusWeeks(2);

    write(loanToChange, "dueDate", newDueDate);

    loansClient.replace(loan.getId(), loanToChange);

    IndividualResource loanAfterUpdate = loansClient.get(loan);

    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    Map<String, Matcher<String>> noticeContextMatchers = new HashMap<>();

    noticeContextMatchers.putAll(TemplateContextMatchers.getUserContextMatchers(steve));
    noticeContextMatchers.putAll(TemplateContextMatchers.getItemContextMatchers(smallAngryPlanet, true));
    noticeContextMatchers.putAll(TemplateContextMatchers.getLoanContextMatchers(loanAfterUpdate));
    noticeContextMatchers.putAll(TemplateContextMatchers.getLoanPolicyContextMatchers(renewalLimit, renewalLimit));
    noticeContextMatchers.putAll(TemplateContextMatchers.getLoanAdditionalInfoContextMatchers(infoAdded));

    assertThat(FakeModNotify.getSentPatronNotices(),
      hasItems(hasEmailNoticeProperties(steve.getId(), manualDueDateChangeTemplateId, noticeContextMatchers)));
  }

  @Test
  void shouldRejectDueDateChangeWhenItemIsInDisallowedStatus() {
    useLostItemPolicy(lostItemFeePoliciesFixture.chargeFee().getId());

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource loan = checkOutFixture.checkOutByBarcode(smallAngryPlanet);
    final JsonObject loanJson = loan.getJson();

    declareLostFixtures.declareItemLost(loanJson);

    Response fetchedLoan = loansClient.getById(loan.getId());
    JsonObject loanToChange = fetchedLoan.getJson().copy();
    ZonedDateTime dueDate = parseDateTime(loanToChange.getString("dueDate"));
    ZonedDateTime newDueDate = dueDate.plusDays(14);
    write(loanToChange, "action", "dueDateChange");
    write(loanToChange, "dueDate", newDueDate);

    Response response = loansFixture.attemptToReplaceLoan(loan.getId(), loanToChange);

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("item is Declared lost"),
      hasUUIDParameter("itemId", smallAngryPlanet.getId()))));

    Response updatedLoanResponse = loansClient.getById(loan.getId());
    JsonObject updatedLoan = updatedLoanResponse.getJson();

    assertThat("status is open",
      updatedLoan.getJsonObject("status").getString("name"), is("Open"));
    assertThat("action is declaredLost",
      updatedLoan.getString("action"), is("declaredLost"));
    assertThat("should not contain a return date",
      updatedLoan.containsKey("returnDate"), is(false));
    assertThat("due date hasn't been changed",
      updatedLoan.getString("dueDate"), isEquivalentTo(dueDate));
    assertThat("renewal count should not have changed",
      updatedLoan.containsKey("renewalCount"), is(false));

    JsonObject fetchedItem = itemsClient.getById(smallAngryPlanet.getId()).getJson();

    assertThat("item status is declared lost",
      fetchedItem.getJsonObject("status").getString("name"), is("Declared lost"));

    final JsonObject loanInStorage = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("item status snapshot in storage is declared lost",
      loanInStorage.getString("itemStatus"), is("Declared lost"));
    assertThat("Should not contain check in service point summary",
      loanInStorage.containsKey("checkinServicePoint"), is(false));
    assertThat("Should not contain check out service point summary",
      loanInStorage.containsKey("checkoutServicePoint"), is(false));
  }

  @Test
  void dueDateChangeShouldResetDueDateChangedFlagWhenNoOpenRecallsInQueue() {
    final var loanPolicy = loanPoliciesFixture.create(
      new LoanPolicyBuilder()
        .withName("loan policy")
        .withRecallsMinimumGuaranteedLoanPeriod(org.folio.circulation.domain.policy.Period.weeks(2))
        .rolling(org.folio.circulation.domain.policy.Period.months(1)));

    useFallbackPolicies(loanPolicy.getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandardDoNotCountClosed().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    final var itemBuilder = ItemExamples.basedUponSmallAngryPlanet(
      materialTypesFixture.book().getId(), loanTypesFixture.canCirculate().getId(),
      EMPTY, "ItemPrefix", "ItemSuffix", "");

    final var smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      itemBuilder, itemsFixture.thirdFloorHoldings());

    final var steve = usersFixture.steve();

    final var initialLoan = checkOutFixture.checkOutByBarcode(smallAngryPlanet, steve);
    final var loanId = initialLoan.getId();
    final var initialDueDate = ZonedDateTime.parse(initialLoan.getJson().getString("dueDate"));

    final var recall = requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .by(usersFixture.charlotte())
      .fulfillToHoldShelf(servicePointsFixture.cd1()));

    Response recalledLoan = loansClient.getById(loanId);

    assertThat(recalledLoan.getJson().getBoolean("dueDateChangedByRecall"), equalTo(true));

    requestsFixture.cancelRequest(recall);

    final var newDueDate = initialDueDate.plusMonths(1);

    loansClient.replace(loanId, loanForDueDateChangeRequest(recalledLoan, newDueDate));

    final var dueDateChangedLoan = loansClient.getById(loanId).getJson();

    assertThat(dueDateChangedLoan.getBoolean("dueDateChangedByRecall"), equalTo(false));
    assertThat("due date should be provided new due date",
      dueDateChangedLoan.getString("dueDate"), isEquivalentTo(newDueDate));
  }

  @Test
  void dueDateChangedFlagShouldNotBeResetWhenDueDateHasNotChanged() {
    final var loanPolicy = loanPoliciesFixture.create(
      new LoanPolicyBuilder()
        .withName("loan policy")
        .withRecallsMinimumGuaranteedLoanPeriod(org.folio.circulation.domain.policy.Period.weeks(2))
        .rolling(org.folio.circulation.domain.policy.Period.months(1)));

    useFallbackPolicies(loanPolicy.getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandardDoNotCountClosed().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    final var itemBuilder = ItemExamples.basedUponSmallAngryPlanet(
      materialTypesFixture.book().getId(), loanTypesFixture.canCirculate().getId(),
      EMPTY, "ItemPrefix", "ItemSuffix", "");

    final var smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      itemBuilder, itemsFixture.thirdFloorHoldings());

    final var steve = usersFixture.steve();

    final var initialLoan = checkOutFixture.checkOutByBarcode(smallAngryPlanet, steve);
    final var loanId = initialLoan.getId();
    final var initialDueDate = ZonedDateTime.parse(initialLoan.getJson().getString("dueDate"));

    final var recall = requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .by(usersFixture.charlotte())
      .fulfillToHoldShelf(servicePointsFixture.cd1()));

    Response recalledLoan = loansClient.getById(loanId);

    assertThat(recalledLoan.getJson().getBoolean("dueDateChangedByRecall"), equalTo(true));

    requestsFixture.cancelRequest(recall);

    final var newDueDate = initialDueDate.plusMonths(1);

    // Intended to represent a replacement of a loan that does not change the due date
    // If checks are added later for identical loan representations,
    // this may provide false positive
    loansClient.replace(loanId, recalledLoan.getJson());

    final var dueDateChangedLoan = loansClient.getById(loanId).getJson();

    assertThat(dueDateChangedLoan.getBoolean("dueDateChangedByRecall"), equalTo(true));
  }

  private JsonObject loanForDueDateChangeRequest(Response fetchedLoan, ZonedDateTime newDueDate) {
    JsonObject loanToChange = fetchedLoan.getJson().copy();

    write(loanToChange, "action", "dueDateChange");
    write(loanToChange, "dueDate", newDueDate);

    return loanToChange;
  }
}
