package api.support.utl;

import java.util.stream.Stream;

import org.junit.jupiter.params.provider.Arguments;

public class BooleanArgumentProvider {
  public static Stream<Arguments> provideTrueValues() {
    return Stream.of(
      Arguments.of(true),
      Arguments.of("true")
    );
  }

  public static Stream<Arguments> provideFalseValues() {
    return Stream.of(
      Arguments.of(false),
      Arguments.of("false")
    );
  }

  public static Stream<Arguments> provideTrueAndFalseValues() {
    return Stream.of(
      Arguments.of(true, false),
      Arguments.of("true", false),
      Arguments.of(true, "false"),
      Arguments.of("true", "false")
    );
  }
}
