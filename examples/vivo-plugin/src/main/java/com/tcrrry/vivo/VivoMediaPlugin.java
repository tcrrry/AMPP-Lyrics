package com.tcrrry.vivo;

import android.content.Context;
import dev.amenhancer.plugin.api.*;

/** The public entry links only to SDK v1, so stock hosts can report a useful error. */
public final class VivoMediaPlugin extends AmppPlugin {
    private AmppPlugin delegate;
    @Override public void onLoad(PluginContext context) throws Exception {
        try {
            Class.forName("dev.amenhancer.plugin.api.PluginMediaEndpoint", false, PluginContext.class.getClassLoader());
            PluginContext.class.getMethod("getExternalMedia");
        } catch (ReflectiveOperationException | LinkageError error) {
            throw new PluginUnsupportedException("需要安装配套 External Media 宿主；仅导入 ZIP 无法添加系统服务声明与媒体接口");
        }
        // Keep the optional interface out of the entry class's verifier path.
        delegate = (AmppPlugin) Class.forName("com.tcrrry.vivo.VivoMediaEndpointPlugin", true,
                getClass().getClassLoader()).getDeclaredConstructor().newInstance();
        delegate.onLoad(context);
    }
    @Override public void onStart() throws Exception { if (delegate != null) delegate.onStart(); }
    @Override public void onStop() throws Exception { if (delegate != null) delegate.onStop(); }
    @Override public PluginSettingsSession createSettings(Context context) throws Exception {
        return delegate == null ? null : delegate.createSettings(context);
    }
}
