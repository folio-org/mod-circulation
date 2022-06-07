package org.folio.circulation.resources.renewal;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.failures.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.results.Result.succeeded;

import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RenewByIdRequest {
  static final String USER_ID = "userId";
  private static final String ITEM_ID = "itemId";

  private final String itemId;
  private final String userId;

  public static Result<RenewByIdRequest> from(JsonObject json) {
    final String itemId = getProperty(json, ITEM_ID);

    if (isBlank(itemId)) {
      return failedValidation("Renewal request must have an item ID",
        ITEM_ID, null);
    }

    final String userId = getProperty(json, USER_ID);

    if (isBlank(userId)) {
      return failedValidation("Renewal request must have a user ID",
        USER_ID, null);
    }

    return succeeded(new RenewByIdRequest(itemId, userId));
  }
}
