package br.com.phs.coleta;

import android.content.*;
import android.net.Uri;
import android.provider.DocumentsContract;
import androidx.activity.result.contract.ActivityResultContracts;

/** Output is restricted to the Android provider for built-in shared storage. */
final class LocalStorage {
  static final String AUTHORITY = "com.android.externalstorage.documents";

  static final class FolderPicker extends ActivityResultContracts.OpenDocumentTree {
    @Override
    public Intent createIntent(Context context, Uri input) {
      Uri initial =
          input != null
              ? input
              : DocumentsContract.buildDocumentUri(AUTHORITY, "primary:Documents");
      return super.createIntent(context, initial).putExtra(Intent.EXTRA_LOCAL_ONLY, true);
    }
  }

  private static boolean builtInId(String id) {
    return id.startsWith("primary:") || id.startsWith("home:");
  }

  static boolean internalFolder(Uri uri) {
    try {
      return uri != null
          && "content".equals(uri.getScheme())
          && AUTHORITY.equals(uri.getAuthority())
          && builtInId(DocumentsContract.getTreeDocumentId(uri));
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  static boolean internalFile(Uri uri) {
    try {
      return uri != null
          && "content".equals(uri.getScheme())
          && AUTHORITY.equals(uri.getAuthority())
          && builtInId(DocumentsContract.getDocumentId(uri));
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  static void removeCloudPreferences(SharedPreferences prefs) {
    SharedPreferences.Editor edit = prefs.edit();
    for (String key : prefs.getAll().keySet())
      if (key.startsWith("graph_session")
          || key.equals("graphClientId")
          || key.equals("outputMode")
          || key.equals("output")) edit.remove(key);
    edit.apply();
  }

  private LocalStorage() {}
}
