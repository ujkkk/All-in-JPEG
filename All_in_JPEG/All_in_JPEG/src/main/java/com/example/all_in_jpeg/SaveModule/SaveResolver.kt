package com.example.all_in_jpeg.SaveModule

import android.annotation.SuppressLint
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.example.all_in_jpeg.AllInContainer
import com.example.all_in_jpeg.Contents.Picture
import java.io.*


class SaveResolver(_mainActivity: Activity, _allInContainer: AllInContainer) {
    private var allInContainer: AllInContainer
    private var mainActivity: Activity

    init {
        allInContainer = _allInContainer
        mainActivity = _mainActivity
    }

    /**
     *  AllInContainer 객체를 파일로 저장하는 함수. 저장된 URI를 리턴
     */
    fun save(isAllInJPEG : Boolean): String {
        var savedUri: String = ""
        var resultByteArray = serializeAllInContainer(isAllInJPEG)
        val fileName = System.currentTimeMillis().toString() + ".jpg" // 파일이름 현재시간.jpg

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            //Q 버전 이상일 경우. (안드로이드 10, API 29 이상일 경우)
            savedUri = saveImageOnAboveAndroidQ(resultByteArray, fileName)
            resultByteArray = ByteArray(0)
        } else {
            // Q 버전 이하일 경우. 저장소 권한을 얻어온다.
            val writePermission = mainActivity?.let {
                ActivityCompat.checkSelfPermission(
                    it,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
            if (writePermission == PackageManager.PERMISSION_GRANTED) {
                savedUri = saveImageOnUnderAndroidQ(resultByteArray)
                // Toast.makeText(context, "이미지 저장이 완료되었습니다.", Toast.LENGTH_SHORT).show()
            } else {
                val requestExternalStorageCode = 1

                val permissionStorage = arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )

                ActivityCompat.requestPermissions(
                    mainActivity as Activity,
                    permissionStorage,
                    requestExternalStorageCode
                )
            }
        }
        return savedUri!!

    }

    /**
     * AllInContainer 객체를 기존에 존재하는 fileName과 같은 uri에 저장하여 파일을 덮어 씌우는 함수. 저장된 URI를 리턴
     */
    fun overwriteSave(isAllInJPEG : Boolean, fileName: String): String {
        var savedUri: String = ""
        var resultByteArray = serializeAllInContainer(isAllInJPEG)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            //Q 버전 이상일 경우. (안드로이드 10, API 29 이상일 경우)
            savedUri = saveImageOnAboveAndroidQ(resultByteArray, fileName)
            resultByteArray = ByteArray(0)

        } else {
            // Q 버전 이하일 경우. 저장소 권한을 얻어온다.
            val writePermission = mainActivity?.let {
                ActivityCompat.checkSelfPermission(
                    it,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
            if (writePermission == PackageManager.PERMISSION_GRANTED) {
                savedUri = saveImageOnUnderAndroidQ(resultByteArray)

            } else {
                val requestExternalStorageCode = 1
                val permissionStorage = arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )

                ActivityCompat.requestPermissions(
                    mainActivity as Activity,
                    permissionStorage,
                    requestExternalStorageCode
                )
            }
        }
        return savedUri


    }

    /**
     * Android Q (Android 10, API 29 이상에서는 이 메서드를 통해서 이미지를 저장 하고 저장한 URI를 리턴
     */
    @SuppressLint("Range", "Recycle")
    @RequiresApi(Build.VERSION_CODES.Q)
    fun saveImageOnAboveAndroidQ(byteArray: ByteArray, fileName: String): String {
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
        return uri.toString()
    }



