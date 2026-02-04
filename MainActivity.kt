package com.legend.companyapp

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

// TEK DOSYA - TÜM KOD BURADA!
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    
    // UI ELEMANLARI
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var mainContainer: ConstraintLayout
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var refreshButton: ImageButton
    private lateinit var typingIndicator: TextView
    private lateinit var chatContainer: LinearLayout
    
    // ADAPTÖR VE LİSTELER
    private lateinit var chatAdapter: ChatAdapter
    private val messageList = ArrayList<ChatMessage>()
    private val userList = ArrayList<User>()
    
    // GITHUB AYARLARI (BURAYI KENDİNİZE GÖRE DÜZENLEYİN!)
    private val GITHUB_USERNAME = "ForsDev101"  // GitHub kullanıcı adınız
    private val GITHUB_REPO = "AndroidCI"       // Repo adı
    private val GITHUB_TOKEN = ""               // Token GEREKMEZ! Public repo için boş bırakın
    
    // RENKLER (Pembe-Kırmızı-Siyah-Beyaz)
    private val colors = mapOf(
        "primary_pink" to Color.parseColor("#E91E63"),
        "dark_pink" to Color.parseColor("#C2185B"),
        "accent_red" to Color.parseColor("#F44336"),
        "black" to Color.parseColor("#000000"),
        "white" to Color.parseColor("#FFFFFF"),
        "gray_dark" to Color.parseColor("#212121"),
        "gray_light" to Color.parseColor("#757575")
    )
    
    // OKHTTP ve JSON
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val handler = Handler(Looper.getMainLooper())
    
    // MESAJ KONTROLÜ İÇİN
    private var lastMessageCount = 0
    private var currentUser = User(
        id = "user_${Random().nextInt(1000)}",
        name = "Çalışan ${Random().nextInt(100)}",
        email = "user${Random().nextInt(100)}@company.com",
        avatarColor = getRandomColor()
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // EKRANI MANUEL OLUŞTUR
        createUI()
        
        // KULLANICIYI AYARLA
        setupUser()
        
        // MESAJLARI YÜKLE
        loadMessages()
        
        // HER 10 SANİYEDE BİR MESAJ KONTROL ET
        startMessagePolling()
    }
    
    // === UI OLUŞTURMA ===
    private fun createUI() {
        // Ana container
        mainContainer = ConstraintLayout(this).apply {
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(colors["black"]!!)
        }
        
        // Toolbar
        toolbar = Toolbar(this).apply {
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                dpToPx(56)
            ).apply {
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            }
            setBackgroundColor(colors["primary_pink"]!!)
            title = "Legend Chat"
            setTitleTextColor(colors["white"]!!)
            popupTheme = androidx.appcompat.R.style.ThemeOverlay_AppCompat_Light
        }
        mainContainer.addView(toolbar)
        
        // Drawer Layout
        drawerLayout = DrawerLayout(this).apply {
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.MATCH_PARENT
            ).apply {
                topToBottom = toolbar.id
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            }
            setScrimColor(Color.TRANSPARENT)
        }
        mainContainer.addView(drawerLayout)
        
        // Ana içerik
        val mainContent = ConstraintLayout(this).apply {
            layoutParams = DrawerLayout.LayoutParams(
                DrawerLayout.LayoutParams.MATCH_PARENT,
                DrawerLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(colors["black"]!!)
        }
        
        // Chat Container
        chatContainer = LinearLayout(this).apply {
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.MATCH_PARENT
            ).apply {
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            }
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(android.R.color.transparent)
        }
        
        // Typing Indicator
        typingIndicator = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(40)
            ).apply {
                setMargins(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            }
            text = "⏳ Mesajlar yükleniyor..."
            setTextColor(colors["white"]!!)
            gravity = Gravity.CENTER
            background = createRoundRectDrawable(colors["gray_dark"]!!, dpToPx(20))
            visibility = View.VISIBLE
        }
        chatContainer.addView(typingIndicator)
        
        // RecyclerView
        chatRecyclerView = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0
            ).apply {
                weight = 1f
            }
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = ChatAdapter(messageList).also { chatAdapter = it }
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        }
        chatContainer.addView(chatRecyclerView)
        
        // Input Container
        val inputContainer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(60)
            ).apply {
                setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createRoundRectDrawable(colors["gray_dark"]!!, dpToPx(30))
            setPadding(dpToPx(16), 0, dpToPx(8), 0)
        }
        
        // Message Input
        messageInput = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1f
            }
            hint = "Mesajınızı yazın..."
            setHintTextColor(colors["gray_light"]!!)
            setTextColor(colors["white"]!!)
            background = null
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        inputContainer.addView(messageInput)
        
        // Buton Container
        val buttonContainer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        
        // Refresh Button
        refreshButton = ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(48),
                dpToPx(48)
            )
            setImageResource(android.R.drawable.ic_menu_rotate)
            setColorFilter(colors["white"]!!)
            background = createRoundRectDrawable(colors["accent_red"]!!, dpToPx(24))
            setOnClickListener { loadMessages() }
        }
        buttonContainer.addView(refreshButton)
        
        // Send Button
        sendButton = ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(48),
                dpToPx(48)
            ).apply {
                marginStart = dpToPx(8)
            }
            setImageResource(android.R.drawable.ic_menu_send)
            setColorFilter(colors["white"]!!)
            background = createRoundRectDrawable(colors["primary_pink"]!!, dpToPx(24))
            setOnClickListener { sendMessage() }
        }
        buttonContainer.addView(sendButton)
        
        inputContainer.addView(buttonContainer)
        chatContainer.addView(inputContainer)
        mainContent.addView(chatContainer)
        drawerLayout.addView(mainContent)
        
        // Navigation Drawer
        navView = NavigationView(this).apply {
            layoutParams = DrawerLayout.LayoutParams(
                dpToPx(280),
                DrawerLayout.LayoutParams.MATCH_PARENT,
                Gravity.START
            )
            setBackgroundColor(colors["gray_dark"]!!)
            setNavigationItemSelectedListener(this@MainActivity)
            
            // Menü öğeleri
            menu.apply {
                add("Profilim").setIcon(android.R.drawable.ic_menu_my_calendar)
                add("Tüm Çalışanlar").setIcon(android.R.drawable.ic_menu_myplaces)
                add("Sohbet Odaları").setIcon(android.R.drawable.ic_menu_chat)
                add("Ayarlar").setIcon(android.R.drawable.ic_menu_preferences)
                add("Çıkış Yap").setIcon(android.R.drawable.ic_lock_power_off)
            }
            
            // Header
            val headerView = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(24), dpToPx(56), dpToPx(24), dpToPx(24))
                setBackgroundColor(colors["primary_pink"]!!)
                
                val avatar = TextView(this@MainActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        dpToPx(60),
                        dpToPx(60)
                    ).apply {
                        gravity = Gravity.CENTER
                    }
                    text = currentUser.name.take(2).uppercase()
                    setTextColor(colors["white"]!!)
                    textSize = 20f
                    gravity = Gravity.CENTER
                    background = createRoundRectDrawable(currentUser.avatarColor, dpToPx(30))
                }
                addView(avatar)
                
                val userName = TextView(this@MainActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dpToPx(12)
                        gravity = Gravity.CENTER
                    }
                    text = currentUser.name
                    setTextColor(colors["white"]!!)
                    textSize = 18f
                    gravity = Gravity.CENTER
                }
                addView(userName)
                
                val userEmail = TextView(this@MainActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dpToPx(4)
                        gravity = Gravity.CENTER
                    }
                    text = currentUser.email
                    setTextColor(Color.parseColor("#FFCCD5"))
                    textSize = 12f
                    gravity = Gravity.CENTER
                }
                addView(userEmail)
            }
            addHeaderView(headerView)
        }
        drawerLayout.addView(navView)
        
        // Drawer Toggle
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        
        // Animasyon
        animateBackground()
        
        // Set content view
        setContentView(mainContainer)
    }
    
    // === CHAT ADAPTER ===
    private inner class ChatAdapter(private val messages: List<ChatMessage>) :
        RecyclerView.Adapter<ChatAdapter.ViewHolder>() {
        
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val messageText: TextView = view.findViewById(R.id.message_text)
            val senderName: TextView = view.findViewById(R.id.sender_name)
            val timeText: TextView = view.findViewById(R.id.time_text)
            val messageCard: CardView = view.findViewById(R.id.message_card)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat_message, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val message = messages[position]
            val isMe = message.senderId == currentUser.id
            
            holder.messageText.text = message.text
            holder.senderName.text = message.senderName
            holder.timeText.text = message.time
            
            // Mesaj rengi: Kendi mesajımız pembe, diğerleri kırmızı
            val messageColor = if (isMe) colors["primary_pink"]!! else colors["accent_red"]!!
            val textColor = colors["white"]!!
            
            holder.messageCard.setCardBackgroundColor(messageColor)
            holder.messageText.setTextColor(textColor)
            holder.senderName.setTextColor(Color.parseColor("#FFCCD5"))
            holder.timeText.setTextColor(Color.parseColor("#FFCCD5"))
            
            // Hizalama
            val params = holder.messageCard.layoutParams as LinearLayout.LayoutParams
            params.gravity = if (isMe) Gravity.END else Gravity.START
            params.setMargins(
                if (isMe) dpToPx(50) else dpToPx(16),
                dpToPx(4),
                if (isMe) dpToPx(16) else dpToPx(50),
                dpToPx(4)
            )
        }
        
        override fun getItemCount() = messages.size
    }
    
    // === MESAJ İŞLEMLERİ ===
    private fun loadMessages() {
        typingIndicator.text = "📥 Mesajlar yükleniyor..."
        
        Thread {
            try {
                // GitHub'dan messages.json'u oku
                val messagesJson = readFromGitHub("messages.json")
                val usersJson = readFromGitHub("users.json")
                
                // JSON'u parse et
                val messages = parseMessages(messagesJson)
                val users = parseUsers(usersJson)
                
                handler.post {
                    messageList.clear()
                    messageList.addAll(messages)
                    userList.clear()
                    userList.addAll(users)
                    
                    chatAdapter.notifyDataSetChanged()
                    chatRecyclerView.scrollToPosition(messageList.size - 1)
                    
                    typingIndicator.text = "✅ ${messageList.size} mesaj yüklendi"
                    
                    // 3 saniye sonra gizle
                    handler.postDelayed({
                        typingIndicator.visibility = View.GONE
                    }, 3000)
                    
                    // Yeni mesaj varsa bildirim göster
                    if (messageList.size > lastMessageCount) {
                        showNotification("${messageList.size - lastMessageCount} yeni mesaj")
                        lastMessageCount = messageList.size
                    }
                }
            } catch (e: Exception) {
                handler.post {
                    typingIndicator.text = "❌ Hata: ${e.message}"
                    Toast.makeText(this, "Mesajlar yüklenemedi", Toast.LENGTH_SHORT).show()
                    
                    // Örnek mesajlar göster
                    showSampleMessages()
                }
            }
        }.start()
    }
    
    private fun sendMessage() {
        val text = messageInput.text.toString().trim()
        if (text.isEmpty()) return
        
        // Yeni mesaj oluştur
        val newMessage = ChatMessage(
            id = "msg_${System.currentTimeMillis()}",
            text = text,
            senderId = currentUser.id,
            senderName = currentUser.name,
            time = getCurrentTime(),
            timestamp = System.currentTimeMillis()
        )
        
        // Listeye ekle
        messageList.add(newMessage)
        chatAdapter.notifyItemInserted(messageList.size - 1)
        chatRecyclerView.scrollToPosition(messageList.size - 1)
        
        // Input'u temizle
        messageInput.text.clear()
        
        // GitHub'a kaydet (arka planda)
        Thread {
            try {
                saveToGitHub(newMessage)
                handler.post {
                    Toast.makeText(this, "Mesaj gönderildi ✓", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                handler.post {
                    Toast.makeText(this, "Mesaj kaydedilemedi (offline)", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
    
    // === GİTHUB İŞLEMLERİ ===
    private fun readFromGitHub(filename: String): String {
        val url = "https://api.github.com/repos/$GITHUB_USERNAME/$GITHUB_REPO/contents/$filename"
        
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
        
        // Token varsa header'a ekle
        if (GITHUB_TOKEN.isNotEmpty()) {
            requestBuilder.header("Authorization", "token $GITHUB_TOKEN")
        }
        
        val request = requestBuilder.build()
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            // Dosya yoksa boş JSON döndür
            if (filename == "messages.json") {
                return """{"messages": []}"""
            } else {
                return """{"users": []}"""
            }
        }
        
        val json = response.body?.string() ?: ""
        val jsonObj = JSONObject(json)
        val contentBase64 = jsonObj.getString("content")
        
        // Base64 decode
        return android.util.Base64.decode(contentBase64, android.util.Base64.DEFAULT)
            .toString(Charsets.UTF_8)
    }
    
    private fun saveToGitHub(message: ChatMessage) {
        // Önce mevcut mesajları oku
        val currentContent = readFromGitHub("messages.json")
        val jsonObj = if (currentContent.contains("messages")) {
            JSONObject(currentContent)
        } else {
            JSONObject().put("messages", JSONArray())
        }
        
        // Yeni mesajı ekle
        val messagesArray = jsonObj.getJSONArray("messages")
        val messageObj = JSONObject().apply {
            put("id", message.id)
            put("text", message.text)
            put("senderId", message.senderId)
            put("senderName", message.senderName)
            put("time", message.time)
            put("timestamp", message.timestamp)
        }
        messagesArray.put(messageObj)
        
        // Kullanıcıyı da users.json'a ekle
        saveUserToGitHub()
        
        // GitHub'a yükle
        updateFileOnGitHub("messages.json", jsonObj.toString())
    }
    
    private fun saveUserToGitHub() {
        val currentContent = readFromGitHub("users.json")
        val jsonObj = if (currentContent.contains("users")) {
            JSONObject(currentContent)
        } else {
            JSONObject().put("users", JSONArray())
        }
        
        val usersArray = jsonObj.getJSONArray("users")
        
        // Bu kullanıcı zaten var mı kontrol et
        var userExists = false
        for (i in 0 until usersArray.length()) {
            val userObj = usersArray.getJSONObject(i)
            if (userObj.getString("id") == currentUser.id) {
                userExists = true
                break
            }
        }
        
        if (!userExists) {
            val userObj = JSONObject().apply {
                put("id", currentUser.id)
                put("name", currentUser.name)
                put("email", currentUser.email)
                put("avatarColor", currentUser.avatarColor)
                put("lastSeen", getCurrentTime())
            }
            usersArray.put(userObj)
            updateFileOnGitHub("users.json", jsonObj.toString())
        }
    }
    
    private fun updateFileOnGitHub(filename: String, newContent: String) {
        // Önce dosyanın SHA'sını al
        val getUrl = "https://api.github.com/repos/$GITHUB_USERNAME/$GITHUB_REPO/contents/$filename"
        val getRequest = Request.Builder()
            .url(getUrl)
            .get()
            .apply {
                if (GITHUB_TOKEN.isNotEmpty()) {
                    header("Authorization", "token $GITHUB_TOKEN")
                }
            }
            .build()
        
        val getResponse = client.newCall(getRequest).execute()
        val sha = if (getResponse.isSuccessful) {
            JSONObject(getResponse.body?.string()).getString("sha")
        } else {
            null // Yeni dosya
        }
        
        // Dosyayı güncelle
        val updateUrl = "https://api.github.com/repos/$GITHUB_USERNAME/$GITHUB_REPO/contents/$filename"
        val updateBody = JSONObject().apply {
            put("message", "Update $filename via Legend Chat App")
            put("content", android.util.Base64.encodeToString(
                newContent.toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP
            ))
            if (sha != null) put("sha", sha)
        }
        
        val requestBody = updateBody.toString().toRequestBody("application/json".toMediaType())
        val updateRequest = Request.Builder()
            .url(updateUrl)
            .put(requestBody)
            .apply {
                if (GITHUB_TOKEN.isNotEmpty()) {
                    header("Authorization", "token $GITHUB_TOKEN")
                }
            }
            .build()
        
        client.newCall(updateRequest).execute()
    }
    
    // === YARDIMCI FONKSİYONLAR ===
    private fun parseMessages(json: String): List<ChatMessage> {
        return try {
            val jsonObj = JSONObject(json)
            val messagesArray = jsonObj.getJSONArray("messages")
            val messages = ArrayList<ChatMessage>()
            
            for (i in 0 until messagesArray.length()) {
                val msgObj = messagesArray.getJSONObject(i)
                messages.add(ChatMessage(
                    id = msgObj.getString("id"),
                    text = msgObj.getString("text"),
                    senderId = msgObj.getString("senderId"),
                    senderName = msgObj.getString("senderName"),
                    time = msgObj.getString("time"),
                    timestamp = msgObj.optLong("timestamp", System.currentTimeMillis())
                ))
            }
            messages
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun parseUsers(json: String): List<User> {
        return try {
            val jsonObj = JSONObject(json)
            val usersArray = jsonObj.getJSONArray("users")
            val users = ArrayList<User>()
            
            for (i in 0 until usersArray.length()) {
                val userObj = usersArray.getJSONObject(i)
                users.add(User(
                    id = userObj.getString("id"),
                    name = userObj.getString("name"),
                    email = userObj.getString("email"),
                    avatarColor = userObj.optInt("avatarColor", getRandomColor())
                ))
            }
            users
        } catch (e: Exception) {
            listOf(currentUser) // En azından mevcut kullanıcıyı göster
        }
    }
    
    private fun showSampleMessages() {
        messageList.addAll(listOf(
            ChatMessage("1", "Legend Chat'e hoş geldiniz! 🎉", "system", "Sistem", "12:00"),
            ChatMessage("2", "Merhaba herkese! 👋", "user_1", "Ahmet Yılmaz", "12:01"),
            ChatMessage("3", "Hoş geldin Ahmet! Bugün toplantı var.", "user_2", "Mehmet Demir", "12:02"),
            ChatMessage("4", "Evet, saat 15:00'te. Hazırlıklı olun.", "user_3", "Ayşe Kaya", "12:03"),
            ChatMessage("5", "Anlaşıldı. Dosyaları paylaşayım.", currentUser.id, currentUser.name, getCurrentTime())
        ))
        chatAdapter.notifyDataSetChanged()
    }
    
    private fun setupUser() {
        // Kullanıcı bilgilerini SharedPreferences'tan oku veya oluştur
        val prefs = getSharedPreferences("legend_chat", MODE_PRIVATE)
        currentUser = User(
            id = prefs.getString("user_id", currentUser.id) ?: currentUser.id,
            name = prefs.getString("user_name", currentUser.name) ?: currentUser.name,
            email = prefs.getString("user_email", currentUser.email) ?: currentUser.email,
            avatarColor = prefs.getInt("avatar_color", currentUser.avatarColor)
        )
        
        // Kaydet
        prefs.edit()
            .putString("user_id", currentUser.id)
            .putString("user_name", currentUser.name)
            .putString("user_email", currentUser.email)
            .putInt("avatar_color", currentUser.avatarColor)
            .apply()
    }
    
    private fun startMessagePolling() {
        // Her 10 saniyede bir mesajları kontrol et
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (typingIndicator.visibility != View.VISIBLE) {
                    loadMessages()
                }
                handler.postDelayed(this, 10000)
            }
        }, 10000)
    }
    
    private fun showNotification(message: String) {
        Toast.makeText(this, "💬 $message", Toast.LENGTH_SHORT).show()
        
        // Titreşim efekti (isteğe bağlı)
        val animator = ValueAnimator.ofFloat(0f, 5f, 0f).apply {
            duration = 500
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                chatContainer.translationX = it.animatedValue as Float
            }
        }
        animator.start()
    }
    
    private fun animateBackground() {
        // Gradient animasyonu
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(colors["primary_pink"]!!, colors["accent_red"]!!, colors["black"]!!)
        )
        gradient.cornerRadius = 0f
        mainContainer.background = gradient
    }
    
    private fun createRoundRectDrawable(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }
    }
    
    private fun getRandomColor(): Int {
        val colors = listOf(
            Color.parseColor("#E91E63"), // Pembe
            Color.parseColor("#F44336"), // Kırmızı
            Color.parseColor("#9C27B0"), // Mor
            Color.parseColor("#2196F3"), // Mavi
            Color.parseColor("#4CAF50")  // Yeşil
        )
        return colors.random()
    }
    
    private fun getCurrentTime(): String {
        val calendar = Calendar.getInstance()
        return String.format("%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
    }
    
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
    
    // === NAVIGATION ===
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.title) {
            "Profilim" -> showProfile()
            "Tüm Çalışanlar" -> showEmployees()
            "Sohbet Odaları" -> showChatRooms()
            "Ayarlar" -> showSettings()
            "Çıkış Yap" -> logout()
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
    
    private fun showProfile() {
        Toast.makeText(this, "Profil: ${currentUser.name}", Toast.LENGTH_SHORT).show()
    }
    
    private fun showEmployees() {
        val count = userList.size
        Toast.makeText(this, "$count çalışan online", Toast.LENGTH_SHORT).show()
    }
    
    private fun showChatRooms() {
        Toast.makeText(this, "Sohbet odaları yükleniyor...", Toast.LENGTH_SHORT).show()
    }
    
    private fun showSettings() {
        Toast.makeText(this, "Ayarlar menüsü", Toast.LENGTH_SHORT).show()
    }
    
    private fun logout() {
        finish()
    }
    
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}

// === DATA CLASSES ===
data class ChatMessage(
    val id: String,
    val text: String,
    val senderId: String,
    val senderName: String,
    val time: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class User(
    val id: String,
    val name: String,
    val email: String,
    val avatarColor: Int
)

// === LAYOUT ID'LERİ (Programatik oluşturduğumuz için burada tanımlıyoruz) ===
// Bu ID'ler R.java'da otomatik oluşacak
class R {
    companion object {
        object id {
            const val message_text = 1001
            const val sender_name = 1002
            const val time_text = 1003
            const val message_card = 1004
        }
        object layout {
            const val item_chat_message = 2001
        }
        object string {
            const val navigation_drawer_open = 3001
            const val navigation_drawer_close = 3002
        }
    }
}

// === XML LAYOUT (Programatik yerine XML kullanmak isterseniz) ===
// Bu kodu res/layout/item_chat_message.xml olarak kaydedin:
/*
<?xml version="1.0" encoding="utf-8"?>
<androidx.cardview.widget.CardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/message_card"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_margin="8dp"
    android:elevation="4dp"
    android:maxWidth="280dp">
    
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="12dp">
        
        <TextView
            android:id="@+id/sender_name"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="12sp"
            android:textStyle="bold"/>
        
        <TextView
            android:id="@+id/message_text"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:textSize="16sp"/>
        
        <TextView
            android:id="@+id/time_text"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:textSize="10sp"
            android:layout_gravity="end"/>
    </LinearLayout>
</androidx.cardview.widget.CardView>
*/
