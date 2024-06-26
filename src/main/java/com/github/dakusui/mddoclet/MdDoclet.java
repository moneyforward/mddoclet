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
import java.util.function.Predicate;
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
    return Set.of(
        createOption("-d",
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
                         report("Specified destination " + destinationDirectory +
                                    " is not a directory: " + destinationDirectory);
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
                       report("Relative path from the site URL to the document root is set to " + args.getFirst());
                       MdDoclet.this.basePath = args.getFirst();
                       return true;
                     }));
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
   *
   * @param docEnv from which essential information can be extracted
   * @return {@code true} on success.
   */
  @Override
  public boolean run(DocletEnvironment docEnv) {
    this.report("Hello, I'm a Markdown Doclet!");
    var utils = docEnv.getElementUtils();
    docEnv.getIncludedElements()
          .forEach(element -> {
            if (element.getKind() == ElementKind.MODULE || element.getKind() == ElementKind.PACKAGE || element instanceof TypeElement) {
              /*
              reportElement(element, "- element", utils, docEnv);
              element.getEnclosedElements()
                     .forEach(each -> reportElement(each, "  - child", utils, docEnv));
                     
               */
              DocTrees docTrees = docEnv.getDocTrees();
              MarkdownPage markdownPage = new MarkdownPage(element instanceof TypeElement
                                                           ? MarkdownPage.PageStyle.TYPE
                                                           : MarkdownPage.PageStyle.INDEX, docEnv).title(
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
    } else if (element instanceof PackageElement) {
      return ((PackageElement) element).getQualifiedName()
                                       .toString();
    } else if (element instanceof ModuleElement moduleElement) {
      return ((ModuleElement) element).getQualifiedName()
                                      .toString();
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
