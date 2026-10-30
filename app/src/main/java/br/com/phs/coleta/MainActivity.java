package br.com.phs.coleta;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.camera.view.PreviewView;
import androidx.documentfile.provider.DocumentFile;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends ComponentActivity {
  private static final int NAVY = Color.rgb(9, 36, 59),
      LIME = Color.rgb(180, 189, 0),
      MUTED = Color.rgb(91, 110, 124),
      LINE = Color.rgb(219, 227, 233),
      SOFT = Color.rgb(243, 246, 248);
  private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private Store store;
  private SharedPreferences prefs;
  private ScanController scanner;
  private PreviewView preview;
  private TextView who,
      variety,
      meta,
      feidLabel,
      total,
      mean,
      footer,
      notice,
      dateButton,
      observationSummary;
  private final TextView[] scoreViews = new TextView[5];
  private final String[] values = {"", "", "", "", ""};
  private final String[] categories = {"R", "MR", "MS", "S", "AS"};
  private int active = 0;
  private Domain.Sample current;
  private String observation = "", captureDay = "", editingDay = "";
  private boolean dirty = false, busy = false;
  private LinearLayout content;
  private OneDriveExportFlow oneDriveFlow;
  private ActivityResultLauncher<String[]> inputPicker;
  private ActivityResultLauncher<Uri> folderPicker;
  private ActivityResultLauncher<String> permission;
  private ActivityResultLauncher<String> exportPicker;
  private byte[] pendingExport;
  private String pendingDay, pendingFile;
  private long lastMissingAt = 0;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable clockTick =
      new Runnable() {
        public void run() {
          refresh();
          handler.postDelayed(this, 30_000);
        }
      };

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    store = new Store(this);
    prefs = getSharedPreferences("settings", MODE_PRIVATE);
    DailyDates.resetIfNewDay(prefs, day());
    if (!prefs.contains("deviceId"))
      prefs
          .edit()
          .putString("deviceId", UUID.randomUUID().toString())
          .putString("deviceName", Build.MODEL)
          .apply();
    inputPicker =
        registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
              if (uri != null) {
                persist(uri);
                importInput(uri);
              }
            });
    folderPicker =
        registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(),
            uri -> {
              if (uri != null) {
                persist(uri);
                if (pendingExport != null) exportToFolder(uri, pendingFile.endsWith(".csv"));
              }
            });
    permission =
        registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
              if (granted) startCamera();
              else message("Permissão de câmera negada. Use a lupa para buscar parcela.");
            });
    exportPicker =
        registerForActivityResult(
            new ActivityResultContracts.CreateDocument(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            uri -> {
              if (uri != null && pendingExport != null) writeExport(uri);
              else pendingExport = null;
            });
    buildScreen();
    if (state != null) restoreDraft(state);
    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
      startCamera();
    else permission.launch(Manifest.permission.CAMERA);
    clockTick.run();
    if (store.catalogCount() == 0) message("Abra as configurações para importar ENTRADA_PHS.xlsx.");
  }

  private int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private TextView text(String value, int size, int color) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setGravity(Gravity.CENTER_VERTICAL);
    t.setFontFeatureSettings("tnum");
    return t;
  }

  private android.graphics.drawable.Drawable background(int color, int stroke) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(9));
    if (stroke != 0) d.setStroke(dp(1), stroke);
    return d;
  }

  private Button button(String label, Runnable click) {
    Button b = new Button(this);
    b.setText(label);
    b.setTextSize(14);
    b.setAllCaps(false);
    b.setTextColor(NAVY);
    b.setPadding(dp(7), 0, dp(7), 0);
    b.setMinHeight(0);
    b.setMinimumHeight(0);
    b.setStateListAnimator(null);
    b.setBackground(background(Color.WHITE, LINE));
    b.setOnClickListener(v -> click.run());
    return b;
  }

  private Button primary(String label, Runnable click) {
    Button b = button(label, click);
    b.setBackground(background(NAVY, 0));
    b.setTextColor(Color.WHITE);
    return b;
  }

  private LinearLayout column() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  private LinearLayout row() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.HORIZONTAL);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  private void add(LinearLayout layout, View view, int height) {
    layout.addView(view, new LinearLayout.LayoutParams(-1, dp(height)));
  }

  private void weighted(LinearLayout layout, View view, int height) {
    LinearLayout.LayoutParams p =
        new LinearLayout.LayoutParams(0, height < 0 ? height : dp(height), 1);
    p.setMargins(dp(3), dp(2), dp(3), dp(2));
    layout.addView(view, p);
  }

  private void buildScreen() {
    FitLayout fit = new FitLayout(this);
    fit.setBackgroundColor(SOFT);
    LinearLayout canvas = column();
    canvas.setBackgroundColor(Color.WHITE);
    fit.addView(canvas);
    setContentView(fit);
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
        fit,
        (v, insets) -> {
          androidx.core.graphics.Insets bars =
              insets.getInsets(
                  androidx.core.view.WindowInsetsCompat.Type.systemBars()
                      | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
          v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return insets;
        });
    LinearLayout bar = row();
    bar.setPadding(dp(14), 0, dp(8), 0);
    bar.setBackgroundColor(NAVY);
    TextView title = text("PHS Reader", 21, Color.WHITE);
    title.setTypeface(null, Typeface.BOLD);
    bar.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
    bar.addView(
        icon("user", "Usuários", this::usersDialog, Color.WHITE, NAVY),
        new LinearLayout.LayoutParams(dp(46), dp(46)));
    bar.addView(
        icon("settings", "Configurações", this::settingsDialog, Color.WHITE, NAVY),
        new LinearLayout.LayoutParams(dp(46), dp(46)));
    bar.addView(
        icon("export", "Exportar lote", this::exportDialog, Color.WHITE, NAVY),
        new LinearLayout.LayoutParams(dp(46), dp(46)));
    add(canvas, bar, 50);
    content = column();
    content.setPadding(dp(13), dp(7), dp(13), dp(5));
    canvas.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout collector = row();
    who = text("", 12, MUTED);
    who.setSingleLine(true);
    who.setEllipsize(android.text.TextUtils.TruncateAt.END);
    collector.addView(who, new LinearLayout.LayoutParams(0, -1, 1));
    TextView today = text("", 12, MUTED);
    today.setTag("today");
    collector.addView(today);
    add(content, collector, 24);
    LinearLayout capture = row();
    capture.setGravity(Gravity.CENTER);
    FrameLayout camera = new FrameLayout(this);
    preview = new PreviewView(this);
    preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
    preview.setScaleType(PreviewView.ScaleType.FILL_CENTER);
    camera.addView(preview, new FrameLayout.LayoutParams(-1, -1));
    camera.addView(new FramingView(this), new FrameLayout.LayoutParams(-1, -1));
    feidLabel = text("Aponte para o QR Code", 11, Color.WHITE);
    feidLabel.setGravity(Gravity.CENTER);
    feidLabel.setBackgroundColor(0xdd09243b);
    FrameLayout.LayoutParams codeParams = new FrameLayout.LayoutParams(-1, dp(25), Gravity.BOTTOM);
    camera.addView(feidLabel, codeParams);
    capture.addView(camera, new LinearLayout.LayoutParams(dp(164), dp(164)));
    LinearLayout cameraTools = column();
    cameraTools.setPadding(dp(12), 0, 0, 0);
    dateButton = smallTool("calendar", "Datas", this::datesDialog);
    cameraTools.addView(dateButton, new LinearLayout.LayoutParams(dp(76), dp(52)));
    cameraTools.addView(
        smallTool("search", "Parcela", this::searchDialog),
        new LinearLayout.LayoutParams(dp(76), dp(52)));
    cameraTools.addView(
        smallTool("scan", "Reler", this::nextScan), new LinearLayout.LayoutParams(dp(76), dp(52)));
    capture.addView(cameraTools);
    add(content, capture, 174);
    LinearLayout id = column();
    variety = text("Nenhum genótipo selecionado", 17, NAVY);
    variety.setTypeface(null, Typeface.BOLD);
    variety.setMaxLines(1);
    variety.setEllipsize(android.text.TextUtils.TruncateAt.END);
    add(id, variety, 29);
    meta = text("Importe a entrada para iniciar", 11, MUTED);
    meta.setMaxLines(2);
    add(id, meta, 29);
    add(content, id, 61);
    TextView label = text("Número de espigas", 13, NAVY);
    add(content, label, 23);
    LinearLayout scores = row();
    for (int i = 0; i < 5; i++) {
      final int index = i;
      LinearLayout group = column();
      TextView cat = text(categories[i], 12, MUTED);
      cat.setGravity(Gravity.CENTER);
      add(group, cat, 20);
      scoreViews[i] = text("", 23, NAVY);
      scoreViews[i].setGravity(Gravity.CENTER);
      scoreViews[i].setContentDescription("Contagem " + categories[i]);
      scoreViews[i].setFocusable(true);
      scoreViews[i].setOnClickListener(
          v -> {
            active = index;
            refreshScores();
          });
      add(group, scoreViews[i], 46);
      weighted(scores, group, 70);
    }
    add(content, scores, 73);
    LinearLayout sums = row();
    total = text("Total: 0", 12, MUTED);
    mean = text("Média: —", 12, MUTED);
    sums.addView(total, new LinearLayout.LayoutParams(0, -1, 1));
    sums.addView(mean);
    add(content, sums, 27);
    LinearLayout obs = row();
    obs.setPadding(dp(5), 0, dp(5), 0);
    obs.setBackground(background(SOFT, 0));
    obs.setOnClickListener(v -> observationsDialog());
    obs.setFocusable(true);
    TextView obsLabel = text("Observações", 13, NAVY);
    obs.addView(obsLabel, new LinearLayout.LayoutParams(0, -1, 1));
    observationSummary = text("Adicionar  ›", 12, MUTED);
    observationSummary.setMaxLines(1);
    observationSummary.setEllipsize(android.text.TextUtils.TruncateAt.END);
    obs.addView(observationSummary, new LinearLayout.LayoutParams(dp(170), -1));
    add(content, obs, 39);
    LinearLayout keypad = column();
    keypad.setPadding(0, dp(6), 0, dp(3));
    for (String[] keys :
        new String[][] {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"⌫", "0", "→"}}) {
      LinearLayout keysRow = row();
      for (String k : keys) {
        Button key = button(k, () -> key(k));
        key.setTextSize(22);
        if (k.equals("→")) {
          key.setBackground(background(NAVY, 0));
          key.setTextColor(Color.WHITE);
          key.setContentDescription("Próxima categoria");
        } else if (k.equals("⌫")) key.setContentDescription("Apagar dígito");
        weighted(keysRow, key, -1);
      }
      keypad.addView(keysRow, new LinearLayout.LayoutParams(-1, 0, 1));
    }
    content.addView(keypad, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout actions = row();
    weighted(
        actions,
        button(
            "Limpar",
            () -> {
              if (!dirty) {
                clearDraft();
                return;
              }
              confirm(
                  "Limpar campos?",
                  "Descartar as anotações que ainda não foram salvas?",
                  "Limpar",
                  this::clearDraft);
            }),
        46);
    Button save = button("✓  Salvar registro", this::save);
    save.setBackground(background(LIME, 0));
    LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, dp(46), 1.8f);
    saveParams.setMargins(dp(3), dp(2), dp(3), dp(2));
    actions.addView(save, saveParams);
    add(content, actions, 51);
    notice = text("", 11, MUTED);
    notice.setMaxLines(1);
    notice.setEllipsize(android.text.TextUtils.TruncateAt.END);
    add(content, notice, 20);
    footer = text("", 12, Color.WHITE);
    footer.setSingleLine(true);
    footer.setEllipsize(android.text.TextUtils.TruncateAt.END);
    footer.setPadding(dp(14), 0, dp(14), 0);
    footer.setBackgroundColor(NAVY);
    add(canvas, footer, 29);
    refresh();
  }

  private View icon(String type, String label, Runnable action, int color, int fill) {
    android.widget.ImageButton v = new android.widget.ImageButton(this);
    v.setImageDrawable(new Glyph(type, color));
    v.setContentDescription(label);
    v.setBackground(background(fill, 0));
    v.setPadding(dp(11), dp(11), dp(11), dp(11));
    v.setOnClickListener(w -> action.run());
    return v;
  }

  private TextView smallTool(String icon, String label, Runnable action) {
    TextView view = text(label, 11, NAVY);
    view.setGravity(Gravity.CENTER);
    view.setCompoundDrawablesWithIntrinsicBounds(null, new Glyph(icon, NAVY), null, null);
    view.setCompoundDrawablePadding(dp(2));
    view.setBackground(background(Color.WHITE, 0));
    view.setContentDescription(label);
    view.setFocusable(true);
    view.setOnClickListener(v -> action.run());
    return view;
  }

  private void refresh() {
    if (footer == null) return;
    DailyDates.resetIfNewDay(prefs, day());
    who.setText(
        prefs.getString("user", "").trim().isEmpty()
            ? "Selecionar usuário"
            : prefs.getString("user", ""));
    TextView today = content.findViewWithTag("today");
    today.setText(LocalDate.now().format(DISPLAY));
    footer.setText(
        "Salvos hoje: "
            + store.count(day())
            + "     ·     "
            + prefs.getString("deviceName", Build.MODEL));
    dateButton.setText(
        prefs.getString("harvest", "").trim().isEmpty()
                && prefs.getString("assembly", "").trim().isEmpty()
            ? "Datas"
            : "Datas ✓");
    refreshScores();
    if (current != null) {
      variety.setText(current.variety());
      meta.setText(
          "Parcela "
              + current.parcel()
              + "    Repetição "
              + current.repetition()
              + "\n"
              + current.trial()
              + " · "
              + current.location());
      feidLabel.setText("FEID " + current.feid() + " · identificado");
    }
    observationSummary.setText(observation.trim().isEmpty() ? "Adicionar  ›" : observation);
  }

  private void refreshScores() {
    int[] counts = counts();
    for (int i = 0; i < 5; i++) {
      scoreViews[i].setText(values[i]);
      scoreViews[i].setBackground(
          background(
              i == active ? Color.rgb(249, 250, 225) : Color.WHITE, i == active ? LIME : LINE));
    }
    total.setText("Total: " + Domain.total(counts));
    Double value = Domain.mean(counts);
    mean.setText(
        value == null
            ? "Média: —"
            : String.format(Locale.forLanguageTag("pt-BR"), "Média: %.2f", value));
  }

  private int[] counts() {
    int[] counts = new int[5];
    for (int i = 0; i < 5; i++) counts[i] = Domain.count(values[i]);
    return counts;
  }

  private String day() {
    return LocalDate.now().toString();
  }

  private void key(String key) {
    if (current == null) {
      message("Leia um QR Code ou busque uma parcela.");
      return;
    }
    if (key.equals("→")) active = (active + 1) % 5;
    else if (key.equals("⌫")) {
      if (values[active].isEmpty() && active > 0) active--;
      if (!values[active].isEmpty()) {
        values[active] = values[active].substring(0, values[active].length() - 1);
        dirty = true;
      }
    } else {
      EntryRules.Result result = EntryRules.digit(values[active], key);
      if (result.accepted()) {
        values[active] = result.value();
        dirty = true;
        if (result.advance()) active = Math.min(active + 1, 4);
      } else message("Use de 0 a 15 espigas. Para registrar apenas 1, toque na seta.");
    }
    refreshScores();
  }

  private void clearDraft() {
    Arrays.fill(values, "");
    observation = "";
    active = 0;
    dirty = false;
    refresh();
  }

  private void nextScan() {
    if (dirty) {
      confirm(
          "Nova leitura?",
          "Há anotações sem salvar. Deseja descartá-las?",
          "Descartar",
          this::manualScan);
      return;
    }
    manualScan();
  }

  private void manualScan() {
    if (scanner != null) scanner.allowRepeatedCode();
    armScan();
  }

  private void armScan() {
    clearDraft();
    current = null;
    captureDay = "";
    editingDay = "";
    variety.setText("Aponte para o QR Code");
    meta.setText("Ou use a lupa para buscar pelo número da parcela");
    feidLabel.setText("Centralize um QR Code na moldura");
    if (scanner != null) scanner.setEnabled(true);
  }

  private void select(Domain.Sample sample) {
    Runnable apply =
        () -> {
          Domain.Reading existing = store.existing(sample.feid(), day());
          if (existing != null) {
            confirm(
                "FEID já anotado hoje",
                sample.variety()
                    + " · parcela "
                    + sample.parcel()
                    + "\nEditar atualiza o registro sem aumentar o contador.",
                "Editar",
                () -> {
                  current = existing.sample();
                  clearDraft();
                  for (int i = 0; i < 5; i++) values[i] = Integer.toString(existing.counts()[i]);
                  observation = existing.observation();
                  captureDay = day();
                  editingDay = day();
                  refresh();
                },
                () -> {
                  if (scanner != null) scanner.setEnabled(current == null);
                });
          } else {
            current = sample;
            clearDraft();
            captureDay = day();
            editingDay = "";
            refresh();
          }
        };
    if (dirty)
      confirm(
          "Trocar genótipo?",
          "Descartar as anotações que ainda não foram salvas?",
          "Descartar",
          apply);
    else apply.run();
  }

  private void save() {
    if (busy) return;
    DailyDates.resetIfNewDay(prefs, day());
    if (current == null) {
      message("Selecione um genótipo antes de salvar.");
      return;
    }
    if (prefs.getString("user", "").trim().isEmpty()) {
      usersDialog();
      message("Escolha o usuário antes de salvar.");
      return;
    }
    if (!EntryRules.validNewCounts(counts())) {
      message("Revise as contagens: a nova faixa permitida é de 0 a 15 por categoria.");
      return;
    }
    if (!captureDay.equals(day())) {
      confirm(
          "O dia da coleta mudou",
          "Releia a amostra para conferir registros do novo dia. As anotações atuais serão"
              + " descartadas.",
          "Conferir novo dia",
          () -> {
            Domain.Sample sample = current;
            clearDraft();
            select(sample);
          });
      return;
    }
    Domain.Reading existing = store.existing(current.feid(), day());
    if (existing != null && !editingDay.equals(day())) {
      confirm(
          "Avaliação já existente",
          "Atualizar a avaliação deste FEID hoje sem criar duplicata?",
          "Atualizar",
          () -> {
            editingDay = day();
            save();
          });
      return;
    }
    Runnable write =
        () -> {
          try {
            DailyDates.resetIfNewDay(prefs, day());
            if (!captureDay.equals(day())) {
              save();
              return;
            }
            Domain.Reading reading =
                new Domain.Reading(
                    current,
                    day(),
                    LocalDateTime.now().withNano(0).toString(),
                    prefs.getString("harvest", ""),
                    prefs.getString("assembly", ""),
                    observation,
                    counts(),
                    prefs.getString("user", ""),
                    prefs.getString("deviceId", ""),
                    prefs.getString("deviceName", ""));
            store.save(reading);
            String savedFeid = current.feid();
            if (scanner != null) scanner.afterSave(savedFeid);
            armScan();
            message("Salvo · FEID " + savedFeid + ". Aponte para a próxima etiqueta.");
          } catch (Exception e) {
            error("Não foi possível salvar", e);
          }
        };
    if (Domain.total(counts()) == 0)
      confirm(
          "Salvar total zero?",
          "Campos vazios e zeros serão gravados como zero. A média ficará em branco.",
          "Salvar com zero",
          write);
    else write.run();
  }

  private void startCamera() {
    scanner =
        new ScanController(
            preview,
            this,
            new ScanController.Listener() {
              public void onCode(String value) {
                runOnUiThread(
                    () -> {
                      Domain.Sample s = store.find(Domain.cleanId(value));
                      if (s == null) {
                        long now = System.currentTimeMillis();
                        if (now - lastMissingAt > 3000) {
                          message("FEID não encontrado na entrada: " + value);
                          lastMissingAt = now;
                        }
                        handler.postDelayed(
                            () -> {
                              if (scanner != null && current == null) scanner.setEnabled(true);
                            },
                            1800);
                      } else select(s);
                    });
              }

              public void onError(String value) {
                message(value);
              }
            });
    scanner.start();
    scanner.setEnabled(current == null);
  }

  private void message(String text) {
    if (notice != null) notice.setText(text);
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
  }

  private void error(String title, Exception e) {
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(e.getMessage() == null ? "Operação não concluída." : e.getMessage())
        .setPositiveButton("Fechar", null)
        .show();
  }

  private void confirm(String title, String message, String action, Runnable yes) {
    confirm(title, message, action, yes, () -> {});
  }

  private void confirm(String title, String message, String action, Runnable yes, Runnable no) {
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(action, (d, w) -> yes.run())
        .setNegativeButton("Cancelar", (d, w) -> no.run())
        .setOnCancelListener(d -> no.run())
        .show();
  }

  private LinearLayout dialogBody() {
    LinearLayout l = column();
    l.setPadding(dp(22), dp(10), dp(22), dp(12));
    return l;
  }

  private void dialogAdd(LinearLayout body, String label, View view) {
    TextView t = text(label, 12, MUTED);
    add(body, t, 23);
    add(body, view, 48);
  }

  private EditText edit(String text, boolean numeric) {
    EditText e = new EditText(this);
    e.setText(text);
    e.setTextSize(16);
    e.setSingleLine(true);
    e.setInputType(
        numeric
            ? InputType.TYPE_CLASS_NUMBER
            : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    return e;
  }

  private void showBody(String title, LinearLayout body) {
    ScrollView scroll = new ScrollView(this);
    scroll.addView(body);
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setView(scroll)
        .setNegativeButton("Fechar", null)
        .show();
  }

  private void usersDialog() {
    LinearLayout body = dialogBody();
    List<String> users = store.users();
    Spinner select = new Spinner(this);
    select.setAdapter(
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, users));
    int selected = users.indexOf(prefs.getString("user", ""));
    if (selected >= 0) select.setSelection(selected);
    dialogAdd(body, "Coletor", select);
    EditText name = edit("", false);
    name.setHint("Novo nome ou nome corrigido");
    dialogAdd(body, "Nome", name);
    Button addUser =
        button(
            "Cadastrar nome",
            () -> {
              String n = name.getText().toString().trim();
              if (n.isEmpty()) {
                name.setError("Informe o nome");
                return;
              }
              if (!store.addUser(n)) {
                name.setError("Nome já cadastrado");
                return;
              }
              prefs.edit().putString("user", n).apply();
              refresh();
              message("Usuário cadastrado. Reabra para selecionar outro.");
              users.clear();
              users.addAll(store.users());
              ((ArrayAdapter<?>) select.getAdapter()).notifyDataSetChanged();
              select.setSelection(users.indexOf(n));
              name.setText("");
            });
    add(body, addUser, 46);
    add(
        body,
        button(
            "Renomear selecionado",
            () -> {
              if (select.getSelectedItem() == null) return;
              String old = select.getSelectedItem().toString(),
                  n = name.getText().toString().trim();
              if (n.trim().isEmpty()) {
                name.setError("Informe o novo nome");
                return;
              }
              if (!store.renameUser(old, n)) {
                name.setError("Nome já cadastrado");
                return;
              }
              if (prefs.getString("user", "").equals(old))
                prefs.edit().putString("user", n).apply();
              users.clear();
              users.addAll(store.users());
              ((ArrayAdapter<?>) select.getAdapter()).notifyDataSetChanged();
              select.setSelection(users.indexOf(n));
              name.setText("");
              refresh();
            }),
        46);
    add(
        body,
        button(
            "Excluir selecionado",
            () -> {
              if (select.getSelectedItem() == null) return;
              String n = select.getSelectedItem().toString();
              confirm(
                  "Excluir usuário?",
                  "Remover "
                      + n
                      + " da lista? As avaliações salvas mantêm o nome registrado na coleta.",
                  "Excluir",
                  () -> {
                    store.deleteUser(n);
                    if (prefs.getString("user", "").equals(n)) prefs.edit().remove("user").apply();
                    users.clear();
                    users.addAll(store.users());
                    ((ArrayAdapter<?>) select.getAdapter()).notifyDataSetChanged();
                    refresh();
                  });
            }),
        46);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(body);
    new AlertDialog.Builder(this)
        .setTitle("Usuários")
        .setView(scroll)
        .setPositiveButton(
            "Usar coletor",
            (d, w) -> {
              if (select.getSelectedItem() != null) {
                prefs.edit().putString("user", select.getSelectedItem().toString()).apply();
                refresh();
              }
            })
        .setNegativeButton("Fechar", null)
        .show();
  }

  private void settingsDialog() {
    LinearLayout body = dialogBody();
    TextView input = text(prefs.getString("inputName", "Nenhum arquivo selecionado"), 13, NAVY);
    add(body, input, 28);
    add(
        body,
        button(
            "Escolher arquivo de entrada",
            () ->
                inputPicker.launch(
                    new String[] {
                      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                      "text/csv",
                      "text/comma-separated-values",
                      "application/csv"
                    })),
        46);
    add(
        body,
        button(
            "Atualizar arquivo de entrada",
            () -> {
              String uri = prefs.getString("input", "");
              if (uri.isEmpty()) {
                message("Escolha primeiro o arquivo de entrada.");
                return;
              }
              importInput(Uri.parse(uri));
            }),
        46);
    add(body, text("Catálogo local: " + store.catalogCount() + " genótipos", 12, MUTED), 26);
    EditText device = edit(prefs.getString("deviceName", Build.MODEL), false);
    dialogAdd(body, "Nome do dispositivo", device);
    add(
        body,
        button(
            "Limpar dados de coleta",
            () ->
                confirm(
                    "Limpar dados de coleta?",
                    "Apagar as avaliações de TODOS os dias deste aparelho?\n\n"
                        + "Usuários, nome do dispositivo, catálogo, configurações e arquivos"
                        + " exportados serão mantidos.",
                    "Apagar coletas",
                    () -> {
                      store.clearReadings();
                      clearDraft();
                      message("Coletas removidas. Cadastros preservados.");
                    })),
        46);
    add(body, text("PHS Reader 0.3.0 · coleta offline", 12, MUTED), 28);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(body);
    new AlertDialog.Builder(this)
        .setTitle("Configurações")
        .setView(scroll)
        .setPositiveButton(
            "Salvar",
            (d, w) -> {
              String name = device.getText().toString().trim();
              prefs
                  .edit()
                  .putString("deviceName", name.trim().isEmpty() ? Build.MODEL : name)
                  .apply();
              refresh();
            })
        .setNegativeButton("Fechar", null)
        .show();
  }

  private void sendToOneDrive(boolean csv) {
    if (pendingExport == null) return;
    if (getPackageManager().getLaunchIntentForPackage("com.microsoft.skydrive") == null) {
      new AlertDialog.Builder(this)
          .setTitle("OneDrive não instalado")
          .setMessage(
              "Instale e entre no OneDrive para escolher a pasta pelo aplicativo. Também é possível"
                  + " usar Salvar como.")
          .setPositiveButton("Salvar como", (d, w) -> saveAs(csv))
          .setNegativeButton("Cancelar", null)
          .show();
      return;
    }
    busy = true;
    byte[] bytes = pendingExport;
    String name = pendingFile;
    worker.execute(
        () -> {
          try {
            File directory = new File(getCacheDir(), "exports/" + UUID.randomUUID());
            if (!directory.isDirectory() && !directory.mkdirs())
              throw new IOException("Não foi possível preparar o arquivo de exportação.");
            File file = new File(directory, name);
            try (OutputStream out = new FileOutputStream(file)) {
              out.write(bytes);
            }
            runOnUiThread(
                () -> {
                  busy = false;
                  try {
                    Intent intent = ExportIntents.oneDrive(this, file, csv);
                    startActivity(intent);
                    pendingExport = null;
                    message(
                        "Escolha a pasta e confirme o envio no OneDrive. As coletas permanecem no"
                            + " aparelho.");
                  } catch (Exception e) {
                    new AlertDialog.Builder(this)
                        .setTitle("Não foi possível abrir o OneDrive")
                        .setMessage(
                            "Use Salvar como para escolher outro destino. As coletas estão"
                                + " preservadas.")
                        .setPositiveButton("Salvar como", (d, w) -> saveAs(csv))
                        .setNegativeButton("Cancelar", null)
                        .show();
                  }
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  busy = false;
                  error("Falha ao preparar envio ao OneDrive", e);
                });
          }
        });
  }

  private void datesDialog() {
    DailyDates.resetIfNewDay(prefs, day());
    String openedDay = day();
    LinearLayout body = dialogBody();
    TextView h = dateField(body, "Colheita", "harvest"),
        a = dateField(body, "Montagem", "assembly");
    TextView help =
        text(
            "Datas opcionais, mantidas para as próximas amostras até serem alteradas. Leitura = dia"
                + " em que o registro for salvo.",
            12,
            MUTED);
    help.setMaxLines(4);
    add(body, help, 62);
    new AlertDialog.Builder(this)
        .setTitle("Datas da coleta")
        .setView(body)
        .setPositiveButton(
            "Aplicar",
            (d, w) -> {
              if (!openedDay.equals(day())) {
                DailyDates.resetIfNewDay(prefs, day());
                refresh();
                message("O dia mudou. Informe novamente as datas para o novo dia.");
                return;
              }
              prefs
                  .edit()
                  .putString("datesDay", day())
                  .putString("harvest", h.getTag().toString())
                  .putString("assembly", a.getTag().toString())
                  .apply();
              refresh();
            })
        .setNegativeButton("Cancelar", null)
        .show();
  }

  private TextView dateField(LinearLayout body, String label, String pref) {
    TextView field = text("", 15, NAVY);
    String initial = prefs.getString(pref, "");
    field.setTag(initial);
    field.setText(
        initial.trim().isEmpty() ? "Não informada" : LocalDate.parse(initial).format(DISPLAY));
    field.setBackground(background(SOFT, 0));
    field.setPadding(dp(10), 0, 0, 0);
    field.setOnClickListener(
        v -> {
          LocalDate date =
              field.getTag().toString().trim().isEmpty()
                  ? LocalDate.now()
                  : LocalDate.parse(field.getTag().toString());
          new DatePickerDialog(
                  this,
                  (picker, y, m, d) -> {
                    String value = LocalDate.of(y, m + 1, d).toString();
                    field.setTag(value);
                    field.setText(LocalDate.parse(value).format(DISPLAY));
                  },
                  date.getYear(),
                  date.getMonthValue() - 1,
                  date.getDayOfMonth())
              .show();
        });
    dialogAdd(body, label, field);
    add(
        body,
        button(
            "Limpar " + label.toLowerCase(Locale.ROOT),
            () -> {
              field.setTag("");
              field.setText("Não informada");
            }),
        38);
    return field;
  }

  private void observationsDialog() {
    LinearLayout body = dialogBody();
    LinearLayout suggestions = row();
    EditText free = edit(observation, false);
    free.setSingleLine(false);
    free.setMinLines(3);
    for (String word : new String[] {"Germinado", "Doente", "Verde"}) {
      Button suggestion =
          button(
              word,
              () -> {
                List<String> words =
                    new ArrayList<>(Arrays.asList(free.getText().toString().split(";\\s*")));
                words.removeIf(String::isBlank);
                if (words.contains(word)) words.remove(word);
                else words.add(word);
                free.setText(String.join("; ", words));
              });
      suggestion.setTextSize(12);
      weighted(suggestions, suggestion, 44);
    }
    add(body, suggestions, 49);
    add(body, text("Texto livre", 12, MUTED), 25);
    add(body, free, 100);
    new AlertDialog.Builder(this)
        .setTitle("Observações")
        .setView(body)
        .setPositiveButton(
            "Aplicar",
            (d, w) -> {
              String next = free.getText().toString().trim();
              dirty |= !next.equals(observation);
              observation = next;
              refresh();
            })
        .setNegativeButton("Cancelar", null)
        .show();
  }

  private void searchDialog() {
    LinearLayout body = dialogBody();
    EditText parcel = edit("", true);
    parcel.setHint("Número da parcela");
    dialogAdd(body, "Parcela", parcel);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Buscar parcela")
            .setView(body)
            .setPositiveButton("Buscar", null)
            .setNegativeButton("Cancelar", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    v -> {
                      List<Domain.Sample> results =
                          store.byParcel(Domain.cleanId(parcel.getText().toString()));
                      if (results.isEmpty()) {
                        parcel.setError("Parcela não encontrada na entrada");
                        return;
                      }
                      dialog.dismiss();
                      if (results.size() == 1) select(results.get(0));
                      else {
                        String[] labels =
                            results.stream()
                                .map(
                                    s ->
                                        s.variety()
                                            + " · "
                                            + s.trial()
                                            + " · "
                                            + s.location()
                                            + " · rep. "
                                            + s.repetition()
                                            + " · FEID "
                                            + s.feid())
                                .toArray(String[]::new);
                        new AlertDialog.Builder(this)
                            .setTitle("Escolha a amostra")
                            .setItems(labels, (d2, index) -> select(results.get(index)))
                            .setNegativeButton("Cancelar", null)
                            .show();
                      }
                    }));
    dialog.show();
  }

  private void persist(Uri uri) {
    try {
      getContentResolver()
          .takePersistableUriPermission(
              uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    } catch (SecurityException e) {
      try {
        getContentResolver()
            .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
      } catch (SecurityException ignored) {
      }
    }
  }

  private String documentName(Uri uri) {
    try (Cursor c =
        getContentResolver()
            .query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
      if (c != null && c.moveToFirst()) return c.getString(0);
    } catch (Exception ignored) {
    }
    return "entrada.xlsx";
  }

  private void importInput(Uri uri) {
    if (busy) {
      message("Aguarde a operação em andamento.");
      return;
    }
    busy = true;
    message("Importando e validando o catálogo…");
    worker.execute(
        () -> {
          File tmp = null;
          try {
            String name = documentName(uri);
            tmp = File.createTempFile("phs-input-", ".tmp", getCacheDir());
            try (InputStream in = getContentResolver().openInputStream(uri);
                OutputStream out = new FileOutputStream(tmp)) {
              if (in == null) throw new IOException("Arquivo indisponível.");
              byte[] bytes = new byte[16384];
              long size = 0;
              for (int n; (n = in.read(bytes)) != -1; ) {
                size += n;
                if (size > 30_000_000)
                  throw new IOException("Limite de 30 MB por arquivo de entrada.");
                out.write(bytes, 0, n);
              }
            }
            List<Domain.Sample> samples =
                SheetIO.read(tmp, name.toLowerCase(Locale.ROOT).endsWith(".csv"));
            store.replaceCatalog(samples);
            prefs.edit().putString("input", uri.toString()).putString("inputName", name).apply();
            runOnUiThread(
                () -> {
                  busy = false;
                  message(samples.size() + " genótipos importados. Avaliações preservadas.");
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  busy = false;
                  error("Entrada não importada; catálogo anterior preservado", e);
                });
          } finally {
            if (tmp != null) tmp.delete();
          }
        });
  }

  private void exportDialog() {
    LinkedHashMap<String, Integer> days = store.days();
    if (days.isEmpty()) {
      message("Nenhuma avaliação salva para exportar.");
      return;
    }
    LinearLayout body = dialogBody();
    List<String> keys = new ArrayList<>(days.keySet());
    Spinner date = new Spinner(this);
    List<String> labels = new ArrayList<>();
    for (String d : keys)
      labels.add(LocalDate.parse(d).format(DISPLAY) + " · " + days.get(d) + " registros");
    date.setAdapter(
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
    dialogAdd(body, "Lote · datas disponíveis", date);
    Spinner format = new Spinner(this);
    format.setAdapter(
        new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            new String[] {"Excel (.xlsx)", "CSV (.csv)"}));
    dialogAdd(body, "Formato", format);
    add(body, text("O destino será escolhido ao clicar em Exportar.", 12, MUTED), 34);
    TextView file = text("", 12, NAVY);
    file.setMaxLines(3);
    add(body, file, 52);
    Runnable update =
        () ->
            file.setText(
                Domain.fileName(
                    keys.get(date.getSelectedItemPosition()),
                    prefs.getString("deviceName", Build.MODEL),
                    format.getSelectedItemPosition() == 0 ? "xlsx" : "csv"));
    AdapterView.OnItemSelectedListener changes =
        new AdapterView.OnItemSelectedListener() {
          public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
            update.run();
          }

          public void onNothingSelected(AdapterView<?> p) {}
        };
    date.setOnItemSelectedListener(changes);
    format.setOnItemSelectedListener(changes);
    update.run();
    add(body, text("Exportar mantém as avaliações no aparelho.", 12, MUTED), 32);
    new AlertDialog.Builder(this)
        .setTitle("Exportar lote")
        .setView(body)
        .setPositiveButton(
            "Exportar",
            (d, w) ->
                prepareExport(
                    keys.get(date.getSelectedItemPosition()),
                    format.getSelectedItemPosition() != 0))
        .setNegativeButton("Cancelar", null)
        .show();
  }

  private void prepareExport(String day, boolean csv) {
    if (busy) return;
    busy = true;
    message("Preparando arquivo de saída…");
    worker.execute(
        () -> {
          try {
            List<Domain.Reading> readings = store.readings(day);
            if (readings.isEmpty()) throw new IOException("Este lote não tem registros.");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            SheetIO.write(bytes, readings, csv);
            pendingExport = bytes.toByteArray();
            pendingDay = day;
            pendingFile =
                Domain.fileName(
                    day, prefs.getString("deviceName", Build.MODEL), csv ? "csv" : "xlsx");
            runOnUiThread(
                () -> {
                  busy = false;
                  chooseExportDestination(csv);
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  busy = false;
                  error("Falha ao preparar exportação", e);
                });
          }
        });
  }

  private void chooseExportDestination(boolean csv) {
    if (pendingExport == null) return;
    String[] choices = {
      "OneDrive · conexão direta Microsoft",
      "OneDrive instalado · escolher pasta no aplicativo",
      "Pasta no aparelho",
      "Salvar como"
    };
    new AlertDialog.Builder(this)
        .setTitle("Onde deseja exportar?")
        .setItems(
            choices,
            (dialog, index) -> {
              if (index == 0) {
                if (oneDriveFlow != null) oneDriveFlow.close();
                oneDriveFlow =
                    new OneDriveExportFlow(
                        this, prefs, pendingExport, pendingFile, csv, this::message);
                oneDriveFlow.start();
              } else if (index == 1) sendToOneDrive(csv);
              else if (index == 2) {
                try {
                  folderPicker.launch(null);
                } catch (Exception e) {
                  error("Não foi possível escolher uma pasta", e);
                }
              } else saveAs(csv);
            })
        .setNegativeButton("Cancelar", null)
        .show();
  }

  private void exportToFolder(Uri uri, boolean csv) {
    try {
      DocumentFile folder = DocumentFile.fromTreeUri(this, uri);
      if (folder == null || !folder.canWrite())
        throw new IOException(
            "Não há permissão de gravação nesta pasta. Escolha outro destino ao exportar.");
      DocumentFile existing = folder.findFile(pendingFile);
      if (existing != null)
        confirm(
            "Arquivo já existe",
            "Substituir " + pendingFile + " nesta pasta?",
            "Substituir",
            () -> writeExport(existing.getUri()));
      else {
        DocumentFile file =
            folder.createFile(
                csv
                    ? "text/csv"
                    : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                pendingFile);
        if (file == null) throw new IOException("Não foi possível criar o arquivo nesta pasta.");
        writeExport(file.getUri());
      }
    } catch (Exception e) {
      error("Exportação não concluída; coletas preservadas", e);
    }
  }

  private void saveAs(boolean csv) {
    if (csv) {
      Intent intent =
          new Intent(Intent.ACTION_CREATE_DOCUMENT)
              .addCategory(Intent.CATEGORY_OPENABLE)
              .setType("text/csv")
              .putExtra(Intent.EXTRA_TITLE, pendingFile);
      csvSaveAs.launch(intent);
    } else exportPicker.launch(pendingFile);
  }

  private final androidx.activity.result.ActivityResultLauncher<Intent> csvSaveAs =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            if (result.getResultCode() == RESULT_OK
                && result.getData() != null
                && result.getData().getData() != null) writeExport(result.getData().getData());
            else pendingExport = null;
          });

  private void writeExport(Uri target) {
    if (pendingExport == null) return;
    byte[] bytes = pendingExport;
    busy = true;
    worker.execute(
        () -> {
          try {
            try (OutputStream out = getContentResolver().openOutputStream(target, "wt")) {
              if (out == null) throw new IOException("Destino indisponível.");
              out.write(bytes);
              out.flush();
            }
            runOnUiThread(
                () -> {
                  busy = false;
                  pendingExport = null;
                  message(
                      "Arquivo gravado no destino selecionado. Coletas preservadas no aparelho.");
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  busy = false;
                  error("Exportação não concluída; coletas preservadas", e);
                });
          }
        });
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    super.onSaveInstanceState(state);
    state.putStringArray("values", values);
    state.putString("observation", observation);
    state.putString("captureDay", captureDay);
    state.putString("editingDay", editingDay);
    state.putBoolean("dirty", dirty);
    state.putInt("active", active);
    if (current != null) state.putStringArray("sample", current.fields());
  }

  private void restoreDraft(Bundle state) {
    String[] counts = state.getStringArray("values"), s = state.getStringArray("sample");
    if (counts != null) System.arraycopy(counts, 0, values, 0, 5);
    observation = state.getString("observation", "");
    captureDay = state.getString("captureDay", "");
    editingDay = state.getString("editingDay", "");
    dirty = state.getBoolean("dirty");
    active = state.getInt("active");
    if (s != null) current = new Domain.Sample(s[0], s[1], s[2], s[3], s[4], s[5]);
    refresh();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (prefs != null && footer != null) refresh();
  }

  @Override
  public void onConfigurationChanged(Configuration c) {
    super.onConfigurationChanged(c);
  }

  @Override
  protected void onDestroy() {
    handler.removeCallbacks(clockTick);
    if (scanner != null) scanner.close();
    if (oneDriveFlow != null) oneDriveFlow.close();
    worker.execute(store::close);
    worker.shutdown();
    super.onDestroy();
  }

  private final class FramingView extends View {
    private final Paint paint = new Paint(3);

    FramingView(Context c) {
      super(c);
      setContentDescription("Área central de leitura do QR Code");
    }

    @Override
    protected void onDraw(Canvas canvas) {
      float side = Math.min(getWidth(), getHeight()) * .68f,
          x = (getWidth() - side) / 2,
          y = (getHeight() - side) / 2;
      paint.setColor(0x5509243b);
      paint.setStyle(Paint.Style.FILL);
      canvas.drawRect(0, 0, getWidth(), y, paint);
      canvas.drawRect(0, y + side, getWidth(), getHeight(), paint);
      canvas.drawRect(0, y, x, y + side, paint);
      canvas.drawRect(x + side, y, getWidth(), y + side, paint);
      paint.setColor(LIME);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(dp(2));
      canvas.drawRoundRect(x, y, x + side, y + side, dp(5), dp(5), paint);
    }
  }

  private final class Glyph extends android.graphics.drawable.Drawable {
    private final String name;
    private final Paint p = new Paint(3);

    Glyph(String name, int color) {
      this.name = name;
      p.setColor(color);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(1.8f);
      p.setStrokeCap(Paint.Cap.ROUND);
      p.setStrokeJoin(Paint.Join.ROUND);
      setBounds(0, 0, dp(22), dp(22));
    }

    @Override
    public int getIntrinsicWidth() {
      return dp(22);
    }

    @Override
    public int getIntrinsicHeight() {
      return dp(22);
    }

    @Override
    public void draw(Canvas canvas) {
      canvas.save();
      canvas.translate(getBounds().left, getBounds().top);
      canvas.scale(getBounds().width() / 24f, getBounds().height() / 24f);
      if (name.equals("user")) {
        canvas.drawCircle(12, 7, 3, p);
        canvas.drawArc(5, 12, 19, 23, 180, 180, false, p);
      } else if (name.equals("settings")) {
        for (int y : new int[] {5, 12, 19}) {
          canvas.drawLine(3, y, 21, y, p);
          canvas.drawCircle(y == 12 ? 15 : 8, y, 2.3f, p);
        }
      } else if (name.equals("export")) {
        canvas.drawLine(12, 3, 12, 16, p);
        canvas.drawLine(8, 7, 12, 3, p);
        canvas.drawLine(16, 7, 12, 3, p);
        canvas.drawLine(4, 14, 4, 21, p);
        canvas.drawLine(4, 21, 20, 21, p);
        canvas.drawLine(20, 21, 20, 14, p);
      } else if (name.equals("search")) {
        canvas.drawCircle(10, 10, 6, p);
        canvas.drawLine(15, 15, 21, 21, p);
      } else if (name.equals("calendar")) {
        canvas.drawRoundRect(3, 5, 21, 21, 2, 2, p);
        canvas.drawLine(3, 10, 21, 10, p);
        canvas.drawLine(7, 2, 7, 7, p);
        canvas.drawLine(17, 2, 17, 7, p);
      } else {
        canvas.drawLine(3, 9, 3, 3, p);
        canvas.drawLine(3, 3, 9, 3, p);
        canvas.drawLine(15, 3, 21, 3, p);
        canvas.drawLine(21, 3, 21, 9, p);
        canvas.drawLine(21, 15, 21, 21, p);
        canvas.drawLine(21, 21, 15, 21, p);
        canvas.drawLine(9, 21, 3, 21, p);
        canvas.drawLine(3, 21, 3, 15, p);
        canvas.drawLine(5, 12, 19, 12, p);
      }
      canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
      p.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(android.graphics.ColorFilter filter) {
      p.setColorFilter(filter);
    }

    @Override
    public int getOpacity() {
      return android.graphics.PixelFormat.TRANSLUCENT;
    }
  }
}
