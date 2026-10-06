package com.capstone.etp.kafka;

/**
 * A message that will never be processable (bad JSON, a missing field, a value out of range).
 * The consumer error handler sends it to the dead-letter topic on the first attempt instead of
 * retrying: retrying a poison message would block every account on its partition.
 */
public class MalformedEventException extends RuntimeException {

    public MalformedEventException(String message) {
        super(message);
    }

    public MalformedEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
