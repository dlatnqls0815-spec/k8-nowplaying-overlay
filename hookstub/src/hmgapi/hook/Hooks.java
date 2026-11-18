package hmgapi.hook;

/** Compile-only stub of the HMG-Seogaemo hook host API (real class in the Seogaemo hook host). */
public final class Hooks {
    private Hooks() { }
    public static Class<?> findClass(String name, ClassLoader loader) { return null; }
    public static java.lang.reflect.Method findAndHookMethod(Class<?> c, String name, Object... typesAndHook) { return null; }
    public static Object getObjectField(Object o, String name) { return null; }
    public static Object callMethod(Object o, String name, Object... args) { return null; }
}
