package com.hermes.noir;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;

public class MarkdownTest {
    private static Markdown.Block one(String input){
        List<Markdown.Block> blocks=Markdown.parse(input);
        assertEquals(1,blocks.size());
        return blocks.get(0);
    }
    @Test public void fencedCodeKeepsBodyAndLanguage() {
        Markdown.Block b=one("```python\nprint('hi')\nprint('bye')\n```");
        assertEquals(Markdown.CODE,b.type);assertEquals("python",b.lang);
        assertEquals("print('hi')\nprint('bye')",b.code);
    }
    @Test public void unclosedFenceConsumesRest() {
        Markdown.Block b=Markdown.parse("text before\n```\ncode line").get(1);
        assertEquals(Markdown.CODE,b.type);assertEquals("code line",b.code);
    }
    @Test public void headingsLevelsAndRule() {
        assertEquals(Markdown.HEADING,one("## عنوان فرعي").type);
        assertEquals(2,one("## عنوان فرعي").level);
        assertEquals(Markdown.RULE,one("---").type);
        assertEquals(Markdown.PARAGRAPH,one("#hashtag not a heading").type);
    }
    @Test public void listsGroupConsecutiveItems() {
        Markdown.Block b=one("- اول\n- تاني\n* ثالث");
        assertEquals(Markdown.BULLETS,b.type);assertEquals(3,b.items.size());
        assertEquals("تاني",b.items.get(1).text);
        Markdown.Block ordered=one("1. first\n2. second");
        assertEquals(Markdown.ORDERED,ordered.type);assertEquals(2,ordered.items.size());
    }
    @Test public void quoteAndParagraphLineBreaks() {
        Markdown.Block quote=one("> حكمة\n> تانية");
        assertEquals(Markdown.QUOTE,quote.type);assertEquals(2,quote.items.size());
        List<Markdown.Block> blocks=Markdown.parse("سطر أول\nسطر تاني\n\nفقرة جديدة");
        assertEquals(2,blocks.size());
        assertEquals(2,blocks.get(0).items.size());
        assertEquals("فقرة جديدة",blocks.get(1).items.get(0).text);
    }
    @Test public void inlineEmphasisAndCode() {
        Markdown.Line l=Markdown.inline("**bold** plain *it* `code` ~~gone~~");
        assertEquals("bold plain it code gone",l.text);
        assertTrue(l.marks.stream().anyMatch(m->m.bold&&"bold".equals(l.text.substring(m.start,m.end))));
        assertTrue(l.marks.stream().anyMatch(m->m.italic&&"it".equals(l.text.substring(m.start,m.end))));
        assertTrue(l.marks.stream().anyMatch(m->m.code&&"code".equals(l.text.substring(m.start,m.end))));
        assertTrue(l.marks.stream().anyMatch(m->m.strike&&"gone".equals(l.text.substring(m.start,m.end))));
    }
    @Test public void underscoresInsideWordsAreNotEmphasis() {
        Markdown.Line l=Markdown.inline("my_file_name and __real bold__");
        assertTrue(l.marks.stream().noneMatch(m->m.italic));
        assertTrue(l.marks.stream().anyMatch(m->m.bold&&"real bold".equals(l.text.substring(m.start,m.end))));
    }
    @Test public void nestedEmphasisAndLinks() {
        Markdown.Line l=Markdown.inline("***both*** and [دفتر](https://example.com/x)");
        assertTrue(l.marks.stream().anyMatch(m->m.bold&&m.italic));
        Markdown.Mark link=l.marks.stream().filter(m->m.link!=null).findFirst().orElse(null);
        assertNotNull(link);assertEquals("https://example.com/x",link.link);
        assertEquals("دفتر",l.text.substring(link.start,link.end));
    }
    @Test public void emptyAndNullAreSafe() {
        assertTrue(Markdown.parse(null).isEmpty());
        assertTrue(Markdown.parse("").isEmpty());
        assertTrue(Markdown.parse("\n\n  \n").isEmpty());
    }
    @Test public void arabicTextPreserved() {
        Markdown.Block b=one("السعر **٥٠ جنيه** والضمان سنة");
        assertEquals("السعر ٥٠ جنيه والضمان سنة",b.items.get(0).text);
        assertTrue(b.items.get(0).marks.stream().anyMatch(m->m.bold&&"٥٠ جنيه".equals(b.items.get(0).text.substring(m.start,m.end))));
    }
}
