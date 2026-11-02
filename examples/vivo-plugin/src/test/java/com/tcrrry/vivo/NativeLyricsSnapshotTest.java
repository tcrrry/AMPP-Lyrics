package com.tcrrry.vivo;

import org.junit.Test;
import static org.junit.Assert.*;

public class NativeLyricsSnapshotTest {
    public static class Pointer {
        public boolean dead;final Object value;
        Pointer(Object value){this.value=value;}
        public boolean isNull(){return dead;} public Object get(){return value;}
    }
    public static class Vector extends Pointer {
        public Object[] values;
        Vector(Object...values){super(null);this.values=values;}
        public long size(){return values.length;} public Object get(long i){return values[(int)i];}
    }
    public static class Song extends Pointer {
        public long id;public Vector sections;
        Song(long id,Vector sections){super(null);this.id=id;this.sections=sections;}
        public long getAdamId(){return id;}public Vector getSections(){return sections;}
    }
    public static class Section extends Pointer {
        final Vector lines;Section(Vector lines){super(null);this.lines=lines;}public Vector getLines(){return lines;}
    }
    public static class Line extends Pointer {
        public String pronunciation="",translation="";
        Line(){super(null);}public long getBegin(){return 1234;}
        public String getHtmlLineText(){return "Original";}
        public String getHtmlPronunciationLineText(){return pronunciation;}
        public String getHtmlTranslationLineText(){return translation;}
    }
    private static Song song(long id,Line line){return new Song(id,new Vector(new Pointer(new Section(new Vector(new Pointer(line))))));}
    @Test public void anonymousParseCanBeReadAfterSameObjectReceivesIdentity()throws Exception {
        NativeLyricsSnapshot reader=new NativeLyricsSnapshot(s->s);Song song=song(0,new Line());Pointer pointer=new Pointer(song);
        assertNull(reader.read(pointer,false,false));song.id=42;
        NativeLyricsSnapshot.Document ready=reader.read(pointer,false,false);assertNotNull(ready);assertEquals("42",ready.id);assertEquals(1,ready.rows.size());
        reader.published(ready);assertNull(reader.read(pointer,false,false));
    }
    @Test public void emptyNativeDocumentDoesNotPoisonLaterInstall()throws Exception {
        NativeLyricsSnapshot reader=new NativeLyricsSnapshot(s->s);Song song=new Song(42,new Vector());Pointer pointer=new Pointer(song);
        assertNull(reader.read(pointer,false,false));song.sections=song(42,new Line()).sections;
        assertNotNull(reader.read(pointer,false,false));
    }
    @Test public void installedSelectionRefreshesAuxiliaryTextAfterParse()throws Exception {
        NativeLyricsSnapshot reader=new NativeLyricsSnapshot(s->s);Line line=new Line();Song song=song(42,line);Pointer pointer=new Pointer(song);
        NativeLyricsSnapshot.Document parsed=reader.read(pointer,false,false);reader.published(parsed);
        line.pronunciation="reading";line.translation="translated";
        NativeLyricsSnapshot.Document installed=reader.read(pointer,false,true);
        assertEquals("reading",installed.rows.get(0).pronunciation);assertEquals("translated",installed.rows.get(0).translation);
        assertNotNull(reader.read(song,true,true));
    }
    @Test public void deallocatedPointerIsNeverDereferenced()throws Exception {
        NativeLyricsSnapshot reader=new NativeLyricsSnapshot(s->s);Pointer pointer=new Pointer(null){@Override public Object get(){throw new AssertionError("native call after release");}};
        pointer.dead=true;assertNull(reader.read(pointer,false,true));
    }
}
