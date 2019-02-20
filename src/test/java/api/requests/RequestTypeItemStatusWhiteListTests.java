package api.requests;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.RequestTypeItemStatusWhiteList;
import org.folio.circulation.domain.RequestType;
import org.junit.Test;

public class RequestTypeItemStatusWhiteListTests{

  @Test
  public void canCreateHoldRequestWhenItemStatusCheckedOut(){
    assertTrue(RequestTypeItemStatusWhiteList.canCreateRequestForItem(ItemStatus.CHECKED_OUT, RequestType.HOLD));
  }

  @Test
  public void canCreateRecallRequestWhenItemStatusCheckedOut(){
    assertTrue(RequestTypeItemStatusWhiteList.canCreateRequestForItem(ItemStatus.CHECKED_OUT, RequestType.RECALL));

  }

  @Test
  public void cannotCreatePagedRequestWhenItemStatusCheckedOut(){
    assertFalse(RequestTypeItemStatusWhiteList.canCreateRequestForItem(ItemStatus.CHECKED_OUT, RequestType.PAGE));
  }

  @Test
  public void cannotCreateNoneRequestWhenItemStatusIsAnything(){
    assertFalse(RequestTypeItemStatusWhiteList.canCreateRequestForItem(ItemStatus.CHECKED_OUT, RequestType.NONE));
  }

  @Test
  public void cannotCreatePagedRequestWhenItemStatusIsNone(){
    assertFalse(RequestTypeItemStatusWhiteList.canCreateRequestForItem(ItemStatus.NONE, RequestType.PAGE));
  }
}
