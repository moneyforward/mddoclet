package com.github.dakusui.mddoclet;

import jdk.javadoc.doclet.Doclet;

import java.util.List;
import java.util.function.Predicate;

import static java.util.Collections.singletonList;

/**
 * A utility class to create Option objects.
 */
public enum MdDocletOptions {
  ;
  
  /**
   * @param description A description of the returned option.
   * @return An option object
   * @see Doclet.Option#getParameters()
   */
  static Doclet.Option createOption(String name, String parameters, String description, Predicate<List<String>> callback) {
    return new Doclet.Option() {
      
      @Override
      public int getArgumentCount() {
        return 1;
      }
      
      @Override
      public String getDescription() {
        return description;
      }
      
      @Override
      public Kind getKind() {
        return Kind.OTHER;
      }
      
      @Override
      public List<String> getNames() {
        return singletonList(name);
      }
      
      @Override
      public String getParameters() {
        return name + " " + parameters;
      }
      
      @Override
      public boolean process(String option, List<String> arguments) {
        return callback.test(arguments);
      }
    };
  }
  
}
