package com.apple.android.medialibrary.library;
import java.util.*;import com.apple.android.music.model.CollectionItemView;
public class MediaLibrary {
public final List<N6.g> queries=Collections.synchronizedList(new ArrayList<>());
public final List<Result> results=Collections.synchronizedList(new ArrayList<>());
public volatile List<CollectionItemView> favorites=Arrays.asList(new CollectionItemView(42,1,"Favorite"));
public volatile List<CollectionItemView> downloads=Arrays.asList(new CollectionItemView(43,2,"Downloaded"));
public boolean isReady(){return true;}
public Ah.m g(N6.g params){queries.add(params);Result result=new Result(params.favorite?favorites:downloads);results.add(result);return new Ah.m(result);}
public static class Result implements Z6.f {
 public final List<CollectionItemView> rows;public boolean released;
 Result(List<CollectionItemView> rows){this.rows=new ArrayList<>(rows);}
 public int getItemCount(){if(released)throw new IllegalStateException();return rows.size();}
 public CollectionItemView getItemAtIndex(int i){if(released)throw new IllegalStateException();return rows.get(i);}
 public void release(){released=true;}
}}
