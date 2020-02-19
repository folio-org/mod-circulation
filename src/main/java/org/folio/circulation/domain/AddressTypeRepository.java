package org.folio.circulation.domain;

import static org.folio.circulation.support.Result.ofAsync;
import static org.folio.circulation.support.Result.succeeded;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.MultipleRecordFetcher;
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

  public CompletableFuture<Result<MultipleRecords<AddressType>>> getAddressTypesByIds(Collection<String> ids) {
    return new MultipleRecordFetcher<>(addressTypesStorageClient, "addressTypes", AddressType::new)
      .findByIds(ids);
  }

  public CompletableFuture<Result<MultipleRecords<Request>>> findAddressTypesForRequests(
    MultipleRecords<Request> requests) {

    Set<String> addressTypeIds = requests.toKeys(Request::getDeliveryAddressTypeId);

    return getAddressTypesByIds(addressTypeIds)
      .thenApply(r -> r.next(addressTypes -> matchAddressTypesToRequests(addressTypes, requests)));
  }

  private Result<MultipleRecords<Request>> matchAddressTypesToRequests(
    MultipleRecords<AddressType> addressTypes, MultipleRecords<Request> requests) {

    Map<String, AddressType> addressTypeMap = addressTypes.toMap(AddressType::getId);

    return succeeded(
      requests.mapRecords(request -> request.withAddressType(
        addressTypeMap.getOrDefault(request.getDeliveryAddressTypeId(), null)))
    );
  }
}
