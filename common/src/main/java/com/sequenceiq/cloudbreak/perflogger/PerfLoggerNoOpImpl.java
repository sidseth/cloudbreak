package com.sequenceiq.cloudbreak.perflogger;


import com.sequenceiq.cloudbreak.perflogger.PerfLogger.PerfLoggerInterface;

/**
 * No-op implementation of the perf tracker, for when it is disabled
 */
public class PerfLoggerNoOpImpl implements PerfLoggerInterface {

    private static final PerfLoggerNoOpImpl INSTANCE = new PerfLoggerNoOpImpl();

    private PerfLoggerNoOpImpl() {
    }

    public static PerfLoggerInterface get() {
        return INSTANCE;
    }

    @Override
    public void opBegin(String context, String opName) {

    }

    @Override
    public long opEnd__(String context, String opName) {
        return -1;
    }
}
