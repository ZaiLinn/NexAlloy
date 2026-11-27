package io.github.nexalloy.revanced.meta.filter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Ported from Threads Sieve (dev.threads.sieve.ScriptFilter).
 * Character evidence, not a claim to infer a writer's locale or identity.
 */
public final class ScriptFilter {
    private final Set<Integer> simplified = new HashSet<>();
    private final Set<Integer> traditional = new HashSet<>();

    public ScriptFilter(InputStream dictionary) throws IOException {
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(dictionary, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || !line.contains("\t")) continue;
                String[] parts = line.split("\t", 2);
                int key = parts[0].codePointAt(0);
                boolean different = false, shared = false;
                for (String value : parts[1].trim().split(" +")) {
                    int cp = value.codePointAt(0);
                    if (cp != key) {
                        traditional.add(cp);
                        different = true;
                    } else {
                        shared = true;
                    }
                }
                if (different && !shared) simplified.add(key);
            }
        }
        // Ambiguous characters that occur on both sides are not discriminators.
        Set<Integer> overlap = new HashSet<>(simplified);
        overlap.retainAll(traditional);
        simplified.removeAll(overlap);
        traditional.removeAll(overlap);
    }

    public boolean blocks(CharSequence text, boolean blockSimplified, boolean blockTraditional) {
        if (text == null || (!blockSimplified && !blockTraditional)) return false;
        for (int i = 0; i < text.length(); ) {
            int cp = Character.codePointAt(text, i);
            i += Character.charCount(cp);
            if (blockSimplified && blockTraditional && isHan(cp)) return true;
            if (blockSimplified && simplified.contains(cp)) return true;
            if (blockTraditional && traditional.contains(cp)) return true;
        }
        return false;
    }

    private static boolean isHan(int cp) {
        return Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN;
    }
}
