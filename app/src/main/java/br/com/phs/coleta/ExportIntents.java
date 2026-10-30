package br.com.phs.coleta;

import android.content.*;
import android.net.Uri;
import androidx.core.content.FileProvider;
import java.io.File;

final class ExportIntents {
  static Intent oneDrive(Context context, File file, boolean csv) {
    Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".files", file);
    return oneDrive(uri, file.getName(), csv);
  }

  static Intent oneDrive(Uri uri, String fileName, boolean csv) {
    Intent intent =
        new Intent(Intent.ACTION_SEND)
            .setPackage("com.microsoft.skydrive")
            .setType(
                csv
                    ? "text/csv"
                    : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TITLE, fileName)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    intent.setClipData(ClipData.newRawUri(fileName, uri));
    return intent;
  }

  private ExportIntents() {}
}
