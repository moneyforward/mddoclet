package com.github.dakusui.mddoclet;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.util.DocTrees;
import jdk.javadoc.doclet.DocletEnvironment;

import javax.lang.model.element.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.github.dakusui.mddoclet.MdDoclet.packageNameOf;
import static com.github.dakusui.mddoclet.MdDoclet.typeNameOf;
import static java.util.stream.Collectors.joining;

public class MarkdownPage {
  private final PageStyle pageStyle;
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
  
  MarkdownPage(PageStyle pageStyle, DocletEnvironment docletEnvironment) {
    this.pageStyle = pageStyle;
    this.docletEnvironment = docletEnvironment;
  }
  
  public MarkdownPage title(ElementKind kind, String name) {
    this.title = String.format("%s: `%s`", kind, name);
    return this;
  }
  
  public MarkdownPage commentTree(DocCommentTree docCommentTree) {
    this.body = docCommentTree.getFullBody()
                              .toString();
    this.tags = docCommentTree.getBlockTags();
    return this;
  }
  
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
           : "(blockTagDocTree.b.d.)";
  }
  
  public String renderAsTypePage() {
    StringBuilder sb = new StringBuilder().append(renderCommonPart());
    DocTrees docTrees = docletEnvironment.getDocTrees();
    this.children.stream()
                 .filter((Element element) -> element instanceof ExecutableElement)
                 .map(element -> (ExecutableElement) element)
                 .peek((ExecutableElement element) -> {
                   sb.append(String.format("<a id=\"%s\"></a>%n", methodNameOf(element)));
                   sb.append(String.format("## **%s:** `%s` %s(%s)%n",
                                           element.getKind(),
                                           returnTypeOf(element),
                                           methodNameOf(element),
                                           element.getParameters()
                                                  .stream()
                                                  .map(p -> new String[]{simpleTypeOf(p), nameOf(p)})
                                                  .map(p -> String.format("`%s` `%s`", p[0], p[1]))
                                                  .collect(joining(", "))));
                 })
                 .map(docTrees::getDocCommentTree)
                 .peek(tree -> {
                   if (tree == null) {
                     sb.append(String.format("%nt.b.d.%n"));
                   }
                 })
                 .filter(Objects::nonNull)
                 .forEach((DocCommentTree t) -> {
                   sb.append(String.format("%n"));
                   t.getFullBody()
                    .forEach((DocTree c) -> sb.append(c));
                   sb.append(String.format("%n"));
                   
                   t.getBlockTags()
                    .forEach((DocTree blockTagDocTre) -> {
                      var tag = createTag(blockTagDocTre);
                      renderTag(sb, tag);
                    });
                   sb.append(String.format("%n"));
                 });
    return sb.toString();
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
      if (element instanceof TypeElement typeElement) {
        sb.append(String.format("- **%s:** [%s](%s.md)%n",
                                element.getKind(),
                                typeNameOf(typeElement),
                                typeNameOf(typeElement)));
      } else if (element instanceof PackageElement) {
        sb.append(String.format("- **%s:** [%s](%s/index.md)%n",
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
  
  private static void renderTag(StringBuilder sb, Tag tag) {
    sb.append(String.format("+ **%s:** %s%n", tag.tagType(), tag.tagValue()));
  }
  
  
  private String renderCommonPart() {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("# %s%n%n", title));
    if (this.overview != null)
      sb.append(String.format("%s%n%n", overview));
    if (this.body != null)
      sb.append(String.format("%s%n%n", this.body));
    
    sb.append(renderTags(this.tags));
    return sb.toString();
  }
  
  private static String renderTags(List<? extends DocTree> tags1) {
    StringBuilder sb = new StringBuilder();
    for (Tag tag : tags1.stream()
                        .map(MarkdownPage::createTag)
                        .toList()) {
      renderTag(sb, tag);
    }
    return sb.toString();
  }
  
  
  public MarkdownPage addChild(Element childElement) {
    this.children.add(childElement);
    return this;
  }
  
  public void writeTo(File outputFile) {
    try (var ow = new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(outputFile)))) {
      ow.write(this.pageStyle.render(this));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  record Tag(Tag.Type tagType, String tagValue) {
    enum Type {
      LINK {
        @Override
        String format(String value) {
          return "[here](" + value + ")";
        }
      },
      SEE {
        @Override
        String format(String value) {
          return "`" + value + "`";
        }
      },
      PARAM {
        @Override
        String format(String value) {
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
        String format(String value) {
          return value;
        }
      },
      UNKNOWN {
        @Override
        String format(String value) {
          return "(doc contains some error)";
        }
      };
      
      abstract String format(String value);
    }
    
    @Override
    public String tagValue() {
      return this.tagType()
                 .format(this.tagValue);
    }
    
    public static Tag create(String tagName, String tagValue) {
      return new Tag(tagNameToType(tagName), tagValue);
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
