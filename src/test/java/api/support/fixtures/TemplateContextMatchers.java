package api.support.fixtures;

import static api.support.matchers.JsonObjectMatcher.toStringMatcher;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static java.time.ZoneOffset.UTC;
import static java.util.stream.Collectors.joining;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getObjectProperty;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.hasSize;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.FeeAmount;
import org.folio.circulation.domain.FeeFineAction;
import org.folio.circulation.domain.Instance;
import org.folio.circulation.storage.mappers.InstanceMapper;
import org.hamcrest.Matcher;

import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.UserResource;
import io.vertx.core.json.JsonObject;
import org.hamcrest.core.Is;

public class TemplateContextMatchers {

  private static final String ITEM_REPRESENTATION_PREFIX = "itemLevel%s";

  private TemplateContextMatchers() {
  }

  public static Map<String, Matcher<String>> getRequesterContextMatchers(IndividualResource userResource) {
    return getUserContextMatchers(userResource, "requester");
  }

  public static Map<String, Matcher<String>> getUserContextMatchers(IndividualResource userResource, String prefix) {
    return getUserContextMatchers(userResource.getJson(), prefix);
  }

  public static Map<String, Matcher<String>> getUserContextMatchers(IndividualResource userResource) {
    return getUserContextMatchers(userResource.getJson());
  }

  public static Map<String, Matcher<String>> getUserContextMatchers(JsonObject userJson) {
    return getUserContextMatchers(userJson, "user");
  }

  public static Map<String, Matcher<String>> getUserContextMatchers(JsonObject userJson, String prefix) {
    JsonObject personal = getObjectProperty(userJson, "personal");

    Map<String, Matcher<String>> tokenMatchers = new HashMap<>();
    tokenMatchers.put(prefix + ".firstName", is(personal.getString("firstName")));
    tokenMatchers.put(prefix + ".lastName", is(personal.getString("lastName")));
    tokenMatchers.put(prefix + ".preferredFirstName", isPreferredName(personal));
    tokenMatchers.put(prefix + ".barcode", is(userJson.getString("barcode")));
    return tokenMatchers;
  }

  public static Matcher<String> isPreferredName(JsonObject personal) {
    return personal.getString("preferredFirstName") == null ?
      is(personal.getString("firstName")) : is(personal.getString("preferredFirstName"));
  }

  public static Map<String, Matcher<String>> getItemContextMatchers(ItemResource itemResource,
    boolean applyHoldingRecord) {

    JsonObject item = itemResource.getJson();
    String callNumber = findRepresentationCallNumbers(itemResource, applyHoldingRecord, "callNumber");
    String callNumberPrefix = findRepresentationCallNumbers(itemResource, applyHoldingRecord, "callNumberPrefix");
    String callNumberSuffix = findRepresentationCallNumbers(itemResource, applyHoldingRecord, "callNumberSuffix");
    String copyNumber = findRepresentationCopyNumber(itemResource, applyHoldingRecord);

    Map<String, Matcher<String>> tokenMatchers = new HashMap<>();
    tokenMatchers.put("item.title", notNullValue(String.class));
    tokenMatchers.put("item.allContributors", notNullValue(String.class));
    tokenMatchers.put("item.barcode", is(item.getString("barcode")));
    tokenMatchers.put("item.callNumber", is(callNumber));
    tokenMatchers.put("item.callNumberPrefix", is(callNumberPrefix));
    tokenMatchers.put("item.callNumberSuffix", is(callNumberSuffix));
    tokenMatchers.put("item.copy", is(copyNumber));
    tokenMatchers.put("item.materialType", notNullValue(String.class));
    tokenMatchers.put("item.effectiveLocationSpecific", notNullValue(String.class));
    tokenMatchers.put("item.effectiveLocationLibrary", notNullValue(String.class));
    tokenMatchers.put("item.effectiveLocationCampus", notNullValue(String.class));
    tokenMatchers.put("item.effectiveLocationInstitution", notNullValue(String.class));
    tokenMatchers.put("item.effectiveLocationDiscoveryDisplayName", notNullValue(String.class));
    return tokenMatchers;
  }

  private static String findRepresentationCallNumbers(ItemResource itemResource,
                                                      boolean applyHoldingRecord,
                                                      String propertyName) {
    String itemPropertyName = String
      .format(ITEM_REPRESENTATION_PREFIX, StringUtils.capitalize(propertyName));

    if (!applyHoldingRecord) {
      return itemResource.getJson().getString(itemPropertyName);
    }

    return StringUtils.firstNonBlank(
      itemResource.getJson().getString(itemPropertyName),
      itemResource.getHoldingsRecord().getJson().getString(propertyName));
  }

  private static String findRepresentationCopyNumber(ItemResource itemResource,
                                                     boolean applyHoldingRecord) {
    if (applyHoldingRecord) {
      return itemResource.getJson().containsKey("copyNumber")
        ? findItemRepresentationCopyNumber(itemResource)
        : findHoldingRepresentationCopyNumber(itemResource);
    }

    return findItemRepresentationCopyNumber(itemResource);
  }

