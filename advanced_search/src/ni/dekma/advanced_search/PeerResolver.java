package ni.dekma.advanced_search;

import android.content.Context;
import android.util.LongSparseArray;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public final class PeerResolver {
    private static final LongSparseArray<String> titleCache = new LongSparseArray<>();

    private static Class<?> launchActivityClass;
    private static Field instanceField;
    private static Method getActionBarLayoutMethod;
    private static Field fragmentsStackField;
    private static Method getLastFragmentMethod;

    private static Class<?> userConfigClass;
    private static Field selectedAccountField;
    private static Class<?> messagesControllerClass;
    private static Method getMcInstanceMethod;
    private static Method getUserMethod;
    private static Method getChatMethod;
    private static Class<?> userObjectClass;
    private static Method getUserNameMethod;
    private static Field chatTitleField;

    public static long resolvePeerId(View searchView) {
        if (searchView != null) {
            try {
                Field csdField = searchView.getClass().getDeclaredField("currentSearchDialogId");
                csdField.setAccessible(true);
                long peerId = csdField.getLong(searchView);
                if (peerId != 0) return peerId;
            } catch (Throwable ignored) {
            }

            try {
                Field pfField = searchView.getClass().getDeclaredField("parentFragment");
                pfField.setAccessible(true);
                Object pf = pfField.get(searchView);
                if (pf != null) {
                    long peerId = extractFromFragment(pf);
                    if (peerId != 0) return peerId;
                }
            } catch (Throwable ignored) {
            }
        }

        return resolveActivePeerId();
    }

    public static long resolveActivePeerId() {
        try {
            if (launchActivityClass == null) {
                launchActivityClass = Class.forName("org.telegram.ui.LaunchActivity");
                instanceField = launchActivityClass.getField("instance");
                getActionBarLayoutMethod = launchActivityClass.getMethod("getActionBarLayout");
            }
            Object la = instanceField.get(null);
            if (la == null) return 0;

            Object layout = getActionBarLayoutMethod.invoke(la);
            if (layout == null) return 0;

            if (getLastFragmentMethod == null) {
                getLastFragmentMethod = layout.getClass().getMethod("getLastFragment");
            }

            Object lastFrag = getLastFragmentMethod.invoke(layout);
            if (lastFrag != null) {
                return extractFromFragment(lastFrag);
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    public static String getPeerTitle(Context context, long peerId) {
        if (peerId == 0) return "Все чаты";
        String cached = titleCache.get(peerId);
        if (cached != null) return cached;

        try {
            if (userConfigClass == null) {
                userConfigClass = Class.forName("org.telegram.messenger.UserConfig");
                selectedAccountField = userConfigClass.getField("selectedAccount");
                messagesControllerClass = Class.forName("org.telegram.messenger.MessagesController");
                getMcInstanceMethod = messagesControllerClass.getMethod("getInstance", int.class);
                getUserMethod = messagesControllerClass.getMethod("getUser", Long.class);
                getChatMethod = messagesControllerClass.getMethod("getChat", Long.class);
                userObjectClass = Class.forName("org.telegram.messenger.UserObject");
            }

            int currentAccount = selectedAccountField.getInt(null);
            Object mcInst = getMcInstanceMethod.invoke(null, currentAccount);

            if (peerId > 0) {
                Object user = getUserMethod.invoke(mcInst, peerId);
                if (user != null) {
                    if (getUserNameMethod == null) {
                        getUserNameMethod = userObjectClass.getMethod("getUserName", user.getClass());
                    }
                    Object name = getUserNameMethod.invoke(null, user);
                    if (name != null) {
                        String res = name.toString();
                        titleCache.put(peerId, res);
                        return res;
                    }
                }
            } else {
                Object chat = getChatMethod.invoke(mcInst, -peerId);
                if (chat != null) {
                    if (chatTitleField == null) {
                        chatTitleField = chat.getClass().getField("title");
                    }
                    Object title = chatTitleField.get(chat);
                    if (title != null) {
                        String res = title.toString();
                        titleCache.put(peerId, res);
                        return res;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        String fallback = "Диалог " + peerId;
        titleCache.put(peerId, fallback);
        return fallback;
    }

    private static long extractFromFragment(Object frag) {
        if (frag == null) return 0;
        String name = frag.getClass().getSimpleName();

        if (name.contains("ChatActivity")) {
            try {
                Method getDid = frag.getClass().getMethod("getDialogId");
                Object val = getDid.invoke(frag);
                if (val instanceof Long && (Long) val != 0) return (Long) val;
            } catch (Throwable ignored) {
            }

            try {
                Field dIdField = frag.getClass().getDeclaredField("dialog_id");
                dIdField.setAccessible(true);
                long val = dIdField.getLong(frag);
                if (val != 0) return val;
            } catch (Throwable ignored) {
            }
        }

        if (name.contains("ProfileActivity") || name.contains("ChatUsersActivity")) {
            try {
                Method getDid = frag.getClass().getMethod("getDialogId");
                Object val = getDid.invoke(frag);
                if (val instanceof Long && (Long) val != 0) return (Long) val;
            } catch (Throwable ignored) {
            }
            try {
                Field dIdField = frag.getClass().getDeclaredField("dialogId");
                dIdField.setAccessible(true);
                long val = dIdField.getLong(frag);
                if (val != 0) return val;
            } catch (Throwable ignored) {
            }
        }

        return 0;
    }
}