
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import kotlinx.coroutines.*
import java.io.*



class SaveResolver(_mainActivity: Activity) {
    private var mainActivity: Activity

    init {
        mainActivity = _mainActivity
    }

    /**
     * TODO 촬영 후 파일 저장
     *
     * @param isSaved
     * @param resultByteArray 저장할 데이터
     */
     fun save(resultByteArray : ByteArray, fileName: String?) {
        val finalFileName = fileName ?: System.currentTimeMillis().toString() + ".jpg" // 파일 이름 현재 시간.jpg

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            //saveJPEG(resultByteArray, isSaved)
            //Q 버전 이상일 경우. (안드로이드 10, API 29 이상일 경우)
            saveImageOnAboveAndroidQ(resultByteArray, finalFileName)

        } else {
            // Q 버전 미만일 경우. (안드로이드 10, API 29 미만일 경우)
            saveJPEG(resultByteArray)
        }
    }



    //Android Q (Android 10, API 29 이상에서는 이 메서드를 통해서 이미지를 저장한다.)
    @SuppressLint("Range", "Recycle")
    @RequiresApi(Build.VERSION_CODES.Q)
    fun saveImageOnAboveAndroidQ(
        byteArray: ByteArray,
        fileName: String,
    ) {
        var result: Boolean = true
        var uri: Uri

        /* 새로운 파일 저장 */
        val values = ContentValues()
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/ImageSave")
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")

        uri = mainActivity.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        )!!


        val outputStream: OutputStream? = uri?.let {
            mainActivity.contentResolver.openOutputStream(it)
        }

        if (outputStream != null) {
            outputStream.write(byteArray)
            outputStream.flush()
            Thread.sleep(100) // 약간의 딜레이
            outputStream.close()
        }
    }


    fun saveJPEG(byteArray: ByteArray) {
        val fileName = System.currentTimeMillis().toString() + ".jpg" // 현재 시간
        val externalStorage = Environment.getExternalStorageDirectory().absolutePath
        val path = "$externalStorage/DCIM/imageSave"
        val dir = File(path)
        if (dir.exists().not()) {
            dir.mkdirs() // 폴더 없을경우 폴더 생성
        }
        try {
            val fos = FileOutputStream("$dir/$fileName")
            fos.write(byteArray) // ByteArray의 이미지 데이터를 파일에 쓰기
            fos.close()

            // 미디어 스캐닝을 통해 갤러리에 이미지를 등록
            MediaScannerConnection.scanFile(mainActivity, arrayOf("$dir/$fileName"), null) { _, uri ->

            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

}