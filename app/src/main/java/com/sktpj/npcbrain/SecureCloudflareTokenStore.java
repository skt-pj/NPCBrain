package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecureCloudflareTokenStore {
    private static final String PREFS = "secure_cloudflare_token";
    private static final String KEY_ALIAS = "npcbrain_cloudflare_token";
    private static final String PREF_IV = "iv";
    private static final String PREF_VALUE = "value";

    private final SharedPreferences preferences;

    SecureCloudflareTokenStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    void save(String token) throws Exception {
        String value = token == null ? "" : token.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Cloudflare API token is required");
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        preferences.edit()
                .putString(PREF_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString(PREF_VALUE, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .apply();
    }

    String load() throws Exception {
        String ivText = preferences.getString(PREF_IV, null);
        String valueText = preferences.getString(PREF_VALUE, null);
        if (ivText == null || valueText == null) return "";
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = Base64.decode(ivText, Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(valueText, Base64.NO_WRAP);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    void clear() {
        preferences.edit().clear().apply();
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }
}
