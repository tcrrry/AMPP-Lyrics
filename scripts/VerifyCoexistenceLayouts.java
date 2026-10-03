import com.reandroid.apk.ApkModule;
import com.reandroid.apk.ResFile;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlElement;
import java.io.File;
import java.util.Iterator;

/** Validate the emitted APK, including NPatch's embedded origin, before upload. */
class VerifyCoexistenceLayouts {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("apk [embedded-origin.apk]");
        for (String path : args) {
            boolean libraryBehaviorFound = false;
            try (ApkModule apk = ApkModule.loadApkFile(new File(path))) {
                for (ResFile file : apk.listResFiles()) {
                    if (!"layout".equals(file.getTypeNameFromPath()) || !file.isBinaryXml()) continue;
                    Iterator<ResXmlElement> elements = apk.loadResXmlDocument(file.getFilePath()).recursiveElements();
                    while (elements.hasNext()) {
                        Iterator<ResXmlAttribute> attributes = elements.next().getAttributes();
                        while (attributes.hasNext()) {
                            ResXmlAttribute attribute = attributes.next();
                            if (!"layout_behavior".equals(attribute.getName())) continue;
                            String value = attribute.getValueAsString();
                            if (value != null && value.startsWith(".")) {
                                throw new IllegalStateException("Relative behavior in " + file.getFilePath() + ": " + value);
                            }
                            if ("library_details_page_fragment".equals(file.getEntryNameFromPath()) &&
                                "com.apple.android.music.collection.mediaapi.fragment.ScrollConfigurableAppBarLayoutBehavior".equals(value)) {
                                libraryBehaviorFound = true;
                            }
                        }
                    }
                }
            }
            if (!libraryBehaviorFound) throw new IllegalStateException("Missing original library page behavior: " + path);
            System.out.println("PASS: library behavior and all layout behavior class names verified: " + path);
        }
    }
}
