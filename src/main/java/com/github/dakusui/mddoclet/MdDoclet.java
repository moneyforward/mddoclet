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
import java.io.File;
import java.util.*;

import static com.github.dakusui.mddoclet.MdDocletOptions.createOption;

/**
 * A Doclet, that produces "markdown" files, not HTML files.
 */
public class MdDoclet implements Doclet {
  
  private Reporter reporter;
  private File overviewFile = null;
  private File destinationDirectory = new File(".");
  
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
            if (element.getKind() == ElementKind.MODULE || element.getKind() == ElementKind.PACKAGE || element.getKind() == ElementKind.CLASS) {
              /*
              reportElement(element, "- element", utils, docEnv);
              element.getEnclosedElements()
                     .forEach(each -> reportElement(each, "  - child", utils, docEnv));
                     
               */
              DocTrees docTrees = docEnv.getDocTrees();
              MarkdownPage markdownPage = new MarkdownPage(docEnv).title(element.getKind(),
                                                                         fullyQualifiedNameOf(element));
              
              DocCommentTree docCommentTree = docTrees.getDocCommentTree(element);
              if (docCommentTree != null) {
                markdownPage = markdownPage.commentTree(docCommentTree);
              }
              MarkdownPage finalMarkdownPage = markdownPage;
              element.getEnclosedElements()
                     .forEach(finalMarkdownPage::addChild);
              
              report("FILE: " + this.destinationDirectory + "/test.md");
              Arrays.stream(markdownPage.renderAsTypePage()
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
                markdownPage.writeTo(new File(packageDir, element.getSimpleName() + ".md"));
              } else if (element instanceof PackageElement) {
                var packageName = packageNameOf(element, utils);
                var packageDir = new File(moduleDir, packageName);
                if (packageDir.mkdirs()) {
                  report("PACKAGE DIR: " + packageDir + " was created.");
                }
                markdownPage.writeTo(new File(packageDir, "index.md"));
              } else if (element instanceof ModuleElement) {
                if (moduleDir.mkdirs()) {
                  report("MODULE DIR: " + moduleDir + " was created.");
                }
                markdownPage.writeTo(new File(moduleDir, "index.md"));
              }
            }
          });
    this.report("Bye");
    return true;
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
  
  private void reportElement(Element element, String heading, Elements utils, DocletEnvironment docletEnvironment) {
    var comment = utils.getDocComment(element);
    if (comment != null) {
      Arrays.stream(comment.split("\n"))
            .forEach(c -> this.report(heading + ":<" +
                                          element.getKind() + ":" +
                                          docletEnvironment.getDocTrees() + ":" +
                                          packageNameOf(element, utils) + ":" +
                                          c + ">"));
    } else {
      this.report(heading + ":<" + element.getKind() + ":" + packageNameOf(element, utils) + ">");
    }
    var commentTree = docletEnvironment.getDocTrees()
                                       .getDocCommentTree(element);
    if (commentTree != null) {
      //this.report(Objects.toString(commentTree.getBlockTags()));
      this.report("first:    <" + commentTree.getFirstSentence() + ">");
      this.report("body:     <" + commentTree.getBody() + ">");
      this.report("fullBody: <" + commentTree.getFullBody() + ">");
      this.report("postamble:<" + commentTree.getPostamble() + ">");
      this.report("preamble: <" + commentTree.getPreamble() + ">");
      this.report("blocktags:<" + Optional.ofNullable(commentTree.getBlockTags())
                                          .map(s -> s.stream()
                                                     .map(t -> String.format("<%s:%s>", t.getKind(), t))
                                                     .toList()
                                                     .toString())
                                          .orElse("(empty)") + ">");
    }
  }
  
  
  private static String packageNameOf(Element element, Elements utils) {
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
