package de.myapphub
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class InstallReceiver:BroadcastReceiver(){
 override fun onReceive(context:Context,intent:Intent){
  val status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE)
  if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){
   val confirm=if(Build.VERSION.SDK_INT>=33)intent.getParcelableExtra(PackageInstaller.EXTRA_INTENT,Intent::class.java)
     else intent.getParcelableExtra<Intent>(PackageInstaller.EXTRA_INTENT)
   confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
   if(confirm!=null)context.startActivity(confirm)
   return
  }
  val repo=intent.getStringExtra("repo")?:""
  File(context.cacheDir,"apks/$repo.apk").delete()
  val msg=if(status==PackageInstaller.STATUS_SUCCESS)repo+" erfolgreich installiert."
    else if(status==PackageInstaller.STATUS_FAILURE_ABORTED)"Installation abgebrochen."
    else "Installation fehlgeschlagen: "+(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?:"Unbekannt")
  context.getSharedPreferences("hub",Context.MODE_PRIVATE).edit().putString("lastInstall",msg).apply()
 }
}
class MainActivity:Activity(){
 private lateinit var web:WebView
 private val io=Executors.newSingleThreadExecutor()
 private val owner="NoMercy0805"
 private data class App(val repo:String,val pkg:String,val description:String)
 private val apps=listOf(
  App("CarService","com.schiwy.carservice","Fahrzeuge & Wartung"),
  App("Haushaltsbuch","com.schiwy.haushaltsbuch","Finanzen & Budgets"),
  App("Arbeitszeit","com.schiwy.arbeitszeit","Arbeitszeit & Urlaub"),
  App("GalerieSync","com.schiwy.galeriesync","Fotos & NAS-Synchronisation"),
  App("Vertraege","com.schiwy.vertraege","Verträge & Fristen"),
  App("Rauchfrei","com.schiwy.rauchfrei","Rauchstopp-Begleiter"),
  App("IronLog","com.schiwy.ironlog","Training & Fortschritt")
 )
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  File(cacheDir,"apks").listFiles()?.forEach{if(it.isFile)it.delete()}
  web=WebView(this).apply{
   settings.javaScriptEnabled=true
   settings.domStorageEnabled=true
   webViewClient=WebViewClient()
   addJavascriptInterface(Bridge(),"Hub")
   loadUrl("file:///android_asset/index.html")
  }
  setContentView(web)
 }
 inner class Bridge{
  @JavascriptInterface fun refresh(){io.execute{refreshApps()}}
  @JavascriptInterface fun open(url:String){
   if(!url.startsWith("https://github.com/$owner/"))return
   runOnUiThread{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}
  }
  @JavascriptInterface fun install(repo:String,url:String){
   val app=apps.find{it.repo==repo}?:return
   val uri=Uri.parse(url)
   if(uri.scheme!="https"||uri.host!="github.com"||!uri.path.orEmpty().startsWith("/$owner/$repo/releases/download/"))return
   io.execute{
    val target=File(cacheDir,"apks/$repo.apk")
    try{
     callback("notify","Lade und prüfe "+repo+" …")
     target.parentFile?.mkdirs()
     val c=(URL(url).openConnection() as HttpURLConnection).apply{
      connectTimeout=20000;readTimeout=30000
      setRequestProperty("User-Agent","MyAppHub-Android")
     }
     try{
      if(c.responseCode !in 200..299)error("Download HTTP "+c.responseCode)
      c.inputStream.use{src->target.outputStream().use{dst->src.copyTo(dst)}}
     }finally{c.disconnect()}
     if(target.length()<1024)error("APK ist unvollständig")
     val archive=packageManager.getPackageArchiveInfo(target.absolutePath,PackageManager.GET_SIGNING_CERTIFICATES)
       ?:error("Keine gültige APK")
     if(archive.packageName!=app.pkg)error("Paketname stimmt nicht überein")
     val installed=try{packageManager.getPackageInfo(app.pkg,PackageManager.GET_SIGNING_CERTIFICATES)}
       catch(_:PackageManager.NameNotFoundException){null}
     val newCode=if(Build.VERSION.SDK_INT>=28)archive.longVersionCode else archive.versionCode.toLong()
     val oldCode=if(installed==null)0L else if(Build.VERSION.SDK_INT>=28)installed.longVersionCode else installed.versionCode.toLong()
     if(installed!=null&&newCode<=oldCode)error("Kein neueres Update: "+oldCode+" / "+newCode)
     if(installed!=null&&Build.VERSION.SDK_INT>=28){
      val oldSig=installed.signingInfo?.apkContentsSigners?.map{it.toCharsString()}?.toSet()
      val newSig=archive.signingInfo?.apkContentsSigners?.map{it.toCharsString()}?.toSet()
      if(oldSig.isNullOrEmpty()||newSig.isNullOrEmpty()||oldSig!=newSig)
       error("Signatur stimmt nicht überein. Update gestoppt.")
     }
     val installer=packageManager.packageInstaller
     val params=PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
     params.setAppPackageName(app.pkg)
     val sessionId=installer.createSession(params)
     try{
      installer.openSession(sessionId).use{session->
       target.inputStream().use{input->
        session.openWrite("base.apk",0,target.length()).use{output->
         input.copyTo(output)
         session.fsync(output)
        }
       }
       val pending=PendingIntent.getBroadcast(this@MainActivity,sessionId,
        Intent(this@MainActivity,InstallReceiver::class.java).putExtra("repo",repo),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
       session.commit(pending.intentSender)
      }
     }catch(e:Exception){installer.abandonSession(sessionId);throw e}
     callback("notify","Android-Installationsbestätigung öffnen …")
    }catch(e:Exception){
     target.delete()
     callback("notify",repo+": "+(e.message?:"Installation fehlgeschlagen"))
    }
   }
  }
 }
 private fun readJson(url:String):String{
  val c=(URL(url).openConnection() as HttpURLConnection).apply{
   setRequestProperty("Accept","application/vnd.github+json")
   setRequestProperty("User-Agent","MyAppHub-Android")
   connectTimeout=12000;readTimeout=12000
  }
  return try{
   if(c.responseCode !in 200..299)error("GitHub HTTP ${c.responseCode}")
   c.inputStream.bufferedReader().use{it.readText()}
  }finally{c.disconnect()}
 }
 private fun refreshApps(){
  val result=JSONArray()
  for(app in apps){
   val item=JSONObject().put("repo",app.repo).put("description",app.description)
   try{
    @Suppress("DEPRECATION")
    val installed=try{packageManager.getPackageInfo(app.pkg,0)}catch(_:Exception){null}
    item.put("installed",installed!=null)
    item.put("installedVersion",installed?.versionName?:"")
    val installedCode=if(installed==null)0L else if(Build.VERSION.SDK_INT>=28)installed.longVersionCode else installed.versionCode.toLong()
    item.put("installedCode",installedCode)
    val releases=JSONArray(readJson("https://api.github.com/repos/$owner/${app.repo}/releases?per_page=10"))
    var apk="";var tag="";var remoteCode=0L
    for(i in 0 until releases.length()){
     val release=releases.getJSONObject(i)
     if(release.optBoolean("draft")||release.optBoolean("prerelease"))continue
     val assets=release.optJSONArray("assets")?:continue
     val code=Regex("""(?im)^Android-versionCode:\s*(\d+)\s*$""").find(release.optString("body"))?.groupValues?.get(1)?.toLongOrNull()?:0L
     for(j in 0 until assets.length()){
      val asset=assets.getJSONObject(j)
      if(asset.optString("name").endsWith(".apk",true)){
       apk=asset.optString("browser_download_url")
       tag=release.optString("tag_name")
       remoteCode=code
       break
      }
     }
     if(apk.isNotBlank())break
    }
    item.put("apk",apk).put("tag",tag).put("remoteCode",remoteCode)
    item.put("update",installed!=null&&remoteCode>installedCode)
    item.put("versionKnown",remoteCode>0L)
    if(apk.isBlank()){
     val runs=JSONObject(readJson("https://api.github.com/repos/$owner/${app.repo}/actions/runs?status=success&per_page=1")).optJSONArray("workflow_runs")
     item.put("actions",if(runs!=null&&runs.length()>0)runs.getJSONObject(0).optString("html_url") else "https://github.com/$owner/${app.repo}/actions")
    }
   }catch(e:Exception){item.put("error",e.message?:"Unbekannter Fehler")}
   result.put(item)
  }
  callback("render",result.toString())
 }
 private fun callback(fn:String,value:String){
  runOnUiThread{web.evaluateJavascript("window.$fn(${JSONObject.quote(value)})",null)}
 }
 override fun onResume(){
  super.onResume()
  if(::web.isInitialized){
   val p=getSharedPreferences("hub",MODE_PRIVATE)
   val message=p.getString("lastInstall","").orEmpty()
   if(message.isNotBlank()){
    p.edit().remove("lastInstall").apply()
    callback("notify",message)
    io.execute{refreshApps()}
   }
  }
 }
 override fun onDestroy(){io.shutdownNow();web.destroy();super.onDestroy()}
}
