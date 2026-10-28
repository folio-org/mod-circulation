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
import api.support.http.IndividualResource;
import io.vertx.core.json.JsonObject;

class CheckInsApiTests extends APITests {
  private static final String ID = "id";
  private static final String ITEM_ID = "itemId";
  private static final String SERVICE_POINT_ID = "servicePointId";
  private static final String SERVICE_POINT_NAME = "servicePointName";
  private static final String PERFORMED_BY_USER_ID = "performedByUserId";
  private static final String SOURCE = "source";
  private static final String OCCURRED_DATE_TIME = "occurredDateTime";
  private static final String IN_HOUSE_USE = "inHouseUse";
  private static final String OBJECT = "object";
  private static final String ACTION = "action";

  @Test
  void getByIdReturnsEnrichedCheckInForInHouseUse() {
    final var performedBy = usersFixture.jessica();
    final var servicePoint = servicePointsFixture.cd1();
    final var checkInId = UUID.randomUUID();
    final var itemId = UUID.randomUUID();

    checkInOperationClient.create(checkInRecord(checkInId, itemId, performedBy, servicePoint, true));

    final var fetchedCheckIn = checkInsClient.get(checkInId).getJson();

    assertThat(fetchedCheckIn.getString(ITEM_ID), is(itemId.toString()));
    assertThat(fetchedCheckIn.getString(PERFORMED_BY_USER_ID), is(performedBy.getId().toString()));
    assertThat(fetchedCheckIn.getString(SERVICE_POINT_ID), is(servicePoint.getId().toString()));
    assertThat(fetchedCheckIn.getBoolean(IN_HOUSE_USE), is(true));
    assertThat(fetchedCheckIn.getString(SOURCE), is("Pontefract, Jessica"));
    assertThat(fetchedCheckIn.getString(SERVICE_POINT_NAME), is("Circ Desk 1"));
    assertThat(fetchedCheckIn.getString(OBJECT), is("In-house use"));
    assertThat(fetchedCheckIn.getString(ACTION), is("Recorded"));
  }

  @Test
  void getByIdReturnsEnrichedCheckInForRegularCheckIn() {
    final var performedBy = usersFixture.steve();
    final var servicePoint = servicePointsFixture.cd2();
    final var checkInId = UUID.randomUUID();
    final var itemId = UUID.randomUUID();

    checkInOperationClient.create(checkInRecord(checkInId, itemId, performedBy, servicePoint, false));

    final var fetchedCheckIn = checkInsClient.get(checkInId).getJson();

    assertThat(fetchedCheckIn.getString(OBJECT), is("-"));
    assertThat(fetchedCheckIn.getString(ACTION), is("Checked in"));
    assertThat(fetchedCheckIn.getString(SOURCE), is("Jones, Steven"));
    assertThat(fetchedCheckIn.getString(SERVICE_POINT_NAME), is("Circ Desk 2"));
  }

  @Test
  void getByIdWithoutInHouseUseFieldIsClassifiedAsRegularCheckIn() {
    final var checkInId = UUID.randomUUID();
    final var checkIn = new JsonObject()
      .put(ID, checkInId.toString())
      .put(OCCURRED_DATE_TIME, ZonedDateTime.now(ZoneOffset.UTC).toString())
      .put(ITEM_ID, UUID.randomUUID().toString())
      .put(SERVICE_POINT_ID, UUID.randomUUID().toString())
      .put(PERFORMED_BY_USER_ID, UUID.randomUUID().toString());

    checkInOperationClient.create(checkIn);

    final var fetchedCheckIn = checkInsClient.get(checkInId).getJson();

    assertThat(fetchedCheckIn.getString(OBJECT), is("-"));
    assertThat(fetchedCheckIn.getString(ACTION), is("Checked in"));
  }

  @Test
  void getByIdForUnknownIdReturns404() {
    restAssuredClient.get(checkInsUrl("/" + randomId()), 404, "get-check-in");
  }

  @Test
  void getManyReturnsPaginatedEnrichedCollection() {
    final var jessica = usersFixture.jessica();
    final var steve = usersFixture.steve();
    final var cd1 = servicePointsFixture.cd1();
    final var cd2 = servicePointsFixture.cd2();

    final var firstItemId = UUID.randomUUID();
    final var secondItemId = UUID.randomUUID();

    checkInOperationClient.create(checkInRecord(UUID.randomUUID(), firstItemId, jessica, cd1, true));
    checkInOperationClient.create(checkInRecord(UUID.randomUUID(), secondItemId, steve, cd2, false));

    final var allCheckIns = checkInsClient.getMany(CqlQuery.noQuery());

    assertThat(allCheckIns.size(), is(2));
    assertThat(allCheckIns.totalRecords(), is(2));

    final var firstCheckIn = allCheckIns.stream()
      .filter(checkIn -> checkIn.getString(ITEM_ID).equals(firstItemId.toString()))
      .findFirst().orElseThrow();
    final var secondCheckIn = allCheckIns.stream()
      .filter(checkIn -> checkIn.getString(ITEM_ID).equals(secondItemId.toString()))
      .findFirst().orElseThrow();

    assertThat(firstCheckIn.getString(SOURCE), is("Pontefract, Jessica"));
    assertThat(firstCheckIn.getString(SERVICE_POINT_NAME), is("Circ Desk 1"));
    assertThat(firstCheckIn.getString(OBJECT), is("In-house use"));
    assertThat(firstCheckIn.getString(ACTION), is("Recorded"));

    assertThat(secondCheckIn.getString(SOURCE), is("Jones, Steven"));
    assertThat(secondCheckIn.getString(SERVICE_POINT_NAME), is("Circ Desk 2"));
    assertThat(secondCheckIn.getString(OBJECT), is("-"));
    assertThat(secondCheckIn.getString(ACTION), is("Checked in"));
  }

  @Test
  void getManyForwardsCqlQueryUnchanged() {
    final var performedBy = usersFixture.jessica();
    final var servicePoint = servicePointsFixture.cd1();
    final var matchingItemId = UUID.randomUUID();

    checkInOperationClient.create(
      checkInRecord(UUID.randomUUID(), matchingItemId, performedBy, servicePoint, false));
    checkInOperationClient.create(
      checkInRecord(UUID.randomUUID(), UUID.randomUUID(), performedBy, servicePoint, false));

    final var filteredCheckIns = checkInsClient.getMany(
      CqlQuery.exactMatch(ITEM_ID, matchingItemId.toString()));

    assertThat(filteredCheckIns.size(), is(1));
    assertThat(filteredCheckIns.getFirst().getString(ITEM_ID), is(matchingItemId.toString()));
    assertThat(filteredCheckIns.getFirst().getString(SOURCE), is("Pontefract, Jessica"));
  }

  private static JsonObject checkInRecord(UUID id, UUID itemId, IndividualResource performedBy,
    IndividualResource servicePoint, boolean inHouseUse) {

    return new JsonObject()
      .put(ID, id.toString())
      .put(OCCURRED_DATE_TIME, ZonedDateTime.now(ZoneOffset.UTC).toString())
      .put(ITEM_ID, itemId.toString())
      .put(SERVICE_POINT_ID, servicePoint.getId().toString())
      .put(PERFORMED_BY_USER_ID, performedBy.getId().toString())
      .put(IN_HOUSE_USE, inHouseUse);
  }
}
