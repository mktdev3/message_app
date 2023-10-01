package com.example.message_app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        println("firestore's data")
        auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val usersCollection = db.collection("test")

        usersCollection.get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    println(document.id)
                }
            }
            .addOnFailureListener { exception ->
                // エラーハンドリング
                println("Error fetching documents: ${exception.message}")
            }

        val documentRef = db.collection("test").document("d9ApLSnH5u5HSl2I512U")

        documentRef.get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    println("Document data: ${documentSnapshot.data}")
                } else {
                    println("No such document!")
                }
            }
            .addOnFailureListener { exception ->
                println("Error getting document: ${exception.message}")
            }

        setContent {
            LoginSignUpUI(auth)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginSignUpUI(auth: FirebaseAuth) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val context = LocalContext.current
    val updatedContext = rememberUpdatedState(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Email
        Text("E-mail")
        TextField(
            value = email,
            onValueChange = { email = it },
            placeholder = { Text("Enter email") },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Password
        Text("Password")
        TextField(
            value = password,
            onValueChange = { password = it },
            placeholder = { Text("Enter password") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Sign Up Button
        Button(onClick = {
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = task.result?.user
                        val uid = user?.uid

                        val db = FirebaseFirestore.getInstance()
                        val usersRef = db.collection("users")
                        val newChat = usersRef.document()
                        val chatData = hashMapOf(
                            "uid" to uid,
                            "name" to "No Name"
                        )
                        newChat.set(chatData)

                        Toast.makeText(
                            updatedContext.value, "SignUp 成功: UID = $uid",
                            Toast.LENGTH_SHORT
                        ).show()

                        val intent = Intent(context, HomeActivity::class.java)
                        intent.putExtra("UID", uid)
                        context.startActivity(intent)
                    } else {
                        Toast.makeText(
                            updatedContext.value, "SignUp 失敗",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }) {
            Text("Sign Up")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Login Button
        Button(onClick = {
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val uid = auth.currentUser?.uid ?: "UID取得失敗"
                        Toast.makeText(
                            updatedContext.value, "Login 成功: $uid",
                            Toast.LENGTH_SHORT
                        ).show()

                        // ログイン成功時にホーム画面に移行
                        val intent = Intent(context, HomeActivity::class.java)
                        intent.putExtra("UID", uid)
                        context.startActivity(intent)
                    } else {
                        val errorMessage = task.exception?.message ?: "Unknown error"
                        Toast.makeText(
                            updatedContext.value, "Login 失敗: $errorMessage",
                            Toast.LENGTH_SHORT
                        ).show()
                        println(errorMessage)
                    }
                }
        }) {
            Text("Login")
        }
    }
}