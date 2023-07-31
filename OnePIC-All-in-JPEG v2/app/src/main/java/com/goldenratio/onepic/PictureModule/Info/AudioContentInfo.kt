package com.goldenratio.camerax.PictureModule.Info

import com.goldenratio.onepic.PictureModule.AudioContent
import com.goldenratio.onepic.PictureModule.Contents.ContentAttribute
import java.nio.ByteBuffer

class AudioContentInfo(audioContent: AudioContent, startOffset : Int) {
    var contentInfoSize : Int = 0
    var dataStartOffset : Int = 0
    var datasize : Int = 0
    var attribute : Int

    init{
        // contentInfo size
        contentInfoSize = 16
        dataStartOffset = startOffset
        // size
        if(audioContent.audio != null){
            attribute = audioContent.audio!!.attribute.code
            datasize = 2 + audioContent.audio!!._audioByteArray!!.size
        }else {
            attribute = ContentAttribute.basic.code
            datasize = 0
        }
    }
    fun converBinaryData(): ByteArray {
        val buffer: ByteBuffer = ByteBuffer.allocate(contentInfoSize)
        //Audio Content
        buffer.putInt(contentInfoSize)
        buffer.putInt(dataStartOffset)
        buffer.putInt(attribute)
        buffer.putInt(datasize)
        return buffer.array()
    }
    fun getLength() : Int {
        return contentInfoSize
    }
}