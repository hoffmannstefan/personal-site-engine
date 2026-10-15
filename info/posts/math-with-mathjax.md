---
title: "Math with MathJax"
date: 2026-10-06
authors: [John Doe]
categories: [Theme Demo, Syntax]
tags: [Math, MathJax, LaTeX]
---

Formulas on this site are typeset by [MathJax](https://www.mathjax.org/). You write them in TeX, and the browser
turns them into crisp vector graphics that scale with the text and follow the light/dark theme. The library is
served by this application itself, so no third-party host is involved, and it is only loaded on posts that contain
formulas.

## Inline math

Wrap a formula in single dollar signs to put it in a sentence: the area of a circle is $A = \pi r^2$, and Euler's
identity $e^{i\pi} + 1 = 0$ ties five constants together. The alternative delimiters \(\alpha + \beta = \gamma\)
work as well.

```tex
The area of a circle is $A = \pi r^2$ and \(\alpha + \beta = \gamma\).
```

## Display math

Double dollar signs (or `\[ ... \]`) give a centered formula on its own line. The quadratic formula:

$$x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}$$

The Gaussian integral, which is a good test for integrals and limits:

$$\int_{-\infty}^{\infty} e^{-x^2}\,dx = \sqrt{\pi}$$

A sum, which also shows how limits move above and below the operator in display mode:

$$\sum_{n=1}^{\infty} \frac{1}{n^2} = \frac{\pi^2}{6}$$

```tex
$$x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}$$

$$\int_{-\infty}^{\infty} e^{-x^2}\,dx = \sqrt{\pi}$$

$$\sum_{n=1}^{\infty} \frac{1}{n^2} = \frac{\pi^2}{6}$$
```

## Aligned equations

Use the `aligned` environment to line up several steps at the `=` sign:

$$\begin{aligned}
(a + b)^2 &= (a + b)(a + b) \\
&= a^2 + ab + ba + b^2 \\
&= a^2 + 2ab + b^2
\end{aligned}$$

Environments such as `equation` and `align` are numbered automatically, and `\label` with `\eqref` lets you refer
back to them, like \eqref{eq:maxwell} below:

\begin{align}
\nabla \cdot \mathbf{E} &= \frac{\rho}{\varepsilon_0} \label{eq:maxwell} \\
\nabla \times \mathbf{B} &= \mu_0 \mathbf{J} + \mu_0 \varepsilon_0 \frac{\partial \mathbf{E}}{\partial t}
\end{align}

## Matrices and cases

$$A = \begin{pmatrix} a & b \\ c & d \end{pmatrix}, \qquad \det A = ad - bc$$

$$|x| = \begin{cases} x & \text{if } x \ge 0, \\ -x & \text{if } x < 0. \end{cases}$$

Fourier transform and Bayes' theorem, for good measure:

$$\hat{f}(\xi) = \int_{-\infty}^{\infty} f(x)\, e^{-2\pi i x \xi}\, dx$$

$$P(A \mid B) = \frac{P(B \mid A)\, P(A)}{P(B)}$$

## Writing math in your own posts

Every post is a Markdown file, and any post that contains a formula loads MathJax automatically: there is nothing to
switch on. You do not need to escape `<`, `&`, `_` or `*` inside formulas, and a plain dollar sign such as in
"costs $5 and $10" stays text. To show a literal dollar sign right next to a formula, write `\$`. Code is skipped,
which is why the TeX source above is displayed as written. See
[Writing posts in Markdown, with LaTeX](/posts/markdown-with-latex/) for the full rules, and the
[list of supported TeX commands](https://docs.mathjax.org/en/v3.2/input/tex/macros/index.html) for everything else.
