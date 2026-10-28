package de.myapphub
import android.app.Activity
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
   if(apps.none{it.repo==repo})return
   if(!url.startsWith("https://github.com/$owner/$repo/releases/download/"))return
   io.execute{
    try{
     callback("notify","Lade APK für $repo …")
     val target=File(cacheDir,"apks/$repo.apk")
     target.parentFile?.mkdirs()
     val connection=(URL(url).openConnection() as HttpURLConnection).apply{
      connectTimeout=20000;readTimeout=30000;instanceFollowRedirects=true
     }
     try{
      if(connection.responseCode !in 200..299)error("Download HTTP ${connection.responseCode}")
      connection.inputStream.use{src->target.outputStream().use{dst->src.copyTo(dst)}}
     }finally{connection.disconnect()}
     if(target.length()<1024)error("Datei ist keine APK")
     runOnUiThread{
      val uri=FileProvider.getUriForFile(this@MainActivity,"$packageName.files",target)
      val intent=Intent(Intent.ACTION_VIEW).apply{
       setDataAndType(uri,"application/vnd.android.package-archive")
       addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      try{startActivity(intent)}catch(e:Exception){callback("notify","Installation nicht gestartet: ${e.message}")}
     }
    }catch(e:Exception){callback("notify","Download fehlgeschlagen: ${e.message}")}
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
    val releases=JSONArray(readJson("https://api.github.com/repos/$owner/${app.repo}/releases?per_page=10"))
    var apk="";var tag=""
    for(i in 0 until releases.length()){
     val release=releases.getJSONObject(i)
     if(release.optBoolean("draft")||release.optBoolean("prerelease"))continue
     val assets=release.optJSONArray("assets")?:continue
     for(j in 0 until assets.length()){
      val asset=assets.getJSONObject(j)
      if(asset.optString("name").endsWith(".apk",true)){
       apk=asset.optString("browser_download_url")
       tag=release.optString("tag_name")
       break
      }
     }
     if(apk.isNotBlank())break
    }
    item.put("apk",apk).put("tag",tag)
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
 override fun onDestroy(){io.shutdownNow();web.destroy();super.onDestroy()}
}
