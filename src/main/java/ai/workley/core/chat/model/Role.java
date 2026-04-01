package ai.workley.core.chat.model;

public enum Role {
    ANONYMOUS, USER, ASSISTANT, UNKNOWN;

    private static final String AUTHORITY_PREFIX = "ROLE_";

    public static Role of(String authority) {
        if (authority == null) {
            return UNKNOWN;
        }
        try {
            return valueOf(authority.substring(AUTHORITY_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
