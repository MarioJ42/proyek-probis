package com.example.projectmdp

<<<<<<< Updated upstream
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegisterActivity : AppCompatActivity() {
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        db = AppDatabase.getDatabase(this)

        val fullNameEditText = findViewById<EditText>(R.id.etFullName)
        val emailEditText = findViewById<EditText>(R.id.etEmail)
        val passwordEditText = findViewById<EditText>(R.id.etPassword)
        val registerButton = findViewById<Button>(R.id.btnRegister)
        val loginTextView = findViewById<TextView>(R.id.tvLogin)

        registerButton.setOnClickListener {
            val fullName = fullNameEditText.text.toString()
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()

            if (fullName.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            CoroutineScope(Dispatchers.IO).launch {
                val existingUser = db.userDao().getUserByEmail(email)
                withContext(Dispatchers.Main) {
                    if (existingUser != null) {
                        Toast.makeText(this@RegisterActivity, "Email already registered", Toast.LENGTH_SHORT).show()
                    } else {
                        val user = User(
                            fullName = fullName,
                            email = email,
                            password = password,
                            balance = 50000.0 // Set initial balance to 50,000
                        )
                        println("DEBUG: Registering user: $user")
                        CoroutineScope(Dispatchers.IO).launch {
                            db.userDao().insertUser(user)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@RegisterActivity, "Registration successful", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this@RegisterActivity, loginActivity::class.java))
                                finish()
                            }
                        }
                    }
                }
            }
        }

        loginTextView.setOnClickListener {
            startActivity(Intent(this, loginActivity::class.java))
=======
import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil
import com.example.projectmdp.databinding.ActivityRegister2Binding

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegister2Binding
    val viewModel by viewModels<UserViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this,R.layout.activity_register2)
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }
//        viewModel.init()
        binding.viewModel = viewModel
        binding.lifecycleOwner = this
        binding.btnRegister.setOnClickListener{
            val name = binding.etFullName.text.toString()
            val password = binding.etPassword.text.toString()
            val email = binding.etEmail.text.toString()
            if(name == "" || password == ""||email==""){
                Toast.makeText(this, "ada yang kosong", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }else{
                val user = UserEntity(email = email, password = password, name = name)
                viewModel.putUser(user)


            }
>>>>>>> Stashed changes
        }
    }
}