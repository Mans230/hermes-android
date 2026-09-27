package com.hermes.noir;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal Markdown block and inline parser, Android-free so it can be unit tested.
 * Supports fenced code blocks, ATX headings, bullet/ordered lists, quotes, rules,
 * bold, italic, inline code, strikethrough and [text](url) links. Delimiters are
 * stripped from the produced text and marks reference the cleaned positions.
 * Line breaks inside a paragraph are preserved as the sender wrote them, which is
 * what chat users expect.
 */
public final class Markdown {
    public static final int PARAGRAPH=0, HEADING=1, CODE=2, BULLETS=3, ORDERED=4, QUOTE=5, RULE=6;
    public static final class Mark {
        public final int start,end; public final boolean bold,italic,code,strike; public final String link;
        Mark(int start,int end,boolean bold,boolean italic,boolean code,boolean strike,String link){
            this.start=start;this.end=end;this.bold=bold;this.italic=italic;this.code=code;this.strike=strike;this.link=link;
        }
    }
    public static final class Line {
        public final String text; public final List<Mark> marks=new ArrayList<>();
        Line(String text){this.text=text;}
    }
    public static final class Block {
        public final int type; public int level=1; public String code="",lang="";
        public final List<Line> items=new ArrayList<>();
        Block(int type){this.type=type;}
    }
    private static final String FENCE_BLOCK="-{3,}|\\*{3,}|_{3,}";
    private static final String LIST_ITEM="\\d{1,3}[.)] .*";

    public static List<Block> parse(String input) {
        List<Block> out=new ArrayList<>();
        if(input==null)return out;
        String[] lines=input.replace("\r\n","\n").replace("\r","\n").split("\n",-1);
        int i=0;
        while(i<lines.length){
            String t=lines[i].trim();
            if(t.isEmpty()){i++;continue;}
            if(t.startsWith("```")||t.startsWith("~~~")){
                String fence=t.substring(0,3);String lang=t.length()>3?t.substring(3).trim():"";
                StringBuilder code=new StringBuilder();i++;
                while(i<lines.length&&!lines[i].trim().startsWith(fence)){code.append(lines[i]).append('\n');i++;}
                if(i<lines.length)i++;
                Block b=new Block(CODE);b.lang=lang;
                String body=code.toString();if(body.endsWith("\n"))body=body.substring(0,body.length()-1);
                b.code=body;out.add(b);continue;
            }
            if(t.matches(FENCE_BLOCK)){out.add(new Block(RULE));i++;continue;}
            if(t.startsWith("#")){
                int level=0;while(level<t.length()&&t.charAt(level)=='#')level++;
                if(level<=6&&(level>=t.length()||t.charAt(level)==' ')){
                    Block b=new Block(HEADING);b.level=level;
                    b.items.add(inline(t.substring(Math.min(level+1,t.length())).trim()));out.add(b);i++;continue;
                }
            }
            if(t.startsWith(">")){
                Block b=new Block(QUOTE);
                while(i<lines.length){String q=lines[i].trim();
                    if(q.startsWith(">")){String rest=q.length()>1&&q.charAt(1)==' '?q.substring(2):q.substring(1);b.items.add(inline(rest));i++;}else break;}
                out.add(b);continue;
            }
            if(t.length()>2&&(t.startsWith("- ")||t.startsWith("* ")||t.startsWith("• "))){
                Block b=new Block(BULLETS);
                while(i<lines.length){String q=lines[i].trim();
                    if(q.startsWith("- ")||q.startsWith("* ")||q.startsWith("• ")){b.items.add(inline(q.substring(2).trim()));i++;}else break;}
                out.add(b);continue;
            }
            if(t.matches(LIST_ITEM)){
                Block b=new Block(ORDERED);
                while(i<lines.length){String q=lines[i].trim();
                    if(q.matches(LIST_ITEM)){b.items.add(inline(q.substring(q.indexOf(' ')+1).trim()));i++;}else break;}
                out.add(b);continue;
            }
            Block b=new Block(PARAGRAPH);
            b.items.add(inline(t));i++;
            while(i<lines.length){
                String q=lines[i].trim();
                if(q.isEmpty()||q.startsWith("```")||q.startsWith("~~~")||q.startsWith("#")||q.startsWith(">")
                    ||q.length()>2&&(q.startsWith("- ")||q.startsWith("* ")||q.startsWith("• "))
                    ||q.matches(LIST_ITEM)||q.matches(FENCE_BLOCK))break;
                b.items.add(inline(q));i++;
            }
            out.add(b);
        }
        return out;
    }

