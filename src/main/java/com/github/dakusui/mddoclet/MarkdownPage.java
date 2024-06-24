package com.github.dakusui.mddoclet;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.util.DocTrees;
import jdk.javadoc.doclet.DocletEnvironment;

import javax.lang.model.element.*;
import javax.lang.model.util.Elements;
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
    Elements utils = docletEnvironment.getElementUtils();
    this.children.stream()
                 .filter(c -> c.getKind() == ElementKind.METHOD || c.getKind() == ElementKind.CONSTRUCTOR)
                 .peek(c -> {
                   sb.append("## **")
                     .append(c.getKind())
                     .append(":** ");
                   if (c instanceof ExecutableElement executableElement) {
                     if (c.getKind() != ElementKind.CONSTRUCTOR) {
                       sb.append("`")
                         .append(executableElement.getReturnType()
                                                  .toString()
                                                  .replaceAll("[a-z0-9_]+\\.", ""))
                         .append("`");
                     }
                   }
                   if (c.getKind() == ElementKind.CONSTRUCTOR) {
                     sb.append("&lt;&lt;init&gt;&gt;");
                   } else {
                     sb.append(c.getSimpleName());
                   }
                   sb.append("(");
                   if (c instanceof ExecutableElement executableElement) {
                     sb.append(
                         executableElement.getParameters()
                                          .stream()
                                          .map(p -> new String[]{p.asType()
                                                                  .toString()
                                              .replaceAll("[a-z0-9_]+\\.", ""),
                                              p.getSimpleName().toString()})
                                          .map(p -> String.format("`%s` `%s`", p[0], p[1]))
                                          .collect(joining(", ")));
                     sb.append(")")
                       .append(String.format("%n"));
                   }
                 })
                 .map(docTrees::getDocCommentTree)
                 .peek(tree -> {
                   if (tree == null) {
                     sb.append(String.format("%n"));
                     sb.append("t.b.d.");
                     sb.append(String.format("%n"));
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
                 });
    return sb.toString();
  }
  
  public String renderAsIndexPage() {
    StringBuilder sb = new StringBuilder();
    sb.append(renderCommonPart());
    
    sb.append(String.format("# Enclosed Elements%n"));
    for (Element element : children) {
      sb.append("- **");
      sb.append(element.getKind());
      sb.append(":** ");
      if (element instanceof TypeElement typeElement) {
        sb.append("[");
        sb.append(typeNameOf(typeElement));
        sb.append("](");
        sb.append(String.format("%s.md", typeNameOf(typeElement)));
        sb.append(")");
      } else if (element instanceof PackageElement) {
        sb.append("[");
        sb.append(packageNameOf(element, docletEnvironment.getElementUtils()));
        sb.append("](");
        sb.append(String.format("%s/index.md", packageNameOf(element, docletEnvironment.getElementUtils())));
        sb.append(")");
      } else {
        sb.append("Unsupported type of element: **");
        sb.append(element.getKind());
        sb.append(":** ");
        sb.append(element.getSimpleName());
      }
      sb.append(String.format("%n"));
    }
    sb.append(String.format("%n"));
    
    return sb.toString();
  }
  
  private static void renderTag(StringBuilder sb, Tag tag) {
    sb.append("+ **");
    sb.append(tag.tagType());
    sb.append(":** ");
    sb.append(tag.tagValue());
    sb.append(String.format("%n"));
  }
  
  
  private String renderCommonPart() {
    StringBuilder sb = new StringBuilder();
    sb.append("# ")
      .append(title)
      .append("\n")
      .append("\n");
    if (this.overview != null)
      sb.append(overview)
        .append("\n")
        .append("\n");
    if (this.body != null)
      sb.append(this.body)
        .append("\n")
        .append("\n");
    
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
