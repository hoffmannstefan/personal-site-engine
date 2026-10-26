---
title: "Writing posts in Markdown, with LaTeX"
date: 2026-10-06
authors: [John Doe]
categories: [Theme Demo, Syntax]
tags: [Markdown, LaTeX, MathJax]
---

This post is a plain Markdown file, `markdown-with-latex.md`. The header between the `---` lines is its
*front matter*; everything below is the text. Formulas are written in **LaTeX** and typeset by MathJax, and
the Markdown parser never sees them, so `_`, `*` and `\\` inside a formula are safe.

## Text formatting

Normal Markdown works: *emphasis*, **strong text**, ~~strikethrough~~, `inline code`, a
[link](https://commonmark.org/) and a bare address like https://www.mathjax.org/ that turns into a link.

> A block quote. Mathematics is the art of giving the same name to different things.

1. An ordered list
2. with a formula in an item: $a_1 + a_2 = a_3$
3. and a footnote[^note].

[^note]: Footnotes are collected at the end of the post.

## Inline math

Write a formula between single dollar signs, such as $x_i^2 + y_i^2 = r_i^2$ or $\sqrt{a*b}$.
The LaTeX forms `\(...\)` also work: \(\alpha_{n+1} = \alpha_n + \delta\).

A dollar sign that is *not* math stays one: the book costs $5 and the shipping $10, and a price in
backticks like `$x$` is shown as written, because code is never treated as a formula.

## Display math

Use double dollar signs, on their own lines or in one line:

$$
\int_0^1 x^2 \, dx = \frac{1}{3}
$$

$$\binom{n}{k} = \frac{n!}{k!\,(n-k)!}$$

Several aligned steps, with automatic equation numbers (the `&` and `<` need no escaping here):

\begin{align}
f(x) &= (x + 1)^2 \\
     &= x^2 + 2x + 1 \label{eq:expanded} \\
f(x) &\ge 0 \quad \text{for all } x < \infty
\end{align}

Equation \eqref{eq:expanded} is the expanded form.

## Formulas in tables

A `|` inside a formula would normally split a table cell; here it does not.

| Concept | Formula |
|---|---|
| Conditional probability | $P(A \mid B) = \frac{P(A \cap B)}{P(B)}$ |
| Absolute value | $\lvert x \rvert = \sqrt{x^2}$ |
| Norm | $\|v\|_2 = \sqrt{\sum_i v_i^2}$ |

## Code blocks

Fenced code is shown verbatim, formulas included:

```tex
$$ e^{i\theta} = \cos\theta + i\sin\theta $$
```

```java
String greeting = "Hello, " + name + "!"; // a $ here is just a character
```

## Adding your own posts

Create `my-post.md` with a front matter header in the `posts` folder next to the application (or in
`src/main/resources/content/posts/`) and restart. The file name becomes the address, `/posts/my-post/`.
