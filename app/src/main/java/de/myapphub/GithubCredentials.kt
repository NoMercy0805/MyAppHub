package de.myapphub

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object GithubCredentials {
 private const val ALIAS="myapphub.github.token.v1"
 private fun secret():SecretKey {
  val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
  val existing=store.getKey(ALIAS,null) as? SecretKey
  if(existing!=null)return existing
  val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
  generator.init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
   .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
   .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
   .setRandomizedEncryptionRequired(true).build())
  return generator.generateKey()
 }
 fun save(context:Context,token:String) {
  val value=token.trim()
  require(value.length in 16..512 && !value.contains('\n')){"Ungültiges Tokenformat"}
  val cipher=Cipher.getInstance("AES/GCM/NoPadding")
  cipher.init(Cipher.ENCRYPT_MODE,secret())
  val encrypted=cipher.doFinal(value.toByteArray(Charsets.UTF_8))
  val pref=context.getSharedPreferences("github_credentials",Context.MODE_PRIVATE)
  pref.edit().putString("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP))
   .putString("value",Base64.encodeToString(encrypted,Base64.NO_WRAP)).apply()
 }
 fun get(context:Context):String? {
  val p=context.getSharedPreferences("github_credentials",Context.MODE_PRIVATE)
  val iv=p.getString("iv",null)?:return null
  val data=p.getString("value",null)?:return null
  return try {
   val cipher=Cipher.getInstance("AES/GCM/NoPadding")
   cipher.init(Cipher.DECRYPT_MODE,secret(),GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)))
   String(cipher.doFinal(Base64.decode(data,Base64.NO_WRAP)),Charsets.UTF_8)
  } catch(_:Exception){null}
 }
 fun clear(context:Context){context.getSharedPreferences("github_credentials",Context.MODE_PRIVATE).edit().clear().apply()}
}