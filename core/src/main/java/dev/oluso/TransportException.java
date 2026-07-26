package dev.oluso;

/** Thrown internally when sending an error report to the ingestion API fails. */
public final class TransportException extends RuntimeException {
    public TransportException(String message) {
        super(message);
    }

    public TransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
