package dev.amenhancer.hostproxy;

/** Runs on a plain JVM; no desktop Android stubs are mistaken for device validation. */
public final class ConnectionAuthorizationTest {
    private static void check(boolean condition){if(!condition)throw new AssertionError();}
    public static void main(String[] args){
        String client="com.vivo.musicwidgetmix",root="ampp.external.media";
        ConnectionAuthorization gate=new ConnectionAuthorization();
        // OEM reconnects before the plugin has finished loading: defer instead of reject.
        check(root.equals(gate.authorize(client,100,true,null,root,new String[]{client})));
        check(!gate.ready(null,false));check(!gate.ready((name,uid)->root,false));
        check(gate.ready((name,uid)->root,true));
        // A previously declared bootstrap client must still be accepted by the live plugin.
        check(!gate.ready((name,uid)->null,true));check(!gate.ready((name,uid)->"different",true));
        // Spoofed caller and undeclared early client are rejected before any token publication.
        check(gate.authorize(client,200,false,null,root,new String[]{client})==null);
        check(gate.authorize("other.app",300,true,null,root,new String[]{client})==null);
        // A loaded endpoint can grant its own root, but its rejection never falls back to bootstrap.
        gate.clear();check(gate.authorize(client,100,true,(name,uid)->null,root,new String[]{client})==null);
        check("custom".equals(gate.authorize("other.app",300,true,(name,uid)->"custom",root,new String[]{client})));
        check(gate.ready((name,uid)->"custom",true));
        System.out.println("Deferred connection, revalidation, caller ownership and deny-path scenarios passed.");
    }
}
