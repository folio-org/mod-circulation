package org.folio.circulation.infrastructure.storage.inventory;

import static api.support.matchers.FailureMatcher.isErrorFailureContaining;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.folio.circulation.domain.Holdings;
import org.folio.circulation.domain.Instance;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.LoanType;
import org.folio.circulation.domain.MaterialType;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;

import lombok.SneakyThrows;

class ItemRepositoryTests {
  @Test
  void cannotUpdateAnItemThatHasNotBeenFetched() {
    final var repository = new ItemRepository(null, null, null, null, null,
      null, null);

    final var notFetchedItem = new Item(null, null, null, null, null, null, null, false,
      Holdings.unknown(), Instance.unknown(), MaterialType.unknown(), LoanType.unknown());

    final var updateResult = get(repository.updateItem(notFetchedItem));

    assertThat(updateResult, isErrorFailureContaining(
      "Cannot update item when original representation is not available in identity map"));
  }

  @SneakyThrows
  private <T> Result<T> get(CompletableFuture<Result<T>> future) {
    return future.get(1, TimeUnit.SECONDS);
  }
}
