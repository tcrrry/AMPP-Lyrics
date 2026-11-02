package com.tcrrry.vivo;

import dev.amenhancer.plugin.api.PluginContext;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Reads AM's own local library query results; native result pointers never escape a worker task. */
final class NativeLibrary implements AutoCloseable {
    static final class Row {
        final String id,title,artist;
        Row(String id,String title,String artist){this.id=id;this.title=title;this.artist=artist;}
        @Override public boolean equals(Object other){if(!(other instanceof Row))return false;Row r=(Row)other;return id.equals(r.id)&&title.equals(r.title)&&artist.equals(r.artist);}
        @Override public int hashCode(){return Objects.hash(id,title,artist);}
    }
    private final PluginContext context;
    private final NativePlayer player;
    private final Consumer<String> changed;
    private final ClassLoader loader;
    private final Class<?> libraryType,params,builderType,baseBuilder,queryType,resultType,singleType,itemType,providerBuilder;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"AMPP-vivo-library");t.setDaemon(true);return t;});
    private final Map<String,List<Row>> snapshots=new ConcurrentHashMap<>();
    private final Set<String> pending=ConcurrentHashMap.newKeySet();
    private final Map<String,Long> lastRead=new ConcurrentHashMap<>();
    private volatile boolean closed;
    volatile String status="等待 AM 资料库";
    NativeLibrary(PluginContext context,NativePlayer player,Consumer<String> changed)throws Exception{
        this.context=context;this.player=player;this.changed=changed;loader=context.getHostClassLoader();
        libraryType=loader.loadClass("com.apple.android.medialibrary.library.MediaLibrary");
        params=loader.loadClass("N6.g");builderType=loader.loadClass("N6.f$a");baseBuilder=loader.loadClass("N6.h$a");queryType=loader.loadClass("N6.f");
        resultType=loader.loadClass("Z6.f");singleType=loader.loadClass("Ah.m");itemType=loader.loadClass("com.apple.android.music.model.CollectionItemView");
        providerBuilder=loader.loadClass("com.apple.android.music.playback.queue.LibraryItemListPlaybackQueueItemProvider$Builder");
        libraryType.getMethod("g",params);singleType.getMethod("d");
        providerBuilder.getMethod("dataSource",resultType,itemType);providerBuilder.getMethod("startItemIndex",int.class);providerBuilder.getMethod("build");
        for(int delay:new int[]{0,2,7})worker.schedule(()->{refresh(VivoProtocol.FAVORITE,true);refresh(VivoProtocol.LOCAL,true);},delay,TimeUnit.SECONDS);
    }
    boolean ready(String parent){return snapshots.containsKey(parent);}
    List<Row> rows(String parent){return snapshots.getOrDefault(parent,Collections.emptyList());}
    long capabilities(){return (ready(VivoProtocol.FAVORITE)?1024L:0L)|(ready(VivoProtocol.LOCAL)?512L:0L);}
    static boolean accepts(String parent){return VivoProtocol.FAVORITE.equals(parent)||VivoProtocol.LOCAL.equals(parent);}
    void refresh(String parent,boolean force){
        if(closed||!accepts(parent)||(!force&&System.nanoTime()/1000000-lastRead.getOrDefault(parent,0L)<5000)||!pending.add(parent))return;
        try{worker.execute(()->{
            Object result=null;
            try{
                result=query(parent);List<Row> rows=materialize(result);
                if(closed)return;
                List<Row> before=snapshots.put(parent,Collections.unmodifiableList(rows));
                lastRead.put(parent,System.nanoTime()/1000000);status="已读取 AM 资料库";
                if(!rows.equals(before))changed.accept(parent);
            }catch(Exception error){status="资料库暂不可用";context.log("vivo library query failed parent="+parent,error);}
            finally{release(result);pending.remove(parent);}
        });}catch(RejectedExecutionException ignored){pending.remove(parent);}
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private Object query(String parent)throws Exception{
        Object library=loader.loadClass("com.apple.android.medialibrary.library.a").getMethod("d0").invoke(null);
        if(library==null || !(Boolean)libraryType.getMethod("isReady").invoke(library))throw new IllegalStateException("AM library not ready");
        Object builder=builderType.getConstructor().newInstance();
        Class<?> mediaType=loader.loadClass("N6.g$c"),download=loader.loadClass("N6.g$a");
        builderType.getMethod("c",mediaType).invoke(builder,Enum.valueOf((Class)mediaType,"MediaTypeSong"));
        field(builder,"c",Enum.valueOf((Class)download,VivoProtocol.LOCAL.equals(parent)?"Downloaded":"None"));
        // Favorite songs can exist outside the library; mirror AM's favorites filter without its UI's global state.
        field(builder,"f",VivoProtocol.LOCAL.equals(parent));field(builder,"j",VivoProtocol.FAVORITE.equals(parent));
        Object query=queryType.getConstructor(builderType).newInstance(builder);
        Object single=libraryType.getMethod("g",params).invoke(library,query);
        return singleType.getMethod("d").invoke(single);
    }
    private void field(Object owner,String name,Object value)throws Exception{Field field=baseBuilder.getDeclaredField(name);field.setAccessible(true);field.set(owner,value);}
    private static Object call(Object owner,String name,Object... args)throws Exception{
        Method method=owner.getClass().getMethod(name,args.length==0?new Class<?>[0]:new Class<?>[]{int.class});return method.invoke(owner,args);
    }
    private static String id(Object item)throws Exception{
        long catalog=((Number)call(item,"getReportingAdamId")).longValue();
        return catalog>0?Long.toString(catalog):"ampp.vivo.library:"+call(item,"getPersistentId");
    }
    private static List<Row> materialize(Object result)throws Exception{
        int count=((Number)call(result,"getItemCount")).intValue();
        if(count<0||count>100000)throw new IllegalStateException("Unexpected AM library size: "+count);
        List<Row> rows=new ArrayList<>(count);
        for(int i=0;i<count;i++){
            Object item=call(result,"getItemAtIndex",i);
            rows.add(new Row(id(item),Objects.toString(call(item,"getTitle"),""),Objects.toString(call(item,"getArtistName"),"")));
        }
        return rows;
    }
    boolean play(String parent,String mediaId){
        if(closed||!accepts(parent)||mediaId==null||rows(parent).stream().noneMatch(r->r.id.equals(mediaId)))return false;
        try{worker.execute(()->{
            Object result=null;
            try{
                result=query(parent);int count=((Number)call(result,"getItemCount")).intValue();
                for(int i=0;i<count&&!closed;i++)if(id(call(result,"getItemAtIndex",i)).equals(mediaId)){
                    Object builder=providerBuilder.getConstructor().newInstance();
                    providerBuilder.getMethod("dataSource",resultType,itemType).invoke(builder,result,null);
                    providerBuilder.getMethod("startItemIndex",int.class).invoke(builder,i);
                    Object provider=providerBuilder.getMethod("build").invoke(builder);
                    if(!closed&&!player.prepare(provider))context.log("vivo library selection awaits AM controller",null);
                    return;
                }
                context.log("Ignored removed vivo library item",null);
            }catch(Exception error){context.log("vivo library selection failed",error);}
            finally{release(result);}
        });return true;}catch(RejectedExecutionException ignored){return false;}
    }
    private void release(Object result){if(result!=null)try{call(result,"release");}catch(Exception error){context.log("vivo library release failed",error);}}
    @Override public void close(){closed=true;worker.shutdownNow();snapshots.clear();}
}
