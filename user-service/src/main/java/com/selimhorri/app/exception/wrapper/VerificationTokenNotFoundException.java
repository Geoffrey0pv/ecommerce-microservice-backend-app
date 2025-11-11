package com.selimhorri.app.exception.wrapper;

/**
 * Exception thrown when a verification token is not found in the system.
 * This typically occurs during email verification or password reset flows.
 * 
 * @author ecommerce-team
 * @version 1.1.0
 * @since 2025-10-26
 */
public class VerificationTokenNotFoundException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Default constructor
     */
    public VerificationTokenNotFoundException() {
        super("Verification token not found");
    }
    
    /**
     * Constructor with message and cause
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public VerificationTokenNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Constructor with message
     * @param message the detail message
     */
    public VerificationTokenNotFoundException(String message) {
        super(message);
    }
    
    /**
     * Constructor with cause
     * @param cause the cause of the exception
     */
    public VerificationTokenNotFoundException(Throwable cause) {
        super("Verification token not found", cause);
    }
}