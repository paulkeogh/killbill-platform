/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.slf4j.impl;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MarkerIgnoringBase;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.helpers.Util;
import org.slf4j.spi.LocationAwareLogger;

/**
 * <p>Simple implementation of {@link Logger} that sends all enabled log messages,
 * for all defined loggers, to the console ({@code System.err}).
 * The following system properties are supported to configure the behavior of this logger:</p>
 * <p/>
 * <ul>
 * <li><code>org.slf4j.simpleLogger.logFile</code> - The output target which can be the <em>path</em> to a file, or
 * the special values "System.out" and "System.err". Default is "System.err".
 * <p/>
 * <li><code>org.slf4j.simpleLogger.defaultLogLevel</code> - Default log level for all instances of SimpleLogger.
 * Must be one of ("trace", "debug", "info", "warn", or "error"). If not specified, defaults to "info". </li>
 * <p/>
 * <li><code>org.slf4j.simpleLogger.log.<em>a.b.c</em></code> - Logging detail level for a SimpleLogger instance
 * named "a.b.c". Right-side value must be one of "trace", "debug", "info", "warn", or "error". When a SimpleLogger
 * named "a.b.c" is initialized, its level is assigned from this property. If unspecified, the level of nearest parent
 * logger will be used, and if none is set, then the value specified by
 * <code>org.slf4j.simpleLogger.defaultLogLevel</code> will be used.</li>
 * <p/>
 * <li><code>org.slf4j.simpleLogger.showDateTime</code> - Set to <code>true</code> if you want the current date and
 * time to be included in output messages. Default is <code>true</code></li>
 * <p/>
 * <li><code>org.slf4j.simpleLogger.dateTimeFormat</code> - The date and time format to be used in the output messages.
 * The pattern describing the date and time format is defined by
 * <a href="http://docs.oracle.com/javase/1.5.0/docs/api/java/text/SimpleDateFormat.html"><code>SimpleDateFormat</code></a>.
 * If the format is not specified or is invalid, the number of milliseconds since start up will be output. </li>
 * <p/>
 * <li><code>org.slf4j.simpleLogger.showThreadName</code> -Set to <code>true</code> if you want to output the current
 * thread name. Defaults to <code>true</code>.</li>
 * <p/>
 * <li><code>org.slf4j.simpleLogger.showLogName</code> - Set to <code>true</code> if you want the Logger instance name
 * to be included in output messages. Defaults to <code>true</code>.</li>
 * <p/>
 * <li><code>org.slf4j.simpleLogger.showShortLogName</code> - Set to <code>true</code> if you want the last component
 * of the name to be included in output messages. Defaults to <code>false</code>.</li>
 * <p/>
 * <li><code>org.slf4j.simpleLogger.levelInBrackets</code> - Should the level string be output in brackets? Defaults
 * to <code>false</code>.</li>
 * <p/>
 * <li><code>org.slf4j.simpleLogger.warnLevelString</code> - The string value output for the warn level. Defaults
 * to <code>WARN</code>.</li>
 * <p/>
 * </ul>
 * <p/>
 * <p>In addition to looking for system properties with the names specified above, this implementation also checks for
 * a class loader resource named <code>"simplelogger.properties"</code>, and includes any matching definitions
 * from this resource (if it exists).</p>
 * <p/>
 * <p>With no configuration, the default output includes the relative time in milliseconds, thread name, the level,
 * logger name, and the message followed by the line separator for the host.  In log4j terms it amounts to the "%r [%t]
 * %level %logger - %m%n" pattern. </p>
 * <p>Sample output follows.</p>
 * <pre>
 * 176 [main] INFO examples.Sort - Populating an array of 2 elements in reverse order.
 * 225 [main] INFO examples.SortAlgo - Entered the sort method.
 * 304 [main] INFO examples.SortAlgo - Dump of integer array:
 * 317 [main] INFO examples.SortAlgo - Element [0] = 0
 * 331 [main] INFO examples.SortAlgo - Element [1] = 1
 * 343 [main] INFO examples.Sort - The next log statement should be an error message.
 * 346 [main] ERROR examples.SortAlgo - Tried to dump an uninitialized array.
 *   at org.log4j.examples.SortAlgo.dump(SortAlgo.java:58)
 *   at org.log4j.examples.Sort.main(Sort.java:64)
 * 467 [main] INFO  examples.Sort - Exiting main method.
 * </pre>
 * <p/>
 * <p>This implementation is heavily inspired by
 * <a href="http://commons.apache.org/logging/">Apache Commons Logging</a>'s SimpleLog.</p>
 *
 * @author Ceki G&uuml;lc&uuml;
 * @author <a href="mailto:sanders@apache.org">Scott Sanders</a>
 * @author Rod Waldhoff
 * @author Robert Burrell Donkin
 * @author C&eacute;drik LIME
 */
public class SimpleLogger extends MarkerIgnoringBase {

    private static final long serialVersionUID = -632788891211436180L;
    private static final String CONFIGURATION_FILE = "simplelogger.properties";

    private static final long START_TIME = System.currentTimeMillis();
    private static final Properties SIMPLE_LOGGER_PROPS = new Properties();

    private static final int LOG_LEVEL_TRACE = LocationAwareLogger.TRACE_INT;
    private static final int LOG_LEVEL_DEBUG = LocationAwareLogger.DEBUG_INT;
    private static final int LOG_LEVEL_INFO = LocationAwareLogger.INFO_INT;
    private static final int LOG_LEVEL_WARN = LocationAwareLogger.WARN_INT;
    private static final int LOG_LEVEL_ERROR = LocationAwareLogger.ERROR_INT;

    private static boolean INITIALIZED = false;

    private static int DEFAULT_LOG_LEVEL = LOG_LEVEL_INFO;
    private static boolean SHOW_DATE_TIME = false;
    private static String DATE_TIME_FORMAT_STR = null;
    private static DateFormat DATE_FORMATTER = null;
    private static boolean SHOW_THREAD_NAME = true;
    private static boolean SHOW_LOG_NAME = true;
    private static boolean SHOW_SHORT_LOG_NAME = false;
    private static String LOG_FILE = "System.err";
    private static PrintStream TARGET_STREAM = null;
    private static boolean LEVEL_IN_BRACKETS = false;
    private static String WARN_LEVEL_STRING = "WARN";

    /**
     * All system properties used by <code>SimpleLogger</code> start with this prefix
     */
    public static final String SYSTEM_PREFIX = "org.slf4j.simpleLogger.";

    public static final String DEFAULT_LOG_LEVEL_KEY = SYSTEM_PREFIX + "defaultLogLevel";
    public static final String SHOW_DATE_TIME_KEY = SYSTEM_PREFIX + "showDateTime";
    public static final String DATE_TIME_FORMAT_KEY = SYSTEM_PREFIX + "dateTimeFormat";
    public static final String SHOW_THREAD_NAME_KEY = SYSTEM_PREFIX + "showThreadName";
    public static final String SHOW_LOG_NAME_KEY = SYSTEM_PREFIX + "showLogName";
    public static final String SHOW_SHORT_LOG_NAME_KEY = SYSTEM_PREFIX + "showShortLogName";
    public static final String LOG_FILE_KEY = SYSTEM_PREFIX + "logFile";
    public static final String LEVEL_IN_BRACKETS_KEY = SYSTEM_PREFIX + "levelInBrackets";
    public static final String WARN_LEVEL_STRING_KEY = SYSTEM_PREFIX + "warnLevelString";

    public static final String LOG_KEY_PREFIX = SYSTEM_PREFIX + "log.";

    private static String getStringProperty(final String name) {
        String prop = null;
        try {
            prop = System.getProperty(name);
        } catch (final SecurityException e) {
            // Ignore
        }
        return (prop == null) ? SIMPLE_LOGGER_PROPS.getProperty(name) : prop;
    }

    private static String getStringProperty(final String name, final String defaultValue) {
        final String prop = getStringProperty(name);
        return (prop == null) ? defaultValue : prop;
    }

    private static boolean getBooleanProperty(final String name, final boolean defaultValue) {
        final String prop = getStringProperty(name);
        return (prop == null) ? defaultValue : "true".equalsIgnoreCase(prop);
    }

    // Initialize class attributes.
    // Load properties file, if found.
    // Override with system properties.
    static void init() {
        INITIALIZED = true;
        loadProperties();

        final String defaultLogLevelString = getStringProperty(DEFAULT_LOG_LEVEL_KEY, null);
        if (defaultLogLevelString != null) {
            DEFAULT_LOG_LEVEL = stringToLevel(defaultLogLevelString);
        }

        SHOW_LOG_NAME = getBooleanProperty(SHOW_LOG_NAME_KEY, SHOW_LOG_NAME);
        SHOW_SHORT_LOG_NAME = getBooleanProperty(SHOW_SHORT_LOG_NAME_KEY, SHOW_SHORT_LOG_NAME);
        SHOW_DATE_TIME = getBooleanProperty(SHOW_DATE_TIME_KEY, SHOW_DATE_TIME);
        SHOW_THREAD_NAME = getBooleanProperty(SHOW_THREAD_NAME_KEY, SHOW_THREAD_NAME);
        DATE_TIME_FORMAT_STR = getStringProperty(DATE_TIME_FORMAT_KEY, DATE_TIME_FORMAT_STR);
        LEVEL_IN_BRACKETS = getBooleanProperty(LEVEL_IN_BRACKETS_KEY, LEVEL_IN_BRACKETS);
        WARN_LEVEL_STRING = getStringProperty(WARN_LEVEL_STRING_KEY, WARN_LEVEL_STRING);

        LOG_FILE = getStringProperty(LOG_FILE_KEY, LOG_FILE);
        TARGET_STREAM = computeTargetStream(LOG_FILE);

        if (DATE_TIME_FORMAT_STR != null) {
            try {
                DATE_FORMATTER = new SimpleDateFormat(DATE_TIME_FORMAT_STR);
            } catch (final IllegalArgumentException e) {
                Util.report("Bad date format in " + CONFIGURATION_FILE + "; will output relative time", e);
            }
        }
    }

    private static PrintStream computeTargetStream(final String logFile) {
        if ("System.err".equalsIgnoreCase(logFile)) {
            return System.err;
        } else if ("System.out".equalsIgnoreCase(logFile)) {
            return System.out;
        } else {
            try {
                final FileOutputStream fos = new FileOutputStream(logFile);
                final PrintStream printStream = new PrintStream(fos);
                return printStream;
            } catch (final FileNotFoundException e) {
                Util.report("Could not open [" + logFile + "]. Defaulting to System.err", e);
                return System.err;
            }
        }
    }

    private static void loadProperties() {
        // Add props from the resource simplelogger.properties
        final InputStream in = (InputStream) AccessController.doPrivileged(
                new PrivilegedAction<Object>() {
                    public Object run() {
                        final ClassLoader threadCL = Thread.currentThread().getContextClassLoader();
                        if (threadCL != null) {
                            return threadCL.getResourceAsStream(CONFIGURATION_FILE);
                        } else {
                            return ClassLoader.getSystemResourceAsStream(CONFIGURATION_FILE);
                        }
                    }
                });
        if (null != in) {
            try {
                SIMPLE_LOGGER_PROPS.load(in);
                in.close();
            } catch (final java.io.IOException e) {
                // ignored
            }
        }
    }

    /**
     * The current log level
     */
    protected int currentLogLevel = LOG_LEVEL_INFO;
    /**
     * The short name of this simple log instance
     */
    private transient String shortLogName = null;

    /**
     * Package access allows only {@link SimpleLoggerFactory} to instantiate
     * SimpleLogger instances.
     */
    SimpleLogger(final String name) {
        if (!INITIALIZED) {
            init();
        }
        this.name = name;

        final String levelString = recursivelyComputeLevelString();
        if (levelString != null) {
            this.currentLogLevel = stringToLevel(levelString);
        } else {
            this.currentLogLevel = DEFAULT_LOG_LEVEL;
        }
    }

    String recursivelyComputeLevelString() {
        String tempName = name;
        String levelString = null;
        int indexOfLastDot = tempName.length();
        while ((levelString == null) && (indexOfLastDot > -1)) {
            tempName = tempName.substring(0, indexOfLastDot);
            levelString = getStringProperty(LOG_KEY_PREFIX + tempName, null);
            indexOfLastDot = String.valueOf(tempName).lastIndexOf(".");
        }
        return levelString;
    }

    private static int stringToLevel(final String levelStr) {
        if ("trace".equalsIgnoreCase(levelStr)) {
            return LOG_LEVEL_TRACE;
        } else if ("debug".equalsIgnoreCase(levelStr)) {
            return LOG_LEVEL_DEBUG;
        } else if ("info".equalsIgnoreCase(levelStr)) {
            return LOG_LEVEL_INFO;
        } else if ("warn".equalsIgnoreCase(levelStr)) {
            return LOG_LEVEL_WARN;
        } else if ("error".equalsIgnoreCase(levelStr)) {
            return LOG_LEVEL_ERROR;
        }
        // assume INFO by default
        return LOG_LEVEL_INFO;
    }

    /**
     * This is our internal implementation for logging regular (non-parameterized)
     * log messages.
     *
     * @param level   One of the LOG_LEVEL_XXX constants defining the log level
     * @param message The message itself
     * @param t       The exception whose stack trace should be logged
     */
    protected void log(final int level, final String message, final Throwable t) {
        if (!isLevelEnabled(level)) {
            return;
        }

        final StringBuffer buf = new StringBuffer(32);

        // Append date-time if so configured
        if (SHOW_DATE_TIME) {
            if (DATE_FORMATTER != null) {
                buf.append(getFormattedDate());
                buf.append(' ');
            } else {
                buf.append(System.currentTimeMillis() - START_TIME);
                buf.append(' ');
            }
        }

        // Append current thread name if so configured
        if (SHOW_THREAD_NAME) {
            buf.append('[');
            buf.append(Thread.currentThread().getName());
            buf.append("] ");
        }

        if (LEVEL_IN_BRACKETS) {
            buf.append('[');
        }

        // Append a readable representation of the log level
        switch (level) {
            case LOG_LEVEL_TRACE:
                buf.append("TRACE");
                break;
            case LOG_LEVEL_DEBUG:
                buf.append("DEBUG");
                break;
            case LOG_LEVEL_INFO:
                buf.append("INFO");
                break;
            case LOG_LEVEL_WARN:
                buf.append(WARN_LEVEL_STRING);
                break;
            case LOG_LEVEL_ERROR:
                buf.append("ERROR");
                break;
        }
        if (LEVEL_IN_BRACKETS) {
            buf.append(']');
        }
        buf.append(' ');

        // Append the name of the log instance if so configured
        if (SHOW_SHORT_LOG_NAME) {
            if (shortLogName == null) {
                shortLogName = computeShortName();
            }
            buf.append(String.valueOf(shortLogName)).append(" - ");
        } else if (SHOW_LOG_NAME) {
            buf.append(String.valueOf(name)).append(" - ");
        }

        // Append the message
        buf.append(message);

        write(buf, t);

    }

    void write(final StringBuffer buf, final Throwable t) {
        TARGET_STREAM.println(buf.toString());
        if (t != null) {
            t.printStackTrace(TARGET_STREAM);
        }
        TARGET_STREAM.flush();
    }

    private String getFormattedDate() {
        final Date now = new Date();
        final String dateText;
        synchronized (DATE_FORMATTER) {
            dateText = DATE_FORMATTER.format(now);
        }
        return dateText;
    }

    private String computeShortName() {
        return name.substring(name.lastIndexOf(".") + 1);
    }

    /**
     * For formatted messages, first substitute arguments and then log.
     *
     * @param level
     * @param format
     * @param arg1
     * @param arg2
     */
    private void formatAndLog(final int level, final String format, final Object arg1,
                              final Object arg2) {
        if (!isLevelEnabled(level)) {
            return;
        }
        final FormattingTuple tp = MessageFormatter.format(format, arg1, arg2);
        log(level, tp.getMessage(), tp.getThrowable());
    }

    /**
     * For formatted messages, first substitute arguments and then log.
     *
     * @param level
     * @param format
     * @param arguments a list of 3 ore more arguments
     */
    private void formatAndLog(final int level, final String format, final Object... arguments) {
        if (!isLevelEnabled(level)) {
            return;
        }
        final FormattingTuple tp = MessageFormatter.arrayFormat(format, arguments);
        log(level, tp.getMessage(), tp.getThrowable());
    }

    /**
     * Is the given log level currently enabled?
     *
     * @param logLevel is this level enabled?
     */
    protected boolean isLevelEnabled(final int logLevel) {
        // log level are numerically ordered so can use simple numeric
        // comparison
        return (logLevel >= currentLogLevel);
    }

    /**
     * Are {@code trace} messages currently enabled?
     */
    public boolean isTraceEnabled() {
        return isLevelEnabled(LOG_LEVEL_TRACE);
    }

    /**
     * A simple implementation which logs messages of level TRACE according
     * to the format outlined above.
     */
    public void trace(final String msg) {
        log(LOG_LEVEL_TRACE, msg, null);
    }

    /**
     * Perform single parameter substitution before logging the message of level
     * TRACE according to the format outlined above.
     */
    public void trace(final String format, final Object param1) {
        formatAndLog(LOG_LEVEL_TRACE, format, param1, null);
    }

    /**
     * Perform double parameter substitution before logging the message of level
     * TRACE according to the format outlined above.
     */
    public void trace(final String format, final Object param1, final Object param2) {
        formatAndLog(LOG_LEVEL_TRACE, format, param1, param2);
    }

    /**
     * Perform double parameter substitution before logging the message of level
     * TRACE according to the format outlined above.
     */
    public void trace(final String format, final Object... argArray) {
        formatAndLog(LOG_LEVEL_TRACE, format, argArray);
    }

    /**
     * Log a message of level TRACE, including an exception.
     */
    public void trace(final String msg, final Throwable t) {
        log(LOG_LEVEL_TRACE, msg, t);
    }

    /**
     * Are {@code debug} messages currently enabled?
     */
    public boolean isDebugEnabled() {
        return isLevelEnabled(LOG_LEVEL_DEBUG);
    }

    /**
     * A simple implementation which logs messages of level DEBUG according
     * to the format outlined above.
     */
    public void debug(final String msg) {
        log(LOG_LEVEL_DEBUG, msg, null);
    }

    /**
     * Perform single parameter substitution before logging the message of level
     * DEBUG according to the format outlined above.
     */
    public void debug(final String format, final Object param1) {
        formatAndLog(LOG_LEVEL_DEBUG, format, param1, null);
    }

    /**
     * Perform double parameter substitution before logging the message of level
     * DEBUG according to the format outlined above.
     */
    public void debug(final String format, final Object param1, final Object param2) {
        formatAndLog(LOG_LEVEL_DEBUG, format, param1, param2);
    }

    /**
     * Perform double parameter substitution before logging the message of level
     * DEBUG according to the format outlined above.
     */
    public void debug(final String format, final Object... argArray) {
        formatAndLog(LOG_LEVEL_DEBUG, format, argArray);
    }

    /**
     * Log a message of level DEBUG, including an exception.
     */
    public void debug(final String msg, final Throwable t) {
        log(LOG_LEVEL_DEBUG, msg, t);
    }

    /**
     * Are {@code info} messages currently enabled?
     */
    public boolean isInfoEnabled() {
        return isLevelEnabled(LOG_LEVEL_INFO);
    }

    /**
     * A simple implementation which logs messages of level INFO according
     * to the format outlined above.
     */
    public void info(final String msg) {
        log(LOG_LEVEL_INFO, msg, null);
    }

    /**
     * Perform single parameter substitution before logging the message of level
     * INFO according to the format outlined above.
     */
    public void info(final String format, final Object arg) {
        formatAndLog(LOG_LEVEL_INFO, format, arg, null);
    }

    /**
     * Perform double parameter substitution before logging the message of level
     * INFO according to the format outlined above.
     */
    public void info(final String format, final Object arg1, final Object arg2) {
        formatAndLog(LOG_LEVEL_INFO, format, arg1, arg2);
    }

    /**
     * Perform double parameter substitution before logging the message of level
     * INFO according to the format outlined above.
     */
    public void info(final String format, final Object... argArray) {
        formatAndLog(LOG_LEVEL_INFO, format, argArray);
    }

    /**
     * Log a message of level INFO, including an exception.
     */
    public void info(final String msg, final Throwable t) {
        log(LOG_LEVEL_INFO, msg, t);
    }

    /**
     * Are {@code warn} messages currently enabled?
     */
    public boolean isWarnEnabled() {
        return isLevelEnabled(LOG_LEVEL_WARN);
    }

    /**
     * A simple implementation which always logs messages of level WARN according
     * to the format outlined above.
     */
    public void warn(final String msg) {
        log(LOG_LEVEL_WARN, msg, null);
    }

    /**
     * Perform single parameter substitution before logging the message of level
     * WARN according to the format outlined above.
     */
    public void warn(final String format, final Object arg) {
        formatAndLog(LOG_LEVEL_WARN, format, arg, null);
    }

    /**
     * Perform double parameter substitution before logging the message of level
     * WARN according to the format outlined above.
     */
    public void warn(final String format, final Object arg1, final Object arg2) {
        formatAndLog(LOG_LEVEL_WARN, format, arg1, arg2);
    }

    /**
     * Perform double parameter substitution before logging the message of level
     * WARN according to the format outlined above.
     */
    public void warn(final String format, final Object... argArray) {
        formatAndLog(LOG_LEVEL_WARN, format, argArray);
    }

    /**
     * Log a message of level WARN, including an exception.
     */
    public void warn(final String msg, final Throwable t) {
        log(LOG_LEVEL_WARN, msg, t);
    }

    /**
     * Are {@code error} messages currently enabled?
     */
    public boolean isErrorEnabled() {
        return isLevelEnabled(LOG_LEVEL_ERROR);
    }

    /**
     * A simple implementation which always logs messages of level ERROR according
     * to the format outlined above.
     */
    public void error(final String msg) {
        log(LOG_LEVEL_ERROR, msg, null);
    }

    /**
     * Perform single parameter substitution before logging the message of level
     * ERROR according to the format outlined above.
     */
    public void error(final String format, final Object arg) {
        formatAndLog(LOG_LEVEL_ERROR, format, arg, null);
    }

    /**
     * Perform double parameter substitution before logging the message of level
     * ERROR according to the format outlined above.
     */
    public void error(final String format, final Object arg1, final Object arg2) {
        formatAndLog(LOG_LEVEL_ERROR, format, arg1, arg2);
    }

    /**
     * Perform double parameter substitution before logging the message of level
     * ERROR according to the format outlined above.
     */
    public void error(final String format, final Object... argArray) {
        formatAndLog(LOG_LEVEL_ERROR, format, argArray);
    }

    /**
     * Log a message of level ERROR, including an exception.
     */
    public void error(final String msg, final Throwable t) {
        log(LOG_LEVEL_ERROR, msg, t);
    }
}
