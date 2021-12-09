package org.folio.circulation.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutionException;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CirculationPolicyRepositoryTest {
  private final CirculationPolicyRepository<LoanPolicy> repository = mock(CirculationPolicyRepository.class, Mockito.CALLS_REAL_METHODS);
  private final Item item = mock(Item.class);
  private final User user = mock(User.class);

  @Test
  void lookupPolicyIdShouldFailWhenPatronGroupIdIsNullForTheUser() throws ExecutionException, InterruptedException {
    when(item.isNotFound()).thenReturn(false);
    when(user.getPatronGroupId()).thenReturn(null);
    var result = repository.lookupPolicyId(item, user).get();

    assertEquals("Server error failure, reason: Unable to apply circulation rules to a user with null value as patronGroupId", result.cause().toString());
  }

  @Test
  void lookupPolicyIdShouldFailWhenLocationIdIsNullForTheItem() throws ExecutionException, InterruptedException {
    when(item.isNotFound()).thenReturn(false);
    when(user.getPatronGroupId()).thenReturn("1111");
    when(item.getLocationId()).thenReturn(null);
    var result = repository.lookupPolicyId(item, user).get();

    assertEquals("Server error failure, reason: Unable to apply circulation rules to an item with null value as locationId", result.cause().toString());
  }

  @Test
  void lookupPolicyIdShouldFailWhenLoanTypeIdIsNullForTheItem() throws ExecutionException, InterruptedException {
    when(item.isNotFound()).thenReturn(false);
    when(user.getPatronGroupId()).thenReturn("1111");
    when(item.getLocationId()).thenReturn("2222");
    when(item.getLoanTypeId()).thenReturn(null);

    var result = repository.lookupPolicyId(item, user).get();

    assertEquals("Server error failure, reason: Unable to apply circulation rules to an item which loan type can not be determined", result.cause().toString());
  }

  @Test
  void lookupPolicyIdShouldFailWhenMaterialTypeIdIsNullForTheItem() throws ExecutionException, InterruptedException {
    when(item.isNotFound()).thenReturn(false);
    when(user.getPatronGroupId()).thenReturn("1111");
    when(item.getLocationId()).thenReturn("2222");
    when(item.getLoanTypeId()).thenReturn("3333");
    when(item.getMaterialTypeId()).thenReturn(null);

    var result = repository.lookupPolicyId(item, user).get();

    assertEquals("Server error failure, reason: Unable to apply circulation rules to an item with null value as materialTypeId", result.cause().toString());
  }
}
