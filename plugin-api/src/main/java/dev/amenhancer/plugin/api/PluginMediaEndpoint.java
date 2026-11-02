package dev.amenhancer.plugin.api;

import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.os.Bundle;
import android.os.ResultReceiver;
import java.util.Collections;
import java.util.List;

/** Endpoint logic belongs to a plugin. The host owns component and native-session lifetime. */
public interface PluginMediaEndpoint {
    boolean acceptsClient(String packageName, int uid);
    default String getRootId() { return "root"; }
    default List<MediaBrowser.MediaItem> loadChildren(String parentId, Bundle options) { return Collections.emptyList(); }
    default MediaMetadata decorateMetadata(MediaMetadata original) { return original; }
    default Bundle decorateExtras(MediaMetadata metadata, Bundle original) { return original; }
    default boolean onCustomAction(String action, Bundle extras) { return false; }
    default boolean onPlayFromMediaId(String mediaId, Bundle extras) { return false; }
    default boolean onCommand(String command, Bundle extras, ResultReceiver result) { return false; }
}
