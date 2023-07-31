package com.goldenratio.onepic

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.goldenratio.onepic.PictureModule.Contents.Picture
import com.goldenratio.onepic.PictureModule.MCContainer


class JpegViewModel(private val context:Context) : ViewModel() {

    val GALLERY_URI = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    var jpegMCContainer = MutableLiveData<MCContainer>()

    private var imageUriList = mutableListOf<String>() // 임시 데이터(원본이 사라져도)
    private val _imageUriLiveData = MutableLiveData<List<String>>() // 내부처리 데이터
    val imageUriLiveData: LiveData<List<String>> get() = _imageUriLiveData //읽기 전용

    private lateinit var urlHashMap:HashMap<String, Int>
    private lateinit var pictureByteArrayList:MutableList<ByteArray> // pictureByteArrayList

    var isGalleryUpdateFinished:MutableLiveData<Boolean> = MutableLiveData(false)


//    var mainPictureIndex: Int = 0
//    var selectPictureIndex: Int = 0

    /* Edit에서 필요 */
    var currentImageUri:String? = null // 현재 메인 이미지 uri(13 이상)
    var selectedSubImage: Picture? = null // 선택된 서브 이미지 picture 객체
    var mainSubImage: Picture? = null // 선택된 서브 이미지 picture 객체

    var isAudioPlay = MutableLiveData<Int>(0)
    var currentFileName : String? = null

    companion object{
        var isUserInentFinish : Boolean = false
        var AllInJPEG : Boolean = true
    }



    private val galleryObserver = object : ContentObserver(android.os.Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            // 갤러리 변화가 있을 때마다 호출됩니다.
            // 이곳에서 LiveData 값을 변경합니다.
            Log.d("갤러리 변화가 있을 때","호출 됩니다!!!")
            getAllPhotos()
        }
    }

    init {
        // ContentObserver를 등록합니다.
        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            galleryObserver
        )
//        selectPictureIndex = 0
//        mainPictureIndex = 0
    }

    fun setContainer(MCContainer: MCContainer) {
        jpegMCContainer.value = MCContainer
    }

    fun setpictureByteArrList(byteArrayList:MutableList<ByteArray>){
        pictureByteArrayList = byteArrayList
    }

    fun setPictureByteList(byteArray: ByteArray, index: Int) {
        if(pictureByteArrayList.size > index)
            pictureByteArrayList[index] = byteArray
    }

    fun getPictureByteArrList():MutableList<ByteArray> {
        return pictureByteArrayList
    }

    fun setCurrentImageUri(position:Int){ // 현재 메인 이미지 filePath 설정
        if (currentImageUri != null) // 초기화
            currentImageUri = null
        this.currentImageUri = imageUriLiveData.value!!.get(position)
    }

    fun setselectedSubImage(picture:Picture?){ // 선택된 서브 이미지 picture 객체 설정
        if (selectedSubImage != null) // 초기화
            selectedSubImage = null
        this.selectedSubImage = picture
//        selectPictureIndex = jpegMCContainer.value!!.imageContent.pictureList.indexOf(picture)
    }

    fun getSelectedSubImageIndex(): Int {
        val index = jpegMCContainer.value!!.imageContent.pictureList.indexOf(selectedSubImage)
        return if(index == -1) 0
        else index
    }

    fun getMainSubImageIndex(): Int {
        if(mainSubImage == null) {
            Log.d("mainSub Index", "mainSub Index : 0")
            return 0
        }
        val index =  jpegMCContainer.value!!.imageContent.pictureList.indexOf(mainSubImage)
        if(index == -1) {
            return 0
        }
        Log.d("mainSub Index", "mainSub Index : $index")
        return index
    }

    fun updateImageUriData(uriList: MutableList<String>) {
        this.imageUriList = uriList
        _imageUriLiveData.value = imageUriList
        urlHashMap = HashMap<String,Int>()
        for (i in 0 until uriList.size) {
            urlHashMap.put(uriList[i],i)
        }
        isGalleryUpdateFinished.value = true
    }

    fun getAllPhotos(){

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU){
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                null,
                null,
                null,
                MediaStore.Images.ImageColumns.DATE_TAKEN + " DESC"
            )
            val uriList = mutableListOf<String>()
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    // 이미지 URI 가져오기
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                        id
                    ).toString()
                    uriList.add(uri)
                }
                cursor.close()
                updateImageUriData(uriList)
            }

        }
        else {
            val cursor = context.contentResolver.query(
                GALLERY_URI,
                null,
                null,
                null,
                MediaStore.Images.ImageColumns.DATE_TAKEN + " DESC")
            val uriList = mutableListOf<String>()
            if(cursor!=null){
                while(cursor.moveToNext()){
                    // 사진 경로 FilePath 가져오기
                    val uri = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                    uriList.add(uri)
                }
                cursor.close()
                updateImageUriData(uriList)
            }
        }
    }

    fun getFilePathIdx(key:String):Int? {
        if (urlHashMap.containsKey(key)) {
            return urlHashMap.get(key)
        }
        return null
    }

    fun getFileNameFromUri(uri: Uri): String {
        val documentFile = DocumentFile.fromSingleUri(context, uri)
        return documentFile?.name!!
    }
}