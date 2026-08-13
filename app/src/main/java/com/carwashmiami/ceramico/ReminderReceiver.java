package com.carwashmiami.ceramico;
import android.app.*;import android.content.*;
public class ReminderReceiver extends BroadcastReceiver{
 public void onReceive(Context c,Intent i){String n=i.getStringExtra("name"),p=i.getStringExtra("plate"),id=i.getStringExtra("id");Intent o=new Intent(c,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(c,0,o,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  Notification.Builder b=android.os.Build.VERSION.SDK_INT>=26?new Notification.Builder(c,"ceramico_reminders"):new Notification.Builder(c);
  b.setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("Carwash Miami • Renovación cerámica").setContentText((n==null?"Cliente":n)+" · "+(p==null?"":p)).setStyle(new Notification.BigTextStyle().bigText("Hoy corresponde contactar a "+(n==null?"este cliente":n)+" para la renovación del acabado cerámico"+(p==null||p.isEmpty()?".":" del vehículo "+p+"."))).setAutoCancel(true).setContentIntent(pi).setPriority(Notification.PRIORITY_HIGH);
  ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(id==null?(int)System.currentTimeMillis():id.hashCode(),b.build());
 }}
