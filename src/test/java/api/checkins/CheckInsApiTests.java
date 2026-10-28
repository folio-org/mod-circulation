package api.checkins;

import static api.support.http.InterfaceUrls.checkInsUrl;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.http.CqlQuery;
import io.vertx.core.json.JsonObject;

class CheckInsApiTests extends APITests {
  private static final String ID = "id";
  private static final String ITEM_ID = "itemId";
  private static final String SERVICE_POINT_ID = "servicePointId";
  private static final String PERFORMED_BY_USER_ID = "performedByUserId";
  private static final String OCCURRED_DATE_TIME = "occurredDateTime";
  private static final String IN_HOUSE_USE = "inHouseUse";

  @Test
  void getByIdReturnsCheckInRecord() {
    final var checkInId = UUID.randomUUID();
    final var itemId = UUID.randomUUID();

    checkInOperationClient.create(checkInRecord(checkInId, itemId, true));

    final var fetchedCheckIn = checkInsClient.get(checkInId);

    assertThat(fetchedCheckIn.getJson().getString(ITEM_ID), is(itemId.toString()));
    assertThat(fetchedCheckIn.getJson().getBoolean(IN_HOUSE_USE), is(true));
  }

  @Test
  void getByIdForUnknownIdReturns404() {
    restAssuredClient.get(checkInsUrl("/" + randomId()), 404, "get-check-in");
  }

  @Test
  void getManyReturnsPaginatedCollection() {
    checkInOperationClient.create(checkInRecord(UUID.randomUUID(), UUID.randomUUID(), false));
    checkInOperationClient.create(checkInRecord(UUID.randomUUID(), UUID.randomUUID(), false));

    final var allCheckIns = checkInsClient.getMany(CqlQuery.noQuery());

    assertThat(allCheckIns.size(), is(2));
    assertThat(allCheckIns.totalRecords(), is(2));
  }

  @Test
  void getManyForwardsCqlQueryUnchanged() {
    final var matchingItemId = UUID.randomUUID();

    checkInOperationClient.create(checkInRecord(UUID.randomUUID(), matchingItemId, false));
    checkInOperationClient.create(checkInRecord(UUID.randomUUID(), UUID.randomUUID(), false));

    final var filteredCheckIns = checkInsClient.getMany(
      CqlQuery.exactMatch(ITEM_ID, matchingItemId.toString()));

    assertThat(filteredCheckIns.size(), is(1));
    assertThat(filteredCheckIns.getFirst().getString(ITEM_ID), is(matchingItemId.toString()));
  }

  private static JsonObject checkInRecord(UUID id, UUID itemId, boolean inHouseUse) {
    return new JsonObject()
      .put(ID, id.toString())
      .put(OCCURRED_DATE_TIME, ZonedDateTime.now(ZoneOffset.UTC).toString())
      .put(ITEM_ID, itemId.toString())
      .put(SERVICE_POINT_ID, UUID.randomUUID().toString())
      .put(PERFORMED_BY_USER_ID, UUID.randomUUID().toString())
      .put(IN_HOUSE_USE, inHouseUse);
  }
}
