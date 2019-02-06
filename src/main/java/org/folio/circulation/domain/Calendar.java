package org.folio.circulation.domain;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.policy.LoanPolicyPeriod;

import java.util.ArrayList;
import java.util.List;

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

  private LoanPolicyPeriod period;
  private int duration;

  Calendar(JsonObject representation) {
    this.representation = representation;
    initFields();
  }

  Calendar(JsonObject representation, LoanPolicyPeriod period, int duration) {
    this.representation = representation;
    this.period = period;
    this.duration = duration;
    initFields();
  }

  Calendar() {
  }

  /**
   * init fields
   */
  private void initFields() {
    this.id = StringUtils.defaultIfBlank(representation.getString(ID_KEY), StringUtils.EMPTY);
    this.servicePointId = StringUtils.defaultIfBlank(representation.getString(SERVICE_POINT_ID_KEY), StringUtils.EMPTY);
    this.name = StringUtils.defaultIfBlank(representation.getString(NAME_KEY), StringUtils.EMPTY);
    this.startDate = StringUtils.defaultIfBlank(representation.getString(START_DATE_KEY), StringUtils.EMPTY);
    this.endDate = StringUtils.defaultIfBlank(representation.getString(END_DATE_KEY), StringUtils.EMPTY);
    this.openingDays = fillOpeningDayPeriod();
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

  private List<OpeningDayPeriod> fillOpeningDayPeriod() {
    List<OpeningDayPeriod> dayPeriods = new ArrayList<>();
    JsonArray openingDaysJson = representation.getJsonArray(OPENING_DAYS_KEY);
    if (openingDaysJson != null) {
      for (int i = 0; i < openingDaysJson.size(); i++) {
        JsonObject jsonObject = openingDaysJson.getJsonObject(i);
        dayPeriods.add(new OpeningDayPeriod(jsonObject));
      }
    }
    return dayPeriods;
  }
}
