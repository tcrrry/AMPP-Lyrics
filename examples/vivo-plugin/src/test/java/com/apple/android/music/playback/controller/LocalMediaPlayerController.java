package com.apple.android.music.playback.controller;

import java.util.*;
import java.util.concurrent.CountDownLatch;

/** Contract fixture: mutations are observable, so queue commands can be checked. */
public class LocalMediaPlayerController {
    public volatile com.apple.android.music.playback.queue.PlaybackQueueItemProvider prepared;
    public volatile boolean started;
    public void prepare(com.apple.android.music.playback.queue.PlaybackQueueItemProvider provider,boolean start){prepared=provider;started=start;command.countDown();}
    public volatile int repeat,shuffle;
    public volatile long selected=-1;
    public volatile boolean allowed=true;
    public volatile List<Item> rows=Arrays.asList(new Item(4,42,"First"),new Item(5,43,"Second"));
    public final CountDownLatch command=new CountDownLatch(1);
    public int getRepeatMode(){return repeat;} public int getShuffleMode(){return shuffle;}
    public List<Item> getAllQueueItems(){return rows;}
    public Item getCurrentItem(){for(Item item:rows)if(item.queueId==selected)return item;return rows.isEmpty()?null:rows.get(0);}
    public boolean canSetRepeatMode(){return allowed;} public boolean canSetShuffleMode(){return allowed;}
    public boolean canSkipToQueueItem(){return allowed;}
    public void setRepeatMode(int value){repeat=value;command.countDown();}
    public void setShuffleMode(int value){shuffle=value;}
    public void skipToQueueItemWithId(long value){selected=value;command.countDown();}
    public static class Item {
        final long queueId;final Media media;
        public Item(long queueId,long adam,String title){this.queueId=queueId;media=new Media(adam,title);}
        public long getPlaybackQueueId(){return queueId;} public Media getItem(){return media;}
    }
    public static class Media {
        final long adam;final String title;
        Media(long adam,String title){this.adam=adam;this.title=title;}
        public String getTitle(){return title;} public String getArtistName(){return "Artist";}
        public long getReportingAdamId(){return adam;}
    }
}
