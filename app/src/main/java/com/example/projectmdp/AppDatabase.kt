package com.example.projectmdp
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RememberUser::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun RememberedUserDAO(): RememberedUserDAO
    companion object{
        @Volatile
        private var INSTANCE: AppDatabase?=null
        fun getInstance(context: Context): AppDatabase{
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context,
                    AppDatabase::class.java, "rememberme_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}