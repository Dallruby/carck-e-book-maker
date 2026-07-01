package com.dallruby.crackebookmaker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int REQ_OPEN_MD = 4001;
    private static final String PREF_LIBRARY = "library_items_v2";

    private static final int BG = Color.rgb(15, 15, 16);
    private static final int PANEL = Color.rgb(27, 27, 29);
    private static final int PANEL_2 = Color.rgb(36, 34, 36);
    private static final int CARD = Color.rgb(39, 38, 36);
    private static final int CARD_HEAD = Color.rgb(78, 76, 71);
    private static final int RED = Color.rgb(255, 75, 75);
    private static final int RED_DARK = Color.rgb(70, 14, 22);
    private static final int TEXT = Color.rgb(232, 232, 232);
    private static final int MUTED = Color.rgb(166, 161, 164);
    private static final int SOFT = Color.rgb(210, 176, 182);
    private static final int LINE = Color.rgb(56, 54, 55);
    private static final int EMPHASIS = Color.rgb(154, 154, 154);

    private final LruCache<String, Bitmap> imageCache = new LruCache<String, Bitmap>(32 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private LinearLayout root;
    private LinearLayout pageContainer;
    private TextView titleView;
    private TextView pageView;
    private Button prevButton;
    private Button nextButton;
    private Button pageJumpButton;
    private Button bookmarkButton;
    private Button bookmarkListButton;
    private ScrollView scrollView;
    private FrameLayout readerFrame;
    private SharedPreferences prefs;

    private final ArrayList<LibraryItem> library = new ArrayList<>();
    private ChatDocument document;
    private LibraryItem activeItem;
    private List<List<ChatBlock>> pages = new ArrayList<>();
    private int currentPage = 0;
    private String userName = "유저";
    private String aiName = "AI";
    private float textScale = 1.0f;
    private Uri currentUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("reader", MODE_PRIVATE);
        textScale = prefs.getFloat("textScale", 1.0f);
        loadLibrary();
        buildShell();
        showHome();
    }

    @Override
    public void onBackPressed() {
        if (activeItem != null) {
            activeItem = null;
            showHome();
        } else {
            super.onBackPressed();
        }
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);
    }

    private void showHome() {
        activeItem = null;
        root.removeAllViews();

        ScrollView homeScroll = new ScrollView(this);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(0, 0, 0, dp(24));
        homeScroll.addView(shell);
        root.addView(homeScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        shell.addView(heroHome(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(246)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(18), dp(18), 0);
        shell.addView(body);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(buttons);

        Button open = primaryButton("채팅방 불러오기");
        open.setOnClickListener(v -> openMarkdownFile());
        buttons.addView(open, new LinearLayout.LayoutParams(0, dp(50), 1));

        Button help = iconButton("도움말");
        help.setOnClickListener(v -> showHelpDialog());
        LinearLayout.LayoutParams helpLp = new LinearLayout.LayoutParams(dp(92), dp(50));
        helpLp.setMargins(dp(10), 0, 0, 0);
        buttons.addView(help, helpLp);

        addSection(body, "보관함 미리보기", true);
        addSection(body, "채팅방 목록", false);
    }

    private View heroHome() {
        FrameLayout heroWrap = new FrameLayout(this);
        ImageView hero = new ImageView(this);
        hero.setImageResource(getResources().getIdentifier("dallruby_header", "drawable", getPackageName()));
        hero.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroWrap.addView(hero, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x33200008, 0xF20F0F10}));
        heroWrap.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.BOTTOM);
        overlay.setPadding(dp(20), dp(18), dp(20), dp(20));
        heroWrap.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView badge = text("DALLRUBY ARCHIVE", 12, Color.rgb(255, 190, 198), Typeface.BOLD);
        badge.setLetterSpacing(0.08f);
        overlay.addView(badge);

        TextView title = text("Crack E-book Reader", 30, Color.WHITE, Typeface.BOLD);
        title.setPadding(0, dp(4), 0, 0);
        overlay.addView(title);

        TextView korean = text("달루비 크랙 이북 리더", 17, Color.WHITE, Typeface.BOLD);
        korean.setPadding(0, dp(2), 0, dp(10));
        overlay.addView(korean);

        TextView sub = text("내 소중하고 뽀짝한 크랙방을 소장하라고 만들었다!\n[패도&특무국] 스토리도 사랑해주세요 💖", 14, SOFT, Typeface.BOLD);
        sub.setLineSpacing(dp(3), 1.0f);
        overlay.addView(sub);

        return heroWrap;
    }

    private void addSection(LinearLayout body, String title, boolean archived) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(22), 0, dp(8));
        TextView label = text(title, 18, Color.WHITE, Typeface.BOLD);
        header.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView count = text(String.valueOf(countArchived(archived)), 12, MUTED, Typeface.BOLD);
        count.setGravity(Gravity.CENTER);
        count.setBackground(rounded(PANEL_2, dp(14), Color.TRANSPARENT, 0));
        header.addView(count, new LinearLayout.LayoutParams(dp(42), dp(26)));
        body.addView(header);

        ArrayList<LibraryItem> items = filteredItems(archived);
        if (items.isEmpty()) {
            TextView empty = text(archived ? "아직 보관함에 넣은 채팅방이 없어요." : "채팅방 불러오기로 Markdown 파일을 추가해줘.", 14, MUTED, Typeface.NORMAL);
            empty.setPadding(dp(14), dp(18), dp(14), dp(18));
            empty.setBackground(rounded(PANEL, dp(12), Color.rgb(44, 42, 44), 1));
            body.addView(empty);
            return;
        }

        for (LibraryItem item : items) {
            body.addView(libraryRow(item, archived));
        }
    }

    private View libraryRow(LibraryItem item, boolean archivedSection) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(rounded(PANEL, dp(12), archivedSection ? RED_DARK : Color.rgb(48, 45, 47), 1));
        card.setOnClickListener(v -> openLibraryItem(item));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(cardLp);

        TextView title = text(valueOr(item.displayTitle, item.title), 16, Color.WHITE, Typeface.BOLD);
        title.setSingleLine(false);
        card.addView(title);

        TextView meta = text(formatDate(item.addedAt) + " · " + valueOr(item.aiName, "AI"), 12, MUTED, Typeface.NORMAL);
        meta.setPadding(0, dp(4), 0, dp(10));
        card.addView(meta);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(row);

        Button read = smallButton("읽기");
        read.setOnClickListener(v -> openLibraryItem(item));
        row.addView(read, new LinearLayout.LayoutParams(0, dp(38), 1));

        Button archive = smallButton(item.archived ? "꺼내기" : "보관");
        archive.setOnClickListener(v -> {
            item.archived = !item.archived;
            saveLibrary();
            showHome();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1);
        lp.setMargins(dp(8), 0, 0, 0);
        row.addView(archive, lp);

        Button rename = smallButton("이름변경");
        rename.setOnClickListener(v -> showRenameDialog(item, false));
        LinearLayout.LayoutParams renameLp = new LinearLayout.LayoutParams(0, dp(38), 1);
        renameLp.setMargins(dp(8), 0, 0, 0);
        row.addView(rename, renameLp);

        return card;
    }

    private void showHelpDialog() {
        new AlertDialog.Builder(this)
                .setTitle("사용법")
                .setMessage("1. 템퍼몽키에서 크랙 채팅을 md 파일로 저장합니다.\n2. 앱에서 채팅방 불러오기를 누릅니다.\n3. {user} 이름과 {char} 이름을 적고 읽기 시작합니다.\n4. 자주 볼 방은 보관함에 넣어두면 됩니다.")
                .setPositiveButton("확인", null)
                .show();
    }

    private void openMarkdownFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/markdown", "text/plain", "application/octet-stream"});
        startActivityForResult(intent, REQ_OPEN_MD);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OPEN_MD && resultCode == RESULT_OK && data != null && data.getData() != null) {
            currentUri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(currentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            loadNewDocument(currentUri);
        }
    }

    private void loadNewDocument(Uri uri) {
        try {
            String markdown = readAll(uri);
            document = CrackMarkdownParser.parse(markdown);
            pages = PageMaker.makePages(document.blocks);
            currentPage = 0;
            showNameDialog(uri);
        } catch (Exception e) {
            Toast.makeText(this, "파일을 읽지 못했어요: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showNameDialog(Uri uri) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);

        EditText user = dialogInput("{user} 이름 (예: 흑냥이)");
        form.addView(user);

        EditText ai = dialogInput("{char} 이름 (예: 조준재)");
        LinearLayout.LayoutParams aiLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        aiLp.setMargins(0, dp(8), 0, 0);
        form.addView(ai, aiLp);

        new AlertDialog.Builder(this)
                .setTitle("이름 설정")
                .setView(form)
                .setPositiveButton("읽기 시작", (dialog, which) -> {
                    userName = valueOr(user.getText().toString(), "유저");
                    aiName = valueOr(ai.getText().toString(), "AI");
                    activeItem = upsertLibraryItem(uri, document, userName, aiName);
                    currentPage = activeItem.lastPage;
                    saveLibrary();
                    showReader();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private EditText dialogInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setText("");
        return input;
    }

    private void openLibraryItem(LibraryItem item) {
        try {
            currentUri = Uri.parse(item.uri);
            String markdown = readAll(currentUri);
            document = CrackMarkdownParser.parse(markdown);
            pages = PageMaker.makePages(document.blocks);
            activeItem = item;
            userName = valueOr(item.userName, "유저");
            aiName = valueOr(item.aiName, "AI");
            currentPage = Math.max(0, Math.min(item.lastPage, Math.max(0, pages.size() - 1)));
            showReader();
        } catch (Exception e) {
            Toast.makeText(this, "채팅방을 다시 열 수 없어요. 파일 위치가 바뀌었을 수 있어요.", Toast.LENGTH_LONG).show();
        }
    }

    private void showReader() {
        root.removeAllViews();

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(BG);
        root.addView(shell, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        shell.addView(readerHeader(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(132)));

        readerFrame = new FrameLayout(this);
        scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        pageContainer = new LinearLayout(this);
        pageContainer.setOrientation(LinearLayout.VERTICAL);
        pageContainer.setPadding(dp(18), dp(16), dp(18), dp(80));
        scrollView.addView(pageContainer);
        readerFrame.addView(scrollView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addFloatButtons();
        shell.addView(readerFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        shell.addView(readerControls());
        renderPage();
    }

    private View readerHeader() {
        FrameLayout heroWrap = new FrameLayout(this);
        ImageView hero = new ImageView(this);
        hero.setImageResource(getResources().getIdentifier("dallruby_header", "drawable", getPackageName()));
        hero.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroWrap.addView(hero, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x22111111, 0xEE111111}));
        heroWrap.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.BOTTOM);
        overlay.setPadding(dp(14), dp(10), dp(14), dp(12));
        heroWrap.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        overlay.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        Button back = ghostButton("← 서재");
        back.setOnClickListener(v -> {
            activeItem = null;
            showHome();
        });
        top.addView(back, new LinearLayout.LayoutParams(dp(82), dp(36)));

        bookmarkButton = ghostButton("북마크");
        bookmarkButton.setOnClickListener(v -> addBookmarkForCurrentPage());
        LinearLayout.LayoutParams bmLp = new LinearLayout.LayoutParams(dp(82), dp(36));
        bmLp.setMargins(dp(8), 0, 0, 0);
        top.addView(bookmarkButton, bmLp);

        bookmarkListButton = ghostButton("목록");
        bookmarkListButton.setOnClickListener(v -> showBookmarkDialog());
        LinearLayout.LayoutParams blLp = new LinearLayout.LayoutParams(dp(70), dp(36));
        blLp.setMargins(dp(8), 0, 0, 0);
        top.addView(bookmarkListButton, blLp);

        Button rename = ghostButton("이름");
        rename.setOnClickListener(v -> showRenameDialog(activeItem, true));
        LinearLayout.LayoutParams rnLp = new LinearLayout.LayoutParams(dp(70), dp(36));
        rnLp.setMargins(dp(8), 0, 0, 0);
        top.addView(rename, rnLp);

        titleView = text(valueOr(activeItem == null ? null : activeItem.displayTitle, document.title), 17, Color.WHITE, Typeface.BOLD);
        titleView.setSingleLine(false);
        titleView.setPadding(0, dp(7), 0, 0);
        overlay.addView(titleView);

        pageView = text("", 12, SOFT, Typeface.BOLD);
        overlay.addView(pageView);

        return heroWrap;
    }

    private View readerControls() {
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setPadding(dp(10), dp(8), dp(10), dp(10));
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setBackgroundColor(Color.rgb(19, 19, 20));

        prevButton = secondaryButton("이전");
        prevButton.setOnClickListener(v -> movePage(-1));
        controls.addView(prevButton, new LinearLayout.LayoutParams(0, dp(44), 1));

        Button smaller = secondaryButton("A-");
        smaller.setOnClickListener(v -> changeTextScale(-0.08f));
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(dp(52), dp(44));
        sLp.setMargins(dp(8), 0, 0, 0);
        controls.addView(smaller, sLp);

        pageJumpButton = ghostButton("1 / 1");
        pageJumpButton.setOnClickListener(v -> showPageJumpDialog());
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(dp(92), dp(44));
        pLp.setMargins(dp(8), 0, 0, 0);
        controls.addView(pageJumpButton, pLp);

        Button bigger = secondaryButton("A+");
        bigger.setOnClickListener(v -> changeTextScale(0.08f));
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(dp(52), dp(44));
        bLp.setMargins(dp(8), 0, 0, 0);
        controls.addView(bigger, bLp);

        nextButton = primaryButton("다음");
        nextButton.setOnClickListener(v -> movePage(1));
        LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(0, dp(44), 1);
        nLp.setMargins(dp(8), 0, 0, 0);
        controls.addView(nextButton, nLp);

        return controls;
    }

    private void addFloatButtons() {
        LinearLayout floating = new LinearLayout(this);
        floating.setOrientation(LinearLayout.VERTICAL);
        floating.setGravity(Gravity.CENTER);

        Button up = roundFloatButton("↑");
        up.setOnClickListener(v -> scrollView.smoothScrollTo(0, 0));
        floating.addView(up, new LinearLayout.LayoutParams(dp(46), dp(46)));

        Button down = roundFloatButton("↓");
        down.setOnClickListener(v -> scrollView.post(() -> scrollView.smoothScrollTo(0, pageContainer.getBottom())));
        LinearLayout.LayoutParams downLp = new LinearLayout.LayoutParams(dp(46), dp(46));
        downLp.setMargins(0, dp(8), 0, 0);
        floating.addView(down, downLp);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT);
        lp.setMargins(0, 0, dp(14), dp(18));
        readerFrame.addView(floating, lp);
    }

    private void movePage(int delta) {
        int next = currentPage + delta;
        goToPage(next);
    }

    private void goToPage(int page) {
        if (pages.isEmpty()) return;
        int next = Math.max(0, Math.min(page, pages.size() - 1));
        currentPage = next;
        if (activeItem != null) {
            activeItem.lastPage = currentPage;
            saveLibrary();
        }
        renderPage();
    }

    private void changeTextScale(float delta) {
        textScale = Math.max(0.82f, Math.min(1.45f, textScale + delta));
        prefs.edit().putFloat("textScale", textScale).apply();
        renderPage();
    }

    private void renderPage() {
        pageContainer.removeAllViews();
        int total = Math.max(1, pages.size());
        String pageText = String.format(Locale.KOREA, "%d / %d 페이지", currentPage + 1, total);
        pageView.setText(pageText);
        pageJumpButton.setText(String.format(Locale.KOREA, "%d / %d", currentPage + 1, total));
        bookmarkButton.setText(isBookmarked(currentPage) ? "저장됨" : "북마크");
        bookmarkListButton.setText(String.format(Locale.KOREA, "목록 %d", activeItem == null ? 0 : activeItem.bookmarks.size()));

        if (pages.isEmpty()) {
            pageContainer.addView(text("읽을 메시지가 없어요.", 16, TEXT, Typeface.NORMAL));
            return;
        }

        for (ChatBlock block : pages.get(currentPage)) {
            renderChatBlock(block);
        }

        prevButton.setEnabled(currentPage > 0);
        nextButton.setEnabled(currentPage < pages.size() - 1);
        scrollView.post(() -> scrollView.scrollTo(0, 0));
    }

    private void renderChatBlock(ChatBlock block) {
        if (block.role == Role.USER) {
            addDivider(dp(16), dp(10));
        }

        TextView label = text(block.role == Role.USER ? userName : aiName, 13, block.role == Role.USER ? Color.WHITE : Color.rgb(255, 211, 216), Typeface.BOLD);
        label.setPadding(0, dp(6), 0, dp(8));
        pageContainer.addView(label);

        renderMarkdown(block.body, block.role);

        if (block.role == Role.USER) {
            addDivider(dp(4), dp(16));
        }
    }

    private void renderMarkdown(String body, Role role) {
        String[] lines = body.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        ArrayList<String> paragraph = new ArrayList<>();
        boolean inCode = false;
        String codeLang = "";
        ArrayList<String> codeLines = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                flushParagraph(paragraph, role);
                if (inCode) {
                    addCodeCard(codeLang, join(codeLines));
                    codeLines.clear();
                    codeLang = "";
                    inCode = false;
                } else {
                    inCode = true;
                    codeLang = trimmed.length() > 3 ? trimmed.substring(3).trim() : "";
                }
                continue;
            }

            if (inCode) {
                codeLines.add(line);
                continue;
            }

            String imageUrl = extractImageUrl(trimmed);
            if (imageUrl != null) {
                flushParagraph(paragraph, role);
                addImage(imageUrl);
                continue;
            }

            if (trimmed.equals("---")) {
                flushParagraph(paragraph, role);
                addDivider(dp(14), dp(14));
                continue;
            }

            if (trimmed.startsWith(">")) {
                flushParagraph(paragraph, role);
                addQuote(trimmed.replaceFirst("^>\\s?", ""));
                continue;
            }

            if (trimmed.isEmpty()) {
                flushParagraph(paragraph, role);
            } else {
                paragraph.add(line);
            }
        }

        flushParagraph(paragraph, role);
        if (inCode && !codeLines.isEmpty()) {
            addCodeCard(codeLang, join(codeLines));
        }
    }

    private void flushParagraph(ArrayList<String> paragraph, Role role) {
        if (paragraph.isEmpty()) return;
        String value = join(paragraph).trim();
        paragraph.clear();
        if (value.isEmpty()) return;

        TextView tv = text("", 16, role == Role.USER ? Color.WHITE : TEXT, role == Role.USER ? Typeface.BOLD : Typeface.NORMAL);
        tv.setText(applyInlineMarkdown(value));
        tv.setLineSpacing(dp(4), 1.05f);
        tv.setPadding(0, 0, 0, dp(14));
        pageContainer.addView(tv);
    }

    private void addQuote(String quote) {
        TextView tv = text("", 15, Color.rgb(235, 235, 235), Typeface.BOLD);
        tv.setText(applyInlineMarkdown(quote));
        tv.setPadding(dp(14), dp(10), dp(12), dp(10));
        tv.setBackground(rounded(Color.rgb(31, 31, 31), dp(8), Color.rgb(67, 67, 67), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(14));
        pageContainer.addView(tv, lp);
    }

    private void addCodeCard(String lang, String code) {
        boolean info = "INFO".equalsIgnoreCase(lang);
        boolean npc = "NPC".equalsIgnoreCase(lang);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(CARD, dp(10), Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, dp(4), 0, dp(16));
        pageContainer.addView(card, cardLp);

        TextView head = text(info ? "INFO" : npc ? "NPC" : valueOr(lang, "CODE"), 13, Color.rgb(190, 190, 190), Typeface.BOLD);
        head.setPadding(dp(14), dp(8), dp(14), dp(8));
        head.setBackground(rounded(CARD_HEAD, dp(10), Color.TRANSPARENT, 0));
        card.addView(head);

        TextView body = text(code, 14, Color.rgb(238, 238, 238), Typeface.NORMAL);
        body.setTypeface(Typeface.MONOSPACE);
        body.setLineSpacing(dp(2), 1.0f);
        body.setPadding(dp(14), dp(12), dp(14), dp(14));
        card.addView(body);
    }

    private void addImage(String url) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, 0, 0, 0);
        box.setBackground(rounded(PANEL, dp(10), Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        boxLp.setMargins(0, dp(4), 0, dp(16));
        pageContainer.addView(box, boxLp);

        ImageView image = new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(22, 22, 22));
        box.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230)));

        Bitmap cached = imageCache.get(url);
        if (cached != null) {
            image.setImageBitmap(cached);
            image.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
            return;
        }

        TextView loading = text("이미지 불러오는 중...", 12, MUTED, Typeface.NORMAL);
        loading.setPadding(dp(12), dp(8), dp(12), dp(10));
        box.addView(loading);

        new ImageTask(url, image, loading).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void addDivider(int top, int bottom) {
        View line = new View(this);
        line.setBackgroundColor(LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.setMargins(0, top, 0, bottom);
        pageContainer.addView(line, lp);
    }

    private SpannableStringBuilder applyInlineMarkdown(String source) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        int i = 0;
        while (i < source.length()) {
            if (source.startsWith("**", i)) {
                int end = source.indexOf("**", i + 2);
                if (end > i) {
                    int start = out.length();
                    out.append(source.substring(i + 2, end));
                    out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 2;
                    continue;
                }
            }
            if (source.charAt(i) == '*') {
                int end = source.indexOf('*', i + 1);
                if (end > i) {
                    int start = out.length();
                    out.append(source.substring(i + 1, end));
                    out.setSpan(new ForegroundColorSpan(EMPHASIS), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 1;
                    continue;
                }
            }
            out.append(source.charAt(i));
            i += 1;
        }
        return out;
    }

    private void showPageJumpDialog() {
        if (pages.isEmpty()) return;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(6), dp(18), 0);

        TextView label = text(String.format(Locale.KOREA, "총 %d페이지 중 이동할 페이지", pages.size()), 14, Color.DKGRAY, Typeface.BOLD);
        form.addView(label);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(currentPage + 1));
        form.addView(input);

        SeekBar seek = new SeekBar(this);
        seek.setMax(Math.max(0, pages.size() - 1));
        seek.setProgress(currentPage);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) input.setText(String.valueOf(progress + 1));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        form.addView(seek);

        new AlertDialog.Builder(this)
                .setTitle("페이지 이동")
                .setView(form)
                .setPositiveButton("이동", (dialog, which) -> {
                    int target = parseInt(input.getText().toString(), currentPage + 1) - 1;
                    goToPage(target);
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void addBookmarkForCurrentPage() {
        if (activeItem == null) return;
        if (!activeItem.bookmarks.contains(currentPage)) {
            activeItem.bookmarks.add(currentPage);
            Collections.sort(activeItem.bookmarks);
            saveLibrary();
            Toast.makeText(this, String.format(Locale.KOREA, "%d페이지 북마크 저장", currentPage + 1), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "이미 북마크된 페이지예요.", Toast.LENGTH_SHORT).show();
        }
        renderPage();
    }

    private void showBookmarkDialog() {
        if (activeItem == null || activeItem.bookmarks.isEmpty()) {
            Toast.makeText(this, "아직 북마크가 없어요.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[activeItem.bookmarks.size()];
        for (int i = 0; i < activeItem.bookmarks.size(); i++) {
            labels[i] = String.format(Locale.KOREA, "%d페이지", activeItem.bookmarks.get(i) + 1);
        }
        new AlertDialog.Builder(this)
                .setTitle("북마크")
                .setItems(labels, (dialog, which) -> goToPage(activeItem.bookmarks.get(which)))
                .setNegativeButton("닫기", null)
                .show();
    }

    private boolean isBookmarked(int page) {
        return activeItem != null && activeItem.bookmarks.contains(page);
    }

    private void showRenameDialog(LibraryItem item, boolean fromReader) {
        if (item == null) return;
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(valueOr(item.displayTitle, item.title));
        input.setSelection(input.getText().length());
        input.setPadding(dp(18), 0, dp(18), 0);

        new AlertDialog.Builder(this)
                .setTitle("채팅방 이름 변경")
                .setView(input)
                .setPositiveButton("저장", (dialog, which) -> {
                    item.displayTitle = valueOr(input.getText().toString(), item.title);
                    saveLibrary();
                    if (fromReader) {
                        titleView.setText(item.displayTitle);
                    } else {
                        showHome();
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private LibraryItem upsertLibraryItem(Uri uri, ChatDocument doc, String user, String ai) {
        String key = uri.toString();
        LibraryItem item = findByUri(key);
        if (item == null) {
            item = new LibraryItem();
            item.id = String.valueOf(System.currentTimeMillis());
            item.uri = key;
            item.addedAt = System.currentTimeMillis();
            library.add(0, item);
        }
        item.title = valueOr(doc.title, "크랙 채팅");
        if (item.displayTitle == null || item.displayTitle.trim().isEmpty()) {
            item.displayTitle = item.title;
        }
        item.userName = user;
        item.aiName = ai;
        item.lastPage = 0;
        return item;
    }

    private LibraryItem findByUri(String uri) {
        for (LibraryItem item : library) {
            if (item.uri.equals(uri)) return item;
        }
        return null;
    }

    private ArrayList<LibraryItem> filteredItems(boolean archived) {
        ArrayList<LibraryItem> out = new ArrayList<>();
        for (LibraryItem item : library) {
            if (item.archived == archived) out.add(item);
        }
        return out;
    }

    private int countArchived(boolean archived) {
        int count = 0;
        for (LibraryItem item : library) {
            if (item.archived == archived) count++;
        }
        return count;
    }

    private void loadLibrary() {
        library.clear();
        String raw = prefs.getString(PREF_LIBRARY, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                library.add(LibraryItem.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {
            library.clear();
        }
    }

    private void saveLibrary() {
        JSONArray arr = new JSONArray();
        for (LibraryItem item : library) {
            arr.put(item.toJson());
        }
        prefs.edit().putString(PREF_LIBRARY, arr.toString()).apply();
    }

    private String readAll(Uri uri) throws IOException {
        InputStream stream = getContentResolver().openInputStream(uri);
        if (stream == null) throw new IOException("stream is null");
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        reader.close();
        return sb.toString();
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp * textScale);
        tv.setTextColor(color);
        tv.setTypeface(Typeface.DEFAULT, style);
        tv.setIncludeFontPadding(true);
        return tv;
    }

    private Button primaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setBackground(rounded(RED, dp(10), Color.TRANSPARENT, 0));
        return b;
    }

    private Button secondaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setBackground(rounded(Color.rgb(42, 42, 46), dp(10), Color.TRANSPARENT, 0));
        return b;
    }

    private Button smallButton(String label) {
        Button b = secondaryButton(label);
        b.setTextSize(12);
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    private Button ghostButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(rounded(Color.argb(172, 24, 24, 26), dp(18), Color.argb(120, 255, 96, 108), 1));
        return b;
    }

    private Button iconButton(String label) {
        Button b = ghostButton(label);
        b.setBackground(rounded(PANEL_2, dp(10), Color.rgb(55, 52, 55), 1));
        return b;
    }

    private Button roundFloatButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(20);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, dp(3));
        b.setBackground(rounded(Color.argb(224, 42, 42, 45), dp(23), Color.argb(160, 255, 84, 91), 1));
        return b;
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        if (strokeWidth > 0) g.setStroke(strokeWidth, strokeColor);
        return g;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String join(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    private String valueOr(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return value.trim();
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private String formatDate(long time) {
        return new SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(new Date(time));
    }

    private String extractImageUrl(String line) {
        Matcher m = Pattern.compile("^!\\[[^\\]]*\\]\\(([^)]+)\\)").matcher(line);
        if (m.find()) return m.group(1).trim();
        int start = line.indexOf("](");
        if (line.startsWith("![") && start >= 0) {
            String rest = line.substring(start + 2).trim();
            if (rest.endsWith(")")) rest = rest.substring(0, rest.length() - 1);
            if (rest.startsWith("http")) return rest;
        }
        if (line.startsWith("http") && line.matches("(?i).*\\.(png|jpg|jpeg|webp)(\\?.*)?$")) return line;
        return null;
    }

    enum Role {
        USER,
        AI
    }

    static class LibraryItem {
        String id = "";
        String uri = "";
        String title = "크랙 채팅";
        String displayTitle = "";
        String userName = "유저";
        String aiName = "AI";
        long addedAt = System.currentTimeMillis();
        int lastPage = 0;
        boolean archived = false;
        final ArrayList<Integer> bookmarks = new ArrayList<>();

        JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("id", id);
                obj.put("uri", uri);
                obj.put("title", title);
                obj.put("displayTitle", displayTitle);
                obj.put("userName", userName);
                obj.put("aiName", aiName);
                obj.put("addedAt", addedAt);
                obj.put("lastPage", lastPage);
                obj.put("archived", archived);
                JSONArray bms = new JSONArray();
                for (Integer page : bookmarks) bms.put(page);
                obj.put("bookmarks", bms);
            } catch (Exception ignored) {
            }
            return obj;
        }

        static LibraryItem fromJson(JSONObject obj) {
            LibraryItem item = new LibraryItem();
            item.id = obj.optString("id", "");
            item.uri = obj.optString("uri", "");
            item.title = obj.optString("title", "크랙 채팅");
            item.displayTitle = obj.optString("displayTitle", item.title);
            item.userName = obj.optString("userName", "유저");
            item.aiName = obj.optString("aiName", "AI");
            item.addedAt = obj.optLong("addedAt", System.currentTimeMillis());
            item.lastPage = obj.optInt("lastPage", 0);
            item.archived = obj.optBoolean("archived", false);
            JSONArray bms = obj.optJSONArray("bookmarks");
            if (bms != null) {
                for (int i = 0; i < bms.length(); i++) {
                    item.bookmarks.add(bms.optInt(i, 0));
                }
            }
            return item;
        }
    }

    static class ChatDocument {
        String title = "크랙 채팅";
        String chatId = "";
        final List<ChatBlock> blocks = new ArrayList<>();
    }

    static class ChatBlock {
        final Role role;
        final String body;
        final int charCount;
        final int imageCount;

        ChatBlock(Role role, String body) {
            this.role = role;
            this.body = body == null ? "" : body.trim();
            this.charCount = this.body.length();
            this.imageCount = countImages(this.body);
        }

        private static int countImages(String body) {
            Matcher m = Pattern.compile("(?m)^!\\[[^\\]]*\\]\\(").matcher(body);
            int count = 0;
            while (m.find()) count++;
            return count;
        }
    }

    static class PageMaker {
        private static final int MAX_BLOCKS = 12;
        private static final int MAX_CHARS = 8000;
        private static final int MAX_IMAGES = 5;

        static List<List<ChatBlock>> makePages(List<ChatBlock> blocks) {
            ArrayList<List<ChatBlock>> pages = new ArrayList<>();
            ArrayList<ChatBlock> current = new ArrayList<>();
            int chars = 0;
            int images = 0;

            for (ChatBlock block : blocks) {
                boolean overflow = !current.isEmpty()
                        && (current.size() >= MAX_BLOCKS
                        || chars + block.charCount > MAX_CHARS
                        || images + block.imageCount > MAX_IMAGES);

                if (overflow) {
                    pages.add(current);
                    current = new ArrayList<>();
                    chars = 0;
                    images = 0;
                }

                current.add(block);
                chars += block.charCount;
                images += block.imageCount;
            }

            if (!current.isEmpty()) pages.add(current);
            return pages;
        }
    }

    static class CrackMarkdownParser {
        static ChatDocument parse(String markdown) {
            ChatDocument doc = new ChatDocument();
            String normalized = markdown == null ? "" : markdown.replace("\r\n", "\n").replace('\r', '\n');
            String[] lines = normalized.split("\n", -1);

            Role role = null;
            StringBuilder body = new StringBuilder();

            for (String line : lines) {
                if (line.startsWith("# ") && doc.title.equals("크랙 채팅")) {
                    doc.title = line.substring(2).trim();
                    continue;
                }
                if (line.startsWith("- chatId:")) {
                    doc.chatId = line.substring("- chatId:".length()).trim();
                    continue;
                }
                if (line.equals("## User") || line.equals("## AI")) {
                    flush(doc, role, body);
                    role = line.equals("## User") ? Role.USER : Role.AI;
                    body.setLength(0);
                    continue;
                }
                if (role != null) {
                    body.append(line).append('\n');
                }
            }
            flush(doc, role, body);
            return doc;
        }

        private static void flush(ChatDocument doc, Role role, StringBuilder body) {
            if (role == null) return;
            String text = body.toString().trim();
            if (!text.isEmpty()) doc.blocks.add(new ChatBlock(role, text));
        }
    }

    private class ImageTask extends AsyncTask<Void, Void, Bitmap> {
        private final String url;
        private final ImageView imageView;
        private final TextView statusView;
        private String error = "";

        ImageTask(String url, ImageView imageView, TextView statusView) {
            this.url = url;
            this.imageView = imageView;
            this.statusView = statusView;
        }

        @Override
        protected Bitmap doInBackground(Void... voids) {
            try {
                Bitmap cached = imageCache.get(url);
                if (cached != null) return cached;
                Bitmap bitmap = fetchScaledBitmap(url, Math.max(640, getResources().getDisplayMetrics().widthPixels));
                if (bitmap != null) imageCache.put(url, bitmap);
                return bitmap;
            } catch (Exception e) {
                error = e.getMessage();
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
                imageView.setAdjustViewBounds(true);
                imageView.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                imageView.requestLayout();
                statusView.setVisibility(View.GONE);
            } else {
                statusView.setText("이미지를 불러오지 못했어요. " + url + (error.isEmpty() ? "" : "\n" + error));
            }
        }
    }

    private Bitmap fetchScaledBitmap(String rawUrl, int targetWidth) throws IOException {
        final int maxImageBytes = 18 * 1024 * 1024;
        HttpURLConnection conn = (HttpURLConnection) new URL(rawUrl).openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("User-Agent", "dallruby-crack-ebook-reader");
        conn.connect();
        if (conn.getResponseCode() < 200 || conn.getResponseCode() >= 300) {
            throw new IOException("HTTP " + conn.getResponseCode());
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        InputStream in = conn.getInputStream();
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            bytes.write(buffer, 0, read);
            if (bytes.size() > maxImageBytes) {
                in.close();
                conn.disconnect();
                throw new IOException("image is too large");
            }
        }
        in.close();
        conn.disconnect();

        byte[] data = bytes.toByteArray();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sampleSize(bounds.outWidth, targetWidth);
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
    }

    private int sampleSize(int width, int targetWidth) {
        int sample = 1;
        while (width / sample > targetWidth * 2) sample *= 2;
        return Math.max(1, sample);
    }
}
