package api.support.fixtures;

import static api.support.matchers.JsonObjectMatcher.toStringMatcher;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.folio.circulation.support.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getObjectProperty;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.circulation.support.http.client.IndividualResource;
import org.hamcrest.Matcher;

import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;

public class TemplateContextMatchers {

  private static final String ITEM_REPRESENTATION_PREFIX = "itemLevel%s";

  private TemplateContextMatchers() {
  }

  public static Map<String, Matcher<String>> getRequesterContextMatchers(IndividualResource userResource) {
    return getUserContextMatchers(userResource, "requester");
  }

  public static Map<String, Matcher<String>> getUserContextMatchers(IndividualResource userResource) {
    return getUserContextMatchers(userResource, "user");
  }

  public static Map<String, Matcher<String>> getUserContextMatchers(IndividualResource userResource, String prefix) {
    JsonObject user = userResource.getJson();
    JsonObject personal = getObjectProperty(user, "personal");

    Map<String, Matcher<String>> tokenMatchers = new HashMap<>();
    tokenMatchers.put(prefix + ".firstName", is(personal.getString("firstName")));
    tokenMatchers.put(prefix + ".lastName", is(personal.getString("lastName")));
    tokenMatchers.put(prefix + ".barcode", is(user.getString("barcode")));
    return tokenMatchers;
  }

  public static Map<String, Matcher<String>> getItemContextMatchers(InventoryItemResource itemResource,
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
    return tokenMatchers;
  }

  private static String findRepresentationCallNumbers(InventoryItemResource itemResource,
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

  private static String findRepresentationCopyNumber(InventoryItemResource itemResource,
                                                     boolean applyHoldingRecord) {
    if (applyHoldingRecord) {
      return itemResource.getJson().containsKey("copyNumber")
        ? findItemRepresentationCopyNumber(itemResource)
        : findHoldingRepresentationCopyNumber(itemResource);
    }

    return findItemRepresentationCopyNumber(itemResource);
  }

  private static String findHoldingRepresentationCopyNumber(InventoryItemResource itemResource) {
    JsonObject holdingRepresentation = itemResource.getHoldingsRecord().getJson();
    String copyNumber = holdingRepresentation.getString("copyNumber");
    return StringUtils.isNotBlank(copyNumber)
      ? copyNumber
      : StringUtils.EMPTY;
  }

  private static String findItemRepresentationCopyNumber(InventoryItemResource itemResource) {
    String copyNumber = itemResource.getResponse().getJson().getString("copyNumber");
    return StringUtils.isNotBlank(copyNumber)
      ? copyNumber
      : StringUtils.EMPTY;
  }

  public static Map<String, Matcher<String>> getLoanContextMatchers(
    IndividualResource loanResource) {
    return getLoanContextMatchers(loanResource.getJson());
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
    tokenMatchers.put("request.requestExpirationDate ",
      isEquivalentTo(getDateTimeProperty(request, "requestExpirationDate")));
    tokenMatchers.put("request.holdShelfExpirationDate",
      isEquivalentTo(getDateTimeProperty(request, "holdShelfExpirationDate")));
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
    Collection<Pair<IndividualResource, InventoryItemResource>> loansWithItems,
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
    IndividualResource loan, InventoryItemResource item,
    Matcher<? super String> loanPolicyMatcher) {

    Matcher<? super String> loanContextMatcher =
      toStringMatcher(getLoanContextMatchers(loan));
    Matcher<? super String> itemContextMatcher =
      toStringMatcher(getItemContextMatchers(item, true));

    return allOf(loanContextMatcher, itemContextMatcher, loanPolicyMatcher);
  }
}
