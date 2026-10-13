import com.reandroid.apk.ApkModule;
import com.reandroid.apk.ResFile;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlElement;
import com.reandroid.arsc.chunk.xml.ResXmlDocument;
import com.reandroid.archive.BlockInputSource;
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

    static int qualifyLayoutBehaviors(ApkModule apk) throws Exception {
        int changed = 0;
        for (ResFile file : apk.listResFiles()) {
            if (!"layout".equals(file.getTypeNameFromPath()) || !file.isBinaryXml()) continue;
            ResXmlDocument document = apk.loadResXmlDocument(file.getFilePath());
            Iterator<ResXmlElement> elements = document.recursiveElements();
            boolean fileChanged = false;
            while (elements.hasNext()) {
                Iterator<ResXmlAttribute> attributes = elements.next().getAttributes();
                while (attributes.hasNext()) {
                    ResXmlAttribute attribute = attributes.next();
                    String value = attribute.getValueAsString();
                    // CoordinatorLayout resolves leading dots against Context's
                    // installation package. Host classes keep their original names.
                    if ("layout_behavior".equals(attribute.getName()) && value != null && value.startsWith(".")) {
                        attribute.setValueAsString(ORIGINAL + value);
                        fileChanged = true;
                        changed++;
                    }
                }
            }
            if (fileChanged) {
                document.refreshFull();
                apk.add(new BlockInputSource<>(file.getInputSource(), document));
            }
        }
        return changed;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2 && args.length != 3) throw new IllegalArgumentException("input.apk output.apk [label]");
        ApkModule apk = ApkModule.loadApkFile(new File(args[0]));
        AndroidManifestBlock manifest = apk.getAndroidManifest();
        if (!ORIGINAL.equals(manifest.getPackageName())) throw new IllegalArgumentException("Wrong host");
        // Resolve relative component names before changing the manifest package.
        manifest.ensureFullClassNames();
        manifest.getManifestElement().removeAttributesWithId(0x0101000b); // sharedUserId
        manifest.getManifestElement().removeAttributesWithId(0x01010261); // sharedUserLabel
        manifest.setPackageName(TARGET);
        manifest.setApplicationLabel(args.length == 3 ? args[2] : "AM++ Lyrics 共存测试版");
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
        int qualifiedBehaviors = qualifyLayoutBehaviors(apk);
        apk.writeApk(new File(args[1]));
        apk.close();
        System.out.println("Prepared isolated host: " + TARGET);
        System.out.println("Qualified " + qualifiedBehaviors + " relative layout behavior classes");
    }
}
