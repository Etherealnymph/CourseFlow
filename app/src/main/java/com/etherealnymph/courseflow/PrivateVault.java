package com.etherealnymph.courseflow;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Private notes are encrypted with AES-256-GCM. The key is derived from the
 * password with PBKDF2-HMAC-SHA256 and never leaves memory; the store only holds
 * ciphertext, a random salt and an encrypted verifier.
 */
public final class PrivateVault {

    private static final int ITERATIONS = 90_000;
    private static final int KEY_BITS = 256;
    private static final String VERIFY_TEXT = "courseflow.private.v1";
    private static final SecureRandom RNG = new SecureRandom();

    private PrivateVault() {}

    public static class Secret {
        public String id;
        public String title;
        public String body;
        public long updatedAt;
        public ArrayList<Store.Attachment> attachments = new ArrayList<>();

        public Secret() {}

        public Secret(String id, String title, String body, long updatedAt) {
            this.id = id;
            this.title = title;
            this.body = body;
            this.updatedAt = updatedAt;
        }
    }

    public static class Unlocked {
        public final SecretKey key;
        public final ArrayList<Secret> notes = new ArrayList<>();

        Unlocked(SecretKey key) {
            this.key = key;
        }
    }

    public static Store.Vault create(String password) throws Exception {
        byte[] salt = random(16);
        SecretKey key = derive(password, salt);
        Store.Vault vault = new Store.Vault();
        vault.salt = Store.base64(salt);
        byte[] iv = random(12);
        byte[] cipher = crypt(Cipher.ENCRYPT_MODE, key, iv, VERIFY_TEXT.getBytes(StandardCharsets.UTF_8));
        vault.verifierIv = Store.base64(iv);
        vault.verifierCipher = Store.base64(cipher);
        return vault;
    }

    public static Unlocked unlock(Store.Vault vault, String password) {
        if (vault == null || password == null) return null;
        try {
            byte[] salt = Store.unbase64(vault.salt);
            SecretKey key = derive(password, salt);
            byte[] plain = crypt(Cipher.DECRYPT_MODE, key, Store.unbase64(vault.verifierIv),
                    Store.unbase64(vault.verifierCipher));
            if (!VERIFY_TEXT.equals(new String(plain, StandardCharsets.UTF_8))) return null;
            Unlocked u = new Unlocked(key);
            for (Store.PrivateNote pn : vault.notes) {
                Secret s = decrypt(pn, key);
                if (s != null) u.notes.add(s);
            }
            u.notes.sort((a, b) -> Long.compare(b.updatedAt, a.updatedAt));
            return u;
        } catch (Exception e) {
            return null;
        }
    }

    public static Store.PrivateNote encrypt(Secret secret, SecretKey key) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("title", secret.title == null ? "" : secret.title);
        obj.put("body", secret.body == null ? "" : secret.body);
        obj.put("attachments", Store.attachmentsToJson(secret.attachments));
        byte[] iv = random(12);
        byte[] cipher = crypt(Cipher.ENCRYPT_MODE, key, iv,
                obj.toString().getBytes(StandardCharsets.UTF_8));
        Store.PrivateNote pn = new Store.PrivateNote();
        pn.id = secret.id == null || secret.id.isEmpty() ? Store.newId("secret") : secret.id;
        pn.iv = Store.base64(iv);
        pn.cipher = Store.base64(cipher);
        pn.updatedAt = System.currentTimeMillis();
        return pn;
    }

    public static Secret decrypt(Store.PrivateNote note, SecretKey key) {
        try {
            byte[] plain = crypt(Cipher.DECRYPT_MODE, key, Store.unbase64(note.iv),
                    Store.unbase64(note.cipher));
            JSONObject obj = new JSONObject(new String(plain, StandardCharsets.UTF_8));
            Secret secret = new Secret(note.id, obj.optString("title", ""),
                    obj.optString("body", ""), note.updatedAt);
            secret.attachments = Store.parseAttachments(obj.optJSONArray("attachments"));
            return secret;
        } catch (Exception e) {
            return null;
        }
    }

    /** Re-encrypts every note under a new salt/key while the vault is unlocked. */
    public static Store.Vault changePassword(Store.Vault oldVault, Unlocked unlocked,
                                             String newPassword) throws Exception {
        Store.Vault fresh = create(newPassword);
        SecretKey key = derive(newPassword, Store.unbase64(fresh.salt));
        for (Secret s : unlocked.notes) {
            fresh.notes.add(encrypt(s, key));
        }
        return fresh;
    }

    public static SecretKey derive(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] bytes = factory.generateSecret(spec).getEncoded();
        spec.clearPassword();
        return new SecretKeySpec(bytes, "AES");
    }

    private static byte[] crypt(int mode, SecretKey key, byte[] iv, byte[] input) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(128, iv));
        return cipher.doFinal(input);
    }

    private static byte[] random(int size) {
        byte[] bytes = new byte[size];
        RNG.nextBytes(bytes);
        return bytes;
    }

    public static String encryptNoteForExport(Store.PrivateNote note) {
        try {
            JSONObject o = new JSONObject();
            o.put("iv", note.iv);
            o.put("cipher", note.cipher);
            return o.toString();
        } catch (JSONException e) {
            return "";
        }
    }
}
