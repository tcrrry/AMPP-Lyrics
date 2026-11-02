package com.tcrrry.vivo;

import android.os.Bundle;
import android.media.Rating;
import dev.amenhancer.plugin.api.*;
import java.util.*;
import java.util.function.Supplier;

/** Exact 1609 app callback, including callbacks constructed before plugin startup. */
final class NativeCommands implements AutoCloseable {
    private final List<PluginRegistration> registrations=new ArrayList<>();
    NativeCommands(PluginContext context,PluginMediaEndpoint endpoint,Supplier<String> mediaId,NativeFavorite favorite)throws Exception {
        // The host contains two independently shaded compat implementations. Media3 uses K4.A.
        for(String callback:new String[]{"android.support.v4.media.session.MediaSessionCompat$a$b","K4.A$b$b"}) {
        Class<?> type=context.getHostClassLoader().loadClass(callback);
        registrations.add(context.getHooks().hook(type.getDeclaredMethod("onSetRating",Rating.class),false,new PluginHook(){
            @Override public void before(PluginCall call){
                Object argument=call.getArguments()[0];
                if(argument instanceof Rating){Rating rating=(Rating)argument;
                    if(rating.getRatingStyle()==Rating.RATING_HEART && rating.isRated() && favorite.set(rating.hasHeart()))call.returnResult(null);
                }
            }
        }));
        for(String name:new String[]{"onCustomAction","onPlayFromMediaId"}) {
            registrations.add(context.getHooks().hook(type.getDeclaredMethod(name,String.class,Bundle.class),false,new PluginHook(){
                @Override public void before(PluginCall call)throws Exception {
                    String action=call.getArguments()[0] instanceof String?(String)call.getArguments()[0]:"";
                    boolean custom=name.equals("onCustomAction");
                    Bundle extras=call.getArguments()[1] instanceof Bundle?(Bundle)call.getArguments()[1]:new Bundle();
                    String list=extras.getString(VivoProtocol.LIST,"");
                    if(custom?!VivoProtocol.MODE_ACTION.equals(action):!(action.startsWith("ampp.vivo.queue:")||VivoProtocol.CURRENT.equals(list)||NativeLibrary.accepts(list)))return;
                    // The native app callback is shared. Leave every other custom action intact.
                    // Queue selections are additionally validated against the native queue epoch.
                    if(mediaId.get().isEmpty())return;
                    boolean handled=custom?endpoint.onCustomAction(action,extras):endpoint.onPlayFromMediaId(action,extras);
                    context.log("vivo native request callback="+callback+" action="+(custom?"mode":"queue")+" handled="+handled,null);
                    if(handled)call.returnResult(null);
                }
            }));
        }
        }
    }
    @Override public void close(){for(PluginRegistration registration:registrations)registration.close();registrations.clear();}
}
