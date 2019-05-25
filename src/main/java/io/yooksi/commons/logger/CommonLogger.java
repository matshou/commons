package io.yooksi.commons.logger;

import io.yooksi.commons.define.MethodsNotNull;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender;
import org.apache.logging.log4j.core.config.*;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.core.filter.LevelRangeFilter;

@MethodsNotNull
@SuppressWarnings({"unused", "WeakerAccess"})
public class CommonLogger extends AbsCommonLogger {

    private static final LoggerContext CONTEXT = getInternalContext();

    static final String[] CONSOLE_APPENDERS = new String[]{ "CLConsole", "Console" };
    static final String[] FILE_APPENDERS = new String[]{ "CLFile", "File" };

    /** Used for internal class logging, particularly by the class constructor */
    static final CommonLogger LOGGER = new CommonLogger("CommonLogger", true);

    final LoggerContext context;
    final Configuration config;
    final LoggerConfig loggerConfig;

    final AppenderData<AbstractOutputStreamAppender> logFileAppender;

    final String name; final Logger logger;
    final Level logLevel; Level logFileLevel;

    /**
     * <p>Construct a new instance of this custom Log4j wrapper.</p>
     * <ul>
     *     <li>The construction process will be logged by an internal logger.</li>
     *     at a debug level to console exclusively.
     *     <li>If either the console or file appender are missing from the
     *     configuration file in the current context it will be created
     *     with default configurations.</li>
     *     <li>When creating a logger with the same name as an existing one
     *     but different console or file log levels the existing logger will
     *     be used and the appropriate appenders will be updated.</li>
     * </ul>
     *
     * @param logger name of the {@code Log4j} logger to create or use
     * @param logLevel console logging level
     * @param logFileLevel logfile logging level
     */
    public CommonLogger(String logger, Level logLevel, String logFile, Level logFileLevel, boolean currentContext, boolean additive) {

        LOGGER.printf(Level.DEBUG, "Initializing new CommonLogger %s " +
                "with level " + "%s(log), %s(file)", logger, logLevel, logFileLevel);

        this.name = logger;
        this.logLevel = logLevel;
        this.logFileLevel = logFileLevel;
        /*
         * Try to find a wider context here with currentContext parameter passed as false.
         * If there already is larger LoggerContext it will be used instead of our local one.
         */
        context = LoggerContext.getContext(currentContext);
        config = context.getConfiguration();

        LOGGER.printf(Level.DEBUG, "Using %s Configuration found in context %s", config.getName(), context.getName());

        final String logFilePath = Log4jUtils.getStandardLogFilePath(!logFile.isEmpty() ? logFile : logger);
        loggerConfig = Log4jUtils.getOrCreateLoggerConfig(this, additive);

        AppenderData consoleAppender = Log4jUtils.getOrInitConsoleAppender(this);
        AppenderData<AbstractOutputStreamAppender> fileAppender = Log4jUtils.getOrInitFileAppender(
                this, consoleAppender.getAppender().getLayout(), logFilePath);

        if (!logFile.isEmpty())
        {
            String filePath = Log4jUtils.getLogFileName(fileAppender.getAppender());
            if (!filePath.equals(logFilePath))
            {
                LOGGER.debug("Creating dedicated FileAppender for logger " + name);
                loggerConfig.removeAppender(fileAppender.getAppender().getName());
                fileAppender = new AppenderData<>(loggerConfig, Log4jUtils.createNewFileAppender(
                        this, fileAppender.getAppender().getLayout(), logFilePath, true), logFileLevel);
            }
        }

        if (!consoleAppender.isLevel(logLevel)) {
            /*
             * If the appender belongs to this LoggerConfig it means that no reachable
             * appender of that type was found so we don't have to worry about additivity
             */
            if (consoleAppender.isLoggerConfig(loggerConfig)) {
                Log4jUtils.updateAppender(loggerConfig, context, consoleAppender.getAppender(), logLevel);
            }
            else {
                if (consoleAppender.isFiltering(logLevel)) {
                    Log4jUtils.createNewConsoleAppender(this, consoleAppender, config, true, LevelRangeFilter
                            .createFilter(Level.OFF, consoleAppender.getLevel(), Filter.Result.DENY, Filter.Result.NEUTRAL));
                }
                else {/* Logger is requesting a lower logging threshold so we have to disable
                       * the additivity effect from this logger affecting the target logger

                    consoleAppender.setFilter(context, AdditivityFilter.createFilter(this.name));

                    TODO: Find away around this problem either by using filters or placing
                        a dedicated local appender with the desired level in root LoggerConfig
                        that will only accept events from this logger and filtering standard
                        console logs with an additivity filter
                    */
                    LOGGER.wrap( Level.WARN,"Filtering logs with levels lower then root is currently not supported " +
                            "due to how Log4j additivity works.", "Setting logger level to default level INFO.");
                }
            }
        }

        this.logFileAppender = fileAppender;
        this.logger = context.getLogger(logger);

        loggerConfig.setAdditive(additive);
        loggerConfig.setLevel(logLevel);
        /*
         * This causes all Loggers to re-fetch information from their LoggerConfig.
         * We have to call this if we want to see our changes take place
         */
        context.updateLoggers();
        LOGGER.debug("Finished constructing logger");
    }