  private static String findHoldingRepresentationCopyNumber(
          ItemResource itemResource) {
    JsonObject holdingRepresentation = itemResource.getHoldingsRecord().getJson();
    String copyNumber = holdingRepresentation.getString("copyNumber");
    return StringUtils.isNotBlank(copyNumber)
      ? copyNumber
      : StringUtils.EMPTY;
  }

  private static String findItemRepresentationCopyNumber(
          ItemResource itemResource) {
    String copyNumber = itemResource.getResponse().getJson().getString("copyNumber");
    return StringUtils.isNotBlank(copyNumber)
      ? copyNumber
      : StringUtils.EMPTY;
  }

  public static Map<String, Matcher<String>> getLoanContextMatchers(
    IndividualResource loanResource) {
    return getLoanContextMatchers(loanResource.getJson());
  }

  public static Map<String, Matcher<String>> getLoanAdditionalInfoContextMatchers(String info) {
    Map<String, Matcher<String>> matchers = new HashMap<>();
    matchers.put("loan.additionalInfo", Is.is(info));
    return matchers;
  }

  public static Map<String, Matcher<String>> getLoanContextMatchers(
    JsonObject loan) {

    Integer renewalCount = getIntegerProperty(loan, "renewalCount", 0);
    Map<String, Matcher<String>> tokenMatchers = new HashMap<>();
    tokenMatchers.put("loan.initialBorrowDate",
      isEquivalentTo(getDateTimeProperty(loan, "loanDate")));
    tokenMatchers.put("loan.numberOfRenewalsTaken", is(Integer.toString(renewalCount)));
    tokenMatchers.put("loan.dueDate",
      isEquivalentTo(getDateTimeProperty(loan, "dueDate")));
    return tokenMatchers;
  }

  public static Map<String, Matcher<String>> getLoanPolicyContextMatchersForUnlimitedRenewals() {
    return getLoanPolicyContextMatchers("unlimited", "unlimited");
  }

  public static Map<String, Matcher<String>> getLoanPolicyContextMatchers(
    int renewalLimit, int renewalsRemaining) {
    return getLoanPolicyContextMatchers(
      Integer.toString(renewalLimit), Integer.toString(renewalsRemaining));
  }

  private static Map<String, Matcher<String>> getLoanPolicyContextMatchers(
    String renewalLimit, String renewalsRemaining) {
    Map<String, Matcher<String>> tokenMatchers = new HashMap<>();
    tokenMatchers.put("loan.numberOfRenewalsAllowed", is(renewalLimit));
    tokenMatchers.put("loan.numberOfRenewalsRemaining", is(renewalsRemaining));

    return tokenMatchers;
  }

  public static Map<String, Matcher<String>> getRequestContextMatchers(
    IndividualResource requestResource) {

    return getRequestContextMatchers(requestResource.getJson());
  }

  public static Map<String, Matcher<String>> getRequestContextMatchers(
    JsonObject request) {

    Map<String, Matcher<String>> tokenMatchers = new HashMap<>();
    tokenMatchers.put("request.servicePointPickup", notNullValue(String.class));

    ZonedDateTime requestExpirationDate = getDateTimeProperty(request, "requestExpirationDate");
    if (requestExpirationDate != null) {
      tokenMatchers.put("request.requestExpirationDate ", isEquivalentTo(ZonedDateTime
        .of(requestExpirationDate.withZoneSameInstant(UTC).toLocalDate(),
          LocalTime.MIDNIGHT.minusSeconds(1), UTC)));
    }

    ZonedDateTime holdShelfExpirationDate = getDateTimeProperty(request, "holdShelfExpirationDate");
    if (holdShelfExpirationDate != null) {
      tokenMatchers.put("request.holdShelfExpirationDate", isEquivalentTo(holdShelfExpirationDate));
    }

    return tokenMatchers;
  }

  public static Map<String, Matcher<String>> getCancelledRequestContextMatchers(
    JsonObject request) {

    Map<String, Matcher<String>> tokenMatchers = new HashMap<>();
    tokenMatchers.put("request.additionalInfo",
      is(request.getString("cancellationAdditionalInformation")));
    tokenMatchers.put("request.reasonForCancellation", notNullValue(String.class));
    return tokenMatchers;
  }

  public static Map<String, Matcher<String>> getTransitContextMatchers(
    IndividualResource fromServicePoint, IndividualResource toServicePoint) {

    Map<String, Matcher<String>> tokenMatchers = new HashMap<>();
    tokenMatchers.put("item.fromServicePoint", servicePointNameMatcher(fromServicePoint));
    tokenMatchers.put("item.toServicePoint", servicePointNameMatcher(toServicePoint));
    return tokenMatchers;
  }

  public static Matcher<String> servicePointNameMatcher(IndividualResource servicePoint) {
    return is(servicePoint.getJson().getString("name"));
  }

