@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.message_app

import android.app.Activity
import android.content.ContentValues.TAG
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.BarcodeFormat
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.CaptureActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

data class Friend(
    val documentId: String,
    val uid: String,
    val name: String
)

data class User(
    val uid: String,
    val name: String
)

data class Message(
    val uid: String,
    val name: String,
    val message: String,
    val date: Date
)

class HomeActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var uid: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        uid = intent.getStringExtra("UID") ?: "Unknown"

        // 必要に応じてauthを使用
        val user = auth.currentUser

        setContent {
            val navController = rememberNavController()

            NavHost(navController = navController, startDestination = "home") {
                composable("home") {
                    HomeScreen(navController, auth, uid)
                }
                composable("details/{itemId}") { backStackEntry ->
                    val itemId = backStackEntry.arguments?.getString("itemId")
                    if (itemId != null) {
                        ChatDetailsScreen(navController, uid, itemId)
                    }
                }
                composable("user") {
                    UserDetailScreen(navController, uid)
                }
                composable("friends") {
                    FriendsListScreen(navController, uid)
                }
                composable("qrcode") {
                    QRCodeScreen(navController, uid)
                }
                composable("addFriend/{friendUid}") { backStackEntry ->
                    val friendUid = backStackEntry.arguments?.getString("friendUid")
                    if (friendUid != null) {
                        AddFriendScreen(navController, uid, friendUid)
                    }
                }
                composable("addFriendToChat/{chatId}") { backStackEntry ->
                    val chatId = backStackEntry.arguments?.getString("chatId")
                    if (chatId != null) {
                        AddFriendToChat(navController, uid, chatId)
                    }
                }
            }
        }
    }

    @Composable
    fun HomeScreen(navController: NavController, auth: FirebaseAuth, uid: String) {
        // 現在のコンテキストを取得
        val context = LocalContext.current

        val db = FirebaseFirestore.getInstance()
        val items = remember { mutableStateOf(listOf<String>()) }
        var myUser by remember { mutableStateOf(User("No UID", "No Name")) }

        DisposableEffect(Unit) {
            val chatsRef = db.collection("chats").whereArrayContains("members", uid)
            val listener = chatsRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Handle the error here
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.map { it.id } ?: listOf()
                items.value = list
                println("test data count: ${list.size}")
            }

            // This will be executed when the composable is disposed
            onDispose {
                listener.remove()
            }
        }

        LaunchedEffect(uid) {
            val usersRef = db.collection("users")
            try {
                val documents = usersRef.whereEqualTo("uid", uid).get().await()
                for (document in documents) {
                    val user = document.data
                    val userName = user["name"] as? String ?: "No Name"
                    myUser = User(uid, userName)
                }
            } catch (e: Exception) {
                println("Error processing documents: ${e.message}")
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Chats") },
                    actions = {
                        Button(onClick = {
                            navController.navigate("friends")
                        }) {
                            Text(text = "Friends")
                        }
                        Button(
                            onClick = {
                                navController.navigate("user")
                            }
                        ) {
                            Icon(Icons.Default.AccountCircle, contentDescription = "Person")
                        }
                        Button(
                            onClick = {
                                // Logout process
                                auth.signOut()
                                context.startActivity(Intent(context, MainActivity::class.java))
                                (context as Activity).finish()
                            }
                        ) {
                            Text("Logout")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = {
                    val viewModelScope = CoroutineScope(Dispatchers.Main)
                    viewModelScope.launch {
                        coroutineScope {
                            launch {
                                try {
                                    val db = FirebaseFirestore.getInstance()
                                    val chatsRef = db.collection("chats")
                                    val newChat = chatsRef.document()
                                    val chatData = hashMapOf(
                                        "members" to arrayListOf(uid),
                                        "owner" to uid
                                    )
                                    newChat.set(chatData).await()

                                    Log.d("Firestore", "Message added successfully!")
                                    navController.navigate("details/${newChat.id}")
                                } catch (e: Exception) {
                                    Log.e("Firestore", "Error", e)
                                }
                            }
                        }
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Welcome ${myUser.name}")
                }
                Spacer(modifier = Modifier.height(8.dp)) // Optional spacer for some spacing between the Row and LazyColumn
                LazyColumn(
                    modifier = Modifier.background(Color.LightGray),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    contentPadding = PaddingValues(5.dp)
                ) {
                    items(items.value) { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White, RoundedCornerShape(5.dp))
                                .clickable {
                                    // Navigate to the details screen with the item as an argument
                                    navController.navigate("details/$item")
                                }
                        ) {
                            Text(
                                text = item,
                                color = Color.Black,
                                fontSize = 25.sp
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ChatDetailsScreen(navController: NavController, uid: String, itemId: String) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("") },
                    navigationIcon = {
                        Button(onClick = {
                            navController.popBackStack()
                        }) {
                            Text("Go Back")
                        }
                    },
                    actions = {
                        Button(onClick = {
                            navController.navigate("addFriendToChat/$itemId")
                        }) {
                            Text("Add Friend")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Text(text = "Details for item: $itemId")
                MessageList(uid, itemId)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MessageList(uid: String, documentId: String) {
        val db = FirebaseFirestore.getInstance()

        // 指定されたdocumentIdを持つドキュメントの参照を取得
        val chatDocument = db.collection("chats").document(documentId)
        // messageサブコレクションへの参照を取得
        val messagesCollection = chatDocument.collection("messages")
        // mutableStateListOfを使用して、リアルタイムのメッセージのアップデートを保持します。
        val messages = remember { mutableStateListOf<Message>() }
        var newMessage by remember { mutableStateOf("") }

        var myUser by remember { mutableStateOf(User("No UID", "No Name")) }

        // サブコレクションのリアルタイムアップデートをリスン
        DisposableEffect(Unit) {
            val sortedQuery = messagesCollection
                .orderBy("date")

            val listenerRegistration = sortedQuery.addSnapshotListener { querySnapshot, exception ->
                if (exception != null) {
                    // エラーハンドリング
                    Log.w(TAG, "Listen failed.", exception)
                    return@addSnapshotListener
                }

                // 既存のリストをクリア
                messages.clear()

                // 新しいデータを追加
                for (document in querySnapshot!!.documents) {
                    val uid = document.getString("uid")
                    val name = document.getString("name")
                    val timestamp = document.getTimestamp("date")
                    val date: Date? = timestamp?.toDate()
                    val message = document.getString("text")
                    if (!uid.isNullOrBlank() &&
                        !name.isNullOrBlank() &&
                        date != null &&
                        !message.isNullOrBlank()
                    ) {
                        messages.add(Message(uid, name, message, date))
                    }
                }
            }

            // コンポーザブルが破棄されるときにリスナーを削除する
            onDispose {
                listenerRegistration.remove()
            }
        }

        LaunchedEffect(uid) {
            val usersRef = db.collection("users")
            try {
                val documents = usersRef.whereEqualTo("uid", uid).get().await()
                for (document in documents) {
                    val user = document.data
                    val userName = user["name"] as? String ?: "No Name"
                    myUser = User(uid, userName)
                }
            } catch (e: Exception) {
                println("Error processing documents: ${e.message}")
            }
        }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // LazyColumnを使用してメッセージを表示
            LazyColumn(
                modifier = Modifier.background(Color.LightGray),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                contentPadding = PaddingValues(5.dp)
            ) {
                items(messages) { message ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(5.dp))
                    ) {
                        Text(
                            text = message.name,
                            color = Color.Black,
                            fontSize = 10.sp
                        )
                        Text(
                            text = message.message,
                            color = Color.Black,
                            fontSize = 25.sp
                        )
                    }

                }
            }

            // TextFieldとButtonのレイアウト
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextField(
                    value = newMessage,
                    onValueChange = { newMessage = it },
                    placeholder = { Text("Enter message") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    val currentDate = Timestamp.now()
                    // Firestoreにメッセージを追加する
                    if (newMessage.isNotBlank()) {
                        messagesCollection.add(
                            mapOf(
                                "uid" to myUser.uid,
                                "name" to myUser.name,
                                "text" to newMessage,
                                "date" to currentDate

                            )
                        )
                        newMessage = "" // TextFieldをクリア
                    }
                }) {
                    Text("Send")
                }
            }
        }
    }

    @Composable
    fun UserDetailScreen(navController: NavController, uid: String) {
        val db = FirebaseFirestore.getInstance()
        var userName by remember { mutableStateOf("") }
        var documentId by remember { mutableStateOf("") }
        val context = LocalContext.current
        val updatedContext = rememberUpdatedState(context)

        LaunchedEffect(uid) {
            val usersRef = db.collection("users")
            try {
                val documents = usersRef.whereEqualTo("uid", uid).get().await()
                for (document in documents) {
                    val user = document.data
                    userName = user["name"] as? String ?: "No Name"
                    documentId = document.id
                }
            } catch (e: Exception) {
                println("Error processing documents: ${e.message}")
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("") },
                    navigationIcon = {
                        Button(onClick = {
                            navController.popBackStack()
                        }) {
                            Text("Go Back")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Text("Name")
                TextField(value = userName, onValueChange = { userName = it })
                Button(onClick = {
                    if (documentId.isNotEmpty()) {
                        val userRef = db.collection("users").document(documentId)
                        userRef.update("name", userName)
                            .addOnSuccessListener {
                                Toast.makeText(
                                    updatedContext.value, "変更完了",
                                    Toast.LENGTH_SHORT
                                ).show()
                                println("Name successfully updated!")
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(
                                    updatedContext.value, "変更失敗",
                                    Toast.LENGTH_SHORT
                                ).show()
                                println("Error updating name: ${e.message}")
                            }

                    }

                }) {
                    Text("Apply")
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun FriendsListScreen(navController: NavController, uid: String) {
        val context = LocalContext.current
        val activityResultLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val scanResult =
                        IntentIntegrator.parseActivityResult(result.resultCode, result.data)
                    if (scanResult != null) {
                        if (scanResult.contents == null) {
                            Toast.makeText(context, "Cancelled", Toast.LENGTH_SHORT).show()
                        } else {
                            val friendUid = scanResult.contents
                            Toast.makeText(
                                context,
                                "Scanned: $friendUid",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        val db = FirebaseFirestore.getInstance()
        var userName by remember { mutableStateOf("") }
        val users = remember { mutableStateOf(listOf<Friend>()) }
        var documentId by remember { mutableStateOf("") }

        DisposableEffect(Unit) {
            val chatsRef = db.collection("friends").whereEqualTo("uid", uid)

            val listener = chatsRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Handle the error here
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.map {
                    Friend(
                        documentId = it.id,
                        uid = it.getString("friendUid") ?: "",
                        name = it.getString("friendName") ?: ""
                    )
                } ?: listOf()
                users.value = list
                println("test data count: ${list.size}")
            }

            // This will be executed when the composable is disposed
            onDispose {
                listener.remove()
            }
        }

        LaunchedEffect(uid) {
            val usersRef = db.collection("users")
            try {
                val documents = usersRef.whereEqualTo("uid", uid).get().await()
                for (document in documents) {
                    val user = document.data
                    userName = user["name"] as? String ?: "No Name"
                    documentId = document.id
                }
            } catch (e: Exception) {
                println("Error processing documents: ${e.message}")
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("") },
                    navigationIcon = {
                        Button(onClick = {
                            navController.popBackStack()
                        }) {
                            Text("Go Back")
                        }
                    },
                    actions = {
                        Button(onClick = {
                            navController.navigate("qrcode")
                        }) {
                            Text("QR Code")
                        }
                        Button(onClick = {
//                        val intent = IntentIntegrator(context as Activity).createScanIntent()
//                        activityResultLauncher.launch(intent)
                            navController.navigate("addFriend/0001")
                        }) {
                            Text("Add")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Text("Friends")
                LazyColumn {
                    items(users.value) { friend ->
                        Text(text = friend.name)
                        // 必要に応じて、デザインやレイアウトをカスタマイズできます
                    }
                }
            }
        }
    }

    fun generateQRCode(text: String, width: Int, height: Int): Bitmap? {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(
                    x,
                    y,
                    if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                )
            }
        }
        return bitmap
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun QRCodeScreen(navController: NavController, uid: String) {
        val qrCodeBitmap: Bitmap? = generateQRCode(uid, 400, 400)
        val imageBitmap: ImageBitmap? = qrCodeBitmap?.asImageBitmap()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("") },
                    navigationIcon = {
                        Button(onClick = {
                            navController.popBackStack()
                        }) {
                            Text("Go Back")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Text("QR Code")
                Spacer(modifier = Modifier.height(16.dp))
                if (imageBitmap != null) {
                    Image(
                        painter = BitmapPainter(imageBitmap),
                        contentDescription = "QR Code",
                        modifier = Modifier.size(200.dp)
                    )
                } else {
                    Text("No QR Code available")
                }
            }
        }
    }

    @Composable
    fun AddFriendScreen(navController: NavController, uid: String, friendUid: String) {
        val db = FirebaseFirestore.getInstance()
        var friendName by remember { mutableStateOf("") }

        LaunchedEffect(friendUid) {
            val usersRef = db.collection("users")
            try {
                val documents = usersRef.whereEqualTo("uid", friendUid).get().await()
                for (document in documents) {
                    val user = document.data
                    friendName = user["name"] as? String ?: "No Name"
                }
            } catch (e: Exception) {
                println("Error processing documents: ${e.message}")
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("") },
                    navigationIcon = {
                        Button(onClick = {
                            navController.popBackStack()
                        }) {
                            Text("Go Back")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Text("Friend's Name: $friendName")
                Button(onClick = {
                    val friendsCollection = db.collection("friends")
                    friendsCollection.add(
                        mapOf(
                            "uid" to uid,
                            "friendName" to friendName,
                            "friendUid" to friendUid

                        )
                    )
                    navController.popBackStack()
                }) {
                    Text("Add")
                }
            }
        }
    }
}

@Composable
fun AddFriendToChat(navController: NavController, uid: String, chatId: String) {
    val db = FirebaseFirestore.getInstance()
    val users = remember { mutableStateOf(listOf<Friend>()) }
    val selectedFriends = remember { mutableStateListOf<Friend>() }
    val members = remember { mutableStateOf(listOf<String>()) }


    DisposableEffect(Unit) {
        val chatsRef = db.collection("friends").whereEqualTo("uid", uid)

        val listener = chatsRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                // Handle the error here
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.map {
                Friend(
                    documentId = it.id,
                    uid = it.getString("friendUid") ?: "",
                    name = it.getString("friendName") ?: ""
                )
            } ?: listOf()
            users.value = list
            println("test data count: ${list.size}")
        }

        // This will be executed when the composable is disposed
        onDispose {
            listener.remove()
        }
    }

    LaunchedEffect(chatId) {
        val chatRef = db.collection("chats").document(chatId)
        try {
            // ドキュメントを非同期に取得
            val document = chatRef.get().await()

            // ドキュメントデータからmembersフィールドを取得し、状態変数に設定
            document.get("members")?.let { data ->
                members.value = data as? List<String> ?: emptyList()
            }
        } catch (e: Exception) {
            println("Error processing documents: ${e.message}")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    Button(onClick = {
                        navController.popBackStack()
                    }) {
                        Text("Go Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text("Friends")
            LazyColumn {
                items(users.value) { friend ->
                    if (!members.value.contains(friend.uid)) {
                        // この友人が選択されているかどうかの状態を保持する
                        val isSelected = friend in selectedFriends

                        // 背景色を動的に設定
                        val backgroundColor = if (isSelected) Color.LightGray else Color.Transparent

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(backgroundColor)
                                .clickable {
                                    if (isSelected) {
                                        selectedFriends.remove(friend)
                                    } else {
                                        selectedFriends.add(friend)
                                    }
                                }
                                .padding(16.dp)
                        ) {
                            Text(text = friend.name)
                        }
                    }
                }
            }
            Button(onClick = {
                // ボタンが押されたときの処理
                for (selectedFriend in selectedFriends) {
                    if (!members.value.contains(selectedFriend.uid)) {
                        members.value = members.value + selectedFriend.uid
                    }
                }
                val chatRef = db.collection("chats").document(chatId)

                chatRef.update("members", members.value)
                    .addOnSuccessListener {
                        println("Document successfully updated!")
                    }
                    .addOnFailureListener { e ->
                        println("Error updating document: $e")
                    }
            }) {
                Text("Register Selected Friends")
            }
        }
    }
}