package br.com.phs.coleta;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Export-scoped connection and folder picker: no persistent output destination setting. */
final class OneDriveExportFlow {
  private final Activity activity;
  private final android.content.SharedPreferences prefs;
  private final byte[] bytes;
  private final String name;
  private final boolean csv;
  private final Consumer<String> message;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final List<OneDriveClient.Folder> path = new ArrayList<>();
  private volatile boolean closed;
  private OneDriveClient client;
  private AlertDialog currentDialog;
  private Runnable poll;

  OneDriveExportFlow(
      Activity activity,
      android.content.SharedPreferences prefs,
      byte[] bytes,
      String name,
      boolean csv,
      Consumer<String> message) {
    this.activity = activity;
    this.prefs = prefs;
    this.bytes = bytes;
    this.name = name;
    this.csv = csv;
    this.message = message;
  }

  void start() {
    String id = prefs.getString("graphClientId", "");
    if (OneDriveClient.validClientId(id)) connect(id);
    else registrationDialog();
  }

  private boolean visible() {
    return !closed && !activity.isFinishing() && !activity.isDestroyed();
  }

  private LinearLayout body() {
    LinearLayout body = new LinearLayout(activity);
    body.setOrientation(LinearLayout.VERTICAL);
    int p = Math.round(20 * activity.getResources().getDisplayMetrics().density);
    body.setPadding(p, p / 2, p, p / 2);
    return body;
  }

