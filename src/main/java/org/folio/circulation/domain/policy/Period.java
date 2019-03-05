package org.folio.circulation.domain.policy;

import static org.folio.circulation.support.HttpResult.failed;

import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class Period {
  private final Integer duration;
  private final String interval;

  private Period(Integer duration, String interval) {
    this.duration = duration;
    this.interval = interval;
  }

  public static Period months(int duration) {
    return from(duration, "Months");
  }

  public static Period weeks(Integer duration) {
    return from(duration, "Weeks");
  }

  public static Period days(Integer duration) {
    return from(duration, "Days");
  }

  public static Period hours(int duration) {
    return from(duration, "Hours");
  }

  static Period minutes(int duration) {
    return from(duration, "Minutes");
  }

  public static Period from(Integer duration, String interval) {
    return new Period(duration, interval);
  }

  //TODO: Change this to use ValidationError instead of ValidationErrorFailure
  HttpResult<DateTime> addTo(
    DateTime from,
    Supplier<ValidationErrorFailure> onUnrecognisedPeriod,
    Function<String, ValidationErrorFailure> onUnrecognisedInterval,
    IntFunction<ValidationErrorFailure> onUnrecognisedDuration) {

    if (interval == null) {
      return failed(onUnrecognisedPeriod.get());
    }

    if (duration == null) {
      return failed(onUnrecognisedPeriod.get());
    }

    if (duration <= 0) {
      return failed(onUnrecognisedDuration.apply(duration));
    }

    switch (interval) {
      case "Months":
        return HttpResult.succeeded(from.plusMonths(duration));
      case "Weeks":
        return HttpResult.succeeded(from.plusWeeks(duration));
      case "Days":
        return HttpResult.succeeded(from.plusDays(duration));
      case "Hours":
        return HttpResult.succeeded(from.plusHours(duration));
      case "Minutes":
        return HttpResult.succeeded(from.plusMinutes(duration));
      default:
        return failed(onUnrecognisedInterval.apply(interval));
    }
  }

  public JsonObject asJson() {
    JsonObject representation = new JsonObject();

    representation.put("duration", duration);
    representation.put("intervalId", interval);

    return representation;
  }
}
