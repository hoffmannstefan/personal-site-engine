---
title: "HTML and CSS only tabs"
date: 2023-07-09
authors: [Lone Coder]
categories: [Theme Demo, Syntax]
tags: [Hugo, Markdown, Css, Html, Shortcodes]
---

## Shortcodes

The following content:

```markdown
{{< tabgroup >}}
{{< tab name="Hello" >}}
Hello World!
{{< /tab >}}

{{< tab name="Goodbye" >}}
Goodbye Everybody!
{{< /tab >}}
{{< /tabgroup >}}
```

Will generate:

<div class="tabs tabs-left">
<style>
  .tabs input#tab-0-0:checked ~ .tab-content-0-0 {
    display: block;
  }
</style>
<input type="radio" class="tab-input" name="tab-select-0" id="tab-0-0" checked/>
<label for="tab-0-0" class="tab-label">Hello</label>
<div class="tab-content tab-content-0-0">

Hello World!

</div>
<style>
  .tabs input#tab-0-1:checked ~ .tab-content-0-1 {
    display: block;
  }
</style>
<input type="radio" class="tab-input" name="tab-select-0" id="tab-0-1" />
<label for="tab-0-1" class="tab-label">Goodbye</label>
<div class="tab-content tab-content-0-1">

Goodbye Everybody!

</div>
</div>


## Right alignment

You can also align the tabs to the right:

```markdown
{{< tabgroup align="right" >}}
{{< tab name="Hello" >}}
Hello World!
{{< /tab >}}

{{< tab name="Goodbye" >}}
Goodbye Everybody!
{{< /tab >}}
{{< /tabgroup >}}
```

Giving you this look:

<div class="tabs tabs-right">
<style>
  .tabs input#tab-1-0:checked ~ .tab-content-1-0 {
    display: block;
  }
</style>
<input type="radio" class="tab-input" name="tab-select-1" id="tab-1-0" checked/>
<label for="tab-1-0" class="tab-label">Hello</label>
<div class="tab-content tab-content-1-0">

Hello World!

</div>
<style>
  .tabs input#tab-1-1:checked ~ .tab-content-1-1 {
    display: block;
  }
</style>
<input type="radio" class="tab-input" name="tab-select-1" id="tab-1-1" />
<label for="tab-1-1" class="tab-label">Goodbye</label>
<div class="tab-content tab-content-1-1">

Goodbye Everybody!

</div>
</div>


## Markdown content

Any valid markdown can be used inside the tab:

````markdown
{{< tabgroup align="right" style="code" >}}
{{< tab name="Ruby" >}}

```ruby
puts 'Hello'
```

{{< /tab >}}
{{< tab name="Python" >}}

```python
print('Hello')
```

{{< /tab >}}
{{< tab name="JavaScript" >}}

```js
console.log("Hello");
```

{{< /tab >}}
{{< /tabgroup >}}
````

And you get this lovely content:

<div class="tabs tabs-code tabs-right">
<style>
  .tabs input#tab-2-0:checked ~ .tab-content-2-0 {
    display: block;
  }
</style>
<input type="radio" class="tab-input" name="tab-select-2" id="tab-2-0" checked/>
<label for="tab-2-0" class="tab-label">Ruby</label>
<div class="tab-content tab-content-2-0">

```ruby
puts 'Hello'
```

</div>
<style>
  .tabs input#tab-2-1:checked ~ .tab-content-2-1 {
    display: block;
  }
</style>
<input type="radio" class="tab-input" name="tab-select-2" id="tab-2-1" />
<label for="tab-2-1" class="tab-label">Python</label>
<div class="tab-content tab-content-2-1">

```python
print('Hello')
```

</div>
<style>
  .tabs input#tab-2-2:checked ~ .tab-content-2-2 {
    display: block;
  }
</style>
<input type="radio" class="tab-input" name="tab-select-2" id="tab-2-2" />
<label for="tab-2-2" class="tab-label">JavaScript</label>
<div class="tab-content tab-content-2-2">

```js
console.log("Hello");
```

</div>
</div>


In this case `style="code"` makes it look a little nicer for scenarios where
your content is purely a code block.
