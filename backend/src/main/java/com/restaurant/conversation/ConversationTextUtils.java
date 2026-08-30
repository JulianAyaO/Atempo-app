package com.restaurant.conversation;

import java.util.ArrayList;
import java.util.List;

final class ConversationTextUtils {

    private ConversationTextUtils() {}

    static String normalizeMessage(String msg) {
        return stripAccents(msg.toLowerCase().trim()
            .replaceAll("[¡!¿?.,;:]", ""));
    }

    static String stripAccents(String msg) {
        return msg
            .replaceAll("á", "a").replaceAll("é", "e").replaceAll("í", "i")
            .replaceAll("ó", "o").replaceAll("ú", "u").replaceAll("ñ", "n");
    }

    static String normalizeKeepingListSeparators(String msg) {
        return stripAccents(msg.toLowerCase().trim()
            .replaceAll("[¡!¿?;:]", " "));
    }

    static List<String> splitOrderSegments(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        String prepared = normalizeKeepingListSeparators(raw);
        List<String> commaParts = new ArrayList<>();
        for (String part : prepared.split("\\s*,\\s*")) {
            if (!part.isBlank()) commaParts.add(part.trim());
        }
        List<String> segments = new ArrayList<>();
        for (String part : commaParts) {
            String[] andParts = part.split("\\s+y\\s+(?=un\\b|una\\b|unos\\b|unas\\b|el\\b|la\\b|los\\b|las\\b|otro\\b|otra\\b|tambien\\b|\\d+)");
            if (andParts.length == 1) {
                andParts = part.split("\\s+y\\s+");
            }
            for (String ap : andParts) {
                String t = ap.trim();
                if (!t.isBlank()) segments.add(t.replaceAll("[.,]", " ").replaceAll("\\s+", " ").trim());
            }
        }
        return segments;
    }

    static String normalizeProductName(String name) {
        return name.toLowerCase().replaceAll("\\(.*\\)", "").trim()
            .replaceAll("á", "a").replaceAll("é", "e").replaceAll("í", "i")
            .replaceAll("ó", "o").replaceAll("ú", "u").replaceAll("ñ", "n");
    }

    static boolean containsPhrase(String msg, String phrase) {
        if (msg == null || phrase == null || phrase.isBlank()) return false;
        String p = stripAccents(phrase.toLowerCase().trim()).replaceAll("[¡!¿?.,;:]", "");
        if (p.length() < 3) return false;
        String regex = "(?i)(?<![a-z0-9])" + java.util.regex.Pattern.quote(p) + "(?![a-z0-9])";
        return java.util.regex.Pattern.compile(regex).matcher(msg).find();
    }

    static boolean matchesAny(String msg, String... patterns) {
        for (String p : patterns) {
            if (msg.contains(p)) return true;
        }
        return false;
    }

    static String stem(String token) {
        if (token == null) return "";
        String t = token.trim();
        if (t.length() > 4 && t.endsWith("es")) return t.substring(0, t.length() - 2);
        if (t.length() > 3 && t.endsWith("s")) return t.substring(0, t.length() - 1);
        return t;
    }

    static boolean mentionsToken(String msg, String rawToken) {
        if (msg == null || rawToken == null || rawToken.isBlank()) return false;
        String token = normalizeMessage(rawToken);
        if (token.length() < 3) return false;
        if (msg.contains(token)) return true;
        String tokenStem = stem(token);
        if (tokenStem.length() >= 4 && msg.contains(tokenStem)) return true;
        for (String word : msg.split("\\s+")) {
            if (word.length() < 4) continue;
            if (tokensClose(word, token) || tokensClose(stem(word), tokenStem)) return true;
        }
        return false;
    }

    static boolean tokensClose(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) return false;
        if (a.equals(b)) return true;
        int min = Math.min(a.length(), b.length());
        if (min < 5) return false;
        return levenshtein(a, b) <= (min >= 8 ? 2 : 1);
    }

    static int levenshtein(String a, String b) {
        int n = a.length();
        int m = b.length();
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[m];
    }
}
