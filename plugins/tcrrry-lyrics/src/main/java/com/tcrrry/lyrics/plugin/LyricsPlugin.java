package com.tcrrry.lyrics.plugin;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import dev.amenhancer.plugin.api.*;
import java.io.*;
import java.lang.reflect.Method;
import java.util.Properties;

public final class LyricsPlugin extends AmppPlugin {
    private PluginContext runtime;
    private volatile boolean fixSpaces = true, smooth = false;
    private File settingsFile;
    @Override public void onLoad(PluginContext context) throws Exception {
        runtime = context; settingsFile = new File(context.getDataDirectory(), "settings.properties");
        Properties values = new Properties();
        if (settingsFile.isFile()) try (InputStream in = new FileInputStream(settingsFile)) { values.load(in); }
        fixSpaces = Boolean.parseBoolean(values.getProperty("spaces", "true"));
        smooth = Boolean.parseBoolean(values.getProperty("smooth", "false"));
        Class<?> parser = context.getHostClassLoader().loadClass("com.apple.android.music.ttml.javanative.TTMLParser$TTMLParserNative");
        Method method = parser.getDeclaredMethod("songInfoFromTTML", String.class);
        if (!method.getReturnType().getName().equals("com.apple.android.music.ttml.javanative.model.SongInfo$SongInfoPtr"))
            throw new PluginUnsupportedException("不支持的 TTML 解析接口，已停止加载");
        context.getHooks().hook(method, false, new PluginHook() {
            @Override public void before(PluginCall call) {
                Object[] args = call.getArguments();
                if (!(args[0] instanceof String)) return;
                String text = (String)args[0];
                if (fixSpaces) text = TtmlNormalizer.spaces(text);
                if (smooth) text = TtmlNormalizer.smooth(text);
                args[0] = text;
            }
        });
        context.log("Tcrrry parser compatibility prepared; independent settings", null);
    }
    private synchronized void save() throws IOException {
        Properties values = new Properties(); values.setProperty("spaces", Boolean.toString(fixSpaces));
        values.setProperty("smooth", Boolean.toString(smooth));
        File temporary = new File(settingsFile.getParentFile(), "settings.properties.tmp");
        try (FileOutputStream out = new FileOutputStream(temporary)) { values.store(out, "Tcrrry Lyrics"); out.getFD().sync(); }
        if (!temporary.renameTo(settingsFile)) throw new IOException("Cannot commit settings");
    }
    @Override public PluginSettingsSession createSettings(Context context) {
        LinearLayout layout = new LinearLayout(context); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(24,24,24,24);
        TextView help = new TextView(context); help.setText("Tcrrry 歌词兼容优化\n独立插件：修复英文空格；可选合并相邻短字。设置在重新加载歌词后生效。此版本不含第三方搜词、翻译和发音。当前正式版四种安装包不能加载插件。"); layout.addView(help);
        Switch spaces = new Switch(context); spaces.setText("保留独立英文词间空格"); spaces.setChecked(fixSpaces); layout.addView(spaces);
        Switch units = new Switch(context); units.setText("平滑少于 100ms 的相邻短字（默认关闭）"); units.setChecked(smooth); layout.addView(units);
        spaces.setOnCheckedChangeListener((button, checked) -> { fixSpaces=checked; try { save(); } catch (IOException e) { runtime.log("Settings save failed",e); } });
        units.setOnCheckedChangeListener((button, checked) -> { smooth=checked; try { save(); } catch (IOException e) { runtime.log("Settings save failed",e); } });
        return new PluginSettingsSession() {
            @Override public android.view.View getView() { return layout; }
            @Override public void close() { spaces.setOnCheckedChangeListener(null); units.setOnCheckedChangeListener(null); }
        };
    }
}
