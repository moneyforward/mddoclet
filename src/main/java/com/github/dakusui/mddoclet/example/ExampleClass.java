package com.github.dakusui.mddoclet.example;

/**
 * Hello, I am example class.
 * How are you?
 * I am implementing {@link ExampleInterface}.
 *
 * @see ExampleInterface
 * @link <a href="http://www.example.com">...</a>
 */
public class ExampleClass implements ExampleInterface {
  /**
   * An example `String` field.
   */
  private String exampleField1;
  /**
   * An example `int` field.
   */
  public int exampleField2;
  
  /**
   * Another constructor.
   *
   * This creates an instance of `ExampleClass`.
   *
   * @param exampleField1 A value for `exampleField1`
   * @param exampleField2 A value for `exampleField2`
   */
  public ExampleClass(String exampleField1, int exampleField2) {
    this.exampleField1 = exampleField1;
    this.exampleField2 = exampleField2;
  }
  
  /**
   * This is a method to return `field1`.
   *
   * @return a string value
   */
  @Override
  public String exampleMethod1() {
    return exampleField1;
  }
  
  /**
   * This is a method to return `field2`.
   *
   * @param p A parameter to this method.
   * @return an integer value.
   * @see ExampleInterface
   */
  public int exampleMethod2(String p) {
    return exampleField2;
  }
  
  
  /**
   * @return A string representation of this object.
   */
  @Override
  public String toString() {
    return "ExampleClass(" + this.exampleField1 + ", " + this.exampleField2 + ")";
  }
  
  /**
   * This is an example of inner class.
   */
  public static class InnerExampleClass {
    /**
     * An example method that prints a message.
     *
     * @param message A message to be printed.
     */
    public static void exampleMethod(String message) {
      System.out.println(message);
    }
  }
}
