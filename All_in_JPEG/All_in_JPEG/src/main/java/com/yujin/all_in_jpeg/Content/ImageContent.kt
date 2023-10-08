

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer


/**
 * 하나 이상의 Picture(이미지)를 담는 컨테이너
 */
class ImageContent {

    var pictureList : ArrayList<Picture> = arrayListOf()
    var pictureCount = 0

    lateinit var jpegHeader : ByteArray
    lateinit var mainPicture : Picture


    constructor()

    fun init() {
        pictureList.clear()
        pictureCount = 0
    }

    /**
     * TODO ImageContent 갱신 - 카메라 찍을 때 호출되는 함수
     *
     * @param byteArrayList
     * @param contentAttribute
     * @return
     */
    suspend fun setContent(byteArrayList: ArrayList<ByteArray>, contentAttribute : ContentAttribute) : Boolean = withContext(Dispatchers.Default){
        init()
        var sum = 0
        // 메타 데이터 분리
        jpegHeader = extractMetaDataFromFirstImage(byteArrayList.get(0))
        for(i in 0 until byteArrayList.size){
            val singleJpegBytes = byteArrayList.get(i)
            sum += singleJpegBytes.size

            // 메타 데이터 분리
            val frameStartPos = getFrameStartPos(singleJpegBytes)
            val metaData = singleJpegBytes.copyOfRange(0, frameStartPos)

            // frame 분리
            var frameBytes = async {
                extractFrame(byteArrayList.get(i))
            }
             // Picture 객체 생성
            var picture = Picture(contentAttribute, metaData, frameBytes.await())
            picture.waitForByteArrayInitialized()
            insertPicture(picture)

            if(i == 0){
                mainPicture = picture
            }
        }
        return@withContext true
    }

    /**
        TODO   ImageContent 리셋 후 초기화 - 파일을 parsing할 때 ImageContent를 생성
     */
    fun setContent(_pictureList : ArrayList<Picture>){
        init()
        // frame만 있는 pictureList
        pictureList = _pictureList
        pictureCount = _pictureList.size
        mainPicture = pictureList.get(0)
    }

    /**
        TODO ImageContent 리셋 후 초기화 - 파일을 parsing할 때 일반 JPEG 생성
     */
    fun setBasicContent(singleJpegBytes: ByteArray){
        init()
        // 메타 데이터 분리
        val frameStartPos = getFrameStartPos(singleJpegBytes)
        jpegHeader = singleJpegBytes.copyOfRange(0, frameStartPos)

        var frameBytes : ByteArray = extractFrame(singleJpegBytes)
        // Picture 객체 생성
        var picture = Picture(ContentAttribute.basic, jpegHeader, frameBytes)
        picture.waitForByteArrayInitialized()
        insertPicture(picture)
        mainPicture = pictureList[0]
    }

    /**
     *  TODO metaData와 Picture의 byteArray(frmae)을 붙여서 완전한 JPEG파일의 Bytes를 리턴하는 함수
     */
    fun getJpegBytes(picture : Picture) : ByteArray{
        var buffer = ByteBuffer.allocate(picture._mataData!!.size + picture.imageSize+2)
        buffer.put(picture._mataData)
        buffer.put(picture._pictureByteArray)
        buffer.put("ff".toInt(16).toByte())
        buffer.put("d9".toInt(16).toByte())
        return buffer.array()
    }


    /**
        TODO JPEG 파일의 데이터에서 metaData 부분을 찾아 리턴 하는 함수
     */
    fun extractMetaDataFromFirstImage(bytes: ByteArray) : ByteArray {
        var metaDataEndPos = getFrameStartPos(bytes)

        // Ai JPEG Format 인지 체크
        val (APP3StartIndx, APP3DataLength) = findAiformat(bytes)
        Log.d("AiJPEG", "[meta]APP3StartIndx : ${APP3StartIndx}, APP3DataLength : ${APP3DataLength}" )
        // write
        var resultByte: ByteArray
        val byteBuffer = ByteArrayOutputStream()

        //  Ai JPEG Format 일 때
        if (APP3StartIndx > 0) {
            //  APP3 (Ai jpeg) 영역을 제외하고 metadata write
            Log.d("AiJPEG", "[meta]extract_metadata : MC 포맷이여서 APP3 메타 데이터 뺴고 저장")
            byteBuffer.write(bytes, 0, APP3StartIndx)
            byteBuffer.write(
                bytes,
                APP3StartIndx + APP3DataLength ,
                metaDataEndPos - (APP3StartIndx + APP3DataLength )
            )
            resultByte = byteBuffer.toByteArray()

        //  Ai JPEG Format이 아닐 때
        } else {
            Log.d("AiJPEG", "[meta]extract_metadata : 일반 JEPG처럼 저장 pos : ${metaDataEndPos}")
            // SOF 전까지 추출
            resultByte = bytes.copyOfRange(0, metaDataEndPos)

        }
        Log.d("AiJPEG", "[meta] 추출한 메타데이터 사이즈 ${resultByte.size}")
        return resultByte
    }



