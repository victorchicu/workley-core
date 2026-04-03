package ai.workley.core.chat.model;

public enum Reaction {
    LIKED, DISLIKED;

    public static Reaction of(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
