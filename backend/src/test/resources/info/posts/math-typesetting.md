---
title: "Math Typesetting"
date: 2023-07-10
authors: [Lone Coder]
categories: [Theme Demo, Syntax]
tags: [Hugo, Markdown, Css, Html]
---

Mathematical notation in a Hugo project can be enabled by using third party JavaScript libraries.


In this example we will be using [KaTeX](https://katex.org/)

-   Create a partial under `/layouts/partials/math.html`
-   Within this partial reference the [Auto-render Extension](https://katex.org/docs/autorender.html) or host these scripts locally.
-   Include the partial in your templates like so:

```bash
{{ if or .Params.math .Site.Params.math }}
{{ partial "math.html" . }}
{{ end }}
```

-   To enable KaTeX globally set the parameter `math` to `true` in a project's configuration
-   To enable KaTeX on a per page basis include the parameter `math: true` in content files

**Note:** Use the online reference of [Supported TeX Functions](https://katex.org/docs/supported.html)

### Examples

Inline math: \(\varphi = \dfrac{1+\sqrt5}{2}= 1.6180339887…\)

Block math:

$$
 \varphi = 1+\frac{1} {1+\frac{1} {1+\frac{1} {1+\cdots} } }
$$
