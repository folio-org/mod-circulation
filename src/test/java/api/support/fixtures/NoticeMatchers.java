package api.support.fixtures;

import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static org.folio.circulation.support.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getObjectProperty;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonArray;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.http.client.IndividualResource;
import org.hamcrest.Matcher;

import io.vertx.core.json.JsonObject;

public class NoticeMatchers {

  private static final String ITEM_REPRESENTATION_PREFIX = "itemLevel%s";

  private NoticeMatchers() {
  }

  public static Map<String, Matcher<String>> getUserContextMatchers(IndividualResource userResource) {
    JsonObject user = userResource.getJson();
    JsonObject personal = getObjectProperty(user, "personal");

    Map<String, Matcher<String>> tokenMatchers = new HashMap<>();
    tokenMatchers.put("user.firstName", is(personal.getString("firstName")));
    tokenMatchers.put("user.lastName", is(personal.getString("lastName")));
    tokenMatchers.put("user.barcode", is(user.getString("barcode")));
    return tokenMatchers;
  }

  public static Map<String, Matcher<String>> getItemContextMatchers(InventoryItemResource itemResource,
                                                                    boolean applyHoldingRecord) {
    JsonObject item = itemResource.getJson();
    String callNumber = findRepresentationCallNumbers(itemResource, applyHoldingRecord, "callNumber");
    String callNumberPrefix = findRepresentationCallNumbers(itemResource, applyHoldingRecord, "callNumberPrefix");
    String callNumberSuffix = findRepresentationCallNumbers(itemResource, applyHoldingRecord, "callNumberSuffix");
    String copyNumbers = findRepresentationCopyNumbers(itemResource, applyHoldingRecord);

    Map<String, Matcher<String>> tokenMatchers = new HashMap<>();
    tokenMatchers.put("item.title", notNullValue(String.class));
    tokenMatchers.put("item.allContributors", notNullValue(String.class));
    tokenMatchers.put("item.barcode", is(item.getString("barcode")));
    tokenMatchers.put("item.callNumber", is(callNumber));
    tokenMatchers.put("item.callNumberPrefix", is(callNumberPrefix));
    tokenMatchers.put("item.callNumberSuffix", is(callNumberSuffix));
    tokenMatchers.put("item.copy", is(copyNumbers));
    tokenMatchers.put("item.materialType", notNullValue(String.class));
    tokenMatchers.put("item.loanType", notNullValue(String.class));
    tokenMatchers.put("item.effectiveLocationSpecific", notNullValue(String.class));
    tokenMatchers.put("item.effectiveLocationLibrary", notNullValue(String.class));
    tokenMatchers.put("item.effectiveLocationCampus", notNullValue(String.class));
    tokenMatchers.put("item.effectiveLocationInstitution", notNullValue(String.class));
    return tokenMatchers;
  }

  private static String findRepresentationCallNumbers(InventoryItemResource itemResource,
                                                      boolean applyHoldingRecord,
                                                      String propertyName) {
    return applyHoldingRecord
      ? itemResource.getHoldingsRecord().getJson().getString(propertyName)
      : itemResource.getResponse().getJson()
      .getString(String.format(ITEM_REPRESENTATION_PREFIX, StringUtils.capitalize(propertyName)));
  }

  private static String findRepresentationCopyNumbers(InventoryItemResource itemResource,
                                                      boolean applyHoldingRecord) {
    return applyHoldingRecord
      ? findHoldingRepresentationCopyNumbers(itemResource)
      : findItemRepresentationCopyNumbers(itemResource);
  }

  private static String findHoldingRepresentationCopyNumbers(InventoryItemResource itemResource) {
    JsonObject holdingRepresentation = itemResource.getHoldingsRecord().getJson();
    String copyNumber = holdingRepresentation.getString("copyNumber");
    return StringUtils.isNotBlank(copyNumber)
      ? copyNumber
      : StringUtils.EMPTY;
  }

  private static String findItemRepresentationCopyNumbers(InventoryItemResource itemResource) {
    JsonArray copyNumbers = itemResource.getResponse().getJson().getJsonArray("copyNumbers");
    return copyNumbers.stream()
      .map(Object::toString)
      .collect(Collectors.joining("; "));
  }

  public static Map<String, Matcher<String>> getLoanContextMatchers(
    IndividualResource loanResource, int renewalsCount) {
    return getLoanContextMatchers(loanResource.getJson(), renewalsCount);
  }

  public static Map<String, Matcher<String>> getLoanContextMatchers(
    JsonObject loan, int renewalsCount) {

    Map<String, Matcher<String>> tokenMatchers = new HashMap<>();
    tokenMatchers.put("loan.initialBorrowDate",
      isEquivalentTo(getDateTimeProperty(loan, "loanDate")));
    tokenMatchers.put("loan.numberOfRenewalsTaken", is(Integer.toString(renewalsCount)));
    tokenMatchers.put("loan.dueDate",
      isEquivalentTo(getDateTimeProperty(loan, "dueDate")));
    return tokenMatchers;
  }

  public static Map<String, Matcher<String>> getLoanPolicyContextMatchers(
    IndividualResource loanPolicyResource, int renewalsCount) {
    JsonObject loanPolicy = loanPolicyResource.getJson();
    JsonObject renewalsPolicy = loanPolicy.getJsonObject("renewalsPolicy");
    boolean unlimitedRenewals = getBooleanProperty(renewalsPolicy, "unlimited");

    Map<String, Matcher<String>> tokenMatchers = new HashMap<>();
    if (unlimitedRenewals) {
      tokenMatchers.put("loan.numberOfRenewalsAllowed", is("unlimited"));
      tokenMatchers.put("loan.numberOfRenewalsRemaining", is("unlimited"));
    } else {
      int renewalLimit = getIntegerProperty(renewalsPolicy, "numberAllowed", 0);
      int renewalsRemaining = renewalLimit - renewalsCount;
      tokenMatchers.put("loan.numberOfRenewalsAllowed", is(Integer.toString(renewalLimit)));
      tokenMatchers.put("loan.numberOfRenewalsRemaining", is(Integer.toString(renewalsRemaining)));
    }

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

}
