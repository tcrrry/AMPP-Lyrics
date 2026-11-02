package dev.amenhancer.hostproxy;

import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.service.media.MediaBrowserService;
import java.util.Collections;
import java.util.List;
import java.lang.ref.WeakReference;

/** Framework-only bootstrap injected into the final host DEX, not the plugin class loader. */
public final class ExternalMediaService extends MediaBrowserService {
    public interface Delegate {
        MediaSession.Token token();
        String root(String packageName, int uid);
        List<MediaBrowser.MediaItem> children(String parentId, Bundle options);
        void connected();
    }
    private static volatile Delegate delegate;
    private static WeakReference<ExternalMediaService> active = new WeakReference<>(null);
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ConnectionAuthorization authorization=new ConnectionAuthorization();
    private String bootstrapRoot;
    private String[] bootstrapClients=new String[0];
    public static synchronized void setDelegate(Delegate value) { delegate = value; sessionReady(); }
    public static void sessionReady() {
        ExternalMediaService service=active.get();if(service!=null)service.main.post(service::publishSession);
    }
    private void publishSession() {
        Delegate current=delegate;if(current==null)return;
        try {
            MediaSession.Token token=current.token();
            if(!authorization.ready(current::root,token!=null))return;
            if(getSessionToken()==null)setSessionToken(token);
            if(token.equals(getSessionToken()))current.connected();
        }catch(RuntimeException error){android.util.Log.w("AMPPExternalMedia","Deferred connection not ready",error);}
    }
    public static void childrenChanged(String parentId) {
        ExternalMediaService service = active.get();
        if (service != null) service.notifyChildrenChanged(parentId);
    }
    @Override public void onCreate() {
        super.onCreate();active=new WeakReference<>(this);
        try {
            Bundle config=getPackageManager().getServiceInfo(new ComponentName(this,ExternalMediaService.class),PackageManager.GET_META_DATA).metaData;
            if(config!=null){bootstrapRoot=config.getString("ampp.external.media.bootstrap_root");bootstrapClients=config.getString("ampp.external.media.bootstrap_clients","").split(",");}
        }catch(PackageManager.NameNotFoundException ignored){}
        sessionReady();
    }
    @Override public BrowserRoot onGetRoot(String packageName, int uid, Bundle hints) {
        Delegate current = delegate;
        try {
            String[] packages = getPackageManager().getPackagesForUid(uid);
            boolean ownsPackage = false;
            if (packages != null) for (String name : packages) ownsPackage |= name.equals(packageName);
            if (!ownsPackage) return null;
            // Framework MediaBrowserService supports returning a root before setSessionToken.
            // Never reject a valid reconnect just because plugin/session initialization is pending.
            if(current!=null&&getSessionToken()!=null&&!getSessionToken().equals(current.token()))return null;
            String root=authorization.authorize(packageName,uid,true,current==null?null:current::root,bootstrapRoot,bootstrapClients);
            if(root==null)return null;
            main.post(this::publishSession);
            return new BrowserRoot(root, Bundle.EMPTY);
        } catch (RuntimeException error) { return null; }
    }
    @Override public void onLoadChildren(String parentId, Result<List<MediaBrowser.MediaItem>> result) {
        onLoadChildren(parentId, result, Bundle.EMPTY);
    }
    @Override public void onLoadChildren(String parentId, Result<List<MediaBrowser.MediaItem>> result, Bundle options) {
        Delegate current = delegate;
        try { result.sendResult(current == null ? Collections.emptyList() : current.children(parentId, options)); }
        catch (RuntimeException error) { result.sendResult(Collections.emptyList()); }
    }
    @Override public void onDestroy() {
        if (active.get() == this) active.clear();
        main.removeCallbacksAndMessages(null);authorization.clear();
        super.onDestroy();
    }
}
