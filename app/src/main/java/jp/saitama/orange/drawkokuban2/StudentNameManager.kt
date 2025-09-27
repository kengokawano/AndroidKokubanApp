package jp.saitama.orange.drawkokuban2

import android.content.Context
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

data class StudentNames(
    val names: List<String>
)

object StudentNameManager {
    private var cachedNames: List<String>? = null

    fun loadStudentNames(context: Context): List<String> {
        if (cachedNames != null) {
            return cachedNames!!
        }

        return try {
            val jsonString = context.assets.open("student_names.json").bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val namesArray = jsonObject.getJSONArray("names")
            val names = mutableListOf<String>()

            for (i in 0 until namesArray.length()) {
                names.add(namesArray.getString(i))
            }

            cachedNames = names
            names
        } catch (e: IOException) {
            // ファイルが見つからない場合のデフォルト名前
            listOf("田中 太郎", "佐藤 花子", "鈴木 一郎")
        } catch (e: Exception) {
            // その他のエラーの場合もデフォルト名前
            listOf("田中 太郎", "佐藤 花子", "鈴木 一郎")
        }
    }

    fun getTodaysDutyStudent(context: Context): String {
        val names = loadStudentNames(context)
        if (names.isEmpty()) return "未設定"

        // 今日の日付を基にシードを作成（同じ日は同じ人になる）
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val seed = today.hashCode().toLong()
        val random = Random(seed)

        return names[random.nextInt(names.size)]
    }
}