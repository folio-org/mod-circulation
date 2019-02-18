package api.requests;

import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.RequestRules;
import org.folio.circulation.domain.RequestType;
import org.junit.Assert;
import org.junit.Test;

public class RequestRulesTests {

  @Test
  public void canCreateHoldRequestWhenItemStatusCheckedOut(){
    boolean canCreate = RequestRules.canCreateRequestForItem(ItemStatus.CHECKED_OUT, RequestType.HOLD);
    Assert.assertTrue(canCreate);
  }

  @Test
  public void canCreateRecallRequestWhenItemStatusCheckedOut(){
    boolean canCreate = RequestRules.canCreateRequestForItem(ItemStatus.CHECKED_OUT, RequestType.RECALL);
    Assert.assertTrue(canCreate);
  }

  @Test
  public void cannotCreatePagedRequestWhenItemStatusCheckedOut(){
    boolean canCreate = RequestRules.canCreateRequestForItem(ItemStatus.CHECKED_OUT, RequestType.PAGE);
    Assert.assertFalse(canCreate);
  }

  @Test
  public void cannotCreateNoneRequestWhenItemStatusIsAnything(){
    boolean canCreate = RequestRules.canCreateRequestForItem(ItemStatus.CHECKED_OUT, RequestType.NONE);
    Assert.assertFalse(canCreate);
  }

  @Test
  public void cannotCreatePagedRequestWhenItemStatusIsNone(){
    boolean canCreate = RequestRules.canCreateRequestForItem(ItemStatus.NONE, RequestType.PAGE);
    Assert.assertFalse(canCreate);
  }
}
