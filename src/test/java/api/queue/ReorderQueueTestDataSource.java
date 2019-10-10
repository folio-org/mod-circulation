package api.queue;

import java.util.Arrays;

public class ReorderQueueTestDataSource {

  @SuppressWarnings("unused")
  public static Object[][] provideDataForReorderQueueTwiceTest() {
    return new Object[][]{
      // Initial state, target state
      testCase("1, 2, 3, 4", "4, 3, 2, 1"),
      testCase("1, 2, 4, 3", "3, 4, 2, 1"),
      testCase("2, 1, 4, 3", "3, 4, 1, 2"),
      testCase("2, 1, 3, 4", "4, 3, 1, 2"),
      testCase("3, 1, 2, 4", "2, 4, 1, 3"),
      testCase("3, 4, 2, 1", "3, 4, 2, 1"),
      testCase("3, 4, 1, 2", "2, 1, 50, 3"),
      testCase("4, 3, 1, 2", "4, 3, 2, 1"),
      testCase("4, 1, 3, 2", "4, 3, 1, 2"),
      testCase("4, 1, 2, 3", "2, 4, 1, 3"),
      testCase("4, 50, 2, 3", "2, 1, 50, 3"),
    };
  }

  private static Object[] testCase(String initialState, String targetState) {
    Integer[] initialStateParams = Arrays.stream(initialState.split(","))
      .map(String::trim)
      .map(Integer::new)
      .toArray(Integer[]::new);

    Integer[] targetStateParams = Arrays.stream(targetState.split(","))
      .map(String::trim)
      .map(Integer::new)
      .toArray(Integer[]::new);

    return new Object[]{initialStateParams, targetStateParams};
  }
}
