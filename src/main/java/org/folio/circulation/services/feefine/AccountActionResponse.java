package org.folio.circulation.services.feefine;

import static org.folio.circulation.support.json.JsonObjectArrayPropertyFetcher.mapToList;

import java.util.List;

import org.folio.circulation.domain.FeeFineAction;
import org.folio.circulation.support.http.client.Response;

import io.vertx.core.json.JsonObject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class AccountActionResponse {
  private final String accountId;
  private final String amount;
  private final List<FeeFineAction> feeFineActions;

  public static AccountActionResponse from(Response response) {
    final JsonObject responseJson = response.getJson();

    return new AccountActionResponse(
      responseJson.getString("accountId"),
      responseJson.getString("amount"),
      mapToList(responseJson, "feefineactions", FeeFineAction::from));
  }
}
