import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.dynalang.dynalink.support.Lookup;

public class TestCatchException {
    public static void main(String[] args) throws Throwable {
        MethodHandle throwing = findStatic("throwing");
        MethodHandle catching = findStatic("catching");
        MethodHandles.catchException(throwing, MyException.class,
                MethodHandles.dropArguments(catching, 0, MyException.class));
    }

    private static class MyException extends RuntimeException {
    }

    private static MethodHandle findStatic(String name) {
        return Lookup.PUBLIC.findStatic(TestCatchException.class, name, MethodType.methodType(int.class, Object.class));
    }

    public static int throwing(Object o) {
        throw new IllegalArgumentException();
    }

    public static int catching(Object o) {
        return 0;
    }
}