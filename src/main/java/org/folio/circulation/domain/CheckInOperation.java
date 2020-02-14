package org.folio.circulation.domain;

import java.util.UUID;

import org.apache.commons.lang3.ObjectUtils;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class CheckInOperation {
  private static final String ID = "id";
  private static final String OCCURRED_DATE_TIME = "occurredDateTime";
  private static final String ITEM_ID = "itemId";
  private static final String CHECK_IN_SERVICE_POINT_ID = "checkInServicePointId";
  private static final String PERFORMED_BY_USER_ID = "performedByUserId";

  private final String id;
  private final DateTime occurredDateTime;
  private final String itemId;
  private final String checkInServicePointId;
  private final String performedByUserId;

  private CheckInOperation(Builder builder) {
    this.id = builder.id;
    this.occurredDateTime = builder.occurredDateTime;
    this.itemId = builder.itemId;
    this.checkInServicePointId = builder.checkInServicePointId;
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

    public Builder withCheckInServicePointId(String checkInServicePointId) {
      this.checkInServicePointId = checkInServicePointId;
      return this;
    }

    public Builder withPerformedByUserId(String performedByUserId) {
      this.performedByUserId = performedByUserId;
      return this;
    }

    public CheckInOperation build() {
      if (!ObjectUtils.allNotNull(id, occurredDateTime, itemId,
        checkInServicePointId, performedByUserId)) {

        throw new IllegalStateException("All properties are required");
      }

      return new CheckInOperation(this);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public JsonObject toJson() {
    return new JsonObject()
      .put(ID, id)
      .put(OCCURRED_DATE_TIME, occurredDateTime.toString())
      .put(ITEM_ID, itemId)
      .put(CHECK_IN_SERVICE_POINT_ID, checkInServicePointId)
      .put(PERFORMED_BY_USER_ID, performedByUserId);
  }

  public static CheckInOperation from(JsonObject json) {
    return builder()
      .withId(json.getString(ID))
      .withOccurredDateTime(DateTime.parse(json.getString(OCCURRED_DATE_TIME)))
      .withItemId(json.getString(ITEM_ID))
      .withCheckInServicePointId(json.getString(CHECK_IN_SERVICE_POINT_ID))
      .withPerformedByUserId(json.getString(PERFORMED_BY_USER_ID))
      .build();
  }
}
