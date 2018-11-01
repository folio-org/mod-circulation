/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package api.support.fixtures;

import api.support.builders.ServicePointBuilder;

/**
 *
 * @author kurt
 */
public class ServicePointExamples {
  
  public static ServicePointBuilder basedUponCircDesk1() {
    return new ServicePointBuilder("Circ Desk 1", "cd1",
        "Circulation Desk -- Hallway");
  }
  
  public static ServicePointBuilder basedUponCircDesk2() {
    return new ServicePointBuilder("Circ Desk 1", "cd1",
        "Circulation Desk -- Back Entrance");
  }
  
}
