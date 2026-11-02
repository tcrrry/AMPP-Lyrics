import com.reandroid.apk.ApkModule;
import com.reandroid.arsc.chunk.xml.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Emits only the changed binary manifest; all other APK entries come from the input. */
public class ExternalMediaManifest {
    static final String NAME="dev.amenhancer.hostproxy.ExternalMediaService";
    public static void main(String[] args) throws Exception {
        if(args.length!=3) throw new IllegalArgumentException("input.apk scratch.apk AndroidManifest.xml");
        try(ApkModule apk=ApkModule.loadApkFile(new File(args[0]))) {
            AndroidManifestBlock manifest=apk.getAndroidManifest();
            if(!"com.apple.android.music".equals(manifest.getPackageName()) && !"com.tcrrry.ampplyrics.coexist".equals(manifest.getPackageName())) throw new IllegalStateException("Wrong host");
            ResXmlElement application=null,service=null;
            Iterator<ResXmlElement> elements=manifest.recursiveElements();
            while(elements.hasNext()) {
                ResXmlElement e=elements.next();
                if("application".equals(e.getName())) application=e;
                if("service".equals(e.getName()) && NAME.equals(AndroidManifestBlock.getAndroidNameValue(e))) service=e;
            }
            if(application==null) throw new IllegalStateException("No application");
            boolean added=service==null;
            if(added)service=application.newElement("service");
            service.getOrCreateAndroidAttribute("name",0x01010003).setValueAsString(NAME);
            service.getOrCreateAndroidAttribute("enabled",0x0101000e).setValueAsBoolean(false);
            service.getOrCreateAndroidAttribute("exported",0x01010010).setValueAsBoolean(true);
            if(added){
                ResXmlElement filter=service.newElement("intent-filter");
                for(String action:new String[]{"android.media.browse.MediaBrowserService","com.vivo.musicwidgetmix.support.service"})
                    filter.newElement("action").getOrCreateAndroidAttribute("name",0x01010003).setValueAsString(action);
            }
            for(String[] config:new String[][]{{"ampp.external.media.bootstrap_root","ampp.external.media"},{"ampp.external.media.bootstrap_clients","com.vivo.musicwidgetmix"}}){
                ResXmlElement entry=service.newElement("meta-data");
                entry.getOrCreateAndroidAttribute("name",0x01010003).setValueAsString(config[0]);
                entry.getOrCreateAndroidAttribute("value",0x01010024).setValueAsString(config[1]);
            }
            manifest.refreshFull();apk.writeApk(new File(args[1]));
        }
        try(ZipFile zip=new ZipFile(args[1]);InputStream in=zip.getInputStream(zip.getEntry("AndroidManifest.xml"))) {
            java.nio.file.Files.write(java.nio.file.Path.of(args[2]),in.readAllBytes());
        }
        System.out.println("Emitted default-disabled external media proxy manifest");
    }
}
