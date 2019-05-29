package org.folio.circulation.domain.policy;

import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.server.ValidationError;
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

  Result<DateTime> addTo(
    DateTime from,
    Supplier<ValidationError> onUnrecognisedPeriod,
    Function<String, ValidationError> onUnrecognisedInterval,
    IntFunction<ValidationError> onUnrecognisedDuration) {

    if(interval == null) {
      return failedValidation(onUnrecognisedPeriod.get());
    }

    if(duration == null) {
      return  failedValidation(onUnrecognisedPeriod.get());
    }

    if(duration <= 0) {
      return failedValidation(onUnrecognisedDuration.apply(duration));
    }

    switch (interval) {
      case "Months":
        return succeeded(from.plusMonths(duration));
      case "Weeks":
        return succeeded(from.plusWeeks(duration));
      case "Days":
        return succeeded(from.plusDays(duration));
      case "Hours":
        return succeeded(from.plusHours(duration));
      case "Minutes":
        return succeeded(from.plusMinutes(duration));
      default:
        return failedValidation(onUnrecognisedInterval.apply(interval));
    }
  }

  public JsonObject asJson() {
    JsonObject representation = new JsonObject();

    representation.put("duration", duration);
    representation.put("intervalId", interval);

    return representation;
  }

  public org.joda.time.Period toTimePeriod() {
    switch (interval) {
      case "Months":
        return org.joda.time.Period.months(duration);
      case "Weeks":
        return org.joda.time.Period.weeks(duration);
      case "Days":
        return org.joda.time.Period.days(duration);
      case "Hours":
        return org.joda.time.Period.hours(duration);
      case "Minutes":
      default:
        return org.joda.time.Period.minutes(duration);
    }
  }
}
