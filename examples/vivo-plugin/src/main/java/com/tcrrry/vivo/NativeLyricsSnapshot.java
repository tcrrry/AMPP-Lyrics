package com.tcrrry.vivo;

import java.util.*;
import java.util.function.Function;

/** Reads while the caller still owns the native document. Failed/anonymous reads are retryable. */
final class NativeLyricsSnapshot {
    static final class Document {
        final String id;final List<LyricsPayload.Row> rows;final Object owner;
        Document(long id,List<LyricsPayload.Row> rows,Object owner){this.id=Long.toString(id);this.rows=rows;this.owner=owner;}
    }
    private final Map<Object,String> published=new WeakHashMap<>();
    private final Function<String,String> plain;
    NativeLyricsSnapshot(Function<String,String> plain){this.plain=plain;}
    synchronized Document read(Object value,boolean nativeSong,boolean refresh) throws Exception {
        if(!alive(value))return null;
        Object song=nativeSong?value:invoke(value,"get");if(!alive(song))return null;
        long id=((Number)invoke(song,"getAdamId")).longValue();if(id<=0)return null;
        if(!refresh && Long.toString(id).equals(published.get(song)))return null;
        Object sections=invoke(song,"getSections");long count=size(sections,256);int visited=0;
        TreeMap<Long,LyricsPayload.Row> rows=new TreeMap<>();
        for(long s=0;s<count && visited<6000;s++) {
            Object sectionPtr=get(sections,s);if(!alive(sectionPtr))continue;
            Object section=invoke(sectionPtr,"get");if(!alive(section))continue;
            Object lines=invoke(section,"getLines");long n=size(lines,6000);
            for(long l=0;l<n && visited<6000;l++,visited++) {
                Object linePtr=get(lines,l);if(!alive(linePtr))continue;
                Object line=invoke(linePtr,"get");if(!alive(line))continue;
                long start=((Number)invoke(line,"getBegin")).longValue();String text=text(line,"getHtmlLineText");
                if(start<0 || text.isEmpty())continue;
                LyricsPayload.Row row=new LyricsPayload.Row(start,text,text(line,"getHtmlPronunciationLineText"),text(line,"getHtmlTranslationLineText"));
                rows.merge(start,row,(a,b)->new LyricsPayload.Row(start,merge(a.original,b.original),merge(a.pronunciation,b.pronunciation),merge(a.translation,b.translation)));
            }
        }
        return rows.isEmpty()?null:new Document(id,new ArrayList<>(rows.values()),song);
    }
    synchronized void published(Document document){published.put(document.owner,document.id);}
    synchronized void clear(){published.clear();}
    private String text(Object line,String getter)throws Exception{return plain.apply(Objects.toString(invoke(line,getter),""));}
    private static String merge(String a,String b){return a.isEmpty()?b:b.isEmpty()||a.equals(b)?a:a+" / "+b;}
    private static boolean alive(Object value)throws Exception{return value!=null && !((Boolean)value.getClass().getMethod("isNull").invoke(value));}
    private static Object invoke(Object value,String name)throws Exception{return value.getClass().getMethod(name).invoke(value);}
    private static Object get(Object value,long index)throws Exception{return value.getClass().getMethod("get",long.class).invoke(value,index);}
    private static long size(Object value,int bound)throws Exception{
        if(!alive(value))return 0;
        long n=((Number)invoke(value,"size")).longValue();if(n<0||n>bound)throw new IllegalStateException("Native vector outside bounds");return n;
    }
}
