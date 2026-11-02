package com.tcrrry.vivo;

/** Native favorite action toggles: duplicate desired-state requests must not toggle twice. */
final class FavoriteRequestGate {
    private String song="";
    private Boolean current,pending;
    private long submitted;
    synchronized void update(String id,Boolean value) {
        if(!song.equals(id)){song=id;pending=null;}
        current=value;if(pending!=null && pending.equals(value))pending=null;
    }
    synchronized boolean submit(boolean desired,long now) {
        if(current==null || current==desired)return false;
        if(pending!=null && now-submitted<3000)return false;
        pending=desired;submitted=now;return true;
    }
}
