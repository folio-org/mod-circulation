package api.support.fixtures;

import java.util.List;
import java.util.UUID;

import api.support.builders.InstanceBuilder;
import api.support.builders.SearchInstanceBuilder;
import api.support.http.ItemResource;
import api.support.http.ResourceClient;

public class SearchInstanceFixture {

  private final ResourceClient searchClient;

  public SearchInstanceFixture() {
    this.searchClient = ResourceClient.forSearchClient();
  }

  public void basedUponDunkirk(UUID instanceId, ItemResource itemResource) {
    SearchInstanceBuilder builder = new SearchInstanceBuilder(
      new InstanceBuilder(
        "Dunkirk", UUID.randomUUID()).withId(instanceId).create())
      .withItems(List.of(itemResource.getJson()));
    searchClient.create(builder);
  }
}
