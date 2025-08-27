package api.support.utl;

import java.util.stream.Stream;

import org.junit.jupiter.params.provider.Arguments;

public class BooleanArgumentProvider {
  private static Stream<Arguments> provideTrueValues() {
    return Stream.of(
      Arguments.of(true),
      Arguments.of("true")
    );
  }

  private static Stream<Arguments> provideFalseValues() {
    return Stream.of(
      Arguments.of(false),
      Arguments.of("false")
    );
  }

  private static Stream<Arguments> provideTrueAndFalseValues() {
    return Stream.of(
      Arguments.of(true, false),
      Arguments.of("true", false),
      Arguments.of(true, "false"),
      Arguments.of("true", "false")
    );
  }
}
