package org.folio.circulation.domain.notice;

import static java.lang.Math.max;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTime;
import static org.folio.circulation.support.utils.FeeFineActionHelper.getPatronInfoFromComment;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.FeeFineAction;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.mapper.InventoryMapper;
import org.folio.circulation.domain.mapper.RequestMapper;
import org.folio.circulation.domain.mapper.StaffSlipMapper;
import org.folio.circulation.domain.mapper.UserMapper;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.support.utils.ClockUtil;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class TemplateContextUtil {

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final String USER = "user";
  private static final String ITEM = "item";
  private static final String REQUEST = "request";
  private static final String LOAN = "loan";
  private static final String FEE_CHARGE = "feeCharge";
  private static final String FEE_ACTION = "feeAction";
  private static final String UNLIMITED = "unlimited";
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
      .put(ITEM, InventoryMapper.createItemContext(loan.getItem()))
      .put(LOAN, createLoanContext(loan));
  }

  public static JsonObject createGroupedNoticeContext(User user, String groupToken,
    Collection<JsonObject> noticeContexts) {

    return new JsonObject()
      .put(USER, UserMapper.createUserContext(user))
      .put(groupToken, new JsonArray(new ArrayList<>(noticeContexts)));
  }

  public static JsonObject createLoanNoticeContext(Loan loan) {
    return new JsonObject()
      .put(USER, UserMapper.createUserContext(loan.getUser()))
      .put(ITEM, InventoryMapper.createItemContext(loan.getItem()))
      .put(LOAN, createLoanContext(loan));
  }

  public static JsonObject createRequestNoticeContext(Request request) {
    JsonObject requestNoticeContext = new JsonObject()
      .put(USER, UserMapper.createUserContext(request.getRequester()))
      .put(REQUEST, RequestMapper.createRequestContext(request))
      .put(ITEM, createItemContext(request));

    if (request.hasLoan()) {
      requestNoticeContext.put(LOAN, createLoanContext(request.getLoan()));
    }
    return requestNoticeContext;
  }

  private static JsonObject createItemContext(Request request) {
    return request.hasItem()
      ? InventoryMapper.createItemContext(request.getItem())
      : InventoryMapper.createInstanceContext(request.getInstance(), request.getItem());
  }

  public static JsonObject createCheckInContext(CheckInContext context) {
    Item item = context.getItem();
    Request firstRequest = context.getHighestPriorityFulfillableRequest();
    JsonObject staffSlipContext = StaffSlipMapper.createStaffSlipContext(firstRequest, item);
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
}
