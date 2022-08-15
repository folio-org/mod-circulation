package org.folio.circulation.domain;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.folio.circulation.domain.ItemStatusName.AGED_TO_LOST;
import static org.folio.circulation.domain.ItemStatusName.AVAILABLE;
import static org.folio.circulation.domain.ItemStatusName.AWAITING_DELIVERY;
import static org.folio.circulation.domain.ItemStatusName.CHECKED_OUT;
import static org.folio.circulation.domain.ItemStatusName.CLAIMED_RETURNED;
import static org.folio.circulation.domain.ItemStatusName.DECLARED_LOST;
import static org.folio.circulation.domain.ItemStatusName.INTELLECTUAL_ITEM;
import static org.folio.circulation.domain.ItemStatusName.IN_PROCESS;
import static org.folio.circulation.domain.ItemStatusName.IN_PROCESS_NON_REQUESTABLE;
import static org.folio.circulation.domain.ItemStatusName.LONG_MISSING;
import static org.folio.circulation.domain.ItemStatusName.LOST_AND_PAID;
import static org.folio.circulation.domain.ItemStatusName.NONE;
import static org.folio.circulation.domain.ItemStatusName.ON_ORDER;
import static org.folio.circulation.domain.ItemStatusName.PAGED;
import static org.folio.circulation.domain.ItemStatusName.RESTRICTED;
import static org.folio.circulation.domain.ItemStatusName.UNAVAILABLE;
import static org.folio.circulation.domain.ItemStatusName.UNKNOWN;
import static org.folio.circulation.domain.ItemStatusName.WITHDRAWN;
import static org.folio.circulation.domain.RequestType.HOLD;
import static org.folio.circulation.domain.RequestType.PAGE;
import static org.folio.circulation.domain.RequestType.RECALL;
import static org.folio.circulation.domain.RequestType.from;
import static org.folio.circulation.domain.RequestTypeItemStatusWhiteList.canCreateRequestForItem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;


class  RequestTypeItemStatusWhiteListTests {
  @Test
  void canCreateHoldRequestWhenItemStatusCheckedOut() {
    assertTrue(canCreateRequestForItem(CHECKED_OUT, HOLD));
  }

  @Test
  void canCreateRecallRequestWhenItemStatusCheckedOut() {
    assertTrue(canCreateRequestForItem(CHECKED_OUT, RECALL));
  }

  @Test
  void cannotCreatePagedRequestWhenItemStatusCheckedOut() {
    assertFalse(canCreateRequestForItem(CHECKED_OUT, PAGE));
  }

  @Test
  void cannotCreateNoneRequestWhenItemStatusIsAnything() {
    assertFalse(canCreateRequestForItem(CHECKED_OUT, RequestType.NONE));
  }

  @Test
  void canCreateHoldRequestWhenItemStatusOnOrder() {
    assertTrue(canCreateRequestForItem(ON_ORDER, HOLD));
  }

  @Test
  void canCreateRecallRequestWhenItemStatusOnOrder() {
    assertTrue(canCreateRequestForItem(ON_ORDER, RECALL));
  }

  @Test
  void cannotCreatePagedRequestWhenItemStatusOnOrder() {
    assertFalse(canCreateRequestForItem(ON_ORDER, PAGE));
  }

  @Test
  void canCreateHoldRequestWhenItemStatusInProcess() {
    assertTrue(canCreateRequestForItem(IN_PROCESS, HOLD));
  }

  @Test
  void canCreateRecallRequestWhenItemStatusInProcess() {
    assertTrue(canCreateRequestForItem(IN_PROCESS, RECALL));
  }

  @Test
  void cannotCreatePagedRequestWhenItemStatusInProcess() {
    assertFalse(canCreateRequestForItem(IN_PROCESS, PAGE));
  }

  @Test
  void canCreateRecallRequestWhenItemStatusPaged() {
    assertTrue(canCreateRequestForItem(PAGED, RECALL));
  }

  @Test
  void cannotCreatePagedRequestWhenItemStatusIsNone() {
    assertFalse(canCreateRequestForItem(NONE, PAGE));
  }

  @Test
  void canCreatePagedRequestWhenItemStatusIsAvailable() {
    assertTrue(canCreateRequestForItem(AVAILABLE, PAGE));
  }

