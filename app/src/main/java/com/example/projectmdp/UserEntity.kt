package com.example.projectmdp
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "email") val email:String,
    @ColumnInfo(name = "password") var password:String,
    @ColumnInfo(name = "name") val name:String
){
    override fun toString(): String {
        return "$name - $email "
    }
}