package com.sequenceiq.cloudbreak.perflogger;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sequenceiq.cloudbreak.perflogger.PerfLogger.PerfLoggerInterface;


/**
 * Actual implementation of the perf tracker
 * NOTE: This should NEVER be enabled in prod, at least in the current state. Very easy to cause
 * an OOM if operataions are not cleared out - i.e. the END is not invoked.
 */
public class PerfLoggerImpl implements PerfLoggerInterface {

    private static final Logger LOG = LoggerFactory.getLogger(PerfLoggerImpl.class);

    private static final Logger PERFLOG = LoggerFactory.getLogger("CB.PERFLOG");

    private PerfLoggerImpl() {
        beginTimes = new ConcurrentHashMap<>();
    }

    private enum Instance {
        INSTANCE(new PerfLoggerImpl());

        private final PerfLoggerInterface perfTracker;

        Instance(PerfLoggerInterface perfTracker) {
            this.perfTracker = perfTracker;
        }
    }

    public static PerfLoggerInterface get() {
        return Instance.INSTANCE.perfTracker;
    }

    private final ConcurrentHashMap<Key, Long> beginTimes;

    @Override
    public void opBegin(String context, String opName) {
        Key key = new Key(context, opName);
        long beginTime = System.nanoTime();
        Long old = beginTimes.put(key, beginTime);
        if (old != null) {
            LOG.debug("Duplicate beginOp for key={}", key);
        }
        PERFLOG.info("PERFLOG BEGIN: context=[{}], opName=[{}], beginTime=[{}]", context, opName,
                beginTime);
    }

    // TODO Add support to indicate whether the operation completely successfully or not.
    //  Also add an Invoker which can replace the generic try { BEGIN. OPERATION} finally {END}
    //  block
    @Override
    public long opEnd__(String context, String opName) {
        Key key = new Key(context, opName);
        Long beginTime = beginTimes.remove(key);
        long endTime = System.nanoTime();
        if (beginTime == null) {
            LOG.debug("No begin information found for key: {}. Duration will be recorded as 0.", key);
            beginTime = endTime;
        }

        long duration = (endTime - beginTime) / 1_000_000L;
        PERFLOG
                .info("PERFLOG _END_: context=[{}], opName=[{}], duration=[{}], beginTime=[{}], endTime=[{}]",
                        context, opName, duration, beginTime, endTime);

        logActiveKeys();

        return duration;
    }

    private void logActiveKeys() {
        for (Key key : beginTimes.keySet()) {
            LOG.debug("ActiveKey: {}", key);
        }
    }


    private static class Key {

        private String context;
        private String opName;

        public Key(String context, String opName) {
            this.context = context;
            this.opName = opName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Key key = (Key) o;
            return Objects.equals(context, key.context) &&
                    Objects.equals(opName, key.opName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(context, opName);
        }

        @Override
        public String toString() {
            return "Key{" +
                    "context='" + context + '\'' +
                    ", opName='" + opName + '\'' +
                    '}';
        }
    }
}
