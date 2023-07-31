package com.goldenratio.onepic.CameraModule

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.camera2.*
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.*
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.goldenratio.onepic.AudioModule.AudioResolver
import com.goldenratio.onepic.CameraModule.Camera2Module.Camera2
import com.goldenratio.onepic.EditModule.FaceDetectionModule
import com.goldenratio.onepic.ImageToolModule
import com.goldenratio.onepic.JpegViewModel
import com.goldenratio.onepic.PictureModule.Contents.ContentAttribute
import com.goldenratio.onepic.PictureModule.Contents.ContentType
import com.goldenratio.onepic.PictureModule.ImageContent
import com.goldenratio.onepic.R
import com.goldenratio.onepic.ViewerModule.ViewerEditorActivity
import com.goldenratio.onepic.databinding.FragmentCameraBinding
import kotlinx.coroutines.*
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.text.SimpleDateFormat
import java.util.*


@OptIn(ExperimentalCamera2Interop::class)
class CameraFragment : Fragment() {


    private lateinit var camera2Module: Camera2

    data class pointData(var x: Float, var y: Float)
    data class DetectionResult(val boundingBox: RectF, val text: String)

    private var pointArrayList: ArrayList<pointData> = arrayListOf() // Object Focus
    private var previewByteArrayList: ArrayList<ByteArray> = arrayListOf()

    private lateinit var activity: CameraEditorActivity
    private lateinit var binding: FragmentCameraBinding
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var rotation: ObjectAnimator
    private var selectedRadioIndex: Int? = 0

    // Camera
    private lateinit var camera: Camera
    private lateinit var cameraController: CameraControl

    //    private lateinit var cameraExecutor: ExecutorService
    private lateinit var camera2CameraInfo: Camera2CameraInfo
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var cameraSelector: CameraSelector
    private lateinit var imageCapture: ImageCapture

    // TFLite
    private lateinit var customObjectDetector: ObjectDetector
    private lateinit var detectedList: List<DetectionResult>

    // Distance Focus
    private var lensDistanceSteps: Float = 0F
    private var minFocusDistance: Float = 0F
    private var focusDistance: Float = 0F

    // Object Focus
    private lateinit var factory: MeteringPointFactory
    private var isFocusSuccess: Boolean? = null

    private val jpegViewModel by activityViewModels<JpegViewModel>()

    // audio
    private lateinit var audioResolver: AudioResolver

    // imageContent
    private lateinit var imageContent: ImageContent
    private lateinit var imageToolModule: ImageToolModule
    private lateinit var faceDetectionModule: FaceDetectionModule

    // imageAnalysis
    private var imageAnalyzer: ImageAnalysis? = null
    private lateinit var bitmapBuffer: Bitmap
    var analyzeImageWidth: Int = 480

    // Lens Flag
    private var isBackLens: Boolean? = true

    // Animation
    private lateinit var fadeIn: ObjectAnimator
    private lateinit var fadeOut: ObjectAnimator

    // bounding box
    private var boundingBoxArrayList: ArrayList<ArrayList<Int>> = arrayListOf()

    private var burstSize = 15

    override fun onAttach(context: Context) {
        super.onAttach(context)
        activity = context as CameraEditorActivity
        audioResolver = AudioResolver(activity)
    }

    @SuppressLint("SuspiciousIndentation")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        // 상태바 색상 변경
        val window: Window = activity?.window
            ?: throw IllegalStateException("Fragment is not attached to an activity")
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.setStatusBarColor(ContextCompat.getColor(requireContext(), R.color.white))

        binding = FragmentCameraBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Camera2 모듈 생성
        camera2Module = Camera2(activity, requireContext(), binding)

        // imageContent 설정
        imageContent = jpegViewModel.jpegMCContainer.value!!.imageContent

        // 촬영 완료음 설정
        mediaPlayer = MediaPlayer.create(context, R.raw.end_sound)

        // rewind
        imageToolModule = ImageToolModule()
        faceDetectionModule = FaceDetectionModule()

        // Initialize the detector object
        setDetecter()

        // warning Gif (Object Focus 촬영 중 gif)
        imageToolModule.settingLoopGif(binding.warningLoadingImageView, R.raw.flower_loading)

