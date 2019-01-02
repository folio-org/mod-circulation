package api.support.fixtures;

import api.APITestSuite;
import api.support.builders.InstanceBuilder;

public class InstanceExamples {
  public static InstanceBuilder basedUponSmallAngryPlanet() {
    return new InstanceBuilder("The Long Way to a Small, Angry Planet", APITestSuite.booksInstanceTypeId())
      .withContributor("Chambers, Becky");
  }

  public static InstanceBuilder basedUponNod() {
    return new InstanceBuilder("Nod", APITestSuite.booksInstanceTypeId())
      .withContributor("Barnes, Adrian");
  }

  public static InstanceBuilder basedUponUprooted() {
    return new InstanceBuilder("Uprooted", APITestSuite.booksInstanceTypeId())
      .withContributor("Novik, Naomi");
  }

  public static InstanceBuilder basedUponTemeraire() {
    return new InstanceBuilder("Temeraire", APITestSuite.booksInstanceTypeId())
      .withContributor("Novik, Naomi");
  }

  public static InstanceBuilder basedUponInterestingTimes() {
    return new InstanceBuilder("Interesting Times", APITestSuite.booksInstanceTypeId())
      .withContributor("Pratchett, Terry");
  }

  public static InstanceBuilder basedUponDunkirk() {
    return new InstanceBuilder("Dunkirk", APITestSuite.booksInstanceTypeId())
      .withContributor("Nolan, Christopher");
  }
}
