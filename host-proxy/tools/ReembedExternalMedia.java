import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.security.*;

/** Retains NPatch 832 and all unchanged host payloads. Handles overlapping NPatch input ZIPs. */
public class ReembedExternalMedia {
    static final String ORIGIN="assets/npatch/origin.apk";
    static boolean signature(String name){return name.matches("(?i)META-INF/[^/]+\\.(SF|RSA|DSA|EC|MF)");}
    static byte[] read(ZipFile zip,ZipEntry e)throws Exception{try(InputStream in=zip.getInputStream(e)){return in.readAllBytes();}}
    static String nextDex(ZipFile zip) {
        int n=1;while(zip.getEntry(n==1?"classes.dex":"classes"+n+".dex")!=null)n++;
        return n==1?"classes.dex":"classes"+n+".dex";
    }
    static void entry(ZipOutputStream out,String name,int method,byte[] bytes)throws Exception {
        ZipEntry e=new ZipEntry(name);e.setTime(0);e.setMethod(method);
        if(method==ZipEntry.STORED){CRC32 crc=new CRC32();crc.update(bytes);e.setSize(bytes.length);e.setCompressedSize(bytes.length);e.setCrc(crc.getValue());}
        out.putNextEntry(e);out.write(bytes);out.closeEntry();
    }
    static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    static void rewrite(Path input,Path output,Map<String,byte[]> replace,byte[] dex)throws Exception {
        if(Files.exists(output))throw new IOException("Refusing overwrite: "+output);
        Set<String> applied=new HashSet<>();String dexName;
        try(ZipFile zip=new ZipFile(input.toFile());ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(output))) {
            dexName=nextDex(zip);Set<String> seen=new HashSet<>();Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()) {
                ZipEntry old=entries.nextElement();String name=old.getName();if(signature(name))continue;
                if(!seen.add(name))throw new IOException("Duplicate: "+name);
                byte[] bytes=replace.containsKey(name)?replace.get(name):read(zip,old);if(replace.containsKey(name))applied.add(name);
                entry(out,name,old.getMethod(),bytes);
            }
            entry(out,dexName,ZipEntry.DEFLATED,dex);
        }
        if(!applied.equals(replace.keySet()))throw new IOException("Missing replaced entries: "+replace.keySet());
        int retained=0;
        try(ZipFile before=new ZipFile(input.toFile());ZipFile after=new ZipFile(output.toFile())) {
            Enumeration<? extends ZipEntry> entries=before.entries();
            while(entries.hasMoreElements()) {
                ZipEntry e=entries.nextElement();if(signature(e.getName())||replace.containsKey(e.getName()))continue;
                if(!sha(read(before,e)).equals(sha(read(after,after.getEntry(e.getName())))))throw new IOException("Payload changed: "+e.getName());
                retained++;
            }
        }
        System.out.println(output.getFileName()+": retained="+retained+", replaced="+replace.keySet()+", added="+dexName);
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=7)throw new IllegalArgumentException("base.apk module.apk shim.dex manifest.bin scratch-dir output.apk zipalign-executable");
        Path scratch=Path.of(args[4]);Files.createDirectories(scratch);Path origin=scratch.resolve("origin-input.apk");
        String module;
        try(ZipFile base=new ZipFile(args[0])) {
            Files.write(origin,read(base,base.getEntry(ORIGIN)));
            List<String> modules=base.stream().map(ZipEntry::getName).filter(n -> n.startsWith("assets/npatch/modules/") && n.endsWith(".apk")).toList();
            if(modules.size()!=1)throw new IOException("Expected exactly one embedded module");
            module=modules.get(0);
        }
        byte[] dex=Files.readAllBytes(Path.of(args[2]));Path modified=scratch.resolve("origin-with-proxy.apk");
        rewrite(origin,modified,Map.of(),dex);
        Path aligned=scratch.resolve("origin-aligned.apk");
        int status=new ProcessBuilder(args[6],"-P","16","-f","4",modified.toString(),aligned.toString()).inheritIO().start().waitFor();
        if(status!=0)throw new IOException("Embedded origin zipalign failed: "+status);
        rewrite(Path.of(args[0]),Path.of(args[5]),Map.of(module,Files.readAllBytes(Path.of(args[1])),ORIGIN,Files.readAllBytes(aligned),"AndroidManifest.xml",Files.readAllBytes(Path.of(args[3]))),dex);
    }
}