        // 서서히 나타나기/없어지기 애니메이션 설정
        imageToolModule.settingAnimation(binding.successInfoConstraintLayout)

        // shutter Btn 애니메이션 설정
        binding.shutterBtn.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.shutterBtn.viewTreeObserver.removeOnGlobalLayoutListener(this)

                // 뷰를 회전시키는 애니메이션을 생성합니다.
                rotation = ObjectAnimator.ofFloat(binding.shutterBtn, View.ROTATION, 0f, 360f)
                rotation.apply {
                    duration = 1000 // 애니메이션 시간 (밀리초)
                    interpolator = AccelerateDecelerateInterpolator() // 가속도 감속도 애니메이션 인터폴레이터
                    repeatCount = ObjectAnimator.INFINITE // 애니메이션 반복 횟수 (INFINITE: 무한반복)
                    repeatMode = ObjectAnimator.RESTART // 애니메이션 반복 모드 (RESTART: 처음부터 다시 시작)

                }
            }
        })

        // 카메라 전환 (전면<>후면)
        binding.convertBtn.setOnClickListener {
            camera2Module.stopCamera2()

            if (isBackLens!!) camera2Module.cameraId = "1" // CAMERA_FRONT
            else camera2Module.cameraId = "0" // CAMERA_BACK

            camera2Module.startCamera2()

            isBackLens = !isBackLens!!
        }

        // 갤러리 버튼
        binding.galleryBtn.setOnClickListener {
            val intent =
                Intent(
                    activity,
                    ViewerEditorActivity::class.java
                ) //fragment라서 activity intent와는 다른 방식

            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            activity?.supportFragmentManager?.beginTransaction()?.addToBackStack(null)?.commit()

            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()

        // 카메라 시작하기
        camera2Module.startCamera2()

        // 앱을 나갔다와도 변수 값 기억하게 하는 SharedPreference
        val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE)
        /**
         * isBackLens : 카메라 렌즈 전면 / 후면
         * selectedRadioIndex : 선택된 카메라 촬영 모드
         * burstSize : 연속 촬영 장 수
         */
        isBackLens = sharedPref?.getBoolean("isBackLens", true)
        selectedRadioIndex = sharedPref?.getInt("selectedRadioIndex", binding.basicRadioBtn.id)
        burstSize = sharedPref?.getInt("selectedBurstSize", burstSize)!!

        /**
         * 앱을 나갔다 들어와도 촬영 모드 기억하기
         *      - 카메라 모드에 따른 UI 적용
         */
        if (selectedRadioIndex != null && selectedRadioIndex!! >= 0) {

            when (selectedRadioIndex) {

                binding.basicRadioBtn.id -> {
                    binding.basicRadioBtn.isChecked = true

                    imageToolModule.showView(binding.overlay, false)
                    imageToolModule.showView(binding.infoConstraintLayout, false)
                    imageToolModule.showView(binding.burstSizeConstraintLayout, false)
                }

                binding.burstRadioBtn.id -> {
                    binding.burstRadioBtn.isChecked = true

                    imageToolModule.showView(binding.overlay, false)
                    imageToolModule.showView(binding.infoConstraintLayout, true)
                    imageToolModule.showView(binding.burstSizeConstraintLayout, true)
                }

                binding.objectFocusRadioBtn.id -> {
                    binding.objectFocusRadioBtn.isChecked = true

                    imageToolModule.showView(binding.overlay, true)
                    imageToolModule.showView(binding.infoConstraintLayout, true)
                    imageToolModule.showView(binding.burstSizeConstraintLayout, false)

                    setText(binding.infoTextView, resources.getString(R.string.camera_object_info))
                }

                binding.distanceFocusRadioBtn.id -> {
                    binding.distanceFocusRadioBtn.isChecked = true

                    imageToolModule.showView(binding.overlay, false)
                    imageToolModule.showView(binding.infoConstraintLayout, true)
                    imageToolModule.showView(binding.burstSizeConstraintLayout, false)

                    setText(
                        binding.infoTextView,
                        resources.getString(R.string.camera_distance_info)
                    )
                }
            }
        }

        // burst size 기억하기
        if (burstSize != null && burstSize >= 0 && selectedRadioIndex == binding.burstRadioBtn.id) {
            updateBurstSize()
        }

        /**
         * radioGroup.setOnCheckedChangeListener
         *      - 촬영 모드 선택(라디오 버튼)했을 때 UI 변경
         */
        binding.modeRadioGroup.setOnCheckedChangeListener { group, checkedId ->
            when (checkedId) {
                binding.basicRadioBtn.id -> {

                    selectedRadioIndex = binding.basicRadioBtn.id

                    imageToolModule.showView(binding.overlay, false)
                    imageToolModule.showView(binding.infoConstraintLayout, false)
                    imageToolModule.showView(binding.burstSizeConstraintLayout, false)

                    setBold(binding.basicRadioBtn, true)
                    setBold(binding.burstRadioBtn, false)
                    setBold(binding.objectFocusRadioBtn, false)
                    setBold(binding.distanceFocusRadioBtn, false)
                }

                binding.burstRadioBtn.id -> {

                    selectedRadioIndex = binding.burstRadioBtn.id

                    imageToolModule.showView(binding.overlay, false)
                    imageToolModule.showView(binding.infoConstraintLayout, true)
                    imageToolModule.showView(binding.burstSizeConstraintLayout, true)

                    setBold(binding.basicRadioBtn, false)
                    setBold(binding.burstRadioBtn, true)
                    setBold(binding.objectFocusRadioBtn, false)
                    setBold(binding.distanceFocusRadioBtn, false)

                    updateBurstSize()
                }

                binding.objectFocusRadioBtn.id -> {

                    selectedRadioIndex = binding.objectFocusRadioBtn.id

                    imageToolModule.showView(binding.overlay, true)
                    imageToolModule.showView(binding.infoConstraintLayout, true)
                    imageToolModule.showView(binding.burstSizeConstraintLayout, false)

                    setBold(binding.basicRadioBtn, false)
                    setBold(binding.burstRadioBtn, false)
                    setBold(binding.objectFocusRadioBtn, true)
                    setBold(binding.distanceFocusRadioBtn, false)

                    setText(binding.infoTextView, resources.getString(R.string.camera_object_info))
                }

                binding.distanceFocusRadioBtn.id -> {

                    selectedRadioIndex = binding.distanceFocusRadioBtn.id

                    imageToolModule.showView(binding.overlay, false)
                    imageToolModule.showView(binding.infoConstraintLayout, true)
                    imageToolModule.showView(binding.burstSizeConstraintLayout, false)

                    setBold(binding.basicRadioBtn, false)
                    setBold(binding.burstRadioBtn, false)
                    setBold(binding.objectFocusRadioBtn, false)
                    setBold(binding.distanceFocusRadioBtn, true)

                    setText(
                        binding.infoTextView,
                        resources.getString(R.string.camera_distance_info)
                    )
                }
            }
        }

        binding.burstSizeSettingRadioGroup.setOnCheckedChangeListener { group, checkedId ->
            setBusrtSize(checkedId)
        }

        binding.textureView.setOnClickListener {

        }

        // shutter Btn 클릭
        binding.shutterBtn.setOnClickListener {

            rotation.start()

            binding.shutterBtn.isEnabled = false
            binding.galleryBtn.isEnabled = false
            binding.convertBtn.isEnabled = false
            binding.basicRadioBtn.isEnabled = false
            binding.burstRadioBtn.isEnabled = false
            binding.objectFocusRadioBtn.isEnabled = false
            binding.distanceFocusRadioBtn.isEnabled = false

            // previewByteArrayList 초기화
            previewByteArrayList.clear()

//            //Basic 모드
//            if(basicRadioBtn.isChecked){
//                turnOnAFMode()
//                audioResolver.startRecording("camera_record")
//
//                CoroutineScope(Dispatchers.IO).launch {
//
//                    val result = takePicture(0)
//
//                    // 녹음 중단
//                    val savedFile = audioResolver.stopRecording()
//                    if (savedFile != null) {
//                        val audioBytes = audioResolver.getByteArrayInFile(savedFile)
//                        jpegViewModel.jpegMCContainer.value!!.setAudioContent(
//                            audioBytes,
//                            ContentAttribute.basic
//                        )
//                        Log.d("AudioModule", "녹음된 오디오 사이즈 : ${audioBytes.size.toString()}")
//                    }
//
//
//                    CoroutineScope(Dispatchers.Default).launch {
//                        // FaceBlendingFragment로 이동
//                        withContext(Dispatchers.Main) {
//                            var jop = async {
//                                jpegViewModel.jpegMCContainer.value!!.setImageContent(
//                                    previewByteArrayList,
//                                    ContentType.Image,
//                                    ContentAttribute.burst
//                                )
//                            }
//                            jop.await()
//                            Log.d("error 잡기", "넘어가기 전")
////
////                            findNavController().navigate(R.id.action_cameraFragment_to_burstModeEditFragment)
//                            JpegViewModel.AllInJPEG = false
//                            jpegViewModel.jpegMCContainer.value?.save()
//                        }
//                    }
//
//                    withContext(Dispatchers.Main) {
//
//                        mediaPlayer.start()
//
//                        shutterBtn.isEnabled = true
//                        galleryBtn.isEnabled = true
//                        convertBtn.isEnabled = true
//                        basicRadioBtn.isEnabled = true
//                        burstRadioBtn.isEnabled = true
//                        objectFocusRadioBtn.isEnabled = true
//                        distanceFocusRadioBtn.isEnabled = true
//                        successInfoConstraintLayout.visibility = View.VISIBLE
//
//                        fadeIn.start()
//                        rotation.cancel()
//                    }
//
//                } // end of Coroutine ...
//            }
//
            if (binding.burstRadioBtn.isChecked) {
                camera2Module.previewByteArrayList.clear()

                camera2Module.lockFocus()

                CoroutineScope(Dispatchers.IO).launch {
                    while (true) {

                        if (camera2Module.state == 0) { // state : PREVIEW

                            while (camera2Module.previewByteArrayList.size < burstSize) {
                            }
                            previewByteArrayList = camera2Module.previewByteArrayList


                            CoroutineScope(Dispatchers.Default).launch {
                                // FaceBlendingFragment로 이동
                                withContext(Dispatchers.Main) {
                                    var jop = async {
                                        jpegViewModel.jpegMCContainer.value!!.setImageContent(
                                            previewByteArrayList,
                                            ContentType.Image,
                                            ContentAttribute.burst
                                        )
                                    }
                                    jop.await()
                                    Log.d("error 잡기", "넘어가기 전")
//
//                                    findNavController().navigate(R.id.action_cameraFragment_to_burstModeEditFragment)
                                    JpegViewModel.AllInJPEG = true
                                    jpegViewModel.jpegMCContainer.value?.save()
                                }

                                withContext(Dispatchers.Main) {
//                                    mediaPlayer.start()

                                    binding.shutterBtn.isEnabled = true
                                    binding.galleryBtn.isEnabled = true
                                    binding.convertBtn.isEnabled = true
                                    binding.basicRadioBtn.isEnabled = true
                                    binding.burstRadioBtn.isEnabled = true
                                    binding.objectFocusRadioBtn.isEnabled = true
                                    binding.distanceFocusRadioBtn.isEnabled = true
                                    binding.successInfoConstraintLayout.visibility =
                                        View.VISIBLE

//                                    fadeIn.start()
                                    imageToolModule.fadeIn.start()
                                    rotation.cancel()
                                }
                            }

                            break
                        }
                    }
                }
            }
//
//            // Object Focus 모드
//            else if(objectFocusRadioBtn.isChecked){
//                turnOnAFMode()
//                pointArrayList.clear()
//                boundingBoxArrayList.clear()
//                isFocusSuccess = false
//
//                saveObjectCenterPoint()
//                JpegViewModel.AllInJPEG = true
//                audioResolver.startRecording("camera_record")
//                imageToolModule.showView(objectWarningConstraintLayout, true)
//            }
//
//            // Distance Focus 모드
//            else if(distanceFocusRadioBtn.isChecked){
//
//                JpegViewModel.AllInJPEG = true
//                turnOffAFMode(0f)
//                audioResolver.startRecording("camera_record")
//
//                initProgressBar()
//                imageToolModule.showView(distanceWarningConstraintLayout, true)
//
//                controlLensFocusDistance(0)
//            }
//
//        }
//
//        // info 닫기 버튼 클릭
//        binding.infoCloseBtn.setOnClickListener {
//            binding.infoConstraintLayout.visibility = View.GONE
//        }
        }
    }

    // A 프래그먼트의 onPause() 메서드에서 호출됩니다.
    override fun onPause() {
        super.onPause()

        // 값 기억하기 (프래그먼트 이동 후 다시 돌아왔을 때도 유지하기 위한 기억)
        val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE)
        with(sharedPref?.edit()) {
            this?.putInt("selectedRadioIndex", selectedRadioIndex!!)
            this?.putBoolean("isBackLens", isBackLens!!)
            this?.putInt("selectedBurstSize", burstSize)
            this?.apply()
        }

        // 카메라 멈추기
        camera2Module.stopCamera2()
    }


