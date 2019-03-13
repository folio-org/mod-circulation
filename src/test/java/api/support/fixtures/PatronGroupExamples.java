package api.support.fixtures;

import api.support.builders.PatronGroupBuilder;

class PatronGroupExamples {
  static PatronGroupBuilder regular() {
    return new PatronGroupBuilder("Regular Group", "Regular group");
  }

  static PatronGroupBuilder alternative() {
    return new PatronGroupBuilder("Alternative Group", "Alternative group");
  }

  static PatronGroupBuilder staff() {
    return new PatronGroupBuilder("staff", "Staff users");
  }

  static PatronGroupBuilder faculty() {
    return new PatronGroupBuilder("faculty", "Faculty users");
  }

  static PatronGroupBuilder undergrad(){
    return new PatronGroupBuilder("undergrad", "Undergraduate users");
  }
}
