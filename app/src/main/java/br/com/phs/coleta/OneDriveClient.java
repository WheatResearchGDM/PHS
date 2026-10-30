package br.com.phs.coleta;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;
import org.json.*;

/** Microsoft public-client device authorization and delegated file access. */
final class OneDriveClient {
  interface SessionStore {
    String read() throws Exception;

    void save(String token) throws Exception;

    void clear();
  }

  interface Transport {
    Response request(String method, String url, Map<String, String> headers, byte[] bytes)
        throws Exception;
  }

  record Response(int status, JSONObject body) {}

  record DeviceCode(
      String deviceCode, String userCode, String verificationUri, int interval, long expiresAt) {}

  record Folder(String driveId, String id, String name) {}

  record Item(String id, String name, String etag) {}

  static final String GRAPH = "https://graph.microsoft.com/v1.0";
  static final String AUTH = "https://login.microsoftonline.com/common/oauth2/v2.0";
  static final String SCOPE = "https://graph.microsoft.com/Files.ReadWrite offline_access";

  static final class SignInRequired extends IOException {
    SignInRequired() {
      super("Conecte sua conta Microsoft para continuar.");
    }
  }

  private final String clientId;
  private final SessionStore session;
  private final Transport transport;
  private String access = "";
  private long expiresAt;

  OneDriveClient(String clientId, SessionStore session) {
    this(clientId, session, new HttpTransport());
  }

  OneDriveClient(String clientId, SessionStore session, Transport transport) {
    if (!validClientId(clientId))
      throw new IllegalArgumentException(
          "Informe o ID do aplicativo (cliente) do registro PHS Reader na Microsoft.");
    this.clientId = clientId;
    this.session = session;
    this.transport = transport;
  }

