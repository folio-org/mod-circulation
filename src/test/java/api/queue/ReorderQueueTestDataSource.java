package api.queue;

import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;

class ReorderQueueTestDataSource implements ArgumentsProvider {

  @Override
  public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(
        // Initial state, target state.
        Arguments.of(asArray("1, 2, 3, 4"), asArray("4, 3, 2, 1")),
        Arguments.of(asArray("1, 2, 4, 3"), asArray("3, 4, 2, 1")),
        Arguments.of(asArray("2, 1, 4, 3"), asArray("3, 4, 1, 2")),
        Arguments.of(asArray("2, 1, 3, 4"), asArray("4, 3, 1, 2")),
        Arguments.of(asArray("3, 1, 2, 4"), asArray("2, 4, 1, 3")),
        Arguments.of(asArray("3, 4, 2, 1"), asArray("3, 4, 2, 1")),
        Arguments.of(asArray("3, 4, 1, 2"), asArray("2, 1, 4, 3")),
        Arguments.of(asArray("4, 3, 1, 2"), asArray("4, 3, 2, 1")),
        Arguments.of(asArray("4, 1, 3, 2"), asArray("4, 3, 1, 2")),
        Arguments.of(asArray("4, 1, 2, 3"), asArray("2, 4, 1, 3"))
      );
  }

  private static Integer[] asArray(String csv) {
    Integer[] params = Arrays.stream(csv.split(","))
      .map(String::trim)
      .map(value -> {
        return Integer.valueOf(value);
      })
      .toArray(Integer[]::new);

    return params;
  }
}