    /**
     * Overload constructor for when we don't want to log to file,
     * or when we want the file and console logging levels to be the same.
     *
     * @see #CommonLogger(String, Level, String, Level, boolean, boolean)
     */
    public CommonLogger(String logger, Level logLevel, Level logFileLevel, boolean dedicatedFile, boolean currentContext, boolean additive) {
        this(logger, logLevel, dedicatedFile ? logger : "", logFileLevel, currentContext, additive);
    }

    public CommonLogger(String logger, Level logLevel, boolean dedicatedFile, boolean currentContext, boolean additive) {
        this(logger, logLevel, dedicatedFile ? logger : "", logLevel, currentContext, additive);
    }

    public CommonLogger(String logger, Level logLevel, String logFile, boolean currentContext, boolean additive) {
        this(logger, logLevel, logFile, logLevel, currentContext, additive);
    }

    CommonLogger(String logger, boolean clearLogFile) {

        this.name = logger;
        this.logLevel = Level.ALL;
        this.logFileLevel = Level.ALL;

        context = CONTEXT;
        config = context.getConfiguration();
        loggerConfig = config.getLoggerConfig(logger);

        Class<AbstractOutputStreamAppender> clazz = AbstractOutputStreamAppender.class;
        this.logFileAppender = Log4jUtils.findFileAppender(FILE_APPENDERS, loggerConfig);
        this.logger = context.getLogger(logger);

        if (clearLogFile)
            clearLogFile();
    }

