package com.sequenceiq.cloudbreak.perflogger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class PerfLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(PerfLogger.class);

    private static PerfLoggerInterface INSTANCE;

    static {
        // Read enabled via the CB configuration system.
//        String enabled = System.getProperty(RCSSystemProperties.RCS_PERF_TRACKER_ENABLED);
//        if (enabled != null && enabled.equalsIgnoreCase("true")) {
//            LOG.info("PerfLogger: {}", PerfLoggerImpl.class.getName());
//            INSTANCE = PerfLoggerImpl.get();
//        } else {
//            LOG.info("PerfLogger: {}", PerfLoggerNoOpImpl.class.getName());
//            INSTANCE = PerfLoggerNoOpImpl.get();
//        }
        LOGGER.info("ZZZ: Created instance of PerfLoggerImpl");
        INSTANCE = PerfLoggerImpl.get();
    }

    // TODO: Add a generic invoker which takes care of setting whether an operation was successful,
    //  along with the generic try-catch-finally block

    public static PerfLoggerInterface get() {
        return INSTANCE;
    }


    public interface PerfLoggerInterface {

        void opBegin(String context, String opName);

        // TODO: Add a flag which indicates whether the operation was successful or not
        long opEnd__(String context, String opName);
    }
}
