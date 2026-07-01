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
import android.text.SpannableStringBuilder;
import android.text.Spanned;
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
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int REQ_OPEN_MD = 4001;
    private static final int BG = Color.rgb(17, 17, 17);
    private static final int PANEL = Color.rgb(30, 30, 30);
    private static final int CARD = Color.rgb(38, 37, 35);
    private static final int CARD_HEAD = Color.rgb(78, 76, 71);
    private static final int RED = Color.rgb(255, 75, 75);
    private static final int TEXT = Color.rgb(230, 230, 230);
    private static final int MUTED = Color.rgb(161, 161, 161);

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
    private ScrollView scrollView;
    private SharedPreferences prefs;

    private ChatDocument document;
    private List<List<ChatBlock>> pages = new ArrayList<>();
    private int currentPage = 0;
    private String userName = "조권주";
    private String aiName = "제상혁";
    private float textScale = 1.0f;
    private Uri currentUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("reader", MODE_PRIVATE);
        userName = prefs.getString("userName", userName);
        aiName = prefs.getString("aiName", aiName);
        textScale = prefs.getFloat("textScale", 1.0f);
        buildShell();
        showHome();
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);
    }

    private void showHome() {
        root.removeAllViews();

        ImageView hero = new ImageView(this);
        hero.setImageResource(getResources().getIdentifier("dallruby_header", "drawable", getPackageName()));
        hero.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(hero, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(22), dp(22), dp(22), dp(22));
        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        TextView title = text("dallruby crack e-book maker", 22, Color.WHITE, Typeface.BOLD);
        body.addView(title);

        TextView sub = text("크랙 채팅 Markdown을 이미지까지 살려서 페이지형 이북처럼 읽어요.", 14, MUTED, Typeface.NORMAL);
        sub.setPadding(0, dp(8), 0, dp(20));
        body.addView(sub);

        Button open = primaryButton("Markdown 파일 열기");
        open.setOnClickListener(v -> openMarkdownFile());
        body.addView(open, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        TextView help = text("파일을 열면 유저 이름과 캐릭터/작품 이름을 먼저 입력합니다.\n1천턴 이상 방도 현재 페이지만 렌더링해서 버티는 구조입니다.", 13, MUTED, Typeface.NORMAL);
        help.setPadding(0, dp(18), 0, 0);
        body.addView(help);
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
            loadDocument(currentUri);
        }
    }

    private void loadDocument(Uri uri) {
        try {
            String markdown = readAll(uri);
            document = CrackMarkdownParser.parse(markdown);
            pages = PageMaker.makePages(document.blocks);
            currentPage = prefs.getInt("page:" + stableKey(uri), 0);
            if (currentPage < 0 || currentPage >= pages.size()) currentPage = 0;
            showNameDialog();
        } catch (Exception e) {
            Toast.makeText(this, "파일을 읽지 못했어요: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showNameDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);

        EditText user = new EditText(this);
        user.setHint("유저 이름 예: 조권주");
        user.setSingleLine(true);
        user.setText(userName);
        form.addView(user);

        EditText ai = new EditText(this);
        ai.setHint("캐릭터/작품 이름 예: 제상혁");
        ai.setSingleLine(true);
        ai.setText(aiName);
        form.addView(ai);

        new AlertDialog.Builder(this)
                .setTitle("이름 설정")
                .setMessage("Markdown 안의 User / AI를 앱에서 보여줄 이름으로 바꿔요.")
                .setView(form)
                .setPositiveButton("읽기 시작", (dialog, which) -> {
                    userName = valueOr(user.getText().toString(), "유저");
                    aiName = valueOr(ai.getText().toString(), "AI");
                    prefs.edit().putString("userName", userName).putString("aiName", aiName).apply();
                    showReader();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showReader() {
        root.removeAllViews();

        FrameLayout heroWrap = new FrameLayout(this);
        ImageView hero = new ImageView(this);
        hero.setImageResource(getResources().getIdentifier("dallruby_header", "drawable", getPackageName()));
        hero.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroWrap.addView(hero, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.BOTTOM);
        overlay.setPadding(dp(16), 0, dp(16), dp(12));
        GradientDrawable fade = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00111111, 0xE6111111});
        overlay.setBackground(fade);
        heroWrap.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        titleView = text(document.title, 17, Color.WHITE, Typeface.BOLD);
        titleView.setSingleLine(false);
        overlay.addView(titleView);
        pageView = text("", 12, Color.rgb(220, 190, 196), Typeface.BOLD);
        overlay.addView(pageView);
        root.addView(heroWrap, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(122)));

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        pageContainer = new LinearLayout(this);
        pageContainer.setOrientation(LinearLayout.VERTICAL);
        pageContainer.setPadding(dp(18), dp(16), dp(18), dp(24));
        scrollView.addView(pageContainer);
        root.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setPadding(dp(12), dp(8), dp(12), dp(10));
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setBackgroundColor(Color.rgb(20, 20, 20));

        prevButton = secondaryButton("이전");
        prevButton.setOnClickListener(v -> movePage(-1));
        controls.addView(prevButton, new LinearLayout.LayoutParams(0, dp(44), 1));

        Button smaller = secondaryButton("A-");
        smaller.setOnClickListener(v -> changeTextScale(-0.08f));
        controls.addView(smaller, new LinearLayout.LayoutParams(dp(56), dp(44)));

        Button bigger = secondaryButton("A+");
        bigger.setOnClickListener(v -> changeTextScale(0.08f));
        controls.addView(bigger, new LinearLayout.LayoutParams(dp(56), dp(44)));

        nextButton = primaryButton("다음");
        nextButton.setOnClickListener(v -> movePage(1));
        controls.addView(nextButton, new LinearLayout.LayoutParams(0, dp(44), 1));

        root.addView(controls);
        renderPage();
    }

    private void movePage(int delta) {
        int next = currentPage + delta;
        if (next < 0 || next >= pages.size()) return;
        currentPage = next;
        prefs.edit().putInt("page:" + stableKey(currentUri), currentPage).apply();
        renderPage();
    }

    private void changeTextScale(float delta) {
        textScale = Math.max(0.82f, Math.min(1.45f, textScale + delta));
        prefs.edit().putFloat("textScale", textScale).apply();
        renderPage();
    }

    private void renderPage() {
        pageContainer.removeAllViews();
        pageView.setText(String.format(Locale.KOREA, "%d / %d 페이지", currentPage + 1, Math.max(1, pages.size())));

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
            addDivider();
        }

        TextView label = text(block.role == Role.USER ? userName : aiName, 13, block.role == Role.USER ? Color.WHITE : Color.rgb(255, 210, 215), Typeface.BOLD);
        label.setPadding(0, dp(10), 0, dp(8));
        pageContainer.addView(label);

        renderMarkdown(block.body, block.role);
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
                addImage(imageUrl, extractImageAlt(trimmed));
                continue;
            }

            if (trimmed.equals("---")) {
                flushParagraph(paragraph, role);
                addDivider();
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
        String text = join(paragraph).trim();
        paragraph.clear();
        if (text.isEmpty()) return;

        TextView tv = text("", 16, role == Role.USER ? Color.WHITE : TEXT, role == Role.USER ? Typeface.BOLD : Typeface.NORMAL);
        tv.setText(applyInlineMarkdown(text));
        tv.setLineSpacing(dp(3), 1.05f);
        tv.setPadding(0, 0, 0, dp(14));
        pageContainer.addView(tv);
    }

    private void addQuote(String quote) {
        TextView tv = text("", 15, Color.rgb(235, 235, 235), Typeface.BOLD);
        tv.setText(applyInlineMarkdown(quote));
        tv.setPadding(dp(14), dp(10), dp(12), dp(10));
        GradientDrawable bg = rounded(Color.rgb(31, 31, 31), dp(8), Color.rgb(67, 67, 67), 1);
        tv.setBackground(bg);
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

        TextView body = text(code, 14, Color.rgb(238, 238, 238), Typeface.MONOSPACE.getStyle());
        body.setTypeface(Typeface.MONOSPACE);
        body.setLineSpacing(dp(2), 1.0f);
        body.setPadding(dp(14), dp(12), dp(14), dp(14));
        card.addView(body);
    }

    private void addImage(String url, String alt) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(rounded(PANEL, dp(10), Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        boxLp.setMargins(0, dp(4), 0, dp(16));
        pageContainer.addView(box, boxLp);

        TextView caption = text(valueOr(alt, "이미지"), 12, MUTED, Typeface.BOLD);
        caption.setPadding(dp(12), dp(8), dp(12), dp(6));
        box.addView(caption);

        ImageView image = new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(22, 22, 22));
        box.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)));

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

    private void addDivider() {
        View line = new View(this);
        line.setBackgroundColor(Color.rgb(48, 48, 48));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.setMargins(0, dp(18), 0, dp(8));
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
                    out.setSpan(new StyleSpan(Typeface.ITALIC), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 1;
                    continue;
                }
            }
            out.append(source.charAt(i));
            i += 1;
        }
        return out;
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
        b.setBackground(rounded(RED, dp(8), Color.TRANSPARENT, 0));
        return b;
    }

    private Button secondaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(rounded(Color.rgb(42, 42, 46), dp(8), Color.TRANSPARENT, 0));
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

    private String stableKey(Uri uri) {
        return uri == null ? "none" : uri.toString();
    }

    private String extractImageAlt(String line) {
        Matcher m = Pattern.compile("^!\\[([^\\]]*)\\]").matcher(line);
        return m.find() ? m.group(1) : "";
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
        conn.setRequestProperty("User-Agent", "dallruby-crack-ebook-maker");
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
