package api.support.matchers;

import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static api.support.matchers.JsonObjectMatcher.hasNoJsonPath;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.folio.circulation.domain.RequestType;
import org.hamcrest.Matcher;

import api.support.dto.AllowedServicePoint;
import io.vertx.core.json.JsonObject;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class AllowedServicePointsMatchers {

  public static Matcher<JsonObject> allowedServicePointMatcher(Map<RequestType,
    List<AllowedServicePoint>> parsedResponse) {

    List<Matcher<? super JsonObject>> arraysMatcher = parsedResponse.keySet().stream()
      .filter(type -> parsedResponse.get(type) != null && !parsedResponse.get(type).isEmpty())
      .map(type -> hasJsonPath(type.value + "[*]",
        getAllowedServicePointsListMatcher(parsedResponse.get(type))))
      .collect(Collectors.toList());

    List<Matcher<? super JsonObject>> missingTypesMatcher = Arrays.stream(RequestType.values())
      .filter(type -> !RequestType.NONE.equals(type))
      .filter(type -> !parsedResponse.containsKey(type) ||
        (parsedResponse.get(type) != null && parsedResponse.get(type).isEmpty()))
      .map(type -> hasNoJsonPath(type.value + "[*]"))
      .collect(Collectors.toList());

    List<Matcher<? super JsonObject>> allMatchers = new ArrayList<>();
    allMatchers.addAll(arraysMatcher);
    allMatchers.addAll(missingTypesMatcher);

    return allOf(allMatchers);
  }

  public static Matcher<Iterable<? extends JsonObject>> getAllowedServicePointsListMatcher(
    List<AllowedServicePoint> allowedServicePoints) {

    return containsInAnyOrder(
      allowedServicePoints.stream()
        .map(AllowedServicePointsMatchers::getSingleAllowedServicePointMatcher)
        .collect(Collectors.toList())
    );
  }

  public static Matcher<JsonObject> getSingleAllowedServicePointMatcher(
    AllowedServicePoint allowedServicePoint) {

    return allOf(
      hasJsonPath("id", is(allowedServicePoint.getId())),
      hasJsonPath("name", is(allowedServicePoint.getName()))
    );
  }

}
