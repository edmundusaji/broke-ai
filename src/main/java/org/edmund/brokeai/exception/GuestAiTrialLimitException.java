package org.edmund.brokeai.exception;

public class GuestAiTrialLimitException extends RuntimeException {

    public static final String ERROR_CODE = "GUEST_AI_LIMIT_REACHED";
    public static final String ERROR_MESSAGE =
        "You have used all 2 free AI scan trials. Please sign in to continue.";

    public GuestAiTrialLimitException() {
        super(ERROR_MESSAGE);
    }
}
