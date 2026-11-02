import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Updates the existing shim in both loaders; never appends a duplicate class definition. */
public final class UpdateExternalMedia {
    static void rewrite(Path input,Path output,Map<String,byte[]> changes)throws Exception {
        if(Files.exists(output))throw new IOException("Refusing overwrite: "+output);
        Set<String> applied=new HashSet<>(),seen=new HashSet<>();
        try(ZipFile zip=new ZipFile(input.toFile());ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(output))){
            var entries=zip.entries();while(entries.hasMoreElements()){
                ZipEntry old=entries.nextElement();String name=old.getName();if(ReembedExternalMedia.signature(name))continue;
                if(!seen.add(name))throw new IOException("Duplicate entry: "+name);
                byte[] bytes=changes.get(name);if(bytes!=null)applied.add(name);else bytes=ReembedExternalMedia.read(zip,old);
                ReembedExternalMedia.entry(out,name,old.getMethod(),bytes);
            }
        }
        if(!applied.equals(changes.keySet()))throw new IOException("Missing replacement entry");
        int retained=0;
        try(ZipFile before=new ZipFile(input.toFile());ZipFile after=new ZipFile(output.toFile())){
            var entries=before.entries();while(entries.hasMoreElements()){
                ZipEntry old=entries.nextElement();String name=old.getName();if(ReembedExternalMedia.signature(name)||changes.containsKey(name))continue;
                if(!Arrays.equals(ReembedExternalMedia.read(before,old),ReembedExternalMedia.read(after,after.getEntry(name))))throw new IOException("Unexpected payload change: "+name);
                retained++;
            }
        }
        System.out.println(output.getFileName()+": unchanged="+retained+", replaced="+changes.keySet());
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=7)throw new IllegalArgumentException("base.apk module.apk shim.dex manifest.bin scratch-dir output.apk zipalign");
        Path scratch=Path.of(args[4]);Files.createDirectories(scratch);Path origin=scratch.resolve("origin.apk");
        byte[] outerShim;
        try(ZipFile base=new ZipFile(args[0])){
            Files.write(origin,ReembedExternalMedia.read(base,base.getEntry(ReembedExternalMedia.ORIGIN)));
            outerShim=ReembedExternalMedia.read(base,base.getEntry("classes3.dex"));
        }
        try(ZipFile zip=new ZipFile(origin.toFile())){
            if(!Arrays.equals(outerShim,ReembedExternalMedia.read(zip,zip.getEntry("classes5.dex"))))throw new IOException("Unexpected shim layout");
        }
        byte[] shim=Files.readAllBytes(Path.of(args[2]));
        Path modified=scratch.resolve("origin-updated.apk"),aligned=scratch.resolve("origin-aligned.apk");
        rewrite(origin,modified,Map.of("classes5.dex",shim));
        if(new ProcessBuilder(args[6],"-P","16","-f","4",modified.toString(),aligned.toString()).inheritIO().start().waitFor()!=0)throw new IOException("Origin alignment failed");
        rewrite(Path.of(args[0]),Path.of(args[5]),Map.of(ReembedExternalMedia.MODULE,Files.readAllBytes(Path.of(args[1])),ReembedExternalMedia.ORIGIN,Files.readAllBytes(aligned),"classes3.dex",shim,"AndroidManifest.xml",Files.readAllBytes(Path.of(args[3]))));
    }
}
