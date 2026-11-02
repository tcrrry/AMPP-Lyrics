package com.tcrrry.vivo;

import java.util.*;

/** One timed cue. Vivo's native LRC parser expands ^ after splitting timed records. */
final class LyricsPayload {
    static final class Row {
        final long time; final String original, pronunciation, translation;
        Row(long time,String original,String pronunciation,String translation) {
            this.time=time;this.original=original;this.pronunciation=pronunciation;this.translation=translation;
        }
    }
    static String compose(List<Row> rows,boolean translation,boolean pronunciation) {
        StringBuilder out=new StringBuilder();
        for(Row row:rows) {
            if(row.time<0 || row.original.isEmpty()) continue;
            LinkedHashSet<String> parts=new LinkedHashSet<>();
            if(pronunciation && !row.pronunciation.isEmpty()) parts.add(row.pronunciation);
            parts.add(row.original);
            if(translation && !row.translation.isEmpty()) parts.add(row.translation);
            long t=row.time;
            out.append(String.format(Locale.ROOT,"[%02d:%02d.%03d]",t/60000,t/1000%60,t%1000));
            out.append(String.join("^",parts));
            // Keep each cue together; put the extra separation BETWEEN cues, not inside them.
            // Native parsing trims before expanding ^, so trailing markers survive as display spacing.
            if(parts.size()>1)out.append("^^");
            out.append('\n');
            if(out.length()>256*1024) return "";
        }
        return out.toString();
    }
}
