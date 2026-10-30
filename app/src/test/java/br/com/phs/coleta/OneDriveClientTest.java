package br.com.phs.coleta;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class OneDriveClientTest {
  private static final String ID = "11111111-2222-3333-4444-555555555555";

  static final class Session implements OneDriveClient.SessionStore {
    String value = "refresh";

    public String read() {
      return value;
    }

    public void save(String token) {
      value = token;
    }

    public void clear() {
      value = "";
    }
  }

  record Request(String method, String url, Map<String, String> headers, byte[] bytes) {}

  static final class Fake implements OneDriveClient.Transport {
    final Queue<OneDriveClient.Response> responses = new ArrayDeque<>();
    final List<Request> requests = new ArrayList<>();

    Fake response(int status, String body) throws Exception {
      responses.add(new OneDriveClient.Response(status, new JSONObject(body)));
      return this;
    }

    public OneDriveClient.Response request(
        String method, String url, Map<String, String> headers, byte[] bytes) {
      requests.add(new Request(method, url, new HashMap<>(headers), bytes));
      if (responses.isEmpty())
        throw new AssertionError("Unexpected request: " + method + " " + url);
      return responses.remove();
    }
  }

  private Fake authorized(Fake http) throws Exception {
    return http.response(
        200,
        "{\"access_token\":\"test-access\",\"refresh_token\":\"rotated-refresh\",\"expires_in\":3600}");
  }

  @Test
  public void clientIdMustBePublicGuid() {
    assertTrue(OneDriveClient.validClientId(ID));
    assertFalse(OneDriveClient.validClientId("secret"));
    assertThrows(
        IllegalArgumentException.class, () -> new OneDriveClient("", new Session(), new Fake()));
  }

  @Test
  public void signInRequestsOnlyDelegatedFileScopeWithoutSecret() throws Exception {
    Fake http =
        new Fake()
            .response(
                200,
                "{\"device_code\":\"private-code\",\"user_code\":\"ABCD1234\",\"verification_uri\":\"https://microsoft.com/devicelogin\",\"interval\":5,\"expires_in\":900}");
    OneDriveClient c = new OneDriveClient(ID, new Session(), http);
    OneDriveClient.DeviceCode code = c.beginSignIn();
    assertEquals("ABCD1234", code.userCode());
    String body = new String(http.requests.get(0).bytes(), StandardCharsets.UTF_8);
    assertTrue(body.contains("Files.ReadWrite"));
    assertFalse(body.contains("Files.ReadWrite.All"));
    assertFalse(body.contains("client_secret"));
    assertTrue(body.contains("offline_access"));
  }

  @Test
  public void pendingAuthorizationDoesNotStoreSession() throws Exception {
    Fake http = new Fake().response(400, "{\"error\":\"authorization_pending\"}");
    Session s = new Session();
    s.value = "";
    OneDriveClient c = new OneDriveClient(ID, s, http);
    assertEquals(
        "authorization_pending",
        c.poll(
            new OneDriveClient.DeviceCode(
                "code",
                "user",
                "https://microsoft.com/devicelogin",
                5,
                System.currentTimeMillis() + 10000)));
    assertEquals("", s.value);
  }

  @Test
  public void slowDownIsExposedToPollingLoop() throws Exception {
    Fake http = new Fake().response(400, "{\"error\":\"slow_down\"}");
    assertEquals(
        "slow_down",
        new OneDriveClient(ID, new Session(), http)
            .poll(
                new OneDriveClient.DeviceCode(
                    "code",
                    "user",
                    "https://microsoft.com/devicelogin",
                    5,
                    System.currentTimeMillis() + 10000)));
  }

  @Test
  public void expiredCodeIsNotPolled() throws Exception {
    Fake http = new Fake();
    assertThrows(
        java.io.IOException.class,
        () ->
            new OneDriveClient(ID, new Session(), http)
                .poll(
                    new OneDriveClient.DeviceCode(
                        "c", "u", "https://microsoft.com/devicelogin", 5, 0)));
    assertTrue(http.requests.isEmpty());
  }

  @Test
  public void rootRefreshesTokenAndRotatesSession() throws Exception {
    Fake http =
        authorized(new Fake())
            .response(200, "{\"id\":\"drive\"}")
            .response(200, "{\"id\":\"root\",\"name\":\"Documents\"}");
    Session s = new Session();
    OneDriveClient.Folder root = new OneDriveClient(ID, s, http).root();
    assertEquals("root", root.id());
    assertEquals("rotated-refresh", s.value);
    assertEquals("Bearer test-access", http.requests.get(1).headers().get("Authorization"));
  }

  @Test
  public void missingSessionRequiresUserConnection() throws Exception {
    Session s = new Session();
    s.value = "";
    assertThrows(
        OneDriveClient.SignInRequired.class, () -> new OneDriveClient(ID, s, new Fake()).root());
  }

  @Test
  public void foldersFilterFilesAndFollowTrustedPages() throws Exception {
    Fake http =
        authorized(new Fake())
            .response(
                200,
                "{\"value\":[{\"id\":\"b\",\"name\":\"B\",\"folder\":{}},{\"id\":\"file\",\"name\":\"file.xlsx\"}],\"@odata.nextLink\":\"https://graph.microsoft.com/v1.0/next\"}")
            .response(200, "{\"value\":[{\"id\":\"a\",\"name\":\"A\",\"folder\":{}}]}");
    List<OneDriveClient.Folder> folders =
        new OneDriveClient(ID, new Session(), http)
            .folders(new OneDriveClient.Folder("drive", "root", "OneDrive"));
    assertEquals(2, folders.size());
    assertEquals("A", folders.get(0).name());
    assertEquals("B", folders.get(1).name());
  }

  @Test
  public void paginationCannotSendTokenToAnotherHost() throws Exception {
    Fake http =
        authorized(new Fake())
            .response(200, "{\"value\":[],\"@odata.nextLink\":\"https://example.com/steal\"}");
    assertThrows(
        java.io.IOException.class,
        () ->
            new OneDriveClient(ID, new Session(), http)
                .folders(new OneDriveClient.Folder("d", "r", "OneDrive")));
    assertEquals(2, http.requests.size());
  }

  @Test
  public void notFoundFileIsNotAnUploadFailure() throws Exception {
    Fake http = authorized(new Fake()).response(404, "{\"error\":{\"code\":\"itemNotFound\"}}");
    assertNull(
        new OneDriveClient(ID, new Session(), http)
            .existing(new OneDriveClient.Folder("d", "r", "OneDrive"), "test.xlsx"));
  }

  @Test
  public void uploadEncodesNameAndReturnsRemoteConfirmation() throws Exception {
    Fake http =
        authorized(new Fake()).response(201, "{\"id\":\"saved\",\"name\":\"teste espaço.xlsx\"}");
    OneDriveClient.Item item =
        new OneDriveClient(ID, new Session(), http)
            .upload(
                new OneDriveClient.Folder("d", "r", "OneDrive"),
                "teste espaço.xlsx",
                new byte[] {1, 2},
                false,
                null);
    assertEquals("saved", item.id());
    assertTrue(http.requests.get(1).url().contains("%20"));
    assertTrue(http.requests.get(1).url().endsWith(":/content"));
    assertArrayEquals(new byte[] {1, 2}, http.requests.get(1).bytes());
  }

  @Test
  public void replacingUsesItemIdAndVersion() throws Exception {
    Fake http = authorized(new Fake()).response(200, "{\"id\":\"old\",\"name\":\"test.csv\"}");
    new OneDriveClient(ID, new Session(), http)
        .upload(
            new OneDriveClient.Folder("d", "r", "OneDrive"),
            "test.csv",
            new byte[] {1},
            true,
            new OneDriveClient.Item("old", "test.csv", "version1"));
    Request upload = http.requests.get(1);
    assertEquals("version1", upload.headers().get("If-Match"));
    assertTrue(upload.url().endsWith("/items/old/content"));
    assertEquals("text/csv", upload.headers().get("Content-Type"));
  }

  @Test
  public void conflictDoesNotReportSuccess() throws Exception {
    Fake http =
        authorized(new Fake()).response(412, "{\"error\":{\"code\":\"preconditionFailed\"}}");
    assertThrows(
        java.io.IOException.class,
        () ->
            new OneDriveClient(ID, new Session(), http)
                .upload(
                    new OneDriveClient.Folder("d", "r", "OneDrive"),
                    "test.xlsx",
                    new byte[] {1},
                    false,
                    new OneDriveClient.Item("old", "test.xlsx", "v1")));
  }

  @Test
  public void redirectAndInvalidHostsAreRejected() {
    assertFalse(OneDriveClient.trustedGraphUrl("http://graph.microsoft.com/v1.0/me"));
    assertFalse(OneDriveClient.trustedGraphUrl("https://graph.microsoft.com.evil.test/v1.0/me"));
    assertFalse(OneDriveClient.trustedGraphUrl("https://user@graph.microsoft.com/v1.0/me"));
  }
}