  private void registrationDialog() {
    LinearLayout body = body();
    TextView text = new TextView(activity);
    text.setText(
        "A conexão direta precisa do registro PHS Reader no Microsoft Entra. Informe apenas o ID"
            + " público do aplicativo. Não informe senha nem segredo.\n\n"
            + "O acesso normal a arquivos não exige API cobrada. Se a Microsoft solicitar uma"
            + " contratação para criar o ambiente, interrompa o cadastro.");
    body.addView(text);
    EditText id = new EditText(activity);
    id.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    id.setSingleLine();
    id.setHint("ID do aplicativo (cliente)");
    id.setText(prefs.getString("graphClientId", ""));
    body.addView(id);
    Button help = new Button(activity);
    help.setText("Como registrar sem contratar recursos");
    help.setAllCaps(false);
    help.setOnClickListener(v -> instructions());
    body.addView(help);
    AlertDialog dialog =
        new AlertDialog.Builder(activity)
            .setTitle("Conectar OneDrive diretamente")
            .setView(body)
            .setPositiveButton("Conectar", null)
            .setNegativeButton("Cancelar", (d, w) -> close())
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      String value = id.getText().toString().trim();
                      if (!OneDriveClient.validClientId(value)) {
                        id.setError("Informe o ID no formato xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx");
                        return;
                      }
                      dialog.dismiss();
                      if (!value.equals(prefs.getString("graphClientId", "")))
                        new TokenVault(prefs, prefs.getString("graphClientId", "")).clear();
                      prefs.edit().putString("graphClientId", value).apply();
                      connect(value);
                    }));
    dialog.setOnCancelListener(d -> close());
    currentDialog = dialog;
    dialog.show();
  }

  private void instructions() {
    new AlertDialog.Builder(activity)
        .setTitle("Registro Microsoft · sem recursos pagos")
        .setMessage(
            "1. Acesse entra.microsoft.com com sua conta da organização.\n"
                + "2. Aplicativos → Registros de aplicativos → Novo registro: PHS Reader.\n"
                + "3. Escolha contas organizacionais e pessoais, conforme permitido.\n"
                + "4. Em Autenticação, habilite fluxos de cliente público.\n"
                + "5. Em Permissões de API, adicione Microsoft Graph → Delegadas →"
                + " Files.ReadWrite.\n"
                + "6. Copie o ID do aplicativo (cliente) para esta janela.\n\n"
                + "Não crie segredo, servidor, banco Azure ou assinatura paga. Se não houver acesso"
                + " a Registros de aplicativos, peça ao administrador. Enquanto isso, use o"
                + " OneDrive instalado ou Salvar como.")
        .setPositiveButton(
            "Abrir Microsoft Entra",
            (d, w) ->
                activity.startActivity(
                    new Intent(Intent.ACTION_VIEW, Uri.parse("https://entra.microsoft.com/"))))
        .setNegativeButton("Fechar", null)
        .show();
  }

  private void connect(String id) {
    client = new OneDriveClient(id, new TokenVault(prefs, id));
    loadRoot();
  }

  private void loadRoot() {
    job(
        "Conectando ao OneDrive…",
        client::root,
        root -> {
          path.clear();
          path.add(root);
          browse();
        });
  }

  private <T> void job(String label, Callable<T> work, Consumer<T> success) {
    if (!visible()) return;
    AlertDialog progress =
        new AlertDialog.Builder(activity)
            .setTitle("OneDrive")
            .setMessage(label)
            .setCancelable(false)
            .create();
    currentDialog = progress;
    progress.show();
    executor.execute(
        () -> {
          try {
            T result = work.call();
            handler.post(
                () -> {
                  progress.dismiss();
                  if (visible()) success.accept(result);
                });
          } catch (Exception e) {
            handler.post(
                () -> {
                  progress.dismiss();
                  if (!visible()) return;
                  if (e instanceof OneDriveClient.SignInRequired) signIn();
                  else showError(e);
                });
          }
        });
  }

  private void signIn() {
    job(
        "Preparando conexão Microsoft…",
        client::beginSignIn,
        code -> {
          LinearLayout body = body();
          TextView text = new TextView(activity);
          text.setText(
              "Abra a página Microsoft e informe este código para conectar sua conta ao PHS"
                  + " Reader:");
          body.addView(text);
          TextView userCode = new TextView(activity);
          userCode.setText(code.userCode());
          userCode.setTextSize(26);
          userCode.setTextIsSelectable(true);
          body.addView(userCode);
          TextView wait = new TextView(activity);
          wait.setText("Aguardando autorização. Nenhuma senha é digitada no PHS Reader.");
          body.addView(wait);
          AlertDialog login =
              new AlertDialog.Builder(activity)
                  .setTitle("Conectar conta Microsoft")
                  .setView(body)
                  .setPositiveButton("Copiar código e abrir Microsoft", null)
                  .setNegativeButton("Cancelar", (d, w) -> close())
                  .create();
          login.setOnShowListener(
              d ->
                  login
                      .getButton(AlertDialog.BUTTON_POSITIVE)
                      .setOnClickListener(
                          v -> {
                            android.content.ClipboardManager clipboard =
                                (android.content.ClipboardManager)
                                    activity.getSystemService(Context.CLIPBOARD_SERVICE);
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("Código Microsoft", code.userCode()));
                            activity.startActivity(
                                new Intent(Intent.ACTION_VIEW, Uri.parse(code.verificationUri())));
                          }));
          login.setOnCancelListener(d -> close());
          currentDialog = login;
          login.show();
          int[] interval = {code.interval()};
          poll =
              () -> {
                if (!visible()) return;
                executor.execute(
                    () -> {
                      try {
                        String status = client.poll(code);
                        handler.post(
                            () -> {
                              if (!visible()) {
                                if (status.equals("authorized")) client.disconnect();
                                return;
                              }
                              if (status.equals("authorized")) {
                                login.dismiss();
                                poll = null;
                                loadRoot();
                              } else {
                                if (status.equals("slow_down")) interval[0] += 5;
                                handler.postDelayed(poll, interval[0] * 1000L);
                              }
                            });
                      } catch (Exception e) {
                        handler.post(
                            () -> {
                              login.dismiss();
                              if (visible()) showError(e);
                            });
                      }
                    });
              };
          handler.postDelayed(poll, interval[0] * 1000L);
        });
  }

  private void browse() {
    OneDriveClient.Folder selected = path.get(path.size() - 1);
    job(
        "Carregando pastas…",
        () -> client.folders(selected),
        folders -> {
          List<String> labels = new ArrayList<>();
          if (path.size() > 1) labels.add("↑ Voltar");
          for (OneDriveClient.Folder folder : folders) labels.add("▸ " + folder.name());
          AlertDialog dialog =
              new AlertDialog.Builder(activity)
                  .setTitle("Pasta: " + selected.name())
                  .setItems(
                      labels.toArray(new String[0]),
                      (d, index) -> {
                        if (path.size() > 1 && index == 0) path.remove(path.size() - 1);
                        else path.add(folders.get(index - (path.size() > 1 ? 1 : 0)));
                        browse();
                      })
                  .setPositiveButton("Usar esta pasta", (d, w) -> checkExisting(selected))
                  .setNeutralButton(
                      "Trocar conta",
                      (d, w) -> {
                        client.disconnect();
                        signIn();
                      })
                  .setNegativeButton("Cancelar", (d, w) -> close())
                  .create();
          dialog.setOnCancelListener(d -> close());
          currentDialog = dialog;
          dialog.show();
        });
  }

  private void checkExisting(OneDriveClient.Folder folder) {
    job(
        "Conferindo arquivo de saída…",
        () -> client.existing(folder, name),
        existing -> {
          if (existing == null) upload(folder, null);
          else {
            currentDialog =
                new AlertDialog.Builder(activity)
                    .setTitle("Arquivo já existe")
                    .setMessage(
                        "Substituir "
                            + name
                            + " nesta pasta com todos os registros atuais do lote?")
                    .setPositiveButton("Substituir", (d, w) -> upload(folder, existing))
                    .setNegativeButton("Cancelar", (d, w) -> close())
                    .show();
          }
        });
  }

  private void upload(OneDriveClient.Folder folder, OneDriveClient.Item existing) {
    job(
        "Enviando " + name + "…",
        () -> client.upload(folder, name, bytes, csv, existing),
        result -> {
          message.accept("Arquivo salvo no OneDrive: " + result.name());
          close();
        });
  }

  private void showError(Exception e) {
    currentDialog =
        new AlertDialog.Builder(activity)
            .setTitle("Conexão OneDrive não concluída")
            .setMessage(
                (e.getMessage() == null
                        ? "Tente novamente com conexão à internet."
                        : e.getMessage())
                    + "\n\n"
                    + "As coletas permanecem no aparelho. Você pode exportar novamente usando outro"
                    + " destino.")
            .setPositiveButton("Fechar", (d, w) -> close())
            .show();
  }

  void close() {
    closed = true;
    if (poll != null) handler.removeCallbacks(poll);
    if (currentDialog != null) currentDialog.dismiss();
    executor.shutdown();
  }
}
