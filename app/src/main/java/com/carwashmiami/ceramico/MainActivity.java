package com.carwashmiami.ceramico;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.provider.ContactsContract;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
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
    static final int CREATE_BACKUP=201, OPEN_BACKUP=202, PICK_CONTACT=203, PICK_RECEIPT=204, CREATE_MONTHLY_PDF=205;
    String pendingContactTarget="";
    String pendingMonthlyPdf="";

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


        @JavascriptInterface public void pickReceipt() {
            runOnUiThread(() -> {
                Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                startActivityForResult(i,PICK_RECEIPT);
            });
        }

        @JavascriptInterface public void exportMonthlyPdf(String json) {
            pendingMonthlyPdf=json==null?"":json;
            runOnUiThread(() -> {
                Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/pdf");
                i.putExtra(Intent.EXTRA_TITLE,"CarwashMiami_Reporte_Mensual.pdf");
                startActivityForResult(i,CREATE_MONTHLY_PDF);
            });
        }

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
            if(r==PICK_RECEIPT) {
                try {
                    String ext=".jpg";
                    String typ=getContentResolver().getType(u);
                    if(typ!=null&&typ.contains("png"))ext=".png";
                    File dir=new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES),"CarwashMiamiReceipts");
                    if(!dir.exists())dir.mkdirs();
                    File dst=new File(dir,"recibo_"+System.currentTimeMillis()+ext);
                    try(InputStream in=getContentResolver().openInputStream(u);OutputStream out=new FileOutputStream(dst)){
                        byte[]buf=new byte[8192];int n;while((n=in.read(buf))>0)out.write(buf,0,n);
                    }
                    webView.evaluateJavascript("window.onReceiptPicked("+JSONObject.quote(dst.getAbsolutePath())+")",null);
                } catch(Exception e){jsToast("No se pudo guardar el comprobante");}
                return;
            }
            if(r==CREATE_MONTHLY_PDF&&pendingMonthlyPdf!=null&&!pendingMonthlyPdf.isEmpty()) {
                try(OutputStream out=getContentResolver().openOutputStream(u)){
                    writeMonthlyPdf(out,pendingMonthlyPdf);
                }
                pendingMonthlyPdf="";jsToast("Reporte PDF guardado");return;
            }
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


    void writeMonthlyPdf(OutputStream out,String raw) throws Exception {
        org.json.JSONObject d=new org.json.JSONObject(raw);
        PdfDocument pdf=new PdfDocument();
        Paint p=new Paint(1);Paint line=new Paint(1);
        int W=595,H=842,y=48,pageNo=1;
        PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(W,H,pageNo).create());
        Canvas c=page.getCanvas();
        p.setColor(Color.rgb(8,36,54));c.drawRect(0,0,W,72,p);
        p.setColor(Color.WHITE);p.setTextSize(22);p.setTypeface(Typeface.DEFAULT_BOLD);c.drawText("CARWASH MIAMI",32,42,p);
        p.setTextSize(12);p.setTypeface(Typeface.DEFAULT);c.drawText("Reporte mensual "+d.optString("month"),32,60,p);y=100;
        String[] labs={"Ventas registradas","Cobrado","Pendiente","Gastos pagados","Resultado operativo","Ticket promedio"};
        double[] vals={d.optDouble("sales"),d.optDouble("collected"),d.optDouble("pending"),d.optDouble("expenses"),d.optDouble("result"),d.optDouble("ticket")};
        p.setTextSize(11);p.setColor(Color.DKGRAY);
        for(int i=0;i<labs.length;i++){p.setTypeface(Typeface.DEFAULT_BOLD);c.drawText(labs[i],32,y,p);p.setTypeface(Typeface.DEFAULT);c.drawText("CRC "+String.format(Locale.US,"%,.0f",vals[i]),210,y,p);y+=22;}
        y+=10;p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(13);c.drawText("Formas de pago",32,y,p);y+=18;
        org.json.JSONObject pay=d.optJSONObject("payment");double cash=pay==null?0:pay.optDouble("cash"),card=pay==null?0:pay.optDouble("card"),sinpe=pay==null?0:pay.optDouble("sinpe"),tot=Math.max(1,cash+card+sinpe);
        Paint arc=new Paint(1);RectF oval=new RectF(32,y,132,y+100);float st=-90;
        int[] cols={Color.rgb(53,230,149),Color.rgb(59,216,255),Color.rgb(192,121,255)};double[] pv={cash,card,sinpe};
        for(int i=0;i<3;i++){arc.setColor(cols[i]);float sweep=(float)(pv[i]/tot*360);c.drawArc(oval,st,sweep,true,arc);st+=sweep;}
        p.setTextSize(10);p.setColor(Color.DKGRAY);c.drawText("Efectivo "+String.format(Locale.US,"%,.0f",cash),150,y+25,p);c.drawText("Tarjeta "+String.format(Locale.US,"%,.0f",card),150,y+50,p);c.drawText("SINPE "+String.format(Locale.US,"%,.0f",sinpe),150,y+75,p);y+=125;
        org.json.JSONObject services=d.optJSONObject("services");if(services!=null){p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(13);c.drawText("Ventas estimadas por servicio",32,y,p);y+=18;
            java.util.Iterator<String> it=services.keys();java.util.ArrayList<String> ks=new java.util.ArrayList<>();double mx=1;while(it.hasNext()){String k=it.next();ks.add(k);mx=Math.max(mx,services.optDouble(k));}
            p.setTypeface(Typeface.DEFAULT);p.setTextSize(9);for(String k:ks){if(y>760){pdf.finishPage(page);pageNo++;page=pdf.startPage(new PdfDocument.PageInfo.Builder(W,H,pageNo).create());c=page.getCanvas();y=50;}double v=services.optDouble(k);p.setColor(Color.DKGRAY);c.drawText(k,32,y,p);Paint bp=new Paint(1);bp.setColor(Color.rgb(53,201,255));c.drawRect(150,y-8,150+(float)(260*v/mx),y,bp);c.drawText("CRC "+String.format(Locale.US,"%,.0f",v),430,y,p);y+=18;}}
        y+=10;p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(13);p.setColor(Color.DKGRAY);c.drawText("Detalle de movimientos",32,y,p);y+=18;p.setTypeface(Typeface.DEFAULT);p.setTextSize(8);
        org.json.JSONArray rec=d.optJSONArray("records");if(rec!=null)for(int i=0;i<rec.length();i++){if(y>790){pdf.finishPage(page);pageNo++;page=pdf.startPage(new PdfDocument.PageInfo.Builder(W,H,pageNo).create());c=page.getCanvas();y=45;}org.json.JSONObject r=rec.getJSONObject(i);String s=r.optString("date")+"  "+r.optString("name")+"  "+r.optString("plate")+"  CRC "+String.format(Locale.US,"%,.0f",r.optDouble("amount"));c.drawText(s,32,y,p);y+=14;}
        pdf.finishPage(page);pdf.writeTo(out);pdf.close();
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
