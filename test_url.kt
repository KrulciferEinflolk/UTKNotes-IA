import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun main() {
    val searchUrl = "https://www.googleapis.com/drive/v3/files?q=name='utk_notes_ia_backup.json' and trashed=false&spaces=appDataFolder"
    val parsed = searchUrl.toHttpUrlOrNull()
    println(parsed)
}
