package com.tcrrry.vivo;

import dev.amenhancer.plugin.api.*;
import android.text.Html;
import java.lang.reflect.Method;
import java.util.*;

/** Snapshot the currently installed pointer synchronously, before native ownership can change. */
final class NativeLyricsReader implements AutoCloseable {
    private final List<PluginRegistration> registrations=new ArrayList<>();
    private final NativeLyricsSnapshot snapshot=new NativeLyricsSnapshot(text->Html.fromHtml(text,0).toString().replace('\n',' ').replace('\r',' ').trim());
    volatile String status="等待歌词文档";
    private final LinkedHashMap<String,List<LyricsPayload.Row>> cache=new LinkedHashMap<>();
    private final PluginExternalMedia bridge;
    private boolean translation,pronunciation;
    NativeLyricsReader(PluginContext context,PluginExternalMedia bridge) throws Exception {
        this.bridge=bridge;
        ClassLoader loader=context.getHostClassLoader();
        Class<?> fragment=loader.loadClass("com.apple.android.music.player.fragment.PlayerLyricsViewFragment");
        Class<?> ptr=loader.loadClass("com.apple.android.music.ttml.javanative.model.SongInfo$SongInfoPtr");
        Method installed=fragment.getDeclaredMethod("w2",ptr);
        PluginObserver observer=new PluginObserver() {
            @Override public void after(PluginObservation call) {
                if(call.getThrowable()!=null) return;
                try {
                    boolean applied=call.getMethod().equals(installed);
                    boolean nativeSong=call.getMethod().getDeclaringClass().getSimpleName().equals("SongInfoNative");
                    Object value=nativeSong?call.getReceiver():applied?call.getArguments()[0]:call.getResult();
                    NativeLyricsSnapshot.Document document=snapshot.read(value,nativeSong,applied||nativeSong);
                    if(document==null)return;
                    if(remember(document.id,document.rows)) {
                        snapshot.published(document);status="已读取 "+document.rows.size()+" 行";
                    }
                } catch(Exception error) { status="读取失败："+error.getClass().getSimpleName();context.log("Current native lyrics snapshot unavailable",error); }
            }
        };
        registrations.add(context.getHooks().observe(installed,observer));
        Class<?> parser=loader.loadClass("com.apple.android.music.ttml.javanative.TTMLParser$TTMLParserNative");
        registrations.add(context.getHooks().observe(parser.getDeclaredMethod("songInfoFromTTML",String.class),observer));
        Class<?> nativeSong=loader.loadClass("com.apple.android.music.ttml.javanative.model.SongInfo$SongInfoNative");
        registrations.add(context.getHooks().observe(nativeSong.getDeclaredMethod("setAdamId",long.class),observer));
        for(String selector:new String[]{"setTranslation","setPronunciation"})
            registrations.add(context.getHooks().observe(nativeSong.getDeclaredMethod(selector,String.class),observer));
    }
    private synchronized boolean remember(String id,List<LyricsPayload.Row> rows) {
        cache.remove(id);cache.put(id,rows);
        while(cache.size()>8) cache.remove(cache.keySet().iterator().next());
        String lrc=LyricsPayload.compose(rows,translation,pronunciation);
        if(lrc.isEmpty())return false;
        bridge.publishLyrics(id,lrc);return true;
    }
    synchronized void options(boolean translation,boolean pronunciation) {
        this.translation=translation;this.pronunciation=pronunciation;
        for(Map.Entry<String,List<LyricsPayload.Row>> entry:cache.entrySet()) {
            String lrc=LyricsPayload.compose(entry.getValue(),translation,pronunciation);
            bridge.publishLyrics(entry.getKey(),lrc);
        }
    }
    @Override public synchronized void close() { for(PluginRegistration registration:registrations) registration.close();registrations.clear();snapshot.clear();cache.clear(); }
}
