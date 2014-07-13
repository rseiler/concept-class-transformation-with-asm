package at.rseiler.concept;

import java.util.Arrays;

/**
 * Just has a static method which takes the method name and the arguments of the method and prints them to stout.
 *
 * @author reinhard.seiler@gmail.com
 */
public class MethodLogger {

    public static void log(String methodName, Object... args) {
        System.out.println(methodName + "(" + Arrays.deepToString(args) + ")");
    }

}
