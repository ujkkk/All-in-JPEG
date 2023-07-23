package com.example.all_in_jpeg

import com.example.all_in_jpeg.Info.AudioContentInfo
import com.example.all_in_jpeg.Info.ImageContentInfo
import com.example.all_in_jpeg.Info.TextContentInfo
import java.nio.ByteBuffer

class Header(_MC_container : AllInContainer) {

    var headerDataLength = 0

    private var AllInContainer : AllInContainer
    lateinit var imageContentInfo : ImageContentInfo
    lateinit var audioContentInfo : AudioContentInfo
    lateinit var textContentInfo: TextContentInfo

    init {
        AllInContainer =_MC_container
    }

    // MC Container에 채워진 Content의 정보를 Info 클래스들로 생성
    fun settingHeaderInfo(){
        imageContentInfo = ImageContentInfo(AllInContainer.imageContent)
        textContentInfo = TextContentInfo(AllInContainer.textContent)
        audioContentInfo = AudioContentInfo(AllInContainer.audioContent,imageContentInfo.getEndOffset()+3)
        headerDataLength = getAPP3FieldLength()
        applyAddedSize()
    }
    //추가한 APP3 extension + JpegMeta data 만큼 offset 변경
    fun applyAddedSize(){
        // 추가할 APP3 extension 만큼 offset 변경 - APP3 marker(2) + APP3 Data field length + EOI
        var headerLength = getAPP3FieldLength() + 2
        var jpegMetaLength = AllInContainer.getJpegMetaBytes().size
        for(i in 0..imageContentInfo.imageCount-1){
            var pictureInfo = imageContentInfo.imageInfoList.get(i)
            if(i == 0){
                pictureInfo.dataSize += (headerLength+jpegMetaLength) + 3
            }else{
                pictureInfo.offset += (headerLength+jpegMetaLength) + 2
            }
        }
        audioContentInfo.dataStartOffset += (headerLength+jpegMetaLength)
        //textContentInfo.dataStartOffset += (headerLength+jpegMetaLength)
    }
    fun getAPP3FieldLength(): Int{
        var size = 0
        size += imageContentInfo.getLength()
        size += textContentInfo.getLength()
        size += audioContentInfo.getLength()
        return size + 10
    }
    fun convertBinaryData() : ByteArray {
        val buffer: ByteBuffer = ByteBuffer.allocate(getAPP3FieldLength() + 2)
        buffer.put("ff".toInt(16).toByte())
        buffer.put("e3".toInt(16).toByte())
        buffer.putInt(headerDataLength)
        // A, I, F, 0
        buffer.put(0x41.toByte())
        buffer.put(0x69.toByte())
        buffer.put(0x46.toByte())
        buffer.put(0x00.toByte())
        buffer.put(imageContentInfo.converBinaryData())
        buffer.put(textContentInfo.converBinaryData())
        buffer.put(audioContentInfo.converBinaryData())
        return buffer.array()
    }

}