package br.com.phs.coleta;

import static org.junit.Assert.*;

import android.content.*;
import android.content.pm.*;
import android.content.res.XmlResourceParser;
import android.net.Uri;
import androidx.core.content.FileProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.xmlpull.v1.XmlPullParser;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ExportIntentsTest {
  @Test
  public void oneDriveReceivesScopedContentUriAndReadPermission() {
    Uri uri =
        Uri.parse(
            "content://br.com.phs.coleta.files/exports/batch/RESULTADO_PHS_30-10-2026_CAMPO01.xlsx");
    Intent intent = ExportIntents.oneDrive(uri, "RESULTADO_PHS_30-10-2026_CAMPO01.xlsx", false);
    assertEquals("com.microsoft.skydrive", intent.getPackage());
    assertEquals(Intent.ACTION_SEND, intent.getAction());
    assertEquals(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", intent.getType());
    assertEquals(uri, intent.getParcelableExtra(Intent.EXTRA_STREAM));
    assertEquals(uri, intent.getClipData().getItemAt(0).getUri());
    assertTrue((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
    assertEquals(0, intent.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
  }

  @Test
  public void csvKeepsCsvMimeType() {
    assertEquals(
        "text/csv",
        ExportIntents.oneDrive(
                Uri.parse("content://br.com.phs.coleta.files/exports/test.csv"), "test.csv", true)
            .getType());
  }

  @Test
  public void providerExposesOnlyExportCacheAndIsNotExported() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    ProviderInfo provider =
        c.getPackageManager()
            .getProviderInfo(
                new ComponentName(c, FileProvider.class), PackageManager.GET_META_DATA);
    assertFalse(provider.exported);
    assertTrue(provider.grantUriPermissions);
    int count = 0;
    try (XmlResourceParser xml =
        c.getResources().getXml(provider.metaData.getInt("android.support.FILE_PROVIDER_PATHS"))) {
      for (int event = xml.getEventType();
          event != XmlPullParser.END_DOCUMENT;
          event = xml.next()) {
        if (event == XmlPullParser.START_TAG && !xml.getName().equals("paths")) {
          assertEquals("cache-path", xml.getName());
          assertEquals("exports/", xml.getAttributeValue(null, "path"));
          count++;
        }
      }
    }
    assertEquals(1, count);
  }
}
