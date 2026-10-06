package com.tcrrry.lyrics.plugin;
public final class Regression {
    private static void equal(String expected, String actual) { if (!expected.equals(actual)) throw new AssertionError(actual); }
    public static void main(String[] args) {
        equal("<p><span begin=\"0s\" end=\"1s\">stay</span> <span begin=\"1s\" end=\"2s\">forever</span></p>",
          TtmlNormalizer.spaces("<p><span begin=\"0s\" end=\"1s\">stay </span><span begin=\"1s\" end=\"2s\">forever</span></p>"));
        equal("<p><span>stay</span> <span>forever</span></p>", TtmlNormalizer.spaces("<p><span>stay</span><span begin=\"1s\" end=\"1.05s\"> </span><span>forever</span></p>"));
        String shortWords="<p><span begin=\"1s\" end=\"1.08s\">好</span><span begin=\"1.08s\" end=\"1.2s\">喜</span></p>";
        equal("<p><span begin=\"1s\" end=\"1.2s\">好喜</span></p>",TtmlNormalizer.smooth(shortWords));
        String english=shortWords.replace("好", "a ").replace("喜","word"); equal(english,TtmlNormalizer.smooth(english));
        String boundaries=shortWords.replace("</span><span", "</span></p><p><span"); equal(boundaries,TtmlNormalizer.smooth(boundaries));
        String background=shortWords.replace("<span begin", "<span role=\"background\" begin"); equal(background,TtmlNormalizer.smooth(background));
        System.out.println("6 parser compatibility regressions passed");
    }
}
