package com.github.dakusui.mddoclet;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.util.DocTrees;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import java.io.*;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import static com.github.dakusui.mddoclet.MdDocletOptions.createOption;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * A Doclet, that produces "markdown" files, not HTML files.
 */
public class MdDoclet implements Doclet {
  
  private Reporter reporter;
  private File overviewFile = null;
  private File destinationDirectory = new File(".");
  private String basePath = "/";
  private BiPredicate<ModuleElement, PackageElement> packageFilter = (moduleElement, packageElement) -> true;
  
  /**
   * Creates an instance of this class.
   */
  public MdDoclet() {
  }
  
  /**
   * Initializes this instance.
   * Currently, just reports a callback is triggered.
   *
   * @param locale   the locale to be used
   * @param reporter the reporter to be used
   */
  @Override
  public void init(Locale locale, Reporter reporter) {
    this.reporter = reporter;
    this.reporter.print(Diagnostic.Kind.NOTE, "Doclet initialized");
    
  }
  
  /**
   * Returns a name of this doclet.
   *
   * @return A name of this doclet.
   */
  @Override
  public String getName() {
    return "mdDoclet";
  }
  
  /**
   * Returns an empty set as this doclet doesn't have any extra options.
   *
   * @return An empty set.
   */
  @Override
  public Set<? extends Option> getSupportedOptions() {
    return Set.of(createOption("-d",
                               "<directory>",
                               "Destination directory for output",
                               args -> {
                                 destinationDirectory = new File(args.getFirst());
                                 if (!destinationDirectory.exists()) {
                                   if (!destinationDirectory.mkdirs()) {
                                     report("Failed to create destination directory: " + destinationDirectory);
                                     return false;
                                   }
                                 }
                                 if (destinationDirectory.exists() && !destinationDirectory.isDirectory()) {
                                   report(
                                       "Specified destination " + destinationDirectory + " is not a directory: " + destinationDirectory);
                                   return false;
                                 }
                                 return true;
                               }),
                  createOption("-overview",
                               "<file>",
                               "Read overview documentation from markdown file",
                               args -> {
                                 overviewFile = new File(args.getFirst());
                                 if (!(overviewFile.exists() && overviewFile.isFile() && overviewFile.canRead())) {
                                   report("Overview file does not exist or is not readable: " + overviewFile);
                                   return false;
                                 }
                                 return true;
                               }),
                  createOption("-base-path",
                               "<pathFromSiteUrlToDocRoot>",
                               "Path from site URL to the document root",
                               args -> {
                                 report(
                                     "Relative path from the site URL to the document root is set to " + args.getFirst());
                                 MdDoclet.this.basePath = (args.getFirst() + "/").replaceAll("/+",
                                                                                             "/");
                                 return true;
                               }),
                  createOption("-target-packages",
                               "<moduleNameRegex#packageNameRegex>",
                               "Packages to generate JavaDocs; Only packages whose enclosing module name and their own names match given regex are processed by this Doclet",
                               args -> {
                                 MdDoclet.this.packageFilter = (moduleElement, packageElement) -> {
                                   var hasPoundSign = args.getFirst()
                                                          .contains("#");
                                   var poundSignIndex = args.getFirst()
                                                            .indexOf("#");
                                   String patternForModule = hasPoundSign
                                                             ? args.getFirst()
                                                                   .substring(0, poundSignIndex)
                                                             : ".*";
                                   String patternForPackage = hasPoundSign
                                                              ? args.getFirst()
                                                                    .substring(poundSignIndex + 1)
                                                              : args.getFirst();
                                   moduleElement.getQualifiedName();
                                   return moduleElement.getQualifiedName()
                                                       .toString()
                                                       .matches(patternForModule)
                                       && packageElement.getQualifiedName()
                                                        .toString()
                                                        .matches(patternForPackage);
                                 };
                                 return true;
                               }
                              ));
  }
  
