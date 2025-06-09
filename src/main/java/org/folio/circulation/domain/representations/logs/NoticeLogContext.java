package org.folio.circulation.domain.representations.logs;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.ACCOUNT_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.DATE;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.ERROR_MESSAGE;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.ITEMS;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.REQ_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.USER_BARCODE;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.USER_ID;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.notice.schedule.ScheduledNotice;
import org.folio.circulation.domain.notice.session.PatronSessionRecord;
import org.folio.circulation.support.utils.LogUtil;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.With;


@AllArgsConstructor
@NoArgsConstructor
@With
public class NoticeLogContext {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private String userBarcode;
  private String userId;
  @Getter private List<NoticeLogContextItem> items = new ArrayList<>();
  private ZonedDateTime date;
  private String requestId;
  private String accountId;
  private String errorMessage;

  public static NoticeLogContext from(Loan loan) {
    log.debug("from:: parameters loan: {}", loan);

    return new NoticeLogContext()
      .withUser(loan.getUser())
      .withUserId(loan.getUserId())
      .withItems(singletonList(NoticeLogContextItem.from(loan)));
  }

  public static NoticeLogContext from(Request request) {
    log.debug("from:: parameters request: {}", request);

    return new NoticeLogContext()
      .withUserId(request.getUserId())
      .withUser(request.getRequester())
      .withItems(singletonList(NoticeLogContextItem.from(request)))
      .withRequestId(request.getId());
  }

  public static NoticeLogContext from(ScheduledNotice scheduledNotice) {
    log.debug("from:: parameters scheduledNotice: {}", scheduledNotice);

    return new NoticeLogContext()
      .withUserId(scheduledNotice.getRecipientUserId())
      .withRequestId(scheduledNotice.getRequestId())
      .withItems(singletonList(
        new NoticeLogContextItem()
          .withLoanId(scheduledNotice.getLoanId())
          .withTriggeringEvent(scheduledNotice.getTriggeringEvent().getRepresentation())
          .withTemplateId(scheduledNotice.getConfiguration().getTemplateId())
      ));
  }

  // it is assumed that all sessions have same user and action type
  public static NoticeLogContext from(List<PatronSessionRecord> sessions) {
    log.debug("from:: parameters sessions: {}", () -> LogUtil.asJson(sessions));

    if (sessions.isEmpty()) {
      log.info("from:: sessions list is empty");
      return new NoticeLogContext();
    }

    return new NoticeLogContext()
      .withUserId(sessions.getFirst().getPatronId().toString())
      .withItems(
        sessions.stream()
          .map(NoticeLogContextItem::from)
          .toList()
      );
  }

  public NoticeLogContext withUser(User user) {
    log.debug("withUser:: parameters user: {}", user);

    if (user != null) {
      log.info("from:: user is not null");
      return withUserBarcode(user.getBarcode())
        .withUserId(user.getId());
    }

    return this;
  }

  public NoticeLogContext withNoticePolicyId(String noticePolicyId) {
    log.debug("withNoticePolicyId:: parameters noticePolicyId: {}", noticePolicyId);

    return withItems(items.stream()
      .map(item -> item.withNoticePolicyId(noticePolicyId))
      .toList());
  }

  public NoticeLogContext withTemplateId(String templateId) {
    log.debug("withTemplateId:: parameters templateId: {}", templateId);

    return withItems(items.stream()
      .map(item -> item.withTemplateId(templateId))
      .toList());
  }

  public NoticeLogContext withTriggeringEvent(String triggeringEvent) {
    log.debug("withTriggeringEvent:: parameters triggeringEvent: {}", triggeringEvent);

    return withItems(items.stream()
      .map(item -> item.withTriggeringEvent(triggeringEvent))
      .toList());
  }

  public JsonObject asJson() {
    log.debug("asJson:: ");

    JsonObject json = new JsonObject();
    write(json, USER_ID.value(), userId);
    write(json, USER_BARCODE.value(), userBarcode);
    JsonArray itemsArray = items.stream()
      .map(NoticeLogContextItem::asJson)
      .collect(collectingAndThen(toList(), JsonArray::new));
    write(json, ITEMS.value(), itemsArray);
    write(json, DATE.value(), date);
    write(json, REQ_ID.value(), requestId);
    write(json, ACCOUNT_ID.value(), accountId);
    write(json, ERROR_MESSAGE.value(), errorMessage);
    return json;
  }
}
