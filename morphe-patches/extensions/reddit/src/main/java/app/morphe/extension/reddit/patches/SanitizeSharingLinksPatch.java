/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.reddit.patches;

import app.morphe.extension.reddit.settings.Settings;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public final class SanitizeSharingLinksPatch {

    /**
     * Tracking query parameters removed from Reddit links by {@link #cleanShareText(String)}.
     * Unlike the formatter hook, which strips the whole query, this fallback only removes
     * these known tracking parameters and leaves every other parameter byte-identical.
     */
    private static final Set<String> REMOVED_QUERY_KEYS = new HashSet<>(Arrays.asList(
            "share_id", "p", "ref", "ref_source", "ref_campaign", "correlation_id",
            "_branch_match_id", "_branch_referrer", "$deep_link", "$3p"));

    /**
     * Links in prose pick up trailing punctuation, which is not part of the link and must
     * stay in the text.
     */
    private static final String TRAILING_PUNCTUATION = ".,;:!?)]}\"'";

    /**
     * An http(s) link ends at the first whitespace or angle bracket or quote.
     */
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s<>\"']+");

    /**
     * @return If this patch was included during patching.
     */
    @SuppressWarnings("SameReturnValue")
    public static boolean isPatchIncluded() {
        return false;  // Modified during patching.
    }

    /**
     * Injection point.
     */
    public static boolean stripQueryParameters() {
        return Settings.SANITIZE_SHARING_LINKS.get();
    }

    /**
     * Injection point.
     * Short links (/r/subreddit/s/id) redirect to the full link with a unique share_id.
     */
    public static boolean disableShortLinks() {
        return Settings.SANITIZE_SHARING_LINKS.get();
    }

    /**
     * Injection point.
     * Fallback for share paths that bypass the link formatter (clipboard, ACTION_SEND
     * extras): cleans the Reddit links inside {@code text} by removing their tracking
     * query parameters. Only Reddit links are rewritten, and only the parameters of
     * {@link #REMOVED_QUERY_KEYS} (plus any {@code utm_} parameter) are removed; the rest
     * of the text is left byte-identical. Short links (/s/) are not touched here.
     *
     * @return The cleaned text, or the exact object passed in when the setting is off,
     *         when the text holds no link, or when nothing was removed.
     */
    public static String cleanShareText(String text) {
        if (text == null || !Settings.SANITIZE_SHARING_LINKS.get()) {
            return text;
        }

        Matcher matcher = URL_PATTERN.matcher(text);
        StringBuilder result = null;
        int copiedUpTo = 0;
        while (matcher.find()) {
            int linkEnd = matcher.end();
            while (linkEnd > matcher.start()
                    && TRAILING_PUNCTUATION.indexOf(text.charAt(linkEnd - 1)) >= 0) {
                linkEnd--;
            }
            String cleanedLink = cleanRedditUrl(text.substring(matcher.start(), linkEnd));
            if (cleanedLink == null) {
                continue;
            }

            if (result == null) {
                result = new StringBuilder(text.length());
            }
            result.append(text, copiedUpTo, matcher.start()).append(cleanedLink);
            copiedUpTo = linkEnd;
        }
        if (result == null) {
            return text;
        }

        result.append(text, copiedUpTo, text.length());
        return result.toString();
    }

    /**
     * @return The link without its tracking query parameters, or {@code null} if it is
     *         not a Reddit link or holds no parameter to remove.
     */
    private static String cleanRedditUrl(String url) {
        int hostStart = url.indexOf("://") + 3;
        int hostEnd = hostStart;
        while (hostEnd < url.length()) {
            char c = url.charAt(hostEnd);
            if (c == '/' || c == '?' || c == '#' || c == ':') {
                break;
            }
            hostEnd++;
        }

        String host = url.substring(hostStart, hostEnd).toLowerCase(Locale.ROOT);
        if (!host.equals("reddit.com") && !host.equals("redd.it")
                && !host.endsWith(".reddit.com") && !host.endsWith(".redd.it")) {
            return null;
        }

        int queryStart = url.indexOf('?', hostEnd);
        int fragmentStart = url.indexOf('#', hostEnd);
        if (queryStart < 0 || (fragmentStart >= 0 && fragmentStart < queryStart)) {
            return null; // No query, or the '?' belongs to the fragment.
        }
        int queryEnd = fragmentStart >= 0 ? fragmentStart : url.length();

        String[] parameters = url.substring(queryStart + 1, queryEnd).split("&", -1);
        StringBuilder kept = new StringBuilder(queryEnd - queryStart);
        boolean changed = false;
        boolean firstKept = true;
        for (String parameter : parameters) {
            int valueStart = parameter.indexOf('=');
            String key = percentDecode(valueStart >= 0 ? parameter.substring(0, valueStart) : parameter);
            if (REMOVED_QUERY_KEYS.contains(key) || key.startsWith("utm_")) {
                changed = true;
                continue;
            }
            // The separator comes before every kept parameter but the first, so an
            // empty parameter ("a&&b") keeps its place.
            if (!firstKept) {
                kept.append('&');
            }
            kept.append(parameter);
            firstKept = false;
        }
        if (!changed) {
            return null;
        }

        return url.substring(0, queryStart)
                + (kept.length() > 0 ? "?" + kept : "")
                + url.substring(queryEnd);
    }

    /**
     * @return The key with its percent escapes decoded, so an encoded key like
     *         {@code %24deep_link} is recognized too.
     */
    private static String percentDecode(String key) {
        if (key.indexOf('%') < 0) {
            return key;
        }

        StringBuilder decoded = new StringBuilder(key.length());
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '%' && i + 2 < key.length()) {
                int high = Character.digit(key.charAt(i + 1), 16);
                int low = Character.digit(key.charAt(i + 2), 16);
                if (high >= 0 && low >= 0) {
                    decoded.append((char) ((high << 4) | low));
                    i += 2;
                    continue;
                }
            }
            decoded.append(c);
        }
        return decoded.toString();
    }
}
