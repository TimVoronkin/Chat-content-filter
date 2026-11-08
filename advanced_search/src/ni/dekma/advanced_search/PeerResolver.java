package ni.dekma.advanced_search;

import android.content.Context;
import android.util.LongSparseArray;
import android.view.View;
import android.view.ViewParent;

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

    public static int resolveCurrentFolderId(View searchView) {
        if (searchView != null) {
            try {
                Field pfField = searchView.getClass().getDeclaredField("parentFragment");
                pfField.setAccessible(true);
                Object pf = pfField.get(searchView);
                if (pf != null) {
                    int fid = extractFolderFromFragment(pf);
                    if (fid != -1) return fid;
                }
            } catch (Throwable ignored) {}

            ViewParent p = searchView.getParent();
            while (p != null) {
                if (p instanceof View) {
                    try {
                        Field pfField = p.getClass().getDeclaredField("parentFragment");
                        pfField.setAccessible(true);
                        Object pf = pfField.get(p);
                        if (pf != null) {
                            int fid = extractFolderFromFragment(pf);
                            if (fid != -1) return fid;
                        }
                    } catch (Throwable ignored) {}
                }
                p = p.getParent();
            }
        }

        try {
            if (launchActivityClass == null) {
                launchActivityClass = Class.forName("org.telegram.ui.LaunchActivity");
                instanceField = launchActivityClass.getField("instance");
                getActionBarLayoutMethod = launchActivityClass.getMethod("getActionBarLayout");
            }
            Object la = instanceField.get(null);
            if (la != null) {
                Object layout = getActionBarLayoutMethod.invoke(la);
                if (layout != null) {
                    if (getLastFragmentMethod == null) {
                        getLastFragmentMethod = layout.getClass().getMethod("getLastFragment");
                    }
                    Object lastFrag = getLastFragmentMethod.invoke(layout);
                    if (lastFrag != null) {
                        int fid = extractFolderFromFragment(lastFrag);
                        if (fid != -1) return fid;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return -1;
    }

    private static int extractFolderFromFragment(Object frag) {
        if (frag == null) return -1;
        String name = frag.getClass().getSimpleName();
        if (name.contains("DialogsActivity")) {
            try {
                Field cfField = frag.getClass().getDeclaredField("currentFilterId");
                cfField.setAccessible(true);
                return cfField.getInt(frag);
            } catch (Throwable ignored) {}

            try {
                Method getFilterId = frag.getClass().getMethod("getCurrentFilterId");
                Object val = getFilterId.invoke(frag);
                if (val instanceof Integer) return (Integer) val;
            } catch (Throwable ignored) {}
        }
        return -1;
    }

    public static String getFolderName(int currentAccount, int folderId) {
        if (folderId <= 0) return "Все чаты";
        if (folderId == 1) return "Архив";
        try {
            Class<?> mcClass = Class.forName("org.telegram.messenger.MessagesController");
            Method getInstance = mcClass.getMethod("getInstance", int.class);
            Object mcInst = getInstance.invoke(null, currentAccount);
            Field dfField = mcClass.getField("dialogFilters");
            List<?> filters = (List<?>) dfField.get(mcInst);
            if (filters != null) {
                for (Object f : filters) {
                    if (f != null) {
                        Field idF = f.getClass().getField("id");
                        if (idF.getInt(f) == folderId) {
                            Field nameF = f.getClass().getField("name");
                            Object name = nameF.get(f);
                            if (name != null) return name.toString();
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return "Папка " + folderId;
    }

    public static Object getUserOrChatObject(int currentAccount, long peerId) {
        if (peerId == 0) return null;
        try {
            Class<?> mcClass = Class.forName("org.telegram.messenger.MessagesController");
            Method getMc = mcClass.getMethod("getInstance", int.class);
            Object mcInst = getMc.invoke(null, currentAccount);

            if ((peerId & 0x2000000000000000L) != 0 || peerId > 0x1000000000000000L) {
                int encryptedChatId = (int) peerId;
                try {
                    Method getEnc = mcClass.getMethod("getEncryptedChat", Integer.class);
                    Object encChat = getEnc.invoke(mcInst, encryptedChatId);
                    if (encChat != null) {
                        Field uidF = encChat.getClass().getField("user_id");
                        long uid = uidF.getLong(encChat);
                        return getUserOrChatObject(currentAccount, uid);
                    }
                } catch (Throwable ignored) {}
                try {
                    Method getEnc = mcClass.getMethod("getEncryptedChat", int.class);
                    Object encChat = getEnc.invoke(mcInst, encryptedChatId);
                    if (encChat != null) {
                        Field uidF = encChat.getClass().getField("user_id");
                        long uid = uidF.getLong(encChat);
                        return getUserOrChatObject(currentAccount, uid);
                    }
                } catch (Throwable ignored) {}
            }

            if (peerId > 0) {
                try {
                    Method getU = mcClass.getMethod("getUser", Long.class);
                    Object u = getU.invoke(mcInst, peerId);
                    if (u != null) return u;
                } catch (Throwable ignored) {}
                try {
                    Method getU = mcClass.getMethod("getUser", long.class);
                    Object u = getU.invoke(mcInst, peerId);
                    if (u != null) return u;
                } catch (Throwable ignored) {}
                try {
                    Field usersF = mcClass.getField("users");
                    Object usersMap = usersF.get(mcInst);
                    if (usersMap != null) {
                        Method getM = usersMap.getClass().getMethod("get", long.class);
                        Object u = getM.invoke(usersMap, peerId);
                        if (u != null) return u;
                    }
                } catch (Throwable ignored) {}
            } else {
                long chatId = -peerId;
                try {
                    Method getC = mcClass.getMethod("getChat", Long.class);
                    Object c = getC.invoke(mcInst, chatId);
                    if (c != null) return c;
                } catch (Throwable ignored) {}
                try {
                    Method getC = mcClass.getMethod("getChat", long.class);
                    Object c = getC.invoke(mcInst, chatId);
                    if (c != null) return c;
                } catch (Throwable ignored) {}
                try {
                    Field chatsF = mcClass.getField("chats");
                    Object chatsMap = chatsF.get(mcInst);
                    if (chatsMap != null) {
                        Method getM = chatsMap.getClass().getMethod("get", long.class);
                        Object c = getM.invoke(chatsMap, chatId);
                        if (c != null) return c;
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static String getPeerUsername(int currentAccount, long peerId) {
        Object obj = getUserOrChatObject(currentAccount, peerId);
        if (obj != null) {
            try {
                Field unF = obj.getClass().getField("username");
                Object un = unF.get(obj);
                if (un != null && !un.toString().isEmpty()) return un.toString();
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public static String getPeerTitle(Context context, long peerId) {
        if (peerId == 0) return "Все чаты";
        String cached = titleCache.get(peerId);
        if (cached != null) return cached;

        int currentAccount = 0;
        try {
            if (userConfigClass == null) {
                userConfigClass = Class.forName("org.telegram.messenger.UserConfig");
                selectedAccountField = userConfigClass.getField("selectedAccount");
            }
            currentAccount = selectedAccountField.getInt(null);
        } catch (Throwable ignored) {}

        try {
            Object obj = getUserOrChatObject(currentAccount, peerId);
            if (obj != null) {
                String className = obj.getClass().getSimpleName();
                if (className.contains("User")) {
                    try {
                        Field fnF = obj.getClass().getField("first_name");
                        Field lnF = obj.getClass().getField("last_name");
                        String fn = (String) fnF.get(obj);
                        String ln = (String) lnF.get(obj);

                        try {
                            Class<?> ccClass = Class.forName("org.telegram.messenger.ContactsController");
                            Method fmtMethod = ccClass.getMethod("formatName", String.class, String.class);
                            String formatted = (String) fmtMethod.invoke(null, fn, ln);
                            if (formatted != null && !formatted.trim().isEmpty()) {
                                titleCache.put(peerId, formatted);
                                return formatted;
                            }
                        } catch (Throwable ignored) {}

                        String combined = ((fn != null ? fn : "") + " " + (ln != null ? ln : "")).trim();
                        if (!combined.isEmpty()) {
                            titleCache.put(peerId, combined);
                            return combined;
                        }
                    } catch (Throwable ignored) {}

                    try {
                        Class<?> uoClass = Class.forName("org.telegram.messenger.UserObject");
                        for (Method m : uoClass.getMethods()) {
                            if (m.getName().equals("getUserName") && m.getParameterTypes().length == 1) {
                                Object name = m.invoke(null, obj);
                                if (name != null && !name.toString().trim().isEmpty()) {
                                    String res = name.toString();
                                    titleCache.put(peerId, res);
                                    return res;
                                }
                                break;
                            }
                        }
                    } catch (Throwable ignored) {}

                    try {
                        Field unF = obj.getClass().getField("username");
                        String un = (String) unF.get(obj);
                        if (un != null && !un.isEmpty()) {
                            String res = "@" + un;
                            titleCache.put(peerId, res);
                            return res;
                        }
                    } catch (Throwable ignored) {}
                } else if (className.contains("Chat")) {
                    try {
                        Field titleF = obj.getClass().getField("title");
                        Object t = titleF.get(obj);
                        if (t != null && !t.toString().trim().isEmpty()) {
                            String res = t.toString();
                            titleCache.put(peerId, res);
                            return res;
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

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

    public static java.util.List<Long> getAllDialogIds(int currentAccount) {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        try {
            Class<?> mcClass = Class.forName("org.telegram.messenger.MessagesController");
            Method getInstance = mcClass.getMethod("getInstance", int.class);
            Object mcInst = getInstance.invoke(null, currentAccount);

            // 1. Try allDialogs or dialogs_all
            for (String fieldName : new String[]{"allDialogs", "dialogs_all", "dialogs_emptyList"}) {
                try {
                    Field f = mcClass.getField(fieldName);
                    List<?> list = (List<?>) f.get(mcInst);
                    if (list != null && !list.isEmpty()) {
                        for (Object d : list) {
                            if (d != null) {
                                long id = d.getClass().getField("id").getLong(d);
                                if (id != 0 && !ids.contains(id)) ids.add(id);
                            }
                        }
                        if (!ids.isEmpty()) return ids;
                    }
                } catch (Throwable ignored) {}
            }

            // 2. Try dialogs_dict (LongSparseArray or HashMap)
            try {
                Field dictF = mcClass.getField("dialogs_dict");
                Object dict = dictF.get(mcInst);
                if (dict instanceof LongSparseArray) {
                    LongSparseArray<?> lsa = (LongSparseArray<?>) dict;
                    for (int i = 0; i < lsa.size(); i++) {
                        long k = lsa.keyAt(i);
                        if (k != 0 && !ids.contains(k)) ids.add(k);
                    }
                }
            } catch (Throwable ignored) {}

            // 3. Try getAllDialogs()
            try {
                Method m = mcClass.getMethod("getAllDialogs");
                List<?> list = (List<?>) m.invoke(mcInst);
                if (list != null) {
                    for (Object d : list) {
                        if (d != null) {
                            long id = d.getClass().getField("id").getLong(d);
                            if (id != 0 && !ids.contains(id)) ids.add(id);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        return ids;
    }

    public static long resolvePeerFromString(int currentAccount, String input) {
        if (input == null) return 0;
        input = input.trim();
        if (input.isEmpty()) return 0;

        if (input.startsWith("https://t.me/") || input.startsWith("http://t.me/") || input.startsWith("t.me/")) {
            input = input.substring(input.lastIndexOf('/') + 1);
        }

        try {
            return Long.parseLong(input);
        } catch (NumberFormatException ignored) {}

        String cleanUn = input.startsWith("@") ? input.substring(1) : input;
        try {
            Class<?> mcClass = Class.forName("org.telegram.messenger.MessagesController");
            Method getInstance = mcClass.getMethod("getInstance", int.class);
            Object mcInst = getInstance.invoke(null, currentAccount);

            try {
                Method m = mcClass.getMethod("getUserOrChat", String.class);
                Object obj = m.invoke(mcInst, cleanUn);
                if (obj != null) {
                    String cls = obj.getClass().getSimpleName();
                    if (cls.contains("User")) {
                        return obj.getClass().getField("id").getLong(obj);
                    } else if (cls.contains("Chat")) {
                        return -obj.getClass().getField("id").getLong(obj);
                    }
                }
            } catch (Throwable ignored) {}

            try {
                Method m = mcClass.getMethod("getUser", String.class);
                Object user = m.invoke(mcInst, cleanUn);
                if (user != null) {
                    return user.getClass().getField("id").getLong(user);
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        return 0;
    }
}
