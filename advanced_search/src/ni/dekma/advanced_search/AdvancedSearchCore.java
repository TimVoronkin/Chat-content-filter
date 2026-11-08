package ni.dekma.advanced_search;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public final class AdvancedSearchCore {
    private static final String BUTTON_TAG = "extera_advanced_search_btn";
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final TimeInterpolator EASE_OUT_QUINT = new TimeInterpolator() {
        @Override
        public float getInterpolation(float t) {
            t -= 1.0f;
            return t * t * t * t * t + 1.0f;
        }
    };

    private static boolean fieldsReflected = false;
    private static Field editTextField;
    private static Field additionalIconsLayoutField;

    private final List<Object> unhooks = new ArrayList<>();

    public void load() {
        removeAttachedButtons();
        hookFragmentSearchField();
        hookFilteredSearchView();
        hookActionBarMenuItem();
        hookActionBar();
        hookDialogsActivity();
        hookChatActivity();
        hookBaseFragment();
        hookLaunchActivity();
        scanAndAttachActiveViews();
    }

    public void unload() {
        for (Object unhook : unhooks) {
            try {
                if (unhook instanceof de.robv.android.xposed.XC_MethodHook.Unhook) {
                    ((de.robv.android.xposed.XC_MethodHook.Unhook) unhook).unhook();
                } else if (unhook instanceof br.dekma.XC_MethodHook.Unhook) {
                    ((br.dekma.XC_MethodHook.Unhook) unhook).unhook();
                } else if (unhook != null) {
                    Method m = unhook.getClass().getMethod("unhook");
                    m.invoke(unhook);
                }
            } catch (Throwable ignored) {
            }
        }
        unhooks.clear();
        removeAttachedButtons();
    }

    public static void removeAttachedButtons() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Class<?> laClass = Class.forName("org.telegram.ui.LaunchActivity");
                    Field instF = laClass.getField("instance");
                    Object la = instF.get(null);
                    if (la instanceof android.app.Activity) {
                        View decor = ((android.app.Activity) la).getWindow().getDecorView();
                        removeButtonsFromView(decor);
                    }
                } catch (Throwable ignored) {}
            }
        });
    }

    private static void removeButtonsFromView(View view) {
        if (view == null) return;
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            View btn = vg.findViewWithTag(BUTTON_TAG);
            if (btn != null) {
                vg.removeView(btn);
            }
            for (int i = 0; i < vg.getChildCount(); i++) {
                removeButtonsFromView(vg.getChildAt(i));
            }
        }
    }

    private void hookDialogsActivity() {
        try {
            Class<?> targetCls = Class.forName("org.telegram.ui.DialogsActivity");
            for (Method m : targetCls.getDeclaredMethods()) {
                String name = m.getName();
                if ("createView".equals(name) || "onResume".equals(name) || "onTransitionAnimationEnd".equals(name) || "showSearchField".equals(name) || "checkSearchContainer".equals(name)) {
                    hookMember(m, false);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void hookChatActivity() {
        try {
            Class<?> targetCls = Class.forName("org.telegram.ui.ChatActivity");
            for (Method m : targetCls.getDeclaredMethods()) {
                String name = m.getName();
                if ("createView".equals(name) || "onResume".equals(name) || "onTransitionAnimationEnd".equals(name)) {
                    hookMember(m, false);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void hookBaseFragment() {
        try {
            Class<?> targetCls = Class.forName("org.telegram.ui.ActionBar.BaseFragment");
            for (Method m : targetCls.getDeclaredMethods()) {
                String name = m.getName();
                if ("createView".equals(name) || "onResume".equals(name) || "onTransitionAnimationEnd".equals(name)) {
                    hookMember(m, false);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void hookLaunchActivity() {
        try {
            Class<?> targetCls = Class.forName("org.telegram.ui.LaunchActivity");
            for (Method m : targetCls.getDeclaredMethods()) {
                String name = m.getName();
                if ("onResume".equals(name) || "onAttachedToWindow".equals(name) || "showFragment".equals(name)) {
                    hookMember(m, false);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void hookActionBar() {
        try {
            Class<?> targetCls = Class.forName("org.telegram.ui.ActionBar.ActionBar");
            for (Method m : targetCls.getDeclaredMethods()) {
                String name = m.getName();
                if ("createSearchField".equals(name) || "openSearchField".equals(name) || "onSearchFieldVisibilityChanged".equals(name)) {
                    hookMember(m, false);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static class ActiveViewsScanner implements Runnable {
        @Override
        public void run() {
            try {
                Class<?> laClass = Class.forName("org.telegram.ui.LaunchActivity");
                Field instF = laClass.getField("instance");
                Object la = instF.get(null);
                if (la instanceof android.app.Activity) {
                    android.view.View decor = ((android.app.Activity) la).getWindow().getDecorView();
                    scanViewTree(decor);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public static void scanAndAttachActiveViews() {
        mainHandler.post(new ActiveViewsScanner());
        mainHandler.postDelayed(new ActiveViewsScanner(), 150);
        mainHandler.postDelayed(new ActiveViewsScanner(), 500);
        mainHandler.postDelayed(new ActiveViewsScanner(), 1200);
        mainHandler.postDelayed(new ActiveViewsScanner(), 2500);
    }

    private static void scanViewTree(View view) {
        if (view == null) return;
        if (view instanceof FrameLayout) {
            String name = view.getClass().getName();
            if (name.contains("FragmentSearchField") || name.contains("FilteredSearchView") || name.contains("ActionBarMenuItem")) {
                attachSearchButton((FrameLayout) view);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                scanViewTree(vg.getChildAt(i));
            }
        }
    }

    private void hookFragmentSearchField() {
        try {
            Class<?> targetCls = Class.forName("org.telegram.ui.Components.FragmentSearchField");
            for (Constructor<?> ctor : targetCls.getConstructors()) {
                hookMember(ctor, true);
            }
            for (Method m : targetCls.getDeclaredMethods()) {
                String name = m.getName();
                if ("onAttachedToWindow".equals(name) || "show".equals(name) || "setVisibility".equals(name) || "onLayout".equals(name) || "onMeasure".equals(name)) {
                    hookMember(m, true);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void hookFilteredSearchView() {
        try {
            Class<?> targetCls = Class.forName("org.telegram.ui.FilteredSearchView");
            for (Constructor<?> ctor : targetCls.getConstructors()) {
                hookMember(ctor, true);
            }
            for (Method m : targetCls.getDeclaredMethods()) {
                String name = m.getName();
                if ("onAttachedToWindow".equals(name) || "setVisibility".equals(name) || "show".equals(name) || "init".equals(name)) {
                    hookMember(m, true);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void hookActionBarMenuItem() {
        try {
            Class<?> targetCls = Class.forName("org.telegram.ui.ActionBar.ActionBarMenuItem");
            for (Constructor<?> ctor : targetCls.getConstructors()) {
                hookMember(ctor, false);
            }
            for (Method m : targetCls.getDeclaredMethods()) {
                String name = m.getName();
                if ("toggleSearch".equals(name) || "createSearchContainer".equals(name) || "checkSearchContainer".equals(name) || "openSearch".equals(name) || "showSearchField".equals(name) || "onAttachedToWindow".equals(name)) {
                    hookMember(m, false);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static final class RobustMethodHook extends de.robv.android.xposed.XC_MethodHook {
        private final boolean isSearchFieldDirect;

        RobustMethodHook(boolean isSearchFieldDirect) {
            this.isSearchFieldDirect = isSearchFieldDirect;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            try {
                handleHook(param != null ? param.thisObject : null, isSearchFieldDirect);
            } finally {
                param = null;
            }
        }
    }

    private void hookMember(Member member, final boolean isSearchFieldDirect) {
        try {
            Object unhook = de.robv.android.xposed.XposedBridge.hookMethod(member, new RobustMethodHook(isSearchFieldDirect));
            if (unhook != null) unhooks.add(unhook);
        } catch (Throwable ignored) {
        }
    }

    private static final class HookHandlerRunnable implements Runnable {
        private final Object thisObj;
        private final boolean isSearchFieldDirect;

        HookHandlerRunnable(Object thisObj, boolean isSearchFieldDirect) {
            this.thisObj = thisObj;
            this.isSearchFieldDirect = isSearchFieldDirect;
        }

        @Override
        public void run() {
            try {
                if (isSearchFieldDirect && thisObj instanceof FrameLayout) {
                    attachSearchButton((FrameLayout) thisObj);
                    return;
                }

                Class<?> clazz = thisObj.getClass();
                if (clazz.getName().contains("ActionBarMenuItem") && thisObj instanceof FrameLayout) {
                    attachSearchButton((FrameLayout) thisObj);
                    return;
                }

                if (clazz.getName().contains("DialogsActivity") || clazz.getName().contains("ChatActivity") || clazz.getName().contains("LaunchActivity") || clazz.getName().contains("BaseFragment") || clazz.getName().contains("ActionBar")) {
                    try {
                        Class<?> laClass = Class.forName("org.telegram.ui.LaunchActivity");
                        Field instF = laClass.getField("instance");
                        Object la = instF.get(null);
                        if (la instanceof android.app.Activity) {
                            android.view.View decor = ((android.app.Activity) la).getWindow().getDecorView();
                            scanViewTree(decor);
                        }
                    } catch (Throwable ignored2) {}
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static void handleHook(final Object thisObj, final boolean isSearchFieldDirect) {
        if (thisObj == null) return;
        mainHandler.post(new HookHandlerRunnable(thisObj, isSearchFieldDirect));
    }

    private static final class SearchButton extends TextView {
        private final View searchEt;

        SearchButton(Context context, View searchEt) {
            super(context);
            this.searchEt = searchEt;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            updateButtonTheme(this, searchEt, getContext());
        }
    }

    public static volatile boolean showInMainSearch = true;

    public static void setShowInMainSearch(final boolean show) {
        showInMainSearch = show;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Class<?> laClass = Class.forName("org.telegram.ui.LaunchActivity");
                    Object la = laClass.getField("instance").get(null);
                    if (la instanceof android.app.Activity) {
                        android.view.View decor = ((android.app.Activity) la).getWindow().getDecorView();
                        updateAllButtonsVisibility(decor, show);
                    }
                } catch (Throwable ignored) {}
            }
        });
    }

    private static void updateAllButtonsVisibility(View view, boolean show) {
        if (view == null) return;
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            View btn = vg.findViewWithTag(BUTTON_TAG);
            if (btn != null) {
                Object parentFrag = resolveParentFragment(vg);
                if (isMainChatsSearch(vg, parentFrag)) {
                    if (!show) {
                        btn.setVisibility(View.GONE);
                        vg.removeView(btn);
                    } else {
                        btn.setVisibility(View.VISIBLE);
                    }
                }
            }
            for (int i = 0; i < vg.getChildCount(); i++) {
                updateAllButtonsVisibility(vg.getChildAt(i), show);
            }
        }
    }

    private static Object resolveParentFragment(View view) {
        if (view == null) return null;
        Object parentFrag = null;
        try {
            Field pfField = view.getClass().getDeclaredField("parentFragment");
            pfField.setAccessible(true);
            parentFrag = pfField.get(view);
        } catch (Throwable ignored) {}
        if (parentFrag == null) {
            ViewParent p = view.getParent();
            while (p != null) {
                if (p instanceof View) {
                    try {
                        Field pfField = p.getClass().getDeclaredField("parentFragment");
                        pfField.setAccessible(true);
                        parentFrag = pfField.get(p);
                        if (parentFrag != null) break;
                    } catch (Throwable ignored) {}
                }
                p = p.getParent();
            }
        }
        if (parentFrag == null) {
            try {
                Class<?> laClass = Class.forName("org.telegram.ui.LaunchActivity");
                Object la = laClass.getField("instance").get(null);
                if (la != null) {
                    Method getLayout = laClass.getMethod("getActionBarLayout");
                    Object layout = getLayout.invoke(la);
                    if (layout != null) {
                        Method getLastFrag = layout.getClass().getMethod("getLastFragment");
                        parentFrag = getLastFrag.invoke(layout);
                    }
                }
            } catch (Throwable ignored) {}
        }
        return parentFrag;
    }

    private static boolean isMainChatsSearch(View view, Object parentFrag) {
        if (view != null) {
            View current = view;
            while (current != null) {
                String cls = current.getClass().getName();
                if (cls.contains("ChatActivity")) {
                    return false;
                }
                if (cls.contains("DialogsActivity")) {
                    return true;
                }
                try {
                    Field pfField = current.getClass().getDeclaredField("parentFragment");
                    pfField.setAccessible(true);
                    Object pf = pfField.get(current);
                    if (pf != null) {
                        String pName = pf.getClass().getName();
                        if (pName.contains("ChatActivity")) return false;
                        if (pName.contains("DialogsActivity")) return true;
                    }
                } catch (Throwable ignored) {}

                ViewParent vp = current.getParent();
                if (vp instanceof View) {
                    current = (View) vp;
                } else {
                    break;
                }
            }
        }

        if (parentFrag != null) {
            String name = parentFrag.getClass().getName();
            if (name.contains("DialogsActivity")) {
                return true;
            }
            if (name.contains("ChatActivity")) {
                return false;
            }
        }

        try {
            Class<?> laClass = Class.forName("org.telegram.ui.LaunchActivity");
            Object la = laClass.getField("instance").get(null);
            if (la != null) {
                Method getLayout = laClass.getMethod("getActionBarLayout");
                Object layout = getLayout.invoke(la);
                if (layout != null) {
                    Method getLastFrag = layout.getClass().getMethod("getLastFragment");
                    Object lastFrag = getLastFrag.invoke(layout);
                    if (lastFrag != null) {
                        String fName = lastFrag.getClass().getName();
                        if (fName.contains("ChatActivity")) {
                            return false;
                        }
                        if (fName.contains("DialogsActivity")) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return true;
    }

    private static boolean isExcludedFragment(Object frag) {
        if (frag == null) return false;
        String name = frag.getClass().getName();
        String simple = frag.getClass().getSimpleName();
        return name.contains("Settings") || name.contains("Theme") || name.contains("Language") ||
               name.contains("Sticker") || name.contains("Emoji") || name.contains("Filter") ||
               name.contains("Passcode") || name.contains("Privacy") || name.contains("Storage") ||
               name.contains("Cache") || name.contains("Session") || name.contains("Wallet") ||
               name.contains("Proxy") || name.contains("Notification") || name.contains("QuickReplies") ||
               name.contains("Intro") || name.contains("Data") || name.contains("TwoStep") ||
               name.contains("Folders") || name.contains("ChangePhone") || name.contains("AutoDelete") ||
               name.contains("Account") || name.contains("Login") || name.contains("Auth") ||
               simple.contains("Settings") || simple.contains("Theme") || simple.contains("Filter") ||
               simple.contains("Language") || simple.contains("Session") || simple.contains("Privacy");
    }

    public static void attachSearchButton(final FrameLayout fsf) {
        if (fsf == null) return;

        String clsName = fsf.getClass().getName();
        if (clsName.contains("EnterView") || clsName.contains("ChatActivityEnterView") || 
            clsName.contains("EditTextCaption") || clsName.contains("SizeNotifierFrameLayout") ||
            clsName.contains("Mention") || clsName.contains("Bottom") || clsName.contains("Message") ||
            clsName.contains("Settings") || clsName.contains("Theme") || clsName.contains("Language")) {
            return;
        }

        Object parentFrag = resolveParentFragment(fsf);
        if (isExcludedFragment(parentFrag)) {
            return;
        }

        if (!showInMainSearch && isMainChatsSearch(fsf, parentFrag)) {
            return;
        }

        FrameLayout targetContainer = fsf;
        if (clsName.contains("ActionBarMenuItem")) {
            Field isSearchF = null;
            Field scF = null;
            Class<?> clazz = fsf.getClass();
            while (clazz != null && !clazz.equals(Object.class)) {
                if (isSearchF == null) {
                    try {
                        isSearchF = clazz.getDeclaredField("isSearchField");
                        isSearchF.setAccessible(true);
                    } catch (Throwable ignored) {}
                }
                if (scF == null) {
                    try {
                        scF = clazz.getDeclaredField("searchContainer");
                        scF.setAccessible(true);
                    } catch (Throwable ignored) {}
                }
                clazz = clazz.getSuperclass();
            }
            if (isSearchF != null) {
                try {
                    if (!isSearchF.getBoolean(fsf)) return;
                } catch (Throwable ignored) {}
            }
            if (scF != null) {
                try {
                    Object sc = scF.get(fsf);
                    if (sc instanceof FrameLayout) {
                        targetContainer = (FrameLayout) sc;
                    }
                } catch (Throwable ignored) {}
            }
        }

        if (targetContainer.findViewWithTag(BUTTON_TAG) != null) {
            return;
        }

        final Context context = targetContainer.getContext();
        if (context == null) {
            return;
        }

        if (!fieldsReflected) {
            reflectFields(targetContainer.getClass());
            fieldsReflected = true;
        }

        View searchEt = null;
        if (editTextField != null) {
            try {
                Object et = editTextField.get(targetContainer);
                if (et instanceof View) {
                    searchEt = (View) et;
                }
            } catch (Throwable ignored) {
            }
        }

        if (searchEt == null) {
            searchEt = findEditTextRecursive(targetContainer);
        }
        if (searchEt == null && targetContainer != fsf) {
            searchEt = findEditTextRecursive(fsf);
        }

        if (searchEt == null) {
            return;
        }

        if (searchEt instanceof TextView) {
            CharSequence hint = ((TextView) searchEt).getHint();
            if (hint != null) {
                String h = hint.toString().toLowerCase(java.util.Locale.ROOT);
                if (h.contains("настройк") || h.contains("setting") || h.contains("параметр")) {
                    return;
                }
            }
        }

        final View finalSearchEt = searchEt;
        final FrameLayout finalContainer = targetContainer;
        final SearchButton btn = new SearchButton(context, finalSearchEt);
        btn.setTag(BUTTON_TAG);
        btn.setText(AdvancedSearchDialog.trStatic("Расширенный поиск", "Advanced search"));
        btn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.0f);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btn.setGravity(Gravity.CENTER);
        btn.setSingleLine(true);
        btn.setIncludeFontPadding(false);
        btn.setPadding(dp(context, 10.0f), dp(context, 3.0f), dp(context, 10.0f), dp(context, 3.0f));
        btn.setClickable(true);
        btn.setFocusable(false);

        int dp28 = dp(context, 28.0f);
        updateButtonTheme(btn, finalSearchEt, context);

        registerThemeListener(context, btn, new Runnable() {
            @Override
            public void run() {
                updateButtonTheme(btn, finalSearchEt, context);
            }
        });

        btn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (v.getParent() != null) {
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(120).setInterpolator(EASE_OUT_QUINT).withLayer().start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (v.getParent() != null) {
                            v.getParent().requestDisallowInterceptTouchEvent(false);
                        }
                        v.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(EASE_OUT_QUINT).withLayer().start();
                        break;
                }
                return false;
            }
        });

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performHaptic(v, HapticFeedbackConstants.KEYBOARD_TAP);
                String query = "";
                if (finalSearchEt instanceof EditText) {
                    query = ((EditText) finalSearchEt).getText().toString().trim();
                }

                long currentPeerId = PeerResolver.resolvePeerId(finalContainer);
                Context clickContext = v.getContext() != null ? v.getContext() : finalContainer.getContext();
                AdvancedSearchDialog.show(clickContext, currentPeerId, query);
            }
        });

        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp28);
        flp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        flp.rightMargin = dp(context, 4.0f);
        finalContainer.addView(btn, flp);
        btn.bringToFront();

        updateButtonPosition(finalContainer, btn, finalSearchEt, context);

        finalContainer.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                updateButtonPosition(finalContainer, btn, finalSearchEt, context);
            }
        });

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                updateButtonPosition(finalContainer, btn, finalSearchEt, context);
            }
        });
    }

    private static EditText findEditTextRecursive(View root) {
        if (root == null) return null;
        if (root instanceof EditText) return (EditText) root;
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                EditText et = findEditTextRecursive(vg.getChildAt(i));
                if (et != null) return et;
            }
        }
        return null;
    }

    private static void updateButtonPosition(FrameLayout fsf, TextView btn, View searchEt, Context context) {
        if (fsf == null || btn == null || context == null) return;
        try {
            if (!showInMainSearch && isMainChatsSearch(fsf, null)) {
                btn.setVisibility(View.GONE);
                fsf.removeView(btn);
                return;
            }

            int fsfWidth = fsf.getWidth();
            int dp4 = dp(context, 4.0f);
            int dp6 = dp(context, 6.0f);
            int dp28 = dp(context, 28.0f);
            int maxRightChildOffset = dp4;

            for (int i = 0; i < fsf.getChildCount(); i++) {
                View child = fsf.getChildAt(i);
                if (child == null || child == btn || child == searchEt || child.getVisibility() == View.GONE) {
                    continue;
                }

                int childWidth = child.getWidth();
                if (childWidth <= 0) {
                    ViewGroup.LayoutParams lp = child.getLayoutParams();
                    if (lp != null && lp.width > 0) childWidth = lp.width;
                }

                int childLeft = child.getLeft();
                boolean isRightGravity = false;
                ViewGroup.LayoutParams clp = child.getLayoutParams();
                if (clp instanceof FrameLayout.LayoutParams) {
                    int grav = ((FrameLayout.LayoutParams) clp).gravity;
                    if ((grav & Gravity.END) == Gravity.END || (grav & Gravity.RIGHT) == Gravity.RIGHT) {
                        isRightGravity = true;
                    }
                }

                if (fsfWidth > 0 && (isRightGravity || (childLeft > 0 && childLeft >= fsfWidth - dp(context, 90.0f)))) {
                    int fromRight = fsfWidth - childLeft;
                    if (fromRight > maxRightChildOffset) {
                        maxRightChildOffset = fromRight + dp4;
                    }
                } else if (isRightGravity && childWidth > 0) {
                    int fromRight = childWidth + dp4;
                    if (fromRight > maxRightChildOffset) {
                        maxRightChildOffset = fromRight;
                    }
                }
            }

            int btnWidth = btn.getWidth();
            if (btnWidth <= 0) {
                btn.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(dp28, View.MeasureSpec.EXACTLY));
                btnWidth = btn.getMeasuredWidth();
            }
            if (btnWidth <= 0) btnWidth = dp(context, 130.0f);

            int shiftX = -(maxRightChildOffset - dp4);
            float targetTranslationX = (float) shiftX;

            Object initTag = btn.getTag(0x7F100002);
            if (initTag == null) {
                btn.setTag(0x7F100002, Boolean.TRUE);
                btn.setTag(0x7F100001, targetTranslationX);
                btn.setTranslationX(targetTranslationX);
            } else {
                float currentTarget = btn.getTranslationX();
                Object tagObj = btn.getTag(0x7F100001);
                if (tagObj instanceof Float) {
                    currentTarget = (Float) tagObj;
                }

                if (Math.abs(currentTarget - targetTranslationX) > 1.0f) {
                    btn.setTag(0x7F100001, targetTranslationX);
                    btn.animate()
                            .translationX(targetTranslationX)
                            .setDuration(220)
                            .setInterpolator(EASE_OUT_QUINT)
                            .withLayer()
                            .start();
                }
            }

            btn.bringToFront();

            if (searchEt != null) {
                int totalRightPadding = maxRightChildOffset + btnWidth + dp6;
                if (Math.abs(searchEt.getPaddingRight() - totalRightPadding) > dp(context, 4.0f)) {
                    searchEt.setPadding(
                            searchEt.getPaddingLeft(),
                            searchEt.getPaddingTop(),
                            totalRightPadding,
                            searchEt.getPaddingBottom()
                    );
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void reflectFields(Class<?> clazz) {
        Class<?> current = clazz;
        while (current != null && !current.equals(Object.class)) {
            if (editTextField == null) {
                try {
                    editTextField = current.getDeclaredField("searchEditText");
                    editTextField.setAccessible(true);
                } catch (Throwable ignored) {
                }
            }
            if (editTextField == null) {
                try {
                    editTextField = current.getDeclaredField("searchField");
                    editTextField.setAccessible(true);
                } catch (Throwable ignored) {
                }
            }
            if (editTextField == null) {
                try {
                    editTextField = current.getDeclaredField("editText");
                    editTextField.setAccessible(true);
                } catch (Throwable ignored) {
                }
            }
            if (additionalIconsLayoutField == null) {
                try {
                    additionalIconsLayoutField = current.getDeclaredField("additionalIconsLayout");
                    additionalIconsLayoutField.setAccessible(true);
                } catch (Throwable ignored) {
                }
            }
            current = current.getSuperclass();
        }
    }

    private static int dp(Context context, float value) {
        try {
            return (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    value,
                    context.getResources().getDisplayMetrics()
            );
        } catch (Throwable ignored) {
            return (int) (value * 2.0f);
        }
    }

    private static void updateButtonTheme(TextView btn, View finalSearchEt, Context context) {
        if (btn == null || context == null) return;
        boolean isDark = isDarkTheme();
        int textColor;
        if (isDark) {
            int themeText = getThemeColor("windowBackgroundWhiteBlackText", 0);
            if (themeText != 0 && !isColorDark(themeText)) {
                textColor = themeText;
            } else {
                textColor = 0xFFFFFFFF;
            }
        } else {
            int themeText = getThemeColor("windowBackgroundWhiteBlackText", 0);
            if (themeText != 0 && isColorDark(themeText)) {
                textColor = themeText;
            } else {
                textColor = 0xFF1C1B1F;
            }
        }
        btn.setText(AdvancedSearchDialog.trStatic("Расширенный поиск", "Advanced search"));
        btn.setTextColor(textColor);
        btn.setBackground(createNativeMatchingDrawable(context, isDark));
        btn.invalidate();
    }

    private static Drawable createNativeMatchingDrawable(Context context, boolean isDark) {
        int accent = getThemeColor("featuredStickers_addButton", 0);
        if (accent == 0) accent = getThemeColor("windowBackgroundWhiteBlueText4", 0);
        if (accent == 0) accent = getThemeColor("actionBarDefaultSearch", 0);

        int pillBgColor;
        int strokeColor;
        if (accent != 0) {
            int rgb = accent & 0x00FFFFFF;
            int bgAlpha = isDark ? 0x4D000000 : 0x33000000;
            int strokeAlpha = isDark ? 0x80000000 : 0x55000000;
            pillBgColor = rgb | bgAlpha;
            strokeColor = rgb | strokeAlpha;
        } else {
            pillBgColor = isDark ? 0x2AFFFFFF : 0x1A000000;
            strokeColor = isDark ? 0x44FFFFFF : 0x2E000000;
        }

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dp(context, 100.0f));
        shape.setColor(pillBgColor);
        shape.setStroke(dp(context, 1.0f), strokeColor);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int rippleColor = isDark ? 0x33FFFFFF : 0x24000000;
            GradientDrawable mask = new GradientDrawable();
            mask.setShape(GradientDrawable.RECTANGLE);
            mask.setCornerRadius(dp(context, 100.0f));
            mask.setColor(0xFFFFFFFF);
            return new RippleDrawable(ColorStateList.valueOf(rippleColor), shape, mask);
        }
        return shape;
    }

    private static boolean isDarkTheme() {
        try {
            Class<?> themeClass = Class.forName("org.telegram.ui.ActionBar.Theme");
            try {
                Method m = themeClass.getMethod("isCurrentThemeDark");
                Object res = m.invoke(null);
                if (res instanceof Boolean) return (Boolean) res;
            } catch (Throwable ignored) {}
            try {
                Method m = themeClass.getMethod("isCurrentThemeDay");
                Object res = m.invoke(null);
                if (res instanceof Boolean) return !((Boolean) res);
            } catch (Throwable ignored) {}
            int winBg = getThemeColor("windowBackgroundWhite", 0);
            if (winBg != 0) return isColorDark(winBg);
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean isColorDark(int color) {
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
        return darkness >= 0.5;
    }

    private static int getThemeColor(String key, int fallback) {
        try {
            Class<?> themeClass = Class.forName("org.telegram.ui.ActionBar.Theme");
            try {
                Field keyField = themeClass.getField("key_" + key);
                Object keyVal = keyField.get(null);
                if (keyVal instanceof Integer) {
                    try {
                        Method getColorMethod = themeClass.getMethod("getColor", int.class);
                        Object res = getColorMethod.invoke(null, (Integer) keyVal);
                        if (res instanceof Integer && (Integer) res != 0) {
                            return (Integer) res;
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
            try {
                Field keyField = themeClass.getField(key);
                Object keyVal = keyField.get(null);
                if (keyVal instanceof Integer) {
                    try {
                        Method getColorMethod = themeClass.getMethod("getColor", int.class);
                        Object res = getColorMethod.invoke(null, (Integer) keyVal);
                        if (res instanceof Integer && (Integer) res != 0) {
                            return (Integer) res;
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
            try {
                Method getColorMethod = themeClass.getMethod("getColor", String.class);
                Object res = getColorMethod.invoke(null, key);
                if (res instanceof Integer && (Integer) res != 0) {
                    return (Integer) res;
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static void registerThemeListener(final Context context, final View btn, final Runnable onThemeChanged) {
        try {
            Class<?> notifCenterClass = Class.forName("org.telegram.messenger.NotificationCenter");
            final List<Object> notifCenters = new ArrayList<>();
            try {
                Method getGlobal = notifCenterClass.getMethod("getGlobalInstance");
                Object g = getGlobal.invoke(null);
                if (g != null) notifCenters.add(g);
            } catch (Throwable ignored) {}
            try {
                Method getInstance = notifCenterClass.getMethod("getInstance", int.class);
                for (int a = 0; a < 4; a++) {
                    Object inst = getInstance.invoke(null, a);
                    if (inst != null && !notifCenters.contains(inst)) notifCenters.add(inst);
                }
            } catch (Throwable ignored) {}

            String[] notifNames = new String[]{
                    "didSetNewTheme", "themeAccentUpdated", "needSetDayNightTheme",
                    "wallpaperChanged", "wallpaperUpdated", "reloadInterface",
                    "themeUploadedToServer", "didSetTheme"
            };

            final List<Integer> notifIds = new ArrayList<>();
            for (String name : notifNames) {
                try {
                    Field f = notifCenterClass.getField(name);
                    notifIds.add(f.getInt(null));
                } catch (Throwable ignored) {}
            }

            Class<?> delegateClass = Class.forName("org.telegram.messenger.NotificationCenter$NotificationCenterDelegate");
            final Object delegateProxy = Proxy.newProxyInstance(
                    delegateClass.getClassLoader(),
                    new Class<?>[]{delegateClass},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            if ("didReceivedNotification".equals(method.getName())) {
                                mainHandler.post(onThemeChanged);
                            }
                            return null;
                        }
                    }
            );

            final Method addObserverMethod = notifCenterClass.getMethod("addObserver", Object.class, int.class);
            final Method removeObserverMethod = notifCenterClass.getMethod("removeObserver", Object.class, int.class);

            for (Object center : notifCenters) {
                for (int id : notifIds) {
                    try {
                        addObserverMethod.invoke(center, delegateProxy, id);
                    } catch (Throwable ignored) {}
                }
            }

            Object wallpaperListenerObj = null;
            if (Build.VERSION.SDK_INT >= 27 && context != null) {
                try {
                    final android.app.WallpaperManager wm = android.app.WallpaperManager.getInstance(context);
                    android.app.WallpaperManager.OnColorsChangedListener wpListener = new android.app.WallpaperManager.OnColorsChangedListener() {
                        @Override
                        public void onColorsChanged(android.app.WallpaperColors colors, int which) {
                            mainHandler.post(onThemeChanged);
                        }
                    };
                    wm.addOnColorsChangedListener(wpListener, mainHandler);
                    wallpaperListenerObj = wpListener;
                } catch (Throwable ignored) {}
            }
            final Object finalWpListener = wallpaperListenerObj;

            btn.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    mainHandler.post(onThemeChanged);
                    for (Object center : notifCenters) {
                        for (int id : notifIds) {
                            try {
                                addObserverMethod.invoke(center, delegateProxy, id);
                            } catch (Throwable ignored) {}
                        }
                    }
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    for (Object center : notifCenters) {
                        for (int id : notifIds) {
                            try {
                                removeObserverMethod.invoke(center, delegateProxy, id);
                            } catch (Throwable ignored) {}
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 27 && finalWpListener instanceof android.app.WallpaperManager.OnColorsChangedListener && context != null) {
                        try {
                            android.app.WallpaperManager wm = android.app.WallpaperManager.getInstance(context);
                            wm.removeOnColorsChangedListener((android.app.WallpaperManager.OnColorsChangedListener) finalWpListener);
                        } catch (Throwable ignored) {}
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private static void performHaptic(View view, int type) {
        if (view == null) return;
        try {
            view.performHapticFeedback(type, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Throwable ignored) {
            try {
                view.performHapticFeedback(type);
            } catch (Throwable t) {}
        }
    }

    public static void resetHintTutorial(Context context) {
        AdvancedSearchDialog.resetLongTapTutorial(context);
    }
}