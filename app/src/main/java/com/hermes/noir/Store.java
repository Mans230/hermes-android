package com.hermes.noir;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Atomic, app-private encrypted storage for both credentials and conversations. */
public final class Store {
    private static Store instance;
    private final AtomicFile file;
    private final SecretKey key;
    private JSONObject data;
    public interface Edit { void apply(JSONObject data) throws Exception; }
    public static synchronized Store get(Context c) throws Exception {
        if (instance == null) instance = new Store(c.getApplicationContext());
        return instance;
    }
    private Store(Context c) throws Exception {
        file = new AtomicFile(new File(c.getFilesDir(), "noir.vault"));
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (!ks.containsAlias("noir-v1")) {
            KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder("noir-v1", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            gen.generateKey();
        }
        key = (SecretKey)ks.getKey("noir-v1", null);
        if (file.getBaseFile().exists()) {
            byte[] bytes = file.readFully();
            if (bytes.length < 29 || bytes[0] != 1) throw new Exception("Unsupported or damaged encrypted storage");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, bytes, 1, 12));
            data = new JSONObject(new String(cipher.doFinal(bytes, 13, bytes.length-13), java.nio.charset.StandardCharsets.UTF_8));
            JSONArray threads = data.getJSONArray("threads");
            for (int i=0;i<threads.length();i++) {
                JSONArray messages = threads.getJSONObject(i).getJSONArray("messages");
                for (int j=0;j<messages.length();j++) {
                    JSONObject m = messages.getJSONObject(j);
                    if (m.optString("status").equals("pending") || m.optString("status").equals("queued")) {
                        m.put("status", "error"); m.put("error", "Interrupted — check the bot before retrying");
                    }
                }
            }
        } else data = new JSONObject().put("bots", new JSONArray()).put("threads", new JSONArray()).put("language", "en");
        if (data.optJSONArray("connections") == null) data.put("connections", new JSONArray());
        if (data.optJSONArray("connections").length() == 0 && data.optJSONObject("server") != null && data.optJSONObject("server").optString("url").length() > 0) {
            data.getJSONArray("connections").put(new JSONObject().put("id", "default").put("name", data.optString("serverName", "server"))
                .put("url", data.optJSONObject("server").optString("url")).put("key", data.optJSONObject("server").optString("key")));
            if (data.optString("activeConnection").isEmpty()) data.put("activeConnection", "default");
        }
    }
    public synchronized JSONObject activeConnection() throws Exception {
        JSONArray conns = data.getJSONArray("connections");
        if (conns.length() == 0) return null;
        String id = data.optString("activeConnection", "");
        for (int i = 0; i < conns.length(); i++) if (conns.getJSONObject(i).optString("id").equals(id)) return conns.getJSONObject(i);
        return conns.getJSONObject(0);
    }
    public synchronized JSONObject read() throws Exception { return new JSONObject(data.toString()); }
    public synchronized void edit(Edit edit) throws Exception {
        JSONObject next = new JSONObject(data.toString()); edit.apply(next);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(next.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        FileOutputStream out = null;
        try {
            out = file.startWrite(); out.write(1); out.write(cipher.getIV()); out.write(encrypted);
            file.finishWrite(out); data = next;
        } catch (Exception e) { if (out != null) file.failWrite(out); throw e; }
    }
    public static JSONObject find(JSONArray rows, String id) throws Exception {
        for (int i=0;i<rows.length();i++) if (rows.getJSONObject(i).optString("id").equals(id)) return rows.getJSONObject(i);
        throw new Exception("Item no longer exists");
    }
    public synchronized String createGroup(JSONArray botIds,String title)throws Exception {
        if(botIds.length()<2 || botIds.length()>6)throw new Exception("Choose 2–6 agents");
        String id=id();
        edit(d->d.getJSONArray("threads").put(new JSONObject().put("id",id).put("botIds",botIds)
            .put("title",title).put("updated",System.currentTimeMillis()).put("messages",new JSONArray())));
        return id;
    }
    public static String id() { return UUID.randomUUID().toString(); }
    public synchronized String createThread(String botId, String title) throws Exception {
        String id = id();
        edit(d->d.getJSONArray("threads").put(new JSONObject().put("id",id).put("botId",botId)
            .put("title",title).put("updated",System.currentTimeMillis()).put("messages",new JSONArray())));
        return id;
    }
}