  static boolean validClientId(String id) {
    return id != null
        && id.matches(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
  }

  private static String part(String text) {
    try {
      return URLEncoder.encode(text, "UTF-8").replace("+", "%20");
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static byte[] form(Map<String, String> fields) {
    StringJoiner joined = new StringJoiner("&");
    fields.forEach((k, v) -> joined.add(part(k) + "=" + part(v)));
    return joined.toString().getBytes(StandardCharsets.UTF_8);
  }

  DeviceCode beginSignIn() throws Exception {
    Response r =
        transport.request(
            "POST",
            AUTH + "/devicecode",
            Map.of("Content-Type", "application/x-www-form-urlencoded"),
            form(Map.of("client_id", clientId, "scope", SCOPE)));
    requireOk(r);
    String verify = r.body.getString("verification_uri");
    URI uri = URI.create(verify);
    if (!"https".equals(uri.getScheme())
        || !Set.of("microsoft.com", "www.microsoft.com", "login.microsoftonline.com")
            .contains(uri.getHost()))
      throw new IOException("Endereço de autenticação Microsoft inválido.");
    return new DeviceCode(
        r.body.getString("device_code"),
        r.body.getString("user_code"),
        verify,
        Math.max(5, r.body.optInt("interval", 5)),
        System.currentTimeMillis() + r.body.optLong("expires_in", 900) * 1000);
  }

  /** Returns authorized, authorization_pending, or slow_down; never polls faster than requested. */
  String poll(DeviceCode code) throws Exception {
    if (System.currentTimeMillis() >= code.expiresAt)
      throw new IOException("O código de acesso expirou. Tente conectar novamente.");
    Response r =
        transport.request(
            "POST",
            AUTH + "/token",
            Map.of("Content-Type", "application/x-www-form-urlencoded"),
            form(
                Map.of(
                    "grant_type",
                    "urn:ietf:params:oauth:grant-type:device_code",
                    "client_id",
                    clientId,
                    "device_code",
                    code.deviceCode)));
    if (r.status >= 200 && r.status < 300) {
      acceptToken(r.body);
      return "authorized";
    }
    String error = r.body.optString("error");
    if (error.equals("authorization_pending") || error.equals("slow_down")) return error;
    requireOk(r);
    return "";
  }

  private synchronized void acceptToken(JSONObject token) throws Exception {
    String newAccess = token.getString("access_token");
    if (token.has("refresh_token")) session.save(token.getString("refresh_token"));
    access = newAccess;
    expiresAt =
        System.currentTimeMillis() + Math.max(0, token.optLong("expires_in", 3600) - 60) * 1000;
  }

  private synchronized String token() throws Exception {
    if (!access.isEmpty() && System.currentTimeMillis() < expiresAt) return access;
    String refresh = session.read();
    if (refresh.isEmpty()) throw new SignInRequired();
    Response r =
        transport.request(
            "POST",
            AUTH + "/token",
            Map.of("Content-Type", "application/x-www-form-urlencoded"),
            form(
                Map.of(
                    "grant_type",
                    "refresh_token",
                    "client_id",
                    clientId,
                    "refresh_token",
                    refresh,
                    "scope",
                    SCOPE)));
    if (r.status == 400 || r.status == 401) {
      disconnect();
      throw new SignInRequired();
    }
    requireOk(r);
    acceptToken(r.body);
    return access;
  }

  synchronized void disconnect() {
    access = "";
    expiresAt = 0;
    session.clear();
  }

  private Response graph(String method, String url, byte[] bytes, Map<String, String> extra)
      throws Exception {
    if (!trustedGraphUrl(url)) throw new IOException("Endereço OneDrive inválido.");
    Map<String, String> headers = new HashMap<>(extra);
    headers.put("Authorization", "Bearer " + token());
    Response result = transport.request(method, url, headers, bytes);
    if (result.status == 401) {
      access = "";
      expiresAt = 0;
      headers.put("Authorization", "Bearer " + token());
      result = transport.request(method, url, headers, bytes);
      if (result.status == 401) {
        disconnect();
        throw new SignInRequired();
      }
    }
    return result;
  }

  static boolean trustedGraphUrl(String url) {
    try {
      URI uri = URI.create(url);
      return uri.getScheme().equals("https")
          && "graph.microsoft.com".equals(uri.getHost())
          && uri.getUserInfo() == null
          && (uri.getPort() == -1 || uri.getPort() == 443);
    } catch (Exception e) {
      return false;
    }
  }

  Folder root() throws Exception {
    Response drive = graph("GET", GRAPH + "/me/drive?$select=id", null, Map.of());
    requireOk(drive);
    String id = drive.body.getString("id");
    Response root =
        graph("GET", GRAPH + "/drives/" + part(id) + "/root?$select=id,name", null, Map.of());
    requireOk(root);
    return new Folder(id, root.body.getString("id"), "OneDrive");
  }

  List<Folder> folders(Folder parent) throws Exception {
    List<Folder> folders = new ArrayList<>();
    String next =
        GRAPH
            + "/drives/"
            + part(parent.driveId)
            + "/items/"
            + part(parent.id)
            + "/children?$select=id,name,folder&$top=200";
    Set<String> seen = new HashSet<>();
    while (!next.isEmpty()) {
      if (!seen.add(next) || seen.size() > 500)
        throw new IOException("Resposta de pastas muito extensa ou repetida.");
      Response r = graph("GET", next, null, Map.of());
      requireOk(r);
      JSONArray entries = r.body.getJSONArray("value");
      for (int i = 0; i < entries.length(); i++) {
        JSONObject item = entries.getJSONObject(i);
        if (item.has("folder"))
          folders.add(new Folder(parent.driveId, item.getString("id"), item.getString("name")));
      }
      next = r.body.optString("@odata.nextLink", "");
    }
    folders.sort(Comparator.comparing(Folder::name, String.CASE_INSENSITIVE_ORDER));
    return folders;
  }

  private static String filePath(Folder folder, String name) {
    if (name.isEmpty() || name.contains("/") || name.contains("\\"))
      throw new IllegalArgumentException("Nome de arquivo inválido.");
    return GRAPH
        + "/drives/"
        + part(folder.driveId)
        + "/items/"
        + part(folder.id)
        + ":/"
        + part(name);
  }

  Item existing(Folder folder, String name) throws Exception {
    Response r = graph("GET", filePath(folder, name) + "?$select=id,name,eTag", null, Map.of());
    if (r.status == 404) return null;
    requireOk(r);
    return new Item(
        r.body.getString("id"), r.body.optString("name", name), r.body.optString("eTag", ""));
  }

  Item upload(Folder folder, String name, byte[] bytes, boolean csv, Item existing)
      throws Exception {
    if (bytes.length > 250_000_000)
      throw new IOException("Arquivo excede o limite de envio desta versão.");
    String url =
        existing == null
            ? filePath(folder, name) + ":/content"
            : GRAPH
                + "/drives/"
                + part(folder.driveId)
                + "/items/"
                + part(existing.id)
                + "/content";
    Map<String, String> headers = new HashMap<>();
    headers.put(
        "Content-Type",
        csv ? "text/csv" : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    if (existing != null && !existing.etag.isEmpty()) headers.put("If-Match", existing.etag);
    Response r = graph("PUT", url, bytes, headers);
    requireOk(r);
    return new Item(
        r.body.getString("id"), r.body.optString("name", name), r.body.optString("eTag", ""));
  }

  private static void requireOk(Response r) throws IOException {
    if (r.status >= 200 && r.status < 300) return;
    String code =
        r.body.opt("error") instanceof JSONObject
            ? r.body.optJSONObject("error").optString("code")
            : r.body.optString("error");
    String message =
        switch (code) {
          case "authorization_declined", "access_denied" ->
              "A conexão Microsoft foi cancelada ou recusada.";
          case "invalid_client", "unauthorized_client" ->
              "Confira o ID do aplicativo e habilite os fluxos de cliente público no registro"
                  + " Microsoft.";
          case "expired_token" -> "O código de acesso expirou. Conecte novamente.";
          case "accessDenied", "Authorization_RequestDenied" ->
              "A conta ou a organização não autorizou acesso a esta pasta. Consulte o"
                  + " administrador.";
          default ->
              "Operação OneDrive não concluída (HTTP "
                  + r.status
                  + "). As coletas permanecem no aparelho.";
        };
    if (r.status == 412)
      message =
          "O arquivo foi alterado no OneDrive. Exporte novamente para conferir a substituição.";
    if (r.status == 429)
      message = "O OneDrive limitou temporariamente as requisições. Aguarde e tente novamente.";
    throw new IOException(message);
  }

  static final class HttpTransport implements Transport {
    public Response request(String method, String url, Map<String, String> headers, byte[] bytes)
        throws Exception {
      URI address = URI.create(url);
      if (!"https".equals(address.getScheme())
          || !Set.of("graph.microsoft.com", "login.microsoftonline.com")
              .contains(address.getHost())) throw new IOException("Endereço Microsoft inválido.");
      HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
      try {
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(60000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        headers.forEach(connection::setRequestProperty);
        if (bytes != null) {
          connection.setDoOutput(true);
          connection.setFixedLengthStreamingMode(bytes.length);
          try (OutputStream out = connection.getOutputStream()) {
            out.write(bytes);
          }
        }
        int status = connection.getResponseCode();
        InputStream stream =
            status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String text = "";
        if (stream != null)
          try (InputStream in = stream;
              ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] block = new byte[8192];
            int total = 0;
            for (int n; (n = in.read(block)) != -1; ) {
              total += n;
              if (total > 8_000_000) throw new IOException("Resposta Microsoft muito extensa.");
              out.write(block, 0, n);
            }
            text = out.toString("UTF-8");
          }
        JSONObject body;
        try {
          body = text.isEmpty() ? new JSONObject() : new JSONObject(text);
        } catch (JSONException e) {
          throw new IOException("Resposta Microsoft inválida (HTTP " + status + ").");
        }
        return new Response(status, body);
      } finally {
        connection.disconnect();
      }
    }
  }
}
