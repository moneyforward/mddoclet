package com.github.dakusui.mddoclet;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.util.DocTrees;
import jdk.javadoc.doclet.DocletEnvironment;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.util.Elements;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MarkdownPage {
  
  private final DocletEnvironment docletEnvironment;
  private String body;
  
  private List<Tag> tags = new ArrayList<>();
  private List<Element> children = new ArrayList<>();
  private String title;
  
  public MarkdownPage(DocletEnvironment docletEnvironment) {
    this.docletEnvironment = docletEnvironment;
  }
  
  public MarkdownPage title(ElementKind kind, String name) {
    this.title = String.format("%s: `%s`", kind, name);
    return this;
  }
  
  public MarkdownPage commentTree(DocCommentTree docCommentTree) {
    this.body = docCommentTree.getFullBody()
                              .toString();
    this.tags = docCommentTree.getBlockTags()
                              .stream()
                              .map(MarkdownPage::createTag)
                              .toList();
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
                 .peek(c -> sb.append("## **")
                              .append(c.getKind())
                              .append("**: `")
                              .append(c.getSimpleName())
                              .append(String.format("`%n")))
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
                    .forEach((DocTree dct) -> {
                      var tag = createTag(dct);
                      sb.append("+ **");
                      sb.append(tag.tagType());
                      sb.append("**: ");
                      sb.append(tag.tagValue());
                      sb.append(String.format("%n"));
                    });
                 });
    return sb.toString();
  }
  
  
  public String renderIndexPage() {
    return renderCommonPart();
  }
  
  private String renderCommonPart() {
    StringBuilder sb = new StringBuilder();
    sb.append("# ")
      .append(title)
      .append("\n")
      .append("\n");
    if (this.body != null)
      sb.append(this.body)
        .append("\n")
        .append("\n");
    
    sb.append(renderTags(this.tags));
    return sb.toString();
  }
  
  private static String renderTags(List<Tag> tags1) {
    StringBuilder sb = new StringBuilder();
    for (Tag tag : tags1) {
      sb.append("- `")
        .append(tag.tagType())
        .append("`")
        .append(" ")
        .append(tag.tagValue())
        .append("\n");
    }
    return sb.toString();
  }
  
  
  public MarkdownPage addChild(Element childElement) {
    this.children.add(childElement);
    return this;
  }
  
  public void writeTo(File outputFile) {
    try (var ow = new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(outputFile)))) {
      ow.write(renderAsTypePage());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  record Tag(Tag.Type tagType, String tagValue) {
    enum Type {
      LINK,
      SEE,
      PARAM,
      RETURN,
      UNKNOWN
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
