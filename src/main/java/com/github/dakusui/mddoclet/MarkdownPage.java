package com.github.dakusui.mddoclet;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.util.DocTrees;
import jdk.javadoc.doclet.DocletEnvironment;

import javax.lang.model.element.*;
import java.io.*;
import java.util.*;
import java.util.function.Function;

import static com.github.dakusui.mddoclet.MdDoclet.packageNameOf;
import static com.github.dakusui.mddoclet.MdDoclet.typeNameOf;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

public class MarkdownPage {
  public static final String LINEBREAK = "__MDDOCLET_LINEBREAK__";
  private final PageStyle pageStyle;
  private final Element targetElement;
  private String overview = null;
  
  
  enum PageStyle {
    TYPE {
      @Override
      String render(MarkdownPage p) {
        return p.renderAsTypePage();
      }
    },
    INDEX {
      @Override
      String render(MarkdownPage p) {
        return p.renderAsIndexPage();
      }
    };
    
    abstract String render(MarkdownPage p);
  }
  
  private final DocletEnvironment docletEnvironment;
  private String body;
  
  private List<? extends DocTree> tags = new ArrayList<>();
  private final List<Element> children = new ArrayList<>();
  private String title;
  private final Function<String, String> docResolver;
  
  MarkdownPage(Element targetElement, DocletEnvironment docletEnvironment, Function<String, String> docResolver) {
    this.pageStyle = pageStyleFor(targetElement);
    this.targetElement = targetElement;
    this.docletEnvironment = docletEnvironment;
    this.docResolver = docResolver;
  }
  
  public MarkdownPage title(ElementKind kind, String name) {
    this.title = String.format("%s: `%s`", kind, name);
    return this;
  }
  
  public MarkdownPage commentTree(DocCommentTree docCommentTree) {
    this.body = extractCommentBody(docCommentTree);
    this.tags = docCommentTree.getBlockTags();
    return this;
  }
  
  @SuppressWarnings("UnusedReturnValue")
  public MarkdownPage overview(String overview) {
    this.overview = overview;
    return this;
  }
  
  private static Tag createTag(DocTree blockTagDocTree) {
    return Tag.create(tagNameOf(blockTagDocTree), tagValueOf(blockTagDocTree));
  }
  
  private static String tagNameOf(DocTree blockTagDocTree) {
    String s = blockTagDocTree.toString();
    int i = s.indexOf(" ");
    return i >= 0
           ? s.substring(0, i)
           : "@unknown";
  }
  
  private static String tagValueOf(DocTree blockTagDocTree) {
    String s = blockTagDocTree.toString();
    int i = s.indexOf(" ");
    return i >= 0
           ? s.substring(i)
           : "(t.b.d.)";
  }
  
  /**
   * Render this object as a markdown page for a type documentation.
   *
   * @return A rendered content of the page that this object represents
   */
  public String renderAsTypePage() {
    StringBuilder sb = new StringBuilder().append(renderCommonPart());
    DocTrees docTrees = docletEnvironment.getDocTrees();
    this.children.stream()
                 .sorted(Comparator.comparing(Element::getKind)
                                   .thenComparing(o -> o.getSimpleName()
                                                        .toString()))
                 .filter(e -> Set.of(ElementKind.METHOD, ElementKind.CONSTRUCTOR, ElementKind.FIELD)
                                 .contains(e.getKind()))
                 .peek((Element element) -> {
                   if (element instanceof ExecutableElement executableElement) {
                     sb.append(renderAnchorForExecutableElement(executableElement));
                     sb.append(renderSectionTitleForExecutableElement(executableElement));
                   } else if (element instanceof VariableElement variableElement) {
                     sb.append(renderAnchorForVariableElement(variableElement));
                     sb.append(renderSectionTitleForVariableElement(element, variableElement));
                   }
                 })
                 .map(docTrees::getDocCommentTree)
                 .peek(tree -> {
                   if (tree == null) {
                     sb.append(String.format("%nt.b.d.%n%n"));
                   }
                 })
                 .filter(Objects::nonNull)
                 .forEach((DocCommentTree t) -> {
                   sb.append(String.format("%n"));
                   sb.append(extractCommentBody(t));
                   sb.append(String.format("%n"));
                   sb.append(String.format("%n"));
                   
                   t.getBlockTags()
                    .forEach((DocTree blockTagDocTree) -> renderTag(sb, createTag(blockTagDocTree), this.docResolver));
                   sb.append(String.format("%n"));
                 });
    return sb.toString();
  }
  
