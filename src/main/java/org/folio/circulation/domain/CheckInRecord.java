package org.folio.circulation.domain;

import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.util.UUID;

import org.apache.commons.lang3.ObjectUtils;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class CheckInRecord {
  private static final String ID = "id";
  private static final String OCCURRED_DATE_TIME = "occurredDateTime";
  private static final String ITEM_ID = "itemId";
  private static final String SERVICE_POINT_ID = "servicePointId";
  private static final String PERFORMED_BY_USER_ID = "performedByUserId";

  private final String id;
  private final DateTime occurredDateTime;
  private final String itemId;
  private final String servicePointId;
  private final String performedByUserId;

  private CheckInRecord(Builder builder) {
    this.id = builder.id;
    this.occurredDateTime = builder.occurredDateTime;
    this.itemId = builder.itemId;
    this.servicePointId = builder.checkInServicePointId;
    this.performedByUserId = builder.performedByUserId;
  }

  public static class Builder {
    private String id = UUID.randomUUID().toString();
    private DateTime occurredDateTime;
    private String itemId;
    private String checkInServicePointId;
    private String performedByUserId;

    public Builder withId(String id) {
      this.id = id;
      return this;
    }

    public Builder withOccurredDateTime(DateTime occurredDateTime) {
      this.occurredDateTime = occurredDateTime;
      return this;
    }

    public Builder withItemId(String itemId) {
      this.itemId = itemId;
      return this;
    }

    public Builder withServicePointId(String servicePointId) {
      this.checkInServicePointId = servicePointId;
      return this;
    }

    public Builder withPerformedByUserId(String performedByUserId) {
      this.performedByUserId = performedByUserId;
      return this;
    }

    public CheckInRecord build() {
      if (!ObjectUtils.allNotNull(occurredDateTime, itemId,
        checkInServicePointId)) {

        throw new IllegalStateException("OccurredDateTime, itemId and checkInServicePoint are required");
      }

      return new CheckInRecord(this);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject()
      .put(OCCURRED_DATE_TIME, occurredDateTime.toString())
      .put(ITEM_ID, itemId)
      .put(SERVICE_POINT_ID, servicePointId);

    write(json, ID, id);
    write(json, PERFORMED_BY_USER_ID, performedByUserId);

    return json;
  }
}
