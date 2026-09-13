package me.rerere.rikkahub.data.files

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import me.rerere.rikkahub.data.db.dao.ManagedFileDAO
import me.rerere.rikkahub.data.db.entity.ManagedFileEntity
import me.rerere.rikkahub.data.repository.FilesRepository
import kotlin.uuid.Uuid

internal fun testFilesManager(
    root: Path = Path(SystemTemporaryDirectory, "files-test-${Uuid.random()}"),
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    asyncFileIo: Boolean = true,
): FilesManager = FilesManager(root, FilesRepository(TestManagedFileDAO()), scope, asyncFileIo = asyncFileIo)

private class TestManagedFileDAO : ManagedFileDAO {
    private val rows = MutableStateFlow<List<ManagedFileEntity>>(emptyList())
    private val mutex = Mutex()
    private var nextId = 1L

    override suspend fun insert(file: ManagedFileEntity): Long = mutex.withLock {
        val id = if (file.id == 0L) nextId++ else file.id
        rows.value = rows.value.filterNot { it.id == id || it.relativePath == file.relativePath } + file.copy(id = id)
        id
    }
    override suspend fun update(file: ManagedFileEntity) = mutex.withLock {
        rows.value = rows.value.map { if (it.id == file.id) file else it }
    }
    override suspend fun getById(id: Long) = rows.value.find { it.id == id }
    override suspend fun getByPath(relativePath: String) = rows.value.find { it.relativePath == relativePath }
    override fun listByFolder(folder: String) = rows.map { files ->
        files.filter { it.folder == folder }.sortedByDescending { it.createdAt }
    }
    override suspend fun deleteById(id: Long): Int = remove { it.id == id }
    override suspend fun deleteByPath(relativePath: String): Int = remove { it.relativePath == relativePath }
    override suspend fun deleteByFolder(folder: String): Int = remove { it.folder == folder }
    private suspend fun remove(predicate: (ManagedFileEntity) -> Boolean): Int = mutex.withLock {
        val count = rows.value.count(predicate)
        rows.value = rows.value.filterNot(predicate)
        count
    }
}