    /** Parses inline emphasis, code, strikethrough and links; delimiters are removed from text. */
    public static Line inline(String text) {
        String source=text==null?"":text;
        List<Mark> marks=new ArrayList<>();
        StringBuilder out=new StringBuilder();
        emit(marks,out,source,0,source.length(),false,false,false,false,null);
        Line l=new Line(out.toString());
        l.marks.addAll(marks);
        return l;
    }
    /**
     * Appends the cleaned text of s[from,to) to out and registers one mark per special
     * segment (code, link, strike, emphasis), covering the whole cleaned segment so
     * nested children can add their own overlapping marks. Code segments are copied
     * literally with nothing parsed inside them.
     */
    private static void emit(List<Mark> l,StringBuilder out,String s,int from,int to,boolean bold,boolean italic,boolean code,boolean strike,String link){
        if(code){out.append(s,from,to);return;}
        int i=from;
        while(i<to){
            char ch=s.charAt(i);
            int end=-1,tokenLen=0,skip=0;
            boolean isCode=false,isStrike=false;String newLink=null;
            boolean dbl=false,triple=false;
            if(ch=='`'){
                int close=s.indexOf('`',i+1);
                if(close>i&&close<to){end=close;tokenLen=1;skip=1;isCode=true;}
            }else if(ch=='['){
                int close=s.indexOf("](",i+1);
                if(close>i&&close<to){
                    int rp=s.indexOf(')',close+2);
                    if(rp>close&&rp<to){
                        String url=s.substring(close+2,rp);
                        if(!url.isEmpty()&&!url.contains(" ")&&!url.contains("\n")){end=close;tokenLen=1;skip=rp-i+1;newLink=url;}
                    }
                }
            }else if(ch=='~'&&i+1<to&&s.charAt(i+1)=='~'){
                int close=s.indexOf("~~",i+2);
                if(close>i&&close<to){end=close;tokenLen=2;skip=2;isStrike=true;}
            }else if(ch=='*'||ch=='_'){
                dbl=i+1<to&&s.charAt(i+1)==ch;
                triple=dbl&&i+2<to&&s.charAt(i+2)==ch;
                String token=triple?String.valueOf(ch)+ch+ch:dbl?String.valueOf(ch)+ch:String.valueOf(ch);
                int close=indexOf(s,token,i+token.length(),to);
                if(close>i){
                    if(ch=='_'){
                        boolean prevWord=i>from&&isWord(s.charAt(i-1));
                        boolean nextWord=close+token.length()<to&&isWord(s.charAt(close+token.length()));
                        if(prevWord||nextWord)close=-1;
                    }
                    if(close>i){end=close;tokenLen=token.length();skip=token.length();}
                }
            }
            if(end>i){
                boolean nb=bold||dbl,ni=italic||!dbl||triple;
                int seg=out.length();
                emit(l,out,s,i+tokenLen,end,nb,ni,isCode,isStrike,newLink==null?link:newLink);
                l.add(new Mark(seg,out.length(),nb,ni,isCode,isStrike,newLink==null?link:newLink));
                i=end+skip;
            }else{out.append(ch);i++;}
        }
    }
    private static int indexOf(String s,String token,int from,int to){
        if(token.isEmpty()||from>to-token.length())return -1;
        for(int i=from;i<=to-token.length();i++)if(s.startsWith(token,i))return i;
        return -1;
    }
    private static boolean isWord(char c){return Character.isLetterOrDigit(c)||c=='_';}
}
