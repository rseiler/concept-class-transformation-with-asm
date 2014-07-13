package at.rseiler.concept;

import java.util.logging.Logger;

/**
 * Just wraps a {@link Logger} and prints the info message to stout.
 * For a real world application this makes no sense. But this is just a demo.
 *
 * @author reinhard.seiler@gmail.com
 */
public class LoggerWrapper {

    public static Logger logger(Logger logger) {
        return new MyLogger(logger.getName(), logger.getResourceBundleName(), logger);
    }

    private static class MyLogger extends Logger {

        private final Logger logger;

        private MyLogger(String name, String resourceBundleName, Logger logger) {
            super(name, resourceBundleName);
            this.logger = logger;
        }

        @Override
        public void info(String msg) {
            System.out.println("LoggerWrapper: " + msg);
            logger.info(msg);
        }
    }

}
