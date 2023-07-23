package com.example.all_in_jpeg

import android.util.Log
import com.example.all_in_jpeg.Contents.Audio
import com.example.all_in_jpeg.Contents.ContentAttribute

class AudioContent {
    var audio : Audio? = null

    fun init() {
        audio = null
    }
    fun setContent(byteArray:ByteArray, contentAttribute: ContentAttribute){
        init()
        Log.d("audio_test", "audio SetContent, audio 객체 갱신")
        // audio 객체 생성
        audio = Audio(byteArray, contentAttribute)
        audio!!.waitForByteArrayInitialized()
        Log.d("audio_test", "setContent : 오디오 크기 ${audio!!.size}")
    }
    fun setContent(_audio:Audio){
        init()
        audio = _audio
        audio!!.waitForByteArrayInitialized()
    }

}