package com.example.projectmdp

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
        }
    }
}