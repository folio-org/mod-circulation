package api.support.fixtures;

import api.support.builders.PatronGroupBuilder;

public class PatronGroupExamples {
  
  public static PatronGroupBuilder basedUponRegularUsers() {
    return new PatronGroupBuilder("regular_users", "Regular Users");
  }
  
  public static PatronGroupBuilder basedUponStaff() {
    return new PatronGroupBuilder("staff", "Staff Users");    
  }
  
  public static PatronGroupBuilder basedUponFaculty() {
    return new PatronGroupBuilder("faculty", "Faculty Users");    
  }  
}
