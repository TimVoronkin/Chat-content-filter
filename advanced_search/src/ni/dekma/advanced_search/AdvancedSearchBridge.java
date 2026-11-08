package ni.dekma.advanced_search;

import android.content.Context;

public final class AdvancedSearchBridge {
    private static AdvancedSearchCore core;

    public static synchronized void load() {
        if (core != null) {
            try {
                core.unload();
            } catch (Throwable ignored) {}
            core = null;
        }
        core = new AdvancedSearchCore();
        core.load();
    }

    public static synchronized void unload() {
        if (core != null) {
            try {
                core.unload();
            } catch (Throwable ignored) {}
            core = null;
        }
    }

    public static void openSearch(Context context, long peerId, String query) {
        if (context == null) return;
        AdvancedSearchDialog.show(context, peerId, query);
    }

    public static void openSearchWithUser(Context context, long peerId, String query, long userId, String userName) {
        if (context == null) return;
        AdvancedSearchDialog.showWithUser(context, peerId, query, userId, userName);
    }

    public static void setShowInMainSearch(boolean show) {
        AdvancedSearchCore.setShowInMainSearch(show);
    }

    public static void resetHintTutorial(Context context) {
        AdvancedSearchCore.resetHintTutorial(context);
    }
}