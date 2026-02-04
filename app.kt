package com.legend.companyapp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.format.DateUtils
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    
    // GitHub Ayarları - BURAYI KENDİ BİLGİLERİNLE DOLDUR!
    private val GITHUB_USERNAME = "ForsDev101"  // GitHub kullanıcı adın
    private val GITHUB_TOKEN = "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"     // GitHub Personal Access Token
    private val REPO_NAME = "company-messages"      // Mesajların saklanacağı repo
    private val BRANCH = "main"
    
    // Sabitler
    private val ADMIN_USERNAME = "legend"
    private val ADMIN_PASSWORD = "Hayati1959"
    private val CHANNEL_ID = "company_channel"
    
    // UI Elementleri
    private lateinit var mainContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    
    // Kullanıcı verileri
    private var currentUser: String = ""
    private var currentPassword: String = ""
    private var currentToken: String = ""
    private var userRole: String = "" // "admin" veya "employee"
    private var profileImageBase64: String = ""
    
    // Mesajlar listesi
    private val messagesList = mutableListOf<Message>()
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messagesAdapter: MessagesAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Layout'u programatik oluştur
        createUI()
        
        // İzinleri kontrol et
        checkPermissions()
        
        // Notification kanalını oluştur
        createNotificationChannel()
        
        // GitHub repo kontrolü
        checkAndCreateRepo()
        
        // Eski mesajları temizleme timer'ı
        startCleanupTimer()
    }
    
    private fun createUI() {
        mainContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(16, 16, 16, 16)
        }
        
        scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(mainContainer)
        }
        
        setContentView(scrollView)
        
        showLoginScreen()
    }
    
    private fun showLoginScreen() {
        mainContainer.removeAllViews()
        
        // Başlık
        val title = TextView(this).apply {
            text = "Legend Şirket Uygulaması"
            textSize = 24f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_blue_dark))
        }
        
        // Kullanıcı adı
        val usernameEditText = EditText(this).apply {
            hint = "Kullanıcı Adı"
            setSingleLine(true)
        }
        
        // Şifre
        val passwordEditText = EditText(this).apply {
            hint = "Şifre"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        
        // Giriş butonu
        val loginButton = Button(this).apply {
            text = "Giriş Yap"
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_blue_light))
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
            
            setOnClickListener {
                val username = usernameEditText.text.toString().trim()
                val password = passwordEditText.text.toString().trim()
                
                if (username.isEmpty() || password.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Lütfen tüm alanları doldurun!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                // Admin kontrolü
                if (username == ADMIN_USERNAME && password == ADMIN_PASSWORD) {
                    currentUser = username
                    currentPassword = password
                    userRole = "admin"
                    showAdminDashboard()
                } else {
                    // Çalışan girişi - GitHub'dan kontrol et
                    checkEmployeeLogin(username, password)
                }
            }
        }
        
        // Kayıt ol butonu
        val registerButton = Button(this).apply {
            text = "Yeni Kayıt Ol"
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_light))
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
            
            setOnClickListener {
                showRegistrationScreen()
            }
        }
        
        // Layout'a ekle
        mainContainer.addView(title)
        mainContainer.addView(createSpace(20))
        mainContainer.addView(usernameEditText)
        mainContainer.addView(createSpace(10))
        mainContainer.addView(passwordEditText)
        mainContainer.addView(createSpace(20))
        mainContainer.addView(loginButton)
        mainContainer.addView(createSpace(10))
        mainContainer.addView(registerButton)
    }
    
    private fun showRegistrationScreen() {
        mainContainer.removeAllViews()
        
        // Başlık
        val title = TextView(this).apply {
            text = "Yeni Kayıt Ol"
            textSize = 24f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        
        // Kullanıcı adı
        val usernameEditText = EditText(this).apply {
            hint = "Yeni Kullanıcı Adı"
            setSingleLine(true)
        }
        
        // Şifre
        val passwordEditText = EditText(this).apply {
            hint = "Şifre"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        
        // Şifre tekrar
        val passwordConfirmEditText = EditText(this).apply {
            hint = "Şifre Tekrar"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        
        // GitHub Token
        val tokenEditText = EditText(this).apply {
            hint = "GitHub Token (opsiyonel)"
            setSingleLine(true)
        }
        
        // Profil fotoğrafı seç butonu
        val selectImageButton = Button(this).apply {
            text = "Profil Fotoğrafı Seç"
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_purple))
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
            
            setOnClickListener {
                selectProfileImage()
            }
        }
        
        // Profil fotoğrafı preview
        val imagePreview = ImageView(this).apply {
            layoutParams = ViewGroup.LayoutParams(100, 100)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
        }
        
        // Kayıt butonu
        val registerButton = Button(this).apply {
            text = "Hesap Oluştur"
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark))
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
            
            setOnClickListener {
                val username = usernameEditText.text.toString().trim()
                val password = passwordEditText.text.toString().trim()
                val passwordConfirm = passwordConfirmEditText.text.toString().trim()
                val token = tokenEditText.text.toString().trim()
                
                if (username.isEmpty() || password.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Kullanıcı adı ve şifre gereklidir!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                if (password != passwordConfirm) {
                    Toast.makeText(this@MainActivity, "Şifreler uyuşmuyor!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                if (username == ADMIN_USERNAME) {
                    Toast.makeText(this@MainActivity, "Bu kullanıcı adı alınamaz!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                // Kullanıcıyı GitHub'a kaydet
                registerUserOnGitHub(username, password, token, profileImageBase64)
            }
        }
        
        // Geri butonu
        val backButton = Button(this).apply {
            text = "Geri"
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_light))
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
            
            setOnClickListener {
                showLoginScreen()
            }
        }
        
        // Layout'a ekle
        mainContainer.addView(title)
        mainContainer.addView(createSpace(20))
        mainContainer.addView(usernameEditText)
        mainContainer.addView(createSpace(10))
        mainContainer.addView(passwordEditText)
        mainContainer.addView(createSpace(10))
        mainContainer.addView(passwordConfirmEditText)
        mainContainer.addView(createSpace(10))
        mainContainer.addView(tokenEditText)
        mainContainer.addView(createSpace(10))
        mainContainer.addView(selectImageButton)
        mainContainer.addView(createSpace(10))
        mainContainer.addView(imagePreview)
        mainContainer.addView(createSpace(20))
        mainContainer.addView(registerButton)
        mainContainer.addView(createSpace(10))
        mainContainer.addView(backButton)
    }
    
    private fun showAdminDashboard() {
        mainContainer.removeAllViews()
        
        // Başlık
        val title = TextView(this).apply {
            text = "Admin Paneli - Legend"
            textSize = 24f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark))
        }
        
        // Herkesi Çağır butonu
        val callEveryoneButton = Button(this).apply {
            text = "🚨 HERKESİ ÇAĞIR"
            textSize = 18f
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark))
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
            
            setOnClickListener {
                sendNotificationToAll()
            }
        }
        
        // Çalışanlar listesi
        val employeesTextView = TextView(this).apply {
            text = "Çalışanlar:"
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        
        val employeesList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            id = View.generateViewId()
        }
        
        // Mesajlaşma bölümü
        val messagesTitle = TextView(this).apply {
            text = "Gelen Mesajlar:"
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        
        // RecyclerView for messages
        messagesRecyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 0, 0, 100) // Bottom padding for send button
        }
        
        messagesAdapter = MessagesAdapter(messagesList, true) { message ->
            // Admin mesajı silebilir
            deleteMessage(message.id)
        }
        messagesRecyclerView.adapter = messagesAdapter
        
        // Çıkış butonu
        val logoutButton = Button(this).apply {
            text = "Çıkış Yap"
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_light))
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
            
            setOnClickListener {
                currentUser = ""
                currentPassword = ""
                userRole = ""
                showLoginScreen()
            }
        }
        
        // Layout'a ekle
        mainContainer.addView(title)
        mainContainer.addView(createSpace(20))
        mainContainer.addView(callEveryoneButton)
        mainContainer.addView(createSpace(20))
        mainContainer.addView(employeesTextView)
        mainContainer.addView(createSpace(10))
        mainContainer.addView(employeesList)
        mainContainer.addView(createSpace(20))
        mainContainer.addView(messagesTitle)
        mainContainer.addView(createSpace(10))
        mainContainer.addView(messagesRecyclerView)
        mainContainer.addView(createSpace(20))
        mainContainer.addView(logoutButton)
        
        // Çalışanları yükle
        loadEmployees(employeesList)
        
        // Mesajları yükle
        loadMessages()
        
        // Mesajları otomatik yenile
        startMessageRefreshTimer()
    }
    
    private fun showEmployeeDashboard() {
        mainContainer.removeAllViews()
        
        // Başlık
        val title = TextView(this).apply {
            text = "Hoş Geldin $currentUser"
            textSize = 24f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_blue_dark))
        }
        
        // Legend'a mesaj gönder
        val messageEditText = EditText(this).apply {
            hint = "Legend'a mesaj yaz..."
            setSingleLine(true)
        }
        
        val sendMessageButton = Button(this).apply {
            text = "Mesaj Gönder"
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_blue_dark))
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
            
            setOnClickListener {
                val messageText = messageEditText.text.toString().trim()
                if (messageText.isNotEmpty()) {
                    sendMessageToAdmin(messageText)
                    messageEditText.text.clear()
                }
            }
        }
        
        // Gelen mesajlar
        val messagesTitle = TextView(this).apply {
            text = "Admin Mesajları:"
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        
        // RecyclerView for messages
        messagesRecyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        messagesAdapter = MessagesAdapter(messagesList, false, null)
        messagesRecyclerView.adapter = messagesAdapter
        
        // Çıkış butonu
        val logoutButton = Button(this).apply {
            text = "Çıkış Yap"
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_light))
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
            
            setOnClickListener {
                currentUser = ""
                currentPassword = ""
                userRole = ""
                showLoginScreen()
            }
        }
        
        // Layout'a ekle
        mainContainer.addView(title)
        mainContainer.addView(createSpace(20))
        mainContainer.addView(messageEditText)
        mainContainer.addView(createSpace(10))
        mainContainer.addView(sendMessageButton)
        mainContainer.addView(createSpace(20))
        mainContainer.addView(messagesTitle)
        mainContainer.addView(createSpace(10))
        mainContainer.addView(messagesRecyclerView)
        mainContainer.addView(createSpace(20))
        mainContainer.addView(logoutButton)
        
        // Mesajları yükle
        loadMessages()
        
        // Mesajları otomatik yenile
        startMessageRefreshTimer()
    }
    
    // ==================== GİTHUB İŞLEMLERİ ====================
    
    private fun checkAndCreateRepo() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://api.github.com/user/repos"
                val json = JSONObject().apply {
                    put("name", REPO_NAME)
                    put("private", true)
                    put("auto_init", true)
                }
                
                val request = Request.Builder()
                    .url(url)
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .header("Authorization", "token $GITHUB_TOKEN")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                
                val client = OkHttpClient()
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    Log.d("GitHub", "Repo oluşturuldu veya zaten var")
                    
                    // users.json dosyasını oluştur
                    createUsersFile()
                    
                    // messages.json dosyasını oluştur
                    createMessagesFile()
                }
            } catch (e: Exception) {
                Log.e("GitHub", "Repo oluşturma hatası: ${e.message}")
            }
        }
    }
    
    private fun createUsersFile() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://api.github.com/repos/$GITHUB_USERNAME/$REPO_NAME/contents/users.json"
                
                // Önce dosya var mı kontrol et
                val checkRequest = Request.Builder()
                    .url(url)
                    .header("Authorization", "token $GITHUB_TOKEN")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                
                val client = OkHttpClient()
                val checkResponse = client.newCall(checkRequest).execute()
                
                // Dosya yoksa oluştur
                if (checkResponse.code == 404) {
                    val initialUsers = JSONObject().apply {
                        put("users", JSONArray().apply {
                            put(JSONObject().apply {
                                put("username", ADMIN_USERNAME)
                                put("password", ADMIN_PASSWORD)
                                put("role", "admin")
                                put("profileImage", "")
                                put("createdAt", System.currentTimeMillis())
                            })
                        })
                    }
                    
                    val content = Base64.encodeToString(initialUsers.toString().toByteArray(), Base64.DEFAULT)
                    
                    val json = JSONObject().apply {
                        put("message", "Initial users file")
                        put("content", content)
                        put("branch", BRANCH)
                    }
                    
                    val request = Request.Builder()
                        .url(url)
                        .put(json.toString().toRequestBody("application/json".toMediaType()))
                        .header("Authorization", "token $GITHUB_TOKEN")
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()
                    
                    val response = client.newCall(request).execute()
                    
                    if (response.isSuccessful) {
                        Log.d("GitHub", "Users.json oluşturuldu")
                    }
                }
            } catch (e: Exception) {
                Log.e("GitHub", "Users.json oluşturma hatası: ${e.message}")
            }
        }
    }
    
    private fun createMessagesFile() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://api.github.com/repos/$GITHUB_USERNAME/$REPO_NAME/contents/messages.json"
                
                val checkRequest = Request.Builder()
                    .url(url)
                    .header("Authorization", "token $GITHUB_TOKEN")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                
                val client = OkHttpClient()
                val checkResponse = client.newCall(checkRequest).execute()
                
                if (checkResponse.code == 404) {
                    val initialMessages = JSONObject().apply {
                        put("messages", JSONArray())
                    }
                    
                    val content = Base64.encodeToString(initialMessages.toString().toByteArray(), Base64.DEFAULT)
                    
                    val json = JSONObject().apply {
                        put("message", "Initial messages file")
                        put("content", content)
                        put("branch", BRANCH)
                    }
                    
                    val request = Request.Builder()
                        .url(url)
                        .put(json.toString().toRequestBody("application/json".toMediaType()))
                        .header("Authorization", "token $GITHUB_TOKEN")
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()
                    
                    val response = client.newCall(request).execute()
                    
                    if (response.isSuccessful) {
                        Log.d("GitHub", "Messages.json oluşturuldu")
                    }
                }
            } catch (e: Exception) {
                Log.e("GitHub", "Messages.json oluşturma hatası: ${e.message}")
            }
        }
    }
    
    private fun checkEmployeeLogin(username: String, password: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://api.github.com/repos/$GITHUB_USERNAME/$REPO_NAME/contents/users.json"
                
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "token $GITHUB_TOKEN")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                
                val client = OkHttpClient()
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string())
                    val content = String(Base64.decode(json.getString("content"), Base64.DEFAULT))
                    val usersJson = JSONObject(content)
                    val usersArray = usersJson.getJSONArray("users")
                    
                    var userFound = false
                    
                    for (i in 0 until usersArray.length()) {
                        val user = usersArray.getJSONObject(i)
                        if (user.getString("username") == username && 
                            user.getString("password") == password) {
                            
                            userFound = true
                            currentUser = username
                            currentPassword = password
                            userRole = user.getString("role")
                            
                            Handler(Looper.getMainLooper()).post {
                                if (userRole == "admin") {
                                    showAdminDashboard()
                                } else {
                                    showEmployeeDashboard()
                                }
                            }
                            break
                        }
                    }
                    
                    if (!userFound) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(this@MainActivity, "Kullanıcı adı veya şifre hatalı!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this@MainActivity, "Giriş hatası: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun registerUserOnGitHub(username: String, password: String, token: String, profileImage: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Önce mevcut users.json'ı al
                val url = "https://api.github.com/repos/$GITHUB_USERNAME/$REPO_NAME/contents/users.json"
                
                val getRequest = Request.Builder()
                    .url(url)
                    .header("Authorization", "token $GITHUB_TOKEN")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                
                val client = OkHttpClient()
                val getResponse = client.newCall(getRequest).execute()
                
                if (getResponse.isSuccessful) {
                    val json = JSONObject(getResponse.body?.string())
                    val sha = json.getString("sha")
                    val content = String(Base64.decode(json.getString("content"), Base64.DEFAULT))
                    
                    // 2. Yeni kullanıcıyı ekle
                    val usersJson = JSONObject(content)
                    val usersArray = usersJson.getJSONArray("users")
                    
                    // Kullanıcı zaten var mı kontrol et
                    for (i in 0 until usersArray.length()) {
                        if (usersArray.getJSONObject(i).getString("username") == username) {
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(this@MainActivity, "Bu kullanıcı adı zaten alınmış!", Toast.LENGTH_SHORT).show()
                            }
                            return@launch
                        }
                    }
                    
                    // Yeni kullanıcıyı ekle
                    usersArray.put(JSONObject().apply {
                        put("username", username)
                        put("password", password)
                        put("role", "employee")
                        put("profileImage", profileImage)
                        put("githubToken", token)
                        put("createdAt", System.currentTimeMillis())
                    })
                    
                    // 3. Güncellenmiş dosyayı GitHub'a yükle
                    val updatedContent = Base64.encodeToString(usersJson.toString().toByteArray(), Base64.DEFAULT)
                    
                    val updateJson = JSONObject().apply {
                        put("message", "Add new user: $username")
                        put("content", updatedContent)
                        put("sha", sha)
                        put("branch", BRANCH)
                    }
                    
                    val updateRequest = Request.Builder()
                        .url(url)
                        .put(updateJson.toString().toRequestBody("application/json".toMediaType()))
                        .header("Authorization", "token $GITHUB_TOKEN")
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()
                    
                    val updateResponse = client.newCall(updateRequest).execute()
                    
                    if (updateResponse.isSuccessful) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(this@MainActivity, "Kayıt başarılı! Giriş yapabilirsiniz.", Toast.LENGTH_SHORT).show()
                            showLoginScreen()
                        }
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this@MainActivity, "Kayıt hatası: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun loadEmployees(container: LinearLayout) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://api.github.com/repos/$GITHUB_USERNAME/$REPO_NAME/contents/users.json"
                
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "token $GITHUB_TOKEN")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                
                val client = OkHttpClient()
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string())
                    val content = String(Base64.decode(json.getString("content"), Base64.DEFAULT))
                    val usersJson = JSONObject(content)
                    val usersArray = usersJson.getJSONArray("users")
                    
                    Handler(Looper.getMainLooper()).post {
                        container.removeAllViews()
                        
                        for (i in 0 until usersArray.length()) {
                            val user = usersArray.getJSONObject(i)
                            val username = user.getString("username")
                            val role = user.getString("role")
                            
                            if (role != "admin") {
                                val employeeView = TextView(this@MainActivity).apply {
                                    text = "👤 $username"
                                    textSize = 16f
                                    setPadding(20, 10, 20, 10)
                                    setBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.background_light))
                                }
                                
                                container.addView(employeeView)
                                container.addView(createSpace(5))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GitHub", "Çalışanları yükleme hatası: ${e.message}")
            }
        }
    }
    
    private fun loadMessages() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://api.github.com/repos/$GITHUB_USERNAME/$REPO_NAME/contents/messages.json"
                
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "token $GITHUB_TOKEN")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                
                val client = OkHttpClient()
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string())
                    val content = String(Base64.decode(json.getString("content"), Base64.DEFAULT))
                    val messagesJson = JSONObject(content)
                    val messagesArray = messagesJson.getJSONArray("messages")
                    
                    messagesList.clear()
                    
                    for (i in 0 until messagesArray.length()) {
                        val msg = messagesArray.getJSONObject(i)
                        messagesList.add(Message(
                            id = msg.getString("id"),
                            sender = msg.getString("sender"),
                            receiver = msg.getString("receiver"),
                            text = msg.getString("text"),
                            timestamp = msg.getLong("timestamp"),
                            read = msg.getBoolean("read")
                        ))
                    }
                    
                    // Zaman sırasına göre sırala (en yeni üstte)
                    messagesList.sortByDescending { it.timestamp }
                    
                    Handler(Looper.getMainLooper()).post {
                        messagesAdapter.notifyDataSetChanged()
                    }
                }
            } catch (e: Exception) {
                Log.e("GitHub", "Mesajları yükleme hatası: ${e.message}")
            }
        }
    }
    
    private fun sendMessageToAdmin(text: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Mevcut messages.json'ı al
                val url = "https://api.github.com/repos/$GITHUB_USERNAME/$REPO_NAME/contents/messages.json"
                
                val getRequest = Request.Builder()
                    .url(url)
                    .header("Authorization", "token $GITHUB_TOKEN")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                
                val client = OkHttpClient()
                val getResponse = client.newCall(getRequest).execute()
                
                if (getResponse.isSuccessful) {
                    val json = JSONObject(getResponse.body?.string())
                    val sha = json.getString("sha")
                    val content = String(Base64.decode(json.getString("content"), Base64.DEFAULT))
                    
                    // 2. Yeni mesajı ekle
                    val messagesJson = JSONObject(content)
                    val messagesArray = messagesJson.getJSONArray("messages")
                    
                    val newMessage = JSONObject().apply {
                        put("id", UUID.randomUUID().toString())
                        put("sender", currentUser)
                        put("receiver", ADMIN_USERNAME)
                        put("text", text)
                        put("timestamp", System.currentTimeMillis())
                        put("read", false)
                    }
                    
                    messagesArray.put(newMessage)
                    
                    // 3. Güncellenmiş dosyayı yükle
                    val updatedContent = Base64.encodeToString(messagesJson.toString().toByteArray(), Base64.DEFAULT)
                    
                    val updateJson = JSONObject().apply {
                        put("message", "New message from $currentUser")
                        put("content", updatedContent)
                        put("sha", sha)
                        put("branch", BRANCH)
                    }
                    
                    val updateRequest = Request.Builder()
                        .url(url)
                        .put(updateJson.toString().toRequestBody("application/json".toMediaType()))
                        .header("Authorization", "token $GITHUB_TOKEN")
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()
                    
                    val updateResponse = client.newCall(updateRequest).execute()
                    
                    if (updateResponse.isSuccessful) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(this@MainActivity, "Mesaj gönderildi!", Toast.LENGTH_SHORT).show()
                            
                            // Admin'e bildirim gönder
                            sendNotification("Yeni Mesaj", "$currentUser: $text")
                            
                            loadMessages()
                        }
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this@MainActivity, "Mesaj gönderme hatası: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun deleteMessage(messageId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Mevcut messages.json'ı al
                val url = "https://api.github.com/repos/$GITHUB_USERNAME/$REPO_NAME/contents/messages.json"
                
                val getRequest = Request.Builder()
                    .url(url)
                    .header("Authorization", "token $GITHUB_TOKEN")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                
                val client = OkHttpClient()
                val getResponse = client.newCall(getRequest).execute()
                
                if (getResponse.isSuccessful) {
                    val json = JSONObject(getResponse.body?.string())
                    val sha = json.getString("sha")
                    val content = String(Base64.decode(json.getString("content"), Base64.DEFAULT))
                    
                    // 2. Mesajı sil
                    val messagesJson = JSONObject(content)
                    val messagesArray = messagesJson.getJSONArray("messages")
                    
                    val newMessagesArray = JSONArray()
                    var messageDeleted = false
                    
                    for (i in 0 until messagesArray.length()) {
                        val msg = messagesArray.getJSONObject(i)
                        if (msg.getString("id") != messageId) {
                            newMessagesArray.put(msg)
                        } else {
                            messageDeleted = true
                        }
                    }
                    
                    if (messageDeleted) {
                        messagesJson.put("messages", newMessagesArray)
                        
                        // 3. Güncellenmiş dosyayı yükle
                        val updatedContent = Base64.encodeToString(messagesJson.toString().toByteArray(), Base64.DEFAULT)
                        
                        val updateJson = JSONObject().apply {
                            put("message", "Delete message $messageId")
                            put("content", updatedContent)
                            put("sha", sha)
                            put("branch", BRANCH)
                        }
                        
                        val updateRequest = Request.Builder()
                            .url(url)
                            .put(updateJson.toString().toRequestBody("application/json".toMediaType()))
                            .header("Authorization", "token $GITHUB_TOKEN")
                            .header("Accept", "application/vnd.github.v3+json")
                            .build()
                        
                        val updateResponse = client.newCall(updateRequest).execute()
                        
                        if (updateResponse.isSuccessful) {
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(this@MainActivity, "Mesaj silindi!", Toast.LENGTH_SHORT).show()
                                loadMessages()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this@MainActivity, "Mesaj silme hatası: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun cleanupOldMessages() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://api.github.com/repos/$GITHUB_USERNAME/$REPO_NAME/contents/messages.json"
                
                val getRequest = Request.Builder()
                    .url(url)
                    .header("Authorization", "token $GITHUB_TOKEN")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                
                val client = OkHttpClient()
                val getResponse = client.newCall(getRequest).execute()
                
                if (getResponse.isSuccessful) {
                    val json = JSONObject(getResponse.body?.string())
                    val sha = json.getString("sha")
                    val content = String(Base64.decode(json.getString("content"), Base64.DEFAULT))
                    
                    val messagesJson = JSONObject(content)
                    val messagesArray = messagesJson.getJSONArray("messages")
                    
                    val newMessagesArray = JSONArray()
                    val fiveDaysAgo = System.currentTimeMillis() - (5 * 24 * 60 * 60 * 1000)
                    var cleanedCount = 0
                    
                    for (i in 0 until messagesArray.length()) {
                        val msg = messagesArray.getJSONObject(i)
                        val timestamp = msg.getLong("timestamp")
                        
                        if (timestamp > fiveDaysAgo) {
                            newMessagesArray.put(msg)
                        } else {
                            cleanedCount++
                        }
                    }
                    
                    if (cleanedCount > 0) {
                        messagesJson.put("messages", newMessagesArray)
                        
                        val updatedContent = Base64.encodeToString(messagesJson.toString().toByteArray(), Base64.DEFAULT)
                        
                        val updateJson = JSONObject().apply {
                            put("message", "Cleanup $cleanedCount old messages")
                            put("content", updatedContent)
                            put("sha", sha)
                            put("branch", BRANCH)
                        }
                        
                        val updateRequest = Request.Builder()
                            .url(url)
                            .put(updateJson.toString().toRequestBody("application/json".toMediaType()))
                            .header("Authorization", "token $GITHUB_TOKEN")
                            .header("Accept", "application/vnd.github.v3+json")
                            .build()
                        
                        val updateResponse = client.newCall(updateRequest).execute()
                        
                        if (updateResponse.isSuccessful) {
                            Log.d("Cleanup", "$cleanedCount eski mesaj temizlendi")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Cleanup", "Eski mesaj temizleme hatası: ${e.message}")
            }
        }
    }
    
    // ==================== BİLDİRİM İŞLEMLERİ ====================
    
    private fun sendNotificationToAll() {
        // Burada normalde FCM kullanılırdı ama GitHub API ile sınırlıyız
        // Uygulama içi bildirim gönderelim
        
        sendNotification("🚨 ACİL ÇAĞRI", "LEGEND SİZİ ÇAĞIRIYOR!")
        
        // Tüm çalışanlara mesaj olarak da kaydedelim
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://api.github.com/repos/$GITHUB_USERNAME/$REPO_NAME/contents/messages.json"
                
                val getRequest = Request.Builder()
                    .url(url)
                    .header("Authorization", "token $GITHUB_TOKEN")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                
                val client = OkHttpClient()
                val getResponse = client.newCall(getRequest).execute()
                
                if (getResponse.isSuccessful) {
                    val json = JSONObject(getResponse.body?.string())
                    val sha = json.getString("sha")
                    val content = String(Base64.decode(json.getString("content"), Base64.DEFAULT))
                    
                    val messagesJson = JSONObject(content)
                    val messagesArray = messagesJson.getJSONArray("messages")
                    
                    // Tüm çalışanları bul
                    val usersUrl = "https://api.github.com/repos/$GITHUB_USERNAME/$REPO_NAME/contents/users.json"
                    val usersRequest = Request.Builder()
                        .url(usersUrl)
                        .header("Authorization", "token $GITHUB_TOKEN")
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()
                    
                    val usersResponse = client.newCall(usersRequest).execute()
                    
                    if (usersResponse.isSuccessful) {
                        val usersJson = JSONObject(usersResponse.body?.string())
                        val usersContent = String(Base64.decode(usersJson.getString("content"), Base64.DEFAULT))
                        val usersData = JSONObject(usersContent)
                        val usersArray = usersData.getJSONArray("users")
                        
                        for (i in 0 until usersArray.length()) {
                            val user = usersArray.getJSONObject(i)
                            if (user.getString("role") == "employee") {
                                val employeeName = user.getString("username")
                                
                                val notificationMessage = JSONObject().apply {
                                    put("id", UUID.randomUUID().toString())
                                    put("sender", ADMIN_USERNAME)
                                    put("receiver", employeeName)
                                    put("text", "🚨 LEGEND SİZİ ÇAĞIRIYOR! Acilen iletişime geçin!")
                                    put("timestamp", System.currentTimeMillis())
                                    put("read", false)
                                }
                                
                                messagesArray.put(notificationMessage)
                            }
                        }
                        
                        messagesJson.put("messages", messagesArray)
                        
                        val updatedContent = Base64.encodeToString(messagesJson.toString().toByteArray(), Base64.DEFAULT)
                        
                        val updateJson = JSONObject().apply {
                            put("message", "Admin call to all employees")
                            put("content", updatedContent)
                            put("sha", sha)
                            put("branch", BRANCH)
                        }
                        
                        val updateRequest = Request.Builder()
                            .url(url)
                            .put(updateJson.toString().toRequestBody("application/json".toMediaType()))
                            .header("Authorization", "token $GITHUB_TOKEN")
                            .header("Accept", "application/vnd.github.v3+json")
                            .build()
                        
                        val updateResponse = client.newCall(updateRequest).execute()
                        
                        if (updateResponse.isSuccessful) {
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(this@MainActivity, "Tüm çalışanlara çağrı gönderildi!", Toast.LENGTH_SHORT).show()
                                loadMessages()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Notification", "Çağrı gönderme hatası: ${e.message}")
            }
        }
    }
    
    private fun sendNotification(title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, "Şirket Bildirimleri", importance).apply {
                description = "Legend'dan gelen bildirimler"
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        
        with(NotificationManagerCompat.from(this)) {
            if (ActivityCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                notify(System.currentTimeMillis().toInt(), builder.build())
            }
        }
    }
    
    // ==================== DİĞER METODLAR ====================
    
    private fun createSpace(height: Int): View {
        return View(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            )
        }
    }
    
    private fun selectProfileImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, 100)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            val imageUri = data.data
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                profileImageBase64 = bitmapToBase64(bitmap)
                
                // Önizleme göster
                val imageView = findViewById<ImageView>(R.id.image_preview)
                imageView?.setImageBitmap(bitmap)
                
                Toast.makeText(this, "Profil fotoğrafı seçildi", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Resim yükleme hatası", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Şirket Bildirimleri",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Legend'dan gelen acil bildirimler"
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.POST_NOTIFICATIONS
        )
        
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 1)
        }
    }
    
    private fun startCleanupTimer() {
        val handler = Handler(Looper.getMainLooper())
        val cleanupTask = object : Runnable {
            override fun run() {
                cleanupOldMessages()
                handler.postDelayed(this, 60 * 60 * 1000) // Her saat temizle
            }
        }
        handler.postDelayed(cleanupTask, 5000) // 5 saniye sonra başla
    }
    
    private fun startMessageRefreshTimer() {
        val handler = Handler(Looper.getMainLooper())
        val refreshTask = object : Runnable {
            override fun run() {
                loadMessages()
                handler.postDelayed(this, 30 * 1000) // 30 saniyede bir yenile
            }
        }
        handler.postDelayed(refreshTask, 30000)
    }
    
    // ==================== DATA CLASSES ====================
    
    data class Message(
        val id: String,
        val sender: String,
        val receiver: String,
        val text: String,
        val timestamp: Long,
        val read: Boolean
    )
    
    // ==================== ADAPTER ====================
    
    inner class MessagesAdapter(
        private val messages: List<Message>,
        private val isAdmin: Boolean,
        private val onDelete: ((Message) -> Unit)?
    ) : RecyclerView.Adapter<MessagesAdapter.MessageViewHolder>() {
        
        inner class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val senderText: TextView = view.findViewById(R.id.sender_text)
            val messageText: TextView = view.findViewById(R.id.message_text)
            val timeText: TextView = view.findViewById(R.id.time_text)
            val deleteButton: ImageButton? = view.findViewById(R.id.delete_button)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
            val view = layoutInflater.inflate(R.layout.message_item, parent, false)
            return MessageViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
            val message = messages[position]
            
            holder.senderText.text = "Gönderen: ${message.sender}"
            holder.messageText.text = message.text
            
            // Zamanı formatla
            val date = Date(message.timestamp)
            val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
            holder.timeText.text = sdf.format(date)
            
            // Admin ise sil butonunu göster
            if (isAdmin && onDelete != null) {
                holder.deleteButton?.visibility = View.VISIBLE
                holder.deleteButton?.setOnClickListener {
                    onDelete(message)
                }
            } else {
                holder.deleteButton?.visibility = View.GONE
            }
        }
        
        override fun getItemCount() = messages.size
    }
}

// message_item.xml için programatik view oluşturma
// Bu kısmı onCreate'te çağırmanız gerekebilir
fun createMessageItemView(context: Context): View {
    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setPadding(16, 16, 16, 16)
        setBackgroundColor(ContextCompat.getColor(context, android.R.color.background_light))
    }
    
    val senderRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    
    val senderText = TextView(context).apply {
        id = R.id.sender_text
        textSize = 14f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        )
    }
    
    val timeText = TextView(context).apply {
        id = R.id.time_text
        textSize = 12f
        setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
    }
    
    val deleteButton = ImageButton(context).apply {
        id = R.id.delete_button
        setImageResource(android.R.drawable.ic_menu_delete)
        visibility = View.GONE
    }
    
    senderRow.addView(senderText)
    senderRow.addView(timeText)
    senderRow.addView(deleteButton)
    
    val messageText = TextView(context).apply {
        id = R.id.message_text
        textSize = 16f
        setPadding(0, 8, 0, 0)
    }
    
    container.addView(senderRow)
    container.addView(messageText)
    
    return container
}

// Resource ID'leri
object R {
    object id {
        const val sender_text = 1001
        const val message_text = 1002
        const val time_text = 1003
        const val delete_button = 1004
        const val image_preview = 1005
    }
}
