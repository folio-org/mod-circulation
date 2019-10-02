package org.folio.circulation.domain;

import static org.folio.circulation.support.Result.ofAsync;
import static org.folio.circulation.support.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.Result;

public class AddressTypeRepository {
  private final CollectionResourceClient addressTypesStorageClient;

  public AddressTypeRepository(Clients clients) {
    addressTypesStorageClient = clients.addressTypesStorage();
  }

  public CompletableFuture<Result<AddressType>> getAddressTypeById(String id) {
    if (id == null) {
      return ofAsync(() -> null);
    }

    return FetchSingleRecord.<AddressType>forRecord("address type")
      .using(addressTypesStorageClient)
      .mapTo(AddressType::new)
      .whenNotFound(succeeded(null))
      .fetch(id);
  }
}
