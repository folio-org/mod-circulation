package api.support.fixtures;

import api.support.builders.InstanceBuilder;
import api.support.builders.SearchInstanceBuilder;
import api.support.http.ResourceClient;
import java.util.List;
import java.util.UUID;

public class SearchInstanceFixture {

  private final ResourceClient searchClient;

  public SearchInstanceFixture() {
    this.searchClient = ResourceClient.forSearchClient();
  }

  public void basedUponDunkirk(UUID instanceId) {
    SearchInstanceBuilder builder = new SearchInstanceBuilder(
      new InstanceBuilder(
        "Dunkirk", UUID.randomUUID()).withId(instanceId).create())
      .withItems(List.of(ItemExamples.basedUponDunkirk(UUID.randomUUID(),
        UUID.randomUUID()).create()));
    searchClient.create(builder);
  }
}
