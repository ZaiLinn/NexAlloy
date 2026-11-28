package app.morphe.extension.music.patches;

import app.morphe.extension.music.settings.Settings;

/**
 * Hides the notifications bell of the top bar, and its unread-content badge.
 */
@SuppressWarnings("unused")
public final class HideNotificationsPatch {
    /**
     * Icon enum values that render the notifications bell:
     * NOTIFICATIONS_INBOX(29), NOTIFICATIONS_OFF(200), NOTIFICATIONS(264),
     * NOTIFICATIONS_OFF_OUTLINE(436), NOTIFICATIONS_CAIRO(1185).
     */
    private static final int[] NOTIFICATION_ICON_VALUES = {29, 200, 264, 436, 1185};

    public static boolean isNotificationDotHidden() {
        return Settings.HIDE_NOTIFICATION_DOT.get();
    }

    public static boolean isNotificationButtonHidden() {
        return Settings.HIDE_ACTIVITY_BUTTON.get();
    }

    /**
     * Reads the icon enum value of a top bar button spec (the icon holder field
     * of the spec holds an int of the icon enum the YouTube app also uses), and
     * tells whether the button renders the notifications bell. The bell of the
     * home top bar is a server-driven button, created without a notification
     * count, so it is identified by its icon and not by its parameters.
     */
    public static boolean isNotificationButton(Object buttonSpec) {
        if (buttonSpec == null) {
            return false;
        }
        try {
            Object iconHolder = buttonSpec.getClass().getDeclaredField("f").get(buttonSpec);
            if (iconHolder == null) {
                return false;
            }
            int iconValue = iconHolder.getClass().getDeclaredField("c").getInt(iconHolder);
            for (int value : NOTIFICATION_ICON_VALUES) {
                if (value == iconValue) {
                    return true;
                }
            }
        } catch (Exception ex) {
            app.morphe.extension.shared.Logger.printException(
                    () -> "isNotificationButton failure", ex);
        }
        return false;
    }
}