//    suspend fun takePicture(i : Int) : Int {
//        return suspendCoroutine { continuation ->
//
//            imageCapture.takePicture(cameraExecutor, object :
//                ImageCapture.OnImageCapturedCallback() {
//                override fun onCaptureSuccess(image: ImageProxy) {
//                    val buffer = image.planes[0].buffer
//                    buffer.rewind()
//                    val bytes = ByteArray(buffer.capacity())
//                    buffer.get(bytes)
//                    previewByteArrayList.add(bytes)
//                    image.close()
//
//                    continuation.resume(1)
//                    super.onCaptureSuccess(image)
//                }
//
//                override fun onError(exception: ImageCaptureException) {
//                    super.onError(exception)
//                }
//            })
//        }
//    }


    private fun setDetecter() {
        // Step 2: Initialize the detector object
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setMaxResults(5)          // 최대 결과 (모델에서 감지해야 하는 최대 객체 수)
            .setScoreThreshold(0.4f)    // 점수 임계값 (감지된 객체를 반환하는 객체 감지기의 신뢰도)
            .build()
        customObjectDetector = ObjectDetector.createFromFileAndOptions(
            activity,
            "lite-model_efficientdet_lite0_detection_metadata_1.tflite",
            options
        )
    }

//    private fun turnOffAFMode(distance : Float){
//        Camera2CameraControl.from(camera.cameraControl).captureRequestOptions =
//            CaptureRequestOptions.Builder()
//                .apply {
//                    setCaptureRequestOption(
//                        CaptureRequest.CONTROL_AF_MODE,
//                        CameraMetadata.CONTROL_AF_MODE_OFF
//                    )
//                    // Fix focus lens distance to infinity to get focus far away (avoid to get a close focus)
//                    setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, distance)
//                }.build()
//    }
//
//    private fun turnOnAFMode(){
//        Camera2CameraControl.from(camera.cameraControl).captureRequestOptions =
//            CaptureRequestOptions.Builder()
//                .apply {
//                    setCaptureRequestOption(
//                        CaptureRequest.CONTROL_AF_MODE,
//                        CameraMetadata.CONTROL_AF_MODE_AUTO
//                    )
//                }.build()
//    }
//
//    private fun turnOnBurstMode(){
//        Camera2CameraControl.from(camera.cameraControl).captureRequestOptions =
//            CaptureRequestOptions.Builder()
//                .apply {
//                    setCaptureRequestOption(
//                        CaptureRequest.CONTROL_CAPTURE_INTENT,
//                        CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE
//                    )
//                }.build()
//    }
//
//    private fun previewToByteArray(){
//
//        val imageCapture = imageCapture
//
//        imageCapture!!.takePicture(cameraExecutor, object :
//            ImageCapture.OnImageCapturedCallback() {
//            override fun onCaptureSuccess(image: ImageProxy) {
//
//                val buffer = image.planes[0].buffer
//                buffer.rewind()
//                val bytes = ByteArray(buffer.capacity())
//                buffer.get(bytes)
//                previewByteArrayList.add(bytes)
//
//                image.close()
//                super.onCaptureSuccess(image)
//            }
//        })
//    }
//
//    private fun saveObjectCenterPoint(){
//        val detectObjectList = detectedList
//
//        val scaleFactor = (viewFinder.width.toFloat()) / analyzeImageWidth.toFloat()
//
//        for (obj in detectObjectList) {
//            val boundingBox = ArrayList<Int>()
//            try {
//
//                val left = obj.boundingBox.left.toInt()
//                val top = obj.boundingBox.top.toInt()
//                val right = obj.boundingBox.right.toInt()
//                val bottom = obj.boundingBox.bottom.toInt()
//
//                boundingBox.add(left)
//                boundingBox.add(top)
//                boundingBox.add(right)
//                boundingBox.add(bottom)
//
//                var pointX: Float =
//                    (obj.boundingBox.left + ((obj.boundingBox.right - obj.boundingBox.left) * 0.5f)) * scaleFactor
//                var pointY: Float =
//                    (obj.boundingBox.top + ((obj.boundingBox.bottom - obj.boundingBox.top) * 0.5f)) * scaleFactor
//
//                pointArrayList.add(pointData(pointX, pointY))
//
//            } catch (e: IllegalAccessException) {
//                e.printStackTrace();
//            } catch (e: InvocationTargetException) {
//                e.targetException.printStackTrace(); //getTargetException
//            }
//            Log.v("focus test", "boundingBox : ${boundingBox[0]}, ${boundingBox[1]}, ${boundingBox[2]}, ${boundingBox[3]}")
//            boundingBoxArrayList.add(boundingBox)
//            Log.v("focus text", "boundingBoxArrayList size : ${boundingBoxArrayList.size}")
//        }
//
//        takeObjectFocusMode(0, detectObjectList)
//    }

