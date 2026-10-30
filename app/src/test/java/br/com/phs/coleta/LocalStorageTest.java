package br.com.phs.coleta;

import static org.junit.Assert.*;

import android.content.*;
import android.net.Uri;
import android.provider.DocumentsContract;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LocalStorageTest {
  @Test
  public void acceptsBuiltInDocumentsFolderAndFile() {
    Uri folder =
        DocumentsContract.buildTreeDocumentUri(LocalStorage.AUTHORITY, "primary:Documents/PHS");
    assertTrue(LocalStorage.internalFolder(folder));
    assertTrue(
        LocalStorage.internalFile(
            DocumentsContract.buildDocumentUriUsingTree(
                folder, "primary:Documents/PHS/test.xlsx")));
  }

  @Test
  public void rejectsCloudProvidersAndRemovableVolumes() {
    assertFalse(
        LocalStorage.internalFolder(
            DocumentsContract.buildTreeDocumentUri(
                "com.microsoft.skydrive.documents", "primary:PHS")));
    assertFalse(
        LocalStorage.internalFolder(
            DocumentsContract.buildTreeDocumentUri(LocalStorage.AUTHORITY, "1234-ABCD:PHS")));
    assertFalse(LocalStorage.internalFile(Uri.parse("file:///storage/emulated/0/test.xlsx")));
  }

  @Test
  public void pickerRequestsOnlyLocalStorage() {
    Intent intent =
        new LocalStorage.FolderPicker().createIntent(RuntimeEnvironment.getApplication(), null);
    assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, intent.getAction());
    assertTrue(intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false));
    Uri initial = intent.getParcelableExtra(DocumentsContract.EXTRA_INITIAL_URI);
    assertEquals("primary:Documents", DocumentsContract.getDocumentId(initial));
  }

  @Test
  public void migrationRemovesOnlyCloudConfiguration() {
    SharedPreferences p =
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("migration-test", Context.MODE_PRIVATE);
    p.edit()
        .clear()
        .putString("graphClientId", "client")
        .putString("graph_session.client", "encrypted")
        .putString("outputMode", "onedrive")
        .putString("user", "Maria")
        .putString("deviceName", "CAMPO01")
        .putString("input", "content://input")
        .commit();
    LocalStorage.removeCloudPreferences(p);
    assertFalse(p.contains("graphClientId"));
    assertFalse(p.contains("graph_session.client"));
    assertFalse(p.contains("outputMode"));
    assertEquals("Maria", p.getString("user", ""));
    assertEquals("CAMPO01", p.getString("deviceName", ""));
    assertEquals("content://input", p.getString("input", ""));
  }

  @Test
  public void applicationDoesNotRequestInternetAccess() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    String[] permissions =
        c.getPackageManager()
            .getPackageInfo(c.getPackageName(), android.content.pm.PackageManager.GET_PERMISSIONS)
            .requestedPermissions;
    assertNotNull(permissions);
    for (String permission : permissions)
      assertNotEquals(android.Manifest.permission.INTERNET, permission);
  }
}
