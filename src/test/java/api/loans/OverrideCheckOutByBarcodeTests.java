package api.loans;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.builders.ItemBuilder.CHECKED_OUT;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.UUIDMatcher.is;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.folio.circulation.domain.policy.Period.months;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.domain.representations.ItemLimitBlock;
import org.folio.circulation.domain.representations.ItemNotLoanableBlock;
import org.folio.circulation.domain.representations.OverrideBlocks;
import org.folio.circulation.domain.representations.PatronBlock;
import org.folio.circulation.support.http.client.Response;
import org.hamcrest.Matchers;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.OverrideCheckOutByBarcodeRequestBuilder;
import api.support.builders.RequestBuilder;
import api.support.http.IndividualResource;
import api.support.http.OkapiHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class OverrideCheckOutByBarcodeTests extends APITests {

  private static final DateTime TEST_LOAN_DATE =
    new DateTime(2019, 4, 10, 11, 35, 48, DateTimeZone.UTC);
  private static final DateTime TEST_DUE_DATE =
    new DateTime(2019, 4, 20, 11, 30, 0, DateTimeZone.UTC);
  private static final String TEST_COMMENT = "Some comment";
  private static final String CHECKED_OUT_THROUGH_OVERRIDE = "checkedOutThroughOverride";
  public static final String OVERRIDE_ITEM_NOT_LOANABLE_BLOCK_PERMISSION =
    "circulation.override-item-not-loanable-block";
  public static final String OVERRIDE_PATRON_BLOCK_PERMISSION =
    "circulation.override-patron-block";
  public static final String OVERRIDE_ITEM_LIMIT_BLOCK_PERMISSION =
    "circulation.override-item-limit-block";
  public static final String INSUFFICIENT_OVERRIDE_PERMISSIONS = "Insufficient override permissions";


  @Test
  public void canOverrideCheckoutWhenItemIsNotLoanable() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      item -> item
        .withEnumeration("v.70:no.1-6")
        .withChronology("1987:Jan.-June")
        .withVolume("testVolume"));

    IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.randomUUID();

    final OkapiHeaders okapiHeaders = buildOkapiHeadersWithPermissions(
      OVERRIDE_ITEM_NOT_LOANABLE_BLOCK_PERMISSION);

    setNotLoanablePolicy();
    IndividualResource response = checkOutFixture.overrideCheckOutByBarcode(
      new OverrideCheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(checkoutServicePointId)
        .on(TEST_LOAN_DATE)
        .withDueDate(TEST_DUE_DATE)
        .withComment(TEST_COMMENT)
        .withOverrideBlocks(new OverrideBlocks(
          new ItemNotLoanableBlock(TEST_DUE_DATE), null, null, TEST_COMMENT)),
      okapiHeaders);

    final JsonObject loan = response.getJson();

    assertThat("action should be checkedOutThroughOverride",
      loan.getString("action"), is(CHECKED_OUT_THROUGH_OVERRIDE));

    assertThat("due date should equal to due date from request",
      loan.getString("dueDate"), isEquivalentTo(TEST_DUE_DATE));

    assertThat("due date should equal to due date from request",
      loan.getString("actionComment"), is(TEST_COMMENT));

    assertThat(loan.getString("id"), is(notNullValue()));

    assertThat("user ID should match barcode",
      loan.getString("userId"), is(steve.getId()));

    assertThat("item ID should match barcode",
      loan.getString("itemId"), is(smallAngryPlanet.getId()));

    assertThat("status should be open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action should be checkedout",
      loan.getString("action"), is(CHECKED_OUT_THROUGH_OVERRIDE));

    assertThat("loan date should be as supplied",
      loan.getString("loanDate"), isEquivalentTo(TEST_LOAN_DATE));

    assertThat("due date should be 3 weeks after loan date, based upon loan policy",
      loan.getString("dueDate"), isEquivalentTo(TEST_DUE_DATE));

    assertThat("Checkout service point should be stored",
      loan.getString("checkoutServicePointId"), is(checkoutServicePointId));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));

    assertThat("has item information",
      loan.containsKey("item"), is(true));

    assertThat("title is taken from instance",
      loan.getJsonObject("item").getString("title"),
      is("The Long Way to a Small, Angry Planet"));

    assertThat("barcode is taken from item",
      loan.getJsonObject("item").getString("barcode"),
      is("036000291452"));

    assertThat("call number is 123456", loan.getJsonObject("item")
      .getString("callNumber"), is("123456"));

    assertThat(loan.getJsonObject("item").encode() + " contains 'materialType'",
      loan.getJsonObject("item").containsKey("materialType"), is(true));

    assertThat("materialType is book", loan.getJsonObject("item")
      .getJsonObject("materialType").getString("name"), is("Book"));

    assertThat("item has contributors",
      loan.getJsonObject("item").containsKey("contributors"), is(true));

    assertThat("has item enumeration",
      loan.getJsonObject("item").getString("enumeration"), is("v.70:no.1-6"));

    assertThat("has item chronology",
      loan.getJsonObject("item").getString("chronology"), is("1987:Jan.-June"));

    assertThat("has item volume",
      loan.getJsonObject("item").getString("volume"), is("testVolume"));

    JsonArray contributors = loan.getJsonObject("item").getJsonArray("contributors");

    assertThat("item has a single contributor",
      contributors.size(), is(1));

    assertThat("Becky Chambers is a contributor",
      contributors.getJsonObject(0).getString("name"), is("Chambers, Becky"));

    assertThat("has item status",
      loan.getJsonObject("item").containsKey("status"), is(true));

    assertThat("status is taken from item",
      loan.getJsonObject("item").getJsonObject("status").getString("name"),
      is("Checked out"));

    assertThat("Should not have snapshot of item status, as current status is included",
      loan.containsKey("itemStatus"), is(false));

    assertThat("item has location",
      loan.getJsonObject("item").containsKey("location"), is(true));

    assertThat("location is taken from holding",
      loan.getJsonObject("item").getJsonObject("location").getString("name"),
      is("3rd Floor"));
  }

  @Test
  public void cannotOverrideCheckoutWhenItemIsLoanable() {
    LoanPolicyBuilder loanablePolicy = new LoanPolicyBuilder()
      .withName("Loanable Policy")
      .rolling(Period.days(2));
    useFallbackPolicies(
      loanPoliciesFixture.create(loanablePolicy).getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.randomUUID();
    final OkapiHeaders okapiHeaders = buildOkapiHeadersWithPermissions(
      OVERRIDE_ITEM_NOT_LOANABLE_BLOCK_PERMISSION);

    Response response = checkOutFixture.attemptOverrideCheckOutByBarcode(
      new OverrideCheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(checkoutServicePointId)
        .on(TEST_LOAN_DATE)
        .withDueDate(TEST_DUE_DATE)
        .withComment(TEST_COMMENT)
        .withOverrideBlocks(new OverrideBlocks(
          new ItemNotLoanableBlock(TEST_DUE_DATE), null, null, TEST_COMMENT)),
      okapiHeaders);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Override is not allowed when item is loanable"))));
  }

  @Test
  public void cannotOverrideCheckoutWhenDueDateIsNotPresent() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.randomUUID();

    final OkapiHeaders okapiHeaders = buildOkapiHeadersWithPermissions(
      OVERRIDE_ITEM_NOT_LOANABLE_BLOCK_PERMISSION);

    setNotLoanablePolicy();
    Response response = checkOutFixture.attemptOverrideCheckOutByBarcode(
      new OverrideCheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(checkoutServicePointId)
        .on(TEST_LOAN_DATE)
        .withComment(TEST_COMMENT)
        .withOverrideBlocks(new OverrideBlocks(
          new ItemNotLoanableBlock(TEST_DUE_DATE), null, null, TEST_COMMENT)),
      okapiHeaders);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Override should be performed with due date specified"),
      hasParameter("dueDate", null))));
  }

  @Test
  public void cannotOverrideCheckoutWhenDueDateIsBeforeLoanDate() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.randomUUID();

    final OkapiHeaders okapiHeaders = buildOkapiHeadersWithPermissions(
      OVERRIDE_ITEM_NOT_LOANABLE_BLOCK_PERMISSION);

    setNotLoanablePolicy();
    DateTime invalidDueDate = TEST_LOAN_DATE.minusDays(2);
    Response response = checkOutFixture.attemptOverrideCheckOutByBarcode(
      new OverrideCheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(checkoutServicePointId)
        .on(TEST_LOAN_DATE)
        .withDueDate(invalidDueDate)
        .withComment(TEST_COMMENT)
        .withOverrideBlocks(new OverrideBlocks(
          new ItemNotLoanableBlock(TEST_DUE_DATE), null, null, TEST_COMMENT)),
      okapiHeaders);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Due date should be later than loan date"),
      hasParameter("dueDate", invalidDueDate.toString()))));
  }

  @Test
  public void cannotOverrideCheckoutWhenDueDateIsTheSameAsLoanDate() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.randomUUID();

    final OkapiHeaders okapiHeaders = buildOkapiHeadersWithPermissions(
      OVERRIDE_ITEM_NOT_LOANABLE_BLOCK_PERMISSION);

    setNotLoanablePolicy();
    Response response = checkOutFixture.attemptOverrideCheckOutByBarcode(
      new OverrideCheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(checkoutServicePointId)
        .on(TEST_LOAN_DATE)
        .withDueDate(TEST_LOAN_DATE)
        .withComment(TEST_COMMENT)
        .withOverrideBlocks(new OverrideBlocks(
          new ItemNotLoanableBlock(TEST_DUE_DATE), null, null, TEST_COMMENT)),
      okapiHeaders);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Due date should be later than loan date"),
      hasParameter("dueDate", TEST_LOAN_DATE.toString()))));
  }

  @Test
  public void cannotOverrideCheckoutWhenCommentIsNotPresent() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.randomUUID();

    final OkapiHeaders okapiHeaders = buildOkapiHeadersWithPermissions(
      OVERRIDE_ITEM_NOT_LOANABLE_BLOCK_PERMISSION);

    setNotLoanablePolicy();
    Response response = checkOutFixture.attemptOverrideCheckOutByBarcode(
      new OverrideCheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(checkoutServicePointId)
        .on(TEST_LOAN_DATE)
        .withDueDate(TEST_DUE_DATE)
        .withOverrideBlocks(new OverrideBlocks(
          new ItemNotLoanableBlock(TEST_DUE_DATE), null, null, TEST_COMMENT)),
      okapiHeaders);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Override should be performed with the comment specified"),
      hasParameter("comment", null))));
  }

  @Test
  public void canCreateRecallRequestAfterOverriddenCheckout() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    IndividualResource steve = usersFixture.steve();
    IndividualResource charlotte = usersFixture.charlotte();

    final OkapiHeaders okapiHeaders = buildOkapiHeadersWithPermissions(
      OVERRIDE_ITEM_NOT_LOANABLE_BLOCK_PERMISSION);

    setNotLoanablePolicy();
    checkOutFixture.overrideCheckOutByBarcode(
      new OverrideCheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(servicePointsFixture.cd1())
        .on(TEST_LOAN_DATE)
        .withDueDate(TEST_DUE_DATE)
        .withComment(TEST_COMMENT)
        .withOverrideBlocks(new OverrideBlocks(
        new ItemNotLoanableBlock(TEST_DUE_DATE), null, null, TEST_COMMENT)),
      okapiHeaders);

    final Response placeRequestResponse = requestsFixture.attemptPlace(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .by(charlotte)
      .fulfilToHoldShelf(servicePointsFixture.cd1()));

    assertThat(placeRequestResponse.getStatusCode(), is(201));
  }

  @Test
  public void cannotOverrideItemNotLoanableBlockWhenOverrideBlocksIsNotPresent() {
    final OkapiHeaders okapiHeaders = buildOkapiHeadersWithPermissions(
      OVERRIDE_ITEM_NOT_LOANABLE_BLOCK_PERMISSION);

    setNotLoanablePolicy();
    Response response = checkOutFixture.attemptCheckOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(itemsFixture.basedUponSmallAngryPlanet())
        .to(usersFixture.steve())
        .at(UUID.randomUUID())
        .on(TEST_LOAN_DATE), okapiHeaders);

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson(), hasErrorWith(allOf(hasMessage("Item is not loanable"),
      hasParameter("loanPolicyName", "Not Loanable Policy"))));
  }

  @Test
  public void cannotOverrideItemNotLoanableBlockWhenItemNotLoanableBlockIsNotPresent() {
    final OkapiHeaders okapiHeaders = buildOkapiHeadersWithPermissions(
      OVERRIDE_ITEM_NOT_LOANABLE_BLOCK_PERMISSION);

    setNotLoanablePolicy();
    Response response = checkOutFixture.attemptCheckOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(itemsFixture.basedUponSmallAngryPlanet())
        .to(usersFixture.steve())
        .at(UUID.randomUUID())
        .on(TEST_LOAN_DATE)
        .withOverrideBlocks(new OverrideBlocks(null, null, null, TEST_COMMENT)),
      okapiHeaders);

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson(), hasErrorWith(allOf(hasMessage("Item is not loanable"),
      hasParameter("loanPolicyName", "Not Loanable Policy"))));
  }

  @Test
  public void cannotOverrideItemNotLoanableBlockWhenUserDoesNotHavePermissions() {
    setNotLoanablePolicy();
    Response response = checkOutFixture.attemptCheckOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(itemsFixture.basedUponSmallAngryPlanet())
        .to(usersFixture.steve())
        .at(UUID.randomUUID())
        .on(TEST_LOAN_DATE).withOverrideBlocks(new OverrideBlocks(
          new ItemNotLoanableBlock(TEST_DUE_DATE), null, null, TEST_COMMENT)));

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage(INSUFFICIENT_OVERRIDE_PERMISSIONS))));
    assertThat(getMissingPermissions(response), hasSize(1));
    assertThat(getMissingPermissions(response), hasItem(OVERRIDE_ITEM_NOT_LOANABLE_BLOCK_PERMISSION));
  }

  @Test
  public void cannotOverrideItemNotLoanableBlockWhenUserDoesNotHaveRequiredPermissions() {
    final OkapiHeaders okapiHeaders = buildOkapiHeadersWithPermissions(
      OVERRIDE_PATRON_BLOCK_PERMISSION);

    setNotLoanablePolicy();
    Response response = checkOutFixture.attemptCheckOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(itemsFixture.basedUponSmallAngryPlanet())
        .to(usersFixture.steve())
        .at(UUID.randomUUID())
        .on(TEST_LOAN_DATE).withOverrideBlocks(new OverrideBlocks(
        new ItemNotLoanableBlock(TEST_DUE_DATE), null, null, TEST_COMMENT)),
      okapiHeaders);

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage(INSUFFICIENT_OVERRIDE_PERMISSIONS))));
    assertThat(getMissingPermissions(response), hasSize(1));
    assertThat(getMissingPermissions(response), hasItem(OVERRIDE_ITEM_NOT_LOANABLE_BLOCK_PERMISSION));
  }

  @Test
  public void cannotOverrideItemNotLoanableBlockAndPatronBlockWhenUserDoesNotHavePermissions() {
    setNotLoanablePolicy();
    Response response = checkOutFixture.attemptCheckOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(itemsFixture.basedUponSmallAngryPlanet())
        .to(usersFixture.steve())
        .at(UUID.randomUUID())
        .on(TEST_LOAN_DATE).withOverrideBlocks(new OverrideBlocks(
        new ItemNotLoanableBlock(TEST_DUE_DATE), new PatronBlock(), null, TEST_COMMENT)));

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage(INSUFFICIENT_OVERRIDE_PERMISSIONS))));
    assertThat(getMissingPermissions(response), hasSize(2));
    assertThat(getMissingPermissions(response).get(0), is(OVERRIDE_PATRON_BLOCK_PERMISSION));
    assertThat(getMissingPermissions(response).get(1), is(OVERRIDE_ITEM_NOT_LOANABLE_BLOCK_PERMISSION));
  }

  @Test
  public void canOverrideCheckoutWhenItemLimitWasReachedForBookMaterialType() {
    circulationRulesFixture.updateCirculationRules(createRulesWithBookItemLimit());
    IndividualResource firstBookTypeItem = itemsFixture.basedUponNod();
    IndividualResource secondBookTypeItem = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource steve = usersFixture.steve();

    checkOutFixture.checkOutByBarcode(firstBookTypeItem, steve);
    firstBookTypeItem = itemsClient.get(firstBookTypeItem);
    assertThat(firstBookTypeItem, hasItemStatus(CHECKED_OUT));

    Response response = checkOutFixture.attemptCheckOutByBarcode(secondBookTypeItem, steve);
    assertThat(response.getJson(), hasErrorWith(
      hasMessage("Patron has reached maximum limit of 1 items for material type")));

    final OkapiHeaders okapiHeaders = buildOkapiHeadersWithPermissions(
      OVERRIDE_ITEM_LIMIT_BLOCK_PERMISSION);
    JsonObject loan = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(secondBookTypeItem)
        .to(steve)
        .at(UUID.randomUUID())
        .withOverrideBlocks(new OverrideBlocks(
          null, null, new ItemLimitBlock(), TEST_COMMENT)),
      okapiHeaders).getJson();

    secondBookTypeItem = itemsClient.get(secondBookTypeItem);
    assertThat(secondBookTypeItem, hasItemStatus(CHECKED_OUT));
    assertThat(loan.getString("actionComment"), is(TEST_COMMENT));
    assertThat(loan.getString("action"), is("checkedOutThroughOverride"));
  }

  @Test
  public void cannotOverrideItemLimitBlockWhenUserDoesNotHavePermissions() {
    Response response = checkOutFixture.attemptCheckOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(itemsFixture.basedUponNod())
        .to(usersFixture.steve())
        .at(UUID.randomUUID())
        .withOverrideBlocks(new OverrideBlocks(
          null, null, new ItemLimitBlock(), TEST_COMMENT)));

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage(INSUFFICIENT_OVERRIDE_PERMISSIONS))));
    assertThat(getMissingPermissions(response), hasSize(1));
    assertThat(getMissingPermissions(response), hasItem(OVERRIDE_ITEM_LIMIT_BLOCK_PERMISSION));
  }

  @Test
  public void cannotOverridePatronBlockWhenUserDoesNotHavePermissions() {
    Response response = checkOutFixture.attemptCheckOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(itemsFixture.basedUponNod())
        .to(usersFixture.steve())
        .at(UUID.randomUUID())
        .withOverrideBlocks(new OverrideBlocks(
          null, new PatronBlock(), null, TEST_COMMENT)));

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage(INSUFFICIENT_OVERRIDE_PERMISSIONS))));
    assertThat(getMissingPermissions(response), hasSize(1));
    assertThat(getMissingPermissions(response), hasItem(OVERRIDE_PATRON_BLOCK_PERMISSION));
  }

  @Test
  public void cannotOverridePatronBlockWhenUserDoesNotHaveRequiredPermissions() {
    final OkapiHeaders okapiHeaders = buildOkapiHeadersWithPermissions(
      OVERRIDE_ITEM_LIMIT_BLOCK_PERMISSION);

    Response response = checkOutFixture.attemptCheckOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(itemsFixture.basedUponNod())
        .to(usersFixture.steve())
        .at(UUID.randomUUID())
        .withOverrideBlocks(new OverrideBlocks(
          null, new PatronBlock(), null, TEST_COMMENT)),
      okapiHeaders);

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage(INSUFFICIENT_OVERRIDE_PERMISSIONS))));
    assertThat(getMissingPermissions(response), hasSize(1));
    assertThat(getMissingPermissions(response), hasItem(OVERRIDE_PATRON_BLOCK_PERMISSION));
  }

  private void setNotLoanablePolicy() {
    LoanPolicyBuilder notLoanablePolicy = new LoanPolicyBuilder()
      .withName("Not Loanable Policy")
      .withLoanable(false)
      .notRenewable();

    useFallbackPolicies(
      loanPoliciesFixture.create(notLoanablePolicy).getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.inactiveNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());
  }

  private OkapiHeaders buildOkapiHeadersWithPermissions(String permissions) {
    return getOkapiHeadersFromContext()
      .withRequestId("override-check-out-by-barcode-request")
      .withOkapiPermissions("[" + permissions + "]");
  }

  private String createRulesWithBookItemLimit() {
    final String loanPolicyWithItemLimitId = prepareLoanPolicyWithItemLimit(1).getId().toString();
    final String loanPolicyWithoutItemLimitId = prepareLoanPolicyWithoutItemLimit().getId().toString();
    final String anyRequestPolicy = requestPoliciesFixture.allowAllRequestPolicy().getId().toString();
    final String anyNoticePolicy = noticePoliciesFixture.activeNotice().getId().toString();
    final String anyOverdueFinePolicy = overdueFinePoliciesFixture.facultyStandard().getId().toString();
    final String anyLostItemFeePolicy = lostItemFeePoliciesFixture.facultyStandard().getId().toString();

    return String.join("\n",
      "priority: t, s, c, b, a, m, g",
      "fallback-policy: l " + loanPolicyWithoutItemLimitId + " r " + anyRequestPolicy + " n " + anyNoticePolicy + " o " + anyOverdueFinePolicy + " i " + anyLostItemFeePolicy,
      "m " + materialTypesFixture.book().getId() + " : l " + loanPolicyWithItemLimitId + " r " + anyRequestPolicy + " n " + anyNoticePolicy  + " o " + anyOverdueFinePolicy + " i " + anyLostItemFeePolicy);
  }

  private IndividualResource prepareLoanPolicyWithItemLimit(int itemLimit) {
    return loanPoliciesFixture.create(
      new LoanPolicyBuilder()
        .withName("Loan Policy with item limit")
        .withItemLimit(itemLimit)
        .rolling(months(2))
        .renewFromCurrentDueDate());
  }

  private IndividualResource prepareLoanPolicyWithoutItemLimit() {
    return loanPoliciesFixture.create(
      new LoanPolicyBuilder()
        .withName("Loan Policy without item limit")
        .rolling(months(2))
        .renewFromCurrentDueDate());
  }

  private List<String> getMissingPermissions(Response response) {
    return response.getJson().getJsonArray("errors")
      .stream()
      .map(JsonObject.class::cast)
      .map(error -> error.getJsonObject("overridableBlock"))
      .map(block -> block.getJsonArray("missingPermissions"))
      .map(missingPermissions -> missingPermissions.getString(0))
      .collect(Collectors.toList());
  }
}
