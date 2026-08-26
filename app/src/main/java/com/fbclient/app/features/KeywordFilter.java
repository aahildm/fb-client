package com.fbclient.app.features;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class KeywordFilter {
    private static final String PREF = "keyword_filter";
    private static final String KEY = "keywords";

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public KeywordFilter(Context ctx) {
        prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public List<String> getKeywords() {
        String json = prefs.getString(KEY, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<List<String>>() {}.getType();
        try {
            List<String> list = gson.fromJson(json, type);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) { return new ArrayList<>(); }
    }

    public void addKeyword(String keyword) {
        List<String> list = getKeywords();
        if (!list.contains(keyword.toLowerCase().trim())) {
            list.add(keyword.toLowerCase().trim());
            save(list);
        }
    }

    public void removeKeyword(String keyword) {
        List<String> list = getKeywords();
        list.remove(keyword.toLowerCase().trim());
        save(list);
    }

    /** Generate JS that hides posts containing any blocked keyword */
    public String generateJS() {
        List<String> keywords = getKeywords();
        if (keywords.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("(function(){");
        sb.append("var kw=[");
        for (int i = 0; i < keywords.size(); i++) {
            sb.append("'").append(keywords.get(i).replace("'", "\\'")).append("'");
            if (i < keywords.size() - 1) sb.append(",");
        }
        sb.append("];");
        sb.append("var feed=document.querySelector('div[role=\"feed\"]');");
        sb.append("if(!feed)return;");
        sb.append("Array.from(feed.children).forEach(function(item){");
        sb.append("  var txt=item.textContent.toLowerCase();");
        sb.append("  for(var i=0;i<kw.length;i++){");
        sb.append("    if(txt.indexOf(kw[i])>=0){");
        sb.append("      item.style.setProperty('display','none','important');");
        sb.append("      break;");
        sb.append("    }");
        sb.append("  }");
        sb.append("});");
        sb.append("})();");
        return sb.toString();
    }

    private void save(List<String> list) {
        prefs.edit().putString(KEY, gson.toJson(list)).apply();
    }
}
