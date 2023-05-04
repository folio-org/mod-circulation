package org.folio.circulation.domain.notice;

import static java.lang.Math.max;
import static java.util.stream.Collectors.joining;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTime;
import static org.folio.circulation.support.utils.FeeFineActionHelper.getPatronInfoFromComment;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.CallNumberComponents;
import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.Department;
import org.folio.circulation.domain.FeeFineAction;
import org.folio.circulation.domain.Instance;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.User;
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

  private TemplateContextUtil() {
  }

  public static JsonObject createLoanNoticeContextWithoutUser(Loan loan) {
    return new JsonObject()
      .put(ITEM, createItemContext(loan.getItem()))
      .put(LOAN, createLoanContext(loan));
  }

  public static JsonObject createGroupedNoticeContext(User user, String groupToken,
    Collection<JsonObject> noticeContexts) {

    return new JsonObject()
      .put(USER, createUserContext(user))
      .put(groupToken, new JsonArray(new ArrayList<>(noticeContexts)));
  }

  public static JsonObject createLoanNoticeContext(Loan loan) {
    return new JsonObject()
      .put(USER, createUserContext(loan.getUser()))
      .put(ITEM, createItemContext(loan.getItem()))
      .put(LOAN, createLoanContext(loan));
  }

  public static JsonObject createRequestNoticeContext(Request request) {
    JsonObject requestNoticeContext = new JsonObject()
      .put(USER, createUserContext(request.getRequester()))
      .put(REQUEST, createRequestContext(request))
      .put(ITEM, createItemContext(request));

    if (request.isRecall() && request.getLoan() != null) {
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
        staffSlipContext.put(REQUESTER, createUserContext(requester, request.getDeliveryAddressTypeId()));
      }
    }

    write(staffSlipContext, CURRENT_DATE_TIME, ClockUtil.getZonedDateTime());

    return staffSlipContext;
  }

  public static JsonObject createUserContext(User user, String deliveryAddressTypeId) {
    JsonObject address = user.getAddressByType(deliveryAddressTypeId);

    JsonObject userContext = createUserContext(user);
    if(address != null){
      userContext
        .put("addressLine1", address.getString("addressLine1", null))
        .put("addressLine2", address.getString("addressLine2", null))
        .put("city", address.getString("city", null))
        .put("region", address.getString("region", null))
        .put("postalCode", address.getString("postalCode", null))
        .put("countryId", address.getString("countryId", null));
    }

    return userContext;
  }

  public static JsonObject createUserContext(User user) {
    return new JsonObject()
    .put("firstName", user.getFirstName())
    .put("preferredFirstName", user.getPreferredFirstName() == null ? user.getFirstName() : user.getPreferredFirstName())
    .put("lastName", user.getLastName())
    .put("middleName", user.getMiddleName())
    .put("barcode", user.getBarcode())
    .put("patronGroup", user.getPatronGroup()!=null ? user.getPatronGroup().getGroup():"")
    .put("departments", user.getDepartments() != null && !user.getDepartments().isEmpty() ?
        user.getDepartments().stream().map(Department::getName).collect(joining("; ")) : "");
   }

  private static JsonObject createItemContext(Item item) {
    String yearCaptionsToken = String.join("; ", item.getYearCaption());
    String copyNumber = item.getCopyNumber() != null ? item.getCopyNumber() : "";

    JsonObject itemContext = createInstanceContext(item.getInstance())
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
      .put("descriptionOfPieces", item.getDescriptionOfPieces());

    Location location = item.getLocation();

    if (location != null) {
      itemContext
        .put("effectiveLocationSpecific", location.getName())
        .put("effectiveLocationLibrary", location.getLibraryName())
        .put("effectiveLocationCampus", location.getCampusName())
        .put("effectiveLocationInstitution", location.getInstitutionName())
        .put("effectiveLocationDiscoveryDisplayName", location.getDiscoveryDisplayName());
    }

    CallNumberComponents callNumberComponents = item.getCallNumberComponents();
    if (callNumberComponents != null) {
      itemContext
        .put("callNumber", callNumberComponents.getCallNumber())
        .put("callNumberPrefix", callNumberComponents.getPrefix())
        .put("callNumberSuffix", callNumberComponents.getSuffix());
    }

    return itemContext;
  }

  private static JsonObject createItemContext(Request request) {
    return request.hasItem()
      ? createItemContext(request.getItem())
      : createInstanceContext(request.getInstance());
  }

  private static JsonObject createInstanceContext(Instance instance) {
    JsonObject instanceContext = new JsonObject();

    if (instance != null) {
      instanceContext
        .put("title", instance.getTitle())
        .put("primaryContributor", instance.getPrimaryContributorName())
        .put("allContributors", instance.getContributorNames().collect(joining("; ")));
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
      .map(Request::getRequestExpirationDate)
      .ifPresent(value -> write(requestContext, "requestExpirationDate", value));
    optionalRequest
      .map(Request::getHoldShelfExpirationDate)
      .ifPresent(value -> write(requestContext, "holdShelfExpirationDate", value));
    optionalRequest
      .map(Request::getCancellationAdditionalInformation)
      .ifPresent(value -> requestContext.put("additionalInfo", value));
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
    log.debug("createFeeChargeContext:: params account={}, chargeAction={}", account, chargeAction);

    JsonObject context = new JsonObject();
    write(context, "owner", account.getFeeFineOwner());
    write(context, "type", account.getFeeFineType());
    write(context, "paymentStatus", account.getPaymentStatus());
    write(context, "amount", account.getAmount().toScaledString());
    write(context, "remainingAmount", account.getRemaining().toScaledString());
    write(context, "chargeDate", account.getCreationDate());
    write(context, "chargeDateTime", account.getCreationDate());

    if (chargeAction != null) {
      log.info("createFeeChargeContext:: adding charge action info. account={}, chargeAction={}",
        account, chargeAction);
      write(context, "additionalInfo", getPatronInfoFromComment(chargeAction));
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

}
