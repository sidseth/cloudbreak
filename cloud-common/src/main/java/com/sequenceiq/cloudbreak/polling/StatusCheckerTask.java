package com.sequenceiq.cloudbreak.polling;

public interface StatusCheckerTask<T> {

    boolean checkStatus(T t);

    void handleTimeout(T t);

    String successMessage(T t);

    boolean exitPolling(T t);

    void handleException(Exception e);

    default boolean initialExitCheck(T t) {
        return true;
    }

    default void sendFailureEvent(T t) {

    }

    default void sendTimeoutEvent(T t) {

    }
}
