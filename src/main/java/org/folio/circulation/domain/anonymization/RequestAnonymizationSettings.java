package org.folio.circulation.domain.anonymization;

import static java.util.Locale.ROOT;

import io.vertx.core.json.JsonObject;

public class RequestAnonymizationSettings {

  public static final String MODE_KEY = "mode";
  public static final String DELAY_KEY = "delay";
  public static final String DELAY_UNIT_KEY = "delayUnit";

  public enum Mode {
    NEVER,
    IMMEDIATELY,
    DELAYED;

    public static Mode from(String value) {
      if (value == null) {
        return NEVER;
      }

      switch (value.toLowerCase(ROOT)) {
        case "immediately":
          return IMMEDIATELY;
        case "delayed":
          return DELAYED;
        case "never":
        default:
          return NEVER;
      }
    }

    public String toStorageValue() {
      switch (this) {
        case IMMEDIATELY:
          return "immediately";
        case DELAYED:
          return "delayed";
        case NEVER:
        default:
          return "never";
      }
    }
  }

  public enum DelayUnit {
    MINUTES,
    HOURS,
    DAYS,
    MONTHS,
    YEARS;

    public static DelayUnit from(String value) {
      if (value == null) {
        return HOURS;
      }

      switch (value.toLowerCase(ROOT)) {
        case "minute":
        case "minutes":
        case "minute(s)":
          return MINUTES;
        case "day":
        case "days":
        case "day(s)":
          return DAYS;
        case "month":
        case "months":
        case "month(s)":
          return MONTHS;
        case "year":
        case "years":
        case "year(s)":
          return YEARS;
        case "hour":
        case "hours":
        case "hour(s)":
        default:
          return HOURS;
      }
    }

    public String toStorageValue() {
      switch (this) {
        case MINUTES:
          return "minutes";
        case DAYS:
          return "days";
        case MONTHS:
          return "months";
        case YEARS:
          return "years";
        case HOURS:
        default:
          return "hours";
      }
    }
  }

  private final Mode mode;
  private final int delay;
  private final DelayUnit delayUnit;

  public RequestAnonymizationSettings(Mode mode, int delay, DelayUnit delayUnit) {
    this.mode = mode == null ? Mode.NEVER : mode;
    this.delay = Math.max(0, delay);
    this.delayUnit = delayUnit == null ? DelayUnit.HOURS : delayUnit;
  }

  public Mode getMode() {
    return mode;
  }

  public int getDelay() {
    return delay;
  }

  public DelayUnit getDelayUnit() {
    return delayUnit;
  }

  public boolean isNever() {
    return mode == Mode.NEVER;
  }

  public boolean isImmediately() {
    return mode == Mode.IMMEDIATELY;
  }

  public boolean isDelayed() {
    return mode == Mode.DELAYED;
  }

  public static RequestAnonymizationSettings from(JsonObject json) {
    if (json == null) {
      return defaultSettings();
    }

    Mode mode = Mode.from(json.getString(MODE_KEY));
    int delay = json.getInteger(DELAY_KEY, 0);
    DelayUnit unit = DelayUnit.from(json.getString(DELAY_UNIT_KEY));

    return new RequestAnonymizationSettings(mode, delay, unit);
  }

  public JsonObject toJson() {
    return new JsonObject()
      .put(MODE_KEY, mode.toStorageValue())
      .put(DELAY_KEY, delay)
      .put(DELAY_UNIT_KEY, delayUnit.toStorageValue());
  }

  public static RequestAnonymizationSettings defaultSettings() {
    return new RequestAnonymizationSettings(Mode.NEVER, 0, DelayUnit.HOURS);
  }

  @Override
  public String toString() {
    return "RequestAnonymizationSettings{" + "mode=" + mode + ", delay=" + delay + ", delayUnit=" + delayUnit + '}';
  }
}
