package org.folio.circulation.support.json;

import static java.util.function.Function.identity;
import static java.util.stream.Stream.of;
import static org.folio.circulation.support.StreamToListMapper.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyArray;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import lombok.val;

class JsonArrayToStreamMapperTests {
  @Test
  void shouldMapNullToEmptyStream() {
    val mapper = new JsonArrayToStreamMapper<>(identity());

    assertThat(mapper.toStream(null).toArray(), emptyArray());
  }

  @Test
  void shouldSkipNullElements() {
    val mapper = new JsonArrayToStreamMapper<>(identity());

    val array = new JsonArray(toList(of("Foo", "Bar", null, "Ipsum")));

    assertThat(toList(mapper.toStream(array)), contains("Foo", "Bar", "Ipsum"));
  }
}
