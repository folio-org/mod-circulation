package org.folio.circulation.domain;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.folio.circulation.domain.ItemStatus.WITHDRAWN;
import static org.folio.circulation.domain.RequestTypeItemStatusWhiteList.canCreateRequestForItem;

import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class RequestTypeItemStatusWhiteListTests {

  @Test
  public void canCreateHoldRequestWhenItemStatusCheckedOut() {
    assertTrue(canCreateRequestForItem(ItemStatus.CHECKED_OUT, RequestType.HOLD));
  }

  @Test
  public void canCreateRecallRequestWhenItemStatusCheckedOut() {
    assertTrue(canCreateRequestForItem(ItemStatus.CHECKED_OUT, RequestType.RECALL));
  }

  @Test
  public void cannotCreatePagedRequestWhenItemStatusCheckedOut() {
    assertFalse(canCreateRequestForItem(ItemStatus.CHECKED_OUT, RequestType.PAGE));
  }

  @Test
  public void cannotCreateNoneRequestWhenItemStatusIsAnything() {
    assertFalse(canCreateRequestForItem(ItemStatus.CHECKED_OUT, RequestType.NONE));
  }

  @Test
  public void canCreateHoldRequestWhenItemStatusOnOrder() {
    assertTrue(canCreateRequestForItem(ItemStatus.ON_ORDER, RequestType.HOLD));
  }

  @Test
  public void canCreateRecallRequestWhenItemStatusOnOrder() {
    assertTrue(canCreateRequestForItem(ItemStatus.ON_ORDER, RequestType.RECALL));
  }

  @Test
  public void cannotCreatePagedRequestWhenItemStatusOnOrder() {
    assertFalse(canCreateRequestForItem(ItemStatus.ON_ORDER, RequestType.PAGE));
  }

  @Test
  public void canCreateHoldRequestWhenItemStatusInProcess() {
    assertTrue(canCreateRequestForItem(ItemStatus.IN_PROCESS, RequestType.HOLD));
  }

  @Test
  public void canCreateRecallRequestWhenItemStatusInProcess() {
    assertTrue(canCreateRequestForItem(ItemStatus.IN_PROCESS, RequestType.RECALL));
  }

  @Test
  public void cannotCreatePagedRequestWhenItemStatusInProcess() {
    assertFalse(canCreateRequestForItem(ItemStatus.IN_PROCESS, RequestType.PAGE));
  }

  @Test
  public void canCreateRecallRequestWhenItemStatusPaged() {
    assertTrue(canCreateRequestForItem(ItemStatus.PAGED, RequestType.RECALL));
  }

  @Test
  public void cannotCreatePagedRequestWhenItemStatusIsNone() {
    assertFalse(canCreateRequestForItem(ItemStatus.NONE, RequestType.PAGE));
  }

  @Test
  public void canCreatePagedRequestWhenItemStatusIsAvailable() {
    assertTrue(canCreateRequestForItem(ItemStatus.AVAILABLE, RequestType.PAGE));
  }

  @Test
  public void canCreateHoldRequestWhenItemStatusAwaitingDelivery() {
    assertTrue(canCreateRequestForItem(ItemStatus.AWAITING_DELIVERY, RequestType.HOLD));
  }

  @Test
  public void canCreateRecallRequestWhenItemStatusAwaitingDelivery() {
    assertTrue(canCreateRequestForItem(ItemStatus.AWAITING_DELIVERY, RequestType.RECALL));
  }

  @Test
  public void cannotCreatePagedRequestWhenItemStatusAwaitingDelivery() {
    assertFalse(canCreateRequestForItem(ItemStatus.AWAITING_DELIVERY, RequestType.PAGE));
  }

  @Test
  public void cannotCreateNoneRequestWhenItemStatusAwaitingDelivery() {
    assertFalse(canCreateRequestForItem(ItemStatus.AWAITING_DELIVERY, RequestType.NONE));
  }

  @Test
  @Parameters({
    "",
    "Hold",
    "Recall",
    "Page"
  })
  public void cannotCreateRequestWhenItemStatusDeclaredLostItem(String requestType) {
    assertFalse(canCreateRequestForItem(ItemStatus.DECLARED_LOST,
      RequestType.from(requestType)));
  }

  @Test
  @Parameters({
    "",
    "Hold",
    "Recall",
    "Page"
  })
  public void cannotCreateRequestWhenItemStatusClaimedReturned(String requestType) {
    assertFalse(canCreateRequestForItem(ItemStatus.CLAIMED_RETURNED, RequestType.from(requestType)));
  }

  @Test
  @Parameters({
    "",
    "Hold",
    "Recall",
    "Page"
  })
  public void cannotCreateRequestWhenItemStatusWithdrawn(String requestType) {
    assertFalse(canCreateRequestForItem(WITHDRAWN, RequestType.from(requestType)));
  }
}
