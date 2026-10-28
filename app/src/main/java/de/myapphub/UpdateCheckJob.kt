package de.myapphub

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class UpdateCheckJob:JobService(){
 private val apps=listOf(
  "CarService" to "com.schiwy.carservice",
  "Haushaltsbuch" to "com.schiwy.haushaltsbuch",
  "Arbeitszeit" to "com.schiwy.arbeitszeit",
  "GalerieSync" to "com.schiwy.galeriesync",
  "Vertraege" to "com.schiwy.vertraege",
  "Rauchfrei" to "com.schiwy.rauchfrei",
  "IronLog" to "com.schiwy.ironlog"
 )
 override fun onStartJob(params:JobParameters):Boolean{
  Thread({
   try{
    val available=mutableListOf<String>()
    for((name,pkg) in apps){
     val installed=try{packageManager.getPackageInfo(pkg,0)}catch(_:PackageManager.NameNotFoundException){null}
     if(installed==null)continue
     val installedCode=if(Build.VERSION.SDK_INT>=28)installed.longVersionCode else installed.versionCode.toLong()
     val connection=(URL("https://api.github.com/repos/NoMercy0805/"+name+"/releases?per_page=5").openConnection() as HttpURLConnection).apply{
      setRequestProperty("User-Agent","MyAppHub-Android")
      connectTimeout=10000
      readTimeout=10000
     }
     try{
      if(connection.responseCode !in 200..299)continue
      val arr=JSONArray(connection.inputStream.bufferedReader().use{it.readText()})
      for(i in 0 until arr.length()){
       val release=arr.getJSONObject(i)
       if(release.optBoolean("draft")||release.optBoolean("prerelease"))continue
       val code=Regex("""(?im)^Android-versionCode:\s*(\d+)\s*$""").find(release.optString("body"))?.groupValues?.get(1)?.toLongOrNull()?:0L
       val assets=release.optJSONArray("assets")
       val hasApk=(0 until (assets?.length()?:0)).any{assets!!.getJSONObject(it).optString("name").endsWith(".apk",true)}
       if(hasApk&&code>0){
        if(code>installedCode)available.add(name)
        break
       }
      }
     }finally{connection.disconnect()}
    }
    if(available.isNotEmpty())notifyUpdates(available)
   }catch(_:Exception){}
   finally{jobFinished(params,false)}
  },"MyAppHub-update-check").start()
  return true
 }
 override fun onStopJob(params:JobParameters)=false
 private fun notifyUpdates(names:List<String>){
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
  val nm=getSystemService(NOTIFICATION_SERVICE) as NotificationManager
  nm.createNotificationChannel(NotificationChannel("updates","App-Updates",NotificationManager.IMPORTANCE_DEFAULT))
  val pending=PendingIntent.getActivity(this,8,Intent(this,MainActivity::class.java),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val text=names.joinToString(", ")
  val n=Notification.Builder(this,"updates")
   .setSmallIcon(android.R.drawable.stat_sys_download_done)
   .setContentTitle(if(names.size==1)"1 App-Update verfügbar" else names.size.toString()+" App-Updates verfügbar")
   .setContentText(text)
   .setStyle(Notification.BigTextStyle().bigText(text))
   .setContentIntent(pending).setAutoCancel(true).build()
  nm.notify(51,n)
 }
}
