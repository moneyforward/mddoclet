# The `mddoclet`

## Directory Layout

```
{base}
  +-index.md
  `-module.name
      `-fully.qualified.package.name
        +-index.md
        +-ClassName.md
        `-ClassName.md
```

- [ClassName1](/module.name/fully.qualified.package.name/ClassName1.md]
- [ClassName1](ClassName1.md)

## ClassName.md

```markdown
# CLASS: `fully.qualified.package.name.ClassName1`

{Class Level Comment}

## Parameters

- {Class Level Tag @param}
- {Class Level Tag @param}
- {Class Level Tag @param}

## See also

- {Class Level Tag @see}
- {Class Level Tag @see}
- {Class Level Tag @see}

## Others

- {Class Level Tag "unknown"}
- {Class Level Tag "unknown"}
- {Class Level Tag "unknown"}

## Members

### Fields

### Methods

- 


```
