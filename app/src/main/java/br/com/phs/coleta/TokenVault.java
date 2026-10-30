package br.com.phs.coleta;

import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Refresh tokens never enter plaintext preferences or files. No client secret is used. */
final class TokenVault implements OneDriveClient.SessionStore {
  private final SharedPreferences prefs;
  private final String preferenceKey;
  private static final String ALIAS = "PHSReader.OneDrive.Session";

  TokenVault(SharedPreferences prefs, String clientId) {
    this.prefs = prefs;
    this.preferenceKey = "graph_session." + clientId;
  }

  private javax.crypto.SecretKey key() throws Exception {
    KeyStore store = KeyStore.getInstance("AndroidKeyStore");
    store.load(null);
    if (!store.containsAlias(ALIAS)) {
      KeyGenerator generator =
          KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
      generator.init(
          new KeyGenParameterSpec.Builder(
                  ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .build());
      generator.generateKey();
    }
    return (javax.crypto.SecretKey) store.getKey(ALIAS, null);
  }

  public String read() throws Exception {
    String value = prefs.getString(preferenceKey, "");
    if (value.isEmpty()) return "";
    try {
      String[] parts = value.split(":", 2);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          key(),
          new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
      return new String(
          cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)),
          java.nio.charset.StandardCharsets.UTF_8);
    } catch (Exception e) {
      clear();
      throw new java.io.IOException("A sessão Microsoft expirou. Conecte a conta novamente.");
    }
  }

  public void save(String token) throws Exception {
    if (token.isEmpty()) {
      clear();
      return;
    }
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, key());
    String encrypted =
        Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
            + ":"
            + Base64.encodeToString(
                cipher.doFinal(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                Base64.NO_WRAP);
    if (!prefs.edit().putString(preferenceKey, encrypted).commit())
      throw new java.io.IOException("Não foi possível proteger a sessão Microsoft neste aparelho.");
  }

  public void clear() {
    prefs.edit().remove(preferenceKey).apply();
  }
}
