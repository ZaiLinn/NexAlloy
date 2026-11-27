package app.morphe.extension.reddit.patches;

/** Host post data exposed without linking the module to the host class loader. */
public interface RuntimeLink {
    String getKindWithId();
    String getTitle();
    String getSelftext();
    String getSubreddit();
    String getLinkFlairText();
}