  private static String extractCommentBody(DocCommentTree t) {
    // This is a limitation, where @see,@param,@link,@return inside a code block cannot be rendered.
    // Also, a multi-line text after these cannot be handled properly.
    return decodeUnicodeEscapes(Objects.toString(t)
                                       .replace("\n", LINEBREAK))
        .replaceAll(LINEBREAK, String.format("%n"))
        .replaceAll("@(see|param|link|return)[ \t]+.+", "");
  }
  
  private static String decodeUnicodeEscapes(String input) {
    Properties props = new Properties();
    try {
      props.load(new StringReader("key=" + input));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return props.getProperty("key");
  }
  
  private static String encodeToUnicodeEscapes(String input) {
    StringBuilder escapedString = new StringBuilder();
    for (char ch : input.toCharArray()) {
      if (ch >= 128) { // Check if the character is outside the ASCII range
        escapedString.append(String.format("\\u%04x", (int) ch));
      } else {
        escapedString.append(ch); // Append regular ASCII characters normally
      }
    }
    return escapedString.toString();
  }
  
  private static String renderAnchorForVariableElement(VariableElement variableElement) {
    return String.format("<a id=\"%s\"></a>%n", variableElement.getSimpleName());
  }
  
  private static String renderSectionTitleForVariableElement(Element element, VariableElement variableElement) {
    return String.format("## **%s:** %s%n", variableElement.getKind(), element.getSimpleName()
                                                                              .toString());
  }
  
  private static String renderAnchorForExecutableElement(ExecutableElement element) {
    return String.format("<a id=\"%s\"></a>%n", methodNameOf(element));
  }
  
  private static String renderSectionTitleForExecutableElement(ExecutableElement element) {
    return String.format("## **%s:** `%s` %s(%s)%n",
                         element.getKind(),
                         returnTypeOf(element),
                         methodNameOf(element),
                         element.getParameters()
                                .stream()
                                .map(p -> new String[]{simpleTypeOf(p), nameOf(p)})
                                .map(p -> String.format("`%s` `%s`", p[0], p[1]))
                                .collect(joining(", ")));
  }
  
  private static MarkdownPage.PageStyle pageStyleFor(Element element) {
    return element instanceof TypeElement
           ? MarkdownPage.PageStyle.TYPE
           : MarkdownPage.PageStyle.INDEX;
  }
  
  private static String returnTypeOf(ExecutableElement element) {
    String returnType;
    if (element.getKind() != ElementKind.CONSTRUCTOR) {
      returnType = String.format("%s", simpleReturnTypeOf(element));
    } else {
      returnType = "(none)";
    }
    return returnType;
  }
  
  private static String nameOf(VariableElement p) {
    return p.getSimpleName()
            .toString();
  }
  
  private static String simpleTypeOf(VariableElement p) {
    return p.asType()
            .toString()
            .replaceAll("[a-z0-9_]+\\.", "");
  }
  
  private static String simpleReturnTypeOf(ExecutableElement executableElement) {
    return executableElement.getReturnType()
                            .toString()
                            .replaceAll("[a-z0-9_]+\\.", "");
  }
  
  private static String methodNameOf(Element c) {
    String name;
    if (c.getKind() == ElementKind.CONSTRUCTOR) {
      name = "&lt;&lt;init&gt;&gt;";
    } else {
      name = Objects.toString(c.getSimpleName());
    }
    return name;
  }
  
  public String renderAsIndexPage() {
    StringBuilder sb = new StringBuilder();
    sb.append(renderCommonPart());
    
    sb.append(String.format("# Enclosed Elements%n"));
    for (Element element : children) {
      if (!Objects.equals(this.targetElement, element.getEnclosingElement()))
        continue;
      if (element instanceof TypeElement typeElement) {
        sb.append(String.format("- **%s:** [%s](%s.md)%n",
                                element.getKind(),
                                typeNameOf(typeElement),
                                typeNameOf(typeElement)));
      } else if (element instanceof PackageElement) {
        sb.append(String.format("- **%s:** [%s](%s/)%n",
                                element.getKind(),
                                packageNameOf(element, docletEnvironment.getElementUtils()),
                                packageNameOf(element, docletEnvironment.getElementUtils())));
      } else {
        System.err.println("Ignoring unknown element: " + element);
      }
    }
    sb.append(String.format("%n"));
    
    return sb.toString();
  }
  
  private static void renderTag(StringBuilder sb, Tag tag, Function<String, String> docResolver) {
    if (tag.tagType != Tag.Type.UNKNOWN)
      sb.append(String.format("+ **%s:** %s%n", tag.tagType(), tag.tagValue(docResolver)));
  }
  
  
  private String renderCommonPart() {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("# %s%n%n", title));
    if (this.overview != null)
      sb.append(String.format("%s%n%n", overview));
    if (this.body != null)
      sb.append(String.format("%s%n%n", this.body));
    sb.append(String.format("%n"));
    sb.append(renderTags(this.tags, this.docResolver));
    sb.append(String.format("%n"));
    return sb.toString();
  }
  
