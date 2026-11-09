package com.etherealnymph.courseflow;

import android.graphics.Color;
import android.graphics.Paint;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class NotesScreen implements MainActivity.Screen {

    private final MainActivity a;
    private final Store store;
    private final Palette p;

    private LinearLayout root;
    private FrameLayout contentHost;
    private LinearLayout listHost;
    private EditText search;
    private String query = "";
    private boolean privateTab;
    private boolean unlocked;
    private PrivateVault.Unlocked vault;
    private Sheet privateSheet;
    private boolean needsRebuild;

    public NotesScreen(MainActivity a) {
        this.a = a;
        this.store = a.store();
        this.p = a.palette();
        if (a.notesOpenPrivate) {
            this.privateTab = true;
            a.notesOpenPrivate = false;
        }
    }

    @Override
    public View build() {
        root = Ui.column(a);
        rebuildAll();
        return root;
    }

    private void rebuildAll() {
        if (root == null) return;
        root.removeAllViews();
        root.addView(header(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(segmentBar());
        if (searchVisible()) root.addView(searchArea());
        contentHost = new FrameLayout(a);
        root.addView(contentHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        renderContent();
    }

    private View header() {
        LinearLayout header = Ui.column(a);
        header.setPadding(Ui.dp(a, 20), Ui.dp(a, 8), Ui.dp(a, 20), 0);
        LinearLayout top = Ui.row(a);
        LinearLayout titles = Ui.column(a);
        titles.addView(Ui.eyebrow(a, privateTab ? "NOTES · 私密" : "NOTES · 笔记", p));
        String sub = privateTab
                ? (store.data.vault == null ? "尚未设置密码"
                : (unlocked ? "已解锁 · 离开页面会自动锁定" : "已加密 · 需要密码才能查看"))
                : store.data.notes.size() + " 条笔记 · " + store.data.todos.size() + " 项待办";
        TextView meta = Ui.text(a, sub, 11.5f, p.faint, false);
        meta.setPadding(0, Ui.dp(a, 2), 0, 0);
        titles.addView(meta);
        top.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (privateTab && unlocked) {
            ImageView add = Ui.iconButton(a, R.drawable.ic_plus, p.primary, p, true);
            add.setContentDescription("新建私密笔记");
            add.setOnClickListener(v -> editPrivate(null));
            top.addView(add);
        } else if (!privateTab) {
            ImageView add = Ui.iconButton(a, R.drawable.ic_plus, p.primary, p, true);
            add.setContentDescription("新建笔记");
            add.setOnClickListener(v -> editNote(null));
            top.addView(add);
        }
        ImageView more = Ui.iconButton(a, R.drawable.ic_more, p.sub, p, false);
        more.setContentDescription("笔记操作");
        more.setOnClickListener(v -> showMore());
        top.addView(more);
        header.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return header;
    }

    private View segmentBar() {
        LinearLayout seg = Ui.row(a);
        seg.setBackground(Ui.bg(a, p.surfaceAlt, 14));
        seg.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4));
        TextView normal = Ui.text(a, "我的笔记", 13, privateTab ? p.faint : p.ink, !privateTab);
        normal.setGravity(Gravity.CENTER);
        normal.setBackground(!privateTab ? Ui.stroke(a, p.surface, p.line, 1, 11)
                : Ui.pressable(a, Color.TRANSPARENT, Ui.dp(a, 11), Palette.alpha(p.ink, 15)));
        normal.setOnClickListener(v -> {
            if (privateTab) {
                privateTab = false;
                query = "";
                rebuildAll();
            }
        });
        seg.addView(normal, new LinearLayout.LayoutParams(0, Ui.dp(a, 36), 1));

        int count = store.data.vault == null ? 0 : store.data.vault.notes.size();
        TextView secret = Ui.text(a, privateTab ? "私密笔记" : "私密 · " + (count > 0 ? count : "锁"),
                13, privateTab ? p.ink : p.faint, privateTab);
        secret.setGravity(Gravity.CENTER);
        secret.setBackground(privateTab ? Ui.stroke(a, p.surface, p.line, 1, 11)
                : Ui.pressable(a, Color.TRANSPARENT, Ui.dp(a, 11), Palette.alpha(p.ink, 15)));
        secret.setOnClickListener(v -> {
            if (!privateTab) {
                privateTab = true;
                query = "";
                rebuildAll();
            }
        });
        seg.addView(secret, new LinearLayout.LayoutParams(0, Ui.dp(a, 36), 1));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(a, 20), Ui.dp(a, 14), Ui.dp(a, 20), 0);
        seg.setLayoutParams(lp);
        return seg;
    }

    private boolean searchVisible() {
        return !privateTab || (unlocked && store.data.vault != null);
    }

    private View searchArea() {
        LinearLayout row = Ui.searchBar(a, privateTab ? "搜索私密笔记" : "搜索标题或正文", p);
        search = Ui.searchField(row);
        if (search != null) {
            search.setText(query);
            search.setSelection(search.getText().length());
            search.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    query = s.toString();
                    renderListOnly();
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(a, 20), Ui.dp(a, 10), Ui.dp(a, 20), Ui.dp(a, 6));
        row.setLayoutParams(lp);
        return row;
    }

    private void renderContent() {
        if (contentHost == null) return;
        contentHost.removeAllViews();
        listHost = null;
        if (privateTab) {
            if (store.data.vault == null) {
                contentHost.addView(vaultSetup(), match());
            } else if (!unlocked) {
                contentHost.addView(lockPanel(), match());
            } else {
                contentHost.addView(listScroll(), match());
                renderListOnly();
            }
        } else {
            contentHost.addView(listScroll(), match());
            renderListOnly();
        }
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private View listScroll() {
        ScrollView scroll = Ui.screenScroll(a, p);
        LinearLayout body = Ui.column(a);
        body.setPadding(Ui.dp(a, 20), Ui.dp(a, 4), Ui.dp(a, 20), Ui.dp(a, 28));
        listHost = body;
        scroll.addView(body);
        return scroll;
    }

    private void renderListOnly() {
        if (listHost == null) return;
        listHost.removeAllViews();
        if (privateTab) renderPrivateList(); else renderNormalList();
    }

    // ------------------------------------------------------------- regular

    private void renderNormalList() {
        List<Store.Note> notes = new ArrayList<>(store.data.notes);
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) {
            notes.removeIf(n -> !matches(n.title, q) && !matches(n.body, q));
        }
        notes.sort(Comparator.comparing((Store.Note n) -> !n.pinned)
                .thenComparing((Store.Note n) -> -n.updatedAt));
        if (notes.isEmpty()) {
            listHost.addView(Ui.emptyState(a, R.drawable.ic_note,
                    q.isEmpty() ? "还没有笔记" : "没有找到相关笔记",
                    q.isEmpty() ? "记录课堂、想法与忽然出现的句子。" : "换一个关键词试试。", p));
            if (q.isEmpty()) {
                TextView add = Ui.primaryButton(a, "写第一条笔记", p);
                add.setOnClickListener(v -> editNote(null));
                add.setLayoutParams(centeredButton());
                listHost.addView(add);
            }
            return;
        }
        for (Store.Note n : notes) listHost.addView(noteCard(n), cardLp());
    }

    private LinearLayout noteCard(Store.Note note) {
        LinearLayout card = Ui.row(a);
        card.setBackground(Ui.stroke(a, p.surface, p.line, 1, 18));
        card.setPadding(Ui.dp(a, 0), Ui.dp(a, 13), Ui.dp(a, 12), Ui.dp(a, 13));
        View strip = new View(a);
        strip.setBackground(Ui.bg(a, note.color, 2));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(Ui.dp(a, 3), Ui.dp(a, 54));
        slp.setMargins(0, 0, Ui.dp(a, 13), 0);
        card.addView(strip, slp);

        LinearLayout info = Ui.column(a);
        LinearLayout top = Ui.row(a);
        TextView title = Ui.text(a, note.title.isEmpty() ? "无标题" : note.title, 15,
                p.ink, true);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (note.pinned) top.addView(Ui.icon(a, R.drawable.ic_pin, 13, p.primary));
        info.addView(top);
        String previewText = WeekSpec.stripForPreview(note.body).replace('\n', ' ');
        if (note.attachments != null && !note.attachments.isEmpty()) {
            if (!previewText.isEmpty()) previewText += "  ·  ";
            previewText += note.attachments.size() + " 个附件";
        }
        if (!previewText.isEmpty()) {
            TextView preview = Ui.text(a, previewText, 12, p.sub, false);
            preview.setMaxLines(2);
            preview.setEllipsize(android.text.TextUtils.TruncateAt.END);
            preview.setPadding(0, Ui.dp(a, 6), 0, 0);
            info.addView(preview);
        }
        TextView date = Ui.text(a, Dates.relative(Dates.dateOf(note.updatedAt)) + "  ·  "
                + Dates.time(Dates.timeOf(note.updatedAt)), 10, p.faint, false);
        date.setPadding(0, Ui.dp(a, 7), 0, 0);
        info.addView(date);
        card.addView(info, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.setClickable(true);
        card.setOnClickListener(v -> editNote(note));
        return card;
    }

    private void editNote(Store.Note existing) {
        boolean isNew = existing == null;
        Store.Note draft = new Store.Note();
        if (isNew) {
            draft.id = Store.newId("note");
            draft.color = Palette.NOTE_COLORS[store.data.notes.size() % Palette.NOTE_COLORS.length];
            draft.createdAt = System.currentTimeMillis();
        } else {
            draft.id = existing.id;
            draft.title = existing.title;
            draft.body = existing.body;
            draft.color = existing.color;
            draft.pinned = existing.pinned;
            draft.createdAt = existing.createdAt;
            if (existing.attachments != null) {
                draft.attachments = new ArrayList<>(existing.attachments);
            }
        }
        Sheet sheet = new Sheet(a, p, isNew ? "新建笔记" : "编辑笔记",
                "支持简易格式标记、图片、录音与文件");
        EditText title = Ui.input(a, "标题", p, false);
        title.setText(draft.title);
        sheet.label("标题");
        sheet.add(title);
        RichEditText body = new RichEditText(a, p, draft.attachments);
        body.setHint("写点什么… 选中文字后用工具栏加粗、高亮、改颜色");
        body.setMinHeight(Ui.dp(a, 160));
        body.setMarkup(draft.body);
        RichEditorHelper rich = new RichEditorHelper(a, body, draft.attachments);
        sheet.label("正文");
        sheet.add(rich.toolbar());
        sheet.add(body);
        sheet.add(rich.strip());
        sheet.add(rich.preview());

        sheet.label("颜色");
        LinearLayout swatches = Ui.row(a);
        sheet.add(swatches);
        final boolean[] pinned = {draft.pinned};
        final int[] color = {draft.color};
        Runnable[] renderSwatches = new Runnable[1];
        renderSwatches[0] = () -> {
            swatches.removeAllViews();
            for (int c : Palette.NOTE_COLORS) {
                FrameLayout cell = new FrameLayout(a);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(a, 38), 1);
                cell.setLayoutParams(lp);
                View dot = new View(a);
                boolean selected = color[0] == c;
                dot.setBackground(Ui.stroke(a, c, selected ? p.ink : Color.TRANSPARENT,
                        selected ? 2.5f : 0, 16));
                FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(
                        Ui.dp(a, 27), Ui.dp(a, 27));
                dlp.gravity = Gravity.CENTER;
                cell.addView(dot, dlp);
                cell.setClickable(true);
                cell.setOnClickListener(v -> {
                    color[0] = c;
                    renderSwatches[0].run();
                });
                swatches.addView(cell);
            }
        };
        renderSwatches[0].run();

        LinearLayout pinRow = Ui.row(a);
        pinRow.setPadding(0, Ui.dp(a, 12), 0, 0);
        pinRow.addView(Ui.text(a, "置顶这条笔记", 13, p.ink, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView pin = Ui.chip(a, "置顶", p, pinned[0]);
        pin.setOnClickListener(v -> {
            pinned[0] = !pinned[0];
            Ui.setChipSelected(pin, pinned[0], p);
        });
        pinRow.addView(pin);
        sheet.add(pinRow);

        TextView save = Ui.primaryButton(a, isNew ? "保存笔记" : "保存修改", p);
        save.setOnClickListener(v -> {
            String t = title.getText().toString().trim();
            String b = body.getMarkup();
            if (t.isEmpty() && b.trim().isEmpty() && rich.attachments().isEmpty()) {
                Ui.toast(a, "写点内容再保存吧");
                return;
            }
            draft.title = t.isEmpty() ? "无标题" : t;
            draft.body = b;
            draft.attachments = rich.attachments();
            draft.color = color[0];
            draft.pinned = pinned[0];
            draft.updatedAt = System.currentTimeMillis();
            if (isNew) store.data.notes.add(draft);
            else {
                for (int i = 0; i < store.data.notes.size(); i++) {
                    if (store.data.notes.get(i).id.equals(draft.id)) {
                        store.data.notes.set(i, draft);
                        break;
                    }
                }
            }
            store.save();
            sheet.dismiss();
            rebuildAll();
            Ui.toast(a, isNew ? "笔记已保存" : "修改已保存");
        });
        if (!isNew) {
            TextView delete = Ui.dangerButton(a, "删除笔记", p);
            delete.setOnClickListener(v -> Sheet.confirm(a, p, "删除这条笔记？",
                    "删除后无法恢复。", "删除", true, () -> {
                        store.data.notes.removeIf(n -> n.id == null ? false : n.id.equals(draft.id));
                        store.save();
                        sheet.dismiss();
                        rebuildAll();
                    }));
            TextView cancel = Ui.secondaryButton(a, "取消", p);
            cancel.setOnClickListener(v -> sheet.dismiss());
            sheet.actions(delete, save);
        } else {
            sheet.footer().addView(save);
        }
        sheet.show();
    }

    // ------------------------------------------------------------- private

    private View vaultSetup() {
        ScrollView scroll = Ui.screenScroll(a, p);
        LinearLayout body = Ui.column(a);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(Ui.dp(a, 26), Ui.dp(a, 24), Ui.dp(a, 26), Ui.dp(a, 30));
        scroll.addView(body);

        FrameLayout halo = new FrameLayout(a);
        halo.setBackground(Ui.bg(a, p.primarySoft, 34));
        ImageView icon = Ui.icon(a, R.drawable.ic_shield, 32, p.primary);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(Ui.dp(a, 32), Ui.dp(a, 32));
        ilp.gravity = Gravity.CENTER;
        halo.addView(icon, ilp);
        body.addView(halo, new LinearLayout.LayoutParams(Ui.dp(a, 72), Ui.dp(a, 72)));
        TextView title = Ui.text(a, "为私密笔记设置密码", 19, p.ink, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, Ui.dp(a, 16), 0, 0);
        body.addView(title);
        TextView sub = Ui.text(a, "标题与正文会使用 AES-256-GCM 加密保存。密码不会被上传或写入导出文件，也无法找回。",
                12.5f, p.sub, false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 14));
        body.addView(sub);

        EditText pw = Ui.passwordInput(a, "设置密码（至少 4 位）", p);
        body.addView(pw, fullLp());
        body.addView(Ui.spacer(a, 8));
        EditText confirm = Ui.passwordInput(a, "再次输入密码", p);
        body.addView(confirm, fullLp());

        TextView create = Ui.primaryButton(a, "创建私密空间", p);
        create.setOnClickListener(v -> {
            String one = pw.getText().toString();
            String two = confirm.getText().toString();
            if (one.length() < 4) {
                Ui.toast(a, "密码至少需要 4 位");
                return;
            }
            if (!one.equals(two)) {
                Ui.toast(a, "两次输入的密码不一致");
                return;
            }
            try {
                Store.Vault vault = PrivateVault.create(one);
                store.data.vault = vault;
                store.save();
                this.vault = PrivateVault.unlock(vault, one);
                if (this.vault == null) {
                    Ui.toast(a, "创建失败，请重试");
                    return;
                }
                unlocked = true;
                rebuildAll();
                Ui.toast(a, "私密空间已创建");
            } catch (Exception e) {
                Sheet.alert(a, p, "创建失败", "系统加密组件不可用：" + e.getMessage());
            }
        });
        body.addView(create, fullLpMargin(12));

        TextView hint = Ui.caption(a, "忘记密码将无法恢复已加密内容，只能重置私密空间。", p);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, Ui.dp(a, 12), 0, 0);
        body.addView(hint);
        return scroll;
    }

    private View lockPanel() {
        ScrollView scroll = Ui.screenScroll(a, p);
        LinearLayout body = Ui.column(a);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(Ui.dp(a, 26), Ui.dp(a, 28), Ui.dp(a, 26), Ui.dp(a, 30));
        scroll.addView(body);

        FrameLayout halo = new FrameLayout(a);
        halo.setBackground(Ui.bg(a, p.primarySoft, 34));
        ImageView icon = Ui.icon(a, R.drawable.ic_lock, 30, p.primary);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(Ui.dp(a, 30), Ui.dp(a, 30));
        ilp.gravity = Gravity.CENTER;
        halo.addView(icon, ilp);
        body.addView(halo, new LinearLayout.LayoutParams(Ui.dp(a, 72), Ui.dp(a, 72)));
        TextView title = Ui.text(a, "私密笔记已锁定", 19, p.ink, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, Ui.dp(a, 16), 0, 0);
        body.addView(title);
        int count = store.data.vault == null ? 0 : store.data.vault.notes.size();
        TextView sub = Ui.text(a, count + " 条加密笔记。输入密码后才能查看和编辑。", 12.5f, p.sub, false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 16));
        body.addView(sub);

        EditText pw = Ui.passwordInput(a, "输入私密密码", p);
        body.addView(pw, fullLp());
        TextView unlock = Ui.primaryButton(a, "解锁", p);
        unlock.setOnClickListener(v -> {
            PrivateVault.Unlocked u = PrivateVault.unlock(store.data.vault, pw.getText().toString());
            if (u == null) {
                Ui.toast(a, "密码不正确");
                pw.setText("");
                return;
            }
            vault = u;
            unlocked = true;
            needsRebuild = false;
            rebuildAll();
        });
        body.addView(unlock, fullLpMargin(12));
        TextView forgot = Ui.textButton(a, "忘记密码？重置私密空间", p.faint, p);
        forgot.setOnClickListener(v -> confirmResetVault());
        body.addView(forgot);
        return scroll;
    }

    private void confirmResetVault() {
        Sheet.confirm(a, p, "重置私密空间？", "这会永久删除所有私密笔记，且无法恢复。普通笔记与待办不受影响。",
                "永久删除", true, () -> {
                    store.data.vault = null;
                    store.save();
                    unlocked = false;
                    vault = null;
                    if (privateSheet != null) privateSheet.dismiss();
                    rebuildAll();
                    Ui.toast(a, "私密空间已重置");
                });
    }

    private void renderPrivateList() {
        if (vault == null) return;
        List<PrivateVault.Secret> notes = new ArrayList<>(vault.notes);
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) {
            notes.removeIf(n -> !matches(n.title, q) && !matches(n.body, q));
        }
        notes.sort((x, y) -> Long.compare(y.updatedAt, x.updatedAt));
        if (notes.isEmpty()) {
            listHost.addView(Ui.emptyState(a, R.drawable.ic_lock,
                    q.isEmpty() ? "私密空间是空的" : "没有找到相关私密笔记",
                    q.isEmpty() ? "只有输入密码后，这里的内容才会被解密。" : "换一个关键词试试。", p));
            if (q.isEmpty()) {
                TextView add = Ui.primaryButton(a, "写一条私密笔记", p);
                add.setOnClickListener(v -> editPrivate(null));
                add.setLayoutParams(centeredButton());
                listHost.addView(add);
            }
            return;
        }
        for (PrivateVault.Secret n : notes) listHost.addView(privateCard(n), cardLp());
    }

    private LinearLayout privateCard(PrivateVault.Secret note) {
        LinearLayout card = Ui.row(a);
        card.setBackground(Ui.stroke(a, p.surface, p.line, 1, 18));
        card.setPadding(Ui.dp(a, 13), Ui.dp(a, 13), Ui.dp(a, 12), Ui.dp(a, 13));
        FrameLayout lockWrap = new FrameLayout(a);
        lockWrap.setBackground(Ui.bg(a, p.primarySoft, 11));
        ImageView lock = Ui.icon(a, R.drawable.ic_lock, 15, p.primary);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(Ui.dp(a, 15), Ui.dp(a, 15));
        llp.gravity = Gravity.CENTER;
        lockWrap.addView(lock, llp);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(Ui.dp(a, 34), Ui.dp(a, 34));
        wlp.rightMargin = Ui.dp(a, 11);
        card.addView(lockWrap, wlp);
        LinearLayout info = Ui.column(a);
        info.addView(Ui.text(a, note.title.isEmpty() ? "无标题" : note.title, 14, p.ink, true));
        String previewText = WeekSpec.stripForPreview(note.body).replace('\n', ' ');
        if (note.attachments != null && !note.attachments.isEmpty()) {
            if (!previewText.isEmpty()) previewText += "  ·  ";
            previewText += note.attachments.size() + " 个附件";
        }
        if (!previewText.isEmpty()) {
            TextView pv = Ui.text(a, previewText, 11.5f, p.sub, false);
            pv.setMaxLines(2);
            pv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            pv.setPadding(0, Ui.dp(a, 5), 0, 0);
            info.addView(pv);
        }
        info.addView(Ui.text(a, Dates.relative(Dates.dateOf(note.updatedAt)) + "  ·  "
                + Dates.time(Dates.timeOf(note.updatedAt)), 10, p.faint, false));
        card.addView(info, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.setClickable(true);
        card.setOnClickListener(v -> editPrivate(note));
        return card;
    }

    private void editPrivate(PrivateVault.Secret existing) {
        if (!unlocked || vault == null) return;
        boolean isNew = existing == null;
        PrivateVault.Secret draft = isNew ? new PrivateVault.Secret() : existing;
        if (isNew) {
            draft.id = Store.newId("secret");
            draft.updatedAt = System.currentTimeMillis();
        }
        Sheet sheet = new Sheet(a, p, isNew ? "新建私密笔记" : "编辑私密笔记",
                "保存时使用 AES-256-GCM 加密");
        privateSheet = sheet;
        final Runnable[] clearRef = new Runnable[1];
        clearRef[0] = () -> privateSheet = null;
        sheet.dialog().setOnDismissListener(d -> {
            if (privateSheet == sheet) privateSheet = null;
        });

        EditText title = Ui.input(a, "标题", p, false);
        title.setText(draft.title);
        sheet.label("标题");
        sheet.add(title);
        RichEditText body = new RichEditText(a, p, draft.attachments);
        body.setHint("写点什么… 选中文字后用工具栏加粗、高亮、改颜色");
        body.setMinHeight(Ui.dp(a, 160));
        body.setMarkup(draft.body);
        RichEditorHelper rich = new RichEditorHelper(a, body, draft.attachments);
        sheet.label("正文");
        sheet.add(rich.toolbar());
        sheet.add(body);
        sheet.add(rich.strip());
        sheet.add(rich.preview());

        TextView save = Ui.primaryButton(a, "加密保存", p);
        save.setOnClickListener(v -> {
            String t = title.getText().toString().trim();
            String b = body.getMarkup();
            if (t.isEmpty() && b.trim().isEmpty() && rich.attachments().isEmpty()) {
                Ui.toast(a, "写点内容再保存吧");
                return;
            }
            draft.title = t.isEmpty() ? "无标题" : t;
            draft.body = b;
            draft.attachments = rich.attachments();
            draft.updatedAt = System.currentTimeMillis();
            try {
                Store.PrivateNote encrypted = PrivateVault.encrypt(draft, vault.key);
                boolean replaced = false;
                for (int i = 0; i < store.data.vault.notes.size(); i++) {
                    if (store.data.vault.notes.get(i).id.equals(encrypted.id)) {
                        store.data.vault.notes.set(i, encrypted);
                        replaced = true;
                        break;
                    }
                }
                if (!replaced) store.data.vault.notes.add(encrypted);
                if (isNew) vault.notes.add(0, draft);
                vault.notes.sort((x, y) -> Long.compare(y.updatedAt, x.updatedAt));
                store.save();
                sheet.dismiss();
                rebuildAll();
                Ui.toast(a, "私密笔记已加密保存");
            } catch (Exception e) {
                Ui.toast(a, "加密失败：" + e.getMessage());
            }
        });

        if (!isNew) {
            TextView delete = Ui.dangerButton(a, "删除", p);
            delete.setOnClickListener(v -> Sheet.confirm(a, p, "删除这条私密笔记？", "删除后无法恢复。",
                    "删除", true, () -> {
                        store.data.vault.notes.removeIf(n -> n.id.equals(draft.id));
                        vault.notes.removeIf(n -> n.id.equals(draft.id));
                        store.save();
                        sheet.dismiss();
                        rebuildAll();
                    }));
            sheet.actions(delete, save);
        } else {
            sheet.footer().addView(save);
        }
        sheet.show();
    }

    // ---------------------------------------------------------------- more

    private void showMore() {
        Sheet sheet = new Sheet(a, p, privateTab ? "私密笔记" : "笔记与待办",
                privateTab ? "加密空间" : "数据可以随时带走");
        if (!privateTab) {
            ScheduleScreen.addAction(sheet, R.drawable.ic_download, "导入笔记与待办",
                    "从 JSON 文件合并或替换", () -> {
                        sheet.dismiss();
                        a.importNotes();
                    });
            ScheduleScreen.addAction(sheet, R.drawable.ic_upload, "导出笔记与待办",
                    "包括加密的私密笔记备份", () -> {
                        sheet.dismiss();
                        a.exportNotes();
                    });
            ScheduleScreen.addAction(sheet, R.drawable.ic_info, "关于导入导出",
                    "私密内容以密文导出，需要原密码解锁", () -> {
                        sheet.dismiss();
                        Sheet.alert(a, p, "关于笔记导出",
                                "导出的 JSON 包含普通笔记与待办；如果已经创建私密空间，还会包含 AES-256-GCM 加密后的私密笔记密文与校验信息。\n\n密码本身永远不会写入文件，因此导入后仍需使用原来的密码解锁。若当前设备已有不同的私密密码，密文会被跳过。");
                    });
        } else if (unlocked) {
            ScheduleScreen.addAction(sheet, R.drawable.ic_shield, "修改私密密码",
                    "会用新密码重新加密全部私密笔记", () -> {
                        sheet.dismiss();
                        changePassword();
                    });
            ScheduleScreen.addAction(sheet, R.drawable.ic_lock, "立即锁定",
                    "清除内存中的解密内容", () -> {
                        sheet.dismiss();
                        lockNow();
                        rebuildAll();
                    });
            ScheduleScreen.addAction(sheet, R.drawable.ic_upload, "导出加密备份",
                    "密文导出，需原密码解锁", () -> {
                        sheet.dismiss();
                        a.exportNotes();
                    });
            ScheduleScreen.addAction(sheet, R.drawable.ic_warning, "重置私密空间",
                    "永久删除全部私密笔记", () -> {
                        sheet.dismiss();
                        confirmResetVault();
                    });
        } else {
            ScheduleScreen.addAction(sheet, R.drawable.ic_lock, "解锁私密空间",
                    "输入密码查看与编辑", () -> {
                        sheet.dismiss();
                        unlocked = false;
                        rebuildAll();
                    });
        }
        sheet.show();
    }

    private void changePassword() {
        Sheet sheet = new Sheet(a, p, "修改私密密码", "所有私密笔记将用新密码重新加密");
        EditText one = Ui.passwordInput(a, "新密码（至少 4 位）", p);
        EditText two = Ui.passwordInput(a, "再次输入新密码", p);
        sheet.label("新密码");
        sheet.add(one);
        sheet.add(Ui.spacer(a, 8));
        sheet.add(two);
        TextView save = Ui.primaryButton(a, "重新加密并保存", p);
        save.setOnClickListener(v -> {
            String p1 = one.getText().toString();
            if (p1.length() < 4) {
                Ui.toast(a, "密码至少需要 4 位");
                return;
            }
            if (!p1.equals(two.getText().toString())) {
                Ui.toast(a, "两次输入的密码不一致");
                return;
            }
            try {
                Store.Vault fresh = PrivateVault.changePassword(store.data.vault, vault, p1);
                store.data.vault = fresh;
                store.save();
                vault = PrivateVault.unlock(fresh, p1);
                if (vault == null) {
                    Ui.toast(a, "新密码验证失败");
                    return;
                }
                sheet.dismiss();
                rebuildAll();
                Ui.toast(a, "密码已更新");
            } catch (Exception e) {
                Ui.toast(a, "修改失败：" + e.getMessage());
            }
        });
        sheet.footer().addView(save);
        sheet.show();
    }

    // --------------------------------------------------------------- lock

    public void lockNow() {
        if (!unlocked && privateSheet == null) return;
        if (privateSheet != null) {
            privateSheet.dismiss();
            privateSheet = null;
        }
        unlocked = false;
        vault = null;
        needsRebuild = true;
    }

    public void onActivityResume() {
        if (needsRebuild && privateTab && root != null) {
            needsRebuild = false;
            rebuildAll();
        }
    }

    private boolean matches(String value, String q) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(q);
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(a, 9);
        return lp;
    }

    private LinearLayout.LayoutParams fullLp() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams fullLpMargin(int top) {
        LinearLayout.LayoutParams lp = fullLp();
        lp.topMargin = Ui.dp(a, top);
        return lp;
    }

    private LinearLayout.LayoutParams centeredButton() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                Ui.dp(a, 190), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = Ui.dp(a, 4);
        return lp;
    }

    @Override
    public boolean onBack() {
        if (privateTab) {
            privateTab = false;
            query = "";
            rebuildAll();
            return true;
        }
        if (query != null && !query.isEmpty()) {
            query = "";
            if (search != null) search.setText("");
            renderListOnly();
            return true;
        }
        return false;
    }
}
