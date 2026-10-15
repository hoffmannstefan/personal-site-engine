---
title: "Mermaid JS support"
date: 2023-07-08
authors: [Lone Coder]
categories: [Theme Demo, Syntax]
tags: [Hugo, Markdown, Css, Html]
---

If you want to use [Mermaid-JS](https://mermaid-js.github.io/mermaid/#/) on your website.
Provide `mermaid` as [Shortcode](https://gohugo.io/content-management/shortcodes/#readout) in your markdown file.

```mermaid
flowchart LR

    A --> B

    B --> C

    C --> D

    D --> B
```

```mermaid
sequenceDiagram
participant Alice
participant Bob
Alice->>Bob: Hi Bob
Bob->>Alice: Hi Alice
```

Find more example on [Mermaid-JS](https://mermaid-js.github.io/mermaid/#/) website.
