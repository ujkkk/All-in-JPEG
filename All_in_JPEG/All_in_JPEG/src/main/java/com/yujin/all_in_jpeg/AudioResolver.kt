

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.service.controls.ControlsProviderService.TAG
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

class AudioResolver(val context : Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    var savedFile: File?  = null
    var mediaPlayer = MediaPlayer()

    init{
        savedFile = getOutputMediaFilePath("viewer_record")
    }
    
    fun startRecording(fileName: String) : File? {
        if(isRecording){
            return null
        }
        isRecording = true
        Log.d("AudioModule", "녹음 start")
        savedFile = getOutputMediaFilePath(fileName)

        // 녹음 시작
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

            // 해당 파일에 write
            setOutputFile(savedFile!!.path)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(48000) // 샘플링 레이트를 48kHz로 설정
            setAudioEncodingBitRate(192000) // 비트 레이트를 192kbps로 설정
            setAudioChannels(2)
            try {
                prepare()
                start()
            } catch (e: Exception) {
                Log.e(TAG, "startRecording: ${e.message}")
            }
        }
        return savedFile
    }

    fun stopRecording() : File?{
        if(!isRecording){
            return null
        }
        isRecording = false
        Log.d("AudioModule", "녹음 stop")
        mediaRecorder?.apply {
            try {
                stop()
                reset()
                release()
            }catch (e : java.lang.RuntimeException){
                Log.e(TAG, "stopRecording: ${e.message}")
            }

        }
        return savedFile
    }

    fun getByteArrayInFile(audioFile : File) : ByteArray{
        var audioBytes : ByteArray = audioFile.readBytes()
        while (!(audioBytes != null)) {
            Thread.sleep(100)
        }
        return audioBytes
    }

    fun getOutputMediaFilePath(fileName : String): File {
        val mediaStorageDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        val mediaDir = "audio"

        val file = File("${mediaStorageDir?.absolutePath}/$mediaDir/$fileName.${"aac"}")
        if (!file.parentFile.exists()) {
            file.parentFile.mkdirs()
        }
        return file
    }


    /** byteArray로 나타낸 오디오 데이터를 파일로 저장하는 함수**/
    fun saveByteArrToAacFile(byteArr: ByteArray, fileName: String) : File{
        savedFile = getOutputMediaFilePath(fileName)
        val outputStream = FileOutputStream(savedFile)
        outputStream.write(byteArr)
        outputStream.flush()
        outputStream.close()
        return savedFile as File
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun audioPlay(_audio : Audio){
        var audio = _audio

        mediaPlayer.reset()
        var byteData = audio._audioByteArray
        if (byteData == null || byteData.isEmpty()) {
            Log.e("AudioModule", "Failed to play audio: _audioByteArray is null or empty.")
            return
        }
         CoroutineScope(Dispatchers.IO).launch {
             // MediaPlayer 인스턴스를 생성하고 오디오 데이터를 설정
             mediaPlayer.apply {
                 setAudioAttributes(
                     AudioAttributes.Builder()
                         .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                         .build())
                 setOnCompletionListener {
                 }
            try {
                 setDataSource(savedFile!!.path)
                 prepare()
                 // MediaPlayer를 시작하여 오디오를 재생한다.
                 start()
                 } catch (e: IOException) {
                     // IOException을 처리하는 코드를 추가한다.
                     Log.e("AudioModule", "Failed to prepare media player: ${e.message}")
                     e.printStackTrace()
                 } catch (e: IllegalStateException){
                    Log.e("AudioModule", "no prepare: ${e.message}")
                    e.printStackTrace()
                 }
             }
         }
    }

    fun audioStop(){
        mediaPlayer.stop()
        mediaPlayer.reset()
    }


}