package com.tcrrry.vivo;
import dev.amenhancer.plugin.api.*;
import com.apple.android.medialibrary.library.*;
import com.apple.android.music.playback.controller.LocalMediaPlayerController;
import com.apple.android.music.model.CollectionItemView;
import org.junit.*;import static org.junit.Assert.*;
import java.util.*;import java.lang.reflect.*;import java.lang.ref.WeakReference;import java.util.concurrent.*;

public class NativeLibraryTest {
 private PluginContext context(){
  PluginHooks hooks=(PluginHooks)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PluginHooks.class},(p,m,a)->(PluginRegistration)()->{});
  return (PluginContext)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PluginContext.class},(p,m,a)->{
   if(m.getName().equals("getHostClassLoader"))return getClass().getClassLoader();if(m.getName().equals("getHooks"))return hooks;return null;
  });
 }
 private void drain(NativeLibrary library)throws Exception{
  Field f=NativeLibrary.class.getDeclaredField("worker");f.setAccessible(true);ExecutorService worker=(ExecutorService)f.get(library);
  for(int i=0;i<4;i++)worker.submit(()->{}).get(2,TimeUnit.SECONDS);
 }
 @Before public void reset(){a.instance=new MediaLibrary();}
 @Test public void catalogsUseIndependentNativeFiltersAndReleaseResults()throws Exception{
  try(NativePlayer player=new NativePlayer(context(),()->{});NativeLibrary library=new NativeLibrary(context(),player,p->{})){
   drain(library);assertEquals(1536L,library.capabilities());assertEquals("42",library.rows(VivoProtocol.FAVORITE).get(0).id);
   assertEquals("43",library.rows(VivoProtocol.LOCAL).get(0).id);
   for(N6.g query:a.instance.queries){if(query.favorite){assertFalse(query.libraryOnly);assertEquals(N6.g.a.None,query.download);}else{assertTrue(query.libraryOnly);assertEquals(N6.g.a.Downloaded,query.download);}}
   for(MediaLibrary.Result result:a.instance.results)assertTrue(result.released);
  }
 }
 @Test public void selectionRequeriesAndBuildsNativeQueueAtSelectedIndex()throws Exception{
  a.instance.favorites=Arrays.asList(new CollectionItemView(40,1,"A"),new CollectionItemView(42,2,"B"));
  try(NativePlayer player=new NativePlayer(context(),()->{});NativeLibrary library=new NativeLibrary(context(),player,p->{})){
   LocalMediaPlayerController owner=new LocalMediaPlayerController();Field f=NativePlayer.class.getDeclaredField("controller");f.setAccessible(true);f.set(player,new WeakReference<>(owner));
   drain(library);assertTrue(library.play(VivoProtocol.FAVORITE,"42"));drain(library);
   assertTrue(owner.command.await(2,TimeUnit.SECONDS));assertTrue(owner.started);assertEquals(1,owner.prepared.index);assertEquals(2,owner.prepared.rows.size());
   for(MediaLibrary.Result result:a.instance.results)assertTrue(result.released);
  }
 }
 @Test public void removedSongDoesNotPlayDifferentIndexAndLocalIdsRemainDistinct()throws Exception{
  a.instance.downloads=Arrays.asList(new CollectionItemView(0,71,"Local one"),new CollectionItemView(0,72,"Local two"));
  try(NativePlayer player=new NativePlayer(context(),()->{});NativeLibrary library=new NativeLibrary(context(),player,p->{})){
   LocalMediaPlayerController owner=new LocalMediaPlayerController();Field f=NativePlayer.class.getDeclaredField("controller");f.setAccessible(true);f.set(player,new WeakReference<>(owner));
   drain(library);assertEquals("ampp.vivo.library:71",library.rows(VivoProtocol.LOCAL).get(0).id);assertEquals("ampp.vivo.library:72",library.rows(VivoProtocol.LOCAL).get(1).id);
   a.instance.favorites=Collections.emptyList();assertTrue(library.play(VivoProtocol.FAVORITE,"42"));drain(library);
   for(MediaLibrary.Result result:a.instance.results)assertTrue(result.released);
   assertNull(owner.prepared);library.close();assertFalse(library.play(VivoProtocol.LOCAL,"ampp.vivo.library:71"));
  }
 }
}
