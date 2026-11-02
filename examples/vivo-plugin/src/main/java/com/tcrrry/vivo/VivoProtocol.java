package com.tcrrry.vivo;

/** Spelling follows the actual 6.2.7.2 wire protocol, including its typos. */
final class VivoProtocol {
    static final String SUPPORT="vivomusicmix.media.metadata.support_event";
    static final String LOOP="vivomusicmix.media.metadata.LOOP_MODE";
    static final String MODE_ACTION="vivomusicmix.media.action.PLAY_MODE";
    static final String CURRENT="vivomusicmix_current_list", LOCAL="vivomusicmix_local_list", FAVORITE="vivomusicmix_favorite_list";
    static final String LIST="vivomusicmix_key_list";
    static final String PAGE="vivomusicmix_key_media_page", MORE="vivomusicmix_key_has_more";
    static final String ACTION="vivomusicmix.meida.extra.key.action", LRC_CHANGE="vivomusicmix.extra.lrc_change";
    static final String MEDIA_ID="vivomusicmix.extra.key.meidia_id", LYRIC="vivomusicmix.extra.key.lyric";
    static int loop(int repeat,int shuffle) { return shuffle==1?3:repeat==1?2:1; }
    static int[] nativeMode(int mode) {
        if(mode==1) return new int[]{2,0}; if(mode==2) return new int[]{1,0}; if(mode==3) return new int[]{2,1}; return null;
    }
    static long capabilities(boolean lyrics,boolean modes,boolean queue) {
        return 7L|16L|(lyrics?8L:0L)|(modes?128L:0L)|(queue?256L:0L);
    }
}
