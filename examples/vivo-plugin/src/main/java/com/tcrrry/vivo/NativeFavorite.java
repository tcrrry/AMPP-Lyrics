package com.tcrrry.vivo;

import android.media.MediaMetadata;
import android.media.Rating;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import dev.amenhancer.plugin.api.*;
import java.lang.ref.WeakReference;
import java.util.*;

/** Reuses the same action as AM's native favorite button; never requests account credentials. */
final class NativeFavorite implements AutoCloseable {
    static final String ACTION="com.apple.android.music.playback.action.FAVORITE";
    private final List<PluginRegistration> registrations=new ArrayList<>();
    private final FavoriteRequestGate gate=new FavoriteRequestGate();
    private volatile WeakReference<MediaSession> session=new WeakReference<>(null);
    volatile Boolean heart;
    volatile boolean available;
    private volatile boolean closed;
    NativeFavorite(PluginContext context,Runnable changed)throws Exception {
        PluginObserver observer=new PluginObserver(){@Override public void after(PluginObservation call){
            if(closed || call.getThrowable()!=null || !(call.getReceiver() instanceof MediaSession))return;
            MediaSession owner=(MediaSession)call.getReceiver();
            MediaMetadata metadata=owner.getController().getMetadata();PlaybackState state=owner.getController().getPlaybackState();
            Rating rating=metadata==null?null:metadata.getRating(MediaMetadata.METADATA_KEY_USER_RATING);
            Boolean value=rating!=null&&rating.isRated()&&rating.getRatingStyle()==Rating.RATING_HEART?rating.hasHeart():null;
            boolean action=false;if(state!=null)for(PlaybackState.CustomAction item:state.getCustomActions())action|=ACTION.equals(item.getAction());
            boolean supported=action&&value!=null;
            boolean update=available!=supported||!Objects.equals(heart,value);
            session=new WeakReference<>(owner);heart=value;available=supported;
            gate.update(metadata==null?"":Objects.toString(metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),""),value);
            if(update)changed.run();
        }};
        try{
            registrations.add(context.getHooks().observe(MediaSession.class.getDeclaredMethod("setMetadata",MediaMetadata.class),observer));
            registrations.add(context.getHooks().observe(MediaSession.class.getDeclaredMethod("setPlaybackState",PlaybackState.class),observer));
        }catch(Exception error){close();throw error;}
    }
    boolean set(boolean desired){
        MediaSession owner=session.get();if(closed||!available||owner==null)return false;
        if(gate.submit(desired,System.nanoTime()/1000000))owner.getController().getTransportControls().sendCustomAction(ACTION,Bundle.EMPTY);
        return true;
    }
    @Override public void close(){closed=true;available=false;for(PluginRegistration r:registrations)r.close();registrations.clear();session.clear();}
}
