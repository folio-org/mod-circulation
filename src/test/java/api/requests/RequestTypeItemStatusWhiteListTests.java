package api.requests;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.RequestTypeItemStatusWhiteList;
import org.junit.Test;

public class RequestTypeItemStatusWhiteListTests {

  @Test
  public void canCreateHoldRequestWhenItemStatusCheckedOut() {
    assertTrue(RequestTypeItemStatusWhiteList.canCreateRequestForItem(ItemStatus.CHECKED_OUT, RequestType.HOLD));
  }

  @Test
  public void canCreateRecallRequestWhenItemStatusCheckedOut() {
    assertTrue(RequestTypeItemStatusWhiteList.canCreateRequestForItem(ItemStatus.CHECKED_OUT, RequestType.RECALL));
  }

  @Test
  public void cannotCreatePagedRequestWhenItemStatusCheckedOut() {
    assertFalse(RequestTypeItemStatusWhiteList.canCreateRequestForItem(ItemStatus.CHECKED_OUT, RequestType.PAGE));
  }

  @Test
  public void cannotCreateNoneRequestWhenItemStatusIsAnything() {
    assertFalse(RequestTypeItemStatusWhiteList.canCreateRequestForItem(ItemStatus.CHECKED_OUT, RequestType.NONE));
  }

  @Test
  public void canCreateHoldRequestWhenItemStatusOnOrder() {
    assertTrue(RequestTypeItemStatusWhiteList.canCreateRequestForItem(ItemStatus.ON_ORDER, RequestType.HOLD));
  }

  @Test
  public void canCreateRecallRequestWhenItemStatusOnOrder() {
    assertTrue(RequestTypeItemStatusWhiteList.canCreateRequestForItem(ItemStatus.ON_ORDER, RequestType.RECALL));
  }

  @Test
  public void cannotCreatePagedRequestWhenItemStatusOnOrder() {
    assertFalse(RequestTypeItemStatusWhiteList.canCreateRequestForItem(ItemStatus.ON_ORDER, RequestType.PAGE));
  }

  @Test
  public void canCreateHoldRequestWhenItemStatusInProcess() {
    assertTrue(RequestTypeItemStatusWhiteList.canCreateRequestForItem(ItemStatus.IN_PROCESS, RequestType.HOLD));
  }

  @Test
  public void canCreateRecallRequestWhenItemStatusInProcess() {
    assertTrue(RequestTypeItemStatusWhiteList.canCreateRequestForItem(ItemStatus.IN_PROCESS, RequestType.RECALL));
  }

  @Test
  public void cannotCreatePagedRequestWhenItemStatusInProcess() {
    assertFalse(RequestTypeItemStatusWhiteList.canCreateRequestForItem(ItemStatus.IN_PROCESS, RequestType.PAGE));
  }

  @Test
  public void canCreateRecallRequestWhenItemStatusPaged() {    
    assertTrue(RequestTypeItemStatusWhiteList.canCreateRequestForItem(ItemStatus.PAGED, RequestType.RECALL));
  }

  @Test
  public void cannotCreatePagedRequestWhenItemStatusIsNone() {
    assertFalse(RequestTypeItemStatusWhiteList.canCreateRequestForItem(ItemStatus.NONE, RequestType.PAGE));
  }

  @Test
  public void canCreatePagedRequestWhenItemStatusIsAvailable() {
    assertTrue(RequestTypeItemStatusWhiteList.canCreateRequestForItem(ItemStatus.AVAILABLE, RequestType.PAGE));
  }
}
