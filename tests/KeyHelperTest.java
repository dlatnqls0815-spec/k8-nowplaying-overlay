import io.github.k8nowplaying.helper.KeyHelperAccess;

/** Plain-JVM checks for the helper's input filters (no Android runtime needed). */
public final class KeyHelperTest {
    static int failures;

    public static void main(String[] args) {
        eq(KeyHelperAccess.parseKey("166"), 166, "prev");
        eq(KeyHelperAccess.parseKey("167\r"), 167, "next with CR");
        eq(KeyHelperAccess.parseKey(" 91 "), 91, "play/pause");
        eq(KeyHelperAccess.parseKey("164"), -1, "volume mute refused");
        eq(KeyHelperAccess.parseKey("85"), -1, "other media key");
        eq(KeyHelperAccess.parseKey("4"), -1, "back key");
        eq(KeyHelperAccess.parseKey(""), -1, "empty");
        eq(KeyHelperAccess.parseKey(null), -1, "null");
        eq(KeyHelperAccess.uidFromLine("io.github.k8nowplaying 10123 0 /data/data/io.github.k8nowplaying default"), 10123, "uid");
        eq(KeyHelperAccess.uidFromLine("io.github.k8nowplaying.evil 10124 0 /data/data/x default"), -1, "prefix pkg");
        eq(KeyHelperAccess.uidFromLine("io.github.k8nowplaying 1000 0 /data/data/x default"), -1, "system uid refused");
        eq(KeyHelperAccess.uidFromLine("io.github.k8nowplaying abc"), -1, "bad uid");
        eq(KeyHelperAccess.uidFromLine("com.other 10050 0 /x default"), -1, "other pkg");
        if (failures > 0) {
            System.out.println(failures + " failed");
            System.exit(1);
        }
        System.out.println("13 passed");
    }

    static void eq(int got, int want, String name) {
        if (got != want) {
            failures++;
            System.out.println("FAIL " + name + ": " + got + " != " + want);
        }
    }
}
