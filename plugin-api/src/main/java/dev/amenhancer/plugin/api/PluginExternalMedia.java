package dev.amenhancer.plugin.api;

/** Optional SDK v1 capability; discovery templates must already exist in the host package. */
public interface PluginExternalMedia {
    boolean supportsEndpoint(String id);
    PluginRegistration registerEndpoint(String id, PluginMediaEndpoint endpoint);
    /** Re-read the active endpoint without replacing the native MediaItem or playback state. */
    void refresh();
    void notifyChildrenChanged(String parentId);
    /** Share exact-song, timestamped LRC across media integration plugins. */
    void publishLyrics(String mediaId, String lrc);
    String lyricsFor(String mediaId);
}
