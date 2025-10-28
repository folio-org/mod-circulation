package org.folio.circulation.infrastructure.storage.inventory;

import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;

public class ShadowLocationRepository extends LocationRepository{

  private ShadowLocationRepository(CollectionResourceClient locationsStorageClient,
    CollectionResourceClient institutionsStorageClient,
    CollectionResourceClient campusesStorageClient,
    CollectionResourceClient librariesStorageClient,
    ServicePointRepository servicePointRepository) {
    super(
      locationsStorageClient,
      institutionsStorageClient,
      campusesStorageClient,
      librariesStorageClient,
      servicePointRepository
    );
  }

  public static ShadowLocationRepository using(Clients clients,
    ServicePointRepository servicePointRepository) {

    return new ShadowLocationRepository(
      clients.shadowLocationsStorage(),
      clients.shadowInstitutionsStorage(),
      clients.shadowCampusesStorage(),
      clients.shadowLibrariesStorage(),
      servicePointRepository);
  }

  public static ShadowLocationRepository using(Clients clients) {
    return new ShadowLocationRepository(
      clients.shadowLocationsStorage(),
      clients.shadowInstitutionsStorage(),
      clients.shadowCampusesStorage(),
      clients.shadowLibrariesStorage(),
      new ServicePointRepository(clients));
  }
}
