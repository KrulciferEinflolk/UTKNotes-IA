fun main() {
    println(java.net.URLEncoder.encode("name='utk_notes_ia_backup.json' and trashed=false", "UTF-8").replace("+", "%20"))
}
