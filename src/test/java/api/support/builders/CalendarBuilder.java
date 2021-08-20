package api.support.builders;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import api.support.OpeningDayPeriod;

import org.joda.time.DateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collector;

public class CalendarBuilder extends JsonBuilder implements Builder {

  private static final String CALENDAR_NAME = "Calendar Name";
  private static final String START_DATE = DateTime.now().minusMonths(1).toString();
  private static final String END_DATE = DateTime.now().plusMonths(6).toString();

  private static final String ID_KEY = "id";
  private static final String SERVICE_POINT_ID_KEY = "servicePointId";
  private static final String NAME_KEY = "name";
  public static final String START_DATE_KEY = "startDate";
  private static final String END_DATE_KEY = "endDate";
  private static final String OPENING_DAYS_KEY = "openingDays";

  private JsonObject representation;

  public CalendarBuilder(OpeningDayPeriodBuilder periodBuilder) {
    this.representation = new JsonObject()
      .put(ID_KEY, UUID.randomUUID().toString())
      .put(SERVICE_POINT_ID_KEY, periodBuilder.getServiceId())
      .put(NAME_KEY, CALENDAR_NAME)
      .put(START_DATE_KEY, START_DATE)
      .put(END_DATE_KEY, END_DATE)
      .put(OPENING_DAYS_KEY, openingDaysToJsonArray(periodBuilder.getOpeningDays()));
  }

  public CalendarBuilder(String servicePointId, String name) {
    this.representation = new JsonObject()
      .put(ID_KEY, UUID.randomUUID().toString())
      .put(SERVICE_POINT_ID_KEY, servicePointId)
      .put(NAME_KEY, name)
      .put(START_DATE_KEY, START_DATE)
      .put(END_DATE_KEY, END_DATE)
      .put(OPENING_DAYS_KEY, new JsonArray());
  }

  public CalendarBuilder(String servicePointId, DateTime startDate, DateTime endDate) {
    this.representation = new JsonObject()
      .put(ID_KEY, UUID.randomUUID().toString())
      .put(SERVICE_POINT_ID_KEY, servicePointId)
      .put(NAME_KEY, "CASE_CLOSED_LIBRARY")
      .put(START_DATE_KEY, startDate.toString())
      .put(END_DATE_KEY, endDate.toString())
      .put(OPENING_DAYS_KEY, new JsonArray());
  }

  private JsonArray openingDaysToJsonArray(List<OpeningDayPeriod> openingDays) {
    return openingDays.stream()
      .map(OpeningDayPeriod::toJson)
      .collect(Collector.of(JsonArray::new, JsonArray::add, JsonArray::add));
  }

  @Override
  public JsonObject create() {
    return this.representation;
  }

  @Override
  public String toString() {
    return this.representation.toString();
  }
}
