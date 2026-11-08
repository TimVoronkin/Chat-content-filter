package ni.dekma.advanced_search;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.app.Activity;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AdvancedSearchDialog {
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static int currentToken = 0;

    private static final Interpolator EASE_OUT_QUINT = new Interpolator() {
        @Override
        public float getInterpolation(float t) {
            t -= 1.0f;
            return t * t * t * t * t + 1.0f;
        }
    };

    private static final Interpolator EASE_IN_QUINT = new Interpolator() {
        @Override
        public float getInterpolation(float t) {
            return t * t * t * t * t;
        }
    };

    private static final Interpolator M3_EMPHASIZED = Build.VERSION.SDK_INT >= 21 ? new PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f) : new DecelerateInterpolator();
    private static final Interpolator M3_EMPHASIZED_DECELERATE = Build.VERSION.SDK_INT >= 21 ? new PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f) : new DecelerateInterpolator();
    private static final Interpolator M3_EMPHASIZED_ACCELERATE = Build.VERSION.SDK_INT >= 21 ? new PathInterpolator(0.3f, 0.0f, 0.8f, 0.15f) : new AccelerateInterpolator();

    private final Context context;
    private final long peerId;
    private String query;
    private String scope;
    private long filterByUserId;
    private String filterByUserName;
    private long filterByUserAccessHash;
    private Object selectedUserObject;

    private String selectedFilter = "all";
    private String selectedDatePreset = "all";
    private long customMinDate = 0;
    private long customMaxDate = 0;

    private final Map<String, TextView> scopeViews = new HashMap<>();
    private final Map<String, TextView> filterChipViews = new HashMap<>();
    private final Map<String, TextView> dateChipViews = new HashMap<>();
    private String selectedVideoSort = "date";
    private final Map<String, TextView> videoSortChipViews = new HashMap<>();
    private HorizontalScrollView videoSortScroll;
    private LinearLayout videoSortContainer;
    private final List<Object> accumulatedRawMessages = new ArrayList<>();

    private String selectedDocExt = "all";
    private String selectedDocSort = "date";
    private final Map<String, TextView> docExtChipViews = new HashMap<>();
    private final Map<String, TextView> docSortChipViews = new HashMap<>();
    private HorizontalScrollView docSubScroll;
    private LinearLayout docSubContainer;

    private boolean onlyMineSelected = false;
    private TextView onlyMineChipView;

    private static AdvancedSearchDialog currentInstance = null;
    private static Object playingMessageObj = null;
    private static ImageView playingIconView = null;
    private static int activeVoiceMsgId = 0;
    private static int activeVoiceDuration = 0;
    private static TextView activeVoiceDurTv = null;
    private static final Map<Integer, View> activeWaveformViews = new HashMap<>();
    private static float activeVoiceTargetProgress = 0f;
    private static float activeVoiceRenderProgress = 0f;
    private static long activeVoiceLastQueryTime = 0;
    private static final Runnable voiceProgressTicker = new Runnable() {
        @Override
        public void run() {
            if (playingMessageObj == null || activeVoiceMsgId == 0) return;
            try {
                long now = System.currentTimeMillis();
                Class<?> moClass = Class.forName("org.telegram.messenger.MessageObject");
                Class<?> mcClass = Class.forName("org.telegram.messenger.MediaController");
                Method getInstance = mcClass.getMethod("getInstance");
                Object mcInst = getInstance.invoke(null);
                Method isPlaying = mcClass.getMethod("isPlayingMessage", moClass);
                boolean isCurPlaying = (boolean) isPlaying.invoke(mcInst, playingMessageObj);
                Method isPaused = mcClass.getMethod("isMessagePaused");
                boolean isCurPaused = (boolean) isPaused.invoke(mcInst);
                if (isCurPlaying && !isCurPaused) {
                    if (now - activeVoiceLastQueryTime >= 40) {
                        activeVoiceLastQueryTime = now;
                        try {
                            Field pField = moClass.getField("audioProgress");
                            activeVoiceTargetProgress = pField.getFloat(playingMessageObj);
                        } catch (Throwable t) {
                            try {
                                Method getMo = mcClass.getMethod("getPlayingMessageObject");
                                Object curMo = getMo.invoke(mcInst);
                                if (curMo != null) {
                                    Field pField = curMo.getClass().getField("audioProgress");
                                    activeVoiceTargetProgress = pField.getFloat(curMo);
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                    if (activeVoiceRenderProgress < activeVoiceTargetProgress) {
                        float diff = activeVoiceTargetProgress - activeVoiceRenderProgress;
                        if (diff > 0.15f) {
                            activeVoiceRenderProgress = activeVoiceTargetProgress;
                        } else {
                            activeVoiceRenderProgress += Math.max(0.001f, diff * 0.35f);
                            if (activeVoiceRenderProgress > activeVoiceTargetProgress) {
                                activeVoiceRenderProgress = activeVoiceTargetProgress;
                            }
                        }
                    } else if (activeVoiceRenderProgress - activeVoiceTargetProgress > 0.05f) {
                        activeVoiceRenderProgress = activeVoiceTargetProgress;
                    }
                    View wv = activeWaveformViews.get(activeVoiceMsgId);
                    if (wv != null) {
                        wv.setTag(activeVoiceRenderProgress);
                        wv.invalidate();
                    }
                    if (activeVoiceDurTv != null && activeVoiceDuration > 0) {
                        int sec = (int) (activeVoiceRenderProgress * activeVoiceDuration);
                        activeVoiceDurTv.setText(formatVoiceDuration(sec));
                    }
                    mainHandler.postDelayed(this, 16);
                } else if (!isCurPlaying) {
                    if (playingIconView != null) {
                        int accentColor = currentInstance != null ? currentInstance.getAccentColor() : 0xFF2B87D1;
                        int s = currentInstance != null ? currentInstance.dp(14) : 28;
                        playingIconView.setImageDrawable(new VectorIconDrawable("play", accentColor, s));
                    }
                    View wv = activeWaveformViews.get(activeVoiceMsgId);
                    if (wv != null) {
                        wv.setTag(0f);
                        wv.invalidate();
                    }
                    if (activeVoiceDurTv != null && activeVoiceDuration > 0) {
                        activeVoiceDurTv.setText(formatVoiceDuration(activeVoiceDuration));
                    }
                    playingMessageObj = null;
                    playingIconView = null;
                    activeVoiceMsgId = 0;
                    activeVoiceDuration = 0;
                    activeVoiceDurTv = null;
                    activeVoiceTargetProgress = 0f;
                    activeVoiceRenderProgress = 0f;
                    activeVoiceLastQueryTime = 0;
                }
            } catch (Throwable ignored) {}
        }
    };

    private LinearLayout userFilterChipContainer;
    private LinearLayout docSearchWarningBanner;
    private LinearLayout resetRow;
    private LinearLayout resultsContainer;
    private ProgressBar searchProgressBar;
    private TextView emptyTextView;
    private LinearLayout emptyStateContainer;
    private TextView emptyTitleView;
    private TextView emptySubtitleView;
    private TextView emptyResetBtn;
    private ValueAnimator skeletonShimmerAnim;
    private TextView statsBadge;
    private Dialog currentDialog;
    private LinearLayout rootContainer;
    private FrameLayout rootFrame;
    private FrameLayout m3SearchBar;
    private View fastScrollThumb;
    private ScrollView contentScroll;
    private LinearLayout userSuggestionsContainer;

    private Runnable searchRunnable;
    private Runnable hideThumbRunnable;
    private EditText searchInputField;
    private int lastRenderAccount;
    private Object lastRenderResponse;

    private boolean isLoadingMore = false;
    private boolean hasMoreResults = true;
    private int lastOffsetId = 0;
    private Object lastOffsetPeer = null;
    private int lastOffsetRate = 0;
    private int totalLoadedCount = 0;
    private final Set<String> loadedMessageKeys = new HashSet<>();
    private ProgressBar loadMoreProgressBar;
    private int currentUserSearchToken = 0;

    private static Method localeIsoMethod;
    private static boolean localeIsoMethodResolved = false;

    static boolean isEnglish() {
        if (!localeIsoMethodResolved) {
            localeIsoMethodResolved = true;
            try {
                Class<?> lcClass = Class.forName("org.telegram.messenger.LocaleController");
                localeIsoMethod = lcClass.getMethod("getLocaleStringIso639");
            } catch (Throwable ignored) {}
        }
        if (localeIsoMethod != null) {
            try {
                String iso = (String) localeIsoMethod.invoke(null);
                if (iso != null && !iso.isEmpty()) {
                    String lower = iso.toLowerCase(Locale.ROOT);
                    if (lower.startsWith("ru") || lower.startsWith("uk") || lower.startsWith("be")) {
                        return false;
                    }
                    return true;
                }
            } catch (Throwable ignored) {}
        }
        try {
            Locale def = Locale.getDefault();
            if (def != null && def.getLanguage() != null) {
                String lower = def.getLanguage().toLowerCase(Locale.ROOT);
                if (lower.startsWith("ru") || lower.startsWith("uk") || lower.startsWith("be")) {
                    return false;
                }
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    static String trStatic(String ru, String en) {
        return isEnglish() ? en : ru;
    }

    private final boolean isEnglish;

    private String tr(String ru, String en) {
        return isEnglish ? en : ru;
    }

    private AdvancedSearchDialog(Context context, long peerId, String initialQuery) {
        this(context, peerId, initialQuery, 0, null);
    }

    private AdvancedSearchDialog(Context context, long peerId, String initialQuery, long initialUserId, String initialUserName) {
        this.context = context;
        this.isEnglish = isEnglish();
        this.peerId = peerId;
        this.query = initialQuery != null ? initialQuery : "";
        this.scope = (peerId != 0) ? "chat" : "global";
        this.filterByUserId = initialUserId;
        this.filterByUserName = initialUserName;
    }

    private static Context getActivityContext(Context ctx) {
        if (ctx == null) {
            try {
                Class<?> laClass = Class.forName("org.telegram.ui.LaunchActivity");
                Object la = laClass.getField("instance").get(null);
                if (la instanceof android.app.Activity) return (android.app.Activity) la;
            } catch (Throwable ignored) {}
            return null;
        }
        Context cur = ctx;
        while (cur instanceof android.content.ContextWrapper) {
            if (cur instanceof android.app.Activity) {
                return cur;
            }
            cur = ((android.content.ContextWrapper) cur).getBaseContext();
        }
        if (cur instanceof android.app.Activity) {
            return cur;
        }
        try {
            Class<?> laClass = Class.forName("org.telegram.ui.LaunchActivity");
            Object la = laClass.getField("instance").get(null);
            if (la instanceof android.app.Activity) return (android.app.Activity) la;
        } catch (Throwable ignored) {}
        return ctx;
    }

    public static void show(final Context context, final long peerId, final String query) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Context actContext = getActivityContext(context);
                    if (actContext == null) actContext = context;
                    if (currentInstance != null && currentInstance.currentDialog != null) {
                        currentInstance.restoreFromMinimize();
                        return;
                    }
                    new AdvancedSearchDialog(actContext, peerId, query).open();
                } catch (Throwable ignored) {}
            }
        });
    }

    public static void showWithUser(final Context context, final long peerId, final String query, final long userId, final String userName) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Context actContext = getActivityContext(context);
                    if (actContext == null) actContext = context;
                    if (currentInstance != null && currentInstance.currentDialog != null) {
                        currentInstance.restoreFromMinimize();
                        return;
                    }
                    new AdvancedSearchDialog(actContext, peerId, query, userId, userName).open();
                } catch (Throwable ignored) {}
            }
        });
    }

    public void minimize() {
        if (rootFrame != null) {
            rootFrame.animate()
                    .translationY(rootFrame.getHeight() > 0 ? rootFrame.getHeight() : dp(800))
                    .alpha(0f)
                    .setDuration(240)
                    .setInterpolator(M3_EMPHASIZED_ACCELERATE)
                    .withLayer()
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (currentDialog != null) currentDialog.hide();
                            } catch (Throwable ignored) {}
                        }
                    }).start();
        } else if (currentDialog != null) {
            try {
                currentDialog.hide();
            } catch (Throwable ignored) {}
        }
    }

    public void restoreFromMinimize() {
        if (currentDialog != null) {
            try {
                currentDialog.show();
            } catch (Throwable ignored) {}
            if (rootFrame != null) {
                rootFrame.setAlpha(0f);
                rootFrame.post(new Runnable() {
                    @Override
                    public void run() {
                        int h = rootFrame.getHeight();
                        if (h <= 0) h = dp(800);
                        rootFrame.setTranslationY(h);
                        rootFrame.animate()
                                .translationY(0f)
                                .alpha(1f)
                                .setDuration(280)
                                .setInterpolator(M3_EMPHASIZED_DECELERATE)
                                .withLayer()
                                .start();
                    }
                });
            }
        }
    }

    public void open() {
        if (context instanceof android.app.Activity) {
            android.app.Activity act = (android.app.Activity) context;
            if (act.isFinishing()) return;
            if (Build.VERSION.SDK_INT >= 17 && act.isDestroyed()) return;
        }
        currentInstance = this;
        currentDialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {
            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                Window win = getWindow();
                if (win != null) {
                    win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
                    int winBg = isDarkTheme() ? getMonetColor("system_neutral1_900", 0xFF141218) : getMonetColor("system_neutral1_50", 0xFFFDF8FD);
                    win.setBackgroundDrawable(new ColorDrawable(winBg));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        win.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                        win.setStatusBarColor(0x00000000);
                        win.setNavigationBarColor(winBg);
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        int flags = win.getDecorView().getSystemUiVisibility();
                        flags |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
                        if (!isDarkTheme()) {
                            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isDarkTheme()) {
                            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                        }
                        win.getDecorView().setSystemUiVisibility(flags);
                    }
                }
            }

            @Override
            public void onBackPressed() {
                if (rootFrame != null) {
                    rootFrame.animate()
                            .translationY(rootFrame.getHeight() > 0 ? rootFrame.getHeight() : dp(800))
                            .alpha(0f)
                            .setDuration(240)
                            .setInterpolator(M3_EMPHASIZED_ACCELERATE)
                            .withLayer()
                            .withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    dismiss();
                                }
                            }).start();
                } else {
                    super.onBackPressed();
                }
            }
        };

        buildUi();
        currentDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                currentInstance = null;
                int acc = 0;
                try {
                    Class<?> uc = Class.forName("org.telegram.messenger.UserConfig");
                    acc = uc.getField("selectedAccount").getInt(null);
                } catch (Throwable ignored) {}
                cancelActiveRequests(acc);
                stopAudioPlayback();
            }
        });
        currentDialog.show();

        if (rootFrame != null) {
            rootFrame.setAlpha(0f);
            rootFrame.post(new Runnable() {
                @Override
                public void run() {
                    int h = rootFrame.getHeight();
                    if (h <= 0) h = dp(800);
                    rootFrame.setTranslationY(h);
                    rootFrame.animate()
                            .translationY(0f)
                            .alpha(1f)
                            .setDuration(320)
                            .setInterpolator(M3_EMPHASIZED_DECELERATE)
                            .withLayer()
                            .start();
                }
            });
        }
    }

    private void buildUi() {
        int primaryText = getPrimaryTextColor();
        int secondaryText = getSecondaryTextColor();
        final int accentColor = getAccentColor();

        rootContainer = new LinearLayout(context);
        rootContainer.setOrientation(LinearLayout.VERTICAL);
        rootContainer.setPadding(0, getStatusBarHeight(context) + dp(6), 0, 0);

        registerThemeListener(context, rootContainer, new Runnable() {
            @Override
            public void run() {
                applyThemeColors();
            }
        });

        LinearLayout searchBarContainer = new LinearLayout(context);
        searchBarContainer.setOrientation(LinearLayout.HORIZONTAL);
        searchBarContainer.setPadding(dp(12), dp(4), dp(12), dp(6));
        searchBarContainer.setGravity(Gravity.CENTER_VERTICAL);

        m3SearchBar = new FrameLayout(context);
        m3SearchBar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(getSurfaceContainerHighColor());
        searchBg.setCornerRadius(dp(28));
        searchBg.setStroke(dp(1), getOutlineVariantColor());
        m3SearchBar.setBackground(searchBg);
        if (Build.VERSION.SDK_INT >= 21) {
            m3SearchBar.setElevation(dp(3));
        }

        LinearLayout barInner = new LinearLayout(context);
        barInner.setOrientation(LinearLayout.HORIZONTAL);
        barInner.setGravity(Gravity.CENTER_VERTICAL);
        barInner.setPadding(dp(12), dp(4), dp(12), dp(4));

        ImageView backBtn = new ImageView(context);
        backBtn.setPadding(dp(6), dp(6), dp(6), dp(6));
        final VectorIconDrawable backIcon = new VectorIconDrawable("close", secondaryText, dp(20));
        backBtn.setImageDrawable(backIcon);
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                if (rootFrame != null) {
                    rootFrame.animate()
                            .translationY(rootFrame.getHeight() > 0 ? rootFrame.getHeight() : dp(800))
                            .alpha(0f)
                            .setDuration(240)
                            .setInterpolator(M3_EMPHASIZED_ACCELERATE)
                            .withLayer()
                            .withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    if (currentDialog != null) currentDialog.dismiss();
                                }
                            }).start();
                } else if (currentDialog != null) {
                    currentDialog.dismiss();
                }
            }
        });
        barInner.addView(backBtn);

        final EditText editField = new EditText(context);
        searchInputField = editField;
        editField.setText(query);
        editField.setHint(tr("Поиск по сообщениям, файлам, @юзеру...", "Search messages, files, @user..."));
        editField.setHintTextColor(secondaryText);
        editField.setTextColor(primaryText);
        editField.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        editField.setBackground(null);
        editField.setSingleLine(true);
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        editField.setLayoutParams(editLp);

        final ImageView clearBtn = new ImageView(context);
        clearBtn.setImageDrawable(new VectorIconDrawable("close", secondaryText, dp(15)));
        clearBtn.setPadding(dp(8), dp(8), dp(4), dp(8));
        clearBtn.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                editField.setText("");
                editField.requestFocus();
            }
        });

        editField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s.toString();
                clearBtn.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                if (query.startsWith("@")) {
                    searchUsers(query.length() > 1 ? query.substring(1) : "");
                } else {
                    hideUserSuggestions();
                    triggerSearchDelayed();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        final ImageView bookmarkBtn = new ImageView(context);
        bookmarkBtn.setImageDrawable(new VectorIconDrawable("bookmark", secondaryText, dp(16)));
        bookmarkBtn.setPadding(dp(6), dp(8), dp(6), dp(8));
        bookmarkBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                performHaptic(v, HapticFeedbackConstants.KEYBOARD_TAP);
                v.animate().scaleX(1.3f).scaleY(1.3f).setDuration(100).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    }
                }).start();
                if (hasActiveSearchCriteria()) {
                    addSearchHistoryItem(query, scope, selectedFilter, selectedDatePreset, customMinDate, customMaxDate, filterByUserId, filterByUserName, true);
                } else {
                    renderSearchHistory();
                }
            }
        });

        final ImageView minimizeBtn = new ImageView(context);
        minimizeBtn.setImageDrawable(new VectorIconDrawable("minimize", secondaryText, dp(18)));
        minimizeBtn.setPadding(dp(6), dp(8), dp(6), dp(8));
        minimizeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                minimize();
            }
        });

        barInner.addView(editField);
        barInner.addView(bookmarkBtn);
        barInner.addView(clearBtn);
        barInner.addView(minimizeBtn);

        m3SearchBar.addView(barInner, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        searchBarContainer.addView(m3SearchBar);
        rootContainer.addView(searchBarContainer);

        HorizontalScrollView scopeScroll = new HorizontalScrollView(context);
        scopeScroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scopeScroll.setHorizontalScrollBarEnabled(false);
        scopeScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout scopeRow = new LinearLayout(context);
        scopeRow.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scopeRow.setOrientation(LinearLayout.HORIZONTAL);
        scopeRow.setPadding(dp(16), dp(0), dp(16), dp(6));

        if (peerId != 0) {
            scopeViews.put("chat", createM3Chip(scopeRow, tr("В этом чате", "This chat"), "chat", true, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                    animateChipClick(v);
                    scope = "chat";
                    updateScopeStyles();
                    triggerSearchDelayed();
                }
            }, "chat"));

            scopeViews.put("global", createM3Chip(scopeRow, tr("По всем чатам", "All chats"), "global", false, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                    animateChipClick(v);
                    scope = "global";
                    updateScopeStyles();
                    triggerSearchDelayed();
                }
            }, "global"));
        }

        onlyMineChipView = createM3Chip(scopeRow, tr("Только мои", "Only mine"), "only_mine", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                animateChipClick(v);
                onlyMineSelected = !onlyMineSelected;
                if (onlyMineSelected) {
                    int acc = 0;
                    try {
                        Class<?> uc = Class.forName("org.telegram.messenger.UserConfig");
                        acc = uc.getField("selectedAccount").getInt(null);
                    } catch (Throwable ignored) {}
                    long myId = getMyUserId(acc);
                    if (myId != 0) {
                        filterByUserId = myId;
                        filterByUserName = tr("Мои сообщения", "My messages");
                        selectedUserObject = getMyUserObject(acc);
                        updateUserFilterChip();
                    }
                } else {
                    filterByUserId = 0;
                    filterByUserName = null;
                    selectedUserObject = null;
                    updateUserFilterChip();
                }
                updateOnlyMineChipStyle();
                triggerSearchDelayed();
            }
        }, "user");

        scopeScroll.addView(scopeRow);
        rootContainer.addView(scopeScroll);

        HorizontalScrollView filterScroll = new HorizontalScrollView(context);
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filterRow = new LinearLayout(context);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setPadding(dp(16), dp(0), dp(16), dp(6));

        String[][] filters = {
            {"all", tr("Все", "All"), null},
            {"photo", tr("Фото", "Photos"), "photo"},
            {"video", tr("Видео", "Videos"), "video"},
            {"document", tr("Файлы", "Files"), "document"},
            {"music", tr("Музыка", "Music"), "music"},
            {"voice", tr("Голосовые", "Voice"), "voice"},
            {"url", tr("Ссылки", "Links"), "url"}
        };

        for (final String[] f : filters) {
            filterChipViews.put(f[0], createM3Chip(filterRow, f[1], f[0], "all".equals(f[0]), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                    animateChipClick(v);
                    selectedFilter = f[0];
                    updateFilterStyles();
                    triggerSearchDelayed();
                }
            }, f[2]));
        }

        filterScroll.addView(filterRow);
        rootContainer.addView(filterScroll);

        videoSortScroll = new HorizontalScrollView(context);
        videoSortScroll.setHorizontalScrollBarEnabled(false);
        videoSortScroll.setVisibility(View.GONE);
        videoSortContainer = new LinearLayout(context);
        videoSortContainer.setOrientation(LinearLayout.HORIZONTAL);
        videoSortContainer.setPadding(dp(16), dp(0), dp(16), dp(6));

        String[][] videoSortOptions = {
            {"date", tr("По дате", "By date"), "calendar"},
            {"dur_asc", tr("Сначала короткие", "Shortest first"), "time"},
            {"dur_desc", tr("Сначала длинные", "Longest first"), "time"}
        };

        for (final String[] opt : videoSortOptions) {
            videoSortChipViews.put(opt[0], createM3Chip(videoSortContainer, opt[1], opt[0], "date".equals(opt[0]), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                    animateChipClick(v);
                    selectedVideoSort = opt[0];
                    updateVideoSortStyles();
                    if (lastRenderResponse != null) {
                        renderResults(lastRenderAccount, lastRenderResponse, null, false);
                    } else {
                        triggerSearchDelayed();
                    }
                }
            }, opt[2]));
        }

        videoSortScroll.addView(videoSortContainer);
        rootContainer.addView(videoSortScroll);

        docSubScroll = new HorizontalScrollView(context);
        docSubScroll.setHorizontalScrollBarEnabled(false);
        docSubScroll.setVisibility(View.GONE);
        docSubContainer = new LinearLayout(context);
        docSubContainer.setOrientation(LinearLayout.HORIZONTAL);
        docSubContainer.setPadding(dp(16), dp(0), dp(16), dp(6));

        String[][] docExtOptions = {
            {"all", tr("Все", "All"), null},
            {"pdf", "PDF", "document"},
            {"apk", "APK", "document"},
            {"archive", tr("Архивы", "Archives"), "document"},
            {"doc", tr("Документы", "Documents"), "document"},
            {"sheet", tr("Таблицы", "Sheets"), "document"}
        };

        for (final String[] opt : docExtOptions) {
            docExtChipViews.put(opt[0], createM3Chip(docSubContainer, opt[1], opt[0], "all".equals(opt[0]), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                    animateChipClick(v);
                    selectedDocExt = opt[0];
                    updateDocSubStyles();
                    triggerSearchDelayed();
                }
            }, opt[2]));
        }

        View sep = new View(context);
        sep.setBackgroundColor(getOutlineVariantColor());
        LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(dp(1), dp(20));
        sepLp.gravity = Gravity.CENTER_VERTICAL;
        sepLp.leftMargin = dp(6);
        sepLp.rightMargin = dp(6);
        docSubContainer.addView(sep, sepLp);

        String[][] docSortOptions = {
            {"date", tr("По дате", "By date"), "calendar"},
            {"size_desc", tr("Сначала большие", "Largest first"), "download"},
            {"size_asc", tr("Сначала маленькие", "Smallest first"), "download"}
        };

        for (final String[] opt : docSortOptions) {
            docSortChipViews.put(opt[0], createM3Chip(docSubContainer, opt[1], opt[0], "date".equals(opt[0]), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                    animateChipClick(v);
                    selectedDocSort = opt[0];
                    updateDocSubStyles();
                    if (lastRenderResponse != null) {
                        renderResults(lastRenderAccount, lastRenderResponse, null, false);
                    } else {
                        triggerSearchDelayed();
                    }
                }
            }, opt[2]));
        }

        docSubScroll.addView(docSubContainer);
        rootContainer.addView(docSubScroll);

        HorizontalScrollView dateScroll = new HorizontalScrollView(context);
        dateScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout dateRow = new LinearLayout(context);
        dateRow.setOrientation(LinearLayout.HORIZONTAL);
        dateRow.setPadding(dp(16), dp(0), dp(16), dp(8));

        String[][] datePresets = {
            {"all", tr("Все время", "All time"), null},
            {"today", tr("Сегодня", "Today"), "time"},
            {"yesterday", tr("Вчера", "Yesterday"), "time"},
            {"week", tr("7 дней", "7 days"), "time"},
            {"month", tr("30 дней", "30 days"), "time"},
            {"custom", tr("Период", "Custom range"), "calendar"}
        };

        for (final String[] d : datePresets) {
            dateChipViews.put(d[0], createM3Chip(dateRow, d[1], d[0], "all".equals(d[0]), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                    animateChipClick(v);
                    if ("custom".equals(d[0])) {
                        showDateRangePicker();
                    } else {
                        selectedDatePreset = d[0];
                        customMinDate = 0;
                        customMaxDate = 0;
                        updateDateStyles();
                        triggerSearchDelayed();
                    }
                }
            }, d[2]));
        }

        dateScroll.addView(dateRow);
        rootContainer.addView(dateScroll);

        userFilterChipContainer = new LinearLayout(context);
        userFilterChipContainer.setOrientation(LinearLayout.HORIZONTAL);
        userFilterChipContainer.setPadding(dp(16), dp(0), dp(16), dp(6));
        userFilterChipContainer.setVisibility(View.GONE);
        rootContainer.addView(userFilterChipContainer);

        docSearchWarningBanner = new LinearLayout(context);
        docSearchWarningBanner.setOrientation(LinearLayout.HORIZONTAL);
        docSearchWarningBanner.setGravity(Gravity.CENTER_VERTICAL);
        docSearchWarningBanner.setVisibility(View.GONE);
        docSearchWarningBanner.setPadding(dp(12), dp(7), dp(12), dp(7));

        int warnColor = isDarkTheme() ? getMonetColor("system_accent2_300", 0xFFFFA726) : getMonetColor("system_accent2_600", 0xFFE65100);
        GradientDrawable warnBg = new GradientDrawable();
        warnBg.setCornerRadius(dp(10));
        warnBg.setColor((warnColor & 0x00FFFFFF) | 0x18000000);
        warnBg.setStroke(dp(1), (warnColor & 0x00FFFFFF) | 0x38000000);
        docSearchWarningBanner.setBackground(warnBg);

        LinearLayout.LayoutParams warnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        warnLp.setMargins(dp(16), 0, dp(16), dp(6));
        docSearchWarningBanner.setLayoutParams(warnLp);

        ImageView warnIcon = new ImageView(context);
        warnIcon.setImageDrawable(new VectorIconDrawable("time", warnColor, dp(15)));
        warnIcon.setPadding(0, 0, dp(8), 0);
        docSearchWarningBanner.addView(warnIcon);

        TextView warnTv = new TextView(context);
        warnTv.setText(tr("Поиск по содержимому файлов может занять некоторое время", "Searching file contents may take some time"));
        warnTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        warnTv.setTextColor(warnColor);
        warnTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        docSearchWarningBanner.addView(warnTv);

        rootContainer.addView(docSearchWarningBanner);

        resetRow = new LinearLayout(context);
        resetRow.setOrientation(LinearLayout.HORIZONTAL);
        resetRow.setPadding(dp(16), dp(0), dp(16), dp(6));
        resetRow.setGravity(Gravity.END);
        resetRow.setVisibility(View.GONE);

        final TextView resetBtn = new TextView(context);
        resetBtn.setText(tr("Сбросить фильтры", "Reset filters"));
        resetBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        int errorColor = isDarkTheme() ? getMonetColor("system_accent3_300", 0xFFE17076) : getMonetColor("system_accent3_600", 0xFFB3261E);
        resetBtn.setTextColor(errorColor);
        resetBtn.setCompoundDrawablesWithIntrinsicBounds(new VectorIconDrawable("close", errorColor, dp(12)), null, null, null);
        resetBtn.setCompoundDrawablePadding(dp(5));
        resetBtn.setPadding(dp(10), dp(5), dp(10), dp(5));
        GradientDrawable resetBg = new GradientDrawable();
        resetBg.setCornerRadius(dp(8));
        resetBg.setColor((errorColor & 0x00FFFFFF) | 0x22000000);
        resetBtn.setBackground(resetBg);
        resetBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performHaptic(v, HapticFeedbackConstants.CONTEXT_CLICK);
                resetBtn.setScaleX(0.9f);
                resetBtn.setScaleY(0.9f);
                resetBtn.animate().scaleX(1f).scaleY(1f).setDuration(180).setInterpolator(EASE_OUT_QUINT).withLayer().start();
                resetAllFilters();
            }
        });
        resetRow.addView(resetBtn);
        rootContainer.addView(resetRow);

        statsBadge = new TextView(context);
        statsBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        statsBadge.setTextColor(accentColor);
        try {
            statsBadge.setTypeface(Typeface.DEFAULT_BOLD);
        } catch (Throwable ignored) {}
        statsBadge.setGravity(Gravity.CENTER);
        statsBadge.setPadding(dp(12), dp(4), dp(12), dp(4));
        statsBadge.setVisibility(View.GONE);

        GradientDrawable statsBg = new GradientDrawable();
        statsBg.setShape(GradientDrawable.RECTANGLE);
        statsBg.setCornerRadius(dp(12));
        statsBg.setColor((accentColor & 0x00FFFFFF) | 0x22000000);
        statsBg.setStroke(dp(1), (accentColor & 0x00FFFFFF) | 0x48000000);
        statsBadge.setBackground(statsBg);

        LinearLayout.LayoutParams statsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statsLp.leftMargin = dp(16);
        statsLp.bottomMargin = dp(6);
        statsBadge.setLayoutParams(statsLp);
        rootContainer.addView(statsBadge);

        searchProgressBar = new ProgressBar(context);
        searchProgressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(dp(32), dp(32));
        pbLp.gravity = Gravity.CENTER_HORIZONTAL;
        pbLp.topMargin = dp(24);
        searchProgressBar.setLayoutParams(pbLp);
        rootContainer.addView(searchProgressBar);

        emptyTextView = new TextView(context);
        emptyTextView.setText(tr("Ничего не найдено", "No results found"));
        emptyTextView.setTextColor(secondaryText);
        emptyTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        emptyTextView.setGravity(Gravity.CENTER);
        emptyTextView.setPadding(dp(20), dp(40), dp(20), dp(40));

        FrameLayout scrollContainer = new FrameLayout(context);
        contentScroll = new ScrollView(context);
        contentScroll.setVerticalScrollBarEnabled(false);

        resultsContainer = new LinearLayout(context);
        resultsContainer.setOrientation(LinearLayout.VERTICAL);
        resultsContainer.setPadding(dp(14), dp(4), dp(14), dp(14));
        contentScroll.addView(resultsContainer);
        scrollContainer.addView(contentScroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final FrameLayout thumbContainer = new FrameLayout(context);
        fastScrollThumb = thumbContainer;
        thumbContainer.setVisibility(View.GONE);

        final View thumbBar = new View(context);
        GradientDrawable thumbBg = new GradientDrawable();
        thumbBg.setCornerRadius(dp(4));
        thumbBg.setColor(accentColor);
        thumbBar.setBackground(thumbBg);

        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(dp(5), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
        barLp.rightMargin = dp(3);
        thumbBar.setLayoutParams(barLp);
        thumbContainer.addView(thumbBar);

        FrameLayout.LayoutParams thumbLp = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.END | Gravity.TOP);
        thumbContainer.setLayoutParams(thumbLp);
        scrollContainer.addView(thumbContainer);

        if (Build.VERSION.SDK_INT >= 23) {
            contentScroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
                @Override
                public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                    updateThumbPosition();
                    showThumb();
                    checkLoadMore(scrollY);
                }
            });
        }

        contentScroll.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                    updateThumbPosition();
                    showThumb();
                }
                return false;
            }
        });

        contentScroll.getViewTreeObserver().addOnScrollChangedListener(new android.view.ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                updateThumbPosition();
                showThumb();
                checkLoadMore(contentScroll.getScrollY());
            }
        });

        final boolean[] isDraggingThumb = new boolean[1];
        final float[] dragStartY = new float[1];
        final int[] dragStartScrollY = new int[1];

        thumbContainer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                View child = contentScroll.getChildAt(0);
                if (child == null) return false;
                int visibleHeight = contentScroll.getHeight();
                int contentHeight = child.getHeight();
                int scrollRange = contentHeight - visibleHeight;
                if (scrollRange <= 0) return false;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isDraggingThumb[0] = true;
                        dragStartY[0] = event.getRawY();
                        dragStartScrollY[0] = contentScroll.getScrollY();
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        thumbBar.animate().scaleX(1.5f).setDuration(120).setInterpolator(M3_EMPHASIZED).start();
                        performHaptic(v, HapticFeedbackConstants.CLOCK_TICK);
                        showThumb();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (isDraggingThumb[0]) {
                            float deltaY = event.getRawY() - dragStartY[0];
                            int thumbH = thumbContainer.getHeight();
                            float maxTop = visibleHeight - thumbH;
                            if (maxTop > 0) {
                                float deltaScroll = (deltaY / maxTop) * scrollRange;
                                int targetScroll = Math.max(0, Math.min(scrollRange, (int) (dragStartScrollY[0] + deltaScroll)));
                                contentScroll.scrollTo(0, targetScroll);
                            }
                            showThumb();
                            return true;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        isDraggingThumb[0] = false;
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                        thumbBar.animate().scaleX(1.0f).setDuration(180).setInterpolator(M3_EMPHASIZED).start();
                        scheduleHideThumb();
                        return true;
                }
                return false;
            }
        });

        scrollContainer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (fastScrollThumb == null || fastScrollThumb.getVisibility() != View.VISIBLE) return false;
                View child = contentScroll.getChildAt(0);
                if (child == null) return false;
                int visibleHeight = contentScroll.getHeight();
                int contentHeight = child.getHeight();
                int scrollRange = contentHeight - visibleHeight;
                if (scrollRange <= 0) return false;

                int width = scrollContainer.getWidth();
                if (event.getX() >= width - dp(32)) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                        isDraggingThumb[0] = true;
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        float touchY = event.getY();
                        int thumbH = thumbContainer.getHeight();
                        float maxTop = visibleHeight - thumbH;
                        if (maxTop > 0) {
                            float ratio = Math.max(0f, Math.min(1f, (touchY - thumbH / 2f) / maxTop));
                            contentScroll.scrollTo(0, (int) (ratio * scrollRange));
                        }
                        thumbBar.animate().scaleX(1.6f).setDuration(100).start();
                        showThumb();
                        return true;
                    } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                        isDraggingThumb[0] = false;
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                        thumbBar.animate().scaleX(1.0f).setDuration(150).start();
                        scheduleHideThumb();
                        return true;
                    }
                }
                return false;
            }
        });

        rootContainer.addView(scrollContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        rootFrame = new FrameLayout(context);
        rootFrame.addView(rootContainer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        userSuggestionsContainer = new LinearLayout(context);
        userSuggestionsContainer.setOrientation(LinearLayout.VERTICAL);
        int sugBgColor = isDarkTheme() ? getMonetColor("system_neutral1_800", 0xFF2B2930) : getMonetColor("system_neutral1_100", 0xFFECE6F0);
        GradientDrawable sugBg = new GradientDrawable();
        sugBg.setColor(sugBgColor);
        sugBg.setCornerRadius(dp(16));
        sugBg.setStroke(dp(1), isDarkTheme() ? 0x22FFFFFF : 0x14000000);
        userSuggestionsContainer.setBackground(sugBg);
        userSuggestionsContainer.setPadding(dp(4), dp(4), dp(4), dp(4));
        userSuggestionsContainer.setVisibility(View.GONE);

        FrameLayout.LayoutParams sugLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sugLp.topMargin = getStatusBarHeight(context) + dp(80);
        sugLp.leftMargin = dp(16);
        sugLp.rightMargin = dp(16);
        userSuggestionsContainer.setLayoutParams(sugLp);
        rootFrame.addView(userSuggestionsContainer);

        if (filterByUserId != 0) {
            updateUserFilterChip();
        }

        currentDialog.setContentView(rootFrame);

        triggerSearchDelayed();

        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    editField.requestFocus();
                    InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(editField, InputMethodManager.SHOW_IMPLICIT);
                    }
                } catch (Throwable ignored) {}
            }
        }, 150);
    }

    private void searchUsers(final String queryTag) {
        final int token = ++currentUserSearchToken;
        final String qClean = queryTag != null ? queryTag.trim() : "";

        final int currentAccount;
        try {
            Class<?> uc = Class.forName("org.telegram.messenger.UserConfig");
            currentAccount = uc.getField("selectedAccount").getInt(null);
        } catch (Throwable t) {
            hideUserSuggestions();
            return;
        }

        final List<Object> localUsers = new ArrayList<>();
        final Set<Long> addedIds = new HashSet<>();

        try {
            Class<?> mcClass = Class.forName("org.telegram.messenger.MessagesController");
            Method getInstance = mcClass.getMethod("getInstance", int.class);
            Object mcInst = getInstance.invoke(null, currentAccount);

            if (!qClean.isEmpty()) {
                try {
                    Method getUser = mcClass.getMethod("getUser", String.class);
                    Object singleUser = getUser.invoke(mcInst, qClean);
                    if (singleUser != null) {
                        long uid = singleUser.getClass().getField("id").getLong(singleUser);
                        if (uid != 0) {
                            localUsers.add(singleUser);
                            addedIds.add(uid);
                        }
                    }
                } catch (Throwable ignored) {}
            }

            try {
                Field usersField = null;
                for (Field f : mcClass.getDeclaredFields()) {
                    if ("users".equals(f.getName())) {
                        usersField = f;
                        break;
                    }
                }
                if (usersField != null) {
                    usersField.setAccessible(true);
                    Object usersSparse = usersField.get(mcInst);
                    if (usersSparse != null) {
                        Method sizeMethod = usersSparse.getClass().getMethod("size");
                        Method valueAtMethod = usersSparse.getClass().getMethod("valueAt", int.class);
                        int sz = (int) sizeMethod.invoke(usersSparse);
                        String qLower = qClean.toLowerCase(Locale.ROOT);
                        for (int i = 0; i < sz; i++) {
                            Object u = valueAtMethod.invoke(usersSparse, i);
                            if (u != null) {
                                long uid = u.getClass().getField("id").getLong(u);
                                if (uid != 0 && !addedIds.contains(uid)) {
                                    String un = (String) u.getClass().getField("username").get(u);
                                    String fn = (String) u.getClass().getField("first_name").get(u);
                                    String ln = (String) u.getClass().getField("last_name").get(u);
                                    boolean match = qLower.isEmpty();
                                    if (!match && un != null && un.toLowerCase(Locale.ROOT).contains(qLower)) match = true;
                                    if (!match && fn != null && fn.toLowerCase(Locale.ROOT).contains(qLower)) match = true;
                                    if (!match && ln != null && ln.toLowerCase(Locale.ROOT).contains(qLower)) match = true;
                                    if (match) {
                                        localUsers.add(u);
                                        addedIds.add(uid);
                                        if (localUsers.size() >= 8) break;
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}

            try {
                Class<?> ccClass = Class.forName("org.telegram.messenger.ContactsController");
                Method getCC = ccClass.getMethod("getInstance", int.class);
                Object ccInst = getCC.invoke(null, currentAccount);
                Field contactsDictF = ccClass.getDeclaredField("contactsDict");
                contactsDictF.setAccessible(true);
                Object contactsDict = contactsDictF.get(ccInst);
                if (contactsDict != null) {
                    Method sizeMethod = contactsDict.getClass().getMethod("size");
                    Method valueAtMethod = contactsDict.getClass().getMethod("valueAt", int.class);
                    int sz = (int) sizeMethod.invoke(contactsDict);
                    String qLower = qClean.toLowerCase(Locale.ROOT);
                    for (int i = 0; i < sz; i++) {
                        Object u = valueAtMethod.invoke(contactsDict, i);
                        if (u != null) {
                            long uid = u.getClass().getField("id").getLong(u);
                            if (uid != 0 && !addedIds.contains(uid)) {
                                String un = (String) u.getClass().getField("username").get(u);
                                String fn = (String) u.getClass().getField("first_name").get(u);
                                String ln = (String) u.getClass().getField("last_name").get(u);
                                boolean match = qLower.isEmpty();
                                if (!match && un != null && un.toLowerCase(Locale.ROOT).contains(qLower)) match = true;
                                if (!match && fn != null && fn.toLowerCase(Locale.ROOT).contains(qLower)) match = true;
                                if (!match && ln != null && ln.toLowerCase(Locale.ROOT).contains(qLower)) match = true;
                                if (match) {
                                    localUsers.add(u);
                                    addedIds.add(uid);
                                    if (localUsers.size() >= 8) break;
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}

        if (!localUsers.isEmpty()) {
            renderUserSuggestions(currentAccount, localUsers);
        }

        if (!qClean.isEmpty()) {
            try {
                Class<?> reqClass = Class.forName("org.telegram.tgnet.TLRPC$TL_contacts_search");
                Object req = reqClass.newInstance();
                reqClass.getField("q").set(req, qClean);
                reqClass.getField("limit").set(req, 10);

                Class<?> cmClass = Class.forName("org.telegram.tgnet.ConnectionsManager");
                Method getInstance = cmClass.getMethod("getInstance", int.class);
                Object cmInst = getInstance.invoke(null, currentAccount);

                Class<?> reqDelClass = Class.forName("org.telegram.tgnet.RequestDelegate");
                Object delegate = Proxy.newProxyInstance(
                        reqDelClass.getClassLoader(),
                        new Class<?>[]{reqDelClass},
                        new InvocationHandler() {
                            @Override
                            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                                if ("run".equals(method.getName()) && args != null && args.length >= 2) {
                                    final Object response = args[0];
                                    if (response != null) {
                                        mainHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (token == currentUserSearchToken) {
                                                    try {
                                                        Field usersF = response.getClass().getField("users");
                                                        List<?> remoteUsers = (List<?>) usersF.get(response);
                                                        if (remoteUsers != null && !remoteUsers.isEmpty()) {
                                                            List<Object> combined = new ArrayList<>(localUsers);
                                                            for (Object ru : remoteUsers) {
                                                                if (ru != null) {
                                                                    long rUid = ru.getClass().getField("id").getLong(ru);
                                                                    if (rUid != 0 && !addedIds.contains(rUid)) {
                                                                        combined.add(ru);
                                                                        addedIds.add(rUid);
                                                                    }
                                                                }
                                                            }
                                                            renderUserSuggestions(currentAccount, combined);
                                                        } else if (localUsers.isEmpty()) {
                                                            hideUserSuggestions();
                                                        }
                                                    } catch (Throwable ignored) {}
                                                }
                                            }
                                        });
                                    } else if (localUsers.isEmpty()) {
                                        mainHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (token == currentUserSearchToken) {
                                                    hideUserSuggestions();
                                                }
                                            }
                                        });
                                    }
                                }
                                return null;
                            }
                        }
                );

                Method sendReq = cmClass.getMethod("sendRequest", Class.forName("org.telegram.tgnet.TLObject"), reqDelClass);
                sendReq.invoke(cmInst, req, delegate);
            } catch (Throwable ignored) {
                if (localUsers.isEmpty()) {
                    hideUserSuggestions();
                }
            }
        } else if (localUsers.isEmpty()) {
            hideUserSuggestions();
        }
    }

    private void renderUserSuggestions(final int currentAccount, final List<Object> foundUsers) {
        if (foundUsers == null || foundUsers.isEmpty() || userSuggestionsContainer == null) {
            hideUserSuggestions();
            return;
        }

        userSuggestionsContainer.removeAllViews();
        int count = 0;
        for (final Object item : foundUsers) {
            if (item == null) continue;
            long uid = 0;
            long aHash = 0;
            String username = "";
            String firstName = "";
            String lastName = "";
            try {
                uid = item.getClass().getField("id").getLong(item);
                aHash = item.getClass().getField("access_hash").getLong(item);
                username = (String) item.getClass().getField("username").get(item);
                firstName = (String) item.getClass().getField("first_name").get(item);
                lastName = (String) item.getClass().getField("last_name").get(item);
            } catch (Throwable ignored) {}

            String displayName = ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
            if (displayName.isEmpty()) displayName = (username != null && !username.isEmpty()) ? ("@" + username) : ("ID: " + uid);

            final long targetId = uid;
            final long finalAHash = aHash;
            final String finalDisplayName = displayName;
            final String finalUsername = username;

            if (userSuggestionsContainer.getChildCount() > 0) {
                View div = new View(context);
                div.setBackgroundColor(isDarkTheme() ? 0x14FFFFFF : 0x0A000000);
                LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
                divLp.setMargins(dp(58), 0, dp(12), 0);
                userSuggestionsContainer.addView(div, divLp);
            }

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(8), dp(12), dp(8));

            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setCornerRadius(dp(12));
            rowBg.setColor(0x00000000);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                int rippleColor = (getAccentColor() & 0x00FFFFFF) | 0x20000000;
                GradientDrawable mask = new GradientDrawable();
                mask.setCornerRadius(dp(12));
                mask.setColor(0xFFFFFFFF);
                row.setBackground(new RippleDrawable(ColorStateList.valueOf(rippleColor), rowBg, mask));
            } else {
                row.setBackground(rowBg);
            }

            View avatar = createAvatarView(currentAccount, item, targetId, finalDisplayName);
            row.addView(avatar);

            LinearLayout textCol = new LinearLayout(context);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

            TextView nameTv = new TextView(context);
            nameTv.setText(finalDisplayName);
            nameTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            nameTv.setTextColor(getPrimaryTextColor());
            nameTv.setTypeface(null, Typeface.BOLD);
            textCol.addView(nameTv);

            if (finalUsername != null && !finalUsername.isEmpty()) {
                TextView unTv = new TextView(context);
                unTv.setText("@" + finalUsername);
                unTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                unTv.setTextColor(getSecondaryTextColor());
                textCol.addView(unTv);
            }

            row.addView(textCol);

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    performHaptic(v, HapticFeedbackConstants.KEYBOARD_TAP);
                    filterByUserId = targetId;
                    filterByUserAccessHash = finalAHash;
                    selectedUserObject = item;
                    filterByUserName = (finalUsername != null && !finalUsername.isEmpty()) ? ("@" + finalUsername) : finalDisplayName;
                    if (searchInputField != null) {
                        searchInputField.setText("");
                    }
                    query = "";
                    hideUserSuggestions();
                    updateUserFilterChip();
                    triggerSearchDelayed();
                }
            });

            final int fAcc = currentAccount;
            row.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    performHaptic(v, HapticFeedbackConstants.LONG_PRESS);
                    showUserActionMenu(fAcc, targetId, item, finalDisplayName, finalUsername, finalAHash);
                    return true;
                }
            });

            userSuggestionsContainer.addView(row);
            count++;
            if (count >= 6) break;
        }

        userSuggestionsContainer.setVisibility(View.VISIBLE);
        userSuggestionsContainer.bringToFront();
        userSuggestionsContainer.setAlpha(0f);
        userSuggestionsContainer.setTranslationY(-dp(8));
        userSuggestionsContainer.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220)
                .setInterpolator(EASE_OUT_QUINT)
                .withLayer()
                .start();
    }

    private void hideUserSuggestions() {
        if (userSuggestionsContainer != null && userSuggestionsContainer.getVisibility() == View.VISIBLE) {
            userSuggestionsContainer.animate()
                .alpha(0f)
                .translationY(-dp(8))
                .setDuration(160)
                .setInterpolator(EASE_IN_QUINT)
                .withLayer()
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        userSuggestionsContainer.removeAllViews();
                        userSuggestionsContainer.setVisibility(View.GONE);
                    }
                }).start();
        }
    }

    private void showThumb() {
        if (fastScrollThumb == null) return;
        mainHandler.removeCallbacks(hideThumbRunnable);
        fastScrollThumb.setVisibility(View.VISIBLE);
        fastScrollThumb.setAlpha(1f);
        scheduleHideThumb();
    }

    private void scheduleHideThumb() {
        if (fastScrollThumb == null) return;
        mainHandler.removeCallbacks(hideThumbRunnable);
        hideThumbRunnable = new Runnable() {
            @Override
            public void run() {
                if (fastScrollThumb != null && fastScrollThumb.getVisibility() == View.VISIBLE) {
                    fastScrollThumb.animate().alpha(0f).setDuration(250).withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            if (fastScrollThumb != null && fastScrollThumb.getAlpha() == 0f) {
                                fastScrollThumb.setVisibility(View.GONE);
                            }
                        }
                    }).start();
                }
            }
        };
        mainHandler.postDelayed(hideThumbRunnable, 1200);
    }

    private void updateThumbPosition() {
        if (contentScroll == null || fastScrollThumb == null) return;
        View child = contentScroll.getChildAt(0);
        if (child == null) return;
        int visibleHeight = contentScroll.getHeight();
        int contentHeight = child.getHeight();
        int scrollY = contentScroll.getScrollY();
        int scrollRange = contentHeight - visibleHeight;
        if (scrollRange <= 0 || visibleHeight <= 0) {
            fastScrollThumb.setVisibility(View.GONE);
            return;
        }
        int thumbH = Math.max(dp(36), Math.min(visibleHeight - dp(16), (int) ((float) visibleHeight * visibleHeight / contentHeight)));
        if (fastScrollThumb.getLayoutParams().height != thumbH) {
            fastScrollThumb.getLayoutParams().height = thumbH;
            fastScrollThumb.requestLayout();
        }
        float ratio = Math.max(0f, Math.min(1f, (float) scrollY / scrollRange));
        float maxTop = visibleHeight - thumbH;
        fastScrollThumb.setTranslationY(ratio * maxTop);
    }

    private TextView createM3Chip(LinearLayout parent, String title, String key, boolean selected, View.OnClickListener listener, String iconType) {
        TextView chip = new TextView(context);
        chip.setText(title);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        chip.setSingleLine(true);
        chip.setMaxLines(1);
        chip.setMinimumHeight(dp(32));
        chip.setPadding(dp(14), dp(6), dp(14), dp(6));
        chip.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(8));
        chip.setLayoutParams(lp);

        if (iconType != null) {
            VectorIconDrawable icon = new VectorIconDrawable(iconType, selected ? getOnSecondaryContainerColor() : getSecondaryTextColor(), dp(15));
            chip.setTag(icon);
            chip.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
            chip.setCompoundDrawablePadding(dp(6));
        }

        applyM3ChipStyle(chip, selected);
        chip.setOnClickListener(listener);
        parent.addView(chip);
        return chip;
    }

    private void applyM3ChipStyle(TextView chip, boolean selected) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(8));
        int textColor;
        if (selected) {
            gd.setColor(getSecondaryContainerColor());
            textColor = getOnSecondaryContainerColor();
            chip.setTextColor(textColor);
            try {
                Class<?> auClass = Class.forName("org.telegram.messenger.AndroidUtilities");
                Method getTypeface = auClass.getMethod("getTypeface", String.class);
                Typeface tf = (Typeface) getTypeface.invoke(null, "fonts/rmedium.ttf");
                if (tf != null) chip.setTypeface(tf);
                else chip.setTypeface(null, Typeface.BOLD);
            } catch (Throwable t) {
                chip.setTypeface(null, Typeface.BOLD);
            }
        } else {
            gd.setColor(getSurfaceContainerLowColor());
            gd.setStroke(dp(1), getOutlineVariantColor());
            textColor = getSecondaryTextColor();
            chip.setTextColor(textColor);
            chip.setTypeface(null, Typeface.NORMAL);
        }
        chip.setBackground(gd);
        Object tag = chip.getTag();
        if (tag instanceof VectorIconDrawable) {
            ((VectorIconDrawable) tag).setColor(textColor);
        }
    }

    private void updateScopeStyles() {
        for (Map.Entry<String, TextView> e : scopeViews.entrySet()) {
            applyM3ChipStyle(e.getValue(), e.getKey().equals(scope));
        }
    }

    private void updateFilterStyles() {
        for (Map.Entry<String, TextView> e : filterChipViews.entrySet()) {
            applyM3ChipStyle(e.getValue(), e.getKey().equals(selectedFilter));
        }
        updateDocWarningVisibility();
        updateVideoSortVisibility();
        updateDocSubVisibility();
        updateResetButtonVisibility();
    }

    private void updateVideoSortStyles() {
        for (Map.Entry<String, TextView> e : videoSortChipViews.entrySet()) {
            applyM3ChipStyle(e.getValue(), e.getKey().equals(selectedVideoSort));
        }
        updateResetButtonVisibility();
    }

    private void updateVideoSortVisibility() {
        if (videoSortScroll != null) {
            boolean show = "video".equals(selectedFilter);
            if (show && videoSortScroll.getVisibility() != View.VISIBLE) {
                videoSortScroll.setVisibility(View.VISIBLE);
                videoSortScroll.setAlpha(0f);
                videoSortScroll.animate().alpha(1f).setDuration(200).setInterpolator(M3_EMPHASIZED_DECELERATE).withLayer().start();
            } else if (!show && videoSortScroll.getVisibility() == View.VISIBLE) {
                videoSortScroll.animate().alpha(0f).setDuration(160).setInterpolator(M3_EMPHASIZED_ACCELERATE).withLayer().withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        videoSortScroll.setVisibility(View.GONE);
                    }
                }).start();
            }
        }
    }

    private void updateDocSubStyles() {
        for (Map.Entry<String, TextView> e : docExtChipViews.entrySet()) {
            applyM3ChipStyle(e.getValue(), e.getKey().equals(selectedDocExt));
        }
        for (Map.Entry<String, TextView> e : docSortChipViews.entrySet()) {
            applyM3ChipStyle(e.getValue(), e.getKey().equals(selectedDocSort));
        }
        updateResetButtonVisibility();
    }

    private void updateDocSubVisibility() {
        if (docSubScroll != null) {
            boolean show = "document".equals(selectedFilter);
            if (show && docSubScroll.getVisibility() != View.VISIBLE) {
                docSubScroll.setVisibility(View.VISIBLE);
                docSubScroll.setAlpha(0f);
                docSubScroll.animate().alpha(1f).setDuration(200).setInterpolator(M3_EMPHASIZED_DECELERATE).withLayer().start();
            } else if (!show && docSubScroll.getVisibility() == View.VISIBLE) {
                docSubScroll.animate().alpha(0f).setDuration(160).setInterpolator(M3_EMPHASIZED_ACCELERATE).withLayer().withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        docSubScroll.setVisibility(View.GONE);
                    }
                }).start();
            }
        }
    }

    private void updateOnlyMineChipStyle() {
        if (onlyMineChipView != null) {
            applyM3ChipStyle(onlyMineChipView, onlyMineSelected);
        }
        updateResetButtonVisibility();
    }

    private void updateDateStyles() {
        for (Map.Entry<String, TextView> e : dateChipViews.entrySet()) {
            applyM3ChipStyle(e.getValue(), e.getKey().equals(selectedDatePreset));
        }
        updateResetButtonVisibility();
    }

    private void updateDocWarningVisibility() {
        if (docSearchWarningBanner != null) {
            boolean show = "document".equals(selectedFilter);
            if (show && docSearchWarningBanner.getVisibility() != View.VISIBLE) {
                docSearchWarningBanner.setVisibility(View.VISIBLE);
                docSearchWarningBanner.setAlpha(0f);
                docSearchWarningBanner.animate().alpha(1f).setDuration(200).setInterpolator(EASE_OUT_QUINT).withLayer().start();
            } else if (!show && docSearchWarningBanner.getVisibility() == View.VISIBLE) {
                docSearchWarningBanner.animate().alpha(0f).setDuration(160).setInterpolator(EASE_IN_QUINT).withLayer().withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        docSearchWarningBanner.setVisibility(View.GONE);
                    }
                }).start();
            }
        }
    }

    private void updateResetButtonVisibility() {
        boolean hasFilters = !"all".equals(selectedFilter) || !"all".equals(selectedDatePreset) || customMinDate != 0 || customMaxDate != 0 || filterByUserId != 0 || ("video".equals(selectedFilter) && !"date".equals(selectedVideoSort)) || ("document".equals(selectedFilter) && (!"all".equals(selectedDocExt) || !"date".equals(selectedDocSort))) || onlyMineSelected;
        if (resetRow != null) {
            if (hasFilters && resetRow.getVisibility() != View.VISIBLE) {
                resetRow.setVisibility(View.VISIBLE);
                resetRow.setAlpha(0f);
                resetRow.animate().alpha(1f).setDuration(200).setInterpolator(EASE_OUT_QUINT).withLayer().start();
            } else if (!hasFilters && resetRow.getVisibility() == View.VISIBLE) {
                resetRow.animate().alpha(0f).setDuration(160).setInterpolator(EASE_IN_QUINT).withLayer().withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        resetRow.setVisibility(View.GONE);
                    }
                }).start();
            }
        }
    }

    public static class VectorIconDrawable extends Drawable {
        private static final Map<String, Path> pathCache = new HashMap<>();
        private static final Paint sharedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private static final Paint sharedFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        static {
            sharedPaint.setStyle(Paint.Style.STROKE);
            sharedPaint.setStrokeCap(Paint.Cap.ROUND);
            sharedPaint.setStrokeJoin(Paint.Join.ROUND);
            sharedFillPaint.setStyle(Paint.Style.FILL);
        }

        private final String iconType;
        private int color;
        private final int size;

        public VectorIconDrawable(String iconType, int color, int sizePx) {
            this.iconType = iconType != null ? iconType : "";
            this.color = color;
            this.size = sizePx;
            setBounds(0, 0, sizePx, sizePx);
        }

        public void setColor(int newColor) {
            if (this.color != newColor) {
                this.color = newColor;
                invalidateSelf();
            }
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            int w = b.width() > 0 ? b.width() : size;
            int h = b.height() > 0 ? b.height() : size;
            float s = Math.min(w, h) / 24.0f;

            canvas.save();
            canvas.translate(b.left + (w - 24.0f * s) / 2.0f, b.top + (h - 24.0f * s) / 2.0f);

            sharedPaint.setColor(color);
            sharedPaint.setStrokeWidth(Math.max(1.6f * s, 1.0f));
            sharedFillPaint.setColor(color);

            if ("chat".equals(iconType) || "location".equals(iconType)) {
                Path p = pathCache.get("chat");
                if (p == null) {
                    p = new Path();
                    p.moveTo(12, 21.5f);
                    p.cubicTo(7, 14.5f, 5, 11, 5, 8.5f);
                    p.arcTo(new RectF(5, 2, 19, 16), 180, 180);
                    p.cubicTo(19, 11, 17, 14.5f, 12, 21.5f);
                    p.close();
                    pathCache.put("chat", p);
                }
                canvas.save();
                canvas.scale(s, s);
                canvas.drawPath(p, sharedPaint);
                canvas.drawCircle(12, 9, 2.5f, sharedFillPaint);
                canvas.restore();
            } else if ("global".equals(iconType)) {
                canvas.drawCircle(12 * s, 12 * s, 8.5f * s, sharedPaint);
                canvas.drawLine(3.5f * s, 12 * s, 20.5f * s, 12 * s, sharedPaint);
                canvas.drawLine(12 * s, 3.5f * s, 12 * s, 20.5f * s, sharedPaint);
                canvas.drawOval(new RectF(7.5f * s, 3.5f * s, 16.5f * s, 20.5f * s), sharedPaint);
            } else if ("photo".equals(iconType)) {
                canvas.drawRoundRect(new RectF(3.5f * s, 4.5f * s, 20.5f * s, 19.5f * s), 3 * s, 3 * s, sharedPaint);
                canvas.drawCircle(8.5f * s, 9.5f * s, 2 * s, sharedFillPaint);
                Path p = pathCache.get("photo");
                if (p == null) {
                    p = new Path();
                    p.moveTo(4, 17);
                    p.lineTo(9.5f, 12);
                    p.lineTo(14.5f, 16);
                    p.lineTo(17.5f, 13.5f);
                    p.lineTo(20, 16);
                    pathCache.put("photo", p);
                }
                canvas.save();
                canvas.scale(s, s);
                canvas.drawPath(p, sharedPaint);
                canvas.restore();
            } else if ("video".equals(iconType)) {
                canvas.drawRoundRect(new RectF(3.5f * s, 5 * s, 20.5f * s, 19 * s), 3.5f * s, 3.5f * s, sharedPaint);
                Path p = pathCache.get("video");
                if (p == null) {
                    p = new Path();
                    p.moveTo(10, 8.5f);
                    p.lineTo(16, 12);
                    p.lineTo(10, 15.5f);
                    p.close();
                    pathCache.put("video", p);
                }
                canvas.save();
                canvas.scale(s, s);
                canvas.drawPath(p, sharedFillPaint);
                canvas.restore();
            } else if ("document".equals(iconType)) {
                Path p = pathCache.get("document");
                if (p == null) {
                    p = new Path();
                    p.moveTo(6, 3.5f);
                    p.lineTo(14, 3.5f);
                    p.lineTo(18.5f, 8);
                    p.lineTo(18.5f, 20.5f);
                    p.lineTo(6, 20.5f);
                    p.close();
                    pathCache.put("document", p);
                }
                canvas.save();
                canvas.scale(s, s);
                canvas.drawPath(p, sharedPaint);
                canvas.drawLine(14, 3.5f, 14, 8, sharedPaint);
                canvas.drawLine(14, 8, 18.5f, 8, sharedPaint);
                canvas.drawLine(9, 12.5f, 15.5f, 12.5f, sharedPaint);
                canvas.drawLine(9, 16, 13.5f, 16, sharedPaint);
                canvas.restore();
            } else if ("music".equals(iconType)) {
                Path p = pathCache.get("music");
                if (p == null) {
                    p = new Path();
                    p.moveTo(11, 16);
                    p.lineTo(11, 6);
                    p.lineTo(18, 4);
                    p.lineTo(18, 14);
                    pathCache.put("music", p);
                }
                canvas.save();
                canvas.scale(s, s);
                canvas.drawPath(p, sharedPaint);
                canvas.drawLine(11, 9, 18, 7, sharedPaint);
                canvas.drawCircle(9, 16.5f, 2.5f, sharedFillPaint);
                canvas.drawCircle(16, 14.5f, 2.5f, sharedFillPaint);
                canvas.restore();
            } else if ("voice".equals(iconType)) {
                canvas.drawRoundRect(new RectF(9.5f * s, 4 * s, 14.5f * s, 13 * s), 2.5f * s, 2.5f * s, sharedFillPaint);
                canvas.drawArc(new RectF(6.5f * s, 7.5f * s, 17.5f * s, 16 * s), 0, 180, false, sharedPaint);
                canvas.drawLine(12 * s, 16 * s, 12 * s, 20 * s, sharedPaint);
                canvas.drawLine(8.5f * s, 20 * s, 15.5f * s, 20 * s, sharedPaint);
            } else if ("round".equals(iconType)) {
                canvas.drawCircle(12 * s, 12 * s, 8.5f * s, sharedPaint);
                Path p = pathCache.get("round");
                if (p == null) {
                    p = new Path();
                    p.moveTo(10.5f, 9);
                    p.lineTo(15.5f, 12);
                    p.lineTo(10.5f, 15.5f);
                    p.close();
                    pathCache.put("round", p);
                }
                canvas.save();
                canvas.scale(s, s);
                canvas.drawPath(p, sharedFillPaint);
                canvas.restore();
            } else if ("url".equals(iconType)) {
                canvas.drawRoundRect(new RectF(4 * s, 8.5f * s, 12.5f * s, 15.5f * s), 3.5f * s, 3.5f * s, sharedPaint);
                canvas.drawRoundRect(new RectF(11.5f * s, 8.5f * s, 20 * s, 15.5f * s), 3.5f * s, 3.5f * s, sharedPaint);
                canvas.drawLine(9.5f * s, 12 * s, 14.5f * s, 12 * s, sharedPaint);
            } else if ("calendar".equals(iconType)) {
                canvas.drawRoundRect(new RectF(4 * s, 5.5f * s, 20 * s, 20.5f * s), 3 * s, 3 * s, sharedPaint);
                canvas.drawLine(4 * s, 10.5f * s, 20 * s, 10.5f * s, sharedPaint);
                canvas.drawLine(8 * s, 3.5f * s, 8 * s, 7.5f * s, sharedPaint);
                canvas.drawLine(16 * s, 3.5f * s, 16 * s, 7.5f * s, sharedPaint);
                canvas.drawCircle(8.5f * s, 14.5f * s, 1.2f * s, sharedFillPaint);
                canvas.drawCircle(12 * s, 14.5f * s, 1.2f * s, sharedFillPaint);
                canvas.drawCircle(15.5f * s, 14.5f * s, 1.2f * s, sharedFillPaint);
                canvas.drawCircle(8.5f * s, 17.5f * s, 1.2f * s, sharedFillPaint);
                canvas.drawCircle(12 * s, 17.5f * s, 1.2f * s, sharedFillPaint);
            } else if ("user".equals(iconType)) {
                canvas.drawCircle(12 * s, 8 * s, 3.5f * s, sharedPaint);
                canvas.drawArc(new RectF(5 * s, 13.5f * s, 19 * s, 23.5f * s), 180, 180, false, sharedPaint);
            } else if ("close".equals(iconType)) {
                canvas.drawLine(6 * s, 6 * s, 18 * s, 18 * s, sharedPaint);
                canvas.drawLine(18 * s, 6 * s, 6 * s, 18 * s, sharedPaint);
            } else if ("minimize".equals(iconType)) {
                canvas.drawLine(6 * s, 15 * s, 18 * s, 15 * s, sharedPaint);
            } else if ("add".equals(iconType)) {
                canvas.drawLine(12 * s, 5 * s, 12 * s, 19 * s, sharedPaint);
                canvas.drawLine(5 * s, 12 * s, 19 * s, 12 * s, sharedPaint);
            } else if ("search".equals(iconType)) {
                canvas.drawCircle(10.5f * s, 10.5f * s, 5.5f * s, sharedPaint);
                canvas.drawLine(14.5f * s, 14.5f * s, 19.5f * s, 19.5f * s, sharedPaint);
            } else if ("time".equals(iconType)) {
                canvas.drawCircle(12 * s, 12 * s, 8.5f * s, sharedPaint);
                canvas.drawLine(12 * s, 7 * s, 12 * s, 12 * s, sharedPaint);
                canvas.drawLine(12 * s, 12 * s, 16 * s, 14 * s, sharedPaint);
            } else if ("copy".equals(iconType)) {
                canvas.drawRoundRect(new RectF(7 * s, 4 * s, 19 * s, 17 * s), 2.5f * s, 2.5f * s, sharedPaint);
                canvas.drawRoundRect(new RectF(4 * s, 7 * s, 16 * s, 20 * s), 2.5f * s, 2.5f * s, sharedPaint);
            } else if ("play".equals(iconType)) {
                Path p = pathCache.get("play");
                if (p == null) {
                    p = new Path();
                    p.moveTo(8.5f, 6.5f);
                    p.lineTo(18.5f, 12f);
                    p.lineTo(8.5f, 17.5f);
                    p.close();
                    pathCache.put("play", p);
                }
                canvas.save();
                canvas.scale(s, s);
                canvas.drawPath(p, sharedFillPaint);
                canvas.restore();
            } else if ("pause".equals(iconType)) {
                canvas.drawRoundRect(new RectF(7.5f * s, 6.5f * s, 10.5f * s, 17.5f * s), 1.5f * s, 1.5f * s, sharedFillPaint);
                canvas.drawRoundRect(new RectF(13.5f * s, 6.5f * s, 16.5f * s, 17.5f * s), 1.5f * s, 1.5f * s, sharedFillPaint);
            } else if ("download".equals(iconType)) {
                canvas.drawLine(12 * s, 5 * s, 12 * s, 15 * s, sharedPaint);
                canvas.drawLine(8 * s, 11 * s, 12 * s, 15 * s, sharedPaint);
                canvas.drawLine(16 * s, 11 * s, 12 * s, 15 * s, sharedPaint);
                canvas.drawLine(5 * s, 19 * s, 19 * s, 19 * s, sharedPaint);
            } else if ("check".equals(iconType)) {
                canvas.drawLine(5 * s, 12 * s, 10 * s, 17 * s, sharedPaint);
                canvas.drawLine(10 * s, 17 * s, 19 * s, 7 * s, sharedPaint);
            } else if ("link".equals(iconType)) {
                canvas.drawRoundRect(new RectF(4 * s, 8.5f * s, 12.5f * s, 15.5f * s), 3.5f * s, 3.5f * s, sharedPaint);
                canvas.drawRoundRect(new RectF(11.5f * s, 8.5f * s, 20 * s, 15.5f * s), 3.5f * s, 3.5f * s, sharedPaint);
                canvas.drawLine(9.5f * s, 12 * s, 14.5f * s, 12 * s, sharedPaint);
            } else if ("id".equals(iconType)) {
                canvas.drawRoundRect(new RectF(4 * s, 5.5f * s, 20 * s, 18.5f * s), 2.5f * s, 2.5f * s, sharedPaint);
                canvas.drawCircle(8.5f * s, 11 * s, 2 * s, sharedFillPaint);
                canvas.drawLine(13 * s, 10 * s, 17 * s, 10 * s, sharedPaint);
                canvas.drawLine(13 * s, 13 * s, 17 * s, 13 * s, sharedPaint);
            } else if ("tag".equals(iconType)) {
                Path p = pathCache.get("tag");
                if (p == null) {
                    p = new Path();
                    p.moveTo(4, 12);
                    p.lineTo(12, 4);
                    p.lineTo(19.5f, 4);
                    p.lineTo(19.5f, 11.5f);
                    p.lineTo(11.5f, 19.5f);
                    p.close();
                    pathCache.put("tag", p);
                }
                canvas.save();
                canvas.scale(s, s);
                canvas.drawPath(p, sharedPaint);
                canvas.drawCircle(16, 7.5f, 1.5f, sharedFillPaint);
                canvas.restore();
            } else if ("message".equals(iconType)) {
                Path p = pathCache.get("message");
                if (p == null) {
                    p = new Path();
                    p.moveTo(4, 17.5f);
                    p.lineTo(4, 7.5f);
                    p.arcTo(new RectF(4, 4.5f, 20, 17.5f), 180, 180);
                    p.arcTo(new RectF(4, 4.5f, 20, 17.5f), 0, 90);
                    p.lineTo(7, 17.5f);
                    p.lineTo(4, 20.5f);
                    p.close();
                    pathCache.put("message", p);
                }
                canvas.save();
                canvas.scale(s, s);
                canvas.drawPath(p, sharedPaint);
                canvas.restore();
            } else if ("bookmark".equals(iconType)) {
                Path p = pathCache.get("bookmark");
                if (p == null) {
                    p = new Path();
                    p.moveTo(6, 4);
                    p.lineTo(18, 4);
                    p.lineTo(18, 20);
                    p.lineTo(12, 15.5f);
                    p.lineTo(6, 20);
                    p.close();
                    pathCache.put("bookmark", p);
                }
                canvas.save();
                canvas.scale(s, s);
                canvas.drawPath(p, sharedPaint);
                canvas.restore();
            } else if ("history".equals(iconType)) {
                canvas.drawCircle(12 * s, 12 * s, 8.5f * s, sharedPaint);
                canvas.drawLine(12 * s, 7 * s, 12 * s, 12 * s, sharedPaint);
                canvas.drawLine(12 * s, 12 * s, 16 * s, 14 * s, sharedPaint);
                Path p = pathCache.get("history_arrow");
                if (p == null) {
                    p = new Path();
                    p.moveTo(3.5f, 12);
                    p.lineTo(6.5f, 8);
                    p.lineTo(6.5f, 12);
                    p.close();
                    pathCache.put("history_arrow", p);
                }
                canvas.save();
                canvas.scale(s, s);
                canvas.drawPath(p, sharedFillPaint);
                canvas.restore();
            } else if ("info".equals(iconType)) {
                canvas.drawCircle(12 * s, 12 * s, 8.5f * s, sharedPaint);
                canvas.drawCircle(12 * s, 8 * s, 1.2f * s, sharedFillPaint);
                canvas.drawLine(12 * s, 11 * s, 12 * s, 16.5f * s, sharedPaint);
                canvas.drawLine(10.5f * s, 11 * s, 12 * s, 11 * s, sharedPaint);
            } else if ("touch".equals(iconType)) {
                canvas.drawCircle(12 * s, 12 * s, 2.5f * s, sharedFillPaint);
                canvas.drawCircle(12 * s, 12 * s, 5.5f * s, sharedPaint);
                canvas.drawCircle(12 * s, 12 * s, 8.5f * s, sharedPaint);
            } else if ("more".equals(iconType)) {
                canvas.drawCircle(12 * s, 6 * s, 1.8f * s, sharedFillPaint);
                canvas.drawCircle(12 * s, 12 * s, 1.8f * s, sharedFillPaint);
                canvas.drawCircle(12 * s, 18 * s, 1.8f * s, sharedFillPaint);
            } else if ("share".equals(iconType)) {
                canvas.drawCircle(18 * s, 6 * s, 2.8f * s, sharedPaint);
                canvas.drawCircle(6 * s, 12 * s, 2.8f * s, sharedPaint);
                canvas.drawCircle(18 * s, 18 * s, 2.8f * s, sharedPaint);
                canvas.drawLine(8.5f * s, 10.7f * s, 15.5f * s, 7.3f * s, sharedPaint);
                canvas.drawLine(8.5f * s, 13.3f * s, 15.5f * s, 16.7f * s, sharedPaint);
            } else if ("trash".equals(iconType)) {
                canvas.drawLine(4 * s, 6 * s, 20 * s, 6 * s, sharedPaint);
                canvas.drawLine(9 * s, 6 * s, 9 * s, 3.5f * s, sharedPaint);
                canvas.drawLine(15 * s, 6 * s, 15 * s, 3.5f * s, sharedPaint);
                canvas.drawLine(9 * s, 3.5f * s, 15 * s, 3.5f * s, sharedPaint);
                canvas.drawRoundRect(new RectF(6 * s, 6 * s, 18 * s, 20.5f * s), 2 * s, 2 * s, sharedPaint);
                canvas.drawLine(10 * s, 9.5f * s, 10 * s, 17 * s, sharedPaint);
                canvas.drawLine(14 * s, 9.5f * s, 14 * s, 17 * s, sharedPaint);
            } else if ("open".equals(iconType)) {
                canvas.drawLine(12 * s, 4 * s, 20 * s, 4 * s, sharedPaint);
                canvas.drawLine(20 * s, 4 * s, 20 * s, 12 * s, sharedPaint);
                canvas.drawLine(10 * s, 14 * s, 19.5f * s, 4.5f * s, sharedPaint);
                canvas.drawRoundRect(new RectF(4 * s, 8 * s, 15 * s, 20 * s), 2.5f * s, 2.5f * s, sharedPaint);
            }

            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {
            sharedPaint.setAlpha(alpha);
            sharedFillPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            sharedPaint.setColorFilter(colorFilter);
            sharedFillPaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    public static class SearchHistoryItem {
        public String query;
        public String scope;
        public String filter;
        public String datePreset;
        public long minDate;
        public long maxDate;
        public long userId;
        public String userName;
        public long timestamp;
        public boolean isPinned;

        public SearchHistoryItem(String query, String scope, String filter, String datePreset, long minDate, long maxDate, long userId, String userName, long timestamp, boolean isPinned) {
            this.query = query != null ? query : "";
            this.scope = scope != null ? scope : "chat";
            this.filter = filter != null ? filter : "all";
            this.datePreset = datePreset != null ? datePreset : "all";
            this.minDate = minDate;
            this.maxDate = maxDate;
            this.userId = userId;
            this.userName = userName != null ? userName : "";
            this.timestamp = timestamp;
            this.isPinned = isPinned;
        }
    }

    private static List<SearchHistoryItem> cachedHistory = null;

    private List<SearchHistoryItem> loadSearchHistory() {
        if (cachedHistory != null) {
            return new ArrayList<>(cachedHistory);
        }
        List<SearchHistoryItem> pinnedList = new ArrayList<>();
        List<SearchHistoryItem> recentList = new ArrayList<>();
        try {
            SharedPreferences prefs = context.getSharedPreferences("extera_advanced_search_prefs", Context.MODE_PRIVATE);
            String raw = prefs.getString("search_history", null);
            if (raw != null && !raw.trim().isEmpty()) {
                String[] items = raw.split(";;;");
                for (String it : items) {
                    String[] p = it.split("\\|\\|\\|");
                    if (p.length >= 10) {
                        boolean isPinned = Boolean.parseBoolean(p[9]);
                        SearchHistoryItem item = new SearchHistoryItem(
                                p[0], p[1], p[2], p[3],
                                Long.parseLong(p[4]), Long.parseLong(p[5]),
                                Long.parseLong(p[6]), p[7],
                                Long.parseLong(p[8]), isPinned);
                        if (isPinned) {
                            pinnedList.add(item);
                        } else {
                            recentList.add(item);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        List<SearchHistoryItem> result = new ArrayList<>();
        result.addAll(pinnedList);
        result.addAll(recentList);
        cachedHistory = result;
        return new ArrayList<>(result);
    }

    private void saveSearchHistory(List<SearchHistoryItem> list) {
        cachedHistory = new ArrayList<>(list);
        try {
            StringBuilder sb = new StringBuilder();
            for (SearchHistoryItem it : list) {
                if (sb.length() > 0) sb.append(";;;");
                sb.append(it.query).append("|||")
                        .append(it.scope).append("|||")
                        .append(it.filter).append("|||")
                        .append(it.datePreset).append("|||")
                        .append(it.minDate).append("|||")
                        .append(it.maxDate).append("|||")
                        .append(it.userId).append("|||")
                        .append(it.userName).append("|||")
                        .append(it.timestamp).append("|||")
                        .append(it.isPinned);
            }
            SharedPreferences prefs = context.getSharedPreferences("extera_advanced_search_prefs", Context.MODE_PRIVATE);
            prefs.edit().putString("search_history", sb.toString()).apply();
        } catch (Throwable ignored) {}
    }

    private void addSearchHistoryItem(String q, String sc, String f, String dp, long minD, long maxD, long uId, String uName, boolean pinned) {
        if (!hasActiveSearchCriteria()) return;
        List<SearchHistoryItem> list = loadSearchHistory();
        String cleanQ = q != null ? q.trim() : "";
        String cleanSc = sc != null ? sc : "chat";
        String cleanF = f != null ? f : "all";
        String cleanDp = dp != null ? dp : "all";
        String cleanUn = uName != null ? uName.trim() : "";

        boolean existedAsPinned = false;
        for (SearchHistoryItem it : list) {
            boolean isSame = it.query.equalsIgnoreCase(cleanQ)
                    && it.filter.equals(cleanF)
                    && it.datePreset.equals(cleanDp)
                    && it.userId == uId
                    && it.minDate == minD
                    && it.maxDate == maxD;
            if (isSame && it.isPinned) {
                existedAsPinned = true;
            }
        }

        boolean finalPinned = pinned || existedAsPinned;
        SearchHistoryItem newItem = new SearchHistoryItem(cleanQ, cleanSc, cleanF, cleanDp, minD, maxD, uId, cleanUn, System.currentTimeMillis(), finalPinned);

        List<SearchHistoryItem> updatedPinned = new ArrayList<>();
        List<SearchHistoryItem> updatedRecent = new ArrayList<>();

        if (finalPinned) {
            updatedPinned.add(newItem);
        } else {
            updatedRecent.add(newItem);
        }

        for (SearchHistoryItem it : list) {
            boolean isSame = it.query.equalsIgnoreCase(cleanQ)
                    && it.filter.equals(cleanF)
                    && it.datePreset.equals(cleanDp)
                    && it.userId == uId
                    && it.minDate == minD
                    && it.maxDate == maxD;
            if (!isSame) {
                if (it.isPinned) {
                    if (updatedPinned.size() < 50) updatedPinned.add(it);
                } else {
                    if (updatedRecent.size() < 50) updatedRecent.add(it);
                }
            }
        }

        List<SearchHistoryItem> finalList = new ArrayList<>();
        finalList.addAll(updatedPinned);
        finalList.addAll(updatedRecent);
        saveSearchHistory(finalList);
    }

    private void deleteSearchHistoryItem(SearchHistoryItem target) {
        if (target == null) return;
        List<SearchHistoryItem> list = loadSearchHistory();
        List<SearchHistoryItem> updated = new ArrayList<>();
        for (SearchHistoryItem it : list) {
            boolean isSame = it.query.equals(target.query)
                    && it.filter.equals(target.filter)
                    && it.datePreset.equals(target.datePreset)
                    && it.userId == target.userId
                    && it.minDate == target.minDate
                    && it.maxDate == target.maxDate
                    && it.isPinned == target.isPinned;
            if (!isSame) {
                updated.add(it);
            }
        }
        saveSearchHistory(updated);
    }

    private void clearSavedFilters() {
        List<SearchHistoryItem> list = loadSearchHistory();
        List<SearchHistoryItem> updated = new ArrayList<>();
        for (SearchHistoryItem it : list) {
            if (!it.isPinned) {
                updated.add(it);
            }
        }
        saveSearchHistory(updated);
    }

    private void clearSearchHistory() {
        List<SearchHistoryItem> list = loadSearchHistory();
        List<SearchHistoryItem> updated = new ArrayList<>();
        for (SearchHistoryItem it : list) {
            if (it.isPinned) {
                updated.add(it);
            }
        }
        saveSearchHistory(updated);
    }

    private boolean hasActiveSearchCriteria() {
        if (query != null && !query.trim().isEmpty()) return true;
        if (!"all".equals(selectedFilter)) return true;
        if (!"all".equals(selectedDatePreset) || customMinDate != 0 || customMaxDate != 0) return true;
        if (filterByUserId != 0 || (filterByUserName != null && !filterByUserName.isEmpty())) return true;
        return false;
    }

    private void renderSearchHistory() {
        if (resultsContainer == null) return;
        resultsContainer.removeAllViews();
        statsBadge.setVisibility(View.GONE);
        docSearchWarningBanner.setVisibility(View.GONE);
        resetRow.setVisibility(View.GONE);

        final List<SearchHistoryItem> all = loadSearchHistory();
        final int primaryColor = getPrimaryTextColor();
        final int secondaryColor = getSecondaryTextColor();
        final int accentColor = getAccentColor();

        final List<SearchHistoryItem> pinnedList = new ArrayList<>();
        final List<SearchHistoryItem> recentList = new ArrayList<>();
        for (SearchHistoryItem it : all) {
            if (it.isPinned) pinnedList.add(it);
            else recentList.add(it);
        }

        if (pinnedList.isEmpty() && recentList.isEmpty()) {
            LinearLayout emptyBox = new LinearLayout(context);
            emptyBox.setOrientation(LinearLayout.VERTICAL);
            emptyBox.setGravity(Gravity.CENTER_HORIZONTAL);
            emptyBox.setPadding(dp(20), dp(48), dp(20), dp(48));

            ImageView emptyIcon = new ImageView(context);
            emptyIcon.setImageDrawable(new VectorIconDrawable("history", (secondaryColor & 0x00FFFFFF) | 0x66000000, dp(38)));
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(38), dp(38));
            iconLp.bottomMargin = dp(14);
            emptyIcon.setLayoutParams(iconLp);
            emptyBox.addView(emptyIcon);

            TextView emptyTitle = new TextView(context);
            emptyTitle.setText(tr("История поиска пуста", "Search history is empty"));
            emptyTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
            emptyTitle.setTextColor(secondaryColor);
            emptyTitle.setTypeface(null, Typeface.BOLD);
            emptyTitle.setGravity(Gravity.CENTER);
            emptyBox.addView(emptyTitle);

            TextView emptySub = new TextView(context);
            emptySub.setText(tr("Введите поисковый запрос или сохраните фильтры", "Enter a query or save filter presets"));
            emptySub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            emptySub.setTextColor((secondaryColor & 0x00FFFFFF) | 0x88000000);
            emptySub.setGravity(Gravity.CENTER);
            emptySub.setPadding(0, dp(4), 0, 0);
            emptyBox.addView(emptySub);

            resultsContainer.addView(emptyBox);
            return;
        }

        if (!pinnedList.isEmpty()) {
            LinearLayout pinHeader = new LinearLayout(context);
            pinHeader.setOrientation(LinearLayout.HORIZONTAL);
            pinHeader.setGravity(Gravity.CENTER_VERTICAL);
            pinHeader.setPadding(dp(4), dp(2), dp(4), dp(8));

            TextView pinTitle = new TextView(context);
            pinTitle.setText(tr("Закрепленные фильтры", "Pinned filters"));
            pinTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
            pinTitle.setTextColor(accentColor);
            pinTitle.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams ptLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            pinTitle.setLayoutParams(ptLp);
            pinHeader.addView(pinTitle);

            TextView clearPinBtn = new TextView(context);
            clearPinBtn.setText(tr("Очистить", "Clear"));
            clearPinBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            clearPinBtn.setTextColor(accentColor);
            clearPinBtn.setTypeface(null, Typeface.BOLD);
            clearPinBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
            clearPinBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                    clearSavedFilters();
                    renderSearchHistory();
                }
            });
            pinHeader.addView(clearPinBtn);
            resultsContainer.addView(pinHeader);

            for (SearchHistoryItem item : pinnedList) {
                renderHistoryCard(item, primaryColor, secondaryColor, accentColor);
            }
        }

        if (!recentList.isEmpty()) {
            LinearLayout recHeader = new LinearLayout(context);
            recHeader.setOrientation(LinearLayout.HORIZONTAL);
            recHeader.setGravity(Gravity.CENTER_VERTICAL);
            recHeader.setPadding(dp(4), pinnedList.isEmpty() ? dp(2) : dp(14), dp(4), dp(8));

            TextView recTitle = new TextView(context);
            recTitle.setText(tr("История поиска", "Search history"));
            recTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
            recTitle.setTextColor(secondaryColor);
            recTitle.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams rtLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            recTitle.setLayoutParams(rtLp);
            recHeader.addView(recTitle);

            TextView clearBtn = new TextView(context);
            clearBtn.setText(tr("Очистить", "Clear"));
            clearBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            clearBtn.setTextColor(accentColor);
            clearBtn.setTypeface(null, Typeface.BOLD);
            clearBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
            clearBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                    clearSearchHistory();
                    renderSearchHistory();
                }
            });
            recHeader.addView(clearBtn);
            resultsContainer.addView(recHeader);

            for (SearchHistoryItem item : recentList) {
                renderHistoryCard(item, primaryColor, secondaryColor, accentColor);
            }
        }
    }

    private void renderHistoryCard(final SearchHistoryItem item, final int primaryColor, final int secondaryColor, final int accentColor) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(10), dp(10), dp(10));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, dp(6));
        card.setLayoutParams(cardLp);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(getSurfaceContainerColor());
        cardBg.setCornerRadius(dp(14));
        int strokeColor = item.isPinned 
                ? ((accentColor & 0x00FFFFFF) | (isDarkTheme() ? 0x44000000 : 0x30000000))
                : (isDarkTheme() ? 0x12FFFFFF : 0x0E000000);
        cardBg.setStroke(dp(1), strokeColor);
        card.setBackground(cardBg);

        ImageView iconIv = new ImageView(context);
        iconIv.setImageDrawable(new VectorIconDrawable(item.isPinned ? "bookmark" : "history", item.isPinned ? accentColor : secondaryColor, dp(17)));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(17), dp(17));
        iconLp.rightMargin = dp(12);
        iconIv.setLayoutParams(iconLp);
        card.addView(iconIv);

        LinearLayout contentCol = new LinearLayout(context);
        contentCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        contentCol.setLayoutParams(colLp);

        String title = item.query != null && !item.query.isEmpty() ? item.query : (item.userName != null && !item.userName.isEmpty() ? item.userName : tr("Поиск по фильтрам", "Filter search"));
        TextView titleTv = new TextView(context);
        titleTv.setText(title);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        titleTv.setTextColor(primaryColor);
        titleTv.setTypeface(null, Typeface.BOLD);
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        contentCol.addView(titleTv);

        LinearLayout tagsRow = new LinearLayout(context);
        tagsRow.setOrientation(LinearLayout.HORIZONTAL);
        tagsRow.setPadding(0, dp(3), 0, 0);

        if (item.scope != null && "global".equals(item.scope)) {
            addHistoryTag(tagsRow, tr("По всем чатам", "All chats"), (accentColor & 0x00FFFFFF) | 0x22000000, accentColor);
        }
        if (item.filter != null && !"all".equals(item.filter)) {
            String fName = getFilterDisplayName(item.filter);
            addHistoryTag(tagsRow, fName, isDarkTheme() ? 0x18FFFFFF : 0x0E000000, secondaryColor);
        }
        if (item.datePreset != null && !"all".equals(item.datePreset)) {
            String dName = getDatePresetDisplayName(item.datePreset, item.minDate, item.maxDate);
            addHistoryTag(tagsRow, dName, isDarkTheme() ? 0x18FFFFFF : 0x0E000000, secondaryColor);
        }
        if (item.userName != null && !item.userName.isEmpty() && !title.equals(item.userName)) {
            addHistoryTag(tagsRow, item.userName, (accentColor & 0x00FFFFFF) | 0x22000000, accentColor);
        }

        if (tagsRow.getChildCount() > 0) {
            contentCol.addView(tagsRow);
        }
        card.addView(contentCol);

        ImageView delBtn = new ImageView(context);
        delBtn.setImageDrawable(new VectorIconDrawable("close", (secondaryColor & 0x00FFFFFF) | 0x88000000, dp(14)));
        delBtn.setPadding(dp(8), dp(8), dp(8), dp(8));
        delBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                deleteSearchHistoryItem(item);
                renderSearchHistory();
            }
        });
        card.addView(delBtn);

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performHaptic(v, HapticFeedbackConstants.KEYBOARD_TAP);
                query = item.query != null ? item.query : "";
                scope = item.scope != null ? item.scope : (peerId != 0 ? "chat" : "global");
                selectedFilter = item.filter != null ? item.filter : "all";
                selectedDatePreset = item.datePreset != null ? item.datePreset : "all";
                customMinDate = item.minDate;
                customMaxDate = item.maxDate;
                filterByUserId = item.userId;
                filterByUserName = item.userName;

                if (searchInputField != null) {
                    searchInputField.setText(query);
                }
                if ("custom".equals(selectedDatePreset) && customMinDate != 0 && customMaxDate != 0) {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd.MM", Locale.getDefault());
                    TextView customChip = dateChipViews.get("custom");
                    if (customChip != null) {
                        customChip.setText(sdf.format(new Date(customMinDate * 1000L)) + " – " + sdf.format(new Date(customMaxDate * 1000L)));
                    }
                }
                updateScopeStyles();
                updateFilterStyles();
                updateDateStyles();
                updateUserFilterChip();
                triggerSearchDelayed();
            }
        });

        resultsContainer.addView(card);
    }

    private void addHistoryTag(LinearLayout parent, String text, int bgColor, int textColor) {
        TextView tag = new TextView(context);
        tag.setText(text);
        tag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        tag.setTextColor(textColor);
        tag.setTypeface(null, Typeface.BOLD);
        tag.setPadding(dp(6), dp(1.5f), dp(6), dp(1.5f));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(6));
        bg.setColor(bgColor);
        tag.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(4);
        tag.setLayoutParams(lp);
        parent.addView(tag);
    }

    private String getFilterDisplayName(String f) {
        switch (f) {
            case "photo": return tr("Фото", "Photos");
            case "video": return tr("Видео", "Videos");
            case "document": return tr("Файлы", "Files");
            case "music": return tr("Музыка", "Music");
            case "voice": return tr("Голосовые", "Voice");
            case "url": return tr("Ссылки", "Links");
            default: return f;
        }
    }

    private String getDatePresetDisplayName(String d, long minD, long maxD) {
        switch (d) {
            case "today": return tr("Сегодня", "Today");
            case "yesterday": return tr("Вчера", "Yesterday");
            case "week": return tr("7 дней", "7 days");
            case "month": return tr("30 дней", "30 days");
            case "custom":
                if (minD != 0 && maxD != 0) {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd.MM", Locale.getDefault());
                    return sdf.format(new Date(minD * 1000L)) + " – " + sdf.format(new Date(maxD * 1000L));
                }
                return tr("Период", "Custom range");
            default: return d;
        }
    }

    private static class DatePresetItem {
        String name;
        int startYear, startMonth, startDay;
        int endYear, endMonth, endDay;
        boolean isRelative;
        int relativeDays;

        DatePresetItem(String name, int startYear, int startMonth, int startDay, int endYear, int endMonth, int endDay) {
            this.name = name;
            this.startYear = startYear;
            this.startMonth = startMonth;
            this.startDay = startDay;
            this.endYear = endYear;
            this.endMonth = endMonth;
            this.endDay = endDay;
            this.isRelative = false;
        }

        DatePresetItem(String name, int relativeDays) {
            this.name = name;
            this.relativeDays = relativeDays;
            this.isRelative = true;
        }
    }

    private List<DatePresetItem> loadSavedDatePresets() {
        List<DatePresetItem> list = new ArrayList<>();
        try {
            SharedPreferences prefs = context.getSharedPreferences("extera_advanced_search_prefs", Context.MODE_PRIVATE);
            String raw = prefs.getString("saved_date_presets", null);
            if (raw != null && !raw.trim().isEmpty()) {
                String[] items = raw.split(";;;");
                for (String it : items) {
                    String[] parts = it.split("\\|\\|");
                    if (parts.length == 7) {
                        list.add(new DatePresetItem(parts[0],
                                Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
                                Integer.parseInt(parts[4]), Integer.parseInt(parts[5]), Integer.parseInt(parts[6])));
                    }
                }
            }
        } catch (Throwable ignored) {}
        return list;
    }

    private void saveDatePresets(List<DatePresetItem> list) {
        try {
            StringBuilder sb = new StringBuilder();
            for (DatePresetItem it : list) {
                if (it.isRelative) continue;
                if (sb.length() > 0) sb.append(";;;");
                sb.append(it.name).append("||")
                        .append(it.startYear).append("||").append(it.startMonth).append("||").append(it.startDay).append("||")
                        .append(it.endYear).append("||").append(it.endMonth).append("||").append(it.endDay);
            }
            SharedPreferences prefs = context.getSharedPreferences("extera_advanced_search_prefs", Context.MODE_PRIVATE);
            prefs.edit().putString("saved_date_presets", sb.toString()).apply();
        } catch (Throwable ignored) {}
    }

    private void showSavePresetPrompt(final Calendar s, final Calendar e, final String defName, final Runnable onSaved) {
        final Dialog promptDialog = new Dialog(context, android.R.style.Theme_Translucent_NoTitleBar);
        final FrameLayout overlay = new FrameLayout(context);
        overlay.setBackgroundColor(0x7F000000);

        final LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int cardBg = isDarkTheme() ? getMonetColor("system_neutral1_800", 0xFF1E1B22) : getMonetColor("system_neutral1_50", 0xFFFFFFFF);
        GradientDrawable cardShape = new GradientDrawable();
        cardShape.setColor(cardBg);
        cardShape.setCornerRadius(dp(28));
        cardShape.setStroke(dp(1), isDarkTheme() ? 0x22FFFFFF : 0x14000000);
        card.setBackground(cardShape);
        card.setPadding(dp(24), dp(24), dp(24), dp(24));

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(dp(320), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        card.setLayoutParams(cardLp);

        TextView titleTv = new TextView(context);
        titleTv.setText(tr("Сохранить в быстрый доступ?", "Save to quick access?"));
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        titleTv.setTextColor(getPrimaryTextColor());
        titleTv.setTypeface(null, Typeface.BOLD);
        card.addView(titleTv);

        TextView descTv = new TextView(context);
        descTv.setText(tr("Этот период появится в панели быстрого выбора дат для мгновенного доступа.", "This range will appear in the quick date bar for instant access."));
        descTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        descTv.setTextColor(getSecondaryTextColor());
        descTv.setPadding(0, dp(8), 0, dp(16));
        card.addView(descTv);

        final EditText nameEt = new EditText(context);
        nameEt.setText(defName);
        nameEt.setHint(tr("Название (например: Отпуск)", "Name (e.g. Vacation)"));
        nameEt.setHintTextColor(getSecondaryTextColor());
        nameEt.setTextColor(getPrimaryTextColor());
        nameEt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        nameEt.setSingleLine(true);

        GradientDrawable etBg = new GradientDrawable();
        etBg.setCornerRadius(dp(12));
        etBg.setColor(getSurfaceContainerColor());
        etBg.setStroke(dp(1), isDarkTheme() ? 0x22FFFFFF : 0x14000000);
        nameEt.setBackground(etBg);
        nameEt.setPadding(dp(14), dp(10), dp(14), dp(10));
        card.addView(nameEt);

        LinearLayout btnsRow = new LinearLayout(context);
        btnsRow.setOrientation(LinearLayout.HORIZONTAL);
        btnsRow.setGravity(Gravity.END);
        btnsRow.setPadding(0, dp(20), 0, 0);

        TextView cancelBtn = new TextView(context);
        cancelBtn.setText(tr("Пропустить", "Skip"));
        cancelBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        cancelBtn.setTextColor(getSecondaryTextColor());
        cancelBtn.setPadding(dp(14), dp(8), dp(14), dp(8));
        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                promptDialog.dismiss();
            }
        });
        btnsRow.addView(cancelBtn);

        TextView saveBtn = new TextView(context);
        saveBtn.setText(tr("Сохранить", "Save"));
        saveBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        int accent = getAccentColor();
        saveBtn.setTextColor(isDarkTheme() ? getMonetColor("system_accent1_900", 0xFF000000) : 0xFFFFFFFF);
        saveBtn.setTypeface(null, Typeface.BOLD);
        saveBtn.setPadding(dp(16), dp(8), dp(16), dp(8));
        GradientDrawable sBg = new GradientDrawable();
        sBg.setCornerRadius(dp(10));
        sBg.setColor(accent);
        saveBtn.setBackground(sBg);
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performHaptic(v, HapticFeedbackConstants.KEYBOARD_TAP);
                String customName = nameEt.getText().toString().trim();
                if (customName.isEmpty()) customName = defName;
                List<DatePresetItem> list = loadSavedDatePresets();
                list.add(new DatePresetItem(customName,
                        s.get(Calendar.YEAR), s.get(Calendar.MONTH), s.get(Calendar.DAY_OF_MONTH),
                        e.get(Calendar.YEAR), e.get(Calendar.MONTH), e.get(Calendar.DAY_OF_MONTH)));
                saveDatePresets(list);
                if (onSaved != null) onSaved.run();
                promptDialog.dismiss();
            }
        });
        btnsRow.addView(saveBtn);

        card.addView(btnsRow);
        overlay.addView(card);
        promptDialog.setContentView(overlay);
        setupDialogWindow(promptDialog);
        promptDialog.show();
    }

    private void showDeletePresetPrompt(final DatePresetItem item, final Runnable onDeleted) {
        final Dialog deleteDialog = new Dialog(context, android.R.style.Theme_Translucent_NoTitleBar);
        final FrameLayout overlay = new FrameLayout(context);
        overlay.setBackgroundColor(0x7F000000);

        final LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int cardBg = isDarkTheme() ? getMonetColor("system_neutral1_800", 0xFF1E1B22) : getMonetColor("system_neutral1_50", 0xFFFFFFFF);
        GradientDrawable cardShape = new GradientDrawable();
        cardShape.setColor(cardBg);
        cardShape.setCornerRadius(dp(28));
        cardShape.setStroke(dp(1), isDarkTheme() ? 0x22FFFFFF : 0x14000000);
        card.setBackground(cardShape);
        card.setPadding(dp(24), dp(24), dp(24), dp(24));

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(dp(300), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        card.setLayoutParams(cardLp);

        TextView titleTv = new TextView(context);
        titleTv.setText(tr("Удалить период?", "Delete custom range?"));
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        titleTv.setTextColor(getPrimaryTextColor());
        titleTv.setTypeface(null, Typeface.BOLD);
        card.addView(titleTv);

        TextView descTv = new TextView(context);
        descTv.setText(tr("Период \"", "Range \"") + item.name + tr("\" будет удален из сохраненных.", "\" will be removed from saved."));
        descTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        descTv.setTextColor(getSecondaryTextColor());
        descTv.setPadding(0, dp(8), 0, dp(16));
        card.addView(descTv);

        LinearLayout btnsRow = new LinearLayout(context);
        btnsRow.setOrientation(LinearLayout.HORIZONTAL);
        btnsRow.setGravity(Gravity.END);
        btnsRow.setPadding(0, dp(16), 0, 0);

        TextView cancelBtn = new TextView(context);
        cancelBtn.setText(tr("Отмена", "Cancel"));
        cancelBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        cancelBtn.setTextColor(getSecondaryTextColor());
        cancelBtn.setPadding(dp(14), dp(8), dp(14), dp(8));
        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                deleteDialog.dismiss();
            }
        });
        btnsRow.addView(cancelBtn);

        TextView delBtn = new TextView(context);
        delBtn.setText(tr("Удалить", "Delete"));
        delBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        int errorColor = isDarkTheme() ? getMonetColor("system_accent3_300", 0xFFE17076) : getMonetColor("system_accent3_600", 0xFFB3261E);
        delBtn.setTextColor(0xFFFFFFFF);
        delBtn.setTypeface(null, Typeface.BOLD);
        delBtn.setPadding(dp(16), dp(8), dp(16), dp(8));
        GradientDrawable dBg = new GradientDrawable();
        dBg.setCornerRadius(dp(10));
        dBg.setColor(errorColor);
        delBtn.setBackground(dBg);
        delBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performHaptic(v, HapticFeedbackConstants.KEYBOARD_TAP);
                List<DatePresetItem> list = loadSavedDatePresets();
                List<DatePresetItem> updated = new ArrayList<>();
                for (DatePresetItem it : list) {
                    if (!it.name.equals(item.name)) {
                        updated.add(it);
                    }
                }
                saveDatePresets(updated);
                if (onDeleted != null) onDeleted.run();
                deleteDialog.dismiss();
            }
        });
        btnsRow.addView(delBtn);

        card.addView(btnsRow);
        overlay.addView(card);
        deleteDialog.setContentView(overlay);
        setupDialogWindow(deleteDialog);
        deleteDialog.show();
    }

    private void showDateRangePicker() {
        final Dialog dateDialog = new Dialog(context, android.R.style.Theme_Translucent_NoTitleBar);

        final FrameLayout rootOverlay = new FrameLayout(context);
        rootOverlay.setBackgroundColor(0x88000000);

        final LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int cardBg = isDarkTheme() ? getMonetColor("system_neutral1_800", 0xFF1E1B22) : getMonetColor("system_neutral1_50", 0xFFFFFFFF);
        GradientDrawable cardShape = new GradientDrawable();
        cardShape.setColor(cardBg);
        cardShape.setCornerRadius(dp(28));
        cardShape.setStroke(dp(1), isDarkTheme() ? 0x22FFFFFF : 0x14000000);
        card.setBackground(cardShape);
        card.setPadding(dp(20), dp(18), dp(20), dp(20));

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(dp(340), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        card.setLayoutParams(cardLp);

        final int[] viewedYear = new int[1];
        final int[] viewedMonth = new int[1];
        final Calendar now = Calendar.getInstance();
        viewedYear[0] = now.get(Calendar.YEAR);
        viewedMonth[0] = now.get(Calendar.MONTH);

        final Calendar[] rangeStart = new Calendar[1];
        final Calendar[] rangeEnd = new Calendar[1];

        if (customMinDate > 0) {
            rangeStart[0] = Calendar.getInstance();
            rangeStart[0].setTimeInMillis(customMinDate * 1000L);
            viewedYear[0] = rangeStart[0].get(Calendar.YEAR);
            viewedMonth[0] = rangeStart[0].get(Calendar.MONTH);
        }
        if (customMaxDate > 0) {
            rangeEnd[0] = Calendar.getInstance();
            rangeEnd[0].setTimeInMillis(customMaxDate * 1000L);
        }

        final int primaryText = getPrimaryTextColor();
        final int secondaryText = getSecondaryTextColor();
        final int accentColor = getAccentColor();

        TextView titleTv = new TextView(context);
        titleTv.setText(tr("Выбор диапазона дат", "Select date range"));
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        titleTv.setTextColor(primaryText);
        titleTv.setTypeface(null, Typeface.BOLD);
        titleTv.setPadding(dp(4), 0, dp(4), dp(2));
        card.addView(titleTv);

        final TextView rangeSubTv = new TextView(context);
        rangeSubTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        rangeSubTv.setTextColor(accentColor);
        rangeSubTv.setSingleLine(true);
        rangeSubTv.setEllipsize(TextUtils.TruncateAt.END);
        rangeSubTv.setMinHeight(dp(20));
        rangeSubTv.setPadding(dp(4), 0, dp(4), dp(10));
        card.addView(rangeSubTv);

        HorizontalScrollView presetsScroll = new HorizontalScrollView(context);
        presetsScroll.setHorizontalScrollBarEnabled(false);
        final LinearLayout presetsRow = new LinearLayout(context);
        presetsRow.setOrientation(LinearLayout.HORIZONTAL);
        presetsRow.setPadding(0, 0, 0, dp(12));
        presetsScroll.addView(presetsRow);
        card.addView(presetsScroll);

        LinearLayout monthNav = new LinearLayout(context);
        monthNav.setOrientation(LinearLayout.HORIZONTAL);
        monthNav.setGravity(Gravity.CENTER_VERTICAL);
        monthNav.setPadding(dp(4), 0, dp(4), dp(8));

        final TextView monthTitle = new TextView(context);
        monthTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        monthTitle.setTextColor(primaryText);
        monthTitle.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams mtLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        monthTitle.setLayoutParams(mtLp);
        monthNav.addView(monthTitle);

        final TextView prevBtn = new TextView(context);
        prevBtn.setText("‹");
        prevBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        prevBtn.setTextColor(secondaryText);
        prevBtn.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams prevLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        prevBtn.setLayoutParams(prevLp);
        GradientDrawable navBg1 = new GradientDrawable();
        navBg1.setShape(GradientDrawable.OVAL);
        navBg1.setColor(isDarkTheme() ? 0x14FFFFFF : 0x0A000000);
        prevBtn.setBackground(navBg1);
        monthNav.addView(prevBtn);

        final TextView nextBtn = new TextView(context);
        nextBtn.setText("›");
        nextBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        nextBtn.setTextColor(secondaryText);
        nextBtn.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nextLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        nextLp.leftMargin = dp(8);
        nextBtn.setLayoutParams(nextLp);
        GradientDrawable navBg2 = new GradientDrawable();
        navBg2.setShape(GradientDrawable.OVAL);
        navBg2.setColor(isDarkTheme() ? 0x14FFFFFF : 0x0A000000);
        nextBtn.setBackground(navBg2);
        monthNav.addView(nextBtn);

        card.addView(monthNav);

        LinearLayout daysHeader = new LinearLayout(context);
        daysHeader.setOrientation(LinearLayout.HORIZONTAL);
        daysHeader.setPadding(0, dp(4), 0, dp(6));
        String[] weekDays = isEnglish ? new String[]{"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"} : new String[]{"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        for (String wd : weekDays) {
            TextView wTv = new TextView(context);
            wTv.setText(wd);
            wTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            wTv.setTextColor(secondaryText);
            wTv.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams wLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            wTv.setLayoutParams(wLp);
            daysHeader.addView(wTv);
        }
        card.addView(daysHeader);

        final LinearLayout calendarGrid = new LinearLayout(context);
        calendarGrid.setOrientation(LinearLayout.VERTICAL);
        card.addView(calendarGrid);

        LinearLayout actionsRow = new LinearLayout(context);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setGravity(Gravity.END);
        actionsRow.setPadding(0, dp(14), 0, 0);

        TextView clearBtn = new TextView(context);
        clearBtn.setText(tr("Сбросить", "Reset"));
        clearBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        clearBtn.setTextColor(secondaryText);
        clearBtn.setPadding(dp(14), dp(8), dp(14), dp(8));
        actionsRow.addView(clearBtn);

        final TextView applyBtn = new TextView(context);
        applyBtn.setText(tr("Применить", "Apply"));
        applyBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        applyBtn.setTextColor(isDarkTheme() ? getMonetColor("system_accent1_900", 0xFF000000) : 0xFFFFFFFF);
        applyBtn.setTypeface(null, Typeface.BOLD);
        applyBtn.setPadding(dp(18), dp(8), dp(18), dp(8));
        GradientDrawable applyBg = new GradientDrawable();
        applyBg.setCornerRadius(dp(10));
        applyBg.setColor(accentColor);
        applyBtn.setBackground(applyBg);
        actionsRow.addView(applyBtn);

        card.addView(actionsRow);

        final SimpleDateFormat monthSdf = new SimpleDateFormat("LLLL yyyy", isEnglish ? Locale.US : new Locale("ru"));
        final String[] ruMonthNames = {"Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"};
        final String[] enMonthNames = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
        final String[] monthNames = isEnglish ? enMonthNames : ruMonthNames;

        final Runnable[] renderRef = new Runnable[1];
        renderRef[0] = new Runnable() {
            @Override
            public void run() {
                String mName = monthNames[viewedMonth[0]] + " " + viewedYear[0];
                monthTitle.setText(mName);

                if (rangeStart[0] == null) {
                    rangeSubTv.setText(tr("Выберите начальную дату", "Select start date"));
                } else if (rangeEnd[0] == null) {
                    SimpleDateFormat df = new SimpleDateFormat("dd MMMM yyyy", isEnglish ? Locale.US : new Locale("ru"));
                    rangeSubTv.setText(tr("От ", "From ") + df.format(rangeStart[0].getTime()) + tr(" (выберите конец)", " (select end)"));
                } else {
                    SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                    rangeSubTv.setText(df.format(rangeStart[0].getTime()) + " — " + df.format(rangeEnd[0].getTime()));
                }

                calendarGrid.removeAllViews();

                Calendar todayCal = Calendar.getInstance();
                int curYear = todayCal.get(Calendar.YEAR);
                int curMonth = todayCal.get(Calendar.MONTH);
                int curDay = todayCal.get(Calendar.DAY_OF_MONTH);
                int todayKey = curYear * 10000 + (curMonth + 1) * 100 + curDay;

                boolean isCurrentOrFutureMonth = (viewedYear[0] > curYear || (viewedYear[0] == curYear && viewedMonth[0] >= curMonth));
                if (isCurrentOrFutureMonth) {
                    nextBtn.setEnabled(false);
                    nextBtn.setAlpha(0.25f);
                    nextBtn.setClickable(false);
                } else {
                    nextBtn.setEnabled(true);
                    nextBtn.setAlpha(1.0f);
                    nextBtn.setClickable(true);
                }

                Calendar cal = Calendar.getInstance();
                cal.set(viewedYear[0], viewedMonth[0], 1);
                int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
                int leadEmpty = (firstDayOfWeek == Calendar.SUNDAY) ? 6 : (firstDayOfWeek - Calendar.MONDAY);
                int maxDaysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

                int rows = 6;

                int startKey = -1;
                int endKey = -1;
                if (rangeStart[0] != null) {
                    startKey = rangeStart[0].get(Calendar.YEAR) * 10000 + (rangeStart[0].get(Calendar.MONTH) + 1) * 100 + rangeStart[0].get(Calendar.DAY_OF_MONTH);
                }
                if (rangeEnd[0] != null) {
                    endKey = rangeEnd[0].get(Calendar.YEAR) * 10000 + (rangeEnd[0].get(Calendar.MONTH) + 1) * 100 + rangeEnd[0].get(Calendar.DAY_OF_MONTH);
                }

                int dayCounter = 1;
                for (int r = 0; r < rows; r++) {
                    LinearLayout row = new LinearLayout(context);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(0, dp(1), 0, dp(1));

                    for (int c = 0; c < 7; c++) {
                        int cellIndex = r * 7 + c;
                        if (cellIndex < leadEmpty || dayCounter > maxDaysInMonth) {
                            View empty = new View(context);
                            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(0, dp(36), 1.0f);
                            empty.setLayoutParams(elp);
                            row.addView(empty);
                        } else {
                            final int dayNum = dayCounter;
                            dayCounter++;

                            int thisKey = viewedYear[0] * 10000 + (viewedMonth[0] + 1) * 100 + dayNum;
                            boolean isFuture = (thisKey > todayKey);
                            boolean isStart = (thisKey == startKey);
                            boolean isEnd = (thisKey == endKey);
                            boolean inRange = false;
                            if (!isFuture) {
                                if (startKey != -1 && endKey != -1 && startKey != endKey) {
                                    inRange = (thisKey >= Math.min(startKey, endKey) && thisKey <= Math.max(startKey, endKey));
                                } else if (startKey != -1 && endKey == -1) {
                                    inRange = isStart;
                                }
                            }

                            FrameLayout cell = new FrameLayout(context);
                            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, dp(36), 1.0f);
                            cell.setLayoutParams(clp);

                            if (inRange) {
                                View rangeBand = new View(context);
                                GradientDrawable band = new GradientDrawable();
                                int bandColor = (accentColor & 0x00FFFFFF) | 0x2E000000;
                                band.setColor(bandColor);

                                float cr = dp(16);
                                boolean isRowStart = (c == 0);
                                boolean isRowEnd = (c == 6);

                                float[] radii = new float[8];
                                if (isStart || isRowStart) {
                                    radii[0] = cr; radii[1] = cr;
                                    radii[6] = cr; radii[7] = cr;
                                }
                                if (isEnd || isRowEnd) {
                                    radii[2] = cr; radii[3] = cr;
                                    radii[4] = cr; radii[5] = cr;
                                }
                                band.setCornerRadii(radii);
                                rangeBand.setBackground(band);
                                FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30), Gravity.CENTER);
                                cell.addView(rangeBand, blp);
                            }

                            TextView dayTv = new TextView(context);
                            dayTv.setText(String.valueOf(dayNum));
                            dayTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
                            dayTv.setGravity(Gravity.CENTER);

                            if (isFuture) {
                                dayTv.setTextColor(isDarkTheme() ? 0x30FFFFFF : 0x28000000);
                                dayTv.setTypeface(null, Typeface.NORMAL);
                                cell.setClickable(false);
                                cell.setEnabled(false);
                            } else if (isStart || isEnd) {
                                GradientDrawable selCircle = new GradientDrawable();
                                selCircle.setShape(GradientDrawable.OVAL);
                                selCircle.setColor(accentColor);
                                dayTv.setBackground(selCircle);
                                dayTv.setTextColor(isDarkTheme() ? getMonetColor("system_accent1_900", 0xFF000000) : 0xFFFFFFFF);
                                dayTv.setTypeface(null, Typeface.BOLD);
                            } else {
                                GradientDrawable circleBg = new GradientDrawable();
                                circleBg.setShape(GradientDrawable.OVAL);
                                circleBg.setColor(isDarkTheme() ? 0x14FFFFFF : 0x0A000000);
                                dayTv.setBackground(circleBg);
                                dayTv.setTextColor(primaryText);
                                dayTv.setTypeface(null, Typeface.NORMAL);
                            }

                            FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER);
                            cell.addView(dayTv, dlp);

                            if (!isFuture) {
                                cell.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        performHaptic(v, HapticFeedbackConstants.KEYBOARD_TAP);
                                        Calendar clicked = Calendar.getInstance();
                                        clicked.set(viewedYear[0], viewedMonth[0], dayNum);

                                        if (rangeStart[0] == null) {
                                            rangeStart[0] = clicked;
                                            rangeEnd[0] = null;
                                        } else if (rangeEnd[0] == null) {
                                            if (clicked.before(rangeStart[0])) {
                                                rangeEnd[0] = rangeStart[0];
                                                rangeStart[0] = clicked;
                                            } else {
                                                rangeEnd[0] = clicked;
                                            }
                                        } else {
                                            rangeStart[0] = clicked;
                                            rangeEnd[0] = null;
                                        }
                                        if (renderRef[0] != null) renderRef[0].run();
                                    }
                                });
                            }

                            row.addView(cell);
                        }
                    }
                    calendarGrid.addView(row);
                }
            }
        };

        final Runnable[] renderPresetsRef = new Runnable[1];
        renderPresetsRef[0] = new Runnable() {
            @Override
            public void run() {
                presetsRow.removeAllViews();
                List<DatePresetItem> allPresets = loadSavedDatePresets();

                for (final DatePresetItem pItem : allPresets) {
                    final TextView pChip = new TextView(context);
                    pChip.setText(pItem.name);
                    pChip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    pChip.setTextColor(secondaryText);
                    pChip.setPadding(dp(10), dp(5), dp(10), dp(5));

                    GradientDrawable pBg = new GradientDrawable();
                    pBg.setCornerRadius(dp(8));
                    pBg.setColor(getSurfaceContainerColor());
                    pBg.setStroke(dp(1), isDarkTheme() ? 0x22FFFFFF : 0x14000000);
                    pChip.setBackground(pBg);

                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.setMarginEnd(dp(6));
                    pChip.setLayoutParams(lp);

                    pChip.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            performHaptic(v, HapticFeedbackConstants.KEYBOARD_TAP);
                            Calendar s = Calendar.getInstance();
                            s.set(pItem.startYear, pItem.startMonth, pItem.startDay, 0, 0, 0);
                            Calendar e = Calendar.getInstance();
                            e.set(pItem.endYear, pItem.endMonth, pItem.endDay, 23, 59, 59);
                            rangeStart[0] = s;
                            rangeEnd[0] = e;
                            viewedYear[0] = s.get(Calendar.YEAR);
                            viewedMonth[0] = s.get(Calendar.MONTH);
                            if (renderRef[0] != null) renderRef[0].run();
                        }
                    });

                    pChip.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            performHaptic(v, HapticFeedbackConstants.LONG_PRESS);
                            showDeletePresetPrompt(pItem, new Runnable() {
                                @Override
                                public void run() {
                                    if (renderPresetsRef[0] != null) renderPresetsRef[0].run();
                                }
                            });
                            return true;
                        }
                    });
                    presetsRow.addView(pChip);
                }

                TextView addChip = new TextView(context);
                addChip.setText(tr("Сохранить", "Save"));
                addChip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                addChip.setTextColor(accentColor);
                addChip.setCompoundDrawablesWithIntrinsicBounds(new VectorIconDrawable("add", accentColor, dp(12)), null, null, null);
                addChip.setCompoundDrawablePadding(dp(4));
                addChip.setPadding(dp(10), dp(5), dp(10), dp(5));
                GradientDrawable addBg = new GradientDrawable();
                addBg.setCornerRadius(dp(8));
                addBg.setColor((accentColor & 0x00FFFFFF) | 0x18000000);
                addBg.setStroke(dp(1), (accentColor & 0x00FFFFFF) | 0x40000000);
                addChip.setBackground(addBg);
                addChip.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        performHaptic(v, HapticFeedbackConstants.KEYBOARD_TAP);
                        if (rangeStart[0] == null) return;
                        Calendar s = (Calendar) rangeStart[0].clone();
                        Calendar e = (rangeEnd[0] != null) ? (Calendar) rangeEnd[0].clone() : (Calendar) rangeStart[0].clone();
                        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM", Locale.getDefault());
                        String defName = sdf.format(s.getTime()) + " – " + sdf.format(e.getTime());
                        if (sdf.format(s.getTime()).equals(sdf.format(e.getTime()))) {
                            defName = sdf.format(s.getTime());
                        }
                        showSavePresetPrompt(s, e, defName, new Runnable() {
                            @Override
                            public void run() {
                                if (renderPresetsRef[0] != null) renderPresetsRef[0].run();
                            }
                        });
                    }
                });
                presetsRow.addView(addChip);
            }
        };

        if (renderPresetsRef[0] != null) renderPresetsRef[0].run();

        prevBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                viewedMonth[0]--;
                if (viewedMonth[0] < 0) {
                    viewedMonth[0] = 11;
                    viewedYear[0]--;
                }
                if (renderRef[0] != null) renderRef[0].run();
            }
        });

        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Calendar todayCal = Calendar.getInstance();
                int curYear = todayCal.get(Calendar.YEAR);
                int curMonth = todayCal.get(Calendar.MONTH);
                if (viewedYear[0] > curYear || (viewedYear[0] == curYear && viewedMonth[0] >= curMonth)) {
                    return;
                }
                performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                viewedMonth[0]++;
                if (viewedMonth[0] > 11) {
                    viewedMonth[0] = 0;
                    viewedYear[0]++;
                }
                if (renderRef[0] != null) renderRef[0].run();
            }
        });

        applyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performHaptic(v, HapticFeedbackConstants.KEYBOARD_TAP);
                if (rangeStart[0] != null) {
                    final Calendar nowCal = Calendar.getInstance();
                    final Calendar cStart = (Calendar) rangeStart[0].clone();
                    if (cStart.after(nowCal)) {
                        cStart.setTimeInMillis(nowCal.getTimeInMillis());
                    }
                    cStart.set(Calendar.HOUR_OF_DAY, 0);
                    cStart.set(Calendar.MINUTE, 0);
                    cStart.set(Calendar.SECOND, 0);

                    final Calendar cEnd = (rangeEnd[0] != null) ? (Calendar) rangeEnd[0].clone() : (Calendar) rangeStart[0].clone();
                    if (cEnd.after(nowCal)) {
                        cEnd.setTimeInMillis(nowCal.getTimeInMillis());
                    }
                    cEnd.set(Calendar.HOUR_OF_DAY, 23);
                    cEnd.set(Calendar.MINUTE, 59);
                    cEnd.set(Calendar.SECOND, 59);

                    customMinDate = cStart.getTimeInMillis() / 1000L;
                    customMaxDate = cEnd.getTimeInMillis() / 1000L;
                    selectedDatePreset = "custom";
                    SimpleDateFormat sdf = new SimpleDateFormat("dd.MM", Locale.getDefault());
                    TextView customChip = dateChipViews.get("custom");
                    if (customChip != null) {
                        if (sdf.format(cStart.getTime()).equals(sdf.format(cEnd.getTime()))) {
                            customChip.setText(sdf.format(cStart.getTime()));
                        } else {
                            customChip.setText(sdf.format(cStart.getTime()) + " – " + sdf.format(cEnd.getTime()));
                        }
                    }
                    updateDateStyles();
                    triggerSearchDelayed();
                    dismissDateDialog(dateDialog, rootOverlay, card);
                }
            }
        });

        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                rangeStart[0] = null;
                rangeEnd[0] = null;
                customMinDate = 0;
                customMaxDate = 0;
                selectedDatePreset = "all";
                TextView customChip = dateChipViews.get("custom");
                if (customChip != null) {
                    customChip.setText(tr("Период", "Custom range"));
                }
                updateDateStyles();
                triggerSearchDelayed();
                dismissDateDialog(dateDialog, rootOverlay, card);
            }
        });

        rootOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismissDateDialog(dateDialog, rootOverlay, card);
            }
        });

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {}
        });

        rootOverlay.addView(card);
        dateDialog.setContentView(rootOverlay);

        if (renderRef[0] != null) renderRef[0].run();

        rootOverlay.setAlpha(0f);
        rootOverlay.animate().alpha(1f).setDuration(220).setInterpolator(EASE_OUT_QUINT).start();
        card.setAlpha(0f);
        card.setScaleX(0.92f);
        card.setScaleY(0.92f);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(EASE_OUT_QUINT).start();

        setupDialogWindow(dateDialog);
        dateDialog.show();
    }

    private void dismissDateDialog(final Dialog dateDialog, final View rootOverlay, final View card) {
        if (rootOverlay == null || card == null || dateDialog == null) return;
        rootOverlay.animate().alpha(0f).setDuration(160).setInterpolator(EASE_IN_QUINT).start();
        card.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(160).setInterpolator(EASE_IN_QUINT).withEndAction(new Runnable() {
            @Override
            public void run() {
                dateDialog.dismiss();
            }
        }).start();
    }

    private void resetAllFilters() {
        selectedFilter = "all";
        selectedVideoSort = "date";
        selectedDocExt = "all";
        selectedDocSort = "date";
        onlyMineSelected = false;
        selectedDatePreset = "all";
        customMinDate = 0;
        customMaxDate = 0;
        filterByUserId = 0;
        filterByUserAccessHash = 0;
        selectedUserObject = null;
        filterByUserName = null;
        updateFilterStyles();
        updateDateStyles();
        updateVideoSortStyles();
        updateDocSubStyles();
        updateOnlyMineChipStyle();
        updateUserFilterChip();
        TextView customChip = dateChipViews.get("custom");
        if (customChip != null) {
            customChip.setText(tr("Период", "Custom range"));
        }
        triggerSearchDelayed();
    }

    private void updateUserFilterChip() {
        userFilterChipContainer.removeAllViews();
        if (filterByUserId == 0 || filterByUserName == null) {
            userFilterChipContainer.setVisibility(View.GONE);
            updateResetButtonVisibility();
            return;
        }
        updateResetButtonVisibility();
        userFilterChipContainer.setVisibility(View.VISIBLE);
        int accentColor = getAccentColor();

        LinearLayout chip = new LinearLayout(context);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(10), dp(6), dp(6), dp(6));
        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setCornerRadius(dp(16));
        chipBg.setColor((accentColor & 0x00FFFFFF) | 0x25000000);
        chip.setBackground(chipBg);

        TextView label = new TextView(context);
        label.setText(filterByUserName);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setTextColor(accentColor);
        label.setTypeface(null, Typeface.BOLD);
        label.setCompoundDrawablesWithIntrinsicBounds(new VectorIconDrawable("user", accentColor, dp(14)), null, null, null);
        label.setCompoundDrawablePadding(dp(6));
        chip.addView(label);

        chip.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                performHaptic(v, HapticFeedbackConstants.LONG_PRESS);
                int acc = 0;
                try {
                    Class<?> uc = Class.forName("org.telegram.messenger.UserConfig");
                    acc = uc.getField("selectedAccount").getInt(null);
                } catch (Throwable ignored) {}
                showUserActionMenu(acc, filterByUserId, selectedUserObject, filterByUserName, filterByUserName, filterByUserAccessHash);
                return true;
            }
        });

        ImageView removeBtn = new ImageView(context);
        removeBtn.setImageDrawable(new VectorIconDrawable("close", accentColor, dp(12)));
        removeBtn.setPadding(dp(6), 0, dp(4), 0);
        removeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                filterByUserId = 0;
                filterByUserAccessHash = 0;
                selectedUserObject = null;
                filterByUserName = null;
                onlyMineSelected = false;
                updateOnlyMineChipStyle();
                updateUserFilterChip();
                triggerSearchDelayed();
            }
        });
        chip.addView(removeBtn);

        chip.setAlpha(0f);
        chip.setScaleX(0.85f);
        chip.setScaleY(0.85f);
        chip.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220)
                .setInterpolator(EASE_OUT_QUINT)
                .withLayer()
                .start();

        userFilterChipContainer.addView(chip);
    }

    private void triggerSearchDelayed() {
        if (searchRunnable != null) {
            mainHandler.removeCallbacks(searchRunnable);
        }
        searchRunnable = new Runnable() {
            @Override
            public void run() {
                executeSearch();
            }
        };
        mainHandler.postDelayed(searchRunnable, 300);
    }

    private long calculateMinDate() {
        if ("custom".equals(selectedDatePreset)) {
            return customMinDate;
        } else if ("today".equals(selectedDatePreset)) {
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            return c.getTimeInMillis() / 1000L;
        } else if ("yesterday".equals(selectedDatePreset)) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_YEAR, -1);
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            return c.getTimeInMillis() / 1000L;
        } else if ("week".equals(selectedDatePreset)) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_YEAR, -7);
            return c.getTimeInMillis() / 1000L;
        } else if ("month".equals(selectedDatePreset)) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_YEAR, -30);
            return c.getTimeInMillis() / 1000L;
        }
        return 0;
    }

    private long calculateMaxDate() {
        if ("custom".equals(selectedDatePreset)) {
            return customMaxDate;
        } else if ("today".equals(selectedDatePreset) || "week".equals(selectedDatePreset) || "month".equals(selectedDatePreset)) {
            return System.currentTimeMillis() / 1000L;
        } else if ("yesterday".equals(selectedDatePreset)) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_YEAR, -1);
            c.set(Calendar.HOUR_OF_DAY, 23);
            c.set(Calendar.MINUTE, 59);
            c.set(Calendar.SECOND, 59);
            return c.getTimeInMillis() / 1000L;
        }
        return 0;
    }

    private void checkLoadMore(int scrollY) {
        if (isLoadingMore || !hasMoreResults || currentToken == 0) return;
        if (!hasActiveSearchCriteria()) return;
        if (contentScroll == null || resultsContainer == null) return;
        int visibleHeight = contentScroll.getHeight();
        int contentHeight = resultsContainer.getHeight();
        if (contentHeight > 0 && visibleHeight > 0 && scrollY + visibleHeight >= contentHeight - dp(350)) {
            loadNextPage();
        }
    }

    private void loadNextPage() {
        if (isLoadingMore || !hasMoreResults || currentToken == 0) return;
        if (!hasActiveSearchCriteria()) return;
        isLoadingMore = true;

        if (loadMoreProgressBar == null) {
            loadMoreProgressBar = new ProgressBar(context);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(28), dp(28));
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            lp.topMargin = dp(12);
            lp.bottomMargin = dp(20);
            loadMoreProgressBar.setLayoutParams(lp);
        }
        if (loadMoreProgressBar.getParent() == null) {
            resultsContainer.addView(loadMoreProgressBar);
        }
        loadMoreProgressBar.setVisibility(View.VISIBLE);

        final long minDate = calculateMinDate();
        final long maxDate = calculateMaxDate();
        performServerSearch(currentToken, minDate, maxDate, true);
    }

    private void executeSearch() {
        if (!hasActiveSearchCriteria()) {
            renderSearchHistory();
            return;
        }

        isLoadingMore = false;
        hasMoreResults = true;
        lastOffsetId = 0;
        lastOffsetPeer = null;
        lastOffsetRate = 0;
        totalLoadedCount = 0;
        loadedMessageKeys.clear();
        if (loadMoreProgressBar != null && loadMoreProgressBar.getParent() != null) {
            ((ViewGroup) loadMoreProgressBar.getParent()).removeView(loadMoreProgressBar);
        }
        loadMoreProgressBar = null;

        final int token = ++currentToken;
        searchProgressBar.setVisibility(View.GONE);
        statsBadge.setVisibility(View.GONE);
        showSkeletonLoading();

        long minDate = calculateMinDate();
        long maxDate = calculateMaxDate();

        performServerSearch(token, minDate, maxDate, false);
    }

    private void showSkeletonLoading() {
        if (skeletonShimmerAnim != null) {
            skeletonShimmerAnim.cancel();
            skeletonShimmerAnim = null;
        }
        resultsContainer.removeAllViews();
        statsBadge.setVisibility(View.GONE);
        int cardBg = getSurfaceContainerColor();
        int shimmerColor = isDarkTheme() ? 0x18FFFFFF : 0x10000000;

        final List<View> skeletonCards = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            final LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardLp.setMargins(0, 0, 0, dp(6));
            card.setLayoutParams(cardLp);

            GradientDrawable cardShape = new GradientDrawable();
            cardShape.setColor(cardBg);
            cardShape.setCornerRadius(dp(16));
            cardShape.setStroke(dp(1), getOutlineVariantColor());
            card.setBackground(cardShape);

            View avatarSkel = new View(context);
            GradientDrawable avShape = new GradientDrawable();
            avShape.setShape(GradientDrawable.OVAL);
            avShape.setColor(shimmerColor);
            avatarSkel.setBackground(avShape);
            LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(dp(40), dp(40));
            avLp.rightMargin = dp(12);
            avatarSkel.setLayoutParams(avLp);
            card.addView(avatarSkel);

            LinearLayout contentCol = new LinearLayout(context);
            contentCol.setOrientation(LinearLayout.VERTICAL);
            contentCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

            View titleSkel = new View(context);
            GradientDrawable titleShape = new GradientDrawable();
            titleShape.setCornerRadius(dp(4));
            titleShape.setColor(shimmerColor);
            titleSkel.setBackground(titleShape);
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(dp(120), dp(12));
            tLp.bottomMargin = dp(8);
            titleSkel.setLayoutParams(tLp);
            contentCol.addView(titleSkel);

            View bodySkel = new View(context);
            GradientDrawable bodyShape = new GradientDrawable();
            bodyShape.setCornerRadius(dp(4));
            bodyShape.setColor(shimmerColor);
            bodySkel.setBackground(bodyShape);
            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(dp(210), dp(10));
            bLp.bottomMargin = dp(4);
            bodySkel.setLayoutParams(bLp);
            contentCol.addView(bodySkel);

            card.addView(contentCol);
            card.setAlpha(0.32f);
            skeletonCards.add(card);
            resultsContainer.addView(card);
        }

        skeletonShimmerAnim = ValueAnimator.ofFloat(0.3f, 0.85f);
        skeletonShimmerAnim.setDuration(750);
        skeletonShimmerAnim.setRepeatMode(ValueAnimator.REVERSE);
        skeletonShimmerAnim.setRepeatCount(ValueAnimator.INFINITE);
        skeletonShimmerAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        skeletonShimmerAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float a = (Float) animation.getAnimatedValue();
                for (View c : skeletonCards) {
                    c.setAlpha(a);
                }
            }
        });
        skeletonShimmerAnim.start();
    }

    private void initEmptyStateView() {
        emptyStateContainer = new LinearLayout(context);
        emptyStateContainer.setOrientation(LinearLayout.VERTICAL);
        emptyStateContainer.setGravity(Gravity.CENTER);
        emptyStateContainer.setPadding(dp(20), dp(44), dp(20), dp(40));

        FrameLayout iconFrame = new FrameLayout(context);
        int circleSize = dp(64);
        LinearLayout.LayoutParams ifLp = new LinearLayout.LayoutParams(circleSize, circleSize);
        ifLp.gravity = Gravity.CENTER_HORIZONTAL;
        ifLp.bottomMargin = dp(16);
        iconFrame.setLayoutParams(ifLp);

        GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        circleBg.setColor(withAlpha(getAccentColor(), 0.12f));
        iconFrame.setBackground(circleBg);

        ImageView iconView = new ImageView(context);
        iconView.setImageDrawable(new VectorIconDrawable("search", getAccentColor(), dp(28)));
        FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams(dp(28), dp(28));
        ivLp.gravity = Gravity.CENTER;
        iconView.setLayoutParams(ivLp);
        iconFrame.addView(iconView);
        emptyStateContainer.addView(iconFrame);

        emptyTitleView = new TextView(context);
        emptyTitleView.setText(tr("Ничего не найдено", "No results found"));
        emptyTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f);
        emptyTitleView.setTextColor(getPrimaryTextColor());
        emptyTitleView.setTypeface(null, Typeface.BOLD);
        emptyTitleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.bottomMargin = dp(6);
        emptyTitleView.setLayoutParams(titleLp);
        emptyStateContainer.addView(emptyTitleView);

        emptySubtitleView = new TextView(context);
        emptySubtitleView.setText(tr("Попробуйте изменить поисковый запрос или фильтры", "Try changing your search query or filters"));
        emptySubtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        emptySubtitleView.setTextColor(getSecondaryTextColor());
        emptySubtitleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.bottomMargin = dp(18);
        emptySubtitleView.setLayoutParams(subLp);
        emptyStateContainer.addView(emptySubtitleView);

        emptyResetBtn = new TextView(context);
        emptyResetBtn.setText(tr("Сбросить фильтры", "Reset filters"));
        emptyResetBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        emptyResetBtn.setTextColor(getOnSecondaryContainerColor());
        emptyResetBtn.setTypeface(null, Typeface.BOLD);
        emptyResetBtn.setGravity(Gravity.CENTER);
        emptyResetBtn.setPadding(dp(16), dp(8), dp(16), dp(8));
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(getSecondaryContainerColor());
        btnBg.setCornerRadius(dp(12));
        btnBg.setStroke(dp(1), getOutlineVariantColor());
        emptyResetBtn.setBackground(btnBg);
        emptyResetBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                animateChipClick(v);
                resetAllFilters();
            }
        });
        emptyStateContainer.addView(emptyResetBtn);
    }

    private void showEmptyState(String title, String subtitle, boolean showReset) {
        if (skeletonShimmerAnim != null) {
            skeletonShimmerAnim.cancel();
            skeletonShimmerAnim = null;
        }
        resultsContainer.removeAllViews();
        statsBadge.setVisibility(View.GONE);
        if (emptyStateContainer == null) {
            initEmptyStateView();
        }
        emptyTitleView.setText(title != null ? title : tr("Ничего не найдено", "No results found"));
        emptySubtitleView.setText(subtitle != null ? subtitle : tr("Попробуйте изменить поисковый запрос или фильтры", "Try changing your search query or filters"));
        emptyResetBtn.setVisibility(showReset ? View.VISIBLE : View.GONE);
        if (emptyStateContainer.getParent() != null) {
            ((ViewGroup) emptyStateContainer.getParent()).removeView(emptyStateContainer);
        }
        resultsContainer.addView(emptyStateContainer);
    }

    private Object createInputPeer(int currentAccount, long targetPeerId, long accessHash, Object userOrChat) {
        try {
            if (targetPeerId == 0) {
                return Class.forName("org.telegram.tgnet.TLRPC$TL_inputPeerEmpty").newInstance();
            }
            Class<?> mcClass = Class.forName("org.telegram.messenger.MessagesController");
            Method getInstance = mcClass.getMethod("getInstance", int.class);
            Object mcInst = getInstance.invoke(null, currentAccount);

            if (userOrChat != null) {
                try {
                    Method getInputPeer = mcClass.getMethod("getInputPeer", Class.forName("org.telegram.tgnet.TLObject"));
                    Object ip = getInputPeer.invoke(mcInst, userOrChat);
                    if (ip != null) return ip;
                } catch (Throwable ignored) {}
            }

            try {
                Method getInputPeerLong = mcClass.getMethod("getInputPeer", long.class);
                Object ip = getInputPeerLong.invoke(mcInst, targetPeerId);
                if (ip != null) return ip;
            } catch (Throwable ignored) {}

            if (targetPeerId > 0) {
                try {
                    Method getUser = mcClass.getMethod("getUser", Long.class);
                    Object user = getUser.invoke(mcInst, targetPeerId);
                    if (user != null) {
                        Method getInputPeer = mcClass.getMethod("getInputPeer", Class.forName("org.telegram.tgnet.TLObject"));
                        Object ip = getInputPeer.invoke(mcInst, user);
                        if (ip != null) return ip;
                    }
                } catch (Throwable ignored) {}

                Class<?> ipuClass = Class.forName("org.telegram.tgnet.TLRPC$TL_inputPeerUser");
                Object ipu = ipuClass.newInstance();
                ipuClass.getField("user_id").setLong(ipu, targetPeerId);
                ipuClass.getField("access_hash").setLong(ipu, accessHash);
                return ipu;
            } else {
                long absId = -targetPeerId;
                try {
                    Method getChat = mcClass.getMethod("getChat", Long.class);
                    Object chat = getChat.invoke(mcInst, absId);
                    if (chat != null) {
                        Method getInputPeer = mcClass.getMethod("getInputPeer", Class.forName("org.telegram.tgnet.TLObject"));
                        Object ip = getInputPeer.invoke(mcInst, chat);
                        if (ip != null) return ip;

                        boolean isChannel = false;
                        try {
                            Class<?> coClass = Class.forName("org.telegram.messenger.ChatObject");
                            Method isChannelM = coClass.getMethod("isChannel", Class.forName("org.telegram.tgnet.TLRPC$Chat"));
                            isChannel = (boolean) isChannelM.invoke(null, chat);
                        } catch (Throwable t) {
                            isChannel = chat.getClass().getSimpleName().contains("Channel");
                        }

                        if (isChannel) {
                            Class<?> ipcClass = Class.forName("org.telegram.tgnet.TLRPC$TL_inputPeerChannel");
                            Object ipc = ipcClass.newInstance();
                            ipcClass.getField("channel_id").setLong(ipc, chat.getClass().getField("id").getLong(chat));
                            ipcClass.getField("access_hash").setLong(ipc, chat.getClass().getField("access_hash").getLong(chat));
                            return ipc;
                        } else {
                            Class<?> ipcClass = Class.forName("org.telegram.tgnet.TLRPC$TL_inputPeerChat");
                            Object ipc = ipcClass.newInstance();
                            ipcClass.getField("chat_id").setLong(ipc, chat.getClass().getField("id").getLong(chat));
                            return ipc;
                        }
                    }
                } catch (Throwable ignored) {}

                if (absId >= 1000000000L) {
                    Class<?> ipcClass = Class.forName("org.telegram.tgnet.TLRPC$TL_inputPeerChannel");
                    Object ipc = ipcClass.newInstance();
                    long chId = absId >= 1000000000000L ? (absId - 1000000000000L) : absId;
                    ipcClass.getField("channel_id").setLong(ipc, chId);
                    ipcClass.getField("access_hash").setLong(ipc, accessHash);
                    return ipc;
                } else {
                    Class<?> ipcClass = Class.forName("org.telegram.tgnet.TLRPC$TL_inputPeerChat");
                    Object ipc = ipcClass.newInstance();
                    ipcClass.getField("chat_id").setLong(ipc, absId);
                    return ipc;
                }
            }
        } catch (Throwable ignored) {
            try {
                return Class.forName("org.telegram.tgnet.TLRPC$TL_inputPeerEmpty").newInstance();
            } catch (Throwable t) {
                return null;
            }
        }
    }

    private final List<Integer> activeReqIds = new ArrayList<>();

    private void cancelActiveRequests(int currentAccount) {
        synchronized (activeReqIds) {
            if (activeReqIds.isEmpty()) return;
            try {
                Class<?> cmClass = Class.forName("org.telegram.tgnet.ConnectionsManager");
                Method cmGetInstance = cmClass.getMethod("getInstance", int.class);
                Object cmInst = cmGetInstance.invoke(null, currentAccount);
                Method cancelMethod = cmClass.getMethod("cancelRequest", int.class, boolean.class);
                for (Integer reqId : activeReqIds) {
                    if (reqId != null && reqId != 0) {
                        cancelMethod.invoke(cmInst, reqId, true);
                    }
                }
            } catch (Throwable ignored) {}
            activeReqIds.clear();
        }
    }

    private void performServerSearch(final int token, final long minDate, final long maxDate, final boolean isLoadMore) {
        final int currentAccount;
        try {
            Class<?> uc = Class.forName("org.telegram.messenger.UserConfig");
            Field f = uc.getField("selectedAccount");
            currentAccount = f.getInt(null);
        } catch (Throwable ignored) {
            return;
        }

        if (!isLoadMore) {
            cancelActiveRequests(currentAccount);
        }

        if ("chat".equals(scope) && peerId != 0) {
            performChatSearch(token, currentAccount, minDate, maxDate, isLoadMore);
        } else {
            performGlobalSearch(token, currentAccount, minDate, maxDate, isLoadMore);
        }
    }

    private void performChatSearch(final int token, final int currentAccount, final long minDate, final long maxDate, final boolean isLoadMore) {
        try {
            final List<Object> mergedMsgs = Collections.synchronizedList(new ArrayList<>());
            final List<Object> mergedUsers = Collections.synchronizedList(new ArrayList<>());
            final List<Object> mergedChats = Collections.synchronizedList(new ArrayList<>());

            final boolean isDocSearch = "document".equals(selectedFilter);
            final int totalRequests = isDocSearch && query != null && !query.trim().isEmpty() ? 2 : 1;
            final AtomicInteger finishedRequests = new AtomicInteger(0);

            final Runnable onComplete = new Runnable() {
                @Override
                public void run() {
                    if (finishedRequests.incrementAndGet() >= totalRequests) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (token == currentToken) {
                                    try {
                                        Class<?> resClass = Class.forName("org.telegram.tgnet.TLRPC$TL_messages_messagesSlice");
                                        Object syntheticResponse = resClass.newInstance();
                                        resClass.getField("messages").set(syntheticResponse, new ArrayList<>(mergedMsgs));
                                        resClass.getField("users").set(syntheticResponse, new ArrayList<>(mergedUsers));
                                        resClass.getField("chats").set(syntheticResponse, new ArrayList<>(mergedChats));
                                        resClass.getField("count").set(syntheticResponse, mergedMsgs.size());
                                        renderResults(currentAccount, syntheticResponse, null, isLoadMore);
                                    } catch (Throwable t) {
                                        renderResults(currentAccount, null, null, isLoadMore);
                                    }
                                }
                            }
                        });
                    }
                }
            };

            Class<?> reqClass = Class.forName("org.telegram.tgnet.TLRPC$TL_messages_search");
            Object inputPeer = createInputPeer(currentAccount, peerId, 0, null);
            Object filterObj = createFilter(selectedFilter);

            Class<?> cmClass = Class.forName("org.telegram.tgnet.ConnectionsManager");
            Method cmGetInstance = cmClass.getMethod("getInstance", int.class);
            final Object cmInst = cmGetInstance.invoke(null, currentAccount);
            final Class<?> reqDelClass = Class.forName("org.telegram.tgnet.RequestDelegate");
            final Method sendReq = cmClass.getMethod("sendRequest", Class.forName("org.telegram.tgnet.TLObject"), reqDelClass);

            final int scanLimit = 100;
            final boolean needsScan = (filterByUserId != 0 || !"all".equals(selectedFilter));
            final int maxScans = needsScan ? 8 : 1;
            final AtomicInteger scanRequests = new AtomicInteger(0);
            final AtomicInteger accumulatedMatches = new AtomicInteger(0);
            final int[] currentOffsetId = new int[]{isLoadMore ? lastOffsetId : 0};

            final Runnable[] sendNextScan = new Runnable[1];
            sendNextScan[0] = new Runnable() {
                @Override
                public void run() {
                    try {
                        if (token != currentToken) return;
                        final int currentScan = scanRequests.incrementAndGet();
                        Object req = reqClass.newInstance();
                        reqClass.getField("peer").set(req, inputPeer);
                        reqClass.getField("q").set(req, query != null ? query : "");
                        reqClass.getField("filter").set(req, filterObj);
                        reqClass.getField("min_date").set(req, (int) minDate);
                        reqClass.getField("max_date").set(req, (int) maxDate);
                        reqClass.getField("offset_id").set(req, currentOffsetId[0]);
                        reqClass.getField("add_offset").set(req, 0);
                        reqClass.getField("limit").set(req, scanLimit);
                        reqClass.getField("max_id").set(req, 0);
                        reqClass.getField("min_id").set(req, 0);
                        reqClass.getField("hash").set(req, 0L);

                        if (peerId < 0 && filterByUserId != 0 && "all".equals(selectedFilter)) {
                            try {
                                Object fromPeer = createInputPeer(currentAccount, filterByUserId, filterByUserAccessHash, selectedUserObject);
                                reqClass.getField("from_id").set(req, fromPeer);
                                int flags = reqClass.getField("flags").getInt(req);
                                flags |= 1;
                                reqClass.getField("flags").set(req, flags);
                            } catch (Throwable ignored) {}
                        }

                        Object delegate = Proxy.newProxyInstance(
                                reqDelClass.getClassLoader(),
                                new Class<?>[]{reqDelClass},
                                new java.lang.reflect.InvocationHandler() {
                                    @Override
                                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                                        if ("run".equals(method.getName()) && args != null && args.length >= 2) {
                                            final Object response = args[0];
                                            int rawCount = 0;
                                            int minId = 0;
                                            if (response != null) {
                                                try {
                                                    Field msgsF = response.getClass().getField("messages");
                                                    List<?> msgs = (List<?>) msgsF.get(response);
                                                    if (msgs != null) {
                                                        rawCount = msgs.size();
                                                        mergedMsgs.addAll(msgs);
                                                        long myUid = getMyUserId(currentAccount);
                                                        for (Object m : msgs) {
                                                            try {
                                                                int mid = m.getClass().getField("id").getInt(m);
                                                                if (minId == 0 || (mid > 0 && mid < minId)) {
                                                                    minId = mid;
                                                                }
                                                                if (matchesSelectedFilter(m, selectedFilter, selectedDocExt)) {
                                                                    boolean isOut = false;
                                                                    try { isOut = m.getClass().getField("out").getBoolean(m); } catch (Throwable ignored) {}
                                                                    long sPeer = 0;
                                                                    try {
                                                                        Object fObj = m.getClass().getField("from_id").get(m);
                                                                        if (fObj != null) sPeer = extractPeerId(fObj);
                                                                    } catch (Throwable ignored) {}
                                                                    if (isOut) sPeer = myUid;
                                                                    else if (sPeer == 0 && peerId > 0) sPeer = peerId;

                                                                    if (filterByUserId == 0 || (isOut ? filterByUserId == myUid : sPeer == filterByUserId)) {
                                                                        accumulatedMatches.incrementAndGet();
                                                                    }
                                                                }
                                                            } catch (Throwable ignored) {}
                                                        }
                                                    }
                                                    Field usersF = response.getClass().getField("users");
                                                    List<?> users = (List<?>) usersF.get(response);
                                                    if (users != null) mergedUsers.addAll(users);
                                                    Field chatsF = response.getClass().getField("chats");
                                                    List<?> chats = (List<?>) chatsF.get(response);
                                                    if (chats != null) mergedChats.addAll(chats);
                                                } catch (Throwable ignored) {}
                                            }

                                            if (token == currentToken && rawCount >= scanLimit && minId > 0 && accumulatedMatches.get() < 15 && currentScan < maxScans) {
                                                currentOffsetId[0] = minId;
                                                sendNextScan[0].run();
                                            } else {
                                                onComplete.run();
                                            }
                                        }
                                        return null;
                                    }
                                }
                        );

                        Object reqId = sendReq.invoke(cmInst, req, delegate);
                        if (reqId instanceof Integer && (Integer) reqId != 0) {
                            synchronized (activeReqIds) {
                                activeReqIds.add((Integer) reqId);
                            }
                        }
                    } catch (Throwable t) {
                        onComplete.run();
                    }
                }
            };
            sendNextScan[0].run();

            if (isDocSearch && query != null && !query.trim().isEmpty()) {
                Object docFetchReq = reqClass.newInstance();
                reqClass.getField("peer").set(docFetchReq, inputPeer);
                reqClass.getField("q").set(docFetchReq, "");
                reqClass.getField("filter").set(docFetchReq, filterObj);
                reqClass.getField("min_date").set(docFetchReq, (int) minDate);
                reqClass.getField("max_date").set(docFetchReq, (int) maxDate);
                reqClass.getField("offset_id").set(docFetchReq, isLoadMore ? lastOffsetId : 0);
                reqClass.getField("add_offset").set(docFetchReq, 0);
                reqClass.getField("limit").set(docFetchReq, scanLimit);
                reqClass.getField("max_id").set(docFetchReq, 0);
                reqClass.getField("min_id").set(docFetchReq, 0);
                reqClass.getField("hash").set(docFetchReq, 0L);

                Object docFetchDelegate = Proxy.newProxyInstance(
                        reqDelClass.getClassLoader(),
                        new Class<?>[]{reqDelClass},
                        new java.lang.reflect.InvocationHandler() {
                            @Override
                            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                                if ("run".equals(method.getName()) && args != null && args.length >= 2) {
                                    Object response = args[0];
                                    if (response != null) {
                                        try {
                                            Field msgsF = response.getClass().getField("messages");
                                            List<?> msgs = (List<?>) msgsF.get(response);
                                            if (msgs != null) mergedMsgs.addAll(msgs);
                                            Field usersF = response.getClass().getField("users");
                                            List<?> users = (List<?>) usersF.get(response);
                                            if (users != null) mergedUsers.addAll(users);
                                            Field chatsF = response.getClass().getField("chats");
                                            List<?> chats = (List<?>) chatsF.get(response);
                                            if (chats != null) mergedChats.addAll(chats);
                                        } catch (Throwable ignored) {}
                                    }
                                    onComplete.run();
                                }
                                return null;
                            }
                        }
                );

                Object docReqId = sendReq.invoke(cmInst, docFetchReq, docFetchDelegate);
                if (docReqId instanceof Integer && (Integer) docReqId != 0) {
                    synchronized (activeReqIds) {
                        activeReqIds.add((Integer) docReqId);
                    }
                }
            }
        } catch (Throwable ex) {
            performFallbackEmptySearch(token, minDate, maxDate, peerId, filterByUserId, filterByUserAccessHash, selectedUserObject, isLoadMore);
        }
    }

    private void performGlobalSearch(final int token, final int currentAccount, final long minDate, final long maxDate, final boolean isLoadMore) {
        try {
            final List<Object> mergedMsgs = Collections.synchronizedList(new ArrayList<>());
            final List<Object> mergedUsers = Collections.synchronizedList(new ArrayList<>());
            final List<Object> mergedChats = Collections.synchronizedList(new ArrayList<>());

            List<Long> targetDialogIds = new ArrayList<>();
            try {
                Class<?> mcClass = Class.forName("org.telegram.messenger.MessagesController");
                Method getInstance = mcClass.getMethod("getInstance", int.class);
                Object mcInst = getInstance.invoke(null, currentAccount);

                Field allDialogsF = mcClass.getField("allDialogs");
                List<?> dialogsList = (List<?>) allDialogsF.get(mcInst);
                if (dialogsList != null) {
                    for (Object d : dialogsList) {
                        if (d != null) {
                            Field idF = d.getClass().getField("id");
                            long did = idF.getLong(d);
                            if (did != 0 && !targetDialogIds.contains(did)) {
                                targetDialogIds.add(did);
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}

            List<Long> searchPeers = new ArrayList<>();
            if (filterByUserId != 0) {
                if (!searchPeers.contains(filterByUserId)) searchPeers.add(filterByUserId);
                for (Long did : targetDialogIds) {
                    if (searchPeers.size() >= 20) break;
                    if (!searchPeers.contains(did)) searchPeers.add(did);
                }
            }

            final int totalRequests = 1 + searchPeers.size();
            final AtomicInteger finishedRequests = new AtomicInteger(0);

            final Runnable onComplete = new Runnable() {
                @Override
                public void run() {
                    if (finishedRequests.incrementAndGet() >= totalRequests) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (token == currentToken) {
                                    try {
                                        Class<?> resClass = Class.forName("org.telegram.tgnet.TLRPC$TL_messages_messagesSlice");
                                        Object syntheticResponse = resClass.newInstance();
                                        resClass.getField("messages").set(syntheticResponse, new ArrayList<>(mergedMsgs));
                                        resClass.getField("users").set(syntheticResponse, new ArrayList<>(mergedUsers));
                                        resClass.getField("chats").set(syntheticResponse, new ArrayList<>(mergedChats));
                                        resClass.getField("count").set(syntheticResponse, mergedMsgs.size());
                                        renderResults(currentAccount, syntheticResponse, null, isLoadMore);
                                    } catch (Throwable t) {
                                        renderResults(currentAccount, null, null, isLoadMore);
                                    }
                                }
                            }
                        });
                    }
                }
            };

            Class<?> reqClass = Class.forName("org.telegram.tgnet.TLRPC$TL_messages_searchGlobal");
            Object req = reqClass.newInstance();

            Object filterObj = createFilter(selectedFilter);

            Object inputPeer = createInputPeer(currentAccount, 0L, 0, null);

            reqClass.getField("folder_id").set(req, 0);
            reqClass.getField("q").set(req, query != null ? query : "");
            reqClass.getField("filter").set(req, filterObj);
            reqClass.getField("min_date").set(req, (int) minDate);
            reqClass.getField("max_date").set(req, (int) maxDate);
            reqClass.getField("offset_rate").set(req, isLoadMore ? lastOffsetRate : 0);
            reqClass.getField("offset_peer").set(req, isLoadMore && lastOffsetPeer != null ? lastOffsetPeer : inputPeer);
            reqClass.getField("offset_id").set(req, isLoadMore ? lastOffsetId : 0);
            reqClass.getField("limit").set(req, 50);

            Class<?> cmClass = Class.forName("org.telegram.tgnet.ConnectionsManager");
            Method cmGetInstance = cmClass.getMethod("getInstance", int.class);
            Object cmInst = cmGetInstance.invoke(null, currentAccount);

            Class<?> reqDelClass = Class.forName("org.telegram.tgnet.RequestDelegate");

            Object delegate = Proxy.newProxyInstance(
                    reqDelClass.getClassLoader(),
                    new Class<?>[]{reqDelClass},
                    new java.lang.reflect.InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            if ("run".equals(method.getName()) && args != null && args.length >= 2) {
                                Object response = args[0];
                                if (response != null) {
                                    try {
                                        try {
                                            Field rateF = response.getClass().getField("rate");
                                            lastOffsetRate = rateF.getInt(response);
                                        } catch (Throwable ignoredRate) {
                                            try {
                                                Field nextRateF = response.getClass().getField("next_rate");
                                                lastOffsetRate = nextRateF.getInt(response);
                                            } catch (Throwable ignoredNextRate) {}
                                        }

                                        Field msgsF = response.getClass().getField("messages");
                                        List<?> msgs = (List<?>) msgsF.get(response);
                                        if (msgs != null) {
                                            mergedMsgs.addAll(msgs);
                                            if (!msgs.isEmpty()) {
                                                Object lastM = msgs.get(msgs.size() - 1);
                                                try {
                                                    lastOffsetId = lastM.getClass().getField("id").getInt(lastM);
                                                    Object pObj = lastM.getClass().getField("peer_id").get(lastM);
                                                    long pId = extractPeerId(pObj);
                                                    if (pId != 0) {
                                                        lastOffsetPeer = createInputPeer(currentAccount, pId, 0, null);
                                                    }
                                                } catch (Throwable ignoredLast) {}
                                            }
                                        }
                                        Field usersF = response.getClass().getField("users");
                                        List<?> users = (List<?>) usersF.get(response);
                                        if (users != null) mergedUsers.addAll(users);
                                        Field chatsF = response.getClass().getField("chats");
                                        List<?> chats = (List<?>) chatsF.get(response);
                                        if (chats != null) mergedChats.addAll(chats);
                                    } catch (Throwable ignored) {}
                                }
                                onComplete.run();
                            }
                            return null;
                        }
                    }
            );

            Method sendReq = cmClass.getMethod("sendRequest", Class.forName("org.telegram.tgnet.TLObject"), reqDelClass);
            Object gReqId = sendReq.invoke(cmInst, req, delegate);
            if (gReqId instanceof Integer && (Integer) gReqId != 0) {
                synchronized (activeReqIds) {
                    activeReqIds.add((Integer) gReqId);
                }
            }

            for (Long targetP : searchPeers) {
                Class<?> peerReqClass = Class.forName("org.telegram.tgnet.TLRPC$TL_messages_search");
                Object peerReq = peerReqClass.newInstance();

                long targetHash = (targetP == filterByUserId) ? filterByUserAccessHash : 0;
                Object targetObj = (targetP == filterByUserId) ? selectedUserObject : null;
                Object targetInputPeer = createInputPeer(currentAccount, targetP, targetHash, targetObj);

                peerReqClass.getField("peer").set(peerReq, targetInputPeer);
                peerReqClass.getField("q").set(peerReq, query != null ? query : "");
                Object peerFilterObj = (filterByUserId != 0 && !"all".equals(selectedFilter)) ? Class.forName("org.telegram.tgnet.TLRPC$TL_inputMessagesFilterEmpty").newInstance() : filterObj;
                peerReqClass.getField("filter").set(peerReq, peerFilterObj);
                peerReqClass.getField("min_date").set(peerReq, (int) minDate);
                peerReqClass.getField("max_date").set(peerReq, (int) maxDate);
                peerReqClass.getField("offset_id").set(peerReq, isLoadMore ? lastOffsetId : 0);
                peerReqClass.getField("add_offset").set(peerReq, 0);
                peerReqClass.getField("limit").set(peerReq, 50);
                peerReqClass.getField("max_id").set(peerReq, 0);
                peerReqClass.getField("min_id").set(peerReq, 0);
                peerReqClass.getField("hash").set(peerReq, 0L);

                if (targetP != filterByUserId && filterByUserId != 0) {
                    try {
                        Object fromPeer = createInputPeer(currentAccount, filterByUserId, filterByUserAccessHash, selectedUserObject);
                        peerReqClass.getField("from_id").set(peerReq, fromPeer);
                        int flags = peerReqClass.getField("flags").getInt(peerReq);
                        flags |= 1;
                        peerReqClass.getField("flags").set(peerReq, flags);
                    } catch (Throwable ignored) {}
                }

                Object peerDelegate = Proxy.newProxyInstance(
                        reqDelClass.getClassLoader(),
                        new Class<?>[]{reqDelClass},
                        new java.lang.reflect.InvocationHandler() {
                            @Override
                            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                                if ("run".equals(method.getName()) && args != null && args.length >= 2) {
                                    Object response = args[0];
                                    if (response != null) {
                                        try {
                                            Field msgsF = response.getClass().getField("messages");
                                            List<?> msgs = (List<?>) msgsF.get(response);
                                            if (msgs != null) mergedMsgs.addAll(msgs);
                                            Field usersF = response.getClass().getField("users");
                                            List<?> users = (List<?>) usersF.get(response);
                                            if (users != null) mergedUsers.addAll(users);
                                            Field chatsF = response.getClass().getField("chats");
                                            List<?> chats = (List<?>) chatsF.get(response);
                                            if (chats != null) mergedChats.addAll(chats);
                                        } catch (Throwable ignored) {}
                                    }
                                    onComplete.run();
                                }
                                return null;
                            }
                        }
                );

                Object pReqId = sendReq.invoke(cmInst, peerReq, peerDelegate);
                if (pReqId instanceof Integer && (Integer) pReqId != 0) {
                    synchronized (activeReqIds) {
                        activeReqIds.add((Integer) pReqId);
                    }
                }
            }
        } catch (Throwable ignored) {
            performFallbackEmptySearch(token, minDate, maxDate, 0L, filterByUserId, filterByUserAccessHash, selectedUserObject, isLoadMore);
        }
    }

    private void performFallbackEmptySearch(final int token, final long minDate, final long maxDate, final long effectivePeer, final long effectiveFromId, final long effectiveFromHash, final Object effectiveFromObj, final boolean isLoadMore) {
        try {
            int currentAccount = 0;
            try {
                Class<?> uc = Class.forName("org.telegram.messenger.UserConfig");
                Field f = uc.getField("selectedAccount");
                currentAccount = f.getInt(null);
            } catch (Throwable ignored) {}

            Object filterObj = Class.forName("org.telegram.tgnet.TLRPC$TL_inputMessagesFilterEmpty").newInstance();
            Object req;

            if (effectivePeer != 0) {
                Class<?> reqClass = Class.forName("org.telegram.tgnet.TLRPC$TL_messages_search");
                req = reqClass.newInstance();

                long targetHash = (effectivePeer == filterByUserId) ? filterByUserAccessHash : 0;
                Object targetObj = (effectivePeer == filterByUserId) ? selectedUserObject : null;
                Object inputPeer = createInputPeer(currentAccount, effectivePeer, targetHash, targetObj);

                reqClass.getField("peer").set(req, inputPeer);
                reqClass.getField("q").set(req, query != null ? query : "");
                reqClass.getField("filter").set(req, filterObj);
                reqClass.getField("min_date").set(req, (int) minDate);
                reqClass.getField("max_date").set(req, (int) maxDate);
                reqClass.getField("offset_id").set(req, isLoadMore ? lastOffsetId : 0);
                reqClass.getField("add_offset").set(req, 0);
                reqClass.getField("limit").set(req, 50);
                reqClass.getField("max_id").set(req, 0);
                reqClass.getField("min_id").set(req, 0);
                reqClass.getField("hash").set(req, 0L);

                if (effectiveFromId != 0) {
                    try {
                        Object fromPeer = createInputPeer(currentAccount, effectiveFromId, effectiveFromHash, effectiveFromObj);
                        reqClass.getField("from_id").set(req, fromPeer);
                        int flags = reqClass.getField("flags").getInt(req);
                        flags |= 1;
                        reqClass.getField("flags").set(req, flags);
                    } catch (Throwable ignored) {}
                }
            } else {
                Class<?> reqClass = Class.forName("org.telegram.tgnet.TLRPC$TL_messages_searchGlobal");
                req = reqClass.newInstance();

                Object inputPeer = createInputPeer(currentAccount, 0L, 0, null);

                reqClass.getField("folder_id").set(req, 0);
                reqClass.getField("q").set(req, query != null ? query : "");
                reqClass.getField("filter").set(req, filterObj);
                reqClass.getField("min_date").set(req, (int) minDate);
                reqClass.getField("max_date").set(req, (int) maxDate);
                reqClass.getField("offset_rate").set(req, isLoadMore ? lastOffsetRate : 0);
                reqClass.getField("offset_peer").set(req, isLoadMore && lastOffsetPeer != null ? lastOffsetPeer : inputPeer);
                reqClass.getField("offset_id").set(req, isLoadMore ? lastOffsetId : 0);
                reqClass.getField("limit").set(req, 50);
            }

            Class<?> cmClass = Class.forName("org.telegram.tgnet.ConnectionsManager");
            Method cmGetInstance = cmClass.getMethod("getInstance", int.class);
            Object cmInst = cmGetInstance.invoke(null, currentAccount);

            Class<?> reqDelClass = Class.forName("org.telegram.tgnet.RequestDelegate");
            final int acc = currentAccount;

            Object delegate = Proxy.newProxyInstance(
                    reqDelClass.getClassLoader(),
                    new Class<?>[]{reqDelClass},
                    new java.lang.reflect.InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            if ("run".equals(method.getName()) && args != null && args.length >= 2) {
                                final Object response = args[0];
                                final Object error = args[1];
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (token == currentToken) {
                                            renderResults(acc, response, error, isLoadMore);
                                        }
                                    }
                                });
                            }
                            return null;
                        }
                    }
            );

            Method sendReq = cmClass.getMethod("sendRequest", Class.forName("org.telegram.tgnet.TLObject"), reqDelClass);
            sendReq.invoke(cmInst, req, delegate);
        } catch (final Throwable ex) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (token == currentToken) {
                        isLoadingMore = false;
                        if (!isLoadMore) {
                            searchProgressBar.setVisibility(View.GONE);
                            showEmptyState(tr("Ошибка поиска", "Search error"), ex.getMessage(), false);
                        }
                    }
                }
            });
        }
    }

    private Object createFilter(String f) {
        try {
            switch (f) {
                case "photo": return Class.forName("org.telegram.tgnet.TLRPC$TL_inputMessagesFilterPhotos").newInstance();
                case "video": return Class.forName("org.telegram.tgnet.TLRPC$TL_inputMessagesFilterVideo").newInstance();
                case "document": return Class.forName("org.telegram.tgnet.TLRPC$TL_inputMessagesFilterDocument").newInstance();
                case "music": return Class.forName("org.telegram.tgnet.TLRPC$TL_inputMessagesFilterMusic").newInstance();
                case "voice": return Class.forName("org.telegram.tgnet.TLRPC$TL_inputMessagesFilterVoice").newInstance();
                case "url": return Class.forName("org.telegram.tgnet.TLRPC$TL_inputMessagesFilterUrl").newInstance();
                default: return Class.forName("org.telegram.tgnet.TLRPC$TL_inputMessagesFilterEmpty").newInstance();
            }
        } catch (Throwable ignored) {
            try {
                return Class.forName("org.telegram.tgnet.TLRPC$TL_inputMessagesFilterEmpty").newInstance();
            } catch (Throwable t) {
                return null;
            }
        }
    }

    private static class MediaInfo {
        final String badge;
        final String icon;
        final String type;

        MediaInfo(String badge, String icon, String type) {
            this.badge = badge;
            this.icon = icon;
            this.type = type;
        }
    }

    private static class MusicInfo {
        final String title;
        final String performer;
        final String duration;
        final String formattedTitle;

        MusicInfo(String title, String performer, String duration, String formattedTitle) {
            this.title = title;
            this.performer = performer;
            this.duration = duration;
            this.formattedTitle = formattedTitle;
        }
    }

    private static MusicInfo extractMusicInfo(Object msg) {
        if (msg == null) return null;
        try {
            Field mediaF = msg.getClass().getField("media");
            Object media = mediaF.get(msg);
            if (media == null) return null;
            if (!media.getClass().getSimpleName().contains("Document")) return null;
            Field docF = media.getClass().getField("document");
            Object doc = docF.get(media);
            if (doc == null) return null;
            Field attrsF = doc.getClass().getField("attributes");
            List<?> attrs = (List<?>) attrsF.get(doc);
            if (attrs == null) return null;

            String title = null;
            String performer = null;
            int durationSec = 0;
            String fileName = null;
            boolean isAudio = false;

            for (Object attr : attrs) {
                if (attr == null) continue;
                String aName = attr.getClass().getSimpleName();
                if (aName.contains("Audio")) {
                    boolean isVoice = false;
                    try {
                        Field voiceF = attr.getClass().getField("voice");
                        isVoice = voiceF.getBoolean(attr);
                    } catch (Throwable ignored) {}
                    if (isVoice) return null;
                    isAudio = true;
                    try {
                        Field tF = attr.getClass().getField("title");
                        title = (String) tF.get(attr);
                    } catch (Throwable ignored) {}
                    try {
                        Field pF = attr.getClass().getField("performer");
                        performer = (String) pF.get(attr);
                    } catch (Throwable ignored) {}
                    try {
                        Field dF = attr.getClass().getField("duration");
                        durationSec = dF.getInt(attr);
                    } catch (Throwable ignored) {}
                } else if (aName.contains("Filename")) {
                    try {
                        Field fnF = attr.getClass().getField("file_name");
                        fileName = (String) fnF.get(attr);
                    } catch (Throwable ignored) {}
                }
            }

            if (!isAudio) {
                String mime = "";
                try {
                    Field mimeF = doc.getClass().getField("mime_type");
                    mime = (String) mimeF.get(doc);
                } catch (Throwable ignored) {}
                if (mime != null && (mime.startsWith("audio/") || "application/ogg".equals(mime))) {
                    isAudio = true;
                }
            }

            if (!isAudio) return null;

            String fullTitle;
            if (performer != null && !performer.trim().isEmpty() && title != null && !title.trim().isEmpty()) {
                fullTitle = performer.trim() + " \u2014 " + title.trim();
            } else if (title != null && !title.trim().isEmpty()) {
                fullTitle = title.trim();
            } else if (performer != null && !performer.trim().isEmpty()) {
                fullTitle = performer.trim();
            } else if (fileName != null && !fileName.trim().isEmpty()) {
                String fn = fileName.trim();
                int dotIdx = fn.lastIndexOf('.');
                if (dotIdx > 0) {
                    fn = fn.substring(0, dotIdx);
                }
                fullTitle = fn;
            } else {
                fullTitle = isEnglish() ? "Audio track" : "\u0410\u0443\u0434\u0438\u043e\u0442\u0440\u0435\u043a";
            }

            String durationStr = null;
            if (durationSec > 0) {
                int m = durationSec / 60;
                int s = durationSec % 60;
                durationStr = String.format(Locale.US, "%d:%02d", m, s);
            }

            return new MusicInfo(title, performer, durationStr, fullTitle);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static class DocumentInfo {
        final String fileName;
        final String fileSize;
        final String ext;
        final Object document;

        DocumentInfo(String fileName, String fileSize, String ext, Object document) {
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.ext = ext;
            this.document = document;
        }
    }

    private static DocumentInfo extractDocumentInfo(Object msg) {
        if (msg == null) return null;
        try {
            Field mediaF = msg.getClass().getField("media");
            Object media = mediaF.get(msg);
            if (media == null) return null;
            if (!media.getClass().getSimpleName().contains("Document")) return null;
            Field docF = media.getClass().getField("document");
            Object doc = docF.get(media);
            if (doc == null) return null;

            String fileName = null;
            Field attrsF = doc.getClass().getField("attributes");
            List<?> attrs = (List<?>) attrsF.get(doc);
            if (attrs != null) {
                for (Object a : attrs) {
                    if (a != null) {
                        String an = a.getClass().getSimpleName();
                        if (an.contains("Filename")) {
                            Field fnF = a.getClass().getField("file_name");
                            fileName = (String) fnF.get(a);
                        } else if (an.contains("Audio")) {
                            Field voiceF = null;
                            try { voiceF = a.getClass().getField("voice"); } catch (Throwable ignored) {}
                            if (voiceF != null && voiceF.getBoolean(a)) {
                                return null;
                            }
                        }
                    }
                }
            }

            Field sizeF = doc.getClass().getField("size");
            long sizeBytes = sizeF.getLong(doc);
            String formattedSize = formatFileSize(sizeBytes);

            Field mimeF = doc.getClass().getField("mime_type");
            String mime = (String) mimeF.get(doc);
            String ext = "FILE";
            if (fileName != null && fileName.contains(".")) {
                ext = fileName.substring(fileName.lastIndexOf(".") + 1).toUpperCase(Locale.ROOT);
                if (ext.length() > 5) ext = ext.substring(0, 5);
            } else if (mime != null && mime.contains("/")) {
                ext = mime.substring(mime.indexOf("/") + 1).toUpperCase(Locale.ROOT);
                if (ext.length() > 5) ext = ext.substring(0, 5);
            }

            if (fileName == null || fileName.isEmpty()) {
                fileName = (isEnglish() ? "Document." : "Документ.") + ext.toLowerCase(Locale.ROOT);
            }

            return new DocumentInfo(fileName, formattedSize, ext, doc);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0f);
        if (bytes < 1024 * 1024 * 1024) return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0f * 1024.0f));
        return String.format(Locale.ROOT, "%.1f GB", bytes / (1024.0f * 1024.0f * 1024.0f));
    }

    private static long extractDocumentSize(Object msg) {
        if (msg == null) return 0;
        try {
            Field mediaF = msg.getClass().getField("media");
            Object media = mediaF.get(msg);
            if (media == null) return 0;
            Field docF = media.getClass().getField("document");
            Object doc = docF.get(media);
            if (doc == null) return 0;
            Field sizeF = doc.getClass().getField("size");
            return sizeF.getLong(doc);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static boolean matchesDocumentExtension(Object msg, String extFilter) {
        if ("all".equals(extFilter) || extFilter == null) return true;
        DocumentInfo docInfo = extractDocumentInfo(msg);
        if (docInfo == null || docInfo.fileName == null) return false;
        String fn = docInfo.fileName.toLowerCase(Locale.ROOT);
        int dotIdx = fn.lastIndexOf('.');
        String ext = (dotIdx != -1) ? fn.substring(dotIdx + 1) : "";
        if ("pdf".equals(extFilter)) {
            return "pdf".equals(ext);
        } else if ("apk".equals(extFilter)) {
            return "apk".equals(ext) || "xapk".equals(ext) || "apks".equals(ext);
        } else if ("archive".equals(extFilter)) {
            return "zip".equals(ext) || "rar".equals(ext) || "7z".equals(ext) || "tar".equals(ext) || "gz".equals(ext) || "bz2".equals(ext) || "xz".equals(ext);
        } else if ("doc".equals(extFilter)) {
            return "doc".equals(ext) || "docx".equals(ext) || "txt".equals(ext) || "rtf".equals(ext) || "odt".equals(ext) || "epub".equals(ext) || "fb2".equals(ext) || "mobi".equals(ext);
        } else if ("sheet".equals(extFilter)) {
            return "xls".equals(ext) || "xlsx".equals(ext) || "csv".equals(ext) || "ods".equals(ext);
        }
        return true;
    }

    private static boolean isVoiceMessage(Object msg) {
        if (msg == null) return false;
        try {
            Field mediaF = msg.getClass().getField("media");
            Object media = mediaF.get(msg);
            if (media == null) return false;
            String mName = media.getClass().getSimpleName();
            if (mName.contains("Voice")) return true;
            if (!mName.contains("Document")) return false;
            Field docF = media.getClass().getField("document");
            Object doc = docF.get(media);
            if (doc == null) return false;
            Field attrsF = doc.getClass().getField("attributes");
            List<?> attrs = (List<?>) attrsF.get(doc);
            if (attrs == null) return false;
            for (Object a : attrs) {
                if (a != null && a.getClass().getSimpleName().contains("Audio")) {
                    Field vF = a.getClass().getField("voice");
                    return vF.getBoolean(a);
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static int extractVoiceDuration(Object msg) {
        if (msg == null) return 0;
        try {
            Field mediaF = msg.getClass().getField("media");
            Object media = mediaF.get(msg);
            if (media == null) return 0;
            Field docF = media.getClass().getField("document");
            Object doc = docF.get(media);
            if (doc == null) return 0;
            Field attrsF = doc.getClass().getField("attributes");
            List<?> attrs = (List<?>) attrsF.get(doc);
            if (attrs == null) return 0;
            for (Object a : attrs) {
                if (a != null && a.getClass().getSimpleName().contains("Audio")) {
                    Field dF = a.getClass().getField("duration");
                    return dF.getInt(a);
                }
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    private static String formatVoiceDuration(int durationSec) {
        int m = durationSec / 60;
        int s = durationSec % 60;
        return String.format(Locale.US, "%d:%02d", m, s);
    }

    private static byte[] extractVoiceWaveform(Object msg) {
        if (msg == null) return null;
        try {
            Field mediaF = msg.getClass().getField("media");
            Object media = mediaF.get(msg);
            if (media == null) return null;
            Field docF = media.getClass().getField("document");
            Object doc = docF.get(media);
            if (doc == null) return null;
            Field attrsF = doc.getClass().getField("attributes");
            List<?> attrs = (List<?>) attrsF.get(doc);
            if (attrs == null) return null;
            for (Object a : attrs) {
                if (a != null) {
                    try {
                        Field wF = a.getClass().getField("waveform");
                        Object wObj = wF.get(a);
                        if (wObj instanceof byte[]) {
                            byte[] b = (byte[]) wObj;
                            if (b.length > 0) return b;
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static int read5Bits(byte[] bytes, int index) {
        int bitPos = index * 5;
        int bytePos = bitPos / 8;
        int bitOffset = bitPos % 8;
        if (bytePos >= bytes.length) return 0;
        int b1 = bytes[bytePos] & 0xFF;
        if (bitOffset <= 3) {
            return (b1 >> bitOffset) & 0x1F;
        } else {
            int b2 = (bytePos + 1 < bytes.length) ? (bytes[bytePos + 1] & 0xFF) : 0;
            int v = (b1 >> bitOffset) | (b2 << (8 - bitOffset));
            return v & 0x1F;
        }
    }

    private static float[] decodeWaveformHeights(byte[] waveform, int count) {
        float[] heights = new float[count];
        if (waveform == null || waveform.length == 0) {
            float[] fallback = {0.35f, 0.65f, 0.45f, 0.85f, 0.7f, 0.5f, 0.95f, 0.6f, 0.8f, 0.4f, 0.7f, 0.9f, 0.55f, 0.35f, 0.75f, 0.6f, 0.45f, 0.8f};
            System.arraycopy(fallback, 0, heights, 0, Math.min(fallback.length, count));
            return heights;
        }
        int totalSamples = (waveform.length * 8) / 5;
        if (totalSamples <= 0) totalSamples = 1;
        for (int i = 0; i < count; i++) {
            int sampleIdx = Math.min((i * totalSamples) / count, totalSamples - 1);
            int val = read5Bits(waveform, sampleIdx);
            float h = val / 31.0f;
            heights[i] = Math.max(0.12f, Math.min(1.0f, h));
        }
        return heights;
    }

    private static File getDownloadedDocumentFile(int currentAccount, Object document) {
        if (document == null) return null;
        try {
            Class<?> flClass = Class.forName("org.telegram.messenger.FileLoader");
            Method getInstance = flClass.getMethod("getInstance", int.class);
            Object flInst = getInstance.invoke(null, currentAccount);
            Method getPathToAttach = flClass.getMethod("getPathToAttach", Class.forName("org.telegram.tgnet.TLObject"), boolean.class);
            File file = (File) getPathToAttach.invoke(flInst, document, true);
            if (file != null && file.exists() && file.length() > 0) return file;
            file = (File) getPathToAttach.invoke(flInst, document, false);
            if (file != null && file.exists() && file.length() > 0) return file;
        } catch (Throwable ignored) {}
        try {
            Class<?> flClass = Class.forName("org.telegram.messenger.FileLoader");
            Method getAttachFileName = flClass.getMethod("getAttachFileName", Class.forName("org.telegram.tgnet.TLObject"));
            String fileName = (String) getAttachFileName.invoke(null, document);
            if (fileName != null && !fileName.isEmpty()) {
                Method getDir = flClass.getMethod("getDirectory", int.class);
                for (int dirType : new int[]{3, 4, 1, 2}) {
                    try {
                        File dir = (File) getDir.invoke(null, dirType);
                        if (dir != null) {
                            File f = new File(dir, fileName);
                            if (f.exists() && f.length() > 0) return f;
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean isDocumentDownloaded(int currentAccount, Object document) {
        return getDownloadedDocumentFile(currentAccount, document) != null;
    }

    private void openDownloadedFile(int currentAccount, Object document, Object msg, String fileName) {
        if (document == null) return;
        File file = getDownloadedDocumentFile(currentAccount, document);
        Activity activity = null;
        try {
            Class<?> auClass = Class.forName("org.telegram.messenger.AndroidUtilities");
            Method getActivityMethod = auClass.getMethod("findActivity", Context.class);
            activity = (Activity) getActivityMethod.invoke(null, context);
        } catch (Throwable ignored) {}
        if (activity == null && context instanceof Activity) {
            activity = (Activity) context;
        }
        if (activity == null) {
            try {
                Class<?> laClass = Class.forName("org.telegram.ui.LaunchActivity");
                Field instField = laClass.getField("instance");
                activity = (Activity) instField.get(null);
            } catch (Throwable ignored) {}
        }
        boolean opened = false;
        if (activity != null) {
            if (msg != null) {
                try {
                    Class<?> moClass = Class.forName("org.telegram.messenger.MessageObject");
                    Constructor<?> moCons = moClass.getConstructor(int.class, Class.forName("org.telegram.tgnet.TLRPC$Message"), boolean.class, boolean.class);
                    Object mo = moCons.newInstance(currentAccount, msg, false, false);
                    Class<?> auClass = Class.forName("org.telegram.messenger.AndroidUtilities");
                    Method openMo = auClass.getMethod("openForView", moClass, Activity.class, Class.forName("org.telegram.ui.ActionBar.Theme$ResourcesProvider"), boolean.class);
                    opened = ((Boolean) openMo.invoke(null, mo, activity, null, false)).booleanValue();
                } catch (Throwable ignored) {}
            }
            if (!opened && file != null && file.exists()) {
                try {
                    String mime = null;
                    try {
                        Field mimeF = document.getClass().getField("mime_type");
                        mime = (String) mimeF.get(document);
                    } catch (Throwable ignored) {}
                    Class<?> auClass = Class.forName("org.telegram.messenger.AndroidUtilities");
                    Method openFile = auClass.getMethod("openForView", File.class, String.class, String.class, Activity.class, Class.forName("org.telegram.ui.ActionBar.Theme$ResourcesProvider"), boolean.class);
                    opened = ((Boolean) openFile.invoke(null, file, fileName != null ? fileName : file.getName(), mime, activity, null, false)).booleanValue();
                } catch (Throwable ignored) {}
            }
        }
        if (!opened && file != null && file.exists()) {
            try {
                String mime = null;
                try {
                    Field mimeF = document.getClass().getField("mime_type");
                    mime = (String) mimeF.get(document);
                } catch (Throwable ignored) {}
                if (mime == null || mime.isEmpty()) mime = "*/*";
                Uri fileUri = null;
                try {
                    Class<?> fpClass = Class.forName("androidx.core.content.FileProvider");
                    Method getUri = fpClass.getMethod("getUriForFile", Context.class, String.class, File.class);
                    fileUri = (Uri) getUri.invoke(null, context, context.getPackageName() + ".provider", file);
                } catch (Throwable t) {
                    fileUri = Uri.fromFile(file);
                }
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(fileUri, mime);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                opened = true;
            } catch (Throwable ignored) {}
        }
        if (opened) {
            minimize();
        }
    }


    private void shareDownloadedFile(int currentAccount, Object document, String fileName) {
        File file = getDownloadedDocumentFile(currentAccount, document);
        if (file == null || !file.exists()) return;
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            String mime = null;
            try {
                Field mimeF = document.getClass().getField("mime_type");
                mime = (String) mimeF.get(document);
            } catch (Throwable ignored) {}
            if (mime == null || mime.isEmpty()) mime = "*/*";
            intent.setType(mime);

            Uri fileUri = null;
            try {
                Class<?> fpClass = Class.forName("androidx.core.content.FileProvider");
                Method getUri = fpClass.getMethod("getUriForFile", Context.class, String.class, File.class);
                fileUri = (Uri) getUri.invoke(null, context, context.getPackageName() + ".provider", file);
            } catch (Throwable t) {
                fileUri = Uri.fromFile(file);
            }

            intent.putExtra(Intent.EXTRA_STREAM, fileUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(intent, fileName != null ? fileName : tr("Поделиться файлом", "Share file"));
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
            minimize();
        } catch (Throwable ignored) {}
    }

    private void saveDownloadedFileToDownloads(int currentAccount, Object document, String fileName) {
        File file = getDownloadedDocumentFile(currentAccount, document);
        if (file == null || !file.exists()) return;
        try {
            Class<?> mcClass = Class.forName("org.telegram.messenger.MediaController");
            Method saveMethod = mcClass.getMethod("saveFile", String.class, Context.class, int.class, String.class, String.class);
            saveMethod.invoke(null, file.getAbsolutePath(), context, 0, fileName, null);
            showToast(tr("Файл сохранен в Загрузки", "File saved to Downloads"));
        } catch (Throwable t) {
            try {
                File publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (publicDownloads != null && (publicDownloads.exists() || publicDownloads.mkdirs())) {
                    String targetName = (fileName != null && !fileName.isEmpty()) ? fileName : file.getName();
                    File dest = new File(publicDownloads, targetName);
                    FileInputStream in = new FileInputStream(file);
                    FileOutputStream out = new FileOutputStream(dest);
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                    in.close();
                    out.close();
                    showToast(tr("Файл сохранен в Загрузки", "File saved to Downloads"));
                }
            } catch (Throwable ignored) {}
        }
    }

    private boolean deleteDownloadedFile(int currentAccount, Object document) {
        File file = getDownloadedDocumentFile(currentAccount, document);
        if (file != null && file.exists()) {
            boolean deleted = file.delete();
            if (deleted) {
                showToast(tr("Файл удален из памяти", "File deleted from storage"));
                return true;
            }
        }
        return false;
    }

    private void showToast(final String text) {
        if (text == null || text.isEmpty()) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
                } catch (Throwable ignored) {}
            }
        });
    }

    private static void downloadDocument(int currentAccount, Object document, Object msg) {
        if (document == null) return;
        try {
            Object parentMo = null;
            if (msg != null) {
                try {
                    Class<?> moClass = Class.forName("org.telegram.messenger.MessageObject");
                    Constructor<?> moCtor = moClass.getConstructor(int.class, Class.forName("org.telegram.tgnet.TLRPC$Message"), boolean.class, boolean.class);
                    parentMo = moCtor.newInstance(currentAccount, msg, false, false);
                } catch (Throwable ignored) {}
            }
            if (parentMo == null) parentMo = msg;

            Class<?> flClass = Class.forName("org.telegram.messenger.FileLoader");
            Method getInstance = flClass.getMethod("getInstance", int.class);
            Object flInst = getInstance.invoke(null, currentAccount);
            Method loadFile = flClass.getMethod("loadFile", Class.forName("org.telegram.tgnet.TLRPC$Document"), Object.class, int.class, int.class);
            loadFile.invoke(flInst, document, parentMo, 2, 0);
        } catch (Throwable ignored) {}
    }

    private static String getMessageLink(Object userOrChat, String username, long dialogId, int msgId) {
        if (msgId <= 0) return null;
        if (username != null && !username.isEmpty()) {
            String u = username.startsWith("@") ? username.substring(1) : username;
            return "https://t.me/" + u + "/" + msgId;
        }
        if (userOrChat != null) {
            try {
                Field unF = userOrChat.getClass().getField("username");
                Object un = unF.get(userOrChat);
                if (un instanceof String && !((String) un).isEmpty()) {
                    String u = ((String) un).startsWith("@") ? ((String) un).substring(1) : (String) un;
                    return "https://t.me/" + u + "/" + msgId;
                }
            } catch (Throwable ignored) {}
        }
        if (dialogId != 0) {
            long peer = Math.abs(dialogId);
            if (peer > 1000000000000L) {
                peer -= 1000000000000L;
            }
            return "https://t.me/c/" + peer + "/" + msgId;
        }
        return null;
    }

    private static long getMyUserId(int currentAccount) {
        try {
            Class<?> ucClass = Class.forName("org.telegram.messenger.UserConfig");
            Method getInstance = ucClass.getMethod("getInstance", int.class);
            Object uc = getInstance.invoke(null, currentAccount);
            Method getClientUserId = ucClass.getMethod("getClientUserId");
            return (long) getClientUserId.invoke(uc);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static Object getMyUserObject(int currentAccount) {
        try {
            Class<?> ucClass = Class.forName("org.telegram.messenger.UserConfig");
            Method getInstance = ucClass.getMethod("getInstance", int.class);
            Object uc = getInstance.invoke(null, currentAccount);
            Method getCurrentUser = ucClass.getMethod("getCurrentUser");
            return getCurrentUser.invoke(uc);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void toggleAudioPlayback(int currentAccount, Object msg, ImageView iconIv) {
        toggleAudioPlayback(currentAccount, msg, iconIv, null, 0);
    }

    private void toggleAudioPlayback(int currentAccount, Object msg, ImageView iconIv, TextView durTv, int durationSec) {
        if (msg == null || iconIv == null) return;
        try {
            Class<?> moClass = Class.forName("org.telegram.messenger.MessageObject");
            Constructor<?> moCtor = moClass.getConstructor(int.class, Class.forName("org.telegram.tgnet.TLRPC$Message"), boolean.class, boolean.class);
            Object messageObject = moCtor.newInstance(currentAccount, msg, false, false);

            Class<?> mcClass = Class.forName("org.telegram.messenger.MediaController");
            Method getInstance = mcClass.getMethod("getInstance");
            Object mcInst = getInstance.invoke(null);
            Method isPlaying = mcClass.getMethod("isPlayingMessage", moClass);
            boolean isCurPlaying = (boolean) isPlaying.invoke(mcInst, messageObject);
            Method isPaused = mcClass.getMethod("isMessagePaused");
            boolean isCurPaused = (boolean) isPaused.invoke(mcInst);

            int accentColor = getAccentColor();
            int mId = 0;
            try {
                Field idF = msg.getClass().getField("id");
                mId = idF.getInt(msg);
            } catch (Throwable ignored) {}

            if (isCurPlaying && !isCurPaused) {
                Method pause = mcClass.getMethod("pauseMessage", moClass, boolean.class);
                pause.invoke(mcInst, messageObject, false);
                iconIv.setImageDrawable(new VectorIconDrawable("play", accentColor, dp(14)));
                mainHandler.removeCallbacks(voiceProgressTicker);
                playingMessageObj = null;
                playingIconView = null;
                activeVoiceMsgId = 0;
                activeVoiceDuration = 0;
                activeVoiceDurTv = null;
                activeVoiceTargetProgress = 0f;
                activeVoiceRenderProgress = 0f;
                activeVoiceLastQueryTime = 0;
            } else {
                if (playingIconView != null && playingIconView != iconIv) {
                    playingIconView.setImageDrawable(new VectorIconDrawable("play", accentColor, dp(14)));
                }
                mainHandler.removeCallbacks(voiceProgressTicker);
                Method play = mcClass.getMethod("playMessage", moClass);
                play.invoke(mcInst, messageObject);
                iconIv.setImageDrawable(new VectorIconDrawable("pause", accentColor, dp(14)));
                playingMessageObj = messageObject;
                playingIconView = iconIv;
                activeVoiceMsgId = mId;
                activeVoiceDuration = durationSec;
                activeVoiceDurTv = durTv;
                activeVoiceLastQueryTime = 0;
                mainHandler.post(voiceProgressTicker);
            }
        } catch (Throwable ignored) {}
    }

    private boolean isMessagePlaying(int currentAccount, Object msg) {
        if (msg == null) return false;
        try {
            Class<?> moClass = Class.forName("org.telegram.messenger.MessageObject");
            Constructor<?> moCtor = moClass.getConstructor(int.class, Class.forName("org.telegram.tgnet.TLRPC$Message"), boolean.class, boolean.class);
            Object messageObject = moCtor.newInstance(currentAccount, msg, false, false);

            Class<?> mcClass = Class.forName("org.telegram.messenger.MediaController");
            Method getInstance = mcClass.getMethod("getInstance");
            Object mcInst = getInstance.invoke(null);
            Method isPlaying = mcClass.getMethod("isPlayingMessage", moClass);
            boolean isCurPlaying = (boolean) isPlaying.invoke(mcInst, messageObject);
            Method isPaused = mcClass.getMethod("isMessagePaused");
            boolean isCurPaused = (boolean) isPaused.invoke(mcInst);
            return isCurPlaying && !isCurPaused;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void stopAudioPlayback() {
        mainHandler.removeCallbacks(voiceProgressTicker);
        if (playingMessageObj != null) {
            try {
                Class<?> moClass = Class.forName("org.telegram.messenger.MessageObject");
                Class<?> mcClass = Class.forName("org.telegram.messenger.MediaController");
                Method getInstance = mcClass.getMethod("getInstance");
                Object mcInst = getInstance.invoke(null);
                Method pause = mcClass.getMethod("pauseMessage", moClass, boolean.class);
                pause.invoke(mcInst, playingMessageObj, false);
            } catch (Throwable ignored) {}
            playingMessageObj = null;
            playingIconView = null;
            activeVoiceMsgId = 0;
            activeVoiceDuration = 0;
            activeVoiceDurTv = null;
            activeVoiceTargetProgress = 0f;
            activeVoiceRenderProgress = 0f;
            activeVoiceLastQueryTime = 0;
        }
    }

    private static void seekVoiceMessage(int currentAccount, Object msg, float progress) {
        if (msg == null) return;
        try {
            Class<?> moClass = Class.forName("org.telegram.messenger.MessageObject");
            Constructor<?> moCtor = moClass.getConstructor(int.class, Class.forName("org.telegram.tgnet.TLRPC$Message"), boolean.class, boolean.class);
            Object messageObject = moCtor.newInstance(currentAccount, msg, false, false);
            Class<?> mcClass = Class.forName("org.telegram.messenger.MediaController");
            Method getInstance = mcClass.getMethod("getInstance");
            Object mcInst = getInstance.invoke(null);
            Method seek = mcClass.getMethod("seekToProgress", moClass, float.class);
            seek.invoke(mcInst, messageObject, progress);
        } catch (Throwable ignored) {}
    }

    private static Object findClosestPhotoSize(List<?> sizes, int targetSide) {
        if (sizes == null || sizes.isEmpty()) return null;
        Object closest = null;
        int minDiff = Integer.MAX_VALUE;
        for (Object s : sizes) {
            if (s == null) continue;
            String name = s.getClass().getSimpleName();
            if (name.contains("Stripped") || name.contains("Empty") || name.contains("Path")) {
                continue;
            }
            try {
                int w = s.getClass().getField("w").getInt(s);
                int h = s.getClass().getField("h").getInt(s);
                int side = Math.max(w, h);
                int diff = Math.abs(side - targetSide);
                if (diff < minDiff) {
                    minDiff = diff;
                    closest = s;
                }
            } catch (Throwable ignored) {}
        }
        if (closest != null) return closest;
        for (Object s : sizes) {
            if (s == null) continue;
            String name = s.getClass().getSimpleName();
            if (!name.contains("Stripped") && !name.contains("Empty") && !name.contains("Path")) {
                return s;
            }
        }
        return null;
    }

    private View createMediaThumbnailView(int currentAccount, Object msg, final long targetPeerId, final int targetMsgId) {
        if (msg == null) return null;
        try {
            Class<?> moClass = Class.forName("org.telegram.messenger.MessageObject");
            Constructor<?> moCtor = moClass.getConstructor(int.class, Class.forName("org.telegram.tgnet.TLRPC$Message"), boolean.class, boolean.class);
            Object messageObject = moCtor.newInstance(currentAccount, msg, false, false);

            Class<?> flClass = Class.forName("org.telegram.messenger.FileLoader");
            Method getClosest = flClass.getMethod("getClosestPhotoSizeWithSize", ArrayList.class, int.class);

            Class<?> ilClass = Class.forName("org.telegram.messenger.ImageLocation");
            Object imgLoc = null;
            Drawable strippedThumb = null;

            try {
                Field stF = moClass.getField("strippedThumb");
                strippedThumb = (Drawable) stF.get(messageObject);
            } catch (Throwable ignored) {}

            try {
                Field thumbsF = moClass.getField("photoThumbs");
                ArrayList<?> photoThumbs = (ArrayList<?>) thumbsF.get(messageObject);
                Field thumbsObjF = moClass.getField("photoThumbsObject");
                Object photoThumbsObj = thumbsObjF.get(messageObject);
                if (photoThumbs != null && !photoThumbs.isEmpty() && photoThumbsObj != null) {
                    Object photoSize = null;
                    try {
                        photoSize = getClosest.invoke(null, photoThumbs, 320);
                    } catch (Throwable ignored) {}
                    if (photoSize == null) photoSize = findClosestPhotoSize(photoThumbs, 320);
                    if (photoSize != null) {
                        try {
                            Method getForObj = ilClass.getMethod("getForObject", Class.forName("org.telegram.tgnet.TLRPC$PhotoSize"), Class.forName("org.telegram.tgnet.TLObject"));
                            imgLoc = getForObj.invoke(null, photoSize, photoThumbsObj);
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}

            if (imgLoc == null) {
                try {
                    Field mediaF = msg.getClass().getField("media");
                    Object media = mediaF.get(msg);
                    if (media != null) {
                        String mediaType = media.getClass().getSimpleName();
                        if (mediaType.contains("Photo")) {
                            Field photoF = media.getClass().getField("photo");
                            Object photo = photoF.get(media);
                            if (photo != null && !photo.getClass().getSimpleName().contains("Empty")) {
                                Field sizesF = photo.getClass().getField("sizes");
                                ArrayList<?> sizes = (ArrayList<?>) sizesF.get(photo);
                                Object photoSize = null;
                                try {
                                    photoSize = getClosest.invoke(null, sizes, 320);
                                } catch (Throwable ignored) {}
                                if (photoSize == null) photoSize = findClosestPhotoSize(sizes, 320);
                                if (photoSize != null) {
                                    Method getForPhoto = ilClass.getMethod("getForPhoto", Class.forName("org.telegram.tgnet.TLRPC$PhotoSize"), Class.forName("org.telegram.tgnet.TLRPC$Photo"));
                                    imgLoc = getForPhoto.invoke(null, photoSize, photo);
                                }
                            }
                        } else if (mediaType.contains("Document")) {
                            Field docF = media.getClass().getField("document");
                            Object doc = docF.get(media);
                            if (doc != null && !doc.getClass().getSimpleName().contains("Empty")) {
                                boolean isVisual = false;
                                try {
                                    Field mimeF = doc.getClass().getField("mime_type");
                                    String mime = (String) mimeF.get(doc);
                                    if (mime != null && (mime.startsWith("video/") || mime.startsWith("image/"))) {
                                        isVisual = true;
                                    }
                                } catch (Throwable ignored) {}
                                if (!isVisual) {
                                    try {
                                        Field attrsF = doc.getClass().getField("attributes");
                                        List<?> attrs = (List<?>) attrsF.get(doc);
                                        if (attrs != null) {
                                            for (Object a : attrs) {
                                                if (a != null) {
                                                    String an = a.getClass().getSimpleName();
                                                    if (an.contains("Video") || an.contains("Image")) {
                                                        isVisual = true;
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                    } catch (Throwable ignored) {}
                                }
                                if (isVisual) {
                                    Field thumbsF = doc.getClass().getField("thumbs");
                                    ArrayList<?> thumbs = (ArrayList<?>) thumbsF.get(doc);
                                    if (thumbs != null && !thumbs.isEmpty()) {
                                        Object thumbSize = null;
                                        try {
                                            thumbSize = getClosest.invoke(null, thumbs, 320);
                                        } catch (Throwable ignored) {}
                                        if (thumbSize == null) thumbSize = findClosestPhotoSize(thumbs, 320);
                                        if (thumbSize != null) {
                                            try {
                                                Method getForDoc = ilClass.getMethod("getForDocument", Class.forName("org.telegram.tgnet.TLRPC$PhotoSize"), Class.forName("org.telegram.tgnet.TLRPC$Document"));
                                                imgLoc = getForDoc.invoke(null, thumbSize, doc);
                                            } catch (Throwable ignored) {}
                                        }
                                    }
                                    if (imgLoc == null) {
                                        try {
                                            Method getForDoc = ilClass.getMethod("getForDocument", Class.forName("org.telegram.tgnet.TLRPC$Document"));
                                            imgLoc = getForDoc.invoke(null, doc);
                                        } catch (Throwable ignored) {}
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }

            if (imgLoc == null && strippedThumb == null) return null;

            Class<?> bimgClass = Class.forName("org.telegram.ui.Components.BackupImageView");
            Constructor<?> ctor = bimgClass.getConstructor(Context.class);
            View bimg = (View) ctor.newInstance(context);

            try {
                Method getIR = bimgClass.getMethod("getImageReceiver");
                Object ir = getIR.invoke(bimg);
                if (ir != null) {
                    Method setAcc = ir.getClass().getMethod("setCurrentAccount", int.class);
                    setAcc.invoke(ir, currentAccount);
                    Method onAtt = ir.getClass().getMethod("onAttachedToWindow");
                    onAtt.invoke(ir);
                }
            } catch (Throwable ignored) {}

            Method setRoundRadius = bimgClass.getMethod("setRoundRadius", int.class);
            setRoundRadius.invoke(bimg, dp(8));

            GradientDrawable placeholder = new GradientDrawable();
            placeholder.setColor(getSurfaceContainerHighColor());
            placeholder.setCornerRadius(dp(8));
            bimg.setBackground(placeholder);

            Method setImage = bimgClass.getMethod("setImage", ilClass, String.class, Drawable.class, Object.class);
            setImage.invoke(bimg, imgLoc, "120_120", strippedThumb, messageObject);

            FrameLayout thumbContainer = new FrameLayout(context);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(52), dp(52));
            lp.leftMargin = dp(10);
            lp.gravity = Gravity.CENTER_VERTICAL;
            thumbContainer.setLayoutParams(lp);

            FrameLayout.LayoutParams bimgLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            bimg.setLayoutParams(bimgLp);
            thumbContainer.addView(bimg);

            int vDur = extractVideoDuration(msg);
            if (vDur > 0) {
                TextView durBadge = new TextView(context);
                durBadge.setText(formatDuration(vDur));
                durBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f);
                durBadge.setTextColor(0xFFFFFFFF);
                durBadge.setTypeface(null, Typeface.BOLD);
                durBadge.setGravity(Gravity.CENTER);
                durBadge.setPadding(dp(4), dp(1.5f), dp(4), dp(1.5f));
                GradientDrawable durBg = new GradientDrawable();
                durBg.setColor(0xCC000000);
                durBg.setCornerRadius(dp(4));
                durBadge.setBackground(durBg);

                FrameLayout.LayoutParams durLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                durLp.gravity = Gravity.BOTTOM | Gravity.END;
                durLp.setMargins(0, 0, dp(3), dp(3));
                durBadge.setLayoutParams(durLp);
                thumbContainer.addView(durBadge);
            }

            thumbContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    jumpToMessage(targetPeerId, targetMsgId);
                }
            });

            return thumbContainer;
        } catch (Throwable ignored) {
            return null;
        }
    }


    private static MediaInfo getMediaInfo(Object msg) {
        if (msg == null) return null;
        try {
            Field mediaF = msg.getClass().getField("media");
            Object media = mediaF.get(msg);
            if (media == null) {
                Field textF = msg.getClass().getField("message");
                String text = (String) textF.get(msg);
                if (text != null && (text.contains("http://") || text.contains("https://") || text.contains("t.me/"))) {
                    return new MediaInfo(trStatic("Ссылка", "Link"), "url", "url");
                }
                return null;
            }
            String mName = media.getClass().getSimpleName();
            if (mName.contains("Photo")) {
                return new MediaInfo(trStatic("Фото", "Photo"), "photo", "photo");
            }
            if (mName.contains("Video")) {
                return new MediaInfo(trStatic("Видео", "Video"), "video", "video");
            }
            if (mName.contains("Voice")) {
                return new MediaInfo(trStatic("Голосовое", "Voice"), "voice", "voice");
            }
            if (mName.contains("Round")) {
                return new MediaInfo(trStatic("Кружок", "Video note"), "voice", "voice");
            }
            if (mName.contains("WebPage")) {
                return new MediaInfo(trStatic("Ссылка", "Link"), "url", "url");
            }
            if (mName.contains("Contact")) {
                return new MediaInfo(trStatic("Контакт", "Contact"), "user_search", "contact");
            }
            if (mName.contains("Geo")) {
                return new MediaInfo(trStatic("Гео", "Location"), "filter", "geo");
            }
            if (mName.contains("Poll")) {
                return new MediaInfo(trStatic("Опрос", "Poll"), "filter", "poll");
            }
            if (mName.contains("Document")) {
                Field docF = media.getClass().getField("document");
                Object doc = docF.get(media);
                if (doc != null) {
                    String mime = "";
                    try {
                        Field mimeF = doc.getClass().getField("mime_type");
                        mime = (String) mimeF.get(doc);
                        if (mime == null) mime = "";
                    } catch (Throwable ignored) {}

                    try {
                        Field attrsF = doc.getClass().getField("attributes");
                        List<?> attrs = (List<?>) attrsF.get(doc);
                        if (attrs != null) {
                            for (Object attr : attrs) {
                                if (attr != null) {
                                    String aName = attr.getClass().getSimpleName();
                                    if (aName.contains("Video")) {
                                        boolean isRound = false;
                                        try {
                                            Field roundF = attr.getClass().getField("round_message");
                                            isRound = roundF.getBoolean(attr);
                                        } catch (Throwable ignored) {}
                                        if (isRound) {
                                            return new MediaInfo(trStatic("Кружок", "Video note"), "voice", "voice");
                                        }
                                        return new MediaInfo(trStatic("Видео", "Video"), "video", "video");
                                    }
                                    if (aName.contains("Audio")) {
                                        boolean isVoice = false;
                                        try {
                                            Field voiceF = attr.getClass().getField("voice");
                                            isVoice = voiceF.getBoolean(attr);
                                        } catch (Throwable ignored) {}
                                        if (isVoice) {
                                            return new MediaInfo(trStatic("Голосовое", "Voice"), "voice", "voice");
                                        }
                                        return new MediaInfo(trStatic("Музыка", "Music"), "music", "music");
                                    }
                                    if (aName.contains("Animated")) {
                                        return new MediaInfo("GIF", "video", "video");
                                    }
                                    if (aName.contains("Sticker")) {
                                        return new MediaInfo(trStatic("Стикер", "Sticker"), "photo", "photo");
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {}

                    if (mime.startsWith("video/")) {
                        return new MediaInfo(trStatic("Видео", "Video"), "video", "video");
                    }
                    if (mime.startsWith("audio/") || "application/ogg".equals(mime)) {
                        return new MediaInfo(trStatic("Музыка", "Music"), "music", "music");
                    }
                    if ("image/gif".equals(mime)) {
                        return new MediaInfo("GIF", "video", "video");
                    }
                    return new MediaInfo(trStatic("Файл", "File"), "document", "document");
                }
                return new MediaInfo(trStatic("Файл", "File"), "document", "document");
            }
            Field textF = msg.getClass().getField("message");
            String text = (String) textF.get(msg);
            if (text != null && (text.contains("http://") || text.contains("https://") || text.contains("t.me/"))) {
                return new MediaInfo(trStatic("Ссылка", "Link"), "url", "url");
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static int extractVideoDuration(Object msg) {
        if (msg == null) return 0;
        try {
            Field mediaF = msg.getClass().getField("media");
            Object media = mediaF.get(msg);
            if (media == null) return 0;
            String mName = media.getClass().getSimpleName();
            if (mName.contains("Document")) {
                Field docF = media.getClass().getField("document");
                Object doc = docF.get(media);
                if (doc != null) {
                    Field attrsF = doc.getClass().getField("attributes");
                    List<?> attrs = (List<?>) attrsF.get(doc);
                    if (attrs != null) {
                        for (Object attr : attrs) {
                            if (attr != null && attr.getClass().getSimpleName().contains("Video")) {
                                try {
                                    Field dF = attr.getClass().getField("duration");
                                    Object val = dF.get(attr);
                                    if (val instanceof Number) {
                                        return ((Number) val).intValue();
                                    }
                                } catch (Throwable ignored) {}
                            }
                        }
                    }
                }
            } else if (mName.contains("Video")) {
                try {
                    Field videoF = media.getClass().getField("video");
                    Object video = videoF.get(media);
                    if (video != null) {
                        Field dF = video.getClass().getField("duration");
                        Object val = dF.get(video);
                        if (val instanceof Number) {
                            return ((Number) val).intValue();
                        }
                    }
                } catch (Throwable ignored) {}
                try {
                    Field dF = media.getClass().getField("duration");
                    Object val = dF.get(media);
                    if (val instanceof Number) {
                        return ((Number) val).intValue();
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    private static String formatDuration(int durationSec) {
        if (durationSec <= 0) return "";
        int h = durationSec / 3600;
        int m = (durationSec % 3600) / 60;
        int s = durationSec % 60;
        if (h > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        } else {
            return String.format(Locale.US, "%d:%02d", m, s);
        }
    }

    private static boolean matchesSelectedFilter(Object msg, String filter, String docExt) {
        if ("all".equals(filter) || filter == null) return true;
        if (msg == null) return false;
        MediaInfo info = getMediaInfo(msg);
        if (info == null) return false;
        if (!filter.equals(info.type)) return false;
        if ("document".equals(filter) && docExt != null && !"all".equals(docExt)) {
            return matchesDocumentExtension(msg, docExt);
        }
        return true;
    }

    private static boolean matchesSelectedFilter(Object msg, String filter) {
        return matchesSelectedFilter(msg, filter, null);
    }

    private static String scanDocumentText(int currentAccount, Object msg, String q) {
        if (q == null || q.trim().isEmpty() || msg == null) return null;
        String queryLower = q.trim().toLowerCase(Locale.ROOT);
        try {
            Field mediaF = msg.getClass().getField("media");
            Object media = mediaF.get(msg);
            if (media == null) return null;
            if (!media.getClass().getSimpleName().contains("Document")) return null;

            Field docF = media.getClass().getField("document");
            Object doc = docF.get(media);
            if (doc == null) return null;

            Field sizeF = doc.getClass().getField("size");
            long size = sizeF.getLong(doc);
            if (size > 8 * 1024 * 1024L) return null;

            String fileName = null;
            Field attrsF = doc.getClass().getField("attributes");
            List<?> attrs = (List<?>) attrsF.get(doc);
            if (attrs != null) {
                for (Object a : attrs) {
                    if (a != null && a.getClass().getSimpleName().contains("Filename")) {
                        Field fnF = a.getClass().getField("file_name");
                        fileName = (String) fnF.get(a);
                        break;
                    }
                }
            }

            if (fileName != null && fileName.toLowerCase(Locale.ROOT).contains(queryLower)) {
                return trStatic("Имя файла: ", "File name: ") + fileName;
            }

            Class<?> flClass = Class.forName("org.telegram.messenger.FileLoader");
            Method getInstance = flClass.getMethod("getInstance", int.class);
            Object flInst = getInstance.invoke(null, currentAccount);
            Method getPathToAttach = flClass.getMethod("getPathToAttach", Class.forName("org.telegram.tgnet.TLObject"), boolean.class);
            File file = (File) getPathToAttach.invoke(flInst, doc, true);
            if (file == null || !file.exists()) {
                file = (File) getPathToAttach.invoke(flInst, doc, false);
            }
            if (file == null || !file.exists()) {
                try {
                    Method checkDir = flClass.getMethod("checkDirectory", int.class);
                    File dir = (File) checkDir.invoke(flInst, 3);
                    if (dir != null && fileName != null) {
                        File candidate = new File(dir, fileName);
                        if (candidate.exists()) file = candidate;
                    }
                } catch (Throwable ignored) {}
            }

            if (file != null && file.exists() && file.isFile() && file.length() > 0 && file.length() <= 8 * 1024 * 1024L) {
                FileInputStream fis = new FileInputStream(file);
                byte[] buffer = new byte[(int) Math.min(file.length(), 4 * 1024 * 1024L)];
                int readBytes = fis.read(buffer);
                fis.close();
                if (readBytes > 0) {
                    String contentUtf8 = new String(buffer, 0, readBytes, "UTF-8");
                    int idx = contentUtf8.toLowerCase(Locale.ROOT).indexOf(queryLower);
                    if (idx != -1) {
                        int lineNum = 1;
                        for (int i = 0; i < idx; i++) {
                            if (contentUtf8.charAt(i) == '\n') lineNum++;
                        }
                        int lineStart = contentUtf8.lastIndexOf('\n', idx);
                        if (lineStart == -1) lineStart = 0; else lineStart++;
                        int lineEnd = contentUtf8.indexOf('\n', idx);
                        if (lineEnd == -1) lineEnd = contentUtf8.length();
                        String line = contentUtf8.substring(lineStart, Math.min(lineEnd, lineStart + 120)).trim();
                        if (line.isEmpty()) {
                            int start = Math.max(0, idx - 30);
                            int end = Math.min(contentUtf8.length(), idx + queryLower.length() + 50);
                            line = contentUtf8.substring(start, end).trim();
                        }
                        return trStatic("Стр. ", "Line ") + lineNum + ": " + line;
                    }

                    String contentCp = new String(buffer, 0, readBytes, "windows-1251");
                    idx = contentCp.toLowerCase(Locale.ROOT).indexOf(queryLower);
                    if (idx != -1) {
                        int lineNum = 1;
                        for (int i = 0; i < idx; i++) {
                            if (contentCp.charAt(i) == '\n') lineNum++;
                        }
                        int lineStart = contentCp.lastIndexOf('\n', idx);
                        if (lineStart == -1) lineStart = 0; else lineStart++;
                        int lineEnd = contentCp.indexOf('\n', idx);
                        if (lineEnd == -1) lineEnd = contentCp.length();
                        String line = contentCp.substring(lineStart, Math.min(lineEnd, lineStart + 120)).trim();
                        return trStatic("Стр. ", "Line ") + lineNum + ": " + line;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void cacheUsersAndChats(int currentAccount, Object response) {
        try {
            Class<?> mcClass = Class.forName("org.telegram.messenger.MessagesController");
            Method getInstance = mcClass.getMethod("getInstance", int.class);
            Object mcInst = getInstance.invoke(null, currentAccount);

            try {
                Field usersF = response.getClass().getField("users");
                List<?> users = (List<?>) usersF.get(response);
                if (users != null) {
                    Method putUser = mcClass.getMethod("putUser", Class.forName("org.telegram.tgnet.TLRPC$User"), boolean.class);
                    for (Object user : users) {
                        if (user != null) {
                            try {
                                putUser.invoke(mcInst, user, false);
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            } catch (Throwable ignored) {}

            try {
                Field chatsF = response.getClass().getField("chats");
                List<?> chats = (List<?>) chatsF.get(response);
                if (chats != null) {
                    Method putChat = mcClass.getMethod("putChat", Class.forName("org.telegram.tgnet.TLRPC$Chat"), boolean.class);
                    for (Object chat : chats) {
                        if (chat != null) {
                            try {
                                putChat.invoke(mcInst, chat, false);
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    private void renderResults(int currentAccount, Object response, Object error, boolean isLoadMore) {
        if (!isLoadMore) {
            lastRenderAccount = currentAccount;
            if (error == null) {
                lastRenderResponse = response;
            }
        }

        if (skeletonShimmerAnim != null) {
            skeletonShimmerAnim.cancel();
            skeletonShimmerAnim = null;
        }

        if (loadMoreProgressBar != null && loadMoreProgressBar.getParent() != null) {
            ((ViewGroup) loadMoreProgressBar.getParent()).removeView(loadMoreProgressBar);
        }

        searchProgressBar.setVisibility(View.GONE);

        if (error != null) {
            isLoadingMore = false;
            if (!isLoadMore) {
                String errStr = tr("Не удалось получить результаты", "Failed to retrieve results");
                try {
                    Field codeF = error.getClass().getField("code");
                    Field textF = error.getClass().getField("text");
                    errStr = tr("Ошибка: ", "Error: ") + textF.get(error) + " (" + codeF.getInt(error) + ")";
                } catch (Throwable ignored) {}
                showEmptyState(tr("Ошибка поиска", "Search error"), errStr, false);
            }
            return;
        }

        if (response == null) {
            isLoadingMore = false;
            if (!isLoadMore) {
                showEmptyState(tr("Ничего не найдено", "No results found"), tr("Попробуйте изменить поисковый запрос или фильтры", "Try changing your search query or filters"), true);
            } else {
                hasMoreResults = false;
            }
            return;
        }

        try {
            cacheUsersAndChats(currentAccount, response);

            Field msgsF = response.getClass().getField("messages");
            List<?> rawMsgs = (List<?>) msgsF.get(response);

            if (rawMsgs == null || rawMsgs.isEmpty()) {
                isLoadingMore = false;
                if (!isLoadMore) {
                    showEmptyState(tr("Ничего не найдено", "No results found"), tr("Попробуйте изменить поисковый запрос или фильтры", "Try changing your search query or filters"), true);
                } else {
                    hasMoreResults = false;
                }
                return;
            }

            if (!isLoadMore) {
                resultsContainer.removeAllViews();
                totalLoadedCount = 0;
                loadedMessageKeys.clear();
                accumulatedRawMessages.clear();
            }

            if (rawMsgs != null) {
                accumulatedRawMessages.addAll(rawMsgs);
            }

            if ("chat".equals(scope)) {
                int minRawId = Integer.MAX_VALUE;
                if (rawMsgs != null) {
                    for (Object rm : rawMsgs) {
                        try {
                            int rId = rm.getClass().getField("id").getInt(rm);
                            if (rId > 0 && rId < minRawId) {
                                minRawId = rId;
                            }
                        } catch (Throwable ignored) {}
                    }
                }
                if (minRawId != Integer.MAX_VALUE) {
                    lastOffsetId = minRawId;
                }
                if (rawMsgs != null && rawMsgs.size() < 100) {
                    hasMoreResults = false;
                } else {
                    hasMoreResults = true;
                }
            }

            List<Object> msgs;
            if (("video".equals(selectedFilter) && !"date".equals(selectedVideoSort)) ||
                ("document".equals(selectedFilter) && !"date".equals(selectedDocSort))) {
                resultsContainer.removeAllViews();
                totalLoadedCount = 0;
                loadedMessageKeys.clear();
                msgs = new ArrayList<>(accumulatedRawMessages);
            } else {
                msgs = new ArrayList<>(rawMsgs != null ? rawMsgs : Collections.emptyList());
            }

            Collections.sort(msgs, new Comparator<Object>() {
                @Override
                public int compare(Object m1, Object m2) {
                    try {
                        if ("video".equals(selectedFilter)) {
                            if ("dur_asc".equals(selectedVideoSort)) {
                                int dur1 = extractVideoDuration(m1);
                                int dur2 = extractVideoDuration(m2);
                                if (dur1 <= 0 && dur2 > 0) return 1;
                                if (dur1 > 0 && dur2 <= 0) return -1;
                                if (dur1 != dur2) {
                                    return Integer.compare(dur1, dur2);
                                }
                            } else if ("dur_desc".equals(selectedVideoSort)) {
                                int dur1 = extractVideoDuration(m1);
                                int dur2 = extractVideoDuration(m2);
                                if (dur1 <= 0 && dur2 > 0) return 1;
                                if (dur1 > 0 && dur2 <= 0) return -1;
                                if (dur1 != dur2) {
                                    return Integer.compare(dur2, dur1);
                                }
                            }
                        } else if ("document".equals(selectedFilter)) {
                            if ("size_asc".equals(selectedDocSort)) {
                                long s1 = extractDocumentSize(m1);
                                long s2 = extractDocumentSize(m2);
                                if (s1 <= 0 && s2 > 0) return 1;
                                if (s1 > 0 && s2 <= 0) return -1;
                                if (s1 != s2) {
                                    return Long.compare(s1, s2);
                                }
                            } else if ("size_desc".equals(selectedDocSort)) {
                                long s1 = extractDocumentSize(m1);
                                long s2 = extractDocumentSize(m2);
                                if (s1 <= 0 && s2 > 0) return 1;
                                if (s1 > 0 && s2 <= 0) return -1;
                                if (s1 != s2) {
                                    return Long.compare(s2, s1);
                                }
                            }
                        }
                        int d1 = m1.getClass().getField("date").getInt(m1);
                        int d2 = m2.getClass().getField("date").getInt(m2);
                        return Integer.compare(d2, d1);
                    } catch (Throwable ignored) {
                        return 0;
                    }
                }
            });

            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
            int newItemsAdded = 0;

            for (Object msg : msgs) {
                if (!matchesSelectedFilter(msg, selectedFilter, selectedDocExt)) continue;

                int msgId = 0;
                try {
                    Field idF = msg.getClass().getField("id");
                    msgId = idF.getInt(msg);
                } catch (Throwable ignored) {}

                Field peerF = null;
                Object peerObj = null;
                try {
                    peerF = msg.getClass().getField("peer_id");
                    peerObj = peerF.get(msg);
                } catch (Throwable ignored) {}

                long msgPeerId = extractPeerId(peerObj);
                if (msgPeerId == 0) msgPeerId = peerId;

                boolean isOutMsg = false;
                try {
                    Field outF = msg.getClass().getField("out");
                    isOutMsg = outF.getBoolean(msg);
                } catch (Throwable ignored) {}

                long myUserId = getMyUserId(currentAccount);
                long senderPeerId = 0;
                try {
                    Field fromIdF = msg.getClass().getField("from_id");
                    Object fromObj = fromIdF.get(msg);
                    if (fromObj != null) {
                        senderPeerId = extractPeerId(fromObj);
                    }
                } catch (Throwable ignored) {}
                if (senderPeerId == 0) {
                    try {
                        Field fId = msg.getClass().getField("from_id");
                        if (fId.getType() == long.class) {
                            senderPeerId = fId.getLong(msg);
                        }
                    } catch (Throwable ignored) {}
                }
                if (isOutMsg) {
                    senderPeerId = myUserId;
                } else if (senderPeerId == 0) {
                    if (msgPeerId > 0) {
                        senderPeerId = msgPeerId;
                    }
                }

                Object userOrChat = null;
                String senderTitle = null;
                String authorUsername = null;
                long authorResolvedId = 0;

                try {
                    Class<?> mcClass = Class.forName("org.telegram.messenger.MessagesController");
                    Method getInstance = mcClass.getMethod("getInstance", int.class);
                    Object mcInst = getInstance.invoke(null, currentAccount);

                    if (senderPeerId > 0) {
                        Method getUser = mcClass.getMethod("getUser", Long.class);
                        userOrChat = getUser.invoke(mcInst, senderPeerId);
                        if (userOrChat != null) {
                            try {
                                authorResolvedId = userOrChat.getClass().getField("id").getLong(userOrChat);
                            } catch (Throwable ignored) {}
                            try {
                                authorUsername = (String) userOrChat.getClass().getField("username").get(userOrChat);
                            } catch (Throwable ignored) {}
                            if (authorUsername == null || authorUsername.isEmpty()) {
                                try {
                                    Object unsList = userOrChat.getClass().getField("usernames").get(userOrChat);
                                    if (unsList instanceof List && !((List<?>) unsList).isEmpty()) {
                                        Object u0 = ((List<?>) unsList).get(0);
                                        authorUsername = (String) u0.getClass().getField("username").get(u0);
                                    }
                                } catch (Throwable ignored) {}
                            }
                            try {
                                Class<?> userObjClass = Class.forName("org.telegram.messenger.UserObject");
                                Method getUserName = userObjClass.getMethod("getUserName", userOrChat.getClass());
                                Object name = getUserName.invoke(null, userOrChat);
                                if (name != null) senderTitle = name.toString();
                            } catch (Throwable t) {
                                try {
                                    Field fnF = userOrChat.getClass().getField("first_name");
                                    Field lnF = userOrChat.getClass().getField("last_name");
                                    String fn = (String) fnF.get(userOrChat);
                                    String ln = (String) lnF.get(userOrChat);
                                    senderTitle = ((fn != null ? fn : "") + " " + (ln != null ? ln : "")).trim();
                                } catch (Throwable ignored) {}
                            }
                        }
                    } else if (senderPeerId < 0) {
                        Method getChat = mcClass.getMethod("getChat", Long.class);
                        userOrChat = getChat.invoke(mcInst, -senderPeerId);
                        if (userOrChat != null) {
                            try {
                                authorResolvedId = -userOrChat.getClass().getField("id").getLong(userOrChat);
                            } catch (Throwable ignored) {}
                            try {
                                authorUsername = (String) userOrChat.getClass().getField("username").get(userOrChat);
                            } catch (Throwable ignored) {}
                            Field titleF = userOrChat.getClass().getField("title");
                            Object title = titleF.get(userOrChat);
                            if (title != null) senderTitle = title.toString();
                        }
                    }
                } catch (Throwable ignored) {}

                if (senderTitle == null || senderTitle.isEmpty()) {
                    senderTitle = PeerResolver.getPeerTitle(context, senderPeerId);
                }

                if (filterByUserId != 0) {
                    boolean authorMatches = false;
                    if (isOutMsg) {
                        authorMatches = (filterByUserId == myUserId);
                    } else {
                        authorMatches = (senderPeerId == filterByUserId) || (authorResolvedId != 0 && authorResolvedId == filterByUserId);
                        if (!authorMatches && senderPeerId == 0 && msgPeerId > 0) {
                            authorMatches = (msgPeerId == filterByUserId);
                        }
                    }
                    if (!authorMatches && filterByUserName != null && !filterByUserName.isEmpty() && (!isOutMsg || filterByUserId == myUserId)) {
                        String cleanTag = filterByUserName.startsWith("@") ? filterByUserName.substring(1).toLowerCase(Locale.ROOT) : filterByUserName.toLowerCase(Locale.ROOT);
                        if (authorUsername != null && (authorUsername.equalsIgnoreCase(cleanTag) || authorUsername.toLowerCase(Locale.ROOT).contains(cleanTag))) {
                            authorMatches = true;
                        }
                        if (!authorMatches && senderTitle != null && senderTitle.toLowerCase(Locale.ROOT).contains(cleanTag)) {
                            authorMatches = true;
                        }
                    }
                    if (!authorMatches) {
                        continue;
                    }
                }

                String itemKey = msgPeerId + "_" + msgId;
                if (loadedMessageKeys.contains(itemKey)) continue;
                loadedMessageKeys.add(itemKey);

                newItemsAdded++;
                totalLoadedCount++;

                String dialogTitle = null;
                if (msgPeerId != senderPeerId && msgPeerId != 0) {
                    dialogTitle = PeerResolver.getPeerTitle(context, msgPeerId);
                }

                Field msgTextF = msg.getClass().getField("message");
                String msgText = (String) msgTextF.get(msg);

                Field dateF = msg.getClass().getField("date");
                int dateSec = dateF.getInt(msg);
                String dateStr = sdf.format(new Date(dateSec * 1000L));

                MediaInfo mediaInfo = getMediaInfo(msg);
                String mediaBadge = (mediaInfo != null) ? mediaInfo.badge : "";
                String mediaIcon = (mediaInfo != null) ? mediaInfo.icon : null;
                if (mediaInfo != null && "video".equals(mediaInfo.type)) {
                    mediaBadge = "";
                    mediaIcon = null;
                }

                MusicInfo musicInfo = extractMusicInfo(msg);
                DocumentInfo documentInfo = extractDocumentInfo(msg);
                String docSnippet = scanDocumentText(currentAccount, msg, query);

                if ("document".equals(selectedFilter) && query != null && !query.trim().isEmpty()) {
                    String qLower = query.trim().toLowerCase(Locale.ROOT);
                    boolean matchesDoc = false;
                    if (msgText != null && msgText.toLowerCase(Locale.ROOT).contains(qLower)) {
                        matchesDoc = true;
                    } else if (documentInfo != null && documentInfo.fileName != null && documentInfo.fileName.toLowerCase(Locale.ROOT).contains(qLower)) {
                        matchesDoc = true;
                    } else if (docSnippet != null && !docSnippet.isEmpty()) {
                        matchesDoc = true;
                    }
                    if (!matchesDoc) {
                        continue;
                    }
                }

                final long finalPeer = msgPeerId;
                final int finalMsgId = msgId;
                View itemView = createM3MessageCard(currentAccount, msg, userOrChat, senderPeerId, senderTitle, authorUsername, dialogTitle, finalPeer, finalMsgId, msgText, dateStr, mediaBadge, mediaIcon, docSnippet, musicInfo, documentInfo);
                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                        jumpToMessage(finalPeer, finalMsgId);
                    }
                });

                int animDelay = Math.min((newItemsAdded - 1) * 24, 240);
                itemView.setAlpha(0f);
                itemView.setTranslationY(dp(16));
                itemView.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(300)
                        .setStartDelay(animDelay)
                        .setInterpolator(M3_EMPHASIZED_DECELERATE)
                        .withLayer()
                        .start();

                resultsContainer.addView(itemView);
            }



            if (!isLoadMore && totalLoadedCount > 0 && hasActiveSearchCriteria()) {
                addSearchHistoryItem(query, scope, selectedFilter, selectedDatePreset, customMinDate, customMaxDate, filterByUserId, filterByUserName, false);
            }

            if (totalLoadedCount == 0) {
                showEmptyState(tr("Ничего не найдено", "No results found"), tr("Ничего не найдено по заданным фильтрам", "No results found for selected filters"), true);
            } else {
                statsBadge.setText(tr("Найдено: " + totalLoadedCount + " сообщений", "Found: " + totalLoadedCount + (totalLoadedCount == 1 ? " message" : " messages")));
                statsBadge.setVisibility(View.VISIBLE);
                if (!isLoadMore && totalLoadedCount > 0) {
                    checkAndShowLongTapTutorial();
                }
            }

            isLoadingMore = false;

            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    updateThumbPosition();
                }
            });
        } catch (Throwable ex) {
            isLoadingMore = false;
            if (!isLoadMore) {
                showEmptyState(tr("Ошибка поиска", "Search error"), ex.getMessage(), false);
            }
        }
    }

    public static void resetLongTapTutorial(Context context) {
        if (context == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences("extera_advanced_search_prefs", Context.MODE_PRIVATE);
            prefs.edit()
                    .remove("has_shown_longtap_hint")
                    .apply();
            try {
                android.widget.Toast.makeText(context, trStatic("Подсказка сброшена! Она появится при следующем поиске.", "Hint reset! It will appear on next search."), android.widget.Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    private void checkAndShowLongTapTutorial() {
        if (context == null || rootFrame == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences("extera_advanced_search_prefs", Context.MODE_PRIVATE);
            boolean alreadyShown = prefs.getBoolean("has_shown_longtap_hint", false);
            if (alreadyShown) return;
            prefs.edit().putBoolean("has_shown_longtap_hint", true).apply();

            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    showLongTapTutorialOverlay();
                }
            }, 350);
        } catch (Throwable ignored) {}
    }

    private void showLongTapTutorialOverlay() {
        if (currentDialog == null || !currentDialog.isShowing() || rootFrame == null || resultsContainer == null) {
            return;
        }

        View targetCard = null;
        for (int i = 0; i < resultsContainer.getChildCount(); i++) {
            View v = resultsContainer.getChildAt(i);
            if (v != null && v != emptyTextView && v != emptyStateContainer && v.getVisibility() == View.VISIBLE && v.getHeight() > 0) {
                targetCard = v;
                break;
            }
        }
        if (targetCard == null) return;

        int[] loc = new int[2];
        targetCard.getLocationInWindow(loc);
        int[] rootLoc = new int[2];
        rootFrame.getLocationInWindow(rootLoc);
        final int targetTop = loc[1] - rootLoc[1];
        final int targetLeft = loc[0] - rootLoc[0];
        final int targetWidth = targetCard.getWidth();
        final int targetHeight = targetCard.getHeight();

        if (targetTop <= 0 || targetWidth <= 0 || targetHeight <= 0) return;

        final int primaryText = getPrimaryTextColor();
        final int secondaryText = getSecondaryTextColor();
        final int accentColor = getAccentColor();

        final Bitmap cardSnapshot;
        try {
            cardSnapshot = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
            Canvas snapshotCanvas = new Canvas(cardSnapshot);
            targetCard.draw(snapshotCanvas);
        } catch (Throwable t) {
            return;
        }

        final FrameLayout tutorialOverlay = new FrameLayout(context);
        tutorialOverlay.setBackgroundColor(0x52000000);
        tutorialOverlay.setClickable(true);
        tutorialOverlay.setFocusable(true);

        final FrameLayout elevatedCard = new FrameLayout(context);
        if (Build.VERSION.SDK_INT >= 21) {
            elevatedCard.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(16));
                }
            });
            elevatedCard.setClipToOutline(true);
            elevatedCard.setElevation(dp(8));
        }

        ImageView snapshotIv = new ImageView(context);
        snapshotIv.setImageBitmap(cardSnapshot);
        snapshotIv.setScaleType(ImageView.ScaleType.FIT_XY);
        elevatedCard.addView(snapshotIv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View strokeOverlay = new View(context);
        GradientDrawable strokeBg = new GradientDrawable();
        strokeBg.setColor(0x00000000);
        strokeBg.setStroke(dp(2), accentColor);
        strokeBg.setCornerRadius(dp(16));
        strokeOverlay.setBackground(strokeBg);
        elevatedCard.addView(strokeOverlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout.LayoutParams hlLp = new FrameLayout.LayoutParams(targetWidth, targetHeight);
        hlLp.topMargin = targetTop;
        hlLp.leftMargin = targetLeft;
        tutorialOverlay.addView(elevatedCard, hlLp);

        final LinearLayout tooltipCard = new LinearLayout(context);
        tooltipCard.setOrientation(LinearLayout.VERTICAL);
        tooltipCard.setPadding(dp(20), dp(20), dp(20), dp(20));

        GradientDrawable tooltipBg = new GradientDrawable();
        tooltipBg.setColor(getSurfaceContainerHighColor());
        tooltipBg.setCornerRadius(dp(28));
        tooltipBg.setStroke(dp(1), getOutlineVariantColor());
        tooltipCard.setBackground(tooltipBg);
        if (Build.VERSION.SDK_INT >= 21) {
            tooltipCard.setElevation(dp(6));
        }

        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout iconCircle = new FrameLayout(context);
        GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        circleBg.setColor(getPrimaryContainerColor());
        iconCircle.setBackground(circleBg);
        ImageView iconIv = new ImageView(context);
        iconIv.setImageDrawable(new VectorIconDrawable("info", accentColor, dp(22)));
        iconCircle.addView(iconIv, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        headerRow.addView(iconCircle, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView titleTv = new TextView(context);
        titleTv.setText(tr("Подсказка по действиям", "Actions hint"));
        titleTv.setTextColor(primaryText);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        try {
            Class<?> auClass = Class.forName("org.telegram.messenger.AndroidUtilities");
            Method getTypeface = auClass.getMethod("getTypeface", String.class);
            Typeface tf = (Typeface) getTypeface.invoke(null, "fonts/rmedium.ttf");
            if (tf != null) titleTv.setTypeface(tf);
            else titleTv.setTypeface(null, Typeface.BOLD);
        } catch (Throwable t) {
            titleTv.setTypeface(null, Typeface.BOLD);
        }
        titleTv.setPadding(dp(12), 0, 0, 0);
        headerRow.addView(titleTv);

        tooltipCard.addView(headerRow);

        TextView descTv = new TextView(context);
        descTv.setText(tr("Удерживайте (долгий тап) любое сообщение, чтобы открыть контекстное меню автора: быстрый поиск всех его сообщений, копирование имени и переход в профиль.", "Long tap any message to open author's context menu: quick search for all their messages, copy name, and view profile."));
        descTv.setTextColor(secondaryText);
        descTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        descTv.setLineSpacing(dp(2), 1.15f);
        descTv.setPadding(0, dp(12), 0, dp(16));
        tooltipCard.addView(descTv);

        TextView okBtn = new TextView(context);
        okBtn.setText(tr("Понятно", "Got it"));
        okBtn.setTextColor(isDarkTheme() ? 0xFF000000 : 0xFFFFFFFF);
        okBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        try {
            Class<?> auClass = Class.forName("org.telegram.messenger.AndroidUtilities");
            Method getTypeface = auClass.getMethod("getTypeface", String.class);
            Typeface tf = (Typeface) getTypeface.invoke(null, "fonts/rmedium.ttf");
            if (tf != null) okBtn.setTypeface(tf);
            else okBtn.setTypeface(null, Typeface.BOLD);
        } catch (Throwable t) {
            okBtn.setTypeface(null, Typeface.BOLD);
        }
        okBtn.setGravity(Gravity.CENTER);
        int btnRadius = dp(20);
        GradientDrawable btnContent = new GradientDrawable();
        btnContent.setColor(accentColor);
        btnContent.setCornerRadius(btnRadius);
        GradientDrawable btnMask = new GradientDrawable();
        btnMask.setColor(0xFFFFFFFF);
        btnMask.setCornerRadius(btnRadius);
        int rippleColor = withAlpha(isDarkTheme() ? 0xFF000000 : 0xFFFFFFFF, 0.16f);
        if (Build.VERSION.SDK_INT >= 21) {
            okBtn.setBackground(new RippleDrawable(ColorStateList.valueOf(rippleColor), btnContent, btnMask));
        } else {
            okBtn.setBackground(btnContent);
        }
        okBtn.setClickable(true);
        okBtn.setFocusable(true);
        tooltipCard.addView(okBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        int rootHeight = rootFrame.getHeight();
        int tooltipMarginTop;
        if (targetTop + targetHeight + dp(220) < rootHeight) {
            tooltipMarginTop = targetTop + targetHeight + dp(14);
        } else {
            tooltipMarginTop = Math.max(dp(24), targetTop - dp(210));
        }

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = tooltipMarginTop;
        cardLp.leftMargin = dp(20);
        cardLp.rightMargin = dp(20);
        tutorialOverlay.addView(tooltipCard, cardLp);

        final Runnable dismissTutorial = new Runnable() {
            @Override
            public void run() {
                applyBlurEffect(false);
                tutorialOverlay.animate()
                        .alpha(0f)
                        .scaleX(0.96f)
                        .scaleY(0.96f)
                        .setDuration(220)
                        .setInterpolator(EASE_IN_QUINT)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                if (tutorialOverlay.getParent() != null) {
                                    ((ViewGroup) tutorialOverlay.getParent()).removeView(tutorialOverlay);
                                }
                                if (cardSnapshot != null && !cardSnapshot.isRecycled()) {
                                    cardSnapshot.recycle();
                                }
                            }
                        })
                        .start();
            }
        };

        tutorialOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismissTutorial.run();
            }
        });

        elevatedCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismissTutorial.run();
            }
        });

        okBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performHaptic(v, HapticFeedbackConstants.KEYBOARD_TAP);
                dismissTutorial.run();
            }
        });

        rootFrame.addView(tutorialOverlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        applyBlurEffect(true);
        tutorialOverlay.setAlpha(0f);
        tutorialOverlay.animate().alpha(1f).setDuration(260).setInterpolator(EASE_OUT_QUINT).start();

        tooltipCard.setScaleX(0.85f);
        tooltipCard.setScaleY(0.85f);
        tooltipCard.animate().scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(new OvershootInterpolator(1.2f)).start();
    }

    private static long extractPeerId(Object peerObj) {
        if (peerObj == null) return 0;
        try {
            Field userF = peerObj.getClass().getField("user_id");
            long uid = userF.getLong(peerObj);
            if (uid != 0) return uid;
        } catch (Throwable ignored) {}
        try {
            Field chatF = peerObj.getClass().getField("chat_id");
            long cid = chatF.getLong(peerObj);
            if (cid != 0) return -cid;
        } catch (Throwable ignored) {}
        try {
            Field channelF = peerObj.getClass().getField("channel_id");
            long chid = channelF.getLong(peerObj);
            if (chid != 0) return -chid;
        } catch (Throwable ignored) {}
        try {
            Field idF = peerObj.getClass().getField("id");
            long id = idF.getLong(peerObj);
            if (id != 0) return id;
        } catch (Throwable ignored) {}
        return 0;
    }

    private View createAvatarView(int currentAccount, Object userOrChat, long peerId, String title) {
        int size = dp(40);
        int radius = size / 2;

        try {
            Class<?> bimgClass = Class.forName("org.telegram.ui.Components.BackupImageView");
            Constructor<?> bimgCtor = bimgClass.getConstructor(Context.class);
            View bimg = (View) bimgCtor.newInstance(context);

            Method setRoundRadius = bimgClass.getMethod("setRoundRadius", int.class);
            setRoundRadius.invoke(bimg, radius);

            Class<?> avClass = Class.forName("org.telegram.ui.Components.AvatarDrawable");
            Object avDrawable = avClass.newInstance();

            try {
                Method setTextSize = avClass.getMethod("setTextSize", int.class);
                setTextSize.invoke(avDrawable, dp(14));
            } catch (Throwable ignored) {}

            if (userOrChat != null) {
                try {
                    Method setInfo = avClass.getMethod("setInfo", int.class, userOrChat.getClass());
                    setInfo.invoke(avDrawable, currentAccount, userOrChat);
                } catch (Throwable t) {
                    try {
                        Method setInfo = avClass.getMethod("setInfo", userOrChat.getClass());
                        setInfo.invoke(avDrawable, userOrChat);
                    } catch (Throwable ignored) {}
                }

                try {
                    Method setForUserOrChat = bimgClass.getMethod("setForUserOrChat", Class.forName("org.telegram.tgnet.TLObject"), avClass);
                    setForUserOrChat.invoke(bimg, userOrChat, avDrawable);
                } catch (Throwable t) {
                    Method setImage = bimgClass.getMethod("setImageDrawable", Drawable.class);
                    setImage.invoke(bimg, avDrawable);
                }
            } else {
                try {
                    Method setInfo = avClass.getMethod("setInfo", long.class, String.class, String.class);
                    setInfo.invoke(avDrawable, peerId, title != null ? title : "", null);
                } catch (Throwable ignored) {}
                Method setImage = bimgClass.getMethod("setImageDrawable", Drawable.class);
                setImage.invoke(bimg, avDrawable);
            }

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.gravity = Gravity.TOP;
            lp.rightMargin = dp(12);
            lp.topMargin = dp(1);
            bimg.setLayoutParams(lp);
            return bimg;
        } catch (Throwable t) {
            TextView fallback = new TextView(context);
            fallback.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            fallback.setTextColor(Color.WHITE);
            fallback.setTypeface(null, Typeface.BOLD);
            fallback.setGravity(Gravity.CENTER);

            String letter = (title != null && !title.trim().isEmpty()) ? title.trim().substring(0, 1).toUpperCase(Locale.ROOT) : "?";
            fallback.setText(letter);

            int[] colors = {0xFFE17076, 0xFFFAA774, 0xFFA695E7, 0xFF7BC862, 0xFF6EC9CB, 0xFF65AADD, 0xFFEE7AAE};
            int color = colors[(int) (Math.abs(peerId) % colors.length)];

            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(color);
            fallback.setBackground(gd);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.gravity = Gravity.TOP;
            lp.rightMargin = dp(12);
            lp.topMargin = dp(1);
            fallback.setLayoutParams(lp);
            return fallback;
        }
    }

    private View createM3MessageCard(int currentAccount, final Object msg, Object userOrChat, long senderPeerId, String senderTitle, String authorUsername, String dialogTitle, final long dialogId, final int msgId, String text, String dateStr, String mediaBadge, String mediaIcon, String docSnippet, MusicInfo musicInfo, final DocumentInfo documentInfo) {
        int cardBg = getSurfaceContainerLowColor();
        int primaryColor = getPrimaryTextColor();
        int secondaryColor = getSecondaryTextColor();
        int accentColor = getAccentColor();

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, dp(6));
        card.setLayoutParams(cardLp);

        GradientDrawable cardShape = new GradientDrawable();
        cardShape.setColor(cardBg);
        cardShape.setCornerRadius(dp(16));
        cardShape.setStroke(dp(1), getOutlineVariantColor());
        if (Build.VERSION.SDK_INT >= 21) {
            GradientDrawable mask = new GradientDrawable();
            mask.setCornerRadius(dp(16));
            mask.setColor(0xFFFFFFFF);
            int rippleColor = withAlpha(accentColor, 0.16f);
            card.setBackground(new RippleDrawable(ColorStateList.valueOf(rippleColor), cardShape, mask));
            card.setElevation(dp(1.5f));
        } else {
            card.setBackground(cardShape);
        }
        card.setClickable(true);
        card.setFocusable(true);

        View avatarView = createAvatarView(currentAccount, userOrChat, senderPeerId, senderTitle);
        final int cAcc = currentAccount;
        final long sPeerId = senderPeerId;
        final Object uObj = userOrChat;
        final String sTitle = senderTitle;
        final String aUn = authorUsername;

        avatarView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                performHaptic(v, HapticFeedbackConstants.LONG_PRESS);
                showUserActionMenu(cAcc, sPeerId, uObj, sTitle, aUn, 0L, dialogId, msgId, msg);
                return true;
            }
        });
        card.addView(avatarView);

        LinearLayout contentCol = new LinearLayout(context);
        contentCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        contentCol.setLayoutParams(colLp);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleTv = new TextView(context);
        titleTv.setText(senderTitle != null ? senderTitle : "...");
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        titleTv.setTextColor(primaryColor);
        try {
            Class<?> auClass = Class.forName("org.telegram.messenger.AndroidUtilities");
            Method getTypeface = auClass.getMethod("getTypeface", String.class);
            Typeface tf = (Typeface) getTypeface.invoke(null, "fonts/rmedium.ttf");
            if (tf != null) titleTv.setTypeface(tf);
            else titleTv.setTypeface(null, Typeface.BOLD);
        } catch (Throwable t) {
            titleTv.setTypeface(null, Typeface.BOLD);
        }
        titleTv.setSingleLine(true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        titleTv.setLayoutParams(titleLp);
        titleTv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                performHaptic(v, HapticFeedbackConstants.LONG_PRESS);
                showUserActionMenu(cAcc, sPeerId, uObj, sTitle, aUn, 0L, dialogId, msgId, msg);
                return true;
            }
        });
        header.addView(titleTv);

        if (mediaBadge != null && !mediaBadge.isEmpty()) {
            TextView badgeTv = new TextView(context);
            badgeTv.setText(mediaBadge);
            badgeTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
            int badgeFg = getOnSecondaryContainerColor();
            badgeTv.setTextColor(badgeFg);
            badgeTv.setTypeface(null, Typeface.BOLD);
            badgeTv.setGravity(Gravity.CENTER);
            badgeTv.setPadding(dp(8), dp(2.5f), dp(8), dp(2.5f));

            if (mediaIcon != null) {
                badgeTv.setCompoundDrawablesWithIntrinsicBounds(new VectorIconDrawable(mediaIcon, badgeFg, dp(12)), null, null, null);
                badgeTv.setCompoundDrawablePadding(dp(4));
            }

            GradientDrawable pillBg = new GradientDrawable();
            pillBg.setShape(GradientDrawable.RECTANGLE);
            pillBg.setCornerRadius(dp(10));
            pillBg.setColor(getSecondaryContainerColor());
            pillBg.setStroke(dp(1), getOutlineVariantColor());
            badgeTv.setBackground(pillBg);

            LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            badgeLp.rightMargin = dp(6);
            badgeTv.setLayoutParams(badgeLp);
            header.addView(badgeTv);
        }

        TextView dateTv = new TextView(context);
        dateTv.setText(dateStr);
        dateTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        dateTv.setTextColor(secondaryColor);
        header.addView(dateTv);

        contentCol.addView(header);

        int highlightBg = withAlpha(accentColor, 0.28f);
        int highlightFg = accentColor;

        if (musicInfo != null && musicInfo.formattedTitle != null && !musicInfo.formattedTitle.isEmpty()) {
            LinearLayout musicBox = new LinearLayout(context);
            musicBox.setOrientation(LinearLayout.HORIZONTAL);
            musicBox.setGravity(Gravity.CENTER_VERTICAL);
            musicBox.setPadding(dp(8), dp(6), dp(8), dp(6));

            LinearLayout.LayoutParams mbLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            mbLp.setMargins(0, dp(4), 0, dp(2));
            musicBox.setLayoutParams(mbLp);

            GradientDrawable mbBg = new GradientDrawable();
            mbBg.setCornerRadius(dp(12));
            mbBg.setColor(getSecondaryContainerColor());
            mbBg.setStroke(dp(1), withAlpha(accentColor, 0.25f));
            musicBox.setBackground(mbBg);

            final ImageView playBtn = new ImageView(context);
            boolean playing = isMessagePlaying(currentAccount, msg);
            playBtn.setImageDrawable(new VectorIconDrawable(playing ? "pause" : "play", accentColor, dp(14)));
            playBtn.setScaleType(ImageView.ScaleType.CENTER);
            GradientDrawable playBg = new GradientDrawable();
            playBg.setShape(GradientDrawable.OVAL);
            playBg.setColor((accentColor & 0x00FFFFFF) | 0x24000000);
            playBtn.setBackground(playBg);
            LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(dp(28), dp(28));
            playLp.rightMargin = dp(8);
            playBtn.setLayoutParams(playLp);
            if (playing) {
                playingIconView = playBtn;
            }
            playBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                    toggleAudioPlayback(cAcc, msg, playBtn);
                }
            });
            musicBox.addView(playBtn);

            TextView trackTv = new TextView(context);
            trackTv.setText(highlightText(musicInfo.formattedTitle, query, highlightBg, highlightFg));
            trackTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            trackTv.setTextColor(primaryColor);
            trackTv.setTypeface(null, Typeface.BOLD);
            trackTv.setSingleLine(true);
            trackTv.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            trackTv.setLayoutParams(trackLp);
            musicBox.addView(trackTv);

            if (musicInfo.duration != null && !musicInfo.duration.isEmpty()) {
                TextView durTv = new TextView(context);
                durTv.setText(musicInfo.duration);
                durTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
                durTv.setTextColor(secondaryColor);
                durTv.setTypeface(null, Typeface.BOLD);
                LinearLayout.LayoutParams durLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                durLp.leftMargin = dp(8);
                durTv.setLayoutParams(durLp);
                musicBox.addView(durTv);
            }

            contentCol.addView(musicBox);
        } else if (isVoiceMessage(msg)) {
            LinearLayout voiceBox = new LinearLayout(context);
            voiceBox.setOrientation(LinearLayout.HORIZONTAL);
            voiceBox.setGravity(Gravity.CENTER_VERTICAL);
            voiceBox.setPadding(dp(8), dp(6), dp(8), dp(6));

            LinearLayout.LayoutParams vbLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            vbLp.setMargins(0, dp(4), 0, dp(2));
            voiceBox.setLayoutParams(vbLp);

            GradientDrawable vbBg = new GradientDrawable();
            vbBg.setCornerRadius(dp(12));
            vbBg.setColor(getSecondaryContainerColor());
            vbBg.setStroke(dp(1), withAlpha(accentColor, 0.25f));
            voiceBox.setBackground(vbBg);

            final ImageView voicePlayBtn = new ImageView(context);
            boolean playing = isMessagePlaying(currentAccount, msg);
            voicePlayBtn.setImageDrawable(new VectorIconDrawable(playing ? "pause" : "play", accentColor, dp(14)));
            voicePlayBtn.setScaleType(ImageView.ScaleType.CENTER);
            GradientDrawable playBg = new GradientDrawable();
            playBg.setShape(GradientDrawable.OVAL);
            playBg.setColor((accentColor & 0x00FFFFFF) | 0x24000000);
            voicePlayBtn.setBackground(playBg);
            LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(dp(28), dp(28));
            playLp.rightMargin = dp(8);
            voicePlayBtn.setLayoutParams(playLp);
            if (playing) {
                playingIconView = voicePlayBtn;
            }

            final int dur = extractVoiceDuration(msg);
            final TextView durTv = new TextView(context);
            if (dur > 0) {
                durTv.setText(formatVoiceDuration(dur));
                durTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
                durTv.setTextColor(secondaryColor);
                durTv.setTypeface(null, Typeface.BOLD);
                LinearLayout.LayoutParams durLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                durLp.leftMargin = dp(8);
                durTv.setLayoutParams(durLp);
            }

            voicePlayBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                    toggleAudioPlayback(cAcc, msg, voicePlayBtn, durTv, dur);
                }
            });
            voiceBox.addView(voicePlayBtn);

            TextView voiceTv = new TextView(context);
            voiceTv.setText(tr("Голосовое сообщение", "Voice message"));
            voiceTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            voiceTv.setTextColor(primaryColor);
            voiceTv.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams voiceLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            voiceTv.setLayoutParams(voiceLp);
            voiceBox.addView(voiceTv);

            final byte[] rawWaveform = extractVoiceWaveform(msg);
            final float[] waveHeights = decodeWaveformHeights(rawWaveform, 22);
            final int waveColor = accentColor;
            final int thisMsgId = msgId;

            final View waveformView = new View(context) {
                private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

                @Override
                protected void onDraw(Canvas canvas) {
                    super.onDraw(canvas);
                    int w = getWidth();
                    int h = getHeight();
                    if (w <= 0 || h <= 0) return;
                    int count = waveHeights.length;
                    float barW = dp(2f);
                    float gap = (w - (count * barW)) / Math.max(1, count - 1);
                    float curProgress = 0f;
                    Object tag = getTag();
                    if (tag instanceof Float) {
                        curProgress = (Float) tag;
                    }

                    p.setStyle(Paint.Style.FILL);
                    p.setColor(withAlpha(waveColor, 0.38f));
                    for (int i = 0; i < count; i++) {
                        float barH = Math.max(dp(2.5f), h * waveHeights[i]);
                        float left = i * (barW + gap);
                        float top = (h - barH) / 2.0f;
                        RectF r = new RectF(left, top, left + barW, top + barH);
                        canvas.drawRoundRect(r, barW / 2.0f, barW / 2.0f, p);
                    }

                    if (curProgress > 0.001f) {
                        float splitX = curProgress * w;
                        canvas.save();
                        canvas.clipRect(0f, 0f, splitX, (float) h);
                        p.setColor(waveColor);
                        for (int i = 0; i < count; i++) {
                            float barH = Math.max(dp(2.5f), h * waveHeights[i]);
                            float left = i * (barW + gap);
                            if (left > splitX) break;
                            float top = (h - barH) / 2.0f;
                            RectF r = new RectF(left, top, left + barW, top + barH);
                            canvas.drawRoundRect(r, barW / 2.0f, barW / 2.0f, p);
                        }
                        canvas.restore();
                    }
                }

                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    int action = event.getAction();
                    if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
                        float x = event.getX();
                        float w = getWidth();
                        float progress = Math.max(0f, Math.min(1f, w > 0 ? (x / w) : 0f));
                        setTag(progress);
                        if (thisMsgId == activeVoiceMsgId) {
                            activeVoiceTargetProgress = progress;
                            activeVoiceRenderProgress = progress;
                            activeVoiceLastQueryTime = System.currentTimeMillis();
                        }
                        invalidate();
                        if (durTv != null && dur > 0) {
                            int sec = (int) (progress * dur);
                            durTv.setText(formatVoiceDuration(sec));
                        }
                        if (action == MotionEvent.ACTION_UP) {
                            performHaptic(this, HapticFeedbackConstants.VIRTUAL_KEY);
                            boolean currentlyPlaying = isMessagePlaying(cAcc, msg);
                            if (!currentlyPlaying) {
                                toggleAudioPlayback(cAcc, msg, voicePlayBtn, durTv, dur);
                            }
                            seekVoiceMessage(cAcc, msg, progress);
                        }
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    }
                    return super.onTouchEvent(event);
                }
            };
            waveformView.setTag(0f);
            activeWaveformViews.put(thisMsgId, waveformView);

            waveformView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    activeWaveformViews.put(thisMsgId, v);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    activeWaveformViews.remove(thisMsgId);
                }
            });

            LinearLayout.LayoutParams wfLp = new LinearLayout.LayoutParams(dp(68), dp(16));
            wfLp.leftMargin = dp(6);
            wfLp.rightMargin = dp(4);
            waveformView.setLayoutParams(wfLp);
            voiceBox.addView(waveformView);

            if (dur > 0) {
                voiceBox.addView(durTv);
            }

            contentCol.addView(voiceBox);
        } else if (documentInfo != null && documentInfo.fileName != null && !documentInfo.fileName.isEmpty()) {
            LinearLayout docBox = new LinearLayout(context);
            docBox.setOrientation(LinearLayout.HORIZONTAL);
            docBox.setGravity(Gravity.CENTER_VERTICAL);
            docBox.setPadding(dp(8), dp(6), dp(8), dp(6));

            LinearLayout.LayoutParams dbLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dbLp.setMargins(0, dp(4), 0, dp(2));
            docBox.setLayoutParams(dbLp);

            GradientDrawable dbBg = new GradientDrawable();
            dbBg.setCornerRadius(dp(10));
            dbBg.setColor((accentColor & 0x00FFFFFF) | 0x14000000);
            dbBg.setStroke(dp(1), (accentColor & 0x00FFFFFF) | 0x30000000);
            docBox.setBackground(dbBg);

            ImageView docIv = new ImageView(context);
            docIv.setImageDrawable(new VectorIconDrawable("document", accentColor, dp(15)));
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(15), dp(15));
            iconLp.rightMargin = dp(8);
            docIv.setLayoutParams(iconLp);
            docBox.addView(docIv);

            TextView docNameTv = new TextView(context);
            docNameTv.setText(highlightText(documentInfo.fileName, query, highlightBg, highlightFg));
            docNameTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            docNameTv.setTextColor(primaryColor);
            docNameTv.setTypeface(null, Typeface.BOLD);
            docNameTv.setSingleLine(true);
            docNameTv.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams docNameLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            docNameTv.setLayoutParams(docNameLp);
            docBox.addView(docNameTv);

            if (documentInfo.fileSize != null && !documentInfo.fileSize.isEmpty()) {
                TextView sizeTv = new TextView(context);
                sizeTv.setText(documentInfo.fileSize);
                sizeTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
                sizeTv.setTextColor(secondaryColor);
                sizeTv.setTypeface(null, Typeface.BOLD);
                LinearLayout.LayoutParams sizeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                sizeLp.leftMargin = dp(8);
                sizeTv.setLayoutParams(sizeLp);
                docBox.addView(sizeTv);
            }

            if (documentInfo.document != null) {
                final FrameLayout actionFrame = new FrameLayout(context);
                LinearLayout.LayoutParams frameLp = new LinearLayout.LayoutParams(dp(44), dp(44));
                frameLp.leftMargin = dp(2);
                frameLp.topMargin = -dp(6);
                frameLp.bottomMargin = -dp(6);
                frameLp.rightMargin = -dp(4);
                actionFrame.setLayoutParams(frameLp);

                final ImageView fileActionBtn = new ImageView(context);
                final boolean isDownloaded = isDocumentDownloaded(currentAccount, documentInfo.document);
                fileActionBtn.setImageDrawable(new VectorIconDrawable(isDownloaded ? "more" : "download", accentColor, dp(17)));
                fileActionBtn.setScaleType(ImageView.ScaleType.CENTER);
                fileActionBtn.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                actionFrame.addView(fileActionBtn);

                if (Build.VERSION.SDK_INT >= 21) {
                    GradientDrawable mask = new GradientDrawable();
                    mask.setShape(GradientDrawable.OVAL);
                    mask.setColor(Color.WHITE);
                    actionFrame.setBackground(new RippleDrawable(ColorStateList.valueOf((accentColor & 0x00FFFFFF) | 0x24000000), null, mask));
                }

                final ProgressBar filePb = new ProgressBar(context);
                filePb.setVisibility(View.GONE);
                FrameLayout.LayoutParams pbLp = new FrameLayout.LayoutParams(dp(20), dp(20));
                pbLp.gravity = Gravity.CENTER;
                filePb.setLayoutParams(pbLp);
                try {
                    filePb.setIndeterminate(true);
                    if (Build.VERSION.SDK_INT >= 21) {
                        filePb.setIndeterminateTintList(ColorStateList.valueOf(accentColor));
                    }
                } catch (Throwable ignored) {}
                actionFrame.addView(filePb);

                actionFrame.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                        boolean downloadedNow = isDocumentDownloaded(cAcc, documentInfo.document);
                        if (downloadedNow) {
                            showFileActionMenu(cAcc, documentInfo.document, msg, documentInfo.fileName, new Runnable() {
                                @Override
                                public void run() {
                                    boolean stillDownloaded = isDocumentDownloaded(cAcc, documentInfo.document);
                                    fileActionBtn.setImageDrawable(new VectorIconDrawable(stillDownloaded ? "more" : "download", accentColor, dp(15)));
                                }
                            });
                        } else {
                            fileActionBtn.setVisibility(View.GONE);
                            filePb.setVisibility(View.VISIBLE);
                            downloadDocument(cAcc, documentInfo.document, msg);
                            final Runnable pollDownload = new Runnable() {
                                private int checkCount = 0;
                                @Override
                                public void run() {
                                    checkCount++;
                                    boolean done = isDocumentDownloaded(cAcc, documentInfo.document);
                                    if (done || checkCount > 240) {
                                        filePb.setVisibility(View.GONE);
                                        fileActionBtn.setVisibility(View.VISIBLE);
                                        if (done) {
                                            fileActionBtn.setImageDrawable(new VectorIconDrawable("check", 0xFF4CAF50, dp(15)));
                                            performHaptic(fileActionBtn, HapticFeedbackConstants.VIRTUAL_KEY);
                                            fileActionBtn.animate().scaleX(1.25f).scaleY(1.25f).setDuration(120).withEndAction(new Runnable() {
                                                @Override
                                                public void run() {
                                                    fileActionBtn.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).withEndAction(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            fileActionBtn.setImageDrawable(new VectorIconDrawable("more", accentColor, dp(15)));
                                                        }
                                                    }).start();
                                                }
                                            }).start();
                                        } else {
                                            fileActionBtn.setImageDrawable(new VectorIconDrawable("download", accentColor, dp(15)));
                                        }
                                    } else {
                                        mainHandler.postDelayed(this, 250);
                                    }
                                }
                            };
                            mainHandler.postDelayed(pollDownload, 250);
                        }
                    }
                });
                docBox.addView(actionFrame);
            }

            contentCol.addView(docBox);
        }

        if (text != null && !text.trim().isEmpty()) {
            TextView bodyTv = new TextView(context);
            bodyTv.setText(highlightText(text.trim(), query, highlightBg, highlightFg));
            bodyTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            bodyTv.setTextColor(primaryColor);
            bodyTv.setMaxLines(3);
            bodyTv.setLineSpacing(dp(1), 1.1f);
            bodyTv.setPadding(0, dp(3), 0, dp(1));
            contentCol.addView(bodyTv);
        }

        if (docSnippet != null && !docSnippet.isEmpty()) {
            TextView snippetTv = new TextView(context);
            snippetTv.setText(highlightText(docSnippet, query, highlightBg, highlightFg));
            snippetTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            snippetTv.setTextColor(accentColor);
            snippetTv.setTypeface(null, Typeface.ITALIC);
            snippetTv.setCompoundDrawablesWithIntrinsicBounds(new VectorIconDrawable("document", accentColor, dp(12)), null, null, null);
            snippetTv.setCompoundDrawablePadding(dp(4));
            snippetTv.setPadding(0, dp(2), 0, dp(2));
            contentCol.addView(snippetTv);
        }

        if (dialogTitle != null && !dialogTitle.isEmpty()) {
            TextView chatTv = new TextView(context);
            chatTv.setText(tr("в ", "in ") + dialogTitle);
            chatTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            chatTv.setTextColor(secondaryColor);
            chatTv.setSingleLine(true);
            chatTv.setPadding(0, dp(2), 0, 0);
            contentCol.addView(chatTv);
        }

        card.addView(contentCol);

        View thumbView = createMediaThumbnailView(currentAccount, msg, dialogId, msgId);
        if (thumbView != null) {
            card.addView(thumbView);
        }

        card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                performHaptic(v, HapticFeedbackConstants.LONG_PRESS);
                showUserActionMenu(cAcc, sPeerId, uObj, sTitle, aUn, 0L, dialogId, msgId, msg);
                return true;
            }
        });

        return card;
    }

    private void jumpToMessage(final long targetPeerId, final int targetMsgId) {
        stopAudioPlayback();
        if (currentDialog != null) currentDialog.dismiss();
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Class<?> chatActivityClass = Class.forName("org.telegram.ui.ChatActivity");
                    Constructor<?> ctor = chatActivityClass.getConstructor(Bundle.class);
                    Bundle args = new Bundle();
                    if (targetPeerId > 0) {
                        args.putLong("user_id", targetPeerId);
                    } else if (targetPeerId < 0) {
                        args.putLong("chat_id", -targetPeerId);
                    }
                    args.putLong("dialog_id", targetPeerId);
                    if (targetMsgId != 0) {
                        args.putInt("message_id", targetMsgId);
                    }
                    Object chatFrag = ctor.newInstance(args);
                    presentFragment(chatFrag);
                } catch (Throwable ignored) {}
            }
        }, 120);
    }

    private void presentFragment(Object fragment) {
        if (fragment == null) return;
        try {
            Class<?> laClass = Class.forName("org.telegram.ui.LaunchActivity");
            Object laInstance = null;
            try {
                Field f = laClass.getField("instance");
                laInstance = f.get(null);
            } catch (Throwable ignored) {}

            if (laInstance == null) {
                Context c = context;
                while (c instanceof android.content.ContextWrapper) {
                    if (c instanceof android.app.Activity) {
                        laInstance = c;
                        break;
                    }
                    c = ((android.content.ContextWrapper) c).getBaseContext();
                }
            }

            Class<?> baseFragClass = Class.forName("org.telegram.ui.ActionBar.BaseFragment");

            if (laInstance != null) {
                try {
                    Method pf = laInstance.getClass().getMethod("presentFragment", baseFragClass);
                    pf.invoke(laInstance, fragment);
                    return;
                } catch (Throwable t) {
                    try {
                        Method pf = laInstance.getClass().getMethod("presentFragment", baseFragClass, boolean.class, boolean.class);
                        pf.invoke(laInstance, fragment, false, false);
                        return;
                    } catch (Throwable ignored) {}
                }
            }

            try {
                Method getLastFrag = laClass.getMethod("getLastFragment");
                Object lastFrag = getLastFrag.invoke(null);
                if (lastFrag != null) {
                    Method pf = lastFrag.getClass().getMethod("presentFragment", baseFragClass);
                    pf.invoke(lastFrag, fragment);
                    return;
                }
            } catch (Throwable ignored) {}

            try {
                Method getSafeLastFrag = laClass.getMethod("getSafeLastFragment");
                Object safeFrag = getSafeLastFrag.invoke(null);
                if (safeFrag != null) {
                    Method pf = safeFrag.getClass().getMethod("presentFragment", baseFragClass);
                    pf.invoke(safeFrag, fragment);
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    private void showUserActionMenu(final int currentAccount, final long targetId, final Object userOrChat, final String displayName, final String username, final long accessHash) {
        showUserActionMenu(currentAccount, targetId, userOrChat, displayName, username, accessHash, 0L, 0, null);
    }

    private void showUserActionMenu(final int currentAccount, final long targetId, final Object userOrChat, final String displayName, final String username, final long accessHash, final long dialogId, final int msgId, final Object msg) {
        if (targetId == 0 && dialogId == 0) return;

        final Dialog menuDialog = new Dialog(context, android.R.style.Theme_Translucent_NoTitleBar);
        final FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(0x7F000000);

        int surfaceColor = isDarkTheme() ? getMonetColor("system_neutral1_900", 0xFF141218) : getMonetColor("system_neutral1_50", 0xFFFDF8FD);
        int primaryText = getPrimaryTextColor();
        int secondaryText = getSecondaryTextColor();
        int accentColor = getAccentColor();

        final int navHeight = getNavigationBarHeight(context);
        final LinearLayout sheet = new LinearLayout(context);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(surfaceColor);
        sheetBg.setCornerRadii(new float[]{dp(28), dp(28), dp(28), dp(28), 0, 0, 0, 0});
        sheet.setBackground(sheetBg);
        sheet.setPadding(dp(18), dp(14), dp(18), dp(16) + navHeight);
        sheet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {}
        });

        FrameLayout.LayoutParams sheetLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        sheet.setLayoutParams(sheetLp);

        final Runnable closeMenuAnim = new Runnable() {
            private boolean closing = false;
            @Override
            public void run() {
                if (closing) return;
                closing = true;
                applyBlurEffect(false);
                root.animate().alpha(0f).setDuration(240).setInterpolator(EASE_IN_QUINT).start();
                sheet.animate()
                        .translationY(dp(320) + navHeight)
                        .setDuration(240)
                        .setInterpolator(EASE_IN_QUINT)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    menuDialog.dismiss();
                                } catch (Throwable ignored) {}
                            }
                        }).start();
            }
        };

        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                closeMenuAnim.run();
            }
        });

        View handle = new View(context);
        GradientDrawable hBg = new GradientDrawable();
        hBg.setColor(0x44938F99);
        hBg.setCornerRadius(dp(2));
        handle.setBackground(hBg);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(dp(36), dp(4));
        hLp.gravity = Gravity.CENTER_HORIZONTAL;
        hLp.bottomMargin = dp(14);
        handle.setLayoutParams(hLp);
        sheet.addView(handle);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(14));

        View av = createAvatarView(currentAccount, userOrChat, targetId, displayName);
        header.addView(av);

        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView nameTv = new TextView(context);
        nameTv.setText(displayName != null && !displayName.isEmpty() ? displayName : ("ID: " + targetId));
        nameTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f);
        nameTv.setTextColor(primaryText);
        nameTv.setTypeface(null, Typeface.BOLD);
        titles.addView(nameTv);

        LinearLayout tagsRow = new LinearLayout(context);
        tagsRow.setOrientation(LinearLayout.HORIZONTAL);
        tagsRow.setPadding(0, dp(4), 0, 0);

        if (username != null && !username.isEmpty()) {
            final String cleanUn = username.startsWith("@") ? username : ("@" + username);
            TextView unBadge = new TextView(context);
            unBadge.setText(cleanUn);
            unBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            unBadge.setTextColor(accentColor);
            unBadge.setTypeface(null, Typeface.BOLD);
            unBadge.setPadding(dp(7), dp(2), dp(7), dp(2));
            GradientDrawable unBg = new GradientDrawable();
            unBg.setCornerRadius(dp(8));
            unBg.setColor((accentColor & 0x00FFFFFF) | 0x22000000);
            unBg.setStroke(dp(1), (accentColor & 0x00FFFFFF) | 0x44000000);
            unBadge.setBackground(unBg);
            LinearLayout.LayoutParams unLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            unLp.rightMargin = dp(6);
            unBadge.setLayoutParams(unLp);
            tagsRow.addView(unBadge);
        }

        TextView idBadge = new TextView(context);
        idBadge.setText("ID: " + Math.abs(targetId));
        idBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        idBadge.setTextColor(secondaryText);
        idBadge.setPadding(dp(7), dp(2), dp(7), dp(2));
        GradientDrawable idBg = new GradientDrawable();
        idBg.setCornerRadius(dp(8));
        idBg.setColor(isDarkTheme() ? 0x18FFFFFF : 0x0E000000);
        idBg.setStroke(dp(1), isDarkTheme() ? 0x22FFFFFF : 0x14000000);
        idBadge.setBackground(idBg);
        tagsRow.addView(idBadge);

        titles.addView(tagsRow);
        header.addView(titles);
        sheet.addView(header);

        View headerDiv = new View(context);
        headerDiv.setBackgroundColor(isDarkTheme() ? 0x1FFFFFFF : 0x14000000);
        sheet.addView(headerDiv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        addUserMenuItem(sheet, "user", tr("Открыть профиль", "Open profile"), primaryText, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                closeMenuAnim.run();
                openProfile(targetId);
            }
        });

        addUserMenuItem(sheet, "message", tr("Написать сообщение", "Send message"), primaryText, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                closeMenuAnim.run();
                openChat(targetId);
            }
        });

        addUserMenuItem(sheet, "search", tr("Искать сообщения этого автора", "Search messages from this author"), accentColor, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                closeMenuAnim.run();
                filterByUserId = targetId;
                filterByUserAccessHash = accessHash;
                selectedUserObject = userOrChat;
                filterByUserName = (username != null && !username.isEmpty()) ? (username.startsWith("@") ? username : ("@" + username)) : displayName;
                updateUserFilterChip();
                triggerSearchDelayed();
            }
        });

        if (username != null && !username.isEmpty()) {
            final String cleanUn = username.startsWith("@") ? username : ("@" + username);
            addUserMenuItem(sheet, "copy", tr("Скопировать ", "Copy ") + cleanUn, primaryText, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                    closeMenuAnim.run();
                    copyToClipboard(cleanUn, tr("Юзернейм скопирован", "Username copied"));
                }
            });
        }

        addUserMenuItem(sheet, "id", tr("Скопировать ID (", "Copy ID (") + Math.abs(targetId) + ")", primaryText, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                closeMenuAnim.run();
                copyToClipboard(String.valueOf(Math.abs(targetId)), tr("ID скопирован", "ID copied"));
            }
        });

        if (displayName != null && !displayName.isEmpty()) {
            addUserMenuItem(sheet, "tag", tr("Скопировать имя", "Copy name"), primaryText, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                    closeMenuAnim.run();
                    copyToClipboard(displayName, tr("Имя скопировано", "Name copied"));
                }
            });
        }

        if (msgId > 0 && dialogId != 0) {
            final String msgLink = getMessageLink(userOrChat, username, dialogId, msgId);
            if (msgLink != null) {
                addUserMenuItem(sheet, "link", tr("Скопировать ссылку на сообщение", "Copy link to message"), primaryText, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                        closeMenuAnim.run();
                        copyToClipboard(msgLink, tr("Ссылка на сообщение скопирована", "Message link copied"));
                    }
                });
            }
        }

        if (msg != null) {
            final DocumentInfo docInfo = extractDocumentInfo(msg);
            if (docInfo != null && docInfo.document != null) {
                if (isDocumentDownloaded(currentAccount, docInfo.document)) {
                    addUserMenuItem(sheet, "open", tr("Открыть файл", "Open file"), primaryText, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                            closeMenuAnim.run();
                            openDownloadedFile(currentAccount, docInfo.document, msg, docInfo.fileName);
                        }
                    });
                    addUserMenuItem(sheet, "share", tr("Поделиться файлом", "Share file"), primaryText, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                            closeMenuAnim.run();
                            shareDownloadedFile(currentAccount, docInfo.document, docInfo.fileName);
                        }
                    });
                    addUserMenuItem(sheet, "download", tr("Сохранить в Загрузки", "Save to Downloads"), primaryText, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                            closeMenuAnim.run();
                            saveDownloadedFileToDownloads(currentAccount, docInfo.document, docInfo.fileName);
                        }
                    });
                    addUserMenuItem(sheet, "trash", tr("Удалить из памяти", "Delete from storage"), 0xFFFF5252, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                            closeMenuAnim.run();
                            deleteDownloadedFile(currentAccount, docInfo.document);
                        }
                    });
                } else {
                    addUserMenuItem(sheet, "download", tr("Скачать файл", "Download file"), accentColor, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                            closeMenuAnim.run();
                            downloadDocument(currentAccount, docInfo.document, msg);
                        }
                    });
                }
            }
        }

        root.addView(sheet);
        menuDialog.setContentView(root);

        applyBlurEffect(true);
        menuDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                applyBlurEffect(false);
            }
        });

        setupDialogWindow(menuDialog);

        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(240).setInterpolator(EASE_OUT_QUINT).withLayer().start();
        sheet.setTranslationY(dp(320) + navHeight);
        sheet.animate().translationY(0f).setDuration(240).setInterpolator(EASE_OUT_QUINT).withLayer().start();

        menuDialog.show();
    }

    private void showFileActionMenu(final int currentAccount, final Object document, final Object msg, final String fileName, final Runnable onStateChanged) {
        if (document == null) return;
        final Dialog menuDialog = new Dialog(context, 0);
        menuDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(0x66000000);

        final LinearLayout sheet = new LinearLayout(context);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(16), dp(16), dp(16), dp(16));

        int sheetBg = getSurfaceContainerColor();
        GradientDrawable sheetShape = new GradientDrawable();
        sheetShape.setColor(sheetBg);
        sheetShape.setCornerRadii(new float[]{dp(24), dp(24), dp(24), dp(24), 0, 0, 0, 0});
        sheet.setBackground(sheetShape);

        int navHeight = getNavigationBarHeight(context);
        FrameLayout.LayoutParams sheetLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sheetLp.gravity = Gravity.BOTTOM;
        sheetLp.bottomMargin = navHeight;
        sheet.setLayoutParams(sheetLp);

        View handle = new View(context);
        GradientDrawable handleShape = new GradientDrawable();
        handleShape.setColor(getOutlineVariantColor());
        handleShape.setCornerRadius(dp(2));
        handle.setBackground(handleShape);
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(36), dp(4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = dp(14);
        sheet.addView(handle, handleLp);

        TextView titleTv = new TextView(context);
        titleTv.setText(fileName != null ? fileName : tr("Управление файлом", "File management"));
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        titleTv.setTextColor(getPrimaryTextColor());
        titleTv.setTypeface(null, Typeface.BOLD);
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        titleTv.setPadding(dp(4), 0, dp(4), dp(12));
        sheet.addView(titleTv);

        final Runnable closeMenuAnim = new Runnable() {
            @Override
            public void run() {
                sheet.animate().translationY(dp(320)).setDuration(180).setInterpolator(EASE_IN_QUINT).withLayer().start();
                root.animate().alpha(0f).setDuration(180).setInterpolator(EASE_IN_QUINT).withLayer().withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            menuDialog.dismiss();
                        } catch (Throwable ignored) {}
                    }
                }).start();
            }
        };

        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeMenuAnim.run();
            }
        });

        int accentColor = getAccentColor();
        int primaryText = getPrimaryTextColor();
        boolean downloaded = isDocumentDownloaded(currentAccount, document);

        if (downloaded) {
            addUserMenuItem(sheet, "open", tr("Открыть файл", "Open file"), primaryText, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                    closeMenuAnim.run();
                    openDownloadedFile(currentAccount, document, msg, fileName);
                }
            });

            addUserMenuItem(sheet, "share", tr("Поделиться файлом", "Share file"), primaryText, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                    closeMenuAnim.run();
                    shareDownloadedFile(currentAccount, document, fileName);
                }
            });

            addUserMenuItem(sheet, "download", tr("Сохранить в Загрузки", "Save to Downloads"), primaryText, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                    closeMenuAnim.run();
                    saveDownloadedFileToDownloads(currentAccount, document, fileName);
                }
            });

            addUserMenuItem(sheet, "trash", tr("Удалить из памяти", "Delete from storage"), 0xFFFF5252, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                    closeMenuAnim.run();
                    boolean deleted = deleteDownloadedFile(currentAccount, document);
                    if (deleted && onStateChanged != null) {
                        onStateChanged.run();
                    }
                }
            });
        } else {
            addUserMenuItem(sheet, "download", tr("Скачать файл", "Download file"), accentColor, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    performHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                    closeMenuAnim.run();
                    downloadDocument(currentAccount, document, msg);
                    if (onStateChanged != null) {
                        onStateChanged.run();
                    }
                }
            });
        }

        root.addView(sheet);
        menuDialog.setContentView(root);

        applyBlurEffect(true);
        menuDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                applyBlurEffect(false);
            }
        });

        setupDialogWindow(menuDialog);

        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(240).setInterpolator(EASE_OUT_QUINT).withLayer().start();
        sheet.setTranslationY(dp(320) + navHeight);
        sheet.animate().translationY(0f).setDuration(240).setInterpolator(EASE_OUT_QUINT).withLayer().start();

        menuDialog.show();
    }

    private ValueAnimator blurAnimator = null;
    private float currentBlurRadius = 0f;

    private void applyBlurEffect(boolean enable) {
        if (rootContainer == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (blurAnimator != null) {
                    blurAnimator.cancel();
                    blurAnimator = null;
                }

                float targetRadius = dp(18);
                float startRadius = currentBlurRadius;
                float endRadius = enable ? targetRadius : 0f;

                blurAnimator = ValueAnimator.ofFloat(startRadius, endRadius);
                blurAnimator.setDuration(240);
                blurAnimator.setInterpolator(enable ? EASE_OUT_QUINT : EASE_IN_QUINT);
                blurAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        currentBlurRadius = (float) animation.getAnimatedValue();
                        try {
                            if (currentBlurRadius > 0.1f) {
                                RenderEffect blurEffect = RenderEffect.createBlurEffect(currentBlurRadius, currentBlurRadius, Shader.TileMode.CLAMP);
                                rootContainer.setRenderEffect(blurEffect);
                            } else {
                                rootContainer.setRenderEffect(null);
                            }
                        } catch (Throwable ignored) {}
                    }
                });
                blurAnimator.start();
            } catch (Throwable ignored) {}
        }
    }

    private void addUserMenuItem(LinearLayout parent, String iconType, String title, int textColor, final View.OnClickListener listener) {
        if (parent.getChildCount() > 2) {
            View divider = new View(context);
            divider.setBackgroundColor(isDarkTheme() ? 0x10FFFFFF : 0x0A000000);
            LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            divLp.leftMargin = dp(52);
            divLp.topMargin = dp(1);
            divLp.bottomMargin = dp(1);
            parent.addView(divider, divLp);
        }

        final LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(8), dp(9), dp(8), dp(9));

        GradientDrawable normalBg = new GradientDrawable();
        normalBg.setCornerRadius(dp(12));
        normalBg.setColor(Color.TRANSPARENT);

        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(dp(12));
        mask.setColor(Color.WHITE);

        RippleDrawable ripple = new RippleDrawable(ColorStateList.valueOf(0x25FFFFFF), normalBg, mask);
        item.setBackground(ripple);

        FrameLayout iconFrame = new FrameLayout(context);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setCornerRadius(dp(10));
        iconBg.setColor(isDarkTheme() ? 0x14FFFFFF : 0x0A000000);
        iconFrame.setBackground(iconBg);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        iconLp.rightMargin = dp(14);
        iconFrame.setLayoutParams(iconLp);

        ImageView iconIv = new ImageView(context);
        iconIv.setImageDrawable(new VectorIconDrawable(iconType, textColor, dp(19)));
        FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        ivLp.gravity = Gravity.CENTER;
        iconFrame.addView(iconIv, ivLp);
        item.addView(iconFrame);

        TextView titleTv = new TextView(context);
        titleTv.setText(title);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        titleTv.setTextColor(textColor);
        titleTv.setTypeface(null, Typeface.NORMAL);
        item.addView(titleTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performHaptic(v, HapticFeedbackConstants.KEYBOARD_TAP);
                item.setScaleX(0.96f);
                item.setScaleY(0.96f);
                item.animate().scaleX(1f).scaleY(1f).setDuration(160).setInterpolator(EASE_OUT_QUINT).withLayer().start();
                if (listener != null) listener.onClick(v);
            }
        });

        parent.addView(item);
    }

    private void openProfile(final long dialogId) {
        if (currentDialog != null) currentDialog.dismiss();
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Class<?> profileActivityClass = Class.forName("org.telegram.ui.ProfileActivity");
                    Constructor<?> ctor = profileActivityClass.getConstructor(Bundle.class);
                    Bundle args = new Bundle();
                    if (dialogId > 0) args.putLong("user_id", dialogId);
                    else args.putLong("chat_id", -dialogId);
                    args.putLong("dialog_id", dialogId);
                    Object profFrag = ctor.newInstance(args);
                    presentFragment(profFrag);
                } catch (Throwable ignored) {}
            }
        }, 120);
    }

    private void openChat(final long dialogId) {
        if (currentDialog != null) currentDialog.dismiss();
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Class<?> chatActivityClass = Class.forName("org.telegram.ui.ChatActivity");
                    Constructor<?> ctor = chatActivityClass.getConstructor(Bundle.class);
                    Bundle args = new Bundle();
                    if (dialogId > 0) args.putLong("user_id", dialogId);
                    else args.putLong("chat_id", -dialogId);
                    args.putLong("dialog_id", dialogId);
                    Object chatFrag = ctor.newInstance(args);
                    presentFragment(chatFrag);
                } catch (Throwable ignored) {}
            }
        }, 120);
    }

    private void copyToClipboard(String text, String toastMsg) {
        try {
            if (text != null && !text.isEmpty()) {
                ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData cd = ClipData.newPlainText("text", text);
                cm.setPrimaryClip(cd);
                if (toastMsg != null && !toastMsg.isEmpty()) {
                    android.widget.Toast.makeText(context, toastMsg, android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Throwable ignored) {}
    }

    private int getAccentColor() {
        int themeAccent = getThemeColor("featuredStickers_addButton", 0);
        if (themeAccent == 0) themeAccent = getThemeColor("windowBackgroundWhiteBlueText4", 0);
        if (themeAccent == 0) themeAccent = getThemeColor("switchTrackChecked", 0);
        if (themeAccent == 0) themeAccent = getThemeColor("actionBarDefault", 0);
        if (themeAccent != 0) return themeAccent;
        return getMonetColor("system_accent1_600", 0xFF50A8EB);
    }

    private int getSurfaceColor() {
        int winBg = getThemeColor("windowBackgroundWhite", 0);
        if (winBg != 0) return winBg;
        return isDarkTheme() ? getMonetColor("system_neutral1_900", 0xFF141218) : getMonetColor("system_neutral1_50", 0xFFFDF8FD);
    }

    private int getSurfaceContainerLowestColor() {
        int bg = getSurfaceColor();
        return isDarkTheme() ? adjustLuminance(bg, -8) : adjustLuminance(bg, 14);
    }

    private int getSurfaceContainerLowColor() {
        int bg = getSurfaceColor();
        return isDarkTheme() ? adjustLuminance(bg, 10) : adjustLuminance(bg, -4);
    }

    private int getSurfaceContainerColor() {
        int bg = getSurfaceColor();
        return isDarkTheme() ? adjustLuminance(bg, 16) : adjustLuminance(bg, -8);
    }

    private int getSurfaceContainerHighColor() {
        int bg = getSurfaceColor();
        return isDarkTheme() ? adjustLuminance(bg, 24) : adjustLuminance(bg, -14);
    }

    private int getSurfaceContainerHighestColor() {
        int bg = getSurfaceColor();
        return isDarkTheme() ? adjustLuminance(bg, 32) : adjustLuminance(bg, -20);
    }

    private int getPrimaryContainerColor() {
        int primary = getAccentColor();
        return withAlpha(primary, isDarkTheme() ? 0.24f : 0.16f);
    }

    private int getOnPrimaryContainerColor() {
        int primary = getAccentColor();
        return isDarkTheme() ? adjustLuminance(primary, 40) : adjustLuminance(primary, -30);
    }

    private int getSecondaryContainerColor() {
        int sec = getMonetColor("system_accent2_600", 0);
        if (sec == 0) sec = getThemeColor("chat_messageLinkIn", 0);
        if (sec == 0) sec = getAccentColor();
        return withAlpha(sec, isDarkTheme() ? 0.24f : 0.16f);
    }

    private int getOnSecondaryContainerColor() {
        int sec = getMonetColor("system_accent2_600", 0);
        if (sec == 0) sec = getThemeColor("chat_messageLinkIn", 0);
        if (sec == 0) sec = getAccentColor();
        return isDarkTheme() ? adjustLuminance(sec, 36) : adjustLuminance(sec, -28);
    }

    private int getOutlineColor() {
        return withAlpha(getPrimaryTextColor(), isDarkTheme() ? 0.28f : 0.20f);
    }

    private int getOutlineVariantColor() {
        return withAlpha(getPrimaryTextColor(), isDarkTheme() ? 0.14f : 0.10f);
    }

    private int getPrimaryTextColor() {
        int text = getThemeColor("windowBackgroundWhiteBlackText", 0);
        if (text == 0) text = getThemeColor("dialogTextBlack", 0);
        if (text != 0) return text;
        return isDarkTheme() ? getMonetColor("system_neutral1_50", 0xFFE6E1E5) : getMonetColor("system_neutral1_900", 0xFF1D1B20);
    }

    private int getSecondaryTextColor() {
        int text = getThemeColor("windowBackgroundWhiteGrayText", 0);
        if (text == 0) text = getThemeColor("dialogTextGray", 0);
        if (text != 0) return text;
        return isDarkTheme() ? getMonetColor("system_neutral2_200", 0xFFCAC4D0) : getMonetColor("system_neutral2_700", 0xFF49454F);
    }

    private boolean isDarkTheme() {
        try {
            Class<?> themeClass = Class.forName("org.telegram.ui.ActionBar.Theme");
            try {
                Method isDark = themeClass.getMethod("isCurrentThemeDark");
                return (Boolean) isDark.invoke(null);
            } catch (Throwable ignored) {}
            try {
                Method isDay = themeClass.getMethod("isCurrentThemeDay");
                return !((Boolean) isDay.invoke(null));
            } catch (Throwable ignored) {}
            int winBg = getThemeColor("windowBackgroundWhite", 0);
            if (winBg != 0) {
                return isColorDark(winBg);
            }
        } catch (Throwable ignored) {}
        try {
            int uiMode = context.getResources().getConfiguration().uiMode;
            return (uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable ignored) {}
        return true;
    }

    private static boolean isColorDark(int color) {
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
        return darkness >= 0.5;
    }

    private static int blendColors(int color1, int color2, float ratio) {
        float inverse = 1.0f - ratio;
        float r = Color.red(color1) * inverse + Color.red(color2) * ratio;
        float g = Color.green(color1) * inverse + Color.green(color2) * ratio;
        float b = Color.blue(color1) * inverse + Color.blue(color2) * ratio;
        return Color.rgb((int) r, (int) g, (int) b);
    }

    private static int adjustLuminance(int color, int delta) {
        int r = Math.max(0, Math.min(255, Color.red(color) + delta));
        int g = Math.max(0, Math.min(255, Color.green(color) + delta));
        int b = Math.max(0, Math.min(255, Color.blue(color) + delta));
        return Color.rgb(r, g, b);
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.round(Color.alpha(color) * Math.max(0f, Math.min(1f, alpha)));
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    private int getMonetColor(String resName, int fallback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (context != null) {
                    int id = context.getResources().getIdentifier(resName, "color", "android");
                    if (id != 0) {
                        return context.getResources().getColor(id, context.getTheme());
                    }
                }
            } catch (Throwable ignored) {}
            try {
                Resources res = Resources.getSystem();
                int id = res.getIdentifier(resName, "color", "android");
                if (id != 0) {
                    return res.getColor(id, null);
                }
            } catch (Throwable ignored) {}
        }
        return fallback;
    }

    private int getThemeColor(String key, int defaultColor) {
        try {
            Class<?> themeClass = Class.forName("org.telegram.ui.ActionBar.Theme");
            try {
                Field keyField = themeClass.getField("key_" + key);
                Object keyVal = keyField.get(null);
                if (keyVal instanceof Integer) {
                    Method getColorMethod = themeClass.getMethod("getColor", int.class);
                    Object res = getColorMethod.invoke(null, (Integer) keyVal);
                    if (res instanceof Integer && (Integer) res != 0) {
                        return (Integer) res;
                    }
                }
            } catch (Throwable ignored) {}

            try {
                Field keyField = themeClass.getField(key);
                Object keyVal = keyField.get(null);
                if (keyVal instanceof Integer) {
                    Method getColorMethod = themeClass.getMethod("getColor", int.class);
                    Object res = getColorMethod.invoke(null, (Integer) keyVal);
                    if (res instanceof Integer && (Integer) res != 0) {
                        return (Integer) res;
                    }
                }
            } catch (Throwable ignored) {}

            try {
                Method getColorMethod = themeClass.getMethod("getColor", String.class);
                Object res = getColorMethod.invoke(null, key);
                if (res instanceof Integer && (Integer) res != 0) {
                    return (Integer) res;
                }
            } catch (Throwable ignored) {}
        } catch (Throwable t) {}
        return defaultColor;
    }

    private int dp(float value) {
        return (int) Math.ceil(context.getResources().getDisplayMetrics().density * value);
    }

    private void performHaptic(View view, int feedbackConstant) {
        try {
            if (view != null) view.performHapticFeedback(feedbackConstant, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Throwable ignored) {}
    }

    private void animateChipClick(View view) {
        try {
            view.setScaleX(0.92f);
            view.setScaleY(0.92f);
            view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(220)
                    .setInterpolator(new OvershootInterpolator(2.5f))
                    .withLayer()
                    .start();
        } catch (Throwable ignored) {}
    }

    private CharSequence highlightText(String source, String queryText, int highlightBg, int highlightFg) {
        if (source == null || queryText == null || queryText.trim().isEmpty()) return source;
        String q = queryText.trim();
        if (q.startsWith("@")) q = q.substring(1).trim();
        if (q.isEmpty()) return source;

        SpannableStringBuilder sb = new SpannableStringBuilder(source);
        String[] tokens = q.split("\\s+");
        for (String token : tokens) {
            if (token.isEmpty()) continue;
            Pattern p = Pattern.compile(Pattern.quote(token), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            Matcher m = p.matcher(source);
            while (m.find()) {
                sb.setSpan(new BackgroundColorSpan(highlightBg), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new ForegroundColorSpan(highlightFg), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new StyleSpan(Typeface.BOLD), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return sb;
    }

    private static int getStatusBarHeight(Context ctx) {
        try {
            int resourceId = ctx.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId > 0) {
                return ctx.getResources().getDimensionPixelSize(resourceId);
            }
        } catch (Throwable ignored) {}
        return (int) (ctx.getResources().getDisplayMetrics().density * 24);
    }

    private static int getNavigationBarHeight(Context ctx) {
        try {
            int resourceId = ctx.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            if (resourceId > 0) {
                return ctx.getResources().getDimensionPixelSize(resourceId);
            }
        } catch (Throwable ignored) {}
        return (int) (ctx.getResources().getDisplayMetrics().density * 24);
    }

    private void setupDialogWindow(Dialog d) {
        if (d == null) return;
        try {
            Window win = d.getWindow();
            if (win != null) {
                win.setBackgroundDrawable(new ColorDrawable(0x00000000));
                win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    win.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                    win.setStatusBarColor(0x00000000);
                    win.setNavigationBarColor(0x00000000);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    int flags = win.getDecorView().getSystemUiVisibility();
                    flags |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
                    if (!isDarkTheme()) {
                        flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                        }
                    }
                    win.getDecorView().setSystemUiVisibility(flags);
                }
            }
        } catch (Throwable ignored) {}
    }

    private void applyThemeColors() {
        if (currentDialog == null || rootContainer == null) return;
        try {
            int primaryText = getPrimaryTextColor();
            int secondaryText = getSecondaryTextColor();
            int accentColor = getAccentColor();

            Window win = currentDialog.getWindow();
            if (win != null) {
                int winBg = isDarkTheme() ? getMonetColor("system_neutral1_900", 0xFF141218) : getMonetColor("system_neutral1_50", 0xFFFDF8FD);
                win.setBackgroundDrawable(new ColorDrawable(winBg));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    win.setNavigationBarColor(winBg);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    int flags = win.getDecorView().getSystemUiVisibility();
                    flags |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
                    if (!isDarkTheme()) {
                        flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                    } else {
                        flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        if (!isDarkTheme()) {
                            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                        } else {
                            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                        }
                    }
                    win.getDecorView().setSystemUiVisibility(flags);
                }
            }

            if (m3SearchBar != null) {
                GradientDrawable searchBg = new GradientDrawable();
                searchBg.setColor(getSurfaceContainerHighColor());
                searchBg.setCornerRadius(dp(28));
                searchBg.setStroke(dp(1), getOutlineVariantColor());
                m3SearchBar.setBackground(searchBg);
                if (Build.VERSION.SDK_INT >= 21) {
                    m3SearchBar.setElevation(dp(3));
                }
            }

            if (searchInputField != null) {
                searchInputField.setTextColor(primaryText);
                searchInputField.setHintTextColor(secondaryText);
            }

            updateScopeStyles();
            updateFilterStyles();
            updateDateStyles();
            updateVideoSortStyles();
            updateDocSubStyles();
            updateOnlyMineChipStyle();

            if (fastScrollThumb instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) fastScrollThumb;
                if (vg.getChildCount() > 0) {
                    View bar = vg.getChildAt(0);
                    GradientDrawable thumbBg = new GradientDrawable();
                    thumbBg.setCornerRadius(dp(4));
                    thumbBg.setColor(accentColor);
                    bar.setBackground(thumbBg);
                }
            }

            updateThumbPosition();

            if (lastRenderResponse != null) {
                renderResults(lastRenderAccount, lastRenderResponse, null, false);
            }
        } catch (Throwable ignored) {}
    }

    private void registerThemeListener(final Context context, final View view, final Runnable onThemeChanged) {
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

            int notifPlayProg = -1;
            int notifPlayState = -1;
            int notifPlayReset = -1;
            try {
                Field fProg = notifCenterClass.getField("messagePlayingProgressDidChanged");
                notifPlayProg = fProg.getInt(null);
                notifIds.add(notifPlayProg);
            } catch (Throwable ignored) {}
            try {
                Field fState = notifCenterClass.getField("messagePlayingPlayStateChanged");
                notifPlayState = fState.getInt(null);
                notifIds.add(notifPlayState);
            } catch (Throwable ignored) {}
            try {
                Field fReset = notifCenterClass.getField("messagePlayingDidReset");
                notifPlayReset = fReset.getInt(null);
                notifIds.add(notifPlayReset);
            } catch (Throwable ignored) {}

            final int fNotifProg = notifPlayProg;
            final int fNotifState = notifPlayState;
            final int fNotifReset = notifPlayReset;

            Class<?> delegateClass = Class.forName("org.telegram.messenger.NotificationCenter$NotificationCenterDelegate");
            final Object delegateProxy = Proxy.newProxyInstance(
                    delegateClass.getClassLoader(),
                    new Class<?>[]{delegateClass},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            if ("didReceivedNotification".equals(method.getName()) && args != null && args.length >= 2) {
                                int id = ((Integer) args[0]).intValue();
                                if (id == fNotifProg && args.length >= 3) {
                                    Object[] evArgs = (Object[]) args[2];
                                    if (evArgs != null && evArgs.length >= 2) {
                                        final int msgId = ((Integer) evArgs[0]).intValue();
                                        final float progress = ((Float) evArgs[1]).floatValue();
                                        mainHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                View wv = activeWaveformViews.get(msgId);
                                                if (wv != null) {
                                                    wv.setTag(progress);
                                                    wv.invalidate();
                                                }
                                                if (activeVoiceMsgId == msgId && activeVoiceDurTv != null && activeVoiceDuration > 0) {
                                                    int sec = (int) (progress * activeVoiceDuration);
                                                    activeVoiceDurTv.setText(formatVoiceDuration(sec));
                                                }
                                            }
                                        });
                                    }
                                } else if (id == fNotifReset || id == fNotifState) {
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (activeVoiceMsgId != 0) {
                                                View wv = activeWaveformViews.get(activeVoiceMsgId);
                                                if (wv != null) {
                                                    wv.setTag(0f);
                                                    wv.invalidate();
                                                }
                                                if (activeVoiceDurTv != null && activeVoiceDuration > 0) {
                                                    activeVoiceDurTv.setText(formatVoiceDuration(activeVoiceDuration));
                                                }
                                            }
                                        }
                                    });
                                } else {
                                    mainHandler.post(onThemeChanged);
                                }
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

            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
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
}
