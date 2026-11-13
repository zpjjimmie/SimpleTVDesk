package cn.simpletvdesk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONException;
import java.text.Collator;
import java.util.*;

public final class HomeActivity extends Activity {
    private static final int WHITE = 0xfff3f7f6, MUTED = 0xffa1b4bc, MINT = 0xfface7d7;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int FILL = ViewGroup.LayoutParams.MATCH_PARENT;
    private final List<ComponentName> pins = new ArrayList<>();
    private final Map<ComponentName, ActivityInfo> available = new LinkedHashMap<>();
    private SharedPreferences preferences;
    private AlertDialog dialog;
    private ComponentName selected;
    private boolean manageFocused;
    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent event) {
            String pkg = event.getData().getSchemeSpecificPart();
            if (Intent.ACTION_PACKAGE_REMOVED.equals(event.getAction())
                    && !event.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                for (Iterator<ComponentName> it = pins.iterator(); it.hasNext();)
                    if (pkg.equals(it.next().getPackageName())) it.remove();
                save();
            }
            // Keep a picker open if an unrelated package changes.
            if (dialog == null || !dialog.isShowing()) render();
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("desktop", MODE_PRIVATE);
        try {
            JSONArray ids = new JSONArray(preferences.getString("pins", "[]"));
            for (int i = 0; i < ids.length(); i++) {
                ComponentName id = ComponentName.unflattenFromString(ids.optString(i));
                if (id != null && !pins.contains(id)) pins.add(id);
            }
        } catch (JSONException ignored) { /* An unreadable list opens the initial picker tile. */ }
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(changes, f);
    }

    @Override protected void onResume() { super.onResume(); render(); }
    @Override protected void onDestroy() { unregisterReceiver(changes); super.onDestroy(); }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (dialog != null) dialog.dismiss();
        selected = null;
        manageFocused = false;
        render();
    }
    @Override public void onBackPressed() {
        if (!getIntent().hasCategory(Intent.CATEGORY_HOME)) super.onBackPressed();
    }

    private void save() {
        JSONArray ids = new JSONArray();
        for (ComponentName id : pins) ids.put(id.flattenToString());
        preferences.edit().putString("pins", ids.toString()).apply();
    }

    private int dp(float value) { return Math.round(getResources().getDisplayMetrics().density * value); }
    private TextView label(String value, int size, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER);
        return v;
    }
    private GradientDrawable surface(int color, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(9));
        g.setStroke(dp(2), stroke);
        return g;
    }
    private void render() {
        PackageManager pm = getPackageManager();
        available.clear();
        for (ComponentName id : pins) {
            try {
                ActivityInfo a = pm.getActivityInfo(id, 0);
                if (a.enabled && a.applicationInfo.enabled && a.exported) available.put(id, a);
            } catch (PackageManager.NameNotFoundException ignored) { }
        }
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xff172e39, 0xff101e29, 0xff0c161f}));
        root.setPadding(dp(40), dp(22), dp(40), dp(22));
        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("h:mm");
        clock.setTextSize(22);
        clock.setTextColor(MUTED);
        root.addView(clock, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.RIGHT));

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setFillViewport(true);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFocusable(false);
        root.addView(scroller, new FrameLayout.LayoutParams(FILL, WRAP, Gravity.CENTER));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        scroller.addView(row, new HorizontalScrollView.LayoutParams(WRAP, WRAP));
        View target = null;
        ComponentName wanted = selected;
        boolean wantManage = manageFocused;
        for (Map.Entry<ComponentName, ActivityInfo> entry : available.entrySet()) {
            ComponentName id = entry.getKey();
            ActivityInfo app = entry.getValue();
            View v = tile(row, app.loadLabel(pm).toString(), app.loadIcon(pm), id, () -> launch(id));
            if (target == null || id.equals(wanted)) target = v;
        }
        boolean empty = available.isEmpty();
        View edit = tile(row, empty ? "添加应用" : "管理", null, null,
                empty ? this::addApp : this::manage);
        setContentView(root);
        (target == null || wantManage ? edit : target).requestFocus();
    }

    private View tile(LinearLayout row, String title, Drawable icon, ComponentName id, Runnable click) {
        LinearLayout card = new LinearLayout(this);
        card.setFocusable(true);
        card.setGravity(Gravity.CENTER);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(10), dp(8), dp(8));
        card.setContentDescription(title);
        LinearLayout.LayoutParams box = new LinearLayout.LayoutParams(dp(132), dp(112));
        box.setMargins(dp(5), dp(4), dp(5), dp(4));
        row.addView(card, box);
        if (icon != null) {
            ImageView image = new ImageView(this);
            image.setImageDrawable(icon);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(image, dp(50), dp(50));
        } else {
            card.addView(label(available.isEmpty() ? "+" : "···", 32, MINT), dp(52), dp(50));
        }
        TextView caption = label(title, 17, MUTED);
        caption.setSingleLine();
        caption.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams text = new LinearLayout.LayoutParams(FILL, WRAP);
        text.topMargin = dp(8);
        card.addView(caption, text);
        card.setBackground(surface(0xff20313c, 0xff334651));
        card.setOnFocusChangeListener((view, focused) -> {
            card.setBackground(surface(focused ? 0xff304e56 : 0xff20313c,
                    focused ? MINT : 0xff334651));
            caption.setTextColor(focused ? WHITE : MUTED);
            caption.setTypeface(Typeface.DEFAULT, focused ? Typeface.BOLD : Typeface.NORMAL);
            if (focused) { selected = id; manageFocused = id == null; }
        });
        card.setOnClickListener(v -> click.run());
        return card;
    }

    private void launch(ComponentName id) {
        Intent intent = Intent.makeMainActivity(id);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try { startActivity(intent); }
        catch (android.content.ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "无法打开此应用", Toast.LENGTH_SHORT).show();
            render();
        }
    }

    private interface Choice { void select(int index); }
    private void menu(String title, List<String> labels, int index, Choice choice) {
        if (dialog != null) dialog.dismiss();
        dialog = new AlertDialog.Builder(this).setTitle(title)
                .setItems(labels.toArray(new String[0]), (d, i) -> choice.select(i))
                .setNegativeButton("返回", null).create();
        dialog.show();
        dialog.getListView().requestFocus();
        dialog.getListView().setSelection(index);
    }

    private void addApp() {
        Map<String, ResolveInfo> byPackage = new LinkedHashMap<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo r : getPackageManager().queryIntentActivities(query, 0)) {
                String pkg = r.activityInfo.packageName;
                if (!pkg.equals(getPackageName()) && r.activityInfo.exported && !byPackage.containsKey(pkg))
                    byPackage.put(pkg, r);
            }
        }
        List<ResolveInfo> apps = new ArrayList<>(byPackage.values());
        List<ComponentName> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        Collator order = Collator.getInstance(Locale.getDefault());
        Collections.sort(apps, (a,b) -> order.compare(a.loadLabel(getPackageManager()).toString(),
                b.loadLabel(getPackageManager()).toString()));
        for (ResolveInfo r : apps) {
            ComponentName id = new ComponentName(r.activityInfo.packageName, r.activityInfo.name);
            if (!pins.contains(id)) {
                ids.add(id);
                names.add(r.loadLabel(getPackageManager()).toString());
            }
        }
        if (ids.isEmpty()) {
            Toast.makeText(this, "没有可添加的应用", Toast.LENGTH_SHORT).show();
            return;
        }
        menu("添加应用", names, 0, i -> {
            selected = ids.get(i);
            manageFocused = false;
            if (!pins.contains(selected)) pins.add(selected);
            save();
            render();
        });
    }

    private void manage() {
        List<ComponentName> ids = new ArrayList<>(available.keySet());
        List<String> names = new ArrayList<>();
        names.add("添加应用");
        for (ComponentName id : ids) names.add(available.get(id).loadLabel(getPackageManager()).toString());
        menu("管理常用应用", names, 0, i -> {
            if (i == 0) addApp();
            else edit(ids.get(i - 1));
        });
    }

    private void edit(ComponentName id) {
        int pos = pins.indexOf(id);
        if (pos < 0) { render(); return; }
        List<String> actions = new ArrayList<>();
        if (pos > 0) actions.add("向左移动");
        if (pos + 1 < pins.size()) actions.add("向右移动");
        actions.add("从桌面移除");
        menu(available.get(id).loadLabel(getPackageManager()).toString(), actions, 0, i -> {
            int current = pins.indexOf(id);
            if (current >= 0) {
                String action = actions.get(i);
                if (action.equals("从桌面移除")) pins.remove(current);
                else {
                    int next = current + (action.equals("向左移动") ? -1 : 1);
                    if (next >= 0 && next < pins.size()) Collections.swap(pins, current, next);
                }
            }
            selected = id;
            manageFocused = false;
            save();
            render();
        });
    }
}