    /**
     * TODO Frame(SOF 시작 or 2 번째 JFIF) 시작 위치를 리턴
     *
     * @param jpegBytes
     * @param attribute
     * @return
     */
    fun getFrameStartPos(jpegBytes: ByteArray) : Int{
        var startIndex = 0
        var SOFList : ArrayList<Int>

        // SOF가 나온 위치부터 프레임으로 추출
        SOFList = getSOFMarkerPosList(jpegBytes)
        if(SOFList.size > 0){
            startIndex = SOFList.last()
        }
        else {
            return 0
        }
        return startIndex
    }


    /**
     * TODO JPEG 파일 데이터의 프레임(SOF ~EOI 전) 데이터를 찾아 ByteArray에 담아 리턴
     */
    fun extractFrame(jpegBytes: ByteArray): ByteArray {
        var pos = 0
        var endIndex = jpegBytes.size

        // Frame Start pos 찾기
        val frameStartPos = getFrameStartPos(jpegBytes)

        // Frame end Pos 찾기
        pos = jpegBytes.size-2
        while (pos > 0) {
            if (jpegBytes[pos] == 0xFF.toByte() && jpegBytes[pos + 1] == 0xD9.toByte()) {
                endIndex = pos
                break
            }
            pos--
        }
        // 프레임 추출
        val frameBytes = jpegBytes.copyOfRange(frameStartPos, endIndex)
        return frameBytes
    }


    /*
       TODO JPEG 데이터의 EOI 마커 위치를 찾아 리턴
    */
    fun getEOIMarekrPosList(jpegBytes: ByteArray) : ArrayList<Int>{
        var EOIStartInex = 0
        var EOIList : ArrayList<Int> = arrayListOf()
        // SOF 시작 offset 찾기
        while (EOIStartInex < jpegBytes.size- 1) {
            if (jpegBytes[EOIStartInex] == 0xFF.toByte() && jpegBytes[EOIStartInex+1] == 0xD9.toByte()) {
                EOIList.add(EOIStartInex)
            }
            EOIStartInex++
        }
        return EOIList
    }

    /*
       TODO JPEG 데이터의 SOF 마커들 위치를 찾아 리스트로 리턴
    */
    fun getSOFMarkerPosList (jpegBytes: ByteArray) : ArrayList<Int> {
        val EOIPosList = getEOIMarekrPosList(jpegBytes)

        var SOFStartInex = 0
        var SOFList : ArrayList<Int> = arrayListOf()
        // SOF 시작 offset 찾기
        while (SOFStartInex < jpegBytes.size/2 - 1) {
            var countFindingEOI = 0
            if (jpegBytes[SOFStartInex] == 0xFF.toByte() && jpegBytes[SOFStartInex+1] == 0xC0.toByte()) {
                SOFList.add(SOFStartInex)
            }
            SOFStartInex++

            if(EOIPosList.size > 0){
                if (SOFStartInex == EOIPosList.last())
                    break
            }

        }
        return SOFList
    }


    /*
        TODO All-in 포맷인지 식별후 APP3 segment 시작 위치와 크기 리턴
     */
    fun findAiformat(jpegBytes: ByteArray) : Pair<Int,Int>{
        var app3StartIndex = 0
        var app3DataLength = 0
        // MC Format인지 확인 - MC Format일 경우 APP3 데이터 빼고 set
        while (app3StartIndex < jpegBytes.size - 1) {
            // APP3 마커가 있는 경우
            if (jpegBytes[app3StartIndex] == 0xFF.toByte() && jpegBytes[app3StartIndex + 1] == 0xE3.toByte()) {
                //MC Format인지 확인
                if (jpegBytes[app3StartIndex+4] == 0x41.toByte() &&  jpegBytes[app3StartIndex+5] == 0x69.toByte()
                    && jpegBytes[app3StartIndex+6] == 0x46.toByte()
                ) {
                    app3DataLength = ((jpegBytes[app3StartIndex +2].toInt() and 0xFF) shl 8) or
                        ((jpegBytes[app3StartIndex +3].toInt() and 0xFF) shl 0)
                    break
                }
            }
            app3StartIndex++
        }
        if(app3StartIndex == jpegBytes.size - 1) app3StartIndex = 0
        return Pair(app3StartIndex, app3DataLength)
    }

    fun checkAttribute(attribute: ContentAttribute): Boolean {
        for(i in 0 until pictureList.size){
            if(pictureList[i].contentAttribute == attribute)
                return true
        }
        return false
    }

    fun insertPicture(picture : Picture){
        pictureList.add(picture)
        pictureCount += 1
    }

    fun insertPicture(index : Int, picture : Picture){
        pictureList.add(index, picture)
        pictureCount += 1
        Log.d("error 잡기", "insertPicture pictureCount= ${pictureCount} ")
    }

    fun removePicture(picture: Picture) : Boolean{
        val index = pictureList.indexOf(picture)
        if(index > 0) {
            val result = pictureList.remove(picture)
            pictureCount -= 1
            return result
        }

        return false
    }

    fun getPictureAtIndex(index : Int): Picture? {
        return pictureList.get(index) ?: null
    }
}
