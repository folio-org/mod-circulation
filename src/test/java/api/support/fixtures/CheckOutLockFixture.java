package api.support.fixtures;

import api.support.http.IndividualResource;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

import java.util.UUID;

import static org.folio.circulation.support.json.JsonPropertyWriter.write;

public class CheckOutLockFixture {

  private final ResourceClient checkOutLockClient;

  public CheckOutLockFixture() {
    this.checkOutLockClient = ResourceClient.forCheckoutLockStorage();
  }

  public IndividualResource createLockForUserId(String userId) {
    final JsonObject checkOutLock = new JsonObject();

    write(checkOutLock, "userId", userId);
    write(checkOutLock, "ttl", 3000);

    return checkOutLockClient.create(checkOutLock);
  }

  public void deleteLock(UUID id) {
    checkOutLockClient.delete(id);
  }

}