//    /**
//     * takeObjectFocusMode(index: Int)
//     *      - 감지된 객체 별로 초점을 맞추고
//     *          Preview를 ByteArray로 저장
//     */
//    private fun takeObjectFocusMode(index: Int, detectedObjectList : List<DetectionResult>) {
//        if(index >= detectedObjectList.size){
//            CoroutineScope(Dispatchers.IO).launch {
//                while (previewByteArrayList.size < detectedObjectList.size) { }
//
//                if (previewByteArrayList.size == detectedObjectList.size) {
//
//                    // 녹음 중단
//                    val savedFile = audioResolver.stopRecording()
//                    if (savedFile != null) {
//                        val audioBytes = audioResolver.getByteArrayInFile(savedFile)
//                        jpegViewModel.jpegMCContainer.value!!.setAudioContent(
//                            audioBytes,
//                            ContentAttribute.basic
//                        )
//                    }
//                    jpegViewModel.jpegMCContainer.value!!.setImageContent(
//                        previewByteArrayList,
//                        ContentType.Image,
//                        ContentAttribute.object_focus
//                    )
//
//
//                    CoroutineScope(Dispatchers.Default).launch {
//                        withContext(Dispatchers.Main) {
//                            val pictureList = jpegViewModel.jpegMCContainer.value!!.imageContent.pictureList
//                            for(i in 0 until pictureList.size) {
//                                pictureList[i].insertEmbeddedData(boundingBoxArrayList[i])
//                                Log.v("focus test", "camera boundingBox size : ${boundingBoxArrayList.size}")
//                                Log.v("focus test", "camera boundingBox : ${boundingBoxArrayList[i].get(0)}, ${boundingBoxArrayList[i].get(1)}, ${boundingBoxArrayList[i].get(2)}, ${boundingBoxArrayList[i].get(3)}")
//                                Log.v("focus test", "camera Analysis Image width : ${analyzeImageWidth}")
//                            }
//                            jpegViewModel.jpegMCContainer.value?.save()
//                        }
//
//                        withContext(Dispatchers.Main) {
//
//                            mediaPlayer.start()
//
//                            shutterBtn.isEnabled = true
//                            galleryBtn.isEnabled = true
//                            convertBtn.isEnabled = true
//                            basicRadioBtn.isEnabled = true
//                            burstRadioBtn.isEnabled = true
//                            objectFocusRadioBtn.isEnabled = true
//                            distanceFocusRadioBtn.isEnabled = true
//
//                            imageToolModule.showView(objectWarningConstraintLayout, false)
//                            imageToolModule.showView(successInfoConstraintLayout, true)
//
//                            fadeIn.start()
//                            rotation.cancel()
//                        }
//                    }
//                }
//            }
//            return
//        } // end of if ...
//
//        val point = factory.createPoint(pointArrayList[index].x, pointArrayList[index].y)
//
//        val action = FocusMeteringAction.Builder(point)
//            .build()
//
//        val result = cameraController?.startFocusAndMetering(action)
//
//        result?.addListener({
//            try {
//                isFocusSuccess = result.get().isFocusSuccessful
//            } catch (e: IllegalAccessException) {
//                Log.e("Error", "IllegalAccessException")
//            } catch (e: InvocationTargetException) {
//                Log.e("Error", "InvocationTargetException")
//            }
//
//            Log.v("focus test", "[$index] isFocusSuccess? : $isFocusSuccess")
//
//            if (isFocusSuccess == true) {
////                mediaPlayer.start()
//
//                previewToByteArray()
//                isFocusSuccess = false
//                takeObjectFocusMode(index + 1, detectedObjectList)
//            } else {
//                // 초점이 안잡혔다면 다시 그 부분에 초점을 맞춰라
//                takeObjectFocusMode(index, detectedObjectList)
//            }
//        }, ContextCompat.getMainExecutor(activity))
//
//
//    } // end of takeObjectFocusMode()...


