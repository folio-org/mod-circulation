package api.loans.scenarios;

import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasUUIDParameter;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.core.Is.is;
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
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import api.support.APITests;
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

public class ChangeDueDateByReplacingLoanTests extends APITests {

  @Before
  public void setupLoanType() {
    noteTypeFixture.generalNoteType();
  }

  @After
  public void cleanUp() {
    notesClient.deleteAll();
    noteTypeClient.deleteAll();
  }

  @Test
  public void canManuallyChangeTheDueDateOfLoan() {
    final InventoryItemResource item = itemsFixture.basedUponNod();

    IndividualResource loan = checkOutFixture.checkOutByBarcode(item);

    Response fetchedLoan = loansClient.getById(loan.getId());

    JsonObject loanToChange = fetchedLoan.getJson().copy();

    DateTime dueDate = DateTime.parse(loanToChange.getString("dueDate"));
    DateTime newDueDate = dueDate.plus(Period.days(14));

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
  public void cannotManuallyChangeTheDueDateOfClaimedReturnedLoan() {
    final InventoryItemResource item = itemsFixture.basedUponNod();

    IndividualResource loan = checkOutFixture.checkOutByBarcode(item);

    final ClaimItemReturnedRequestBuilder claimedItemBuilder =
     (new ClaimItemReturnedRequestBuilder()).forLoan(loan.getId().toString());

    Response claimedLoan = claimItemReturnedFixture.claimItemReturned(claimedItemBuilder);
    assertThat(claimedLoan, hasStatus(HTTP_NO_CONTENT));

    Response fetchedLoan = loansClient.getById(loan.getId());

    JsonObject loanToChange = fetchedLoan.getJson().copy();

    final DateTime dueDate = DateTime.parse(loanToChange.getString("dueDate"));
    final DateTime newDueDate = dueDate.plus(Period.days(14));

    write(loanToChange, "action", "dueDateChange");
    write(loanToChange, "dueDate", newDueDate);

    Response updatedLoanResponse = loansFixture
      .attemptToReplaceLoan(loan.getId(), loanToChange);

    assertThat(updatedLoanResponse.getStatusCode(), is(422));
    assertThat(updatedLoanResponse.getJson(),
      hasErrorWith(hasMessage("item is claimed returned")));
  }

  @Test
  public void canManuallyReapplyTheDueDateOfClaimedReturnedLoan() {
    final InventoryItemResource item = itemsFixture.basedUponNod();

    IndividualResource loan = checkOutFixture.checkOutByBarcode(item);

    final ClaimItemReturnedRequestBuilder claimedItemBuilder =
     (new ClaimItemReturnedRequestBuilder()).forLoan(loan.getId().toString());

    Response claimedLoan = claimItemReturnedFixture.claimItemReturned(claimedItemBuilder);
    assertThat(claimedLoan, hasStatus(HTTP_NO_CONTENT));

    Response fetchedLoan = loansClient.getById(loan.getId());

    JsonObject loanToChange = fetchedLoan.getJson().copy();

    final DateTime dueDate = DateTime.parse(loanToChange.getString("dueDate"));

    write(loanToChange, "action", "dueDateChange");
    write(loanToChange, "dueDate", dueDate);

    loansFixture.replaceLoan(loan.getId(), loanToChange);
  }

  @Test
  public void canChangeDueDateOfLoanWithOpenRequest() {
    final InventoryItemResource item = itemsFixture.basedUponNod();

    IndividualResource loan = checkOutFixture.checkOutByBarcode(item);

    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(item)
      .by(usersFixture.steve())
      .fulfilToHoldShelf(servicePointsFixture.cd1()));

    Response fetchedLoan = loansClient.getById(loan.getId());

    JsonObject loanToChange = fetchedLoan.getJson().copy();

    DateTime dueDate = DateTime.parse(loanToChange.getString("dueDate"));
    DateTime newDueDate = dueDate.plus(Period.days(14));

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
  public void manualDueDateChangeNoticeIsSentWhenPolicyDefinesManualDueDateChangeNoticeConfiguration() {

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
      StringUtils.EMPTY,
      "ItemPrefix",
      "ItemSuffix",
      "");
    InventoryItemResource smallAngryPlanet =
      itemsFixture.basedUponSmallAngryPlanet(itemBuilder, itemsFixture.thirdFloorHoldings());

    IndividualResource steve = usersFixture.steve();


    IndividualResource loan = checkOutFixture.checkOutByBarcode(smallAngryPlanet, steve);
    JsonObject loanToChange = loan.getJson().copy();

    DateTime dueDate = DateTime.parse(loanToChange.getString("dueDate"));
    DateTime newDueDate = dueDate.plus(Period.weeks(2));

    write(loanToChange, "dueDate", newDueDate);

    loansClient.replace(loan.getId(), loanToChange);

    IndividualResource loanAfterUpdate = loansClient.get(loan);

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(patronNoticesClient::getAll, Matchers.hasSize(1));
    List<JsonObject> sentNotices = patronNoticesClient.getAll();

    Map<String, Matcher<String>> noticeContextMatchers = new HashMap<>();
    noticeContextMatchers.putAll(TemplateContextMatchers.getUserContextMatchers(steve));
    noticeContextMatchers.putAll(TemplateContextMatchers.getItemContextMatchers(smallAngryPlanet, true));
    noticeContextMatchers.putAll(TemplateContextMatchers.getLoanContextMatchers(loanAfterUpdate));
    noticeContextMatchers.putAll(TemplateContextMatchers.getLoanPolicyContextMatchers(renewalLimit, renewalLimit));
    MatcherAssert.assertThat(sentNotices,
      hasItems(
        hasEmailNoticeProperties(steve.getId(), manualDueDateChangeTemplateId, noticeContextMatchers)));
  }

  @Test
  public void dueDateCannotBeChangedWhenItemIsDeclaredLost() {
    useLostItemPolicy(lostItemFeePoliciesFixture.chargeFee().getId());

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource loan = checkOutFixture.checkOutByBarcode(smallAngryPlanet);
    final JsonObject loanJson = loan.getJson();

    declareLostFixtures.declareItemLost(loanJson);

    Response fetchedLoan = loansClient.getById(loan.getId());
    JsonObject loanToChange = fetchedLoan.getJson().copy();
    DateTime dueDate = DateTime.parse(loanToChange.getString("dueDate"));
    DateTime newDueDate = dueDate.plus(Period.days(14));
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
}