  @SuppressWarnings("unchecked")
  public static Matcher<? super String> getMultipleLoansContextMatcher(
    IndividualResource user,
    Collection<Pair<IndividualResource, ItemResource>> loansWithItems,
    Matcher<? super String> loanPolicyMatcher) {

    Matcher<? super String> userContextMatcher = toStringMatcher(getUserContextMatchers(user));

    Matcher<? super String>[] loanWithItemContextMatchers = loansWithItems.stream()
      .map(p -> getLoanWithItemContextMatcher(p.getLeft(), p.getRight(), loanPolicyMatcher))
      .toArray(Matcher[]::new);

    return allOf(
      userContextMatcher,
      hasJsonPath("loans[*]", hasItems(loanWithItemContextMatchers)));
  }

  private static Matcher<? super String> getLoanWithItemContextMatcher(
    IndividualResource loan, ItemResource item,
    Matcher<? super String> loanPolicyMatcher) {

    Matcher<? super String> loanContextMatcher =
      toStringMatcher(getLoanContextMatchers(loan));
    Matcher<? super String> itemContextMatcher =
      toStringMatcher(getItemContextMatchers(item, true));

    return allOf(loanContextMatcher, itemContextMatcher, loanPolicyMatcher);
  }

  public static Matcher<?> getBundledFeeChargeContextMatcher(UserResource user,
    Collection<Account> accounts) {

    return allOf(
      toStringMatcher(getUserContextMatchers(user)),
      hasJsonPath("feeCharges[*]", hasSize(accounts.size())),
      hasJsonPath("feeCharges[*]", hasItems(accounts.stream()
      .map(TemplateContextMatchers::getSingleFeeChargeContextMatcher)
      .toArray(Matcher[]::new))));
  }

  public static Matcher<Account> getSingleFeeChargeContextMatcher(Account account) {
    return allOf(
      hasJsonPath("feeCharge.owner", is(account.getFeeFineOwner())),
      hasJsonPath("feeCharge.type", is(account.getFeeFineType())),
      hasJsonPath("feeCharge.paymentStatus", is(account.getPaymentStatus())),
      hasJsonPath("feeCharge.amount", is(account.getAmount().toScaledString())),
      hasJsonPath("feeCharge.remainingAmount", is(account.getRemaining().toScaledString()))
    );
  }

  public static Matcher<Object> getSingleFeeChargeContextMatcher(JsonObject account) {
    return allOf(
      hasJsonPath("feeCharge.owner", is(account.getString("feeFineOwner"))),
      hasJsonPath("feeCharge.type", is(account.getString("feeFineType"))),
      hasJsonPath("feeCharge.paymentStatus", is(getNestedStringProperty(account, "paymentStatus", "name"))),
      hasJsonPath("feeCharge.amount", is(new FeeAmount(account.getDouble("amount")).toScaledString())),
      hasJsonPath("feeCharge.remainingAmount", is(new FeeAmount(account.getDouble("remaining")).toScaledString()))
    );
  }

  public static Matcher<Object> getFeeChargeAdditionalInfoContextMatcher(String additionalInfo) {
    return hasJsonPath("feeCharge.additionalInfo", is(additionalInfo));
  }

  public static Matcher<Object> getFeeChargeAdditionalInfoContextMatcher(String additionalInfo,
    String amount, String actionType) {

    return allOf(hasJsonPath("feeCharge.additionalInfo", is(additionalInfo)),
      hasJsonPath("feeCharge.amount", is(amount)),
      hasJsonPath("feeAction.type", is(actionType)));
  }

  public static Matcher<?> getFeeActionContextMatcher(FeeFineAction action) {
    return allOf(
      hasJsonPath("feeAction.type", is(action.getActionType())),
      hasJsonPath("feeAction.actionDate", isEquivalentTo(action.getDateAction())),
      hasJsonPath("feeAction.actionDateTime", isEquivalentTo(action.getDateAction())),
      hasJsonPath("feeAction.amount", is(action.getAmount().toDouble())),
      hasJsonPath("feeAction.remainingAmount", is(action.getBalance()))
    );
  }

  public static Matcher<Object> getFeeActionContextMatcher(JsonObject action) {
    return allOf(
      hasJsonPath("feeAction.type", is(action.getString("typeAction"))),
      hasJsonPath("feeAction.actionDate", isEquivalentTo(getDateTimeProperty(action, "dateAction"))),
      hasJsonPath("feeAction.actionDateTime", isEquivalentTo(getDateTimeProperty(action, "dateAction"))),
      hasJsonPath("feeAction.amount", is(new FeeAmount(action.getDouble("amountAction")).toScaledString())),
      hasJsonPath("feeAction.remainingAmount", is(new FeeAmount(action.getDouble("balance")).toScaledString()))
    );
  }

  public static Map<String, Matcher<String>> getInstanceContextMatchers(IndividualResource instance) {
    return getInstanceContextMatchers(instance.getJson());
  }

  public static Map<String, Matcher<String>> getInstanceContextMatchers(JsonObject instanceJson) {
    Instance instance = new InstanceMapper().toDomain(instanceJson);
    Map<String, Matcher<String>> tokenMatchers = new HashMap<>();
    tokenMatchers.put("item.title", is(instance.getTitle()));
    tokenMatchers.put("item.primaryContributor", is(instance.getPrimaryContributorName()));
    tokenMatchers.put("item.allContributors", is(instance.getContributorNames().collect(joining("; "))));

    return tokenMatchers;
  }
}
