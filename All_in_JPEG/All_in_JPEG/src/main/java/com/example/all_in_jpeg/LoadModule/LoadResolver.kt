package com.example.all_in_jpeg.LoadModule

import android.util.Log
import com.example.all_in_jpeg.Contents.Audio
import com.example.all_in_jpeg.Contents.ContentAttribute
import com.example.all_in_jpeg.Contents.Picture
import com.example.all_in_jpeg.Contents.Text
import com.example.all_in_jpeg.AllInContainer
import kotlinx.coroutines.*
import java.io.IOException


class LoadResolver() {

    /*
    byteArray로 변환된 이미지 데이터를 All-in Container 객체로 Deserialization하는 함수
     */
    suspend fun createAllInContainer(
        AllInContainer: AllInContainer,
        sourceByteArray: ByteArray
    ) : Boolean{
        val deferredResult = CompletableDeferred<Boolean>()
        CoroutineScope(Dispatchers.IO).launch {
            // All-in 세그먼트의 시작 위치를 찾음
            var AllinSegmentStartPos = 2
            AllinSegmentStartPos = findAllInSegmentStartPos(sourceByteArray)
            // All-in 세그먼트를 찾지 못함
            if (AllinSegmentStartPos == - 1) {
                try{
                    // 기존 JPEG으로 처리
                    Log.d("MCContainer", "일반 JPEG 생성")
                    AllInContainer.setBasicJepg(sourceByteArray)
                }catch (e : IOException){
                    Log.e("MCcontainer", "Basic JPEG Parsing 불가")
                }
                deferredResult.complete(false)
            }
            // All-in 세그먼트를 찾았을 때
            else {
                try {
                    var dataFieldLength = ByteArraytoInt(sourceByteArray, AllinSegmentStartPos)
                    // 1. ImageContent Pasrsing
                    var imageContentInfoSize =
                        ByteArraytoInt(sourceByteArray, AllinSegmentStartPos + 8)
                    var pictureList = async {
                        imageContentParsing(
                            AllInContainer,
                            sourceByteArray,
                            sourceByteArray.copyOfRange(
                                AllinSegmentStartPos + 12,
                                AllinSegmentStartPos + 16 + imageContentInfoSize
                            )
                        )
                    }
                    AllInContainer.imageContent.setContent(pictureList.await())

                    // 2. TextContent Pasrsing
                    var textContentStartOffset = AllinSegmentStartPos + 8 + imageContentInfoSize
                    var textContentInfoSize =
                        ByteArraytoInt(sourceByteArray, textContentStartOffset)
                    if (textContentInfoSize > 0) {
                        var textList = textContentParsing(
                            AllInContainer,
                            sourceByteArray.copyOfRange(
                                textContentStartOffset + 4,
                                textContentStartOffset + 8 + textContentInfoSize
                            )
                        )
                        AllInContainer.textContent.setContent(textList)
                    }

                    // 3. AudioContent Pasrsing
                    var audioContentStartOffset = textContentStartOffset + textContentInfoSize
                    var audioContentInfoSize =
                        ByteArraytoInt(sourceByteArray, audioContentStartOffset)
                    if (audioContentInfoSize > 0) {
                        var audioDataStartOffset =
                            ByteArraytoInt(sourceByteArray, audioContentStartOffset + 4)
                        var audioAttribute =
                            ByteArraytoInt(sourceByteArray, audioContentStartOffset + 8)
                        var audioDataLength =
                            ByteArraytoInt(sourceByteArray, audioContentStartOffset + 12)
                        var audioBytes = sourceByteArray.copyOfRange(
                            audioDataStartOffset,
                            audioDataStartOffset + audioDataLength
                        )
                        var audio = Audio(audioBytes, ContentAttribute.fromCode(audioAttribute))
                        AllInContainer.audioContent.setContent(audio)
                        AllInContainer.audioResolver.saveByteArrToAacFile(
                            audio._audioByteArray!!,
                            "viewer_record"
                        )
                    }
                } catch (e: IOException) {
                    Log.e("MCcontainer", "MC JPEG Parsing 불가")
                }
                deferredResult.complete(true)
            }
        }
        return deferredResult.await()
    }

    fun findAllInSegmentStartPos(sourceByteArray: ByteArray) : Int {
        // APP3 - ALL in JPEG 세그먼트의 시작 위치를 찾음
        var APP3_startOffset = 2
        while (APP3_startOffset < sourceByteArray.size - 1) {
            // found APP3 Marker
            if (sourceByteArray[APP3_startOffset] == 0xFF.toByte() && sourceByteArray[APP3_startOffset + 1] == 0xE3.toByte()) {
                //All-in JPEG 식별자가 존재할 때 TODO("All-in 과 MC과 합쳐져 있음. 후에 MC 지워주기")
                if(sourceByteArray[APP3_startOffset+6] == 0x4D.toByte() &&  sourceByteArray[APP3_startOffset+7] == 0x43.toByte()
                    && sourceByteArray[APP3_startOffset+8] == 0x46.toByte() ||
                    sourceByteArray[APP3_startOffset+6] == 0x41.toByte() &&  sourceByteArray[APP3_startOffset+7] == 0x69.toByte()
                    && sourceByteArray[APP3_startOffset+8] == 0x46.toByte()  ){
                    APP3_startOffset +=2
                    return APP3_startOffset
                }else {
                    // APP3 마커가 있지만 MC Format이 아님
                    return -1
                }
                //break`
            }
            APP3_startOffset++
        }
        // APP3 마커가 없음
        return -1
    }

