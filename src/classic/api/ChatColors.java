package classic.api;

public enum ChatColors {
    BLACK("&0"),
    DARK_BLUE("&1"),
    DARK_GREEN("&2"),
    DARK_AQUA("&3"),
    DARK_RED("&4"),
    PURPLE("&5"),
    GOLD("&6"),
    GRAY("&7"),
    DARK_GRAY("&8"),
    BLUE("&9"),
    GREEN("&a"),
    AQUA("&b"),
    RED("&c"),
    LIGHT_PURPLE("&d"),
    YELLOW("&e"),
    WHITE("&f");

    private final String code;

    ChatColors(String code) {
        this.code = code;
    }

    @Override
    public String toString() {
        return code;
    }

    public String getCode() {
        return code;
    }

    public static boolean startsWithColor(String text) {
        if (text == null || text.length() < 2) return false;

        for (ChatColors color : values()) {
            if (text.startsWith(color.code)) {
                return true;
            }
        }
        return false;
    }

    public static ChatColors getColor(String text) {
        if (text == null || text.length() < 2) return null;

        for (ChatColors color : values()) {
            if (text.startsWith(color.code)) {
                return color;
            }
        }
        return null;
    }

}