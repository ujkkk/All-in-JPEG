

class Audio (audioByteArray : ByteArray? = null, _Content_attribute: ContentAttribute){
    var _audioByteArray : ByteArray? = null
    var attribute = _Content_attribute
    var size : Int = audioByteArray?.size ?: 0
    init {
        if (audioByteArray != null) {
            _audioByteArray = audioByteArray
            size = audioByteArray!!.size
        }
        attribute = _Content_attribute
    }

    fun waitForByteArrayInitialized() {
        while (!isByteArrayInitialized()) {
            Thread.sleep(100)
        }
    }

    fun isByteArrayInitialized(): Boolean {
        return _audioByteArray != null
    }

}