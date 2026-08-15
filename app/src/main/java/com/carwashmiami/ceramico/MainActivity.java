package com.carwashmiami.ceramico;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.provider.ContactsContract;
import android.content.ContentValues;
import android.webkit.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.JSONObject;

public class MainActivity extends Activity {
    WebView webView;
    String pendingExport;
    static final int CREATE_BACKUP=201, OPEN_BACKUP=202, PICK_CONTACT=203;
    String pendingContactTarget="";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT>=33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},99);
        }
        channel();
        webView=new WebView(this);
        setContentView(webView);
        WebSettings s=webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new Bridge(),"Android");
        webView.loadUrl("file:///android_asset/index.html");
    }

    void channel() {
        if(Build.VERSION.SDK_INT>=26) {
            NotificationChannel c=new NotificationChannel(
                "ceramico_reminders","Recordatorios de acabado cerámico",
                NotificationManager.IMPORTANCE_HIGH
            );
            c.enableVibration(true);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
    }

    String hash(String value) {
        try {
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            byte[] bytes=md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb=new StringBuilder();
            for(byte x:bytes) sb.append(String.format(Locale.US,"%02x",x));
            return sb.toString();
        } catch(Exception e) { return value; }
    }

    boolean installed(String pkg) {
        try { getPackageManager().getPackageInfo(pkg,0); return true; }
        catch(Exception e) { return false; }
    }

    public class Bridge {
        @JavascriptInterface public void scheduleReminder(String id,String name,String plate,String date) {
            schedule(MainActivity.this,id,name,plate,date);
        }
        @JavascriptInterface public void cancelReminder(String id) {
            AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);
            Intent i=new Intent(MainActivity.this,ReminderReceiver.class);
            PendingIntent pi=PendingIntent.getBroadcast(MainActivity.this,id.hashCode(),i,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            am.cancel(pi);
        }
        @JavascriptInterface public void syncData(String j) {
            getSharedPreferences("cm_backup",MODE_PRIVATE).edit().putString("clients",j).apply();
        }
        @JavascriptInterface public void exportBackup(String j) {
            pendingExport=j;
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE,"CarwashMiami_Respaldo_"+
                new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date())+".json");
            startActivityForResult(i,CREATE_BACKUP);
        }
        @JavascriptInterface public void importBackup() {
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            startActivityForResult(i,OPEN_BACKUP);
        }
        @JavascriptInterface public void openWhatsApp(String phone,String msg) {
            runOnUiThread(() -> {
                try {
                    String d=phone==null?"":phone.replaceAll("\\D","");
                    if(d.length()==8)d="506"+d;
                    Uri uri=Uri.parse("https://wa.me/"+d+"?text="+Uri.encode(msg==null?"":msg));
                    Intent i=new Intent(Intent.ACTION_VIEW,uri);
                    if(installed("com.whatsapp.w4b")) i.setPackage("com.whatsapp.w4b");
                    else if(installed("com.whatsapp")) i.setPackage("com.whatsapp");
                    startActivity(i);
                } catch(Exception e) {
                    try {
                        String d=phone==null?"":phone.replaceAll("\\D","");
                        if(d.length()==8)d="506"+d;
                        startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+d)));
                    } catch(Exception ignored) {}
                }
            });
        }

        @JavascriptInterface public void autoBackup(String j) {
            runOnUiThread(() -> {
                try {
                    String name="CarwashMiami_Auto_"+new SimpleDateFormat("yyyy-MM-dd_HHmm",Locale.US).format(new Date())+".json";
                    if(Build.VERSION.SDK_INT>=29) {
                        ContentValues v=new ContentValues();
                        v.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
                        v.put(MediaStore.MediaColumns.MIME_TYPE,"application/json");
                        v.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOCUMENTS+"/CarwashMiami");
                        Uri u=getContentResolver().insert(MediaStore.Files.getContentUri("external"),v);
                        if(u==null) throw new IOException("backup");
                        try(OutputStream out=getContentResolver().openOutputStream(u)) { out.write(j.getBytes(StandardCharsets.UTF_8)); }
                    } else {
                        File dir=new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),"CarwashMiami");
                        if(!dir.exists())dir.mkdirs();
                        try(OutputStream out=new FileOutputStream(new File(dir,name))) { out.write(j.getBytes(StandardCharsets.UTF_8)); }
                    }
                    jsToast("Respaldo automático guardado");
                } catch(Exception e) { jsToast("Cierre guardado; no se pudo crear el respaldo automático"); }
            });
        }

        @JavascriptInterface public void pickContact(String target) {
            pendingContactTarget=target==null?"":target;
            runOnUiThread(() -> startActivityForResult(new Intent(Intent.ACTION_PICK,ContactsContract.CommonDataKinds.Phone.CONTENT_URI),PICK_CONTACT));
        }
        @JavascriptInterface public void saveContact(String name,String phone) {
            runOnUiThread(() -> {
                try {
                    Intent i=new Intent(Intent.ACTION_INSERT,ContactsContract.Contacts.CONTENT_URI);
                    i.putExtra(ContactsContract.Intents.Insert.NAME,name);
                    i.putExtra(ContactsContract.Intents.Insert.PHONE,phone);
                    startActivity(i);
                } catch(Exception e){jsToast("No se pudo abrir Contactos");}
            });
        }
        @JavascriptInterface public void scheduleAppointment(String id,String name,String plate,String service,String date,String time) {
            scheduleAppointmentAlarm(MainActivity.this,id,name,plate,service,date,time);
        }
        @JavascriptInterface public void cancelAppointment(String id) { cancelAppointmentAlarm(MainActivity.this,id); }

        @JavascriptInterface public boolean checkLogin(String user,String pass) {
            SharedPreferences sp=getSharedPreferences("cm_security",MODE_PRIVATE);
            String savedUser=sp.getString("user","admin");
            String savedHash=sp.getString("pass_hash",hash("admin"));
            return savedUser.equals(user) && savedHash.equals(hash(pass));
        }
        @JavascriptInterface public boolean changePassword(String oldPass,String newPass) {
            if(newPass==null || newPass.length()<4) return false;
            SharedPreferences sp=getSharedPreferences("cm_security",MODE_PRIVATE);
            String savedHash=sp.getString("pass_hash",hash("admin"));
            if(!savedHash.equals(hash(oldPass))) return false;
            sp.edit().putString("user","admin").putString("pass_hash",hash(newPass)).apply();
            return true;
        }
        @JavascriptInterface public void exitApp() {
            runOnUiThread(() -> {
                if(Build.VERSION.SDK_INT>=21) finishAndRemoveTask();
                else finish();
            });
        }
    }

    @Override public void onBackPressed() {
        if(webView!=null) {
            webView.evaluateJavascript(
                "if(window.handleAndroidBack){window.handleAndroidBack();}else if(window.Android&&Android.exitApp){Android.exitApp();}",
                null
            );
        } else super.onBackPressed();
    }

    @Override protected void onActivityResult(int r,int c,Intent data) {
        super.onActivityResult(r,c,data);
        if(c!=RESULT_OK||data==null||data.getData()==null)return;
        try {
            Uri u=data.getData();
            if(r==PICK_CONTACT) {
                String name="",phone="";
                android.database.Cursor cur=getContentResolver().query(u,new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER},null,null,null);
                if(cur!=null){if(cur.moveToFirst()){name=cur.getString(0);phone=cur.getString(1);}cur.close();}
                webView.evaluateJavascript("window.onContactPicked("+JSONObject.quote(pendingContactTarget)+","+JSONObject.quote(name)+","+JSONObject.quote(phone)+")",null);
                pendingContactTarget=""; return;
            }
            if(r==CREATE_BACKUP&&pendingExport!=null) {
                try(OutputStream out=getContentResolver().openOutputStream(u)) {
                    out.write(pendingExport.getBytes(StandardCharsets.UTF_8));
                }
                jsToast("Respaldo guardado correctamente");
                pendingExport=null;
            } else if(r==OPEN_BACKUP) {
                StringBuilder sb=new StringBuilder();
                try(BufferedReader br=new BufferedReader(
                    new InputStreamReader(getContentResolver().openInputStream(u),StandardCharsets.UTF_8))) {
                    String l; while((l=br.readLine())!=null) sb.append(l);
                }
                webView.evaluateJavascript(
                    "window.importBackupFromAndroid("+JSONObject.quote(sb.toString())+")",null);
            }
        } catch(Exception e) { jsToast("No se pudo procesar el respaldo"); }
    }

    void jsToast(String m) {
        webView.evaluateJavascript("toast("+JSONObject.quote(m)+")",null);
    }

    public static void cancelAppointmentAlarm(Context ctx,String id){
        try{Intent i=new Intent(ctx,ReminderReceiver.class);PendingIntent pi=PendingIntent.getBroadcast(ctx,("appt_"+id).hashCode(),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);((AlarmManager)ctx.getSystemService(ALARM_SERVICE)).cancel(pi);}catch(Exception ignored){}
    }
    public static void scheduleAppointmentAlarm(Context ctx,String id,String name,String plate,String service,String date,String time){
        try{
            cancelAppointmentAlarm(ctx,id);String[]d=date.split("-"),t=time.split(":");Calendar cal=Calendar.getInstance();
            cal.set(Integer.parseInt(d[0]),Integer.parseInt(d[1])-1,Integer.parseInt(d[2]),Integer.parseInt(t[0]),Integer.parseInt(t[1]),0);cal.set(Calendar.MILLISECOND,0);cal.add(Calendar.MINUTE,-20);
            if(cal.getTimeInMillis()<=System.currentTimeMillis())return;
            Intent i=new Intent(ctx,ReminderReceiver.class);i.putExtra("type","appointment");i.putExtra("id","appt_"+id);i.putExtra("name",name);i.putExtra("plate",plate);i.putExtra("service",service);i.putExtra("appointmentTime",time);
            PendingIntent pi=PendingIntent.getBroadcast(ctx,("appt_"+id).hashCode(),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            ((AlarmManager)ctx.getSystemService(ALARM_SERVICE)).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,cal.getTimeInMillis(),pi);
        }catch(Exception ignored){}
    }

    public static void schedule(Context ctx,String id,String name,String plate,String iso) {
        try {
            String[]p=iso.split("-");
            Calendar cal=Calendar.getInstance();
            cal.set(Integer.parseInt(p[0]),Integer.parseInt(p[1])-1,Integer.parseInt(p[2]),9,0,0);
            cal.set(Calendar.MILLISECOND,0);
            if(cal.getTimeInMillis()<=System.currentTimeMillis())
                cal.setTimeInMillis(System.currentTimeMillis()+15000);
            Intent i=new Intent(ctx,ReminderReceiver.class);
            i.putExtra("name",name); i.putExtra("plate",plate); i.putExtra("id",id);
            PendingIntent pi=PendingIntent.getBroadcast(ctx,id.hashCode(),i,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            ((AlarmManager)ctx.getSystemService(ALARM_SERVICE))
                .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,cal.getTimeInMillis(),pi);
        } catch(Exception ignored) {}
    }
}
