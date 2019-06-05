package org.folio.circulation.domain.notice;

import java.util.Optional;
import java.util.stream.Collectors;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.JsonStringArrayHelper;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import io.vertx.core.json.JsonObject;

public class NoticeContextUtil {

  private static final String USER = "user";
  private static final String ITEM = "item";
  private static final String REQUEST = "request";
  private static final String LOAN = "loan";

  private static final String UNLIMITED = "unlimited";

  private NoticeContextUtil() {
  }

  public static JsonObject createLoanNoticeContext(Loan loan, LoanPolicy loanPolicy) {
    return createLoanNoticeContext(loan,loanPolicy, DateTimeZone.UTC);
  }

  public static JsonObject createLoanNoticeContext(
    Loan loan, LoanPolicy loanPolicy, DateTimeZone timeZone) {

    return new JsonObject()
      .put(USER, createUserContext(loan.getUser()))
      .put(ITEM, createItemContext(loan.getItem()))
      .put(LOAN, createLoanContext(loan, loanPolicy, timeZone));
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

  private static JsonObject createUserContext(User user) {
    return new JsonObject()
      .put("firstName", user.getFirstName())
      .put("lastName", user.getLastName())
      .put("middleName", user.getMiddleName())
      .put("barcode", user.getBarcode());
  }

  private static JsonObject createItemContext(Item item) {
    String contributorNamesToken = JsonArrayHelper.toStream(item.getContributorNames())
      .map(o -> o.getString("name"))
      .collect(Collectors.joining("; "));

    String yearCaptionsToken = String.join("; ", item.getYearCaption());

    String captionsToken = JsonStringArrayHelper.toStream(item.getCopyNumbers())
      .collect(Collectors.joining("; "));

    Location location = item.getLocation();

    return new JsonObject()
      .put("title", item.getTitle())
      .put("barcode", item.getBarcode())
      .put("status", item.getStatus().getValue())
      .put("primaryContributor", item.getPrimaryContributorName())
      .put("allContributors", contributorNamesToken)
      .put("callNumber", item.getCallNumber())
      .put("callNumberPrefix", item.getCallNumberPrefix())
      .put("callNumberSuffix", item.getCallNumberSuffix())
      .put("enumeration", item.getEnumeration())
      .put("volume", item.getVolume())
      .put("chronology", item.getChronology())
      .put("yearCaption", yearCaptionsToken)
      .put("materialType", item.getMaterialTypeName())
      .put("loanType", item.getLoanTypeName())
      .put("copy", captionsToken)
      .put("numberOfPieces", item.getNumberOfPieces())
      .put("descriptionOfPieces", item.getDescriptionOfPieces())
      .put("effectiveLocationSpecific", location.getName())
      .put("effectiveLocationLibrary", location.getLibraryName())
      .put("effectiveLocationCampus", location.getCampusName())
      .put("effectiveLocationInstitution", location.getInstitutionName());
  }

  private static JsonObject createRequestContext(Request request) {
    Optional<Request> optionalRequest = Optional.ofNullable(request);
    JsonObject requestContext = new JsonObject();

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
      .map(Request::getCancellationReasonName)
      .ifPresent(value -> requestContext.put("cancellationReason", value));

    return requestContext;
  }

  private static JsonObject createLoanContext(Loan loan) {
    return createLoanContext(loan, null, DateTimeZone.UTC);
  }

  private static JsonObject createLoanContext(
    Loan loan, LoanPolicy loanPolicy, DateTimeZone timeZone) {

    JsonObject loanContext = new JsonObject();
    loanContext.put("initialBorrowDate", loan.getLoanDate().withZone(timeZone).toString());
    loanContext.put("numberOfRenewalsTaken", loan.getRenewalCount());
    loanContext.put("dueDate", loan.getDueDate().withZone(timeZone).toString());

    if (loan.getReturnDate() != null) {
      loanContext.put("checkinDate", loan.getReturnDate().toString());
    }
    if (loanPolicy != null) {
      if (loanPolicy.unlimitedRenewals()) {
        loanContext.put("numberOfRenewalsAllowed", UNLIMITED);
        loanContext.put("numberOfRenewalsRemaining", UNLIMITED);
      } else {
        int renewalLimit = loanPolicy.getRenewalLimit();
        int renewalsRemaining = renewalLimit - loan.getRenewalCount();
        loanContext.put("numberOfRenewalsAllowed", renewalLimit);
        loanContext.put("numberOfRenewalsRemaining", renewalsRemaining);
      }
    }

    return loanContext;
  }
}
