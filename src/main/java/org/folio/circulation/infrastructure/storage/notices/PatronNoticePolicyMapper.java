package org.folio.circulation.infrastructure.storage.notices;

import static java.lang.String.format;
import static org.folio.circulation.support.json.JsonObjectArrayPropertyFetcher.mapToList;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedObjectProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.notice.NoticeConfiguration;
import org.folio.circulation.domain.notice.NoticeConfigurationBuilder;
import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.notice.NoticeFormat;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.notice.PatronNoticePolicy;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class PatronNoticePolicyMapper implements Function<JsonObject, Result<PatronNoticePolicy>> {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final String LOAN_NOTICES = "loanNotices";
  private static final String REQUEST_NOTICES = "requestNotices";
  private static final String FEE_FINE_NOTICES = "feeFineNotices";

  private static final String TEMPLATE_ID = "templateId";
  private static final String FORMAT = "format";
  private static final String SEND_OPTIONS = "sendOptions";
  private static final String SEND_WHEN = "sendWhen";
  private static final String SEND_HOW = "sendHow";
  private static final String SEND_BY = "sendBy";
  private static final String FREQUENCY = "frequency";
  private static final String SEND_EVERY = "sendEvery";
  private static final String REAL_TIME = "realTime";

  private static final String RECURRING_FREQUENCY = "Recurring";

  @Override
  public Result<PatronNoticePolicy> apply(JsonObject representation) {
    List<Result<NoticeConfiguration>> loanNoticeConfigurations =
      mapToList(representation, LOAN_NOTICES, this::toNoticeConfiguration);
    List<Result<NoticeConfiguration>> requestNoticeConfigurations =
      mapToList(representation, REQUEST_NOTICES, this::toNoticeConfiguration);
    List<Result<NoticeConfiguration>> feeFineNoticeConfiguration =
      mapToList(representation, FEE_FINE_NOTICES, this::toNoticeConfiguration);

    return Result.combineAll(loanNoticeConfigurations, requestNoticeConfigurations,
      feeFineNoticeConfiguration).map(PatronNoticePolicy::new);
  }

  private Result<NoticeConfiguration> toNoticeConfiguration(JsonObject representation) {
    return succeeded(new NoticeConfigurationBuilder())
      .combine(getTemplateId(representation), NoticeConfigurationBuilder::setTemplateId)
      .combine(getNoticeFormat(representation), NoticeConfigurationBuilder::setNoticeFormat)
      .map(b -> b.setNoticeEventType(getNoticeEventType(representation)))
      .map(b -> b.setTiming(getNoticeTiming(representation)))
      .next(b -> setTimingPeriod(b, representation))
      .map(b -> b.setRecurring(getRecurring(representation)))
      .next(b -> setRecurringTiming(b, representation))
      .map(b -> b.setSendInRealTime(getBooleanProperty(representation, REAL_TIME)))
      .map(NoticeConfigurationBuilder::build);
  }

  private Result<String> getTemplateId(JsonObject representation) {
    String templateId = representation.getString(TEMPLATE_ID);
    if (templateId == null) {
      return failed(getPolicyParsingFailure("templateId must not be null"));
    }
    return succeeded(templateId);
  }

  private Result<NoticeFormat> getNoticeFormat(JsonObject representation) {
    NoticeFormat noticeFormat = NoticeFormat.from(representation.getString(FORMAT));
    if (noticeFormat == NoticeFormat.UNKNOWN) {
      return failed(getPolicyParsingFailure("unexpected notice format"));
    }
    return succeeded(noticeFormat);
  }

  private NoticeEventType getNoticeEventType(JsonObject representation) {
    return NoticeEventType.from(
      getNestedStringProperty(representation, SEND_OPTIONS, SEND_WHEN));
  }

  private NoticeTiming getNoticeTiming(JsonObject representation) {
    return NoticeTiming.from(
      getNestedStringProperty(representation, SEND_OPTIONS, SEND_HOW));
  }

  private Result<NoticeConfigurationBuilder> setTimingPeriod(
    NoticeConfigurationBuilder builder, JsonObject representation) {

    if (getNoticeTiming(representation).requiresPeriod()) {
      log.debug("setTimingPeriod:: notice timing requires period");
      return getNestedPeriodProperty(representation, SEND_BY)
        .map(builder::setTimingPeriod);
    }
    log.debug("setTimingPeriod:: notice timing does not require period");
    return succeeded(builder);
  }

  private boolean getRecurring(JsonObject representation) {
    return Objects.equals(representation.getString(FREQUENCY), RECURRING_FREQUENCY);
  }

  private Result<NoticeConfigurationBuilder> setRecurringTiming(
    NoticeConfigurationBuilder builder, JsonObject representation) {

    if (getRecurring(representation)) {
      log.debug("setRecurringTiming:: notice is recurring");
      return getNestedPeriodProperty(representation, SEND_EVERY)
        .map(builder::setRecurringPeriod);
    }
    log.debug("setRecurringTiming:: notice is not recurring");
    return succeeded(builder);
  }

  private Result<Period> getNestedPeriodProperty(JsonObject representation, String propertyName) {
    return Period.from(getNestedObjectProperty(representation, SEND_OPTIONS, propertyName),
      () -> getPolicyParsingFailure("the loan period is not recognised"),
      interval -> getPolicyParsingFailure(format("the interval \"%s\" is not recognised", interval)),
      duration -> getPolicyParsingFailure(format("the duration \"%s\"  is invalid", duration)));
  }

  private HttpFailure getPolicyParsingFailure(String message) {
    return new ServerErrorFailure("Unable to parse patron notice policy: " + message);
  }
}
