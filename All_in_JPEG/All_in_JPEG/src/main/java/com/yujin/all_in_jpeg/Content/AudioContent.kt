


class AudioContent {
    var audio : Audio? = null

    fun init() {
        audio = null
    }
    fun setContent(byteArray:ByteArray, contentAttribute: ContentAttribute){
        init()
        // audio 객체 생성
        audio = Audio(byteArray, contentAttribute)
        audio!!.waitForByteArrayInitialized()
    }
    fun setContent(_audio:Audio){
        init()
        audio = _audio
        audio!!.waitForByteArrayInitialized()
    }

}