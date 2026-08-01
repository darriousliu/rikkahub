package me.rerere.rikkahub.data.db.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.FolderEntity

@Dao
interface FolderDAO {
    @Query("SELECT * FROM conversation_folder WHERE assistant_id = :assistantId ORDER BY sort_index ASC, create_at ASC")
    fun getFoldersOfAssistant(assistantId: String): Flow<List<FolderEntity>>

    @Query("SELECT * FROM conversation_folder WHERE id = :id")
    suspend fun getFolderById(id: String): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity)

    @Update
    suspend fun update(folder: FolderEntity)

    @Query("UPDATE conversation_folder SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Delete
    suspend fun delete(folder: FolderEntity)

    @Query("DELETE FROM conversation_folder WHERE id = :id")
    suspend fun deleteById(id: String)
}
