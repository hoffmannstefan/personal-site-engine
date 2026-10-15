package com.homepage;

import static org.assertj.core.api.Assertions.assertThat;

import com.homepage.post.MarkdownRenderer;
import com.homepage.post.MathProtector;
import org.junit.jupiter.api.Test;

class MarkdownRendererTest {

    private final MarkdownRenderer renderer = new MarkdownRenderer();

    private String html(String markdown) {
        return renderer.render(markdown).html();
    }

    private String roundTrip(String markdown) {
        MathProtector math = new MathProtector();
        return math.restore(math.protect(markdown));
    }

    // ---- math protection ----

    @Test
    void inlineMathSurvivesEmphasisAndEscapes() {
        String h = html("Let $a_1 * b_2$ and $c_1 * d_2$ be numbers, and *this* is emphasis.");
        assertThat(h).contains("\\(a_1 * b_2\\)").contains("\\(c_1 * d_2\\)").contains("<em>this</em>");
        assertThat(h).doesNotContain("<em>1 * b");
    }

    @Test
    void displayMathAndEnvironmentsAreKeptVerbatimAndHtmlEscaped() {
        String h = html("$$\na < b \\\\ c & d\n$$\n\n\\begin{align}\nx &= 1 \\\\\ny &< 2\n\\end{align}");
        assertThat(h).contains("\\[\na &lt; b \\\\ c &amp; d\n\\]");
        assertThat(h).contains("\\begin{align}\nx &amp;= 1 \\\\\ny &amp;&lt; 2\n\\end{align}");
    }

    @Test
    void bracketAndParenthesisDelimitersAreProtectedFromMarkdownEscapes() {
        String h = html("Inline \\(x_1\\) and display \\[y_2 = 3\\].");
        assertThat(h).contains("\\(x_1\\)").contains("\\[y_2 = 3\\]");
    }

    @Test
    void lonelyDollarsAreNotMath() {
        String h = html("It costs $5 and $10 in total, or $5-$10 each.");
        assertThat(h).contains("costs $5 and $10 in total, or $5-$10 each");
        assertThat(renderer.render("It costs $5 and $10 in total").math()).isFalse();
    }

    @Test
    void aFormulaNeverEndsInsideACodeSpan() {
        MarkdownRenderer.Rendered r = renderer.render("It costs $10, and `$x$` stays code.");
        assertThat(r.html()).contains("costs $10, and <code>$x$</code> stays code");
        assertThat(r.math()).isFalse();
    }

    @Test
    void escapedDollarIsLiteral() {
        MarkdownRenderer.Rendered r = renderer.render("A price of \\$3 and $x$ and \\$4.");
        assertThat(r.html()).contains("price of $3 and").contains("\\(x\\)").contains("and $4.");
        assertThat(r.math()).isTrue();
    }

    @Test
    void codeIsNeverTreatedAsMath() {
        String h = html("Inline `$x_1$ and $y_2$` here.\n\n```tex\n$$ a_1 $$\n```\n");
        assertThat(h).contains("<code>$x_1$ and $y_2$</code>").contains("$$ a_1 $$");
        assertThat(h).doesNotContain("\\(");
        assertThat(renderer.render("`$x$`").math()).isFalse();
    }

    @Test
    void pipesInsideMathDoNotSplitTableCells() {
        String h = html("| a | b |\n|---|---|\n| x | $P(A|B)$ |\n");
        assertThat(h).contains("<td>\\(P(A|B)\\)</td>");
    }

    @Test
    void inlineMathNeverSpansAParagraphBreak() {
        assertThat(html("Cost $5\n\nand $x$ here")).contains("Cost $5").contains("\\(x\\)");
    }

    @Test
    void roundTripWithoutMarkdownKeepsEverythingElseUntouched() {
        String src = "plain text with `code $x$` and $a$ and $$b$$ and \\begin{eq}c\\end{eq} \\\\ end";
        assertThat(roundTrip(src)).isEqualTo("plain text with `code $x$` and \\(a\\) and \\[b\\] and \\begin{eq}c\\end{eq} \\\\ end");
    }

    @Test
    void headingIdsIgnoreFormulas() {
        assertThat(html("## The $x_1$ case")).contains("id=\"the-case\"");
    }

    // ---- rendering ----

    @Test
    void headingsGetTheThemesAnchorLink() {
        String h = html("## Some Heading");
        assertThat(h).contains("<h2 id=\"some-heading\">").contains("class=\"heading-link\" href=\"#some-heading\"");
    }

    @Test
    void fencedCodeUsesTheThemesHighlightMarkup() {
        String h = html("```java\nint a = 1 < 2;\n```");
        assertThat(h).contains("<div class=\"highlight\"><pre tabindex=\"0\" class=\"chroma\"><code class=\"language-java\" data-lang=\"java\">")
                .contains("int a = 1 &lt; 2;");
    }

    @Test
    void supportsTablesStrikethroughFootnotesAndAutolinks() {
        String h = html("| a |\n|---|\n| 1 |\n\n~~gone~~ see https://example.org and a note[^1].\n\n[^1]: The note.\n");
        assertThat(h).contains("<table>").contains("<del>gone</del>").contains("<a href=\"https://example.org\">")
                .contains("The note.");
    }

    @Test
    void readsFrontMatter() {
        MarkdownRenderer.Rendered r = renderer.render(
                "---\ntitle: \"Colons: allowed\"\ndate: 2026-01-02\ntags: [a, b]\ncategories:\n  - X\n  - Y\n---\n\nBody $x$\n");
        assertThat(r.frontMatter().get("title")).containsExactly("Colons: allowed");
        assertThat(r.frontMatter().get("date")).containsExactly("2026-01-02");
        assertThat(r.frontMatter().get("tags")).containsExactly("a", "b");
        assertThat(renderer.render("---\nauthors: [\"Jane Roe\", 'Max M']\n---\n").frontMatter().get("authors"))
                .containsExactly("Jane Roe", "Max M");
        assertThat(r.frontMatter().get("categories")).containsExactly("X", "Y");
        assertThat(r.html()).doesNotContain("title:");
        assertThat(r.math()).isTrue();
    }
}