  private static String renderTags(List<? extends DocTree> tags, Function<String, String> docResolver) {
    StringBuilder sb = new StringBuilder();
    for (Tag tag : tags.stream()
                       .map(MarkdownPage::createTag)
                       .toList()) {
      renderTag(sb, tag, docResolver);
    }
    return sb.toString();
  }
  
  @SuppressWarnings("UnusedReturnValue")
  public MarkdownPage addChild(Element childElement) {
    this.children.add(childElement);
    return this;
  }
  
  public void writeTo(File outputFile) {
    try (var ow = new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(outputFile)), UTF_8)) {
      ow.write(this.pageStyle.render(this));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  record Tag(Tag.Type tagType, String tagValue) {
    enum Type {
      LINK {
        @Override
        String format(String value, Function<String, String> docResolver) {
          return "[here](" + value + ")";
        }
      },
      SEE {
        @Override
        String format(String value, Function<String, String> docResolver) {
          return "[`" + value + "`](" + docResolver.apply(value) + ")";
        }
      },
      PARAM {
        @Override
        String format(String value, Function<String, String> docResolver) {
          value = value.replaceAll(" +", " ")
                       .replaceAll("^ +", "");
          String ret = "";
          Optional<String> firstToken = firstTokenOf(value);
          if (firstToken.isPresent()) {
            ret = ret + "`" + firstToken.get() + "`";
          } else {
            ret = ret + "t.b.d.";
            return ret;
          }
          if (!value.contains(" "))
            return ret + " t.b.d.";
          return ret + " " + value.substring(value.indexOf(" "));
        }
        
        private Optional<String> firstTokenOf(String value) {
          if (value == null || value.isEmpty())
            return Optional.empty();
          int delimiterIndex = value.indexOf(" ");
          if (delimiterIndex < 0)
            return Optional.empty();
          return Optional.of(value.substring(0, delimiterIndex));
        }
      },
      RETURN {
        @Override
        String format(String value, Function<String, String> docResolver) {
          return value;
        }
      },
      UNKNOWN {
        @Override
        String format(String value, Function<String, String> docResolver) {
          return "(doc contains some error)";
        }
      };
      
      abstract String format(String value, Function<String, String> docResolver);
    }
    
    public String tagValue(Function<String, String> docResolver) {
      return this.tagType()
                 .format(this.tagValue, docResolver);
    }
    
    public static Tag create(String tagName, String tagValue) {
      return new Tag(tagNameToType(tagName), (tagValue != null
                                              ? tagValue
                                              : "").trim());
    }
    
    private static Type tagNameToType(String tagName) {
      if (tagName == null)
        return Type.UNKNOWN;
      return switch (tagName) {
        case "@link" -> Type.LINK;
        case "@see" -> Type.SEE;
        case "@param" -> Type.PARAM;
        case "@return" -> Type.RETURN;
        default -> Type.UNKNOWN;
      };
    }
  }
  
}
