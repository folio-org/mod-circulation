package api.requests;

import static api.support.http.InterfaceUrls.requestsUrl;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.joda.time.DateTime.now;
import static org.joda.time.DateTimeZone.UTC;
import static org.joda.time.format.ISODateTimeFormat.dateTime;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import io.vertx.core.json.JsonObject;

public class InstanceRequestsAPICreationTests extends APITests {
  @Test
  public void canCreateATitleLevelRequestForMultipleAvailableItemsAndAMatchingPickupLocationId() {
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    UUID requesterId = usersFixture.jessica().getId();
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, UTC);
    DateTime requestExpirationDate = requestDate.plusDays(30);

    IndividualResource instanceMultipleCopies = instancesFixture.basedUponDunkirk();
    IndividualResource holdings = holdingsFixture.defaultWithHoldings(
      instanceMultipleCopies.getId());

    IndividualResource locationsResource = locationsFixture.mainFloor();

    itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(),
      null);

    final IndividualResource item2 =
      itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(),
        locationsResource.getId());

    itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(),
      null);

    JsonObject requestBody = createInstanceRequestObject(
      instanceMultipleCopies.getId(), requesterId, pickupServicePointId,
      requestDate, requestExpirationDate);

    Response postResponse = attemptToCreateInstanceRequest(requestBody);

    JsonObject representation = postResponse.getJson();
    validateInstanceRequestResponse(representation, pickupServicePointId,
      instanceMultipleCopies.getId(), item2.getId(), RequestType.PAGE);
  }

  @Test
  public void canCreateATitleLevelRequestForOneAvailableCopy() {
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    UUID requesterId = usersFixture.jessica().getId();
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, UTC);
    DateTime requestExpirationDate = requestDate.plusDays(30);

    IndividualResource instance = instancesFixture.basedUponDunkirk();
    IndividualResource holdings = holdingsFixture.defaultWithHoldings(
      instance.getId());

    IndividualResource locationsResource = locationsFixture.mainFloor();

    final IndividualResource item =
      itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(),
        locationsResource.getId());

    JsonObject requestBody = createInstanceRequestObject(instance.getId(),
      requesterId, pickupServicePointId, requestDate, requestExpirationDate);

    Response postResponse = attemptToCreateInstanceRequest(requestBody);

    assertThat(postResponse, hasStatus(HTTP_CREATED));

    JsonObject representation = postResponse.getJson();

    validateInstanceRequestResponse(representation, pickupServicePointId,
      instance.getId(), item.getId(), RequestType.PAGE);
  }

  @Test
  public void canCreateATitleLevelRequestForOneAvailableCopyWithoutRequestExpirationDate() {
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    UUID requesterId = usersFixture.jessica().getId();
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, UTC);

    IndividualResource instance = instancesFixture.basedUponDunkirk();
    IndividualResource holdings = holdingsFixture.defaultWithHoldings(
      instance.getId());

    IndividualResource locationsResource = locationsFixture.mainFloor();

    final IndividualResource item =
      itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(),
        locationsResource.getId());

    JsonObject requestBody = createInstanceRequestObject(instance.getId(),
      requesterId, pickupServicePointId, requestDate, null);

    Response postResponse = attemptToCreateInstanceRequest(requestBody);

    assertThat(postResponse, hasStatus(HTTP_CREATED));

    JsonObject representation = postResponse.getJson();
    validateInstanceRequestResponse(representation,
      pickupServicePointId,
      instance.getId(),
      item.getId(),
      RequestType.PAGE);
    assertNull(representation.getString("requestExpirationDate"));
  }

  @Test
  public void canCreateATitleLevelRequestForMultipleAvailableItemsWithNoMatchingPickupLocationIds() {
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    UUID requesterId = usersFixture.jessica().getId();
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, UTC);
    DateTime requestExpirationDate = requestDate.plusDays(30);

    IndividualResource instance = instancesFixture.basedUponDunkirk();
    IndividualResource holdings = holdingsFixture.defaultWithHoldings(
      instance.getId());

    IndividualResource locationsResource = locationsFixture.mainFloor();

    //create 3 copies with no location id's assigned.
    itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(),
      locationsResource.getId());

    itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(),
      locationsResource.getId());

    itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(),
      locationsResource.getId());

    JsonObject requestBody = createInstanceRequestObject(instance.getId(), requesterId,
      pickupServicePointId, requestDate, requestExpirationDate);

    Response postResponse = attemptToCreateInstanceRequest(requestBody);

    assertThat(postResponse, hasStatus(HTTP_CREATED));

    JsonObject representation = postResponse.getJson();

    //although we create the items in certain order, this order may not be what
    // the code picks up when query for items by holdings, so there is no point to check for itemId
    validateInstanceRequestResponse(representation,
      pickupServicePointId, instance.getId(), null, RequestType.PAGE);
  }

  @Test
  public void cannotSuccessfullyPlaceATitleLevelRequestWhenTitleLevelRequestMissingRequiredFields() {
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    UUID requesterId = usersFixture.jessica().getId();
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, UTC);
    DateTime requestExpirationDate = requestDate.plusDays(30);

    IndividualResource instance = instancesFixture.basedUponDunkirk();
    IndividualResource holdings = holdingsFixture.defaultWithHoldings(
      instance.getId());

    //create 3 copies with no location id's assigned.
    itemsFixture.basedUponDunkirkWithCustomHoldingAndLocationAndCheckedOut(
      holdings.getId(), null);

    itemsFixture.basedUponDunkirkWithCustomHoldingAndLocationAndCheckedOut(
      holdings.getId(), null);

    itemsFixture.basedUponDunkirkWithCustomHoldingAndLocationAndCheckedOut(
      holdings.getId(), null);

    JsonObject requestBody = createInstanceRequestObject(instance.getId(), requesterId,
      pickupServicePointId, requestDate, requestExpirationDate);
    requestBody.remove("pickupServicePointId");
    requestBody.remove("instanceId");

    Response postResponse = attemptToCreateInstanceRequest(requestBody);
    assertEquals(422, postResponse.getStatusCode());

    assertThat(postResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Request must have an instance id"),
      hasParameter("instanceId", null))));
  }

  @Test
  public void canSuccessfullyPlaceATitleLevelRequestWhenNoCopyIsAvailable() {
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    LocalDate requestDate = new LocalDate(2017, 7, 22);
    LocalDate requestExpirationDate = requestDate.plusDays(30);

    IndividualResource instance = instancesFixture.basedUponDunkirk();
    IndividualResource holdings = holdingsFixture.defaultWithHoldings(
      instance.getId());

    IndividualResource locationsResource = locationsFixture.mainFloor();

    //create 2 copies with no location id's assigned.
    final IndividualResource item1 =
      itemsFixture.basedUponDunkirkWithCustomHoldingAndLocationAndCheckedOut(
        holdings.getId(), locationsResource.getId());

    final IndividualResource item2 =
      itemsFixture.basedUponDunkirkWithCustomHoldingAndLocationAndCheckedOut(
        holdings.getId(), locationsResource.getId());

    //Set up request queues. Item1 has 2 requests (1 queued request), Item2 has 0 queued request. Item2 should be satisfied.
    placeHoldRequest(item1, pickupServicePointId, usersFixture.jessica(),
      requestExpirationDate);

    placeHoldRequest(item1, pickupServicePointId, usersFixture.james(),
      requestExpirationDate);

    placeHoldRequest(item2, pickupServicePointId, usersFixture.steve(),
      requestExpirationDate);

    IndividualResource instanceRequester = usersFixture.charlotte();

    DateTime instanceRequestDate = new DateTime(2017, 7, 22, 10, 22, 54, UTC);
    DateTime instanceRequestDateRequestExpirationDate = instanceRequestDate
      .plusDays(30);

    JsonObject requestBody = createInstanceRequestObject(instance.getId(),
      instanceRequester.getId(), pickupServicePointId, instanceRequestDate,
      instanceRequestDateRequestExpirationDate);

    Response postResponse = attemptToCreateInstanceRequest(requestBody);
    assertEquals(201, postResponse.getStatusCode());


    JsonObject representation = postResponse.getJson();
    validateInstanceRequestResponse(representation, pickupServicePointId,
      instance.getId(), item2.getId(), RequestType.HOLD);
  }

  @Test
  public void canSuccessfullyPlaceATitleLevelRequestOnAvailableCopyWithAdditionalUnavailableCopy() {
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    LocalDate requestDate = new LocalDate(2017, 7, 22);
    LocalDate requestExpirationDate = requestDate.plusDays(30);

    IndividualResource instance = instancesFixture.basedUponDunkirk();
    IndividualResource holdings = holdingsFixture.defaultWithHoldings(
      instance.getId());

    IndividualResource locationsResource = locationsFixture.mainFloor();

    //create 2 copies with no location id's assigned.
    final IndividualResource item1 =
      itemsFixture.basedUponDunkirkWithCustomHoldingAndLocationAndCheckedOut(
        holdings.getId(), locationsResource.getId());

    final IndividualResource item2 =
      itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(),
        locationsResource.getId());

    //Set up request queues. Item1 has requests (1 queued request), Item2 is Available. Item2 should be satisfied.
    placeHoldRequest(item1, pickupServicePointId, usersFixture.jessica(),
      requestExpirationDate);

    placeHoldRequest(item1, pickupServicePointId, usersFixture.james(),
      requestExpirationDate);

    IndividualResource instanceRequester = usersFixture.charlotte();

    DateTime instanceRequestDate = new DateTime(2017, 7, 22, 10, 22, 54, UTC);
    DateTime instanceRequestDateRequestExpirationDate
      = instanceRequestDate.plusDays(30);

    JsonObject requestBody = createInstanceRequestObject(instance.getId(),
      instanceRequester.getId(), pickupServicePointId, instanceRequestDate,
      instanceRequestDateRequestExpirationDate);

    Response postResponse = attemptToCreateInstanceRequest(requestBody);
    assertEquals(201, postResponse.getStatusCode());

    JsonObject representation = postResponse.getJson();
    validateInstanceRequestResponse(representation, pickupServicePointId,
      instance.getId(), item2.getId(), RequestType.PAGE);
  }

  @Test
  public void canSuccessfullyPlaceATitleLevelRequestOnUnavailableCopyWhenBothUnavailableCopiesHaveSameQueueLength() {
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    DateTime instanceRequestDate = new DateTime(2017, 7, 22, 10, 22, 54, UTC);
    DateTime instanceRequestDateRequestExpirationDate
      = instanceRequestDate.plusDays(30);

    LocalDate requestDate = new LocalDate(2017, 7, 22);
    LocalDate requestExpirationDate1 = requestDate.plusDays(30);
    LocalDate requestExpirationDate2 = requestDate.minusDays(30);

    IndividualResource instance = instancesFixture.basedUponDunkirk();
    IndividualResource holdings = holdingsFixture.defaultWithHoldings(
      instance.getId());

    IndividualResource locationsResource = locationsFixture.mainFloor();

    //create 2 copies with no location id's assigned.
    final IndividualResource item1 =
      itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(),
        locationsResource.getId());

    final IndividualResource item2 =
      itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(),
        locationsResource.getId());

    loansFixture.createLoan(item1, usersFixture.charlotte(), now().plusDays(2));
    loansFixture.createLoan(item2, usersFixture.charlotte(), now());

    //Set up request queues. Item1 has requests (1 queued request), Item2 is requests (1 queued), either should be satisfied.
    placeHoldRequest(item1, pickupServicePointId, usersFixture.jessica(),
      requestExpirationDate1);

    placeHoldRequest(item1, pickupServicePointId, usersFixture.james(),
      requestExpirationDate1);

    placeHoldRequest(item2, pickupServicePointId, usersFixture.steve(),
      requestExpirationDate2);

    placeHoldRequest(item2, pickupServicePointId, usersFixture.rebecca(),
      requestExpirationDate2);

    IndividualResource instanceRequester = usersFixture.undergradHenry();

    JsonObject requestBody = createInstanceRequestObject(instance.getId(),
      instanceRequester.getId(), pickupServicePointId, instanceRequestDate,
      instanceRequestDateRequestExpirationDate);

    Response postResponse = attemptToCreateInstanceRequest(requestBody);
    assertEquals(201, postResponse.getStatusCode());

    JsonObject representation = postResponse.getJson();

    assertNotNull(representation);
    assertEquals(pickupServicePointId.toString(),
      representation.getString("pickupServicePointId"));

    assertEquals("Circ Desk 1",
      representation.getJsonObject("pickupServicePoint").getString("name"));

    assertEquals(instance.getId().toString(),
      representation.getJsonObject("item").getString("instanceId"));

    assertEquals(RequestType.HOLD.getValue(),
      representation.getString("requestType"));

    //here we check the itemID. It could be either of the 2 items because we use Future in the code to get request queues from the repository,
    //so it's non-deterministic that the futures should come in by a certain order.
    assertTrue(item1.getId().toString().equals(representation.getString("itemId")) ||
      item2.getId().toString().equals(representation.getString("itemId")));
  }

  @Test
  public void canSuccessfullyPlaceATitleLevelRequestOnOneLoneUnavailableCopy() {
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    DateTime instanceRequestDate = new DateTime(2017, 7, 22, 10, 22, 54, UTC);
    DateTime instanceRequestDateRequestExpirationDate
      = instanceRequestDate.plusDays(30);

    LocalDate requestDate = new LocalDate(2017, 7, 22);
    LocalDate requestExpirationDate1 = requestDate.plusDays(30);

    IndividualResource instance = instancesFixture.basedUponDunkirk();
    IndividualResource holdings = holdingsFixture.defaultWithHoldings(
      instance.getId());

    IndividualResource locationsResource = locationsFixture.mainFloor();

    //create 1 copy with no location id's assigned.
    final IndividualResource item =
      itemsFixture.basedUponDunkirkWithCustomHoldingAndLocationAndCheckedOut(
        holdings.getId(), locationsResource.getId());

    //Set up request queues. Item1 has requests (1 queued request)
    placeHoldRequest(item, pickupServicePointId, usersFixture.jessica(),
      requestExpirationDate1);

    placeHoldRequest(item, pickupServicePointId, usersFixture.james(),
      requestExpirationDate1);

    IndividualResource instanceRequester = usersFixture.charlotte();

    JsonObject requestBody = createInstanceRequestObject(instance.getId(),
      instanceRequester.getId(), pickupServicePointId, instanceRequestDate,
      instanceRequestDateRequestExpirationDate);

    Response postResponse = attemptToCreateInstanceRequest(requestBody);
    assertEquals(201, postResponse.getStatusCode());

    JsonObject representation = postResponse.getJson();
    validateInstanceRequestResponse(representation, pickupServicePointId,
      instance.getId(), item.getId(), RequestType.HOLD);
  }

  @Test
  public void canPlaceATitleLevelRequestOnUnavailableCopyWhenThereIsAvailableItem() {
    //The scenario we're checking is if a user has already checked out an item, but then
    //goes back to place an instance level request. The system will find this item and rejects the request for the user, so the user
    //could be given a successful request on an unavailable item instead, if there is any.
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    DateTime instanceRequestDate = new DateTime(2017, 7, 22, 10, 22, 54, UTC);
    DateTime instanceRequestDateRequestExpirationDate
      = instanceRequestDate.plusDays(30);

    LocalDate requestDate = new LocalDate(2017, 7, 22);
    LocalDate requestExpirationDate2 = requestDate.minusDays(30);

    IndividualResource instance = instancesFixture.basedUponDunkirk();
    IndividualResource holdings = holdingsFixture.defaultWithHoldings(
      instance.getId());

    IndividualResource locationsResource = locationsFixture.mainFloor();
    //create 2 copies with no location id's assigned.
    final IndividualResource item1 =
      itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(),
        locationsResource.getId());

    final IndividualResource item2 =
      itemsFixture.basedUponDunkirkWithCustomHoldingAndLocationAndCheckedOut(
        holdings.getId(), locationsResource.getId());

    //Set up request queues. Item1 has requests (1 queued request), Item2 has requests (1 queued), either could be satisfied.
    loansFixture.createLoan(item1, usersFixture.jessica());

    placeHoldRequest(item2, pickupServicePointId, usersFixture.steve(),
      requestExpirationDate2);

    placeHoldRequest(item2, pickupServicePointId, usersFixture.rebecca(),
      requestExpirationDate2);

    IndividualResource instanceRequester = usersFixture.jessica();

    JsonObject requestBody = createInstanceRequestObject(instance.getId(),
      instanceRequester.getId(), pickupServicePointId, instanceRequestDate,
      instanceRequestDateRequestExpirationDate);

    Response postResponse = attemptToCreateInstanceRequest(requestBody);
    assertEquals(201, postResponse.getStatusCode());

    JsonObject representation = postResponse.getJson();
    //Item2 should have been chosen because Jessica already requested item1
    validateInstanceRequestResponse(representation, pickupServicePointId,
      instance.getId(), item2.getId(), RequestType.HOLD);
  }

  @Test
  public void canPlaceRequestWhenAllCopiesAreCheckedOutButNoRequestQueuesForThem() {
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    DateTime instanceRequestDate = new DateTime(2017, 7, 22, 10, 22, 54, UTC);
    DateTime instanceRequestDateRequestExpirationDate
      = instanceRequestDate.plusDays(30);

    IndividualResource instance = instancesFixture.basedUponDunkirk();
    IndividualResource holdings = holdingsFixture.defaultWithHoldings(
      instance.getId());

    IndividualResource locationsResource = locationsFixture.mainFloor();
    //create 2 copies with no location id's assigned.
    final IndividualResource item1 =
      itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(),
        locationsResource.getId());

    final IndividualResource item2 =
      itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(),
        locationsResource.getId());

    loansFixture.createLoan(item1, usersFixture.james(),  now());
    loansFixture.createLoan(item2, usersFixture.rebecca(), now().plusDays(5));

    IndividualResource instanceRequester = usersFixture.charlotte();

    JsonObject requestBody = createInstanceRequestObject(instance.getId(),
      instanceRequester.getId(), pickupServicePointId, instanceRequestDate,
      instanceRequestDateRequestExpirationDate);

    Response postResponse = attemptToCreateInstanceRequest(requestBody);
    assertEquals(201, postResponse.getStatusCode());

    JsonObject representation = postResponse.getJson();

    assertNotNull(representation);
    assertEquals(pickupServicePointId.toString(),
      representation.getString("pickupServicePointId"));

    assertEquals("Circ Desk 1",
      representation.getJsonObject("pickupServicePoint").getString("name"));
    assertEquals(instance.getId().toString(),
      representation.getJsonObject("item").getString("instanceId"));

    assertEquals(RequestType.HOLD.getValue(),
      representation.getString("requestType"));

    assertTrue(item1.getId().toString().equals(representation.getString("itemId")) ||
      item2.getId().toString().equals(representation.getString("itemId")));
  }

  @Test
  public void canPlaceRequestOnCheckedOutCopyWithNearestDueDate() {
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    DateTime instanceRequestDate = new DateTime(2017, 7, 22, 10, 22, 54, UTC);
    DateTime instanceRequestDateRequestExpirationDate =
      instanceRequestDate.plusDays(30);

    IndividualResource instance = instancesFixture.basedUponDunkirk();
    IndividualResource holdings = holdingsFixture.defaultWithHoldings(instance.getId());

    IndividualResource locationsResource = locationsFixture.mainFloor();

    //create 2 copies with no location id's assigned.
    final IndividualResource item1 =
      itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(),
        locationsResource.getId());

    final IndividualResource item2 =
      itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(),
        locationsResource.getId());

    final IndividualResource item3 =
      itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(),
        locationsResource.getId());

    loansFixture.createLoan(item1, usersFixture.james(),  now().plusDays(5));
    loansFixture.createLoan(item2, usersFixture.rebecca(), now().plusDays(3));
    loansFixture.createLoan(item3, usersFixture.steve(), now().plusDays(10));

    IndividualResource instanceRequester = usersFixture.charlotte();

    JsonObject requestBody = createInstanceRequestObject(instance.getId(),
      instanceRequester.getId(), pickupServicePointId, instanceRequestDate,
      instanceRequestDateRequestExpirationDate);

    Response postResponse = attemptToCreateInstanceRequest(requestBody);
    assertEquals(201, postResponse.getStatusCode());

    JsonObject representation = postResponse.getJson();

    assertNotNull(representation);
    assertEquals(pickupServicePointId.toString(),
      representation.getString("pickupServicePointId"));

    assertEquals("Circ Desk 1",
      representation.getJsonObject("pickupServicePoint").getString("name"));

    assertEquals(instance.getId().toString(),
      representation.getJsonObject("item").getString("instanceId"));

    assertEquals(RequestType.HOLD.getValue(),
      representation.getString("requestType"));

    assertEquals(item2.getId().toString(), representation.getString("itemId"));
  }

  @Test
  public void canPlaceRequestOnCheckedOutCopyWithRequestQueuesAndNearestDueDate() {
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    DateTime instanceRequestDate = new DateTime(2017, 7, 22, 10, 22, 54, UTC);
    DateTime instanceRequestDateRequestExpirationDate =
      instanceRequestDate.plusDays(30);

    LocalDate requestDate = new LocalDate(2017, 7, 22);
    LocalDate requestExpirationDate = requestDate.plusDays(30);

    IndividualResource instance = instancesFixture.basedUponDunkirk();
    IndividualResource holdings = holdingsFixture.defaultWithHoldings(
      instance.getId());

    IndividualResource locationsResource = locationsFixture.mainFloor();
    //create 2 copies with no location id's assigned.
    final IndividualResource item1 =
      itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(),
        locationsResource.getId());

    final IndividualResource item2 =
      itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(),
        locationsResource.getId());

    loansFixture.createLoan(item1, usersFixture.james(),  now().plusDays(21));
    loansFixture.createLoan(item2, usersFixture.rebecca(), now().plusDays(5));

    //Set up request queues. Item1 has requests (1 queued request), Item2 is requests (1 queued), either should be satisfied
    //but only item2 should a request be placed on because its due date is nearest.
    placeHoldRequest(item1, pickupServicePointId, usersFixture.steve(),
      requestExpirationDate);

    placeHoldRequest(item1, pickupServicePointId, usersFixture.jessica(),
      requestExpirationDate);

    placeHoldRequest(item2, pickupServicePointId, usersFixture.steve(),
      requestExpirationDate);

    placeHoldRequest(item2, pickupServicePointId, usersFixture.jessica(),
      requestExpirationDate);

    IndividualResource instanceRequester = usersFixture.charlotte();

    JsonObject requestBody = createInstanceRequestObject(instance.getId(),
      instanceRequester.getId(), pickupServicePointId, instanceRequestDate,
      instanceRequestDateRequestExpirationDate);

    Response postResponse = attemptToCreateInstanceRequest(requestBody);
    assertEquals(201, postResponse.getStatusCode());

    JsonObject representation = postResponse.getJson();

    assertNotNull(representation);
    assertEquals(pickupServicePointId.toString(),
      representation.getString("pickupServicePointId"));

    assertEquals("Circ Desk 1",
      representation.getJsonObject("pickupServicePoint").getString("name"));

    assertEquals(instance.getId().toString(),
      representation.getJsonObject("item").getString("instanceId"));

    assertEquals(RequestType.HOLD.getValue(),
      representation.getString("requestType"));

    assertEquals(item2.getId().toString(), representation.getString("itemId"));
  }

  @Test
  public void canCreateATitleLevelRequestForAnUnAvailableItemWithAMatchingPickupLocationId() {
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, UTC);
    DateTime requestExpirationDate = requestDate.plusDays(30);

    IndividualResource instanceMultipleCopies = instancesFixture.basedUponDunkirk();
    IndividualResource holdings = holdingsFixture.defaultWithHoldings(
      instanceMultipleCopies.getId());

    IndividualResource mainFloor = locationsFixture.mainFloor();
    IndividualResource cd4Location = locationsFixture.fourthServicePoint();

    //mainfloor - is the location of CD1, others - no
    final IndividualResource item1 =
      itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(),
        cd4Location.getId());

    final IndividualResource item2 =
      itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(),
        mainFloor.getId());

    final IndividualResource item3 =
      itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(),
        cd4Location.getId());

    //All of these items are checked out, have the same queue length, and due dates
    DateTime sameCheckoutDate = now();
    loansFixture.createLoan(item1, usersFixture.steve(), sameCheckoutDate );
    loansFixture.createLoan(item2, usersFixture.jessica(), sameCheckoutDate );
    loansFixture.createLoan(item3, usersFixture.james(), sameCheckoutDate );

    //For simplicity and by default, these items' request queue lengths are 0.

    JsonObject requestBody = createInstanceRequestObject(instanceMultipleCopies.getId(),
      usersFixture.charlotte().getId(), pickupServicePointId, requestDate,
      requestExpirationDate);

    Response postResponse = attemptToCreateInstanceRequest(requestBody);

    JsonObject representation = postResponse.getJson();
    //mainfloor - is the location of CD1, others - no
    validateInstanceRequestResponse(representation,
      pickupServicePointId,
      instanceMultipleCopies.getId(),
      item2.getId(),
      RequestType.HOLD);
  }

  @Test
  public void canCreateATitleLevelRequestForUnavailableItemHavingDueDateVersusOneWithoutLoan() {
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, UTC);
    DateTime requestExpirationDate = requestDate.plusDays(30);

    IndividualResource instanceMultipleCopies = instancesFixture.basedUponDunkirk();
    IndividualResource holdings = holdingsFixture.defaultWithHoldings(
      instanceMultipleCopies.getId());

    IndividualResource locationsResource = locationsFixture.mainFloor();

    //create two items
    final IndividualResource item2 =
      itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(),
        locationsResource.getId());

    itemsFixture.basedUponDunkirkWithCustomHoldingAndLocationAndStatusInProcess(
      holdings.getId(), null);

    //For simplicity and by default, these items' request queue lengths are 0.
    //One item is "Checked out", the other item is  "In process"
    loansFixture.createLoan(item2, usersFixture.steve(), now());

    JsonObject requestBody = createInstanceRequestObject(
      instanceMultipleCopies.getId(),
      usersFixture.charlotte().getId(),
      pickupServicePointId,
      requestDate,
      requestExpirationDate);

    Response postResponse = attemptToCreateInstanceRequest(requestBody);

    JsonObject representation = postResponse.getJson();
    validateInstanceRequestResponse(representation,
      pickupServicePointId,
      instanceMultipleCopies.getId(),
      item2.getId(),
      RequestType.HOLD);
  }

  @Test
  public void cannotCreateATitleLevelRequestForAPreviouslyRequestedCopy() {
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, UTC);
    DateTime requestExpirationDate = requestDate.plusDays(30);

    IndividualResource instanceMultipleCopies = instancesFixture.basedUponDunkirk();
    IndividualResource holdings = holdingsFixture.defaultWithHoldings(
      instanceMultipleCopies.getId());

    IndividualResource locationsResource = locationsFixture.mainFloor();

    //create three items
    itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(),
      locationsResource.getId());

    final IndividualResource item1 =
      itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(),
        locationsResource.getId());

    itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(),
      null);

    requestsClient.create(new RequestBuilder()
      .page()
      .forItem(item1)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.jessica()));

    JsonObject requestBody = createInstanceRequestObject(
      instanceMultipleCopies.getId(),
      usersFixture.jessica().getId(),
      pickupServicePointId,
      requestDate,
      requestExpirationDate);

    Response postResponse = attemptToCreateInstanceRequest(requestBody);

    assertEquals(422, postResponse.getStatusCode());

    assertThat(postResponse.getJson(), hasErrorWith(allOf(
      hasMessage("This requester already has an open request for an item of this instance"),
      hasParameter("itemId", item1.getId().toString()))));
  }

  @Test
  public void cannotCreateATitleLevelRequestForAnInstanceWithoutCopies() {
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, UTC);
    DateTime requestExpirationDate = requestDate.plusDays(30);

    IndividualResource instanceMultipleCopies = instancesFixture.basedUponDunkirk();
    holdingsFixture.defaultWithHoldings(instanceMultipleCopies.getId());

    JsonObject requestBody = createInstanceRequestObject(
      instanceMultipleCopies.getId(),
      usersFixture.jessica().getId(),
      pickupServicePointId,
      requestDate,
      requestExpirationDate);

    Response postResponse = attemptToCreateInstanceRequest(requestBody);
    assertEquals(422, postResponse.getStatusCode());
    assertThat(postResponse.getJson(), hasErrorWith(allOf(
      hasMessage("There are no items for this instance"),
      hasParameter("items", "empty"))));
  }

  @Test
  public void cannotCreateATitleLevelRequestForAnInstanceWithoutHoldings() {
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, UTC);
    DateTime requestExpirationDate = requestDate.plusDays(30);

    IndividualResource instanceMultipleCopies = instancesFixture.basedUponDunkirk();

    JsonObject requestBody = createInstanceRequestObject(
      instanceMultipleCopies.getId(),
      usersFixture.jessica().getId(),
      pickupServicePointId,
      requestDate,
      requestExpirationDate);

    Response postResponse = attemptToCreateInstanceRequest(requestBody);

    assertEquals(422, postResponse.getStatusCode());
    assertThat(postResponse.getJson(), hasErrorWith(allOf(
      hasMessage("There are no holdings for this instance"),
      hasParameter("holdingsRecords", "null"))));
  }

  private void validateInstanceRequestResponse(JsonObject representation,
    UUID pickupServicePointId, UUID instanceId, UUID itemId,
    RequestType expectedRequestType) {

    assertNotNull(representation);
    assertEquals(pickupServicePointId.toString(),
      representation.getString("pickupServicePointId"));

    assertEquals("Circ Desk 1",
      representation.getJsonObject("pickupServicePoint").getString("name"));

    assertEquals(instanceId.toString(),
      representation.getJsonObject("item").getString("instanceId"));

    assertEquals(expectedRequestType.getValue(),
      representation.getString("requestType"));

    if (itemId != null) {
      assertEquals(itemId.toString(), representation.getString("itemId"));
    }
  }

  private JsonObject createInstanceRequestObject(UUID instanceId,
    UUID requesterId, UUID pickupServicePointId, DateTime requestDate,
    DateTime requestExpirationDate) {

    JsonObject requestBody = new JsonObject();

    write(requestBody, "instanceId", instanceId.toString());
    write(requestBody, "requestDate", requestDate.toString(dateTime()));
    write(requestBody, "requesterId", requesterId.toString());
    write(requestBody, "pickupServicePointId", pickupServicePointId.toString());
    write(requestBody, "fulfilmentPreference", "Hold Shelf");
    write(requestBody, "requestExpirationDate", requestExpirationDate);

    return requestBody;
  }

  private void placeHoldRequest(IndividualResource item,
    UUID requestPickupServicePointId, IndividualResource patron,
    LocalDate requestExpirationDate) {

    IndividualResource holdRequest = requestsClient.create(
      new RequestBuilder()
        .hold()
        .forItem(item)
        .withPickupServicePointId(requestPickupServicePointId)
        .withRequestExpiration(requestExpirationDate)
        .by(patron));

    JsonObject requestedItem = (holdRequest != null)
      ? holdRequest.getJson().getJsonObject("item") : new JsonObject();

    assertThat(requestedItem.getString("status"),
      is(ItemStatus.CHECKED_OUT.getValue()));
  }

  private Response attemptToCreateInstanceRequest(JsonObject representation) {
    return restAssuredClient.post(representation,
      requestsUrl("/instances"), "attempt-to-create-instance-request");
  }
}
