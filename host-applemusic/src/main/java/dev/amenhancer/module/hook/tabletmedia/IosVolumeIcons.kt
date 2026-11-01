package dev.amenhancer.module.hook.tabletmedia

// Adapted from Kifranei/AM-plus-plus-media-plugin, 9e3f72b (GPL-3.0).
import dev.amenhancer.module.hook.ModernXposedRuntime
import dev.amenhancer.host.applemusic.AppleMusicHostProfiles

import android.graphics.Path
import android.util.Xml
import androidx.core.graphics.PathParser

import org.xmlpull.v1.XmlPullParser

/** Exact 24dp vectors copied from Flamingo's native now-playing resources. */
internal class IosVolumeIcons(val low: Path, val high: Path) {
    companion object {
        // YouLyPlus / AMLL VolumeControlSlider SVGs, original 32x40 / 43x40 viewBoxes.
        fun reference(): Pair<List<Path>, List<Path>> {
            fun parse(data: String) = checkNotNull(PathParser.createPathFromPathData(data))
            return listOf(
                parse("M14.9042 27.1802C14.4202 27.1802 14.0473 26.9897 13.595 26.5612L10.3815 23.5461C10.3339 23.5065 10.2863 23.4906 10.2228 23.4906H8.01703C6.70778 23.4906 5.99365 22.7527 5.99365 21.38V18.4442C5.99365 17.0715 6.70778 16.3257 8.01703 16.3257H10.2307C10.2863 16.3257 10.3418 16.3019 10.3815 16.2622L13.595 13.2709C14.079 12.8107 14.4361 12.6282 14.8883 12.6282C15.6104 12.6282 16.142 13.1915 16.142 13.8977V25.9344C16.142 26.6406 15.6104 27.1802 14.9042 27.1802Z")
            ) to listOf(
                parse("M24.0403 27.1802C23.5642 27.1802 23.1913 26.9897 22.739 26.5612L19.5176 23.5461C19.4779 23.5065 19.4224 23.4906 19.3668 23.4906H17.161C15.8518 23.4906 15.1377 22.7527 15.1377 21.38V18.4442C15.1377 17.0715 15.8518 16.3257 17.161 16.3257H19.3668C19.4303 16.3257 19.4779 16.3019 19.5255 16.2622L22.739 13.2709C23.223 12.8107 23.5721 12.6282 24.0324 12.6282C24.7544 12.6282 25.286 13.1915 25.286 13.8977V25.9344C25.286 26.6406 24.7544 27.1802 24.0403 27.1802Z"),
                parse("M28.0948 23.6653C27.6028 23.3559 27.4996 22.7687 27.8964 22.1101C28.2931 21.4991 28.5232 20.7136 28.5232 19.8964C28.5232 19.0712 28.301 18.2856 27.8964 17.6826C27.4917 17.032 27.6028 16.4369 28.0948 16.1274C28.547 15.8418 29.1104 15.9529 29.404 16.3576C30.0863 17.3097 30.491 18.5713 30.491 19.8964C30.491 21.2214 30.0863 22.4831 29.404 23.4273C29.1104 23.8399 28.547 23.943 28.0948 23.6653Z"),
                parse("M31.6733 25.8711C31.1576 25.5696 31.0942 24.9428 31.4432 24.3794C32.2526 23.1257 32.7207 21.5468 32.7207 19.8964C32.7207 18.2459 32.2605 16.6591 31.4432 15.4133C31.0942 14.8499 31.1576 14.2231 31.6733 13.9137C32.1415 13.6439 32.7128 13.755 33.0143 14.2152C34.0855 15.7783 34.6885 17.8016 34.6885 19.8964C34.6885 21.9911 34.0775 23.9985 33.0143 25.5775C32.7128 26.0377 32.1415 26.1488 31.6733 25.8711Z"),
                parse("M35.2362 28.1007C34.7363 27.7992 34.6569 27.1803 34.9981 26.6249C36.1883 24.7286 36.9104 22.4196 36.9104 19.9122C36.9104 17.397 36.1883 15.0881 34.9981 13.1917C34.6569 12.6362 34.7363 12.0174 35.2362 11.7159C35.7123 11.4302 36.3073 11.5651 36.6088 12.0571C38.0133 14.2866 38.8702 16.9765 38.8702 19.9122C38.8702 22.8401 38.0291 25.5379 36.6088 27.7675C36.3073 28.2515 35.7123 28.3864 35.2362 28.1007Z")
            )
        }
        fun read(context: TabletMediaAssets): IosVolumeIcons {
            fun icon(asset: String, count: Int): Path = context.openAsset(asset).use { input ->
                val parser = Xml.newPullParser().apply { setInput(input, "UTF-8") }
                val ns = "http://schemas.android.com/apk/res/android"
                val result = Path()
                var paths = 0
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType != XmlPullParser.START_TAG) continue
                    when (parser.name) {
                        "vector" -> check(parser.getAttributeValue(ns, "viewportWidth").toFloat() == 24f &&
                            parser.getAttributeValue(ns, "viewportHeight").toFloat() == 24f)
                        "path" -> {
                            result.addPath(checkNotNull(PathParser.createPathFromPathData(parser.getAttributeValue(ns, "pathData"))))
                            paths++
                        }
                    }
                }
                check(paths == count)
                result
            }
            return IosVolumeIcons(icon("flamingo-volume-low.xml", 1), icon("flamingo-volume-high.xml", 4))
        }
    }
}

