package org.folio.circulation.domain;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.policy.LoanPolicyPeriod;

import java.util.ArrayList;
import java.util.List;

import static org.folio.circulation.support.CalendarQueryUtil.getField;

public class Calendar {

  private static final String ID_KEY = "id";
  private static final String SERVICE_POINT_ID_KEY = "servicePointId";
  private static final String NAME_KEY = "name";
  private static final String START_DATE_KEY = "startDate";
  private static final String END_DATE_KEY = "endDate";
  private static final String OPENING_DAYS_KEY = "openingDays";

  private JsonObject representation;

  private String id;
  private String servicePointId;
  private String name;
  private String startDate;
  private String endDate;
  private List<OpeningDayPeriod> openingDays;

  // additional field
  private LoanPolicyPeriod period;
  private int duration;

  Calendar(JsonObject representation, LoanPolicyPeriod period, int duration) {
    this.representation = representation;
    this.period = period;
    this.duration = duration;
    initFields();
  }

  Calendar(JsonObject representation) {
    this.representation = representation;
    initFields();
  }

  Calendar() {
  }

  public LoanPolicyPeriod getPeriod() {
    return period;
  }

  public int getDuration() {
    return duration;
  }

  public JsonObject getRepresentation() {
    return representation;
  }

  public String getId() {
    return id;
  }

  public String getServicePointId() {
    return servicePointId;
  }

  public String getName() {
    return name;
  }

  public String getStartDate() {
    return startDate;
  }

  public String getEndDate() {
    return endDate;
  }

  public List<OpeningDayPeriod> getOpeningDays() {
    return openingDays;
  }

  /**
   * init fields
   */
  private void initFields() {
    this.id = getField(representation.getString(ID_KEY));
    this.servicePointId = getField(representation.getString(SERVICE_POINT_ID_KEY));
    this.name = getField(representation.getString(NAME_KEY));
    this.startDate = getField(representation.getString(START_DATE_KEY));
    this.endDate = getField(representation.getString(END_DATE_KEY));
    this.openingDays = fillOpeningDayPeriod();
  }

  private List<OpeningDayPeriod> fillOpeningDayPeriod() {
    List<OpeningDayPeriod> dayPeriods = new ArrayList<>();
    JsonArray openingDaysJson = representation.getJsonArray(OPENING_DAYS_KEY);
    for (int i = 0; i < openingDaysJson.size(); i++) {
      JsonObject jsonObject = openingDaysJson.getJsonObject(i);
      dayPeriods.add(new OpeningDayPeriod(jsonObject));
    }
    return dayPeriods;
  }
}
