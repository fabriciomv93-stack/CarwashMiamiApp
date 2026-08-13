package com.carwashmiami.ceramico;
import android.content.*;import org.json.*;
public class BootReceiver extends BroadcastReceiver{public void onReceive(Context c,Intent i){String raw=c.getSharedPreferences("cm_backup",Context.MODE_PRIVATE).getString("clients","[]");try{JSONArray a=new JSONArray(raw);for(int x=0;x<a.length();x++){JSONObject o=a.getJSONObject(x);if(!o.optBoolean("completed",false))MainActivity.schedule(c,o.optString("id"),o.optString("name"),o.optString("plate"),o.optString("renewalDate"));}}catch(Exception ignored){}}}
