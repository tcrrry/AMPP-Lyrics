package dev.amenhancer.hostproxy;

import java.util.*;

/** Deferred connections never receive a token until the live endpoint revalidates them. */
final class ConnectionAuthorization {
    interface RootResolver { String root(String name,int uid); }
    private static final class Client {
        final String name,root;final int uid;
        Client(String name,int uid,String root){this.name=name;this.uid=uid;this.root=root;}
    }
    private final Map<String,Client> clients=new LinkedHashMap<>();
    String authorize(String name,int uid,boolean ownsPackage,RootResolver live,String bootstrapRoot,String[] declaredClients) {
        if(!ownsPackage)return null;
        String root=null;
        if(live!=null)root=live.root(name,uid);
        else if(bootstrapRoot!=null&&!bootstrapRoot.isEmpty())
            for(String declared:declaredClients)if(name.equals(declared.trim())){root=bootstrapRoot;break;}
        if(root==null||root.isEmpty())return null;
        clients.put(name+":"+uid,new Client(name,uid,root));return root;
    }
    boolean ready(RootResolver live,boolean tokenAvailable) {
        if(live==null||!tokenAvailable)return false;
        for(Client client:clients.values())if(!client.root.equals(live.root(client.name,client.uid)))return false;
        return true;
    }
    void clear(){clients.clear();}
}
