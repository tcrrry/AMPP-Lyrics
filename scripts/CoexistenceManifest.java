import com.reandroid.apk.ApkModule;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlElement;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

/** Rewrite only the installation identity; retain host DEX and resource namespace. */
class CoexistenceManifest {
    static final String ORIGINAL = "com.apple.android.music";
    static final String TARGET = "com.tcrrry.ampplyrics.coexist";

    static String isolated(String value) {
        if (value == null) return null;
        if (value.equals(ORIGINAL) || value.startsWith(ORIGINAL + ".")) {
            return TARGET + value.substring(ORIGINAL.length());
        }
        return value;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("input.apk output.apk");
        ApkModule apk = ApkModule.loadApkFile(new File(args[0]));
        AndroidManifestBlock manifest = apk.getAndroidManifest();
        if (!ORIGINAL.equals(manifest.getPackageName())) throw new IllegalArgumentException("Wrong host");
        // Resolve relative component names before changing the manifest package.
        manifest.ensureFullClassNames();
        manifest.getManifestElement().removeAttributesWithId(0x0101000b); // sharedUserId
        manifest.getManifestElement().removeAttributesWithId(0x01010261); // sharedUserLabel
        manifest.setPackageName(TARGET);
        manifest.setApplicationLabel("AM++ Lyrics 共存测试版");
        Iterator<ResXmlElement> elements = manifest.recursiveElements();
        ArrayList<ResXmlElement> filters = new ArrayList<>();
        while (elements.hasNext()) {
            ResXmlElement element = elements.next();
            if ("intent-filter".equals(element.getName())) filters.add(element);
            Iterator<ResXmlAttribute> attributes = element.getAttributes();
            while (attributes.hasNext()) {
                ResXmlAttribute attribute = attributes.next();
                String name = attribute.getName();
                boolean permissionName = "name".equals(name) &&
                    ("permission".equals(element.getName()) || "uses-permission".equals(element.getName()));
                if (permissionName || "permission".equals(name) || "readPermission".equals(name) ||
                    "writePermission".equals(name) || "authorities".equals(name)) {
                    String value = attribute.getValueAsString();
                    if (value != null) attribute.setValueAsString(isolated(value));
                }
            }
        }
        for (ResXmlElement filter : filters) {
            boolean browsable = false;
            boolean launcher = false;
            Iterator<ResXmlElement> children = filter.getElements();
            while (children.hasNext()) {
                ResXmlElement child = children.next();
                String name = AndroidManifestBlock.getAndroidNameValue(child);
                if ("android.intent.category.BROWSABLE".equals(name)) browsable = true;
                if ("android.intent.category.LAUNCHER".equals(name)) launcher = true;
            }
            // Keep external Apple links/auth callbacks assigned to the official app.
            if (browsable && !launcher) {
                filter.removeSelf();
            }
        }
        manifest.getMainActivity().removeAttributesWithId(0x01010001);
        apk.writeApk(new File(args[1]));
        apk.close();
        System.out.println("Prepared isolated host: " + TARGET);
    }
}
