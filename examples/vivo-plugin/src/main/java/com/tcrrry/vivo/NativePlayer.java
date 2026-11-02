package com.tcrrry.vivo;

import dev.amenhancer.plugin.api.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Exact public 1609 controller contract; setters enqueue work in Apple's own player. */
final class NativePlayer implements AutoCloseable {
    static final class Row {
        final long queueId; final String mediaId,title,artist,catalogId;
        Row(long id,String key,String title,String artist,String catalogId) { queueId=id;mediaId=key;this.title=title;this.artist=artist;this.catalogId=catalogId; }
    }
    static final class Snapshot {
        final List<Row> rows; final boolean modes,skip; final int repeat,shuffle;
        final long currentQueueId;
        Snapshot(List<Row> rows,boolean modes,boolean skip,int repeat,int shuffle) {
            this(rows,modes,skip,repeat,shuffle,Long.MIN_VALUE);
        }
        Snapshot(List<Row> rows,boolean modes,boolean skip,int repeat,int shuffle,long currentQueueId) {
            this.rows=Collections.unmodifiableList(rows);this.modes=modes;this.skip=skip;this.repeat=repeat;this.shuffle=shuffle;
            this.currentQueueId=currentQueueId;
        }
        String transportId(String catalogId) {
            Row unique=null;int matches=0;
            for(Row row:rows) if(row.catalogId.equals(catalogId)) {
                if(row.queueId==currentQueueId)return row.mediaId;
                unique=row;matches++;
            }
            return matches==1?unique.mediaId:catalogId;
        }
    }
    volatile Snapshot snapshot=new Snapshot(Collections.emptyList(),false,false,0,0);
    private volatile WeakReference<Object> controller=new WeakReference<>(null);
    private final Class<?> type;
    private final PluginContext context;
    private final Runnable changed;
    private final Runnable queueChanged;
    private final List<PluginRegistration> registrations=new ArrayList<>();
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r -> { Thread t=new Thread(r,"AMPP-vivo-native");t.setDaemon(true);return t; });
    private final ThreadLocal<Boolean> reading=new ThreadLocal<>();
    private final AtomicBoolean pending=new AtomicBoolean();
    private volatile boolean closed;
    private volatile long lastRead;
    private volatile Boolean repeatCapability,shuffleCapability;

    NativePlayer(PluginContext context,Runnable changed) throws Exception { this(context,changed,()->{}); }
    NativePlayer(PluginContext context,Runnable changed,Runnable queueChanged) throws Exception {
        this.context=context;this.changed=changed;this.queueChanged=queueChanged;
        type=context.getHostClassLoader().loadClass("com.apple.android.music.playback.controller.LocalMediaPlayerController");
        for(String name:new String[]{"getRepeatMode","getShuffleMode","getAllQueueItems","canSetRepeatMode","canSetShuffleMode","canSkipToQueueItem"}) type.getMethod(name);
        type.getMethod("setRepeatMode",int.class);type.getMethod("setShuffleMode",int.class);type.getMethod("skipToQueueItemWithId",long.class);
        PluginObserver observer=new PluginObserver() {
            @Override public void after(PluginObservation call) {
                if(closed || reading.get()==Boolean.TRUE || call.getThrowable()!=null) return;
                Object current=call.getReceiver();if(current==null || !type.isInstance(current)) return;
                boolean fresh=controller.get()!=current;
                controller=new WeakReference<>(current);
                String method=call.getMethod().getName();
                boolean capabilityChanged=false;
                if(call.getResult() instanceof Boolean) {
                    Boolean available=(Boolean)call.getResult();
                    if(method.equals("canSetRepeatMode")) { capabilityChanged=!available.equals(repeatCapability);repeatCapability=available; }
                    if(method.equals("canSetShuffleMode")) { capabilityChanged=!available.equals(shuffleCapability);shuffleCapability=available; }
                }
                boolean event=call.getMethod() instanceof Method && ((Method)call.getMethod()).getName().startsWith("on");
                if(fresh || event || capabilityChanged || (snapshot.rows.isEmpty() && System.nanoTime()/1000000-lastRead>2000)) request();
            }
        };
        try {
        for(Constructor<?> constructor:type.getDeclaredConstructors()) registrations.add(context.getHooks().observe(constructor,observer));
        for(Method method:type.getDeclaredMethods()) if(Arrays.asList("getCurrentItem","getCurrentPosition","getRepeatMode","getShuffleMode",
            "canSetRepeatMode","canSetShuffleMode","onPlaybackModeChanged","onPlaybackStateChanged",
            "onPlaybackQueueChanged","onPlaybackQueueItemsAdded","onPlaybackIndexChanged","onPlaybackRepeatModeChanged","onPlaybackShuffleModeChanged").contains(method.getName()))
            registrations.add(context.getHooks().observe(method,observer));
        } catch(Exception | LinkageError error) { close();throw error; }
    }
    private static Object call(Object owner,String name) throws Exception { return owner.getClass().getMethod(name).invoke(owner); }
    private Snapshot read(Object owner) throws Exception {
        reading.set(true);
        try {
            int repeat=(Integer)call(owner,"getRepeatMode"),shuffle=(Integer)call(owner,"getShuffleMode");
            boolean canRepeat=(Boolean)call(owner,"canSetRepeatMode"),canShuffle=(Boolean)call(owner,"canSetShuffleMode");
            repeatCapability=canRepeat;shuffleCapability=canShuffle;
            boolean modes=canRepeat&&canShuffle;
            boolean skip=(Boolean)call(owner,"canSkipToQueueItem");
            List<?> items=(List<?>)call(owner,"getAllQueueItems");
            List<Long> ids=new ArrayList<>();List<String[]> text=new ArrayList<>();Set<Long> seen=new HashSet<>();
            StringBuilder fingerprint=new StringBuilder();
            if(items!=null) for(Object item:items) {
                if(item==null || ids.size()>=2000) break;
                long id=((Number)call(item,"getPlaybackQueueId")).longValue();
                if(!seen.add(id)) continue;
                Object media=call(item,"getItem");if(media==null) continue;
                String title=Objects.toString(call(media,"getTitle"),"");
                String artist=Objects.toString(call(media,"getArtistName"),"");
                long adam=((Number)call(media,"getReportingAdamId")).longValue();
                String libraryId="";
                if(adam<=0)try{long persistent=((Number)call(media,"getPersistentId")).longValue();if(persistent!=0)libraryId="ampp.vivo.library:"+persistent;}catch(NoSuchMethodException ignored){}
                ids.add(id);text.add(new String[]{title,artist,Long.toString(adam),libraryId});fingerprint.append(id).append(':').append(adam).append(':').append(title).append('\n');
            }
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(fingerprint.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder epoch=new StringBuilder();for(int i=0;i<8;i++) epoch.append(String.format(Locale.ROOT,"%02x",digest[i]));
            List<Row> rows=new ArrayList<>();
            Map<String,Integer> counts=new HashMap<>();
            for(String[] value:text)counts.merge(value[2],1,Integer::sum);
            for(int i=0;i<ids.size();i++) {
                String catalog=text.get(i)[2];
                String key=!text.get(i)[3].isEmpty()?text.get(i)[3]:!catalog.equals("0") && counts.get(catalog)==1?catalog:"ampp.vivo.queue:"+epoch+":"+ids.get(i);
                rows.add(new Row(ids.get(i),key,text.get(i)[0],text.get(i)[1],catalog));
            }
            Object current=call(owner,"getCurrentItem");
            long currentId=current==null?Long.MIN_VALUE:((Number)call(current,"getPlaybackQueueId")).longValue();
            return new Snapshot(rows,modes,skip,repeat,shuffle,currentId);
        } finally { reading.remove(); }
    }
    private boolean submit(Runnable action) {
        if(closed) return false;
        try { worker.execute(action);return true; }
        catch(RejectedExecutionException ignored) { return false; }
    }
    private static boolean sameQueue(Snapshot a,Snapshot b) {
        if(a.rows.size()!=b.rows.size())return false;
        for(int i=0;i<a.rows.size();i++) {
            Row x=a.rows.get(i),y=b.rows.get(i);
            if(x.queueId!=y.queueId || !x.mediaId.equals(y.mediaId) || !x.title.equals(y.title) || !x.artist.equals(y.artist))return false;
        }
        return true;
    }
    private void request() {
        if(closed || !pending.compareAndSet(false,true)) return;
        if(!submit(() -> {
            try { Object owner=controller.get();if(owner!=null && !closed) { Snapshot previous=snapshot;snapshot=read(owner);lastRead=System.nanoTime()/1000000;
                if(previous.repeat!=snapshot.repeat || previous.shuffle!=snapshot.shuffle || previous.modes!=snapshot.modes)
                    context.log("vivo mode readback repeat="+snapshot.repeat+" shuffle="+snapshot.shuffle+" canRepeat="+repeatCapability+" canShuffle="+shuffleCapability,null);
                boolean queueUpdated=!sameQueue(previous,snapshot);
                if(queueUpdated || previous.currentQueueId!=snapshot.currentQueueId || previous.repeat!=snapshot.repeat || previous.shuffle!=snapshot.shuffle || previous.modes!=snapshot.modes || previous.skip!=snapshot.skip)changed.run();
                if(queueUpdated)queueChanged.run(); } }
            catch(Exception error) { context.log("vivo native snapshot unavailable",error); }
            finally { pending.set(false); }
        })) pending.set(false);
    }
    // Apple's setters enqueue a message. Reading immediately after invoke observes the old mode.
    // Refresh twice after the command, even when the native listener was coalesced with another read.
    private void verifyCommand() {
        if(closed)return;
        try { worker.schedule(this::request,200,TimeUnit.MILLISECONDS);worker.schedule(this::request,900,TimeUnit.MILLISECONDS); }
        catch(RejectedExecutionException ignored) { }
    }
    boolean setMode(int mode) {
        int[] target=VivoProtocol.nativeMode(mode);if(target==null || !snapshot.modes || closed) return false;
        return submit(() -> {
            try {
                Object owner=controller.get();if(owner==null || !read(owner).modes) return;
                type.getMethod("setShuffleMode",int.class).invoke(owner,target[1]);
                type.getMethod("setRepeatMode",int.class).invoke(owner,target[0]);
                context.log("vivo mode submitted target="+mode+" nativeRepeat="+target[0]+" nativeShuffle="+target[1],null);
                verifyCommand();
            } catch(Exception error) { context.log("vivo mode command failed",error); }
        });
    }
    boolean play(String mediaId) {
        if(mediaId==null || closed) return false;
        return submit(() -> {
            try {
                Object owner=controller.get();if(owner==null) return;
                Snapshot fresh=read(owner);if(!fresh.skip) return;
                for(Row row:fresh.rows) if(row.mediaId.equals(mediaId)) { type.getMethod("skipToQueueItemWithId",long.class).invoke(owner,row.queueId);verifyCommand();return; }
                context.log("Ignored stale vivo queue item",null);
            } catch(Exception error) { context.log("vivo queue selection failed",error); }
        });
    }
    boolean prepare(Object provider) {
        if(provider==null || controller.get()==null)return false;
        return submit(() -> {
            try {
                Object owner=controller.get();if(owner==null || closed)return;
                Class<?> contract=context.getHostClassLoader().loadClass("com.apple.android.music.playback.queue.PlaybackQueueItemProvider");
                type.getMethod("prepare",contract,boolean.class).invoke(owner,provider,true);
                verifyCommand();
            }catch(Exception error){context.log("vivo library playback failed",error);}
        });
    }
    @Override public void close() { closed=true;worker.shutdownNow();for(PluginRegistration registration:registrations) registration.close();registrations.clear();controller.clear(); }
}
