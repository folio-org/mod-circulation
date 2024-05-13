package api.support.fixtures;

import api.support.builders.InstanceBuilder;
import api.support.builders.SearchBuilder;
import api.support.http.ResourceClient;
import java.util.List;
import java.util.UUID;

public class SearchFixture {

  private final ResourceClient searchClient;

  public SearchFixture() {
    this.searchClient = ResourceClient.forSearchClient();
  }

  public void basedUponDunkirk(UUID instanceId) {
    SearchBuilder builder = new SearchBuilder(new InstanceBuilder("Dunkirk",
      UUID.randomUUID()).withId(instanceId).create()).withItems(List.of(ItemExamples.basedUponDunkirk(UUID.randomUUID(), UUID.randomUUID()).create()));
    searchClient.create(builder);
  }
}
