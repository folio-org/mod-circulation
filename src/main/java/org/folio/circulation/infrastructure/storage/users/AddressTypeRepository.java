package org.folio.circulation.infrastructure.storage.users;

import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;
import static org.folio.circulation.support.utils.LogUtil.collectionAsString;
import static org.folio.circulation.support.utils.LogUtil.multipleRecordsAsString;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import io.vertx.core.json.JsonArray;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.AddressType;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.User;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.results.Result;

public class AddressTypeRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final CollectionResourceClient addressTypesStorageClient;

  public AddressTypeRepository(Clients clients) {
    addressTypesStorageClient = clients.addressTypesStorage();
  }

  public CompletableFuture<Result<AddressType>> getAddressTypeById(String id) {
    log.debug("getAddressTypeById:: parameters id: {}", id);
    if (id == null) {
      log.info("getAddressTypeById:: id is null");
      return ofAsync(() -> null);
    }

    return FetchSingleRecord.<AddressType>forRecord("address type")
      .using(addressTypesStorageClient)
      .mapTo(AddressType::fromJson)
      .whenNotFound(succeeded(null))
      .fetch(id);
  }

  public CompletableFuture<Result<MultipleRecords<AddressType>>> getAddressTypesByIds(
      Collection<String> ids) {

    log.debug("getAddressTypesByIds:: parameters ids: {}", () -> collectionAsString(ids));
    return findWithMultipleCqlIndexValues(addressTypesStorageClient,
        "addressTypes", AddressType::fromJson)
      .findByIds(ids);
  }

  public CompletableFuture<Result<MultipleRecords<Request>>> findAddressTypesForRequests(
    MultipleRecords<Request> requests) {

    log.debug("findAddressTypesForRequests:: parameters requests: {}", () -> multipleRecordsAsString(requests));

    Set<String> addressTypeIds = requests.toKeys(Request::getDeliveryAddressTypeId);

    return getAddressTypesByIds(addressTypeIds)
      .thenApply(r -> r.next(addressTypes -> matchAddressTypesToRequests(addressTypes, requests)));
  }

  /**
   * Inserts address type names by address type UUID in user's addresses.
   * @param user User object, possibly with an address array.
   * @return The User object with a mutated address array if any.
   */
  public CompletableFuture<Result<User>> setAddressTypeNamesOnUserAddresses(Result<User> user) {
    JsonArray addresses = user.value().getAddresses();
    List<String> addressTypeIds = IntStream.range(0, addresses.size())
        .mapToObj(index -> addresses.getJsonObject(index).getString("addressTypeId"))
        .toList();
    return getAddressTypesByIds(addressTypeIds)
      .thenApply(addressTypesResult -> resolveAddressTypesNamesForIds(addressTypesResult.value(), addresses))
      .thenApply(r -> user);
  }

  private JsonArray resolveAddressTypesNamesForIds(
    MultipleRecords<AddressType> addressTypes, JsonArray addresses) {
    Map<String, AddressType> addressTypeMap = addressTypes.toMap(AddressType::getId);
    IntStream.range(0, addresses.size()).mapToObj(addresses::getJsonObject)
      .forEach(address -> address.put("addressTypeName",
        addressTypeMap.getOrDefault(
          address.getString("addressTypeId", "property missing"),
          new AddressType(null,"", "")
        ).getName()));
    return addresses;
  }

  private Result<MultipleRecords<Request>> matchAddressTypesToRequests(
    MultipleRecords<AddressType> addressTypes, MultipleRecords<Request> requests) {

    log.debug("matchAddressTypesToRequests:: parameters addressTypes: {}, requests: {}",
      () -> multipleRecordsAsString(addressTypes), () -> multipleRecordsAsString(requests));

    Map<String, AddressType> addressTypeMap = addressTypes.toMap(AddressType::getId);

    return succeeded(
      requests.mapRecords(request -> request.withAddressType(
        addressTypeMap.getOrDefault(request.getDeliveryAddressTypeId(), null)))
    );
  }
}
