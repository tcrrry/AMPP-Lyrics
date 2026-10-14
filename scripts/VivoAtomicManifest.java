import com.reandroid.apk.ApkModule;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlElement;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

/** Add only vivo discovery to the existing exported Apple media browser service. */
class VivoAtomicManifest {
    static final String PACKAGE = "com.tcrrry.ampplyrics.coexist";
    static final String ACTION = "com.vivo.musicwidgetmix.support.service";
    static final String BROWSER = "android.media.browse.MediaBrowserService";
    static final String SERVICE = "com.apple.android.music.player.MediaPlaybackService";

    static ResXmlElement browserFilter(AndroidManifestBlock manifest) {
        if (!PACKAGE.equals(manifest.getPackageName())) throw new IllegalArgumentException("Wrong coexist host");
        ArrayList<ResXmlElement> services = new ArrayList<>();
        Iterator<ResXmlElement> elements = manifest.recursiveElements();
        while (elements.hasNext()) {
            ResXmlElement element = elements.next();
            if ("service".equals(element.getName()) && SERVICE.equals(AndroidManifestBlock.getAndroidNameValue(element))) services.add(element);
        }
        if (services.size() != 1) throw new IllegalStateException("Expected one MediaPlaybackService");
        ResXmlElement service = services.get(0);
        if (!service.getOrCreateAndroidAttribute("exported", 0x01010010).getValueAsBoolean()) throw new IllegalStateException("Browser service is not exported");
        ResXmlElement result = null;
        Iterator<ResXmlElement> filters = service.getElements();
        while (filters.hasNext()) {
            ResXmlElement filter = filters.next();
            if (!"intent-filter".equals(filter.getName())) continue;
            Iterator<ResXmlElement> children = filter.getElements();
            while (children.hasNext()) {
                ResXmlElement child = children.next();
                if ("action".equals(child.getName()) && BROWSER.equals(AndroidManifestBlock.getAndroidNameValue(child))) {
                    if (result != null) throw new IllegalStateException("Duplicate browser filters");
                    result = filter;
                }
            }
        }
        if (result == null) throw new IllegalStateException("Missing browser filter");
        return result;
    }

    static int actions(Iterator<ResXmlElement> elements) {
        int count = 0;
        while (elements.hasNext()) {
            ResXmlElement element = elements.next();
            if ("action".equals(element.getName()) && ACTION.equals(AndroidManifestBlock.getAndroidNameValue(element))) count++;
        }
        return count;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("input.apk output.apk | --verify apk");
        boolean verify = "--verify".equals(args[0]);
        try (ApkModule apk = ApkModule.loadApkFile(new File(args[verify ? 1 : 0]))) {
            AndroidManifestBlock manifest = apk.getAndroidManifest();
            ResXmlElement filter = browserFilter(manifest);
            int count = actions(manifest.recursiveElements());
            if (verify) {
                if (count != 1 || actions(filter.recursiveElements()) != 1) throw new IllegalStateException("Expected one vivo action in the exported browser filter");
            } else {
                if (count != 0) throw new IllegalStateException("Action already present");
                filter.newElement("action").getOrCreateAndroidAttribute("name", 0x01010003).setValueAsString(ACTION);
                manifest.refreshFull();
                apk.writeApk(new File(args[1]));
            }
        }
        System.out.println(verify ? "PASS: vivo action and exported browser service verified" : "Added vivo discovery action; no new service or playback code");
    }
}