    @RequiresApi(Build.VERSION_CODES.Q)
    fun deleteImage(fileName: String) {
        // 이미지를 조회하기 위한 쿼리
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(fileName)
        val contentResolver = mainActivity.contentResolver
        val queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        // 이미지 조회
        val cursor = contentResolver.query(queryUri, null, selection, selectionArgs, null)
        cursor?.use { cursor ->
            if (cursor.moveToFirst()) {
                try {
                    // 이미지가 존재하는 경우
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val imageUri = ContentUris.withAppendedId(queryUri, cursor.getLong(idColumn))

                    // 이미지 삭제
                    val deletedRows = contentResolver.delete(imageUri, null, null)
                    if (deletedRows > 0) {
                        Log.d("save_test", "이미지 삭제 성공")
                    } else {
                        Log.d("save_test", "이미지 삭제 실패")
                    }
                  //  JpegViewModel.isUserInentFinish = true

                } catch (e: SecurityException) {
                    // 사용자 요청 메시지를 보냄
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && e is RecoverableSecurityException) {
                        val intentSender = e.userAction.actionIntent.intentSender
                        mainActivity.startIntentSenderForResult(
                            intentSender,
                            1,
                            null,
                            0,
                            0,
                            0,
                            null
                        )
                        Log.d("save_test", "삭제 중 예외 처리 (우리 앱 사진이 아님)")
                    } else {
                        // 예외 처리
                        Log.d("save_test", "삭제 중 예외 처리 (우리 앱 사진이 아님) && 버전 13이하")
                    }

                }

            } else {
                // 이미지가 존재하지 않는 경우
                Log.d("save_test", "이미지가 존재 하지 않음")
             //   JpegViewModel.isUserInentFinish = true

            }
        }
        //JpegViewModel.isUserInentFinish = true
    }


    private fun saveImageOnUnderAndroidQ(byteArray: ByteArray): String {
        val fileName = System.currentTimeMillis().toString() + ".jpg"
        val externalStorage = Environment.getExternalStorageDirectory().absolutePath
        val path = "$externalStorage/DCIM/imageSave"
        val dir = File(path)
        val fileItem = File("$dir/$fileName")
        if (dir.exists().not()) {
            dir.mkdirs() // 폴더 없을경우 폴더 생성
        }
        try {
            fileItem.createNewFile()
            //0KB 파일 생성.

            val fos = FileOutputStream(fileItem) // 파일 아웃풋 스트림
            fos.write(byteArray)
            // bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            //파일 아웃풋 스트림 객체를 통해서 Bitmap 압축.

            fos.close() // 파일 아웃풋 스트림 객체 close

            mainActivity.sendBroadcast(
                Intent(
                    Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                    Uri.fromFile(fileItem)
                )
            )
            // 브로드캐스트 수신자에게 파일 미디어 스캔 액션 요청. 그리고 데이터로 추가된 파일에 Uri를 넘겨준다.

        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return fileItem.toString()
    }

    fun singleImageSave(picture: Picture) {
        val byteBuffer = ByteArrayOutputStream()
        var jpegMetaData = allInContainer.imageContent.jpegMetaData

        byteBuffer.write(jpegMetaData, 0, jpegMetaData.size)
        byteBuffer.write(picture._pictureByteArray)
        byteBuffer.write(0xff)
        byteBuffer.write(0xd9)

        val singleJpegBytes = byteBuffer.toByteArray()
        val fileName = System.currentTimeMillis().toString() + ".jpg" // 파일이름 현재시간.jpg

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            //Q 버전 이상일 경우. (안드로이드 10, API 29 이상일 경우)
            saveImageOnAboveAndroidQ(singleJpegBytes, fileName)
        } else {
            // Q 버전 이하일 경우. 저장소 권한을 얻어온다.
            val writePermission = mainActivity?.let {
                ActivityCompat.checkSelfPermission(
                    it,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
            if (writePermission == PackageManager.PERMISSION_GRANTED) {
                saveImageOnUnderAndroidQ(singleJpegBytes)
                Toast.makeText(mainActivity, "이미지 저장이 완료되었습니다.", Toast.LENGTH_SHORT).show()
            } else {
                val requestExternalStorageCode = 1

                val permissionStorage = arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )

                ActivityCompat.requestPermissions(
                    mainActivity as Activity,
                    permissionStorage,
                    requestExternalStorageCode
                )
            }

        }
        /// return savedFile!!
    }

    fun serializeAllInContainer(isAllInJPEG : Boolean): ByteArray {
        // 순서는 이미지 > 텍스트 > 오디오
        val byteBuffer = ByteArrayOutputStream()
        //Jpeg Meta
        var jpegMetaData = allInContainer.imageContent.jpegMetaData

        // 일반 JPEG으로 저장
        if (!isAllInJPEG) {
            Log.d("save_test", "1. 일반 JPEG으로 저장하기")
            byteBuffer.write(jpegMetaData, 0, jpegMetaData.size)
            Log.d("save_test", "저장하는 메타데이터 사이즈 ${byteBuffer.size()}")
            var picture = allInContainer.imageContent.getPictureAtIndex(0)
            byteBuffer.write(/* b = */ picture!!._pictureByteArray)
            byteBuffer.write(0xff)
            byteBuffer.write(0xd9)

        // ALL in JPEG format으로 저장
        } else {
            Log.d("save_test", "2. all in jpeg으로 저장")
            // APP3 삽입 위치 찾기 ( APP0, APP1, APP2 중 가장 늦게 나온 마커 뒤에)
            var lastAppMarkerOffset = 0
            var pos = 2
            while (pos < jpegMetaData.size - 1) {
                // APP0
                if (jpegMetaData[pos] == 0xFF.toByte() && jpegMetaData[pos + 1] == 0xE0.toByte()){
                    lastAppMarkerOffset = pos
                    Log.d("save_test", "APP0 find :  ${lastAppMarkerOffset}")
                }
                else if (jpegMetaData[pos] == 0xFF.toByte() && jpegMetaData[pos + 1] == 0xE1.toByte()){
                    lastAppMarkerOffset = pos
                    Log.d("save_test", "APP1 find :  ${lastAppMarkerOffset}")
                }
                else if (jpegMetaData[pos] == 0xFF.toByte() && jpegMetaData[pos + 1] == 0xE2.toByte()){
                    lastAppMarkerOffset = pos
                    Log.d("save_test", "APP2 find :  ${lastAppMarkerOffset}")
                }
                pos++
            }

            // APPn(0,1,2) 마커를 찾음
            if(lastAppMarkerOffset != 0){
                var lastAppMarkerDataLength = ((jpegMetaData[lastAppMarkerOffset + 2].toInt() and 0xFF) shl 8) or
                        ((jpegMetaData[lastAppMarkerOffset + 3].toInt() and 0xFF) shl 0)
                Log.d("save_test", "데이터 크기 :  ${lastAppMarkerDataLength}")

                // APPn 마커에 따라 APPn 데이터 크기가 없을 수 있다.
                if(lastAppMarkerDataLength + lastAppMarkerOffset > jpegMetaData.size || lastAppMarkerDataLength == 0){
                    lastAppMarkerDataLength = 2
                }
                // 마지막 APPn(0,1,2) 세그먼트 데이터까지 write
                byteBuffer.write(jpegMetaData, 0, lastAppMarkerOffset + lastAppMarkerDataLength)
                // APP3 데이터 생성
                allInContainer.settingHeaderInfo()
                var APP3ExtensionByteArray = allInContainer.convertHeaderToBinaryData()
                // APP3 데이터 write
                byteBuffer.write(APP3ExtensionByteArray)
                //나머지 첫번째 사진의 메타 데이터 쓰기
                byteBuffer.write(
                    jpegMetaData,
                    lastAppMarkerOffset + lastAppMarkerDataLength,
                    jpegMetaData.size - (lastAppMarkerOffset + lastAppMarkerDataLength)
                )
            }
            else{
                // APPn(0,1,2) 마커가 없음
                byteBuffer.write(jpegMetaData, 0, 2)
                // APP3 데이터 생성
                allInContainer.settingHeaderInfo()
                var APP3ExtensionByteArray = allInContainer.convertHeaderToBinaryData()
                // APP3 데이터 write
                byteBuffer.write(APP3ExtensionByteArray)
                //SOI 제외한 메타 데이터 write
                byteBuffer.write(
                    jpegMetaData,
                    2,
                    jpegMetaData.size - (2)
                )
            }

            // Imgaes Data write
            for (i in 0..allInContainer.imageContent.pictureCount - 1) {
                var picture = allInContainer.imageContent.getPictureAtIndex(i)
                byteBuffer.write(/* b = */ picture!!._pictureByteArray)
                if (i == 0) {
                    //EOI 작성
                    byteBuffer.write(0xff)
                    byteBuffer.write(0xd9)
                }
            }
            // Audio Write
            if (allInContainer.audioContent.audio != null) {
                var audio = allInContainer.audioContent.audio
                byteBuffer.write(/* b = */ audio!!._audioByteArray)
            }
        }
        return byteBuffer.toByteArray()
    }

    fun byteArrayToBitmap(byteArray: ByteArray): Bitmap {
        return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    }


}