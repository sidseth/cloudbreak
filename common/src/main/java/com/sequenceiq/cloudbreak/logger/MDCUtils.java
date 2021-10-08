package com.sequenceiq.cloudbreak.logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;

public class MDCUtils {

    private MDCUtils() {

    }

    public static Optional<String> getRequestId() {
        return Optional.of(MDCBuilder.getOrGenerateRequestId());
    }

    private static final String PERF_CONTEXT = "PerfContext";

    public static void setPerfContextIfAbsent(String perfContext) {
        // Can this somehow be a nested perfContext, which is automatically derived.
        // Something along the lines of ...
        //  a) A user settable context, which will almost always be set.
        //  b) Additionally, the current method name (automatically inferred), which in turn
        //    is appended to the current perf context via a separator, and removed when the op ends.
        MDCBuilder.addMdcField(PERF_CONTEXT, perfContext);
    }

    public static void unsetPerfContextIfMatches(String perfContext) {
        String currentPerfContext = MDCBuilder.getMdcContextMap().get(PERF_CONTEXT);
        if (!Strings.isNullOrEmpty(currentPerfContext) && currentPerfContext.equals(perfContext)) {
            MDCBuilder.removeMdcField(PERF_CONTEXT);
        }
    }

    // spanId seems to change for several operations.
    private static final Set<String> KEYS_TO_SKIP = Sets.newHashSet(LoggerContextKey.SPAN_ID.toString());


    public static String getPerfContextString() {
        Map<String, String> mdcContextMap = MDCBuilder.getMdcContextMap();
        mdcContextMap = mdcContextMap.entrySet().stream().filter(entry -> entry.getValue() != null).collect(Collectors.toMap(Entry::getKey, Entry::getValue));
        List<String> sortedKeys = mdcContextMap.keySet().stream().collect(Collectors.toList());
        Collections.sort(sortedKeys);
        StringBuilder sb = new StringBuilder();
        for (String key : sortedKeys) {
            if (!KEYS_TO_SKIP.contains(key)) {
                sb.append("[").append(key).append(":").append(mdcContextMap.get(key)).append("]");
            }
        }
        // Adding the thread name as a potential mechanism to tie various operations together. A better approach
        // to do this is outlined above, where the PerfContext can be automatically inferred / built along the way.
        sb.append("[").append("ThreadName:").append(Thread.currentThread().getName()).append("]");
        return sb.toString();
    }
}
