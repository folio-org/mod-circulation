package org.folio.circulation.domain;

import static java.time.ZoneOffset.UTC;
import static org.folio.circulation.support.json.JsonObjectArrayPropertyFetcher.mapToList;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getLocalDateProperty;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collector;

import org.folio.circulation.support.utils.DateTimeUtil;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@Getter
@ToString
@AllArgsConstructor
public class OpeningDay {

  private static final String DATE_KEY = "date";
  private static final String ALL_DAY_KEY = "allDay";
  private static final String OPEN_KEY = "open";
  private static final String OPENINGS_KEY = "openings";

  private final List<OpeningHour> openings;
  private final LocalDate date;
  private final boolean allDay;
  private final boolean open;
  private final ZonedDateTime dayWithTimeZone;

  public OpeningDay(List<OpeningHour> openings, LocalDate date, boolean allDay, boolean open) {
    this.openings = openings;
    this.date = date;
    this.allDay = allDay;
    this.open = open;
    this.dayWithTimeZone = null;
  }

  public OpeningDay(List<OpeningHour> openings, LocalDate date, boolean allDay,
    boolean open, ZoneId zone) {
    this(openings, date, allDay, open, DateTimeUtil.atStartOfDay(date, zone));
  }

  /**
   * Construct an OpeningDay from the provided JSON object
   */
  public OpeningDay(JsonObject jsonObject) {
    this(
      createOpeningTimes(jsonObject),
      getLocalDateProperty(jsonObject, DATE_KEY),
      getBooleanProperty(jsonObject, ALL_DAY_KEY),
      getBooleanProperty(jsonObject, OPEN_KEY)
    );
  }

  /**
   * Construct an OpeningDay from the provided JSON object, with zoned date
   */
  public OpeningDay(JsonObject jsonObject, ZoneId zone) {
    this(
      createOpeningTimes(jsonObject),
      getLocalDateProperty(jsonObject, DATE_KEY),
      getBooleanProperty(jsonObject, ALL_DAY_KEY),
      getBooleanProperty(jsonObject, OPEN_KEY),
      DateTimeUtil.atStartOfDay(getLocalDateProperty(jsonObject, DATE_KEY), zone)
    );
  }

  public static OpeningDay createClosedDay() {
    return new OpeningDay(Collections.emptyList(), null, true, false);
  }

  /**
   * Convert the opening hours to JSON for re-packaging
   */
  private JsonArray openingHourToJsonArray() {
    return openings.stream()
      .map(OpeningHour::toJson)
      .collect(Collector.of(JsonArray::new, JsonArray::add, JsonArray::add));
  }

  /**
   * Convert the opening information to JSON
   */
  public JsonObject toJson() {
    final var json = new JsonObject();

    write(json, DATE_KEY, DateTimeUtil.atStartOfDay(date, UTC));
    write(json, ALL_DAY_KEY, allDay);
    write(json, OPEN_KEY, open);
    write(json, OPENINGS_KEY, openingHourToJsonArray());

    return json;
  }

  /**
   * Create a list of {@link OpeningHour OpeningHour} from the provided daily opening object
   */
  private static List<OpeningHour> createOpeningTimes(JsonObject representation) {
    return mapToList(representation, OPENINGS_KEY, OpeningHour::new);
  }
}