//    /**
//     * Lens Focus Distance 바꾸면서 사진 찍기
//     */
//    private fun controlLensFocusDistance(photoCnt: Int) {
//        if (photoCnt >= DISTANCE_FOCUS_PHOTO_COUNT){
//            CoroutineScope(Dispatchers.IO).launch {
//                while (previewByteArrayList.size < DISTANCE_FOCUS_PHOTO_COUNT) { }
//
//                if (previewByteArrayList.size == DISTANCE_FOCUS_PHOTO_COUNT) {
//
//                    turnOnAFMode()
//                    // 녹음 중단
//                    val savedFile = audioResolver.stopRecording()
//                    if (savedFile != null) {
//                        val audioBytes = audioResolver.getByteArrayInFile(savedFile)
//                        jpegViewModel.jpegMCContainer.value!!.setAudioContent(
//                            audioBytes,
//                            ContentAttribute.basic
//                        )
//                        Log.d("AudioModule", "녹음된 오디오 사이즈 : ${audioBytes.size.toString()}")
//                    }
//                    Log.d("burst", "setImageContent 호출 전")
//                    jpegViewModel.jpegMCContainer.value!!.setImageContent(
//                        previewByteArrayList,
//                        ContentType.Image,
//                        ContentAttribute.distance_focus
//                    )
//
//
//                    CoroutineScope(Dispatchers.Default).launch {
//                        withContext(Dispatchers.Main) {
////                            findNavController().navigate(R.id.action_cameraFragment_to_burstModeEditFragment)
//                            jpegViewModel.jpegMCContainer.value?.save()
//                        }
//
//                        withContext(Dispatchers.Main) {
//                            mediaPlayer.start()
//
//                            shutterBtn.isEnabled = true
//                            galleryBtn.isEnabled = true
//                            convertBtn.isEnabled = true
//                            basicRadioBtn.isEnabled = true
//                            burstRadioBtn.isEnabled = true
//                            objectFocusRadioBtn.isEnabled = true
//                            distanceFocusRadioBtn.isEnabled = true
//
//                            imageToolModule.showView(distanceWarningConstraintLayout, false)
//                            imageToolModule.showView(successInfoConstraintLayout, true)
//
//                            fadeIn.start()
//                            rotation.cancel()
//                        }
//                    }
//                }
//            }
//            return
//        }
//
//        val distance: Float? = minFocusDistance - lensDistanceSteps * photoCnt
//        turnOffAFMode(distance!!)
//
//        val imageCapture = imageCapture ?: return
//
//        imageCapture.takePicture(cameraExecutor, object :
//            ImageCapture.OnImageCapturedCallback() {
//            override fun onCaptureSuccess(image: ImageProxy) {
////                mediaPlayer.start()
//
//                incrementProgressBar()
//
//                controlLensFocusDistance(photoCnt + 1)
//                val buffer = image.planes[0].buffer
//                buffer.rewind()
//                val bytes = ByteArray(buffer.capacity())
//                buffer.get(bytes)
//                previewByteArrayList.add(bytes)
//                image.close()
//                super.onCaptureSuccess(image)
//            }
//        })
//    }
//
//    private fun initProgressBar() {
//        distanceProgressBar.progress = 0
//    }
//
//    private fun incrementProgressBar() {
//        var currentProgress = distanceProgressBar.progress
//        currentProgress += 10
//        distanceProgressBar.progress = currentProgress
//    }

    /**
     * takePhoto()
     *      - 사진 촬영 후 저장
     *          오로지 저장이 잘 되는지 확인하는 용도
     */
    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                activity.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(activity),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults) {
//                    val msg = "Photo capture succeeded: ${output.savedUri}"
//                    Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
//                    mediaPlayer.start()
                }
            }
        )
    }

