package io.github.k8nowplaying.helper;

/** Exposes the helper's package-private filters to the plain-JVM test. */
public final class KeyHelperAccess {
    private KeyHelperAccess() {}

    public static int parseKey(String s) {
        return KeyHelper.parseKey(s);
    }

    public static int uidFromLine(String s) {
        return KeyHelper.uidFromLine(s);
    }
}
