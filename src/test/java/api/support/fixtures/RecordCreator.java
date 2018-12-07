package api.support.fixtures;

import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;

import api.support.builders.Builder;
import api.support.http.ResourceClient;

class RecordCreator {
  private final ResourceClient client;
  private final Set<UUID> recordIdsToDelete = new HashSet<>();

  RecordCreator(ResourceClient client) {
    this.client = client;
  }

  IndividualResource create(Builder builder)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource createdRecord = client.create(builder);

    recordIdsToDelete.add(createdRecord.getId());

    return createdRecord;
  }

  void cleanUp()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    for (UUID userId : recordIdsToDelete) {
      client.delete(userId);
    }

    recordIdsToDelete.clear();
  }
}
