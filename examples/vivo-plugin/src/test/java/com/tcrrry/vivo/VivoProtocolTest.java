package com.tcrrry.vivo;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class VivoProtocolTest {
    @Test public void modesRoundTripThroughNativeRepeatShuffle() {
        for(int mode=1;mode<=3;mode++) {
            int[] nativeState=VivoProtocol.nativeMode(mode);
            assertEquals(mode,VivoProtocol.loop(nativeState[0],nativeState[1]));
        }
        assertNull(VivoProtocol.nativeMode(0));assertNull(VivoProtocol.nativeMode(99));
    }
    @Test public void absentCapabilitiesAreNotAdvertised() {
        assertEquals(23,VivoProtocol.capabilities(false,false,false));
        assertEquals(415,VivoProtocol.capabilities(true,true,true));
        assertEquals(0,VivoProtocol.capabilities(false,true,true)&8);
        assertEquals(0,VivoProtocol.capabilities(true,false,true)&128);
        assertEquals(0,VivoProtocol.capabilities(true,true,false)&256);
        assertEquals(256,VivoProtocol.capabilities(true,true,true)&256);
        assertEquals(0,VivoProtocol.capabilities(true,true,true)&64); // Favorite is not implemented.
    }
    @Test public void optionalTextKeepsTimingAndOrderAndDoesNotInventContent() {
        List<LyricsPayload.Row> rows=Arrays.asList(new LyricsPayload.Row(62345,"原文","gen bun","译文"),new LyricsPayload.Row(65000,"same","","same"));
        assertEquals("[01:02.345]原文\n[01:05.000]same\n",LyricsPayload.compose(rows,false,false));
        assertEquals("[01:02.345]gen bun^原文^译文^^\n[01:05.000]same\n",LyricsPayload.compose(rows,true,true));
        assertFalse(LyricsPayload.compose(rows,true,false).contains("gen bun"));
    }
    @Test public void oversizedLyricsAreRejectedWholeInsteadOfTruncatedMidTimestamp() {
        assertEquals("",LyricsPayload.compose(Collections.singletonList(new LyricsPayload.Row(1,String.join("",Collections.nCopies(270000,"a")),"","")),false,false));
    }
    @Test public void displayBreaksKeepOnePhysicalRecordPerTimedCue() {
        String lrc=LyricsPayload.compose(Arrays.asList(new LyricsPayload.Row(1000,"original","pronunciation","translation"),new LyricsPayload.Row(2000,"next","","")),true,true);
        assertEquals(2,lrc.lines().count());
        String first=lrc.split("\n")[0];
        assertEquals("[00:01.000]pronunciation\noriginal\ntranslation\n\n",first.replace("^","\n"));
        assertFalse(lrc.contains(" · "));
    }

}
