package edu.eci.orders.lambda.custom;

/**
 * Error returned to the Lambda runtime. Its message is the JSON error body
 * ({"error": ..., "message": ...}) so that API Gateway (custom integration)
 * can match the error code with a regex and pass the body through unchanged.
 */
public class OrderApiException extends RuntimeException {

    private final String error;

    public OrderApiException(String error, String message) {
        super("{\"error\":\"" + escape(error) + "\",\"message\":\"" + escape(message) + "\"}");
        this.error = error;
    }

    public String getError() {
        return error;
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
