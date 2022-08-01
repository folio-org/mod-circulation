package org.folio.circulation.domain;

import static java.time.ZoneOffset.UTC;
import static org.folio.circulation.support.json.JsonObjectArrayPropertyFetcher.mapToList;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getLocalDateProperty;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collector;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.folio.circulation.support.utils.DateTimeUtil;

@Getter
@ToString
@AllArgsConstructor
public class OpeningDay {

  private static final String DATE_KEY = "date";
  private static final String ALL_DAY_KEY = "allDay";
  private static final String OPEN_KEY = "open";
  private static final String OPENING_HOUR_KEY = "openingHour";

  private final List<OpeningHour> openingHour;
  private final LocalDate date;
  private final boolean allDay;
  private final boolean open;
  private final ZonedDateTime dayWithTimeZone;

  public OpeningDay(
    List<OpeningHour> openingHour,
    LocalDate date,
    boolean allDay,
    boolean open
  ) {
    this.openingHour = openingHour;
    this.date = date;
    this.allDay = allDay;
    this.open = open;
    this.dayWithTimeZone = null;
  }

  public OpeningDay(
    List<OpeningHour> openingHour,
    LocalDate date,
    boolean allDay,
    boolean open,
    ZoneId zone
  ) {
    this(
      openingHour,
      date,
      allDay,
      open,
      DateTimeUtil.atStartOfDay(date, zone)
    );
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
      DateTimeUtil.atStartOfDay(
        getLocalDateProperty(jsonObject, DATE_KEY),
        zone
      )
    );
  }

  public static OpeningDay createClosedDay() {
    return new OpeningDay(Collections.emptyList(), null, true, false);
  }

  /**
   * Convert the opening hours to JSON for re-packaging
   */
  private JsonArray openingHourToJsonArray() {
    return openingHour
      .stream()
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
    write(json, OPENING_HOUR_KEY, openingHourToJsonArray());

    return json;
  }

  /**
   * Create a list of {@link OpeningHour OpeningHour} from the provided daily opening object
   */
  private static List<OpeningHour> createOpeningTimes(
    JsonObject representation
  ) {
    return mapToList(representation, OPENING_HOUR_KEY, OpeningHour::new);
  }
}
