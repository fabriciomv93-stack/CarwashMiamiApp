package com.carwashmiami.ceramico;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.JSONObject;

public class MainActivity extends Activity {
    WebView webView;
    String pendingExport;
    static final int CREATE_BACKUP = 201, OPEN_BACKUP = 202;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);

        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new Bridge(), "Android");
        webView.loadUrl("file:///android_asset/index.html");
    }

    public class Bridge {
        @JavascriptInterface public boolean checkLogin(String user, String pass){
            SharedPreferences p = getSharedPreferences("ventas_app", MODE_PRIVATE);
            String savedPass = p.getString("pass", "admin");
            return "admin".equals(user) && savedPass.equals(pass);
        }

        @JavascriptInterface public boolean changePassword(String oldPass, String newPass){
            SharedPreferences p = getSharedPreferences("ventas_app", MODE_PRIVATE);
            String current = p.getString("pass", "admin");
            if(!current.equals(oldPass)) return false;
            p.edit().putString("pass", newPass).apply();
            return true;
        }

        @JavascriptInterface public void exportBackup(String json){
            pendingExport = json;

            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");

            i.putExtra(
                Intent.EXTRA_TITLE,
                "Respaldo_Ventas_" +
                new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()) +
                ".json"
            );

            startActivityForResult(i, CREATE_BACKUP);
        }

        @JavascriptInterface public void exitApp(){
            finishAffinity();
        }

        @JavascriptInterface public void importBackup(){
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            startActivityForResult(i, OPEN_BACKUP);
        }

        @JavascriptInterface public void openWhatsApp(String phone, String msg){
            String d = phone == null ? "" : phone.replaceAll("\\D", "");

            if(d.length() == 8)
                d = "506" + d;

            try {
                startActivity(
                    new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(
                            "https://wa.me/" +
                            d +
                            "?text=" +
                            Uri.encode(msg)
                        )
                    )
                );
            } catch(Exception ignored) {}
        }
    }

    @Override protected void onActivityResult(int r, int c, Intent data){
        super.onActivityResult(r, c, data);

        if(c != RESULT_OK || data == null || data.getData() == null)
            return;

        try {
            Uri u = data.getData();

            if(r == CREATE_BACKUP && pendingExport != null){

                try(OutputStream out =
                    getContentResolver().openOutputStream(u)){

                    out.write(
                        pendingExport.getBytes(
                            StandardCharsets.UTF_8
                        )
                    );
                }

                webView.evaluateJavascript(
                    "toast('Respaldo guardado correctamente')",
                    null
                );

                pendingExport = null;

            } else if(r == OPEN_BACKUP){

                StringBuilder sb = new StringBuilder();

                try(BufferedReader br =
                    new BufferedReader(
                        new InputStreamReader(
                            getContentResolver().openInputStream(u),
                            StandardCharsets.UTF_8
                        )
                    )){

                    String l;

                    while((l = br.readLine()) != null)
                        sb.append(l);
                }

                webView.evaluateJavascript(
                    "window.importBackupFromAndroid(" +
                    JSONObject.quote(sb.toString()) +
                    ")",
                    null
                );
            }

        } catch(Exception e){

            webView.evaluateJavascript(
                "toast('No se pudo procesar el respaldo')",
                null
            );
        }
    }

    @Override public void onBackPressed(){

        webView.evaluateJavascript(
            "window.handleAndroidBack && " +
            "window.handleAndroidBack()",
            null
        );
    }
}
