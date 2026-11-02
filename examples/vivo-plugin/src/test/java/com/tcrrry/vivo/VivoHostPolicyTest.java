package com.tcrrry.vivo;
import org.junit.Test;
import static org.junit.Assert.*;
public class VivoHostPolicyTest {
 @Test public void supportsOnlyExactOriginalAndCoexistHostVersions() {
  assertTrue(VivoMediaEndpointPlugin.supportsHost("com.apple.android.music", "7.0.0", 1614));
  assertTrue(VivoMediaEndpointPlugin.supportsHost("com.tcrrry.ampplyrics.coexist", "7.0.0", 1614));
  assertTrue(VivoMediaEndpointPlugin.supportsHost("com.apple.android.music", "7.0.0-beta", 1613));
  assertFalse(VivoMediaEndpointPlugin.supportsHost("com.apple.android.music", "7.0.0-beta", 1614));
  assertFalse(VivoMediaEndpointPlugin.supportsHost("other.package", "7.0.0", 1614));
 }
}
