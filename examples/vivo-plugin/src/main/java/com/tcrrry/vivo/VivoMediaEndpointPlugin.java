package com.tcrrry.vivo;

import dev.amenhancer.plugin.api.*;
import android.media.MediaMetadata;
import android.media.MediaDescription;
import android.media.browse.MediaBrowser;
import android.os.*;
import android.content.Context;
import android.view.View;
import android.widget.*;
import java.util.*;
import java.io.*;
import java.nio.file.*;

final class VivoMediaEndpointPlugin extends AmppPlugin implements PluginMediaEndpoint {
    private PluginContext context;
    private PluginExternalMedia bridge;
    private NativePlayer player;
    private NativeLyricsReader lyrics;
    private NativeCommands commands;
    private NativeFavorite favorite;
    private NativeLibrary library;
    private PluginRegistration endpoint;
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile boolean stopped;
    private MediaMetadata cachedBase,cachedResult;
    private long cachedFlags=Long.MIN_VALUE;private int cachedMode=-1;
    private String cachedTransportId="";private Boolean cachedHeart;
    private volatile int lyricCharacters,servedRows,modeRequests,queueRequests;
    private volatile String mediaId="";
    private boolean showTranslation,showPronunciation,originalOnly;
    static boolean supportsHost(String pkg, String version, long code) {
        return ("com.apple.android.music".equals(pkg) || "com.tcrrry.ampplyrics.coexist".equals(pkg)) &&
            (((code == 1609L || code == 1613L) && "7.0.0-beta".equals(version)) || (code == 1614L && "7.0.0".equals(version)));
    }
    @Override public void onLoad(PluginContext value) throws Exception {
        if(!supportsHost(value.getHostPackageName(), value.getHostVersionName(), value.getHostVersionCode()))
            throw new PluginUnsupportedException("vivo 预览仅核对 Apple Music 1609、1613、1614");
        if(value.getApplication().getApplicationInfo().targetSdkVersion>=37)
            throw new PluginUnsupportedException("此预览控制接口仅核对 target 36 的 1609、1613、1614 宿主");
        context=value;bridge=value.getExternalMedia();
        if(!bridge.supportsEndpoint("vivo")) throw new PluginUnsupportedException("请先安装 External Media 预览宿主");
        value.onClose(this::onStop);
        player=new NativePlayer(value,bridge::refresh,() -> bridge.notifyChildrenChanged(VivoProtocol.CURRENT));
        lyrics=new NativeLyricsReader(value,bridge);
        Properties preferences=new Properties();File file=new File(value.getDataDirectory(),"display.properties");
        if(file.isFile()) try(InputStream input=new FileInputStream(file)) { preferences.load(input); }
        showTranslation=Boolean.parseBoolean(preferences.getProperty("translation","false"));
        showPronunciation=Boolean.parseBoolean(preferences.getProperty("pronunciation","false"));
        originalOnly=Boolean.parseBoolean(preferences.getProperty("originalOnly","false"));
        lyrics.options(!originalOnly&&showTranslation,!originalOnly&&showPronunciation);
        try{library=new NativeLibrary(value,player,parent->{bridge.notifyChildrenChanged(parent);bridge.notifyChildrenChanged(getRootId());bridge.refresh();});}
        catch(Exception error){value.log("vivo optional library catalogs unavailable",error);}
        favorite=new NativeFavorite(value,()->{bridge.refresh();if(library!=null)library.refresh(VivoProtocol.FAVORITE,true);});
        endpoint=bridge.registerEndpoint("vivo",this);
        commands=new NativeCommands(value,this,()->mediaId,favorite);
        value.log("vivo external media started: 0.2-preview.3; both compat callbacks registered",null);
    }
    @Override public void onStart() { bridge.refresh(); }
    @Override public boolean acceptsClient(String name,int uid) { return "com.vivo.musicwidgetmix".equals(name); }
    // MediaMetadata supports custom keys; preserve the original catalog ID across native copies.
    @android.annotation.SuppressLint("WrongConstant")
    private static String catalogId(MediaMetadata metadata) {
        String id=metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
        if(id!=null && id.startsWith("ampp.vivo.")) {
            String original=metadata.getString("ampp.external.original_media_id");
            if(original!=null)return original;
        }
        return id;
    }
    // MediaMetadata accepts custom keys at runtime; these names are the vendor wire protocol.
    @android.annotation.SuppressLint("WrongConstant")
    @Override public synchronized MediaMetadata decorateMetadata(MediaMetadata original) {
        if(stopped || original==null) return original;
        String id=catalogId(original);
        mediaId=id==null?"":id;
        String lrc=bridge.lyricsFor(mediaId);lyricCharacters=lrc==null?0:lrc.length();
        NativePlayer.Snapshot state=player.snapshot;
        String transportId=state.transportId(mediaId);
        // This bit advertises a supported protocol, not whether this track has finished parsing.
        // Vivo can decide its UI from the first metadata before lyric extras arrive.
        long flags=original.getLong(VivoProtocol.SUPPORT)|VivoProtocol.capabilities(true,state.modes&&commands!=null,state.skip&&commands!=null&&!state.rows.isEmpty())|(favorite!=null&&favorite.available?64L:0L)|(library==null?0L:library.capabilities());
        int mode=VivoProtocol.loop(state.repeat,state.shuffle);
        Boolean heart=favorite==null?null:favorite.heart;
        if(original==cachedBase && flags==cachedFlags && mode==cachedMode && transportId.equals(cachedTransportId) && Objects.equals(heart,cachedHeart)) return cachedResult;
        cachedBase=original;cachedFlags=flags;cachedMode=mode;cachedTransportId=transportId;cachedHeart=heart;
        MediaMetadata.Builder builder=new MediaMetadata.Builder(original).putLong(VivoProtocol.SUPPORT,flags);
        builder.putString("ampp.external.original_media_id",mediaId).putString(MediaMetadata.METADATA_KEY_MEDIA_ID,transportId);
        if(heart!=null)builder.putRating(MediaMetadata.METADATA_KEY_RATING,android.media.Rating.newHeartRating(heart));
        if(state.modes) builder.putLong(VivoProtocol.LOOP,mode);
        cachedResult=builder.build();return cachedResult;
    }
    @Override public Bundle decorateExtras(MediaMetadata metadata,Bundle original) {
        Bundle result=new Bundle(original);
        if(metadata==null || stopped) return result;
        String id=catalogId(metadata);if(id==null) return result;
        String lrc=bridge.lyricsFor(id);
        result.putString(VivoProtocol.ACTION,VivoProtocol.LRC_CHANGE);
        result.putString(VivoProtocol.MEDIA_ID,player.snapshot.transportId(id));
        result.putString(VivoProtocol.LYRIC,lrc==null?"":lrc);
        return result;
    }
    @Override public String getRootId() { return "ampp.external.media"; }
    @Override public List<MediaBrowser.MediaItem> loadChildren(String parent,Bundle options) {
        if(stopped) return Collections.emptyList();
        context.log("vivo browse parent="+parent+" page="+options.getInt(VivoProtocol.PAGE,0)+" available="+player.snapshot.rows.size(),null);
        if(getRootId().equals(parent)) {
            if(library!=null){library.refresh(VivoProtocol.FAVORITE,false);library.refresh(VivoProtocol.LOCAL,false);}
            List<MediaBrowser.MediaItem> roots=new ArrayList<>();
            roots.add(directory(VivoProtocol.CURRENT,"当前播放列表"));
            if(library!=null){
                if(library.ready(VivoProtocol.FAVORITE))roots.add(directory(VivoProtocol.FAVORITE,"我的收藏"));
                if(library.ready(VivoProtocol.LOCAL))roots.add(directory(VivoProtocol.LOCAL,"已下载歌曲"));
            }
            return roots;
        }
        if(NativeLibrary.accepts(parent) && library!=null){
            library.refresh(parent,false);List<NativeLibrary.Row> rows=library.rows(parent);
            int page=Math.max(0,Math.min(2000,options.getInt(VivoProtocol.PAGE,0)));
            int start=Math.min(rows.size(),page*50),end=Math.min(rows.size(),start+50);
            List<MediaBrowser.MediaItem> items=new ArrayList<>();
            for(int i=start;i<end;i++){
                NativeLibrary.Row row=rows.get(i);Bundle extra=new Bundle();
                extra.putString(VivoProtocol.LIST,parent);extra.putBoolean(VivoProtocol.MORE,end<rows.size());extra.putInt(VivoProtocol.PAGE,page+1);
                items.add(new MediaBrowser.MediaItem(new MediaDescription.Builder().setMediaId(row.id).setTitle(row.title).setSubtitle(row.artist).setExtras(extra).build(),MediaBrowser.MediaItem.FLAG_PLAYABLE));
            }
            return items;
        }
        if(!VivoProtocol.CURRENT.equals(parent)) return Collections.emptyList();
        NativePlayer.Snapshot state=player.snapshot;
        int page=Math.max(0,Math.min(100,options.getInt(VivoProtocol.PAGE,0))),size=50;
        int start=Math.min(state.rows.size(),page*size),end=Math.min(state.rows.size(),start+size);
        List<MediaBrowser.MediaItem> items=new ArrayList<>();
        for(int i=start;i<end;i++) {
            NativePlayer.Row row=state.rows.get(i);Bundle extra=new Bundle();
            extra.putString(VivoProtocol.LIST,parent);extra.putBoolean(VivoProtocol.MORE,end<state.rows.size());extra.putInt(VivoProtocol.PAGE,page+1);
            items.add(new MediaBrowser.MediaItem(new MediaDescription.Builder().setMediaId(row.mediaId)
                .setTitle(row.title).setSubtitle(row.artist).setExtras(extra).build(),MediaBrowser.MediaItem.FLAG_PLAYABLE));
        }
        servedRows=state.rows.size();return items;
    }
    private static MediaBrowser.MediaItem directory(String id,String title){
        return new MediaBrowser.MediaItem(new MediaDescription.Builder().setMediaId(id).setTitle(title).build(),MediaBrowser.MediaItem.FLAG_BROWSABLE);
    }
    @Override public boolean onCustomAction(String action,Bundle extras) {
        if(stopped || !VivoProtocol.MODE_ACTION.equals(action)) return false;
        boolean handled=player.setMode(extras.getInt(VivoProtocol.LOOP,0));
        if(handled) { modeRequests++;main.postDelayed(bridge::refresh,300);main.postDelayed(bridge::refresh,900); }
        return handled;
    }
    @Override public boolean onPlayFromMediaId(String id,Bundle extras) {
        if(stopped)return false;
        String parent=extras==null?"":extras.getString(VivoProtocol.LIST,"");
        boolean handled=NativeLibrary.accepts(parent)?library!=null&&library.play(parent,id):player.play(id);
        if(handled)queueRequests++;return handled;
    }
    @Override public PluginSettingsSession createSettings(Context settingsContext) {
        LinearLayout root=new LinearLayout(settingsContext);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(32,24,32,24);
        Switch translated=new Switch(settingsContext);translated.setText("显示译文");translated.setChecked(showTranslation);root.addView(translated);
        Switch phonetic=new Switch(settingsContext);phonetic.setText("显示发音");phonetic.setChecked(showPronunciation);root.addView(phonetic);
        Switch original=new Switch(settingsContext);original.setText("仅显示原文（适合原子岛）");original.setChecked(originalOnly);root.addView(original,0);
        translated.setEnabled(!originalOnly);phonetic.setEnabled(!originalOnly);
        TextView hint=new TextView(settingsContext);hint.setText("发音在原文上方，译文在下方；整句之间留空行。原子岛与随身听共用歌词，暂不支持自动切换。");root.addView(hint);
        original.setOnCheckedChangeListener((button,checked)->{originalOnly=checked;translated.setEnabled(!checked);phonetic.setEnabled(!checked);saveDisplay();});
        translated.setOnCheckedChangeListener((button,checked)->{showTranslation=checked;saveDisplay();});
        phonetic.setOnCheckedChangeListener((button,checked)->{showPronunciation=checked;saveDisplay();});
        TextView text=new TextView(settingsContext);text.setTextSize(16);root.addView(text);
        final boolean[] closed={false};Runnable update=new Runnable() { public void run() {
            if(closed[0] || stopped) return;
            NativePlayer.Snapshot state=player.snapshot;
            text.setText("vivo 系统播放器扩展 · preview.13\n\n歌词："+(lyricCharacters>0?"已提供 "+lyricCharacters+" 字符":"等待当前歌曲歌词")+
                "\n歌词读取："+lyrics.status+"\nAM 模式控制："+(state.modes?"可用":"等待播放控制器")+
                "\n资料库："+(library==null?"当前宿主不支持":library.status+" · 收藏 "+library.rows(VivoProtocol.FAVORITE).size()+" / 已下载 "+library.rows(VivoProtocol.LOCAL).size())+"\n收藏按钮："+(favorite!=null&&favorite.available?"已接入 AM 喜爱":"等待原生收藏动作")+"\n当前播放列表："+state.rows.size()+" 首\n\n已收到循环请求："+modeRequests+"\n已收到选曲请求："+queueRequests+
                "\n\n接口就绪不代表系统已经显示，请在原子随身听中验证。");main.postDelayed(this,1000);
        }};update.run();
        return new PluginSettingsSession() { public View getView(){return root;} public void close(){closed[0]=true;main.removeCallbacks(update);} };
    }
    private void saveDisplay() {
        if(stopped) return;
        try {
            Properties preferences=new Properties();preferences.setProperty("originalOnly",Boolean.toString(originalOnly));preferences.setProperty("translation",Boolean.toString(showTranslation));preferences.setProperty("pronunciation",Boolean.toString(showPronunciation));
            File directory=context.getDataDirectory();directory.mkdirs();File temporary=new File(directory,"display.properties.tmp");
            try(OutputStream output=new FileOutputStream(temporary)) { preferences.store(output,"vivo lyric display"); }
            Files.move(temporary.toPath(),new File(directory,"display.properties").toPath(),StandardCopyOption.REPLACE_EXISTING);
            lyrics.options(!originalOnly&&showTranslation,!originalOnly&&showPronunciation);bridge.refresh();
        } catch(Exception error) { context.log("vivo display preference could not be saved",error); }
    }
    @Override public void onStop() {
        if(stopped) return;stopped=true;main.removeCallbacksAndMessages(null);
        if(library!=null)library.close();if(endpoint!=null) endpoint.close();if(commands!=null) commands.close();if(favorite!=null)favorite.close();if(player!=null) player.close();if(lyrics!=null) lyrics.close();
    }
}
