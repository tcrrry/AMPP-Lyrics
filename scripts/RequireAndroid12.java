import com.reandroid.apk.ApkModule;
import java.io.File;

/** The bundled SDK lyrics plugin requires API 31 even though the original host accepts API 30. */
class RequireAndroid12 {
    public static void main(String[] args) throws Exception {
        ApkModule apk = ApkModule.loadApkFile(new File(args[0]));
        apk.getAndroidManifest().setMinSdkVersion(31);
        apk.writeApk(new File(args[1]));
        apk.close();
    }
}
