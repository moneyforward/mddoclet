# mddoclet: Doclet generating Markdown files

`mddoclet` is a very simple Doclet that generates documents from JavaDoc comments in Markdown format.
It is suitable for MoneyForward's backstage!

<img width="1462" alt="Screenshot 2024-12-07 at 15 39 18" src="https://github.com/user-attachments/assets/e7090c11-5de5-410b-816e-08e6f73d083f">

Here is a rendered example of it:

```markdown

# CLASS: `com.github.dakusui.mddoclet.example.ExampleClass`

Hello, I am the first example class.

Hallo!
こんにちは! How are you?
I am implementing `ExampleInterface`.

@link

+ **SEE:
  ** [`ExampleInterface`](/Users/ukai.hiroshi/Documents/github/moneyforward/mddoclet/target/classes/JavaMarkdown//com.github.dakusui.mddoclet.example/ExampleInterface)

<a id="exampleField2"></a>

## **FIELD:** exampleField2

An example `int` field.

<a id="exampleMethod1"></a>

## **METHOD:** `String` exampleMethod1()

This is a method to return `field1`.

+ **RETURN:** a string value

```

## Usage

Include following fragment in your `pom.xml` under `.project.build.plugins`.

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-javadoc-plugin</artifactId>
    <version>${maven-javadoc-plugin.version}</version>
    <configuration>
        <doclet>com.github.dakusui.mddoclet.MdDoclet</doclet>
        <docletArtifact>
            <groupId>com.github.moneyforward</groupId>
            <artifactId>mddoclet</artifactId>
            <version>${mddoclet-doclet-plugin.version}</version>
        </docletArtifact>
        <useStandardDocletOptions>false</useStandardDocletOptions>
        <additionalOptions>
            <additionalOption>-overview ${project.basedir}/src/main/javadoc/overview.md</additionalOption>
            <additionalOption>-d ${project.build.outputDirectory}/JavaMarkdown</additionalOption>
            <additionalOption>--source-path ${project.build.sourceDirectory}</additionalOption>
            <additionalOption>-base-path /docs/default/Component/autotest-ca/3-APISpecification</additionalOption>
            <additionalOption>-target-packages '.*#.*example.*'</additionalOption>
        </additionalOptions>
    </configuration>
    <executions>
        <execution>
            <id>attach-javadocs</id>
            <phase>pre-site</phase>
            <goals>
                <goal>jar</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Do not forget you need to define the property: `mddoclet-doclet-plugin.version`.

Then, do `mvn clean package`.

It will generate documentation set generated from your JavaDoc comment for your classes whose pacakge name contains `example` (`--target-packages`).
The generated files will be found under `target/classes/JavaMarkdown` (`-d`).
Absolute links to `.md`  generated by this doclet from your Java source files will have `/docs/default/Component/autotest-ca/3-APISpecifications` before the path from `src/main/java` (`-base-path`). 

You can learn how you can configure it and how generated looks like from the [insdog](https://backstage.test.musubu.co.in/catalog/default/component/insdog) project.

* [backstage doc](https://backstage.test.musubu.co.in/catalog/default/component/insdog/docs/3-APISpecification/)
* [pom.xml](https://github.com/moneyforward/insdog/blob/develop/pom.xml)

Enjoy!
