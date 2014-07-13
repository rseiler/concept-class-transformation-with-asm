package ctransform;

import java.util.logging.Logger;

public class HelloWorld {

    private static final Logger logger1 = Logger.getLogger(HelloWorld.class.getName());

    public void hello() {
        logger1.info(foo("hello"));
        staticMethod("that's static");
    }

    public String foo(String arg) {
        return bar("foo", arg);
    }

    public String bar(String foo, String arg) {
        return foo + "bar " + arg;
    }

    private static void staticMethod(String arg) {
        logger1.info("staticMethod: " + arg);
    }

}
