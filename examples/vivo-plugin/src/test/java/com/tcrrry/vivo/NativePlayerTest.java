package com.tcrrry.vivo;

import com.apple.android.music.playback.controller.LocalMediaPlayerController;
import dev.amenhancer.plugin.api.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.lang.reflect.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class NativePlayerTest {
    private final CountDownLatch logged=new CountDownLatch(1);
    private PluginContext context() {
        PluginHooks hooks=(PluginHooks)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PluginHooks.class},(p,m,a)->(PluginRegistration)()->{});
        return (PluginContext)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PluginContext.class},(p,m,a)->{
            if(m.getName().equals("getHostClassLoader"))return getClass().getClassLoader();
            if(m.getName().equals("getHooks"))return hooks;
            if(m.getName().equals("log"))logged.countDown();return null;
        });
    }
    private NativePlayer.Snapshot attach(NativePlayer player,LocalMediaPlayerController owner) throws Exception {
        Field field=NativePlayer.class.getDeclaredField("controller");field.setAccessible(true);field.set(player,new WeakReference<>(owner));
        Method read=NativePlayer.class.getDeclaredMethod("read",Object.class);read.setAccessible(true);
        NativePlayer.Snapshot state=(NativePlayer.Snapshot)read.invoke(player,owner);player.snapshot=state;return state;
    }
    @Test public void queueSelectionUsesNativeQueueId() throws Exception {
        try(NativePlayer player=new NativePlayer(context(),()->{})) {
            LocalMediaPlayerController owner=new LocalMediaPlayerController();NativePlayer.Snapshot state=attach(player,owner);
            assertTrue(player.play(state.rows.get(1).mediaId));assertTrue(owner.command.await(2,TimeUnit.SECONDS));assertEquals(5,owner.selected);
        }
    }
    @Test public void staleQueueEntryCannotSelectReusedNativeId() throws Exception {
        try(NativePlayer player=new NativePlayer(context(),()->{})) {
            LocalMediaPlayerController owner=new LocalMediaPlayerController();NativePlayer.Snapshot state=attach(player,owner);
            owner.rows=Collections.singletonList(new LocalMediaPlayerController.Item(5,999,"Changed queue"));
            assertTrue(player.play(state.rows.get(1).mediaId));assertTrue(logged.await(2,TimeUnit.SECONDS));assertEquals(-1,owner.selected);
        }
    }
    @Test public void loopRequestsReachNativeControllerAndStopRevokesCommands() throws Exception {
        NativePlayer player=new NativePlayer(context(),()->{});LocalMediaPlayerController owner=new LocalMediaPlayerController();attach(player,owner);
        assertTrue(player.setMode(3));assertTrue(owner.command.await(2,TimeUnit.SECONDS));assertEquals(2,owner.repeat);assertEquals(1,owner.shuffle);
        player.close();assertFalse(player.setMode(2));assertFalse(player.play("ampp.vivo.queue:any:5"));
    }
    @Test public void loopStateIsReadBackWithoutNativeListenerEvent() throws Exception {
        CountDownLatch refreshed=new CountDownLatch(1);
        try(NativePlayer player=new NativePlayer(context(),refreshed::countDown)) {
            LocalMediaPlayerController owner=new LocalMediaPlayerController();attach(player,owner);
            assertTrue(player.setMode(3));
            assertTrue(refreshed.await(2,TimeUnit.SECONDS));
            assertEquals(2,player.snapshot.repeat);assertEquals(1,player.snapshot.shuffle);
        }
    }
    private void refreshAndDrain(NativePlayer player) throws Exception {
        Method request=NativePlayer.class.getDeclaredMethod("request");request.setAccessible(true);request.invoke(player);
        Field worker=NativePlayer.class.getDeclaredField("worker");worker.setAccessible(true);
        ((ExecutorService)worker.get(player)).submit(()->{}).get(2,TimeUnit.SECONDS);
    }
    @Test public void unchangedQueueAndModeReadbacksDoNotInvalidateSystemCard() throws Exception {
        AtomicInteger metadata=new AtomicInteger(),queues=new AtomicInteger();
        try(NativePlayer player=new NativePlayer(context(),metadata::incrementAndGet,queues::incrementAndGet)) {
            LocalMediaPlayerController owner=new LocalMediaPlayerController();attach(player,owner);
            refreshAndDrain(player);refreshAndDrain(player);
            assertEquals(0,metadata.get());assertEquals(0,queues.get());
            owner.repeat=1;refreshAndDrain(player);
            assertEquals(1,metadata.get());assertEquals(0,queues.get());
            owner.rows=Collections.singletonList(new LocalMediaPlayerController.Item(6,44,"New queue"));
            refreshAndDrain(player);refreshAndDrain(player);
            assertEquals(2,metadata.get());assertEquals(1,queues.get());
        }
    }
    @Test public void playingMarkerUsesTheSameIdAsTheSelectedQueueRowIncludingDuplicates() throws Exception {
        try(NativePlayer player=new NativePlayer(context(),()->{})) {
            LocalMediaPlayerController owner=new LocalMediaPlayerController();
            owner.rows=Arrays.asList(new LocalMediaPlayerController.Item(4,42,"First"),new LocalMediaPlayerController.Item(5,42,"Again"));
            owner.selected=5;NativePlayer.Snapshot state=attach(player,owner);
            assertEquals(state.rows.get(1).mediaId,state.transportId("42"));
            assertEquals("unknown",state.transportId("unknown"));
            owner.selected=4;state=attach(player,owner);
            assertEquals(state.rows.get(0).mediaId,state.transportId("42"));
        }
    }
    @Test public void uniqueCatalogIdentitySurvivesQueueChanges() throws Exception {
        try(NativePlayer player=new NativePlayer(context(),()->{})) {
            LocalMediaPlayerController owner=new LocalMediaPlayerController();NativePlayer.Snapshot state=attach(player,owner);
            assertEquals("42",state.rows.get(0).mediaId);
            owner.rows=Arrays.asList(new LocalMediaPlayerController.Item(18,43,"Second"),new LocalMediaPlayerController.Item(19,42,"First"));
            state=attach(player,owner);assertEquals("42",state.rows.get(1).mediaId);
        }
    }

}
