package com.fbclient.app.features;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class AccountManager {
    private static final String PREF = "accounts";
    private static final String KEY = "account_list";
    private static final String KEY_ACTIVE = "active_account";

    public static class Account {
        public String id;
        public String name;
        public String url;
        public String cookieKey; // unique key for cookie store
        public long createdAt;

        public Account(String name, String url) {
            this.id = String.valueOf(System.currentTimeMillis());
            this.name = name;
            this.url = url;
            this.cookieKey = "account_" + id;
            this.createdAt = System.currentTimeMillis();
        }
    }

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public AccountManager(Context ctx) {
        prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public List<Account> getAccounts() {
        String json = prefs.getString(KEY, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<List<Account>>() {}.getType();
        try {
            List<Account> list = gson.fromJson(json, type);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) { return new ArrayList<>(); }
    }

    public Account getActive() {
        String id = prefs.getString(KEY_ACTIVE, null);
        if (id == null) return null;
        for (Account a : getAccounts()) if (a.id.equals(id)) return a;
        return null;
    }

    public void addAccount(Account account) {
        List<Account> list = getAccounts();
        list.add(account);
        save(list);
        if (prefs.getString(KEY_ACTIVE, null) == null) setActive(account.id);
    }

    public void removeAccount(String id) {
        List<Account> list = getAccounts();
        list.removeIf(a -> a.id.equals(id));
        save(list);
    }

    public void setActive(String id) {
        prefs.edit().putString(KEY_ACTIVE, id).apply();
    }

    private void save(List<Account> list) {
        prefs.edit().putString(KEY, gson.toJson(list)).apply();
    }
}