  /**
   * Returns the source code version that this doclet supports.
   *
   * @return The source code version that this doclet supports.
   */
  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latest();
  }
  
  /**
   * A main entry point of this doclet.
   *
   * On a call of this method, the doclet generates markdown based document.
   * <!--- @formatter:off --->
   * ```java
   * @Retention(RUNTIME)
   * public class Main {
   *   public static void main(String... args) {
   *     System.out.println("Hello!);
   *   }
   * }
   * ```
   * <!--- @formatter:on --->
   *
   * @param docEnv from which essential information can be extracted
   * @return {@code true} on success.
   */
  @Override
  public boolean run(DocletEnvironment docEnv) {
    var utils = docEnv.getElementUtils();
    var typeDictionary = scanElementsToBuildTypeDictionary(docEnv.getIncludedElements(), utils);
    System.err.println("typeDictionary: " + typeDictionary);
    docEnv.getIncludedElements()
          .forEach(element -> {
            if (element.getKind() == ElementKind.MODULE || element.getKind() == ElementKind.PACKAGE || element instanceof TypeElement) {
              DocTrees docTrees = docEnv.getDocTrees();
              MarkdownPage markdownPage = new MarkdownPage(element,
                                                           docEnv,
                                                           t -> resolveDocumentPathForType(t, typeDictionary))
                  .title(
                      element.getKind(),
                      fullyQualifiedNameOf(element));
              
              if (element instanceof ModuleElement) {
                reedOverview().ifPresent(markdownPage::overview);
              }
              DocCommentTree docCommentTree = docTrees.getDocCommentTree(element);
              if (docCommentTree != null) {
                markdownPage = markdownPage.commentTree(docCommentTree);
              }
              MarkdownPage finalMarkdownPage = markdownPage;
              element.getEnclosedElements()
                     .stream()
                     .filter(e -> e.getModifiers()
                                   .contains(PUBLIC) || e.getModifiers()
                                                         .contains(PROTECTED) || e instanceof PackageElement)
                     .filter(this::elementMatchesFilterIfPackage)
                     .forEach(finalMarkdownPage::addChild);
              
              Arrays.stream(markdownPage.renderAsIndexPage()
                                        .split(String.format("%n")))
                    .forEach(this::report);
              
              var moduleName = moduleNameOf(element, utils);
              var moduleDir = new File(this.destinationDirectory, moduleName);
              if (element instanceof TypeElement) {
                var packageName = packageNameOf(element, utils);
                var packageDir = new File(moduleDir, packageName);
                if (packageDir.mkdirs()) {
                  report("PACKAGE DIR: " + packageDir + " was created.");
                }
                markdownPage.writeTo(determineOutputFileFor((TypeElement) element, packageDir));
              } else if (element instanceof PackageElement) {
                var packageName = packageNameOf(element, utils);
                var packageDir = new File(moduleDir, packageName);
                if (packageDir.mkdirs()) {
                  report("PACKAGE DIR: " + packageDir + " was created.");
                }
                markdownPage.writeTo(new File(packageDir, "README.md"));
              } else if (element instanceof ModuleElement) {
                if (moduleDir.mkdirs()) {
                  report("MODULE DIR: " + moduleDir + " was created.");
                }
                markdownPage.writeTo(new File(moduleDir, "README.md"));
              }
            }
          });
    this.report("Bye");
    return true;
  }
  
  private boolean elementMatchesFilterIfPackage(Element e) {
    return !(e instanceof PackageElement packageElement) || this.packageFilter.test(
        (ModuleElement) packageElement.getEnclosingElement(), packageElement);
  }
  
  
  private String resolveDocumentPathForType(String t, Map<String, String> typeDictionary) {
    var poundSignPosition = t.indexOf("#");
    var typeName = t.substring(0, poundSignPosition < 0
                                  ? t.length()
                                  : poundSignPosition);
    return typeDictionary.containsKey(typeName)
           ? String.format("%s%s",
                           this.basePath,
                           typeDictionary.get(typeName))
           : "unknownType.md";
  }
  
  private static Map<String, String> scanElementsToBuildTypeDictionary(Set<? extends Element> includedElements, Elements utils) {
    return includedElements.stream()
                           .filter(element -> element instanceof TypeElement)
                           .map(element -> (TypeElement) element)
                           .collect(Collectors.toMap(e -> getSimpleNameContainingEnclosingClasses(e),
                                                     typeElement -> docLocationFromBasePath(typeElement, utils),
                                                     (k1, k2) -> {
                                                       System.err.println("WARNING: '" + k2 + "' is discarded because there is already an entry '" + k1 + "'");
                                                       return k1;
                                                     }
                                                    ));
  }
  
  private static String getSimpleNameContainingEnclosingClasses(TypeElement e) {
    return toSimpleNameContainingEnclosingClasses("",
                                                  e,
                                                  e.getEnclosingElement());
  }
  
  private static String toSimpleNameContainingEnclosingClasses(String cur, TypeElement e, Element enclosingElement) {
    if (enclosingElement instanceof PackageElement)
      return cur + e.getSimpleName()
                    .toString();
    else
      return toSimpleNameContainingEnclosingClasses(enclosingElement.getSimpleName() + "." + cur,
                                                    e,
                                                    enclosingElement.getEnclosingElement());
  }
  
  private static String docLocationFromBasePath(TypeElement typeElement, Elements utils) {
    return String.format("%s/%s/%s",
                         moduleNameOf(typeElement, utils),
                         packageNameOf(typeElement, utils),
                         typeNameOf(typeElement));
  }
  
  private Optional<String> reedOverview() {
    return Optional.ofNullable(this.overviewFile)
                   .map(MdDoclet::readStringFromFile);
  }
  
  private static String readStringFromFile(File f) {
    StringBuilder sb = new StringBuilder();
    try (var reader = new BufferedReader(new InputStreamReader(new FileInputStream(f)))) {
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line);
        sb.append(System.lineSeparator());
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return sb.toString();
  }
  
  private static File determineOutputFileFor(TypeElement element, File packageDir) {
    String typeName = typeNameOf(element);
    return new File(packageDir, typeName + ".md");
  }
  
  public static String typeNameOf(TypeElement element) {
    List<TypeElement> enclosingClasses = new ArrayList<>();
    enclosingClasses.add(element);
    Element enclosingElement;
    Element cur = element;
    while ((enclosingElement = cur.getEnclosingElement()) instanceof TypeElement) {
      enclosingClasses.addFirst((TypeElement) enclosingElement);
      cur = enclosingElement;
    }
    return enclosingClasses.stream()
                           .map(Element::getSimpleName)
                           .collect(Collectors.joining("."));
  }
  
  private static String fullyQualifiedNameOf(Element element) {
    if (element instanceof TypeElement typeElement) {
      return typeElement.getQualifiedName()
                        .toString();
    } else if (element instanceof PackageElement packageElement) {
      return packageElement.getQualifiedName()
                           .toString();
    } else if (element instanceof ModuleElement moduleElement) {
      var moduleName = moduleElement.getQualifiedName()
                                    .toString();
      return !moduleName.isEmpty()
             ? moduleName
             : "unnamed";
    }
    return element.toString();
  }
  
  static String packageNameOf(Element element, Elements utils) {
    return Optional.ofNullable(utils.getPackageOf(element))
                   .map((PackageElement v) -> Objects.toString(v.getQualifiedName()))
                   .orElse("(none)");
  }
  
  private static String moduleNameOf(Element element, Elements utils) {
    return Optional.ofNullable(utils.getModuleOf(element))
                   .map((ModuleElement v) -> Objects.toString(v.getQualifiedName()))
                   .orElse("(none)");
  }
  
  private void report(String message) {
    this.reporter.print(Diagnostic.Kind.NOTE, message);
  }
}
