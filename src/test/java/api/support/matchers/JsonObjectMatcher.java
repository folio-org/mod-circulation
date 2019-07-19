package api.support.matchers;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import com.jayway.jsonpath.ReadContext;
import com.jayway.jsonpath.matchers.JsonPathMatchers;

import io.vertx.core.json.JsonObject;

public class JsonObjectMatcher extends TypeSafeDiagnosingMatcher<JsonObject> {

  @SafeVarargs
  public static Matcher<JsonObject> allOfPaths(Matcher<? super ReadContext>... jsonPathMatchers) {
    return new JsonObjectMatcher(Arrays.asList(jsonPathMatchers));
  }

  public static Matcher<JsonObject> allOfPaths(List<Matcher<? super ReadContext>> jsonPathMatchers) {
    return new JsonObjectMatcher(jsonPathMatchers);
  }

  public static Matcher<JsonObject> allOfPaths(Map<String, Matcher<String>> jsonPathMatchers) {
    List<Matcher<? super ReadContext>> matchers = jsonPathMatchers.entrySet().stream()
      .map(e -> hasJsonPath(e.getKey(), e.getValue()))
      .collect(Collectors.toList());
    return JsonObjectMatcher.allOfPaths(matchers);
  }

  private final List<Matcher<? super ReadContext>> jsonPathMatchers;

  public JsonObjectMatcher(List<Matcher<? super ReadContext>> jsonPathMatchers) {
    this.jsonPathMatchers = jsonPathMatchers;
  }

  @Override
  protected boolean matchesSafely(JsonObject jsonObject, Description description) {
    String jsonString = jsonObject.encode();

    List<Matcher<String>> notMatched = jsonPathMatchers.stream()
      .map(JsonPathMatchers::isJsonString)
      .filter(m -> !m.matches(jsonString))
      .collect(Collectors.toList());

    if (!notMatched.isEmpty()) {
      description.appendText(" not matched paths: ")
        .appendList("<", ", ", ">", notMatched);
      return false;
    }

    return true;
  }

  @Override
  public void describeTo(Description description) {
    description.appendText("json object with: ")
      .appendList("<", ", ", ">", jsonPathMatchers);
  }
}