    /**
    sourceByteArray를 All-in Container의 Image Content 객체로 Deserialization
     **/
    suspend fun imageContentParsing(AllInContainer: AllInContainer, sourceByteArray: ByteArray, imageInfoByteArray: ByteArray): ArrayList<Picture> = withContext(Dispatchers.Default) {
        var picture : Picture
        var pictureList : ArrayList<Picture> = arrayListOf()
        var startIndex = 0
        var imageCount = ByteArraytoInt(imageInfoByteArray, startIndex*4)
        startIndex++
        for(i in 0..imageCount -1){
            var offset = ByteArraytoInt(imageInfoByteArray, (startIndex*4))
            startIndex++
            var size = ByteArraytoInt(imageInfoByteArray, startIndex*4)
            startIndex++
            var attribute = ByteArraytoInt(imageInfoByteArray, startIndex*4)
            startIndex++
            var embeddedDataSize = ByteArraytoInt(imageInfoByteArray, startIndex*4)
            startIndex++
            var embeddedData : ArrayList<Int> = arrayListOf()
            if (embeddedDataSize > 0){
                var curInt : Int = 0
                for(j in 0..embeddedDataSize/4 -1){
                    // 4바이트 단위로 Int 생성
                    curInt = ByteArraytoInt(imageInfoByteArray, startIndex*4)
                    embeddedData.add(curInt)
                    startIndex++
                }
            }
            if(i==0){
                val jpegBytes = sourceByteArray.copyOfRange( offset,  offset + size - 1)
                // Jpeg Meta 데이터 추출
                var jpegMetaData = AllInContainer.imageContent.extractJpegMeta(sourceByteArray.copyOfRange(offset,
                     offset + size -1), ContentAttribute.fromCode(attribute))
                AllInContainer.setJpegMetaBytes(jpegMetaData)
                val frame =async {
                    AllInContainer.imageContent.extractFrame(jpegBytes,ContentAttribute.fromCode(attribute))
                }
                picture = Picture(offset, frame.await(), ContentAttribute.fromCode(attribute), embeddedDataSize, embeddedData)
                picture.waitForByteArrayInitialized()

            }else{
                // picture 생성
                picture = Picture(offset, sourceByteArray.copyOfRange( offset, offset + size - 1), ContentAttribute.fromCode(attribute), embeddedDataSize, embeddedData)
                picture.waitForByteArrayInitialized()
            }
            pictureList.add(picture)
            Log.d("Load_Module", "picutureList size : ${pictureList.size}")
        }
        return@withContext pictureList
    }



    /**
       ByteArray를 All-in Container의 Text Content 객체로 Deserialization
     **/
    fun textContentParsing(AllInContainer: AllInContainer, textInfoByteArray: ByteArray) : ArrayList<Text>{
        var textList : ArrayList<Text> = arrayListOf()
        var startIndex = 4
        var textCount = ByteArraytoInt(textInfoByteArray, 0)

        for(i in 0..textCount -1){
            var attribute = ByteArraytoInt(textInfoByteArray, startIndex)
            startIndex += 4
            var size = ByteArraytoInt(textInfoByteArray,  startIndex)
            startIndex += 4
            val charArray = CharArray(size) // 변환된 char 값들을 담을 배열
            if (size > 0){
                for(j in 0..size*2 -1 step 2){
                    // 4개씩 쪼개서 Int 생성
                    val charValue = ((textInfoByteArray[startIndex+j].toInt() and 0xFF) shl 8) or
                            ((textInfoByteArray[startIndex+j+1].toInt() and 0xFF) shl 0)
                    charArray[j / 2] = charValue.toChar() // char로 변환 후 배열에 저장
                }
                startIndex += size*2
            }
            Log.d("Load_Module", "${charArray.contentToString().toString()}")
            var string : String = String(charArray)
            var text = Text(string, ContentAttribute.fromCode(attribute))
            textList.add(text)
        }
        return textList
    }

    fun ByteArraytoInt(byteArray: ByteArray, stratOffset : Int): Int {
        var intNum :Int = ((byteArray[stratOffset].toInt() and 0xFF) shl 24) or
                ((byteArray[stratOffset+1].toInt() and 0xFF) shl 16) or
                ((byteArray[stratOffset+2].toInt() and 0xFF) shl 8) or
                ((byteArray[stratOffset+3].toInt() and 0xFF))
        return intNum
    }

}