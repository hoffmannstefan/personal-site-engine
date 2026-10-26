package com.homepage.backend.post;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.ext.footnotes.FootnotesExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.HtmlWriter;
import org.springframework.stereotype.Component;

/**
 * Turns a Markdown post (CommonMark + GitHub tables, strikethrough, autolinks, footnotes, YAML front matter)
 * into the same HTML the theme styles for the hand-written posts. TeX formulas are protected from the Markdown
 * parser by {@link MathProtector}.
 *
 * <p>Posts are written by the site owner, so raw HTML inside Markdown is passed through unchanged.
 */
@Component
public class MarkdownRenderer {

    /** Result of rendering one Markdown file. */
    public record Rendered(Map<String, List<String>> frontMatter, String html, boolean math, int words) {
    }

    private static final Pattern HEADING = Pattern.compile("<h([1-6]) id=\"([^\"]*)\">(.*?)</h\\1>", Pattern.DOTALL);
    private static final Pattern LANGUAGE = Pattern.compile("[A-Za-z0-9_+#.-]+");
    /** A Markdown link to another site, as the renderer writes it (raw HTML links are left as written). */
    private static final Pattern EXTERNAL_LINK = Pattern.compile("<a href=\"(https?://)");

    private final Parser parser;
    private final HtmlRenderer renderer;

    public MarkdownRenderer() {
        List<Extension> extensions = List.of(
                YamlFrontMatterExtension.create(),
                TablesExtension.create(),
                StrikethroughExtension.create(),
                AutolinkExtension.create(),
                FootnotesExtension.create(),
                HeadingAnchorExtension.create());
        this.parser = Parser.builder().extensions(extensions).build();
        this.renderer = HtmlRenderer.builder()
                .extensions(extensions)
                .nodeRendererFactory(CodeBlockRenderer::new)
                .build();
    }

    public Rendered render(String markdown) {
        MathProtector math = new MathProtector();
        Node document = parser.parse(math.protect(markdown));
        YamlFrontMatterVisitor front = new YamlFrontMatterVisitor();
        document.accept(front);

        String html = HEADING.matcher(renderer.render(document)).replaceAll(m -> Matcher.quoteReplacement(
                "<h" + m.group(1) + " id=\"" + tidyId(m.group(2)) + "\">\n  " + m.group(3).strip() + "\n"
                        + "  <a class=\"heading-link\" href=\"#" + tidyId(m.group(2)) + "\">\n"
                        + "    <i class=\"fa-solid fa-link\" aria-hidden=\"true\" title=\"Link to heading\"></i>\n"
                        + "    <span class=\"sr-only\">Link to heading</span>\n"
                        + "  </a>\n</h" + m.group(1) + ">"));
        return new Rendered(expandInlineLists(front.getData()), math.restore(html), math.count() > 0, countWords(html));
    }

    /** Makes the Markdown links to other sites in rendered HTML open in a new tab. */
    public static String externalLinksInNewTab(String html) {
        return EXTERNAL_LINK.matcher(html).replaceAll("<a target=\"_blank\" rel=\"noopener\" href=\"$1");
    }

    /** A formula in a heading leaves a gap in its id ("the--case"); close it. */
    private static String tidyId(String id) {
        String tidy = id.replaceAll("-{2,}", "-").replaceAll("^-+", "");
        return tidy.isEmpty() ? id : tidy;
    }

    /** The front matter parser only reads "- item" lists; also accept the common inline form {@code [a, b]}. */
    private static Map<String, List<String>> expandInlineLists(Map<String, List<String>> data) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        data.forEach((key, values) -> {
            List<String> expanded = new ArrayList<>();
            for (String v : values) {
                String t = v.strip();
                if (t.startsWith("[") && t.endsWith("]")) {
                    for (String item : t.substring(1, t.length() - 1).split(",")) {
                        String clean = item.strip().replaceAll("^([\"'])(.*)\\1$", "$2");
                        if (!clean.isEmpty()) {
                            expanded.add(clean);
                        }
                    }
                } else {
                    expanded.add(v);
                }
            }
            out.put(key, expanded);
        });
        return out;
    }

    private static int countWords(String html) {
        String text = html.replaceAll("<[^>]+>", " ").strip();
        return text.isEmpty() ? 0 : text.split("\\s+").length;
    }

    /** Fenced code in the markup of the theme's highlighter ({@code div.highlight > pre.chroma > code}). */
    private static final class CodeBlockRenderer implements NodeRenderer {

        private final HtmlWriter html;

        CodeBlockRenderer(HtmlNodeRendererContext context) {
            this.html = context.getWriter();
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return Set.of(FencedCodeBlock.class);
        }

        @Override
        public void render(Node node) {
            FencedCodeBlock block = (FencedCodeBlock) node;
            String info = block.getInfo() == null ? "" : block.getInfo().strip();
            String language = info.split("\\s+")[0];
            String attributes = LANGUAGE.matcher(language).matches()
                    ? " class=\"language-" + language + "\" data-lang=\"" + language + "\"" : "";
            html.line();
            html.raw("<div class=\"highlight\"><pre tabindex=\"0\" class=\"chroma\"><code" + attributes + ">");
            html.text(block.getLiteral());
            html.raw("</code></pre></div>");
            html.line();
        }
    }
}
