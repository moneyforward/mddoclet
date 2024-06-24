package com.github.dakusui.mddoclet.example;

/**
 * An entry point class of this example.
 */
public class ExampleMain implements ExampleInterface {
  private ExampleMain() {
  }
  
  /**
   * An entry point method.
   * @param args Arguments
   */
  public static void main(String[] args) {
    System.out.println("Hello World!");
  }
  
  
  /**
   * An example method.
   * This method just returns an exxample value.
   * @return An example value.
   */
  @Override
  public String exampleMethod1() {
    return "example";
  }
}