  @Test
  void canCreateHoldRequestWhenItemStatusAwaitingDelivery() {
    assertTrue(canCreateRequestForItem(AWAITING_DELIVERY, HOLD));
  }

  @Test
  void canCreateRecallRequestWhenItemStatusAwaitingDelivery() {
    assertTrue(canCreateRequestForItem(AWAITING_DELIVERY, RECALL));
  }

  @Test
  void cannotCreatePagedRequestWhenItemStatusAwaitingDelivery() {
    assertFalse(canCreateRequestForItem(AWAITING_DELIVERY, PAGE));
  }

  @Test
  void cannotCreateNoneRequestWhenItemStatusAwaitingDelivery() {
    assertFalse(canCreateRequestForItem(AWAITING_DELIVERY, RequestType.NONE));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "Hold",
    "Recall",
    "Page"
  })
  void canCreateRequestWhenItemIsRestricted(String requestType) {
    assertTrue(canCreateRequestForItem(RESTRICTED, from(requestType)));
  }

  @ParameterizedTest
  @EmptySource
  @ValueSource(strings = {
    "Hold",
    "Recall",
    "Page"
  })
  void cannotCreateRequestWhenItemStatusDeclaredLostItem(String requestType) {
    assertFalse(canCreateRequestForItem(DECLARED_LOST, from(requestType)));
  }

  @ParameterizedTest
  @EmptySource
  @ValueSource(strings = {
    "Hold",
    "Recall",
    "Page"
  })
  void cannotCreateRequestWhenItemStatusClaimedReturned(String requestType) {
    assertFalse(canCreateRequestForItem(CLAIMED_RETURNED, from(requestType)));
  }

  @ParameterizedTest
  @EmptySource
  @ValueSource(strings = {
    "Hold",
    "Recall",
    "Page"
  })
  void cannotCreateRequestWhenItemStatusWithdrawn(String requestType) {
    assertFalse(canCreateRequestForItem(WITHDRAWN, from(requestType)));
  }

  @ParameterizedTest
  @EmptySource
  @ValueSource(strings = {
    "Hold",
    "Recall",
    "Page"
  })
  void cannotCreateRequestWhenItemStatusLostAndPaid(String requestType) {
    assertFalse(canCreateRequestForItem(LOST_AND_PAID, from(requestType)));
  }

  @ParameterizedTest
  @EmptySource
  @ValueSource(strings = {
    "Hold",
    "Recall",
    "Page"
  })
  void cannotCreateRequestWhenItemIsAgedToLost(String requestType) {
    assertFalse(canCreateRequestForItem(AGED_TO_LOST, from(requestType)));
  }

  @ParameterizedTest
  @EmptySource
  @ValueSource(strings = {
    "Hold",
    "Recall",
    "Page"
  })
  void cannotCreateRequestWhenItemHasIntellectualItemStatus(String requestType) {
    assertFalse(canCreateRequestForItem(INTELLECTUAL_ITEM, from(requestType)));
  }

  @ParameterizedTest
  @EmptySource
  @ValueSource(strings = {
    "Hold",
    "Recall",
    "Page"
  })
  void cannotCreateRequestWhenItemIsInProcessNonRequestable(String requestType) {
    assertFalse(canCreateRequestForItem(IN_PROCESS_NON_REQUESTABLE, from(requestType)));
  }

  @ParameterizedTest
  @EmptySource
  @ValueSource(strings = {
    "Hold",
    "Recall",
    "Page"
  })
  void cannotCreateRequestWhenItemIsLongMissing(String requestType) {
    assertFalse(canCreateRequestForItem(LONG_MISSING, from(requestType)));
  }

  @ParameterizedTest
  @EmptySource
  @ValueSource(strings = {
    "Hold",
    "Recall",
    "Page"
  })
  void cannotCreateRequestWhenItemIsUnavailable(String requestType) {
    assertFalse(canCreateRequestForItem(UNAVAILABLE, from(requestType)));
  }

  @ParameterizedTest
  @EmptySource
  @ValueSource(strings = {
    "Hold",
    "Recall",
    "Page"
  })
  void cannotCreateRequestWhenItemIsUnknown(String requestType) {
    assertFalse(canCreateRequestForItem(UNKNOWN, from(requestType)));
  }
}
