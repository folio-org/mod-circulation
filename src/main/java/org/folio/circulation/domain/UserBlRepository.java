package org.folio.circulation.domain;

import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.Result;

import io.vertx.core.json.JsonObject;

public class UserBlRepository {

  private final CollectionResourceClient userBlClient;

  public UserBlRepository(Clients clients) {
    userBlClient = clients.usersBlClient();
  }

  public CompletableFuture<Result<String>> getLoggedInUser() {
    return FetchSingleRecord.<String>forRecord("user")
      .using(userBlClient)
      .mapTo(this::userIdMapper)
      .fetch("_self");
  }

  private String userIdMapper(JsonObject json) {
    if (json.isEmpty()) {
      return StringUtils.EMPTY;
    }

    return json.getJsonObject("user").getString("id");
  }
}
