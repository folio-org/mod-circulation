package org.folio.circulation.domain.notice;

import static java.lang.Math.max;
import static java.util.stream.Collectors.joining;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTime;
import static org.folio.circulation.support.utils.FeeFineActionHelper.getPatronInfoFromComment;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.CallNumberComponents;
import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.Department;
import org.folio.circulation.domain.FeeFineAction;
import org.folio.circulation.domain.Instance;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.Publication;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.support.utils.ClockUtil;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class TemplateContextUtil {

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final String USER = "user";
  private static final String ITEM = "item";
  private static final String REQUEST = "request";
  private static final String REQUESTER = "requester";
  private static final String LOAN = "loan";
  private static final String FEE_CHARGE = "feeCharge";
  private static final String FEE_ACTION = "feeAction";
  private static final String UNLIMITED = "unlimited";
  public static final String CURRENT_DATE_TIME = "currentDateTime";
  private static final String ADDITIONAL_INFO_KEY = "additionalInfo";

  private TemplateContextUtil() {
  }

  public static JsonObject createLoanNoticeContextWithoutUser(Loan loan) {
    log.debug("createLoanNoticeContextWithoutUser:: parameters loan: {}", loan);

    if (loan == null) {
      log.info("createLoanNoticeContextWithoutUser:: loan is null");
      return new JsonObject();
    }

    return new JsonObject()
      .put(ITEM, createItemContext(loan.getItem()))
      .put(LOAN, createLoanContext(loan));
  }

  public static JsonObject createGroupedNoticeContext(User user, String groupToken,
    Collection<JsonObject> noticeContexts) {

    return new JsonObject()
      .put(USER, createUserContext(user).asJson())
      .put(groupToken, new JsonArray(new ArrayList<>(noticeContexts)));
  }

  public static JsonObject createLoanNoticeContext(Loan loan) {
    return new JsonObject()
      .put(USER, createUserContext(loan.getUser()).asJson())
      .put(ITEM, createItemContext(loan.getItem()))
      .put(LOAN, createLoanContext(loan));
  }

  public static JsonObject createRequestNoticeContext(Request request) {
    JsonObject requestNoticeContext = new JsonObject()
      .put(USER, createUserContext(request.getRequester()).asJson())
      .put(REQUEST, createRequestContext(request))
      .put(ITEM, createItemContext(request));

    if (request.hasLoan()) {
      requestNoticeContext.put(LOAN, createLoanContext(request.getLoan()));
    }
    return requestNoticeContext;
  }

  public static JsonObject createCheckInContext(CheckInContext context) {
    Item item = context.getItem();
    Request firstRequest = context.getHighestPriorityFulfillableRequest();
    JsonObject staffSlipContext = createStaffSlipContext(item, firstRequest);
    JsonObject itemContext = staffSlipContext.getJsonObject(ITEM);

    if (ObjectUtils.allNotNull(item, itemContext)) {
      write(itemContext, "lastCheckedInDateTime", ClockUtil.getZonedDateTime());
      if (item.getInTransitDestinationServicePoint() != null) {
        itemContext.put("toServicePoint", item.getInTransitDestinationServicePoint().getName());
      }
      if (context.getCheckInServicePoint() != null) {
        itemContext.put("fromServicePoint", context.getCheckInServicePoint().getName());
      }
    }

    return staffSlipContext;
  }

  public static JsonObject createStaffSlipContext(Request request) {
    if (request == null) {
      return new JsonObject();
    }

    return createStaffSlipContext(request.getItem(), request);
  }

  public static JsonObject addPrimaryServicePointNameToStaffSlipContext(JsonObject entries,
    ServicePoint primaryServicePoint, String slipsCollectionName) {

    log.debug("addPrimaryServicePointNameToStaffSlipContext:: parameters entries: {}, " +
      "primaryServicePoint: {}, slipsCollectionName: {}", entries, primaryServicePoint, slipsCollectionName);
    if (primaryServicePoint == null) {
      log.info("addPrimaryServicePointNameToStaffSlipContext:: primaryServicePoint object is null");
      return entries;
    }

    if (entries == null) {
      log.info("addPrimaryServicePointNameToStaffSlipContext:: entries JsonObject is null, " +
        "primaryServicePointName: {}", primaryServicePoint.getName());
      return new JsonObject();
    }

    entries.getJsonArray(slipsCollectionName)
      .stream()
      .map(JsonObject.class::cast)
      .map(pickSlip -> pickSlip.getJsonObject(ITEM))
      .filter(Objects::nonNull)
      .forEach(item -> item.put("effectiveLocationPrimaryServicePointName", primaryServicePoint.getName()));

    log.debug("addPrimaryServicePointNameToStaffSlipContext:: Result entries: {}, " +
      "primaryServicePointName: {}", () -> entries, primaryServicePoint::getName);

    return entries;
  }

  public static JsonObject createStaffSlipContext(
    Item item, Request request) {

    JsonObject staffSlipContext = new JsonObject();

    if (item != null) {
      JsonObject itemContext = createItemContext(item);
      if (item.getLastCheckIn() != null) {
        write(itemContext, "lastCheckedInDateTime", item.getLastCheckIn().getDateTime());
      }
      staffSlipContext.put(ITEM, itemContext);
    }

    if (request != null) {
      staffSlipContext.put(REQUEST, createRequestContext(request));

      User requester = request.getRequester();
      if (requester != null) {
        staffSlipContext.put(REQUESTER, createUserContext(requester, request.getDeliveryAddressTypeId()).asJson());
      }
    }

    write(staffSlipContext, CURRENT_DATE_TIME, ClockUtil.getZonedDateTime());

    return staffSlipContext;
  }

  public static UserContext createUserContext(User user, String deliveryAddressTypeId) {
    return createUserContext(user)
      .withAddressProperties(user.getAddressByType(deliveryAddressTypeId));
  }

  public static UserContext createUserContext(User user) {
    return new UserContext()
      .with(UserContext.FIRST_NAME, user.getFirstName())
      .with(UserContext.PREFERRED_FIRST_NAME, user.getPreferredFirstName() == null ? user.getFirstName() : user.getPreferredFirstName())
      .with(UserContext.LAST_NAME, user.getLastName())
      .with(UserContext.MIDDLE_NAME, user.getMiddleName())
      .with(UserContext.BARCODE, user.getBarcode())
      .with(UserContext.PATRON_GROUP, user.getPatronGroup() != null ? user.getPatronGroup().getGroup() : "")
      .with(UserContext.DEPARTMENTS, user.getDepartments() != null && !user.getDepartments().isEmpty() ?
        user.getDepartments().stream().map(Department::getName).collect(joining("; ")) : "")
      .withPrimaryAddressProperties(user.getPrimaryAddress());
  }

  private static JsonObject createItemContext(Item item) {
    log.debug("createItemContext:: parameters item: {}", item);
    String yearCaptionsToken = String.join("; ", item.getYearCaption());
    String copyNumber = item.getCopyNumber() != null ? item.getCopyNumber() : "";
    String administrativeNotes = String.join("; ", item.getAdministrativeNotes());

    JsonObject itemContext = createInstanceContext(item.getInstance(), item)
      .put("barcode", item.getBarcode())
      .put("status", item.getStatus().getValue())
      .put("enumeration", item.getEnumeration())
      .put("volume", item.getVolume())
      .put("chronology", item.getChronology())
      .put("yearCaption", yearCaptionsToken)
      .put("materialType", item.getMaterialTypeName())
      .put("loanType", item.getLoanTypeName())
      .put("copy", copyNumber)
      .put("numberOfPieces", item.getNumberOfPieces())
      .put("displaySummary", item.getDisplaySummary())
      .put("descriptionOfPieces", item.getDescriptionOfPieces())
      .put("accessionNumber", item.getAccessionNumber())
      .put("administrativeNotes", administrativeNotes);

    Location location = (item.canFloatThroughCheckInServicePoint() && item.isInStatus(ItemStatus.AVAILABLE)) ?
      item.getFloatDestinationLocation() : item.getLocation();

    if (location != null) {
      log.info("createItemContext:: location is not null");

      itemContext
        .put("effectiveLocationSpecific", location.getName())
        .put("effectiveLocationLibrary", location.getLibraryName())
        .put("effectiveLocationCampus", location.getCampusName())
        .put("effectiveLocationInstitution", item.isDcbItem()?item.getLendingLibraryCode():location.getInstitutionName())
        .put("effectiveLocationDiscoveryDisplayName", location.getDiscoveryDisplayName());

      var primaryServicePoint = location.getPrimaryServicePoint();
      if (primaryServicePoint != null) {
        log.info("createItemContext:: primaryServicePoint is not null");
        itemContext.put("effectiveLocationPrimaryServicePointName", primaryServicePoint.getName());
      }
    }

    CallNumberComponents callNumberComponents = item.getCallNumberComponents();
    if (callNumberComponents != null) {
      log.info("createItemContext:: callNumberComponents is not null");
      itemContext
        .put("callNumber", callNumberComponents.getCallNumber())
        .put("callNumberPrefix", callNumberComponents.getPrefix())
        .put("callNumberSuffix", callNumberComponents.getSuffix());
    }

    log.info("createItemContext:: result {}", itemContext);
    return itemContext;
  }

  private static JsonObject createItemContext(Request request) {
    return request.hasItem()
      ? createItemContext(request.getItem())
      : createInstanceContext(request.getInstance(), request.getItem());
  }

  private static JsonObject createInstanceContext(Instance instance, Item item) {
    JsonObject instanceContext = new JsonObject();

    if (instance != null) {
      instanceContext
        .put("title", item != null && item.isDcbItem() ?
          item.getDcbItemTitle() : instance.getTitle())
        .put("instanceHrid", instance.getHrid())
        .put("primaryContributor", instance.getPrimaryContributorName())
        .put("allContributors", instance.getContributorNames().collect(joining("; ")))
        .put("datesOfPublication", instance.getPublication().stream().
          map(Publication::getDateOfPublication).collect(joining("; ")))
        .put("editions", String.join("; ", instance.getEditions()))
        .put("physicalDescriptions", String.join("; ", instance.getPhysicalDescriptions()));
    }

    return instanceContext;
  }

  private static JsonObject createRequestContext(Request request) {
    Optional<Request> optionalRequest = Optional.ofNullable(request);
    JsonObject requestContext = new JsonObject();

    optionalRequest
      .map(Request::getId)
      .ifPresent(value -> requestContext.put("requestID", value));
    optionalRequest
      .map(Request::getPickupServicePoint)
      .map(ServicePoint::getName)
      .ifPresent(value -> requestContext.put("servicePointPickup", value));
    optionalRequest
      .map(Request::getRequestDate)
      .ifPresent(value -> write(requestContext, "requestDate", value));
    optionalRequest
      .map(Request::getRequestExpirationDate)
      .ifPresent(value -> write(requestContext, "requestExpirationDate", value));
    optionalRequest
      .map(Request::getHoldShelfExpirationDate)
      .ifPresent(value -> write(requestContext, "holdShelfExpirationDate", value));
    optionalRequest
      .map(Request::getCancellationAdditionalInformation)
      .ifPresent(value -> requestContext.put(ADDITIONAL_INFO_KEY, value));
    optionalRequest
      .map(Request::getCancellationReasonPublicDescription)
      .map(Optional::of)
      .orElse(optionalRequest.map(Request::getCancellationReasonName))
      .ifPresent(value -> requestContext.put("reasonForCancellation", value));
    optionalRequest
      .map(Request::getAddressType)
      .ifPresent(value -> requestContext.put("deliveryAddressType", value.getName()));

    optionalRequest
      .map(Request::getPatronComments)
      .ifPresent(value -> write(requestContext, "patronComments", value));

    return requestContext;
  }

  private static JsonObject createLoanContext(Loan loan) {
    JsonObject loanContext = new JsonObject();

    write(loanContext, "initialBorrowDate", loan.getLoanDate());
    write(loanContext, "dueDate", loan.getDueDate());
    if (loan.getReturnDate() != null) {
      write(loanContext, "checkedInDate", loan.getReturnDate());
    }

    loanContext.put("numberOfRenewalsTaken", Integer.toString(loan.getRenewalCount()));
    LoanPolicy loanPolicy = loan.getLoanPolicy();
    if (loanPolicy != null) {
      if (loanPolicy.unlimitedRenewals()) {
        loanContext.put("numberOfRenewalsAllowed", UNLIMITED);
        loanContext.put("numberOfRenewalsRemaining", UNLIMITED);
      } else {
        int renewalLimit = loanPolicy.getRenewalLimit();
        int renewalsRemaining = max(renewalLimit - loan.getRenewalCount(), 0);
        loanContext.put("numberOfRenewalsAllowed", Integer.toString(renewalLimit));
        loanContext.put("numberOfRenewalsRemaining", Integer.toString(renewalsRemaining));
      }
    }

    write(loanContext, ADDITIONAL_INFO_KEY, loan.getLatestPatronInfoAddedComment());

    return loanContext;
  }

  public static JsonObject createFeeFineChargeNoticeContext(Account account, Loan loan,
    FeeFineAction chargeAction) {

    return createLoanNoticeContext(loan)
      .put(FEE_CHARGE, createFeeChargeContext(account, chargeAction));
  }

  public static JsonObject createFeeFineChargeNoticeContextWithoutUser(Account account, Loan loan,
    FeeFineAction chargeAction) {

    return createLoanNoticeContextWithoutUser(loan)
      .put(FEE_CHARGE, createFeeChargeContext(account, chargeAction));
  }

  public static JsonObject createFeeFineChargeAndActionNoticeContext(Account account, Loan loan,
    FeeFineAction currentAction, FeeFineAction chargeAction) {

    return createFeeFineChargeNoticeContext(account, loan, chargeAction)
      .put(FEE_ACTION, createFeeActionContext(currentAction));
  }

  private static JsonObject createFeeChargeContext(Account account, FeeFineAction chargeAction) {
    log.debug("createFeeChargeContext:: params account: {}, chargeAction: {}", account, chargeAction);

    JsonObject context = new JsonObject();
    write(context, "owner", account.getFeeFineOwner());
    write(context, "type", account.getFeeFineType());
    write(context, "paymentStatus", account.getPaymentStatus());
    write(context, "amount", account.getAmount().toScaledString());
    write(context, "remainingAmount", account.getRemaining().toScaledString());
    write(context, "chargeDate", account.getCreationDate());
    write(context, "chargeDateTime", account.getCreationDate());

    if (chargeAction != null) {
      log.info("createFeeChargeContext:: adding charge action info. account: {}, chargeAction: {}",
        account, chargeAction);
      write(context, ADDITIONAL_INFO_KEY, getPatronInfoFromComment(chargeAction));
    }

    return context;
  }

  private static JsonObject createFeeActionContext(FeeFineAction feeFineAction) {
    final JsonObject context = new JsonObject();
    String actionDateString = formatDateTime(feeFineAction.getDateAction());

    write(context, "type", feeFineAction.getActionType());
    write(context, "actionDate", actionDateString);
    write(context, "actionDateTime", actionDateString);
    write(context, "amount", feeFineAction.getAmount().toScaledString());
    write(context, "remainingAmount", feeFineAction.getBalance().toScaledString());

    return context;
  }

  public static class UserContext {
    public static final String FIRST_NAME = "firstName";
    public static final String PREFERRED_FIRST_NAME = "preferredFirstName";
    public static final String LAST_NAME = "lastName";
    public static final String MIDDLE_NAME = "middleName";
    public static final String BARCODE = "barcode";
    public static final String PATRON_GROUP = "patronGroup";
    public static final String DEPARTMENTS = "departments";
    public static final String ADDRESS_TYPE_NAME = "addressType";
    public static final String ADDRESS_LINE_1 = "addressLine1";
    public static final String ADDRESS_LINE_2 = "addressLine2";
    public static final String CITY = "city";
    public static final String REGION = "region";
    public static final String POSTAL_CODE = "postalCode";
    public static final String COUNTRY_ID = "countryId";

    public static final String PRIMARY_ADDRESS_ADDRESS_TYPE_NAME = "primaryDeliveryAddressType";
    public static final String PRIMARY_ADDRESS_ADDRESS_LINE_1 = "primaryAddressLine1";
    public static final String PRIMARY_ADDRESS_ADDRESS_LINE_2 = "primaryAddressLine2";
    public static final String PRIMARY_ADDRESS_CITY = "primaryCity";
    public static final String PRIMARY_ADDRESS_REGION = "primaryStateProvRegion";
    public static final String PRIMARY_ADDRESS_POSTAL_CODE = "primaryZipPostalCode";
    public static final String PRIMARY_ADDRESS_COUNTRY_ID = "primaryCountry";

    JsonObject context = new JsonObject();

    public UserContext with(String key, String value) {
      context.put(key, value);
      return this;
    }

    public UserContext withAddressProperties(JsonObject address) {
      if (address != null) {
        return this
          .with(UserContext.ADDRESS_LINE_1, address.getString("addressLine1", null))
          .with(UserContext.ADDRESS_LINE_2, address.getString("addressLine2", null))
          .with(UserContext.CITY, address.getString("city", null))
          .with(UserContext.REGION, address.getString("region", null))
          .with(UserContext.POSTAL_CODE, address.getString("postalCode", null))
          .with(UserContext.COUNTRY_ID,  address.getString(COUNTRY_ID, null))
          .with(UserContext.ADDRESS_TYPE_NAME, address.getString("addressTypeName", null));
      } else {
        return this;
      }
    }

    public String getCountryNameByCodeIgnoreCase(String code) {
      if (StringUtils.isEmpty(code) || !Stream.of(Locale.getISOCountries()).toList().contains(code)) {
        log.info("getCountryNameByCodeIgnoreCase:: Invalid country code {}", code);
        return null;
      }

      return new Locale("",code).getDisplayName();
    }

    public UserContext withPrimaryAddressProperties(JsonObject address) {
      if (address != null) {
        return this
          .with(UserContext.PRIMARY_ADDRESS_ADDRESS_LINE_1, address.getString("addressLine1", null))
          .with(UserContext.PRIMARY_ADDRESS_ADDRESS_LINE_2, address.getString("addressLine2", null))
          .with(UserContext.PRIMARY_ADDRESS_CITY, address.getString("city", null))
          .with(UserContext.PRIMARY_ADDRESS_REGION, address.getString("region", null))
          .with(UserContext.PRIMARY_ADDRESS_POSTAL_CODE, address.getString("postalCode", null))
          .with(UserContext.PRIMARY_ADDRESS_COUNTRY_ID, getCountryNameByCodeIgnoreCase(address.getString(COUNTRY_ID, null)))
          .with(UserContext.PRIMARY_ADDRESS_ADDRESS_TYPE_NAME, address.getString("addressTypeName", null));
      } else {
        return this;
      }
    }

    public JsonObject asJson() {
      return context;
    }
  }
}
