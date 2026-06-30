package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CloudAccountDao {
    @Query("SELECT * FROM cloud_accounts")
    fun getAllAccounts(): Flow<List<CloudAccount>>

    @Query("SELECT * FROM cloud_accounts")
    suspend fun getAllAccountsDirect(): List<CloudAccount>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: CloudAccount)

    @Delete
    suspend fun deleteAccount(account: CloudAccount)

    @Query("DELETE FROM cloud_accounts WHERE id = :id")
    suspend fun deleteAccountById(id: String)

    @Query("DELETE FROM cloud_accounts")
    suspend fun clearAll()
}
