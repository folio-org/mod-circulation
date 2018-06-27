package org.folio.circulation.resources;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.HttpResult;
import org.junit.Test;

import java.util.UUID;

import static api.support.matchers.FailureMatchers.errorResultFor;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class RenewByIdRequestTests {
  @Test
  public void propertiesAreReadFromJson() {
    final UUID itemId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();

    final HttpResult<RenewByIdRequest> request = RenewByIdRequest.from(
      new JsonObject()
        .put("userId", userId.toString())
        .put("itemId", itemId.toString()));

    assertThat(request.succeeded(), is(true));
    assertThat(request.value().getItemId(), is(itemId.toString()));
    assertThat(request.value().getUserId(), is(userId.toString()));
  }

  @Test
  public void failWhenNoItemBarcode() {
    final HttpResult<RenewByIdRequest> result = RenewByIdRequest.from(
      new JsonObject()
        .put("userId", UUID.randomUUID().toString()));

    assertThat(result, errorResultFor("itemId",
      "Renewal request must have an item ID"));
  }

  @Test
  public void failWhenNoUserBarcode() {
    final HttpResult<RenewByIdRequest> result = RenewByIdRequest.from(
      new JsonObject()
        .put("itemId", UUID.randomUUID().toString()));

    assertThat(result, errorResultFor("userId",
      "Renewal request must have a user ID"));
  }
}
