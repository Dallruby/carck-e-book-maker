package com.dallruby.crackebookmaker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
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
import android.view.ViewOutlineProvider;
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
import java.io.File;
import java.io.FileOutputStream;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int REQ_OPEN_MD = 4001;
    private static final int REQ_PICK_THUMBNAIL = 4002;
    private static final String PREF_LIBRARY = "library_items_v2";
    private static final String PROMO_URL = "https://share.crack.wrtn.ai/w65wwr";

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
    private ScrollView scrollView;
    private FrameLayout readerFrame;
    private SharedPreferences prefs;

    private final ArrayList<LibraryItem> library = new ArrayList<>();
    private final Set<String> deleteSelection = new HashSet<>();
    private final ArrayList<ParagraphAnchor> renderedAnchors = new ArrayList<>();
    private ChatDocument document;
    private LibraryItem activeItem;
    private List<List<ChatBlock>> pages = new ArrayList<>();
    private int currentPage = 0;
    private String userName = "유저";
    private String aiName = "AI";
    private float textScale = 1.0f;
    private boolean deleteMode = false;
    private boolean lightTheme = false;
    private boolean bookmarkPickMode = false;
    private Uri currentUri;
    private String pendingBookmarkKey = "";
    private int pendingScrollY = -1;
    private LibraryItem pendingThumbnailItem;
    private Bitmap cropSourceBitmap;
    private ImageView cropPreview;
    private int cropX = 0;
    private int cropY = 0;
    private int cropSize = 0;

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
        if (deleteMode) {
            deleteMode = false;
            deleteSelection.clear();
            showHome();
        } else if (activeItem != null) {
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
        lightTheme = false;
        root.removeAllViews();

        ScrollView homeScroll = new ScrollView(this);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(0, 0, 0, dp(12));
        homeScroll.addView(shell);
        root.addView(homeScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        shell.addView(heroHome(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(246)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(18), dp(18), 0);
        shell.addView(body, centeredContentParams());

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

        if (deleteMode) {
            addDeleteBar(body);
        }

        addSection(body, "보관함", true);
        addSection(body, "채팅방 목록", false);
        root.addView(promoBanner(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(86)));
    }

    private void addDeleteBar(LinearLayout body) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, dp(14), 0, 0);
        body.addView(bar);

        Button delete = primaryButton(String.format(Locale.KOREA, "선택 삭제 %d", deleteSelection.size()));
        delete.setOnClickListener(v -> deleteSelectedItems());
        bar.addView(delete, new LinearLayout.LayoutParams(0, dp(50), 1));

        Button cancel = secondaryButton("취소");
        cancel.setOnClickListener(v -> {
            deleteMode = false;
            deleteSelection.clear();
            showHome();
        });
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(dp(92), dp(50));
        cancelLp.setMargins(dp(10), 0, 0, 0);
        bar.addView(cancel, cancelLp);
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

    private View promoBanner() {
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(10), dp(7), dp(10), dp(9));
        wrap.setBackgroundColor(BG);
        wrap.setOnClickListener(v -> openPromoLink());

        ImageView banner = new ImageView(this);
        banner.setImageResource(getResources().getIdentifier("paedo_sib_banner", "drawable", getPackageName()));
        banner.setScaleType(ImageView.ScaleType.FIT_CENTER);
        banner.setAdjustViewBounds(true);
        banner.setBackground(rounded(PANEL, dp(12), RED_DARK, 1));
        banner.setClipToOutline(true);
        banner.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(12));
            }
        });
        FrameLayout.LayoutParams bannerLp = new FrameLayout.LayoutParams(contentMaxWidth(), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
        wrap.addView(banner, bannerLp);
        return wrap;
    }

    private void openPromoLink() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PROMO_URL)));
        } catch (Exception e) {
            Toast.makeText(this, "링크를 열 수 없어요.", Toast.LENGTH_SHORT).show();
        }
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
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.TOP);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        boolean selected = deleteSelection.contains(item.id);
        int rowBg = selected ? Color.rgb(74, 18, 27) : PANEL;
        int rowStroke = selected ? RED : (archivedSection ? RED_DARK : Color.rgb(48, 45, 47));
        card.setBackground(rounded(rowBg, dp(12), rowStroke, selected ? 2 : 1));
        card.setOnClickListener(v -> {
            if (deleteMode) {
                toggleDeleteSelection(item);
            } else {
                openLibraryItem(item);
            }
        });
        card.setOnLongClickListener(v -> {
            deleteMode = true;
            deleteSelection.add(item.id);
            showHome();
            return true;
        });

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(cardLp);

        ImageView thumb = thumbnailView(item, dp(74));
        LinearLayout.LayoutParams thumbLp = new LinearLayout.LayoutParams(dp(74), dp(74));
        thumbLp.setMargins(0, dp(2), dp(12), 0);
        card.addView(thumb, thumbLp);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        card.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView title = text(valueOr(item.displayTitle, item.title), 16, Color.WHITE, Typeface.BOLD);
        title.setSingleLine(false);
        info.addView(title);

        TextView meta = text(formatDate(item.addedAt) + " · " + valueOr(item.aiName, "AI"), 12, MUTED, Typeface.NORMAL);
        meta.setPadding(0, dp(4), 0, dp(10));
        info.addView(meta);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        info.addView(row);

        if (deleteMode) {
            Button select = smallButton(deleteSelection.contains(item.id) ? "선택됨" : "선택");
            select.setOnClickListener(v -> toggleDeleteSelection(item));
            row.addView(select, new LinearLayout.LayoutParams(0, dp(38), 1));

            Button deleteOne = smallButton("X");
            deleteOne.setOnClickListener(v -> confirmDeleteItem(item));
            LinearLayout.LayoutParams xLp = new LinearLayout.LayoutParams(dp(58), dp(38));
            xLp.setMargins(dp(8), 0, 0, 0);
            row.addView(deleteOne, xLp);
            return card;
        }

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

    private ImageView thumbnailView(LibraryItem item, int size) {
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(rounded(PANEL_2, dp(10), RED_DARK, 1));
        image.setClipToOutline(true);
        image.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(10));
            }
        });
        Bitmap custom = loadThumbnailBitmap(item);
        if (custom != null) {
            image.setImageBitmap(custom);
        } else {
            image.setImageResource(getResources().getIdentifier("ic_launcher", "drawable", getPackageName()));
        }
        image.setOnClickListener(v -> {
            if (deleteMode) {
                toggleDeleteSelection(item);
            } else {
                pickThumbnail(item);
            }
        });
        image.setOnLongClickListener(v -> {
            pickThumbnail(item);
            return true;
        });
        return image;
    }

    private void showHelpDialog() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(8), dp(16), dp(8));
        scroll.addView(list);
        scroll.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(460)));

        addHelpSection(list, "채팅방 불러오기",
                "1. 달루비 크랙 이북 메이커 확장 프로그램으로 크랙 채팅을 md 파일로 저장해요.\n"
                        + "2. 앱 서재에서 채팅방 불러오기를 눌러요.\n"
                        + "3. {user} 이름과 {char} 이름을 적고 읽기 시작해요.");
        addHelpSection(list, "서재와 보관함",
                "채팅방 목록에는 불러온 방이 쌓여요.\n"
                        + "자주 읽는 방은 보관 버튼이나 채팅방 메뉴에서 보관함으로 이동할 수 있어요.\n"
                        + "목록에서 길게 누르면 여러 채팅방을 선택해서 삭제할 수 있어요.");
        addHelpSection(list, "썸네일 바꾸기",
                "서재 목록의 왼쪽 썸네일을 누르면 이미지를 바꿀 수 있어요.\n"
                        + "이미지를 고른 뒤 확대, 축소, 상하좌우 버튼으로 정방형 크롭 위치를 맞추고 저장해요.\n"
                        + "채팅방 안의 점 3개 메뉴에서도 썸네일을 수정할 수 있어요.");
        addHelpSection(list, "읽기 화면",
                "이전/다음으로 페이지를 넘기고, 가운데 페이지 숫자를 눌러 원하는 페이지로 이동해요.\n"
                        + "A-/A+로 글자 크기를 바꿀 수 있고, 읽던 페이지는 채팅방마다 저장돼요.");
        addHelpSection(list, "북마크",
                "오른쪽 아래 북마크 버튼을 누르면 문단 선택 모드가 켜져요.\n"
                        + "원하는 문단 앞의 동그라미를 눌러 북마크하고, 이미 저장된 북마크는 길게 눌러 삭제해요.\n"
                        + "상단 북마크 버튼에서 저장한 위치로 바로 이동할 수 있어요. 최대 10개까지 저장돼요.");

        new AlertDialog.Builder(this)
                .setTitle("사용법")
                .setView(scroll)
                .setPositiveButton("확인", null)
                .show();
    }

    private void addHelpSection(LinearLayout parent, String title, String body) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        box.setBackground(rounded(PANEL, dp(10), Color.rgb(58, 52, 56), 1));

        TextView head = text(title + "  +", 15, TEXT, Typeface.BOLD);
        box.addView(head);

        TextView detail = text(body, 13, MUTED, Typeface.NORMAL);
        detail.setPadding(0, dp(8), 0, 0);
        detail.setLineSpacing(dp(3), 1.0f);
        detail.setVisibility(View.GONE);
        box.addView(detail);

        box.setOnClickListener(v -> {
            boolean open = detail.getVisibility() != View.VISIBLE;
            detail.setVisibility(open ? View.VISIBLE : View.GONE);
            head.setText(title + (open ? "  -" : "  +"));
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        parent.addView(box, lp);
    }

    private void toggleDeleteSelection(LibraryItem item) {
        if (deleteSelection.contains(item.id)) {
            deleteSelection.remove(item.id);
        } else {
            deleteSelection.add(item.id);
        }
        showHome();
    }

    private void confirmDeleteItem(LibraryItem item) {
        new AlertDialog.Builder(this)
                .setTitle("채팅방 삭제")
                .setMessage(valueOr(item.displayTitle, item.title) + "\n목록에서 삭제할까요?")
                .setPositiveButton("삭제", (dialog, which) -> {
                    library.remove(item);
                    deleteSelection.remove(item.id);
                    if (deleteSelection.isEmpty()) deleteMode = false;
                    saveLibrary();
                    showHome();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void deleteSelectedItems() {
        if (deleteSelection.isEmpty()) {
            Toast.makeText(this, "삭제할 채팅방을 선택해줘.", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("선택 삭제")
                .setMessage(String.format(Locale.KOREA, "%d개 채팅방을 목록에서 삭제할까요?", deleteSelection.size()))
                .setPositiveButton("삭제", (dialog, which) -> {
                    for (int i = library.size() - 1; i >= 0; i--) {
                        if (deleteSelection.contains(library.get(i).id)) {
                            library.remove(i);
                        }
                    }
                    deleteSelection.clear();
                    deleteMode = false;
                    saveLibrary();
                    showHome();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void openMarkdownFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/markdown", "text/plain", "application/octet-stream"});
        startActivityForResult(intent, REQ_OPEN_MD);
    }

    private void pickThumbnail(LibraryItem item) {
        if (item == null) return;
        pendingThumbnailItem = item;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQ_PICK_THUMBNAIL);
    }

    private void loadThumbnailForCrop(Uri uri) {
        try {
            cropSourceBitmap = decodeBitmapFromUri(uri, 1800);
            if (cropSourceBitmap == null) throw new IOException("bitmap is null");
            int side = Math.min(cropSourceBitmap.getWidth(), cropSourceBitmap.getHeight());
            cropSize = side;
            cropX = (cropSourceBitmap.getWidth() - side) / 2;
            cropY = (cropSourceBitmap.getHeight() - side) / 2;
            showThumbnailCropDialog();
        } catch (Exception e) {
            Toast.makeText(this, "이미지를 열 수 없어요: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private Bitmap decodeBitmapFromUri(Uri uri, int targetMax) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        InputStream first = getContentResolver().openInputStream(uri);
        if (first == null) throw new IOException("stream is null");
        BitmapFactory.decodeStream(first, null, bounds);
        first.close();

        int sample = 1;
        int max = Math.max(bounds.outWidth, bounds.outHeight);
        while (max / sample > targetMax * 2) sample *= 2;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        InputStream second = getContentResolver().openInputStream(uri);
        if (second == null) throw new IOException("stream is null");
        Bitmap bitmap = BitmapFactory.decodeStream(second, null, opts);
        second.close();
        return bitmap;
    }

    private void showThumbnailCropDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(8), dp(16), 0);

        cropPreview = new ImageView(this);
        cropPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cropPreview.setBackground(rounded(PANEL, dp(10), Color.rgb(58, 52, 56), 1));
        form.addView(cropPreview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260)));

        TextView hint = text("정방형으로 들어갈 부분을 맞춰줘.", 13, MUTED, Typeface.BOLD);
        hint.setPadding(0, dp(10), 0, dp(8));
        form.addView(hint);

        LinearLayout zoomRow = new LinearLayout(this);
        zoomRow.setOrientation(LinearLayout.HORIZONTAL);
        form.addView(zoomRow);
        Button zoomIn = secondaryButton("확대");
        zoomIn.setOnClickListener(v -> resizeCrop(0.86f));
        zoomRow.addView(zoomIn, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button zoomOut = secondaryButton("축소");
        zoomOut.setOnClickListener(v -> resizeCrop(1.16f));
        LinearLayout.LayoutParams zoomOutLp = new LinearLayout.LayoutParams(0, dp(42), 1);
        zoomOutLp.setMargins(dp(8), 0, 0, 0);
        zoomRow.addView(zoomOut, zoomOutLp);

        LinearLayout moveRow = new LinearLayout(this);
        moveRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams moveRowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        moveRowLp.setMargins(0, dp(8), 0, 0);
        form.addView(moveRow, moveRowLp);
        addMoveButton(moveRow, "←", -1, 0);
        addMoveButton(moveRow, "↑", 0, -1);
        addMoveButton(moveRow, "↓", 0, 1);
        addMoveButton(moveRow, "→", 1, 0);

        updateCropPreview();

        new AlertDialog.Builder(this)
                .setTitle("썸네일 크롭")
                .setView(form)
                .setPositiveButton("저장", (dialog, which) -> saveCroppedThumbnail())
                .setNegativeButton("취소", null)
                .show();
    }

    private void addMoveButton(LinearLayout parent, String label, int dx, int dy) {
        Button button = secondaryButton(label);
        button.setTextSize(18);
        button.setOnClickListener(v -> moveCrop(dx, dy));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1);
        if (parent.getChildCount() > 0) lp.setMargins(dp(8), 0, 0, 0);
        parent.addView(button, lp);
    }

    private void resizeCrop(float factor) {
        if (cropSourceBitmap == null) return;
        int oldSize = cropSize;
        int centerX = cropX + oldSize / 2;
        int centerY = cropY + oldSize / 2;
        int minSide = Math.min(cropSourceBitmap.getWidth(), cropSourceBitmap.getHeight());
        cropSize = Math.max(dp(80), Math.min(minSide, Math.round(cropSize * factor)));
        cropX = centerX - cropSize / 2;
        cropY = centerY - cropSize / 2;
        clampCrop();
        updateCropPreview();
    }

    private void moveCrop(int dx, int dy) {
        if (cropSourceBitmap == null) return;
        int step = Math.max(8, cropSize / 8);
        cropX += dx * step;
        cropY += dy * step;
        clampCrop();
        updateCropPreview();
    }

    private void clampCrop() {
        if (cropSourceBitmap == null) return;
        cropSize = Math.max(1, Math.min(cropSize, Math.min(cropSourceBitmap.getWidth(), cropSourceBitmap.getHeight())));
        cropX = Math.max(0, Math.min(cropX, cropSourceBitmap.getWidth() - cropSize));
        cropY = Math.max(0, Math.min(cropY, cropSourceBitmap.getHeight() - cropSize));
    }

    private void updateCropPreview() {
        if (cropPreview == null || cropSourceBitmap == null) return;
        try {
            clampCrop();
            Bitmap cropped = Bitmap.createBitmap(cropSourceBitmap, cropX, cropY, cropSize, cropSize);
            cropPreview.setImageBitmap(cropped);
        } catch (Exception ignored) {
        }
    }

    private void saveCroppedThumbnail() {
        if (pendingThumbnailItem == null || cropSourceBitmap == null) return;
        try {
            clampCrop();
            Bitmap cropped = Bitmap.createBitmap(cropSourceBitmap, cropX, cropY, cropSize, cropSize);
            Bitmap square = Bitmap.createScaledBitmap(cropped, 512, 512, true);
            File dir = new File(getFilesDir(), "thumbnails");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, pendingThumbnailItem.id + ".jpg");
            FileOutputStream out = new FileOutputStream(file);
            square.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            out.close();
            pendingThumbnailItem.thumbnailPath = file.getAbsolutePath();
            saveLibrary();
            Toast.makeText(this, "썸네일을 저장했어요.", Toast.LENGTH_SHORT).show();
            if (activeItem == null) {
                showHome();
            }
        } catch (Exception e) {
            Toast.makeText(this, "썸네일 저장 실패: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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
        } else if (requestCode == REQ_PICK_THUMBNAIL && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            loadThumbnailForCrop(uri);
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
            lightTheme = item.lightTheme;
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
        shell.setBackgroundColor(themed(BG));
        root.addView(shell, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        shell.addView(readerHeader(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(132)));

        readerFrame = new FrameLayout(this);
        scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        pageContainer = new LinearLayout(this);
        pageContainer.setOrientation(LinearLayout.VERTICAL);
        pageContainer.setPadding(dp(18), dp(16), dp(18), dp(80));
        ScrollView.LayoutParams pageLp = new ScrollView.LayoutParams(contentMaxWidth(), ViewGroup.LayoutParams.WRAP_CONTENT);
        pageLp.gravity = Gravity.CENTER_HORIZONTAL;
        scrollView.addView(pageContainer, pageLp);
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
        bookmarkButton.setOnClickListener(v -> showBookmarkDialog());
        LinearLayout.LayoutParams bmLp = new LinearLayout.LayoutParams(dp(82), dp(36));
        bmLp.setMargins(dp(8), 0, 0, 0);
        top.addView(bookmarkButton, bmLp);

        View spacer = new View(this);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));

        Button menu = ghostButton("⋮");
        menu.setTextSize(20);
        menu.setOnClickListener(v -> showChatMenu());
        LinearLayout.LayoutParams menuLp = new LinearLayout.LayoutParams(dp(46), dp(36));
        menuLp.setMargins(dp(8), 0, 0, 0);
        top.addView(menu, menuLp);

        titleView = text(valueOr(activeItem == null ? null : activeItem.displayTitle, document.title), 17, Color.WHITE, Typeface.BOLD);
        titleView.setTextColor(Color.WHITE);
        titleView.setSingleLine(false);
        titleView.setPadding(0, dp(7), 0, 0);
        overlay.addView(titleView);

        pageView = text("", 12, SOFT, Typeface.BOLD);
        pageView.setTextColor(SOFT);
        overlay.addView(pageView);

        return heroWrap;
    }

    private View readerControls() {
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setPadding(dp(10), dp(8), dp(10), dp(10));
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setBackgroundColor(themed(Color.rgb(19, 19, 20)));

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

        Button mark = roundFloatButton("🔖");
        mark.setTextSize(17);
        mark.setOnClickListener(v -> {
            pendingScrollY = scrollView == null ? -1 : scrollView.getScrollY();
            bookmarkPickMode = !bookmarkPickMode;
            Toast.makeText(this, bookmarkPickMode ? "북마크할 문단을 골라줘." : "북마크 선택 모드 종료", Toast.LENGTH_SHORT).show();
            renderPage();
        });
        floating.addView(mark, new LinearLayout.LayoutParams(dp(46), dp(46)));

        Button up = roundFloatButton("↑");
        up.setOnClickListener(v -> scrollView.smoothScrollTo(0, 0));
        LinearLayout.LayoutParams upLp = new LinearLayout.LayoutParams(dp(46), dp(46));
        upLp.setMargins(0, dp(8), 0, 0);
        floating.addView(up, upLp);

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
        bookmarkButton.setText(String.format(Locale.KOREA, "북마크 %d/10", activeItem == null ? 0 : activeItem.bookmarks.size()));
        renderedAnchors.clear();

        if (pages.isEmpty()) {
            pageContainer.addView(text("읽을 메시지가 없어요.", 16, TEXT, Typeface.NORMAL));
            return;
        }

        List<ChatBlock> page = pages.get(currentPage);
        for (int i = 0; i < page.size(); i++) {
            renderChatBlock(page.get(i), i);
        }

        prevButton.setEnabled(currentPage > 0);
        nextButton.setEnabled(currentPage < pages.size() - 1);
        scrollView.post(() -> {
            if (pendingScrollY >= 0) {
                scrollView.scrollTo(0, Math.min(pendingScrollY, Math.max(0, pageContainer.getBottom())));
                pendingScrollY = -1;
            } else if (pendingBookmarkKey != null && !pendingBookmarkKey.isEmpty()) {
                View target = findAnchorView(pendingBookmarkKey);
                if (target != null) {
                    scrollView.scrollTo(0, Math.max(0, target.getTop() - dp(18)));
                } else {
                    scrollView.scrollTo(0, 0);
                }
                pendingBookmarkKey = "";
            } else {
                scrollView.scrollTo(0, 0);
            }
        });
    }

    private void renderChatBlock(ChatBlock block, int blockIndex) {
        if (block.role == Role.USER) {
            addDivider(dp(16), dp(10));
        }

        TextView label = text(block.role == Role.USER ? userName : aiName, 13, block.role == Role.USER ? Color.WHITE : Color.rgb(255, 211, 216), Typeface.BOLD);
        label.setPadding(0, dp(6), 0, dp(8));
        pageContainer.addView(label);

        renderMarkdown(block.body, block.role, blockIndex);

        if (block.role == Role.USER) {
            addDivider(dp(4), dp(16));
        }
    }

    private void renderMarkdown(String body, Role role, int blockIndex) {
        String[] lines = body.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        ArrayList<String> paragraph = new ArrayList<>();
        boolean inCode = false;
        String codeLang = "";
        ArrayList<String> codeLines = new ArrayList<>();
        int paragraphIndex = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                if (flushParagraph(paragraph, role, blockIndex, paragraphIndex)) paragraphIndex++;
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
                if (flushParagraph(paragraph, role, blockIndex, paragraphIndex)) paragraphIndex++;
                addImage(imageUrl);
                continue;
            }

            if (trimmed.equals("---")) {
                if (flushParagraph(paragraph, role, blockIndex, paragraphIndex)) paragraphIndex++;
                addDivider(dp(14), dp(14));
                continue;
            }

            if (trimmed.startsWith(">")) {
                if (flushParagraph(paragraph, role, blockIndex, paragraphIndex)) paragraphIndex++;
                addQuote(trimmed.replaceFirst("^>\\s?", ""));
                continue;
            }

            if (trimmed.isEmpty()) {
                if (flushParagraph(paragraph, role, blockIndex, paragraphIndex)) paragraphIndex++;
            } else {
                paragraph.add(line);
            }
        }

        flushParagraph(paragraph, role, blockIndex, paragraphIndex);
        if (inCode && !codeLines.isEmpty()) {
            addCodeCard(codeLang, join(codeLines));
        }
    }

    private boolean flushParagraph(ArrayList<String> paragraph, Role role, int blockIndex, int paragraphIndex) {
        if (paragraph.isEmpty()) return false;
        String value = join(paragraph).trim();
        paragraph.clear();
        if (value.isEmpty()) return false;

        String key = bookmarkKey(currentPage, blockIndex, paragraphIndex, value);
        boolean marked = hasBookmarkKey(key);
        TextView tv = text("", 16, role == Role.USER ? Color.WHITE : TEXT, role == Role.USER ? Typeface.BOLD : Typeface.NORMAL);
        tv.setText(applyInlineMarkdown(marked ? "🔖 " + value : value));
        tv.setLineSpacing(dp(4), 1.05f);
        tv.setPadding(0, 0, 0, dp(14));
        if (marked) {
            tv.setOnLongClickListener(v -> {
                confirmDeleteBookmark(key);
                return true;
            });
        }

        if (bookmarkPickMode) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            pageContainer.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            Button pick = smallCircleButton(marked ? "✓" : "○");
            pick.setOnClickListener(v -> {
                if (hasBookmarkKey(key)) {
                    Toast.makeText(this, "이미 북마크된 문단이에요. 길게 누르면 삭제할 수 있어요.", Toast.LENGTH_SHORT).show();
                } else {
                    addBookmarkAt(key, currentPage, value);
                }
            });
            pick.setOnLongClickListener(v -> {
                if (hasBookmarkKey(key)) {
                    confirmDeleteBookmark(key);
                    return true;
                }
                return false;
            });
            LinearLayout.LayoutParams pickLp = new LinearLayout.LayoutParams(dp(38), dp(38));
            pickLp.setMargins(0, 0, dp(8), 0);
            row.addView(pick, pickLp);

            row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        } else {
            pageContainer.addView(tv);
        }
        renderedAnchors.add(new ParagraphAnchor(key, currentPage, tv, previewText(value)));
        return true;
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
        line.setBackgroundColor(themed(LINE));
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

    private void addBookmarkAt(String key, int page, String value) {
        if (activeItem == null) return;
        if (activeItem.bookmarks.size() >= 10) {
            Toast.makeText(this, "북마크는 최대 10개까지 저장할 수 있어요.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (hasBookmarkKey(key)) {
            Toast.makeText(this, "이미 북마크된 문단이에요.", Toast.LENGTH_SHORT).show();
            return;
        }
        BookmarkItem item = new BookmarkItem();
        item.page = page;
        item.key = key;
        item.preview = previewText(value);
        item.createdAt = System.currentTimeMillis();
        activeItem.bookmarks.add(item);
        sortBookmarks(activeItem.bookmarks);
        saveLibrary();
        Toast.makeText(this, bookmarkLabel(item) + " 저장", Toast.LENGTH_SHORT).show();
        pendingScrollY = scrollView == null ? -1 : scrollView.getScrollY();
        renderPage();
    }

    private void confirmDeleteBookmark(String key) {
        BookmarkItem target = findBookmark(key);
        if (target == null) return;
        new AlertDialog.Builder(this)
                .setTitle("북마크 삭제")
                .setMessage(bookmarkLabel(target) + " 북마크를 삭제할까요?")
                .setPositiveButton("삭제", (dialog, which) -> {
                    activeItem.bookmarks.remove(target);
                    saveLibrary();
                    pendingScrollY = scrollView == null ? -1 : scrollView.getScrollY();
                    renderPage();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showBookmarkDialog() {
        if (activeItem == null || activeItem.bookmarks.isEmpty()) {
            Toast.makeText(this, "아직 북마크가 없어요.", Toast.LENGTH_SHORT).show();
            return;
        }
        sortBookmarks(activeItem.bookmarks);
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(8), dp(16), dp(8));
        scroll.addView(list);
        scroll.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(420)));

        for (BookmarkItem item : activeItem.bookmarks) {
            TextView row = text(bookmarkLabel(item) + " (" + valueOr(item.preview, "북마크") + ")", 15, TEXT, Typeface.BOLD);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.setBackground(rounded(PANEL, dp(10), Color.rgb(58, 52, 56), 1));
            row.setOnClickListener(v -> goToBookmark(item));
            row.setOnLongClickListener(v -> {
                confirmDeleteBookmark(item.key);
                return true;
            });
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, 0, 0, dp(8));
            list.addView(row, rowLp);
        }
        new AlertDialog.Builder(this)
                .setTitle("북마크")
                .setView(scroll)
                .setNegativeButton("닫기", null)
                .show();
    }

    private void goToBookmark(BookmarkItem bookmark) {
        pendingBookmarkKey = bookmark.key;
        goToPage(bookmark.page);
    }

    private boolean hasBookmarkKey(String key) {
        if (activeItem == null) return false;
        for (BookmarkItem item : activeItem.bookmarks) {
            if (item.key.equals(key)) return true;
        }
        return false;
    }

    private View findAnchorView(String key) {
        for (ParagraphAnchor anchor : renderedAnchors) {
            if (anchor.key.equals(key)) return anchor.view;
        }
        return null;
    }

    private BookmarkItem findBookmark(String key) {
        if (activeItem == null) return null;
        for (BookmarkItem item : activeItem.bookmarks) {
            if (item.key.equals(key)) return item;
        }
        return null;
    }

    private String bookmarkLabel(BookmarkItem item) {
        int count = 0;
        for (BookmarkItem other : activeItem.bookmarks) {
            if (other.page == item.page) count++;
            if (other == item) break;
        }
        return String.format(Locale.KOREA, "페이지%d-%d", item.page + 1, Math.max(1, count));
    }

    private void sortBookmarks(ArrayList<BookmarkItem> bookmarks) {
        Collections.sort(bookmarks, (a, b) -> {
            if (a.page != b.page) return a.page - b.page;
            return a.key.compareTo(b.key);
        });
    }

    private void showRenameDialog(LibraryItem item, boolean fromReader) {
        if (item == null) return;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);

        TextView label = text("새 채팅방 이름", 13, MUTED, Typeface.BOLD);
        form.addView(label);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(valueOr(item.displayTitle, item.title));
        input.setSelection(input.getText().length());
        input.setPadding(0, dp(4), 0, dp(4));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputLp.setMargins(0, dp(8), 0, 0);
        form.addView(input, inputLp);

        new AlertDialog.Builder(this)
                .setTitle("채팅방 이름 변경")
                .setView(form)
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

    private void showChatMenu() {
        if (activeItem == null) return;
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(16), dp(6), dp(16), dp(8));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("채팅방 메뉴")
                .setView(menu)
                .create();

        addMenuRow(menu, "채팅방 이름 변경", "목록과 상단에 보이는 제목을 바꿔요.", () -> {
            dialog.dismiss();
            showRenameDialog(activeItem, true);
        });
        addMenuRow(menu, "썸네일 수정", "서재 목록에 보이는 정방형 이미지를 바꿔요.", () -> {
            dialog.dismiss();
            pickThumbnail(activeItem);
        });
        addMenuRow(menu, "{user}/{char} 수정", "유저 이름과 캐릭터/작품 이름을 바꿔요.", () -> {
            dialog.dismiss();
            showSpeakerDialog();
        });
        addMenuRow(menu, activeItem.archived ? "보관함에서 꺼내기" : "보관함 이동", "자주 읽는 채팅방을 보관함에 따로 모아요.", () -> {
            dialog.dismiss();
            toggleArchiveForActive();
        });
        addMenuRow(menu, lightTheme ? "다크모드로 변경" : "라이트모드로 변경", "읽기 화면 색상을 바꿔요.", () -> {
            dialog.dismiss();
            toggleThemeForActive();
        });
        addMenuRow(menu, "채팅방 삭제", "이 앱의 채팅방 목록에서 삭제해요.", () -> {
            dialog.dismiss();
            confirmDeleteActiveItem();
        });

        dialog.show();
    }

    private void addMenuRow(LinearLayout parent, String title, String sub, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        row.setBackground(rounded(PANEL, dp(10), Color.rgb(58, 52, 56), 1));
        row.setOnClickListener(v -> action.run());

        TextView titleView = text(title, 15, TEXT, Typeface.BOLD);
        row.addView(titleView);
        TextView subView = text(sub, 12, MUTED, Typeface.NORMAL);
        subView.setPadding(0, dp(3), 0, 0);
        row.addView(subView);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        parent.addView(row, lp);
    }

    private void showSpeakerDialog() {
        if (activeItem == null) return;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);

        EditText user = dialogInput("{user} 이름");
        user.setText(userName);
        form.addView(user);

        EditText ai = dialogInput("{char} 이름");
        ai.setText(aiName);
        LinearLayout.LayoutParams aiLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        aiLp.setMargins(0, dp(8), 0, 0);
        form.addView(ai, aiLp);

        new AlertDialog.Builder(this)
                .setTitle("{user}/{char} 수정")
                .setView(form)
                .setPositiveButton("저장", (dialog, which) -> {
                    userName = valueOr(user.getText().toString(), "유저");
                    aiName = valueOr(ai.getText().toString(), "AI");
                    activeItem.userName = userName;
                    activeItem.aiName = aiName;
                    saveLibrary();
                    renderPage();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void toggleArchiveForActive() {
        if (activeItem == null) return;
        activeItem.archived = !activeItem.archived;
        saveLibrary();
        Toast.makeText(this, activeItem.archived ? "보관함으로 이동했어요." : "보관함에서 꺼냈어요.", Toast.LENGTH_SHORT).show();
    }

    private void toggleThemeForActive() {
        if (activeItem == null) return;
        lightTheme = !lightTheme;
        activeItem.lightTheme = lightTheme;
        saveLibrary();
        showReader();
    }

    private void confirmDeleteActiveItem() {
        if (activeItem == null) return;
        LibraryItem target = activeItem;
        new AlertDialog.Builder(this)
                .setTitle("채팅방 삭제")
                .setMessage(valueOr(target.displayTitle, target.title) + "\n목록에서 삭제하고 서재로 돌아갈까요?")
                .setPositiveButton("삭제", (dialog, which) -> {
                    library.remove(target);
                    activeItem = null;
                    saveLibrary();
                    showHome();
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

    private Bitmap loadThumbnailBitmap(LibraryItem item) {
        if (item == null || item.thumbnailPath == null || item.thumbnailPath.trim().isEmpty()) return null;
        try {
            File file = new File(item.thumbnailPath);
            if (!file.exists()) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 2;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        } catch (Exception e) {
            return null;
        }
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

    private int themed(int color) {
        if (!lightTheme) return color;
        if (color == BG || color == Color.rgb(15, 15, 16) || color == Color.rgb(17, 17, 17) || color == Color.rgb(19, 19, 20)) {
            return Color.rgb(250, 247, 248);
        }
        if (color == PANEL || color == Color.rgb(27, 27, 29) || color == Color.rgb(30, 30, 30) || color == Color.rgb(31, 31, 31)) {
            return Color.rgb(255, 252, 253);
        }
        if (color == PANEL_2 || color == Color.rgb(36, 34, 36) || color == Color.rgb(42, 42, 46) || color == Color.rgb(43, 41, 45)) {
            return Color.rgb(242, 235, 238);
        }
        if (color == CARD || color == Color.rgb(38, 37, 35) || color == Color.rgb(39, 38, 36)) {
            return Color.rgb(244, 239, 236);
        }
        if (color == CARD_HEAD || color == Color.rgb(78, 76, 71)) {
            return Color.rgb(223, 215, 211);
        }
        if (color == LINE || color == Color.rgb(56, 54, 55) || color == Color.rgb(48, 48, 48)) {
            return Color.rgb(220, 211, 215);
        }
        if (color == TEXT || color == Color.WHITE || color == Color.rgb(232, 232, 232) || color == Color.rgb(230, 230, 230) || color == Color.rgb(238, 238, 238) || color == Color.rgb(235, 235, 235)) {
            return Color.rgb(38, 34, 36);
        }
        if (color == MUTED || color == Color.rgb(166, 161, 164) || color == Color.rgb(161, 161, 161) || color == Color.rgb(190, 190, 190)) {
            return Color.rgb(116, 102, 108);
        }
        if (color == Color.rgb(255, 211, 216)) {
            return Color.rgb(128, 42, 61);
        }
        if (color == SOFT || color == Color.rgb(210, 176, 182)) {
            return Color.rgb(139, 61, 76);
        }
        if (color == EMPHASIS || color == Color.rgb(154, 154, 154)) {
            return Color.rgb(112, 108, 110);
        }
        if (color == RED_DARK) {
            return Color.rgb(255, 232, 236);
        }
        return color;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp * textScale);
        tv.setTextColor(themed(color));
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
        b.setTextColor(themed(Color.WHITE));
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

    private Button smallCircleButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(themed(label.equals("✓") ? RED : MUTED));
        b.setTextSize(18);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, dp(2));
        b.setBackground(rounded(PANEL_2, dp(19), label.equals("✓") ? RED : Color.rgb(70, 64, 68), 1));
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
        g.setColor(themed(color));
        g.setCornerRadius(radius);
        if (strokeWidth > 0) g.setStroke(strokeWidth, themed(strokeColor));
        return g;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int contentMaxWidth() {
        return Math.min(getResources().getDisplayMetrics().widthPixels, dp(720));
    }

    private LinearLayout.LayoutParams centeredContentParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(contentMaxWidth(), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        return lp;
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

    private String bookmarkKey(int page, int blockIndex, int paragraphIndex, String text) {
        return page + ":" + blockIndex + ":" + paragraphIndex + ":" + previewText(text);
    }

    private String previewText(String text) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (normalized.length() > 15) return normalized.substring(0, 15);
        return normalized;
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

    static class ParagraphAnchor {
        final String key;
        final int page;
        final TextView view;
        final String preview;

        ParagraphAnchor(String key, int page, TextView view, String preview) {
            this.key = key;
            this.page = page;
            this.view = view;
            this.preview = preview;
        }
    }

    static class BookmarkItem {
        int page = 0;
        String key = "";
        String preview = "";
        long createdAt = System.currentTimeMillis();

        JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("page", page);
                obj.put("key", key);
                obj.put("preview", preview);
                obj.put("createdAt", createdAt);
            } catch (Exception ignored) {
            }
            return obj;
        }

        static BookmarkItem fromJson(JSONObject obj) {
            BookmarkItem item = new BookmarkItem();
            item.page = obj.optInt("page", 0);
            item.key = obj.optString("key", "");
            item.preview = obj.optString("preview", "");
            item.createdAt = obj.optLong("createdAt", System.currentTimeMillis());
            return item;
        }
    }

    static class LibraryItem {
        String id = "";
        String uri = "";
        String title = "크랙 채팅";
        String displayTitle = "";
        String userName = "유저";
        String aiName = "AI";
        String thumbnailPath = "";
        long addedAt = System.currentTimeMillis();
        int lastPage = 0;
        boolean archived = false;
        boolean lightTheme = false;
        final ArrayList<BookmarkItem> bookmarks = new ArrayList<>();

        JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("id", id);
                obj.put("uri", uri);
                obj.put("title", title);
                obj.put("displayTitle", displayTitle);
                obj.put("userName", userName);
                obj.put("aiName", aiName);
                obj.put("thumbnailPath", thumbnailPath);
                obj.put("addedAt", addedAt);
                obj.put("lastPage", lastPage);
                obj.put("archived", archived);
                obj.put("lightTheme", lightTheme);
                JSONArray bms = new JSONArray();
                for (BookmarkItem bookmark : bookmarks) bms.put(bookmark.toJson());
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
            item.thumbnailPath = obj.optString("thumbnailPath", "");
            item.addedAt = obj.optLong("addedAt", System.currentTimeMillis());
            item.lastPage = obj.optInt("lastPage", 0);
            item.archived = obj.optBoolean("archived", false);
            item.lightTheme = obj.optBoolean("lightTheme", false);
            JSONArray bms = obj.optJSONArray("bookmarks");
            if (bms != null) {
                for (int i = 0; i < bms.length(); i++) {
                    JSONObject bookmarkObj = bms.optJSONObject(i);
                    if (bookmarkObj != null) {
                        item.bookmarks.add(BookmarkItem.fromJson(bookmarkObj));
                    } else {
                        BookmarkItem old = new BookmarkItem();
                        old.page = bms.optInt(i, 0);
                        old.key = old.page + ":0:0:";
                        old.preview = "이전 북마크";
                        item.bookmarks.add(old);
                    }
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
