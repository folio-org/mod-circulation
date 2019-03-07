package org.folio.circulation.domain;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AdjacentConfigurations {

  private static final String CONFIGS_KEY = "configs";
  private static final String TOTAL_RECORDS_KEY = "totalRecords";
  private static final int DEFAULT_TOTAL_RECORDS = 0;

  private int totalRecords;
  private List<TimeZoneConfig> timeZoneConfigs;

  AdjacentConfigurations(JsonObject jsonObject) {
    totalRecords = initTotalRecords(jsonObject);
    timeZoneConfigs = initTimeZoneConfigs(jsonObject.getJsonArray(CONFIGS_KEY));
  }

  private int initTotalRecords(JsonObject jsonObject) {
    Integer totalRecordsVal = jsonObject.getInteger(TOTAL_RECORDS_KEY);
    return Objects.isNull(totalRecordsVal)
      ? DEFAULT_TOTAL_RECORDS
      : totalRecordsVal;
  }

  private List<TimeZoneConfig> initTimeZoneConfigs(JsonArray jsonArray) {
    List<TimeZoneConfig> configs = new ArrayList<>();
    if (Objects.isNull(jsonArray) || jsonArray.isEmpty()) {
      return configs;
    }
    for (int i = 0; i < jsonArray.size(); i++) {
      configs.add(new TimeZoneConfig(jsonArray.getJsonObject(i)));
    }
    return configs;
  }

  public int getTotalRecords() {
    return totalRecords;
  }

  List<TimeZoneConfig> getTimeZoneConfigs() {
    return timeZoneConfigs;
  }
}
