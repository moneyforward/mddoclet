package com.github.dakusui.mddoclet;

import com.github.dakusui.mddoclet.testutils.TestBase;
import com.github.dakusui.thincrest.TestAssertions;
import com.github.dakusui.thincrest_pcond.fluent.Statement;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static com.github.dakusui.thincrest.TestAssertions.assertStatement;
import static com.github.dakusui.thincrest_pcond.fluent.Statement.objectValue;

@Disabled
public class MdDocletTest extends TestBase {
  @Test
  public void testMain() {
  }
  
  @Disabled
  @Test
  public void givenAppObject_whenProcessHello_thenProcessedHello_step1() {
    String s = "hello";
    // Remember, `TestAssertions` is the class you start with to write your test using `thincrest-pcond`.
    // `assertStatement(Statement)` is the most basic way to begin with.
    TestAssertions.assertStatement(
        // Since it accepts `Statement`, you try `Statement` class.
        // Since you are testing your class, not String, short, int, long., you are choosing `objectValue` method and give your object `new Java8App()`.
        Statement.objectValue(new MdDoclet())
            // Invoke your method `process`, which takes a string parameter.
            // thincrest-pcond chooses appropriate method automatically (narrowest possible one.)
            .invoke("process", s)
            // Optional: Let the compiler know it returns string.
            .asString()
            // Let the object know you are now validating the returned value.
            .then()
            // Does it contain a string 'processed:'?
            .contains("processed:")
            // Does it contain `s`?
            .contains(s));
    // Even if you didn't do `asString()`, you can still do `isEqualTo("processed:" + s)`.
  }
  
  @Disabled
  @Test
  public void givenAppObject_whenProcessHello_thenProcessedHello_step2() {
    String s = "hello";
    // Let's clean up by using static import.
    assertStatement(objectValue(new MdDoclet())
        .invoke("process", s)
        .asString()
        .then()
        .contains("processed:")
        .contains(s));
  }
  
  /**
   * This is an example to show how a failure message from thincrest-pcond looks like.
   * "expected" and "actual" will be displayed side-by-side by your IDE.
   *
   * .expected
   * ----
   *     Java8App@3eb7fc54->transform                         ->"processed:hello"
   *                      ->  <>.process(<hello>)             ->"processed:hello"
   *     "processed:hello"->  castTo[String]                  ->"processed:hello"
   *                      ->THEN:allOf                        ->true
   * [0]                  ->    containsString[notProcessed:] ->true
   *                      ->    containsString[hello]         ->true
   *
   * .Detail of failure [0]
   * ---
   * containsString[notProcessed:]
   * ---
   * ----
   *
   * .actual
   * ----
   *     Java8App@3eb7fc54->transform                         ->"processed:hello"
   *                      ->  <>.process(<hello>)             ->"processed:hello"
   *     "processed:hello"->  castTo[String]                  ->"processed:hello"
   *                      ->THEN:allOf                        ->false
   * [0]                  ->    containsString[notProcessed:] ->false
   *                      ->    containsString[hello]         ->true
   *
   * .Detail of failure [0]
   * ---
   * processed:hello
   * ---
   * ----
   *
   * Which part of your expectation isn't met will be shown with the actual value.
   */
  @Disabled
  @Test
  public void givenAppObject_whenProcessHello_thenNotProcessedHello() {
    String s = "hello";
    assertStatement(objectValue(new MdDoclet())
        .invoke("process", s)
        .asString()
        .then()
        .contains("notProcessed:")
        .contains(s));
  }
}