//    // Update UI after objects have been detected. Extracts original image height/width
//    // to scale and place bounding boxes properly through OverlayView
//    override fun onResults(
//        results: MutableList<Detection>?,
//        inferenceTime: Long,
//        imageHeight: Int,
//        imageWidth: Int
//    ) {
//        activity?.runOnUiThread {
//            // Pass necessary information to OverlayView for drawing on the canvas
//            overlay.setResults(
//                results ?: LinkedList<Detection>(),
//                imageHeight,
//                imageWidth
//            )
//
//            // Force a redraw
//            overlay.invalidate()
//        }
//    }


    // 라디오 버튼 볼드 처리
    fun setBold(radioBtn: RadioButton, isBold: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.Main) {
                if (isBold) radioBtn.setTypeface(null, Typeface.BOLD)
                else radioBtn.setTypeface(null, Typeface.NORMAL)
            }
        }
    }

    fun setText(textView: TextView, string: String) {
        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.Main) {
                textView.text = string
            }
        }
    }

    fun setBusrtSize(checkedId: Int) {
        when (checkedId) {
            binding.burst1RadioBtn.id -> {
                burstSize = BURST_OPTION1
                setText(binding.infoTextView, resources.getString(R.string.burst1_info))
            }
            binding.burst2RadioBtn.id -> {
                burstSize = BURST_OPTION2
                setText(binding.infoTextView, resources.getString(R.string.burst2_info))
            }
            binding.burst3RadioBtn.id -> {
                burstSize = BURST_OPTION3
                setText(binding.infoTextView, resources.getString(R.string.burst3_info))
            }
        }
        camera2Module.setBurstSize(burstSize)
    }

    fun updateBurstSize() {
        when (burstSize) {
            BURST_OPTION1 -> {
                binding.burst1RadioBtn.isChecked = true
                setText(binding.infoTextView, resources.getString(R.string.burst1_info))
            }
            BURST_OPTION2 -> {
                binding.burst2RadioBtn.isChecked = true
                setText(binding.infoTextView, resources.getString(R.string.burst2_info))
            }
            BURST_OPTION3 -> {
                binding.burst3RadioBtn.isChecked = true
                setText(binding.infoTextView, resources.getString(R.string.burst3_info))
            }
        }
        camera2Module.setBurstSize(burstSize)
    }

    fun getStatusBarHeightDP(context: Context): Int {
        var result = 0
        val resourceId: Int =
            context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = context.resources.getDimension(resourceId).toInt()
        }
        return result
    }


//    /**
//     * allPermissionsGranted()
//     *      - 카메라 권한 확인하기
//     */
//    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
//        ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
//    }

    override fun onDestroy() {
        super.onDestroy()
//        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "OnePIC"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val DISTANCE_FOCUS_PHOTO_COUNT = 7
        private val REQUIRED_PERMISSIONS = // Array<String>
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).toTypedArray()

        private val BURST_OPTION1 = 3
        private val BURST_OPTION2 = 15
        private val BURST_OPTION3 = 7
    }
}