    private static LoggerContext getInternalContext() {

        ClassLoader cld = CommonLogger.class.getClassLoader();
        java.net.URL configPath = CommonLogger.class.getResource("/log4j2-cl.xml");
        try {
            /* It's imperative to get context with the currentContext parameter set to true
             * otherwise we risk the context becoming global and applying to other non-related loggers
             *
             * I don't understand enough about Log4J to understand why this happens but setting this
             * parameter to true seems to prevent this unwanted behavior.
             */
            return LoggerContext.getContext(cld, true, configPath.toURI());
        }
        catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    public org.apache.logging.log4j.core.Logger getCoreLogger() {
        return ((org.apache.logging.log4j.core.Logger)logger);
    }

    /**
     * @return Logger level assigned to this logger's ConsoleAppender.
     */
    public Level getLogLevel() {
        return logLevel;
    }
    /**
     * @return Logger level assigned to this logger's FileAppender.
     */
    public Level getLogFileLevel() {
        return logFileLevel;
    }
    public String getName() {
        return name;
    }

    /**
     * <p>{@code LoggerConfigs} are defined as {@code <logger>} blocks in the {@code log4j2.xml}
     * {@code Configuration} file and are defined for each dedicated logger name.</p>
     *
     * If no {@code LoggerConfig} dedicated configuration definition was found
     * in the XML file for our logger the root logger config will be used instead.
     *
     * @return dedicated logger configuration entry from {@code log4j2.xml} or root
     * configuration entry if no dedicated config for our logger was found.
     */
    public LoggerConfig getLoggerConfig() {
        return loggerConfig;
    }

    /**
     * Call this from an {@code AbsCommonLogger} implementation
     * when you need access to more logging methods.
     *
     * @return an instance of {@code Log4j} logger created for this implementation
     * or the internal logger used as fallback when the logger is still initializing
     */
    public final Logger getLogger() {
        return logger != null ? logger : LOGGER.logger;
    }

    public java.io.File getLogFile() {
        return new java.io.File(Log4jUtils.getLogFileName(logFileAppender.getAppender()));
    }

    public void clearLogFile() {
        /*
         * Currently used only by tests but is useful to all users
         * TODO: implement a system that creates new log files on each
         *  run and packs the ones from day before into a zipped archive
         */
        try {
            java.io.PrintWriter writer = new java.io.PrintWriter(getLogFile());
            writer.print(""); writer.close();
        }
        catch (java.io.FileNotFoundException e) {
            error("Unable to clear logfile", e);
        }
    }

    /**
     * Update the logger {@code FileAppender} level to match
     * the method parameter level. If all you want is to start or stop
     * logging to file you should use of these respective methods:
     * <ul style="list-style-type:none">
     *     <li>{@link #startLoggingToFile()}</li>
     *     <li>{@link #stopLoggingToFile()}</li>
     * </ul>
     * @see Log4jUtils#updateAppender(LoggerConfig, LoggerContext, Appender, Level)
     */
    public void setLogFileLevel(Level level) {

        logFileLevel = level;
        Log4jUtils.updateAppender(loggerConfig, context, logFileAppender.getAppender(), level);
        //debug("%s started logging to file with level %s", logger.getName(), level);
    }
    /**
     * Update the logger {@code FileAppender} level to match the
     * default logfile level for this wrapper. This method is intended
     * to be used after the logging to file has been programmatically
     * stopped or the wrapper was constructed with no file logging in mind.
     *
     * <p><i>Note that this will obviously have no effect if the {@code FileAppender}
     * is already set to operate at the wrapped logfile level.</i></p>
     *
     * @see Log4jUtils#updateAppender(LoggerConfig, LoggerContext, Appender, Level)
     * @see #stopLoggingToFile()
     */
    public void startLoggingToFile() {

        Log4jUtils.updateAppender(loggerConfig, context, logFileAppender.getAppender(), logFileLevel);
        //debug("%s started logging to file with level %s", logger.getName(), logFileLevel);
    }

    public void stopLoggingToFile() {

        Log4jUtils.updateAppender(loggerConfig, context, logFileAppender.getAppender(), Level.OFF);
        //debug("%s stopped logging to file with level %s", logger.getName(), logFileLevel);
    }

    public void wrap(Level level, String...logs) {

        for (String log : logs) {
            printf(level, log);
        }
    }

    /*
     * Short-hand methods to print longs to console.
     */
    public void info(String log) {
        logger.info(log);
    }
    public void info(String format, Object...params) {
        logger.printf(Level.INFO, format, params);
    }
    public void info(String log, Throwable t) {
        logger.info(log, t);
    }
    public void error(String log) {
        logger.error(log);
    }
    public void error(String format, Object...params) {
        logger.printf(Level.ERROR, format, params);
    }
    public void error(String log, Throwable t) {
        logger.error(log, t);
    }
    public void warn(String log) {
        logger.warn(log);
    }
    public void debug(String log) {
        logger.debug(log);
    }
    public void debug(String format, Object...params) {
        logger.printf(Level.DEBUG, format, params);
    }
    public void debug(String log, Throwable t) {
        logger.debug(log, t);
    }
    final public void printf(Level level, String format, Object... params) {
        logger.printf(level, format, params);
    }
}
