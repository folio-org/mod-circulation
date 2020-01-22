package org.folio.circulation.domain.notice;

import static java.lang.Math.max;
import static java.util.stream.Collectors.joining;
import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.util.Optional;

import org.folio.circulation.domain.CallNumberComponents;
import org.folio.circulation.domain.CheckInProcessRecords;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.support.JsonArrayHelper;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import io.vertx.core.json.JsonObject;

public class TemplateContextUtil {

  private static final String USER = "user";
  private static final String ITEM = "item";
  private static final String REQUEST = "request";
  private static final String LOAN = "loan";

  private static final String UNLIMITED = "unlimited";

  private TemplateContextUtil() {
  }

  public static JsonObject createLoanNoticeContextWithoutUser(Loan loan) {
    return new JsonObject()
      .put(ITEM, createItemContext(loan.getItem()))
      .put(LOAN, createLoanContext(loan));
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
      .put(ITEM, createItemContext(request.getItem()))
      .put(REQUEST, createRequestContext(request));

    if (request.getRequestType() == RequestType.RECALL && request.getLoan() != null) {
      requestNoticeContext.put(LOAN, createLoanContext(request.getLoan()));
    }
    return requestNoticeContext;
  }

  public static JsonObject createAvailableNoticeContext(Item item, User user, Request request) {
    return new JsonObject()
      .put(USER, createUserContext(user))
      .put(ITEM, createItemContext(item))
      .put(REQUEST, createRequestContext(request));
  }

  public static JsonObject createCheckInContext(CheckInProcessRecords records) {
    JsonObject checkInContext = new JsonObject();

    Item item = records.getItem();
    if (item != null) {
      JsonObject itemContext = createItemContext(item);
      if (item.getInTransitDestinationServicePoint() != null) {
        itemContext.put("fromServicePoint", records.getCheckInServicePoint().getName());
        itemContext.put("toServicePoint", item.getInTransitDestinationServicePoint().getName());
      }
      write(itemContext, "lastCheckedInDateTime", DateTime.now(DateTimeZone.UTC));
      checkInContext.put(ITEM, itemContext);
    }

    Request firstRequest = records.getHighestPriorityFulfillableRequest();
    if (firstRequest != null) {
      checkInContext.put(REQUEST, createRequestContext(firstRequest));

      User requester = firstRequest.getRequester();
      if (requester != null) {
        checkInContext.put("requester", createUserContext(requester, firstRequest.getDeliveryAddressTypeId()));
      }
    }

    return checkInContext;
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
    .put("lastName", user.getLastName())
    .put("middleName", user.getMiddleName())
    .put("barcode", user.getBarcode());
  }

  private static JsonObject createItemContext(Item item) {
    String contributorNamesToken = JsonArrayHelper.toStream(item.getContributorNames())
      .map(o -> o.getString("name"))
      .collect(joining("; "));

    String yearCaptionsToken = String.join("; ", item.getYearCaption());
    String copyNumber = item.getCopyNumber() != null ? item.getCopyNumber() : "";

    JsonObject itemContext = new JsonObject()
      .put("title", item.getTitle())
      .put("barcode", item.getBarcode())
      .put("status", item.getStatus().getValue())
      .put("primaryContributor", item.getPrimaryContributorName())
      .put("allContributors", contributorNamesToken)
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
        .put("effectiveLocationInstitution", location.getInstitutionName());
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
      .map(DateTime::toString)
      .ifPresent(value -> requestContext.put("requestExpirationDate", value));
    optionalRequest
      .map(Request::getHoldShelfExpirationDate)
      .map(DateTime::toString)
      .ifPresent(value -> requestContext.put("holdShelfExpirationDate", value));
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
      .ifPresent(value -> requestContext.put("deliveryAddressType", value.getAddressType()));

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
}
