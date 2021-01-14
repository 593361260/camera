package com.mingo.runplugin;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.mingo.runplugin.impl.OnCameraOperatorListener;
import com.mingo.runplugin.utils.AppTools;
import com.mingo.runplugin.utils.TimeUtil;
import com.mingo.runplugin.widget.TouchView;
import com.mingo.runplugin.widget.paintView.BrushDrawingView;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * <p/>
 */
public class CaptureVideoActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private static final String TAG = "video";

    public static final String EXTRA_DATA_FILE_NAME = "EXTRA_DATA_FILE_NAME";

    private static final int VIDEO_TIMES = 15;

    private static final int VIDEO_WIDTH = 320;

    private static final int VIDEO_HEIGHT = 240;
    // context

    public Handler handler = new Handler();
    // media

    private MediaRecorder mediaRecorder;// 录制视频的类

    private Camera camera;
    // view

    private SurfaceView surfaceview;

    private SurfaceHolder surfaceHolder;

    private ImageView recordBtn;

    private ImageView recordingState;

    private TextView recordingTimeTextView;
    private ImageView ivPreview;
    private BrushDrawingView brushView;

    private ImageView switchCamera; // 切换摄像头
    private TouchView touchBtn;
    private View commit, ivCancel;

    // state
    private int cameraId = 0;
    private String filename;
    private boolean previewing = false;
    private boolean multiCamera = false;
    private boolean recording = false;
    private long start, end; // 录制时间控制
    private long duration = 0;
    private boolean destroyed = false;
    private int mAngle = 0;
    private float dist;
    private LinkedList<Point> backCameraSize = new LinkedList<>();
    private LinkedList<Point> frontCameraSize = new LinkedList<>();
    private int mode;                      //0是聚焦 1是放大
    static final int FOCUS = 1;            // 聚焦
    static final int ZOOM = 2;            // 缩放

    public static void start(Activity activity, String videoFilePath, int captureCode) {
        Intent intent = new Intent();
        intent.setClass(activity, CaptureVideoActivity.class);
        intent.putExtra(EXTRA_DATA_FILE_NAME, videoFilePath);
        activity.startActivityForResult(intent, captureCode);
    }

    // 录制时间计数
    private Runnable runnable = new Runnable() {

        public void run() {
            end = new Date().getTime();
            duration = (end - start);
            int invs = (int) (duration / 1000);
            recordingTimeTextView.setText(TimeUtil.secToTime(invs));
            // 录制过程中红点闪烁效果
            if (invs % 2 == 0) {
                recordingState.setBackgroundResource(R.drawable.shape_red_circle);
            } else {
                recordingState.setBackgroundResource(R.drawable.shape_circle_transparent);
            }
            if (invs >= VIDEO_TIMES) {
                stopRecorder();
                sendVideo();
                touchBtn.updateFinish();
            } else {
                handler.postDelayed(this, 1000);
            }
        }
    };

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFormat(PixelFormat.TRANSLUCENT); // 使得窗口支持透明度
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_record_video);
        parseIntent();
        findViews();
        brushView.setBrushDrawingMode(true);
        initActionBar();
        setViewsListener();
        updateRecordUI();
        getVideoPreviewSize();
        surfaceview = this.findViewById(R.id.videoView);
        SurfaceHolder holder = surfaceview.getHolder();
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        holder.addCallback(this);
        resizeSurfaceView();
        initEvent();
    }

    private float pointX, pointY;

    @SuppressLint("ClickableViewAccessibility")
    private void initEvent() {
        surfaceview.setOnClickListener(view -> pointFocus((int) pointX, (int) pointY));
        surfaceview.setOnTouchListener((View v, @SuppressLint("ClickableViewAccessibility") MotionEvent event) -> {
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    pointX = event.getX();
                    pointY = event.getY();
                    mode = FOCUS;
                    break;
                // 副点按下
                case MotionEvent.ACTION_POINTER_DOWN:
                    dist = spacing(event);
                    // 如果连续两点距离大于10，则判定为多点模式
                    if (spacing(event) > 10f) {
                        mode = ZOOM;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    mode = FOCUS;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (mode == FOCUS) {
                        //pointFocus((int) event.getRawX(), (int) event.getRawY());
                    } else if (mode == ZOOM) {
                        float newDist = spacing(event);
                        if (newDist > 10f) {
                            float tScale = (newDist - dist) / dist;
                            if (tScale < 0) {
                                tScale = tScale * 10;
                            }
                            addZoomIn((int) tScale);
                        }
                    }
                    break;
            }
            return false;
        });
        touchBtn.setOnCameraListener(new OnCameraOperatorListener() {
            @Override
            public void onClick() {
                try {
                    Log.d("Mozator", "拍照");
                    camera.takePicture(null, null, new MyPictureCallback());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onPressStart() {
                startRecorder(false);
            }

            @Override
            public void onFinish(int time) {
                //录制结束

            }
        });
    }

    //定点对焦的代码
    private void pointFocus(int x, int y) {
        try {
            camera.cancelAutoFocus();
        } catch (Exception e) {
        }
        showPoint(x, y);
        autoFocus();
    }

    private void showPoint(int x, int y) {
        Camera.Parameters parameters = camera.getParameters();
        if (parameters.getMaxNumMeteringAreas() > 0) {
            List<Camera.Area> areas = new ArrayList<Camera.Area>();
            //xy变换了
            int rectY = -x * 2000 / AppTools.getWindowWidth(this) + 1000;
            int rectX = y * 2000 / AppTools.getWindowHeight(this) - 1000;

            int left = rectX < -900 ? -1000 : rectX - 100;
            int top = rectY < -900 ? -1000 : rectY - 100;
            int right = rectX > 900 ? 1000 : rectX + 100;
            int bottom = rectY > 900 ? 1000 : rectY + 100;
            Rect area1 = new Rect(left, top, right, bottom);
            areas.add(new Camera.Area(area1, 800));
            parameters.setMeteringAreas(areas);
        }
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        camera.setParameters(parameters);
    }

    private void autoFocus() {
        new Thread() {
            @Override
            public void run() {
                try {
                    sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (camera == null) {
                    return;
                }
                camera.autoFocus((success, camera) -> {
                    Log.d("Mozator", "聚焦成功  " + success);
                });
            }
        };
    }

    //放大缩小
    int curZoomValue = 0;

    private void addZoomIn(int delta) {
        try {
            Camera.Parameters params = camera.getParameters();
            Log.d("Camera", "Is support Zoom " + params.isZoomSupported());
            if (!params.isZoomSupported()) {
                return;
            }
            curZoomValue += delta;
            if (curZoomValue < 0) {
                curZoomValue = 0;
            } else if (curZoomValue > params.getMaxZoom()) {
                curZoomValue = params.getMaxZoom();
            }

            if (!params.isSmoothZoomSupported()) {
                params.setZoom(curZoomValue);
                camera.setParameters(params);
                return;
            } else {
                camera.startSmoothZoom(curZoomValue);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 两点的距离
     */
    private float spacing(MotionEvent event) {
        if (event == null) {
            return 0;
        }
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    private void parseIntent() {
        filename = getIntent().getExtras().getString(EXTRA_DATA_FILE_NAME);
    }

    private void findViews() {
        recordingTimeTextView = findViewById(R.id.record_times);
        recordingState = findViewById(R.id.recording_id);
        recordBtn = findViewById(R.id.record_btn);
        switchCamera = findViewById(R.id.switch_cameras);
        recordBtn.setVisibility(View.GONE);
        touchBtn = findViewById(R.id.touchBtn);
        ivPreview = findViewById(R.id.ivPreview);
        brushView = findViewById(R.id.brushView);
    }

    private void initActionBar() {
        checkMultiCamera();
    }

    private void setViewsListener() {
        //点击拍照
        recordBtn.setOnClickListener(v -> {
            if (recording) {
                stopRecorder();
                sendVideo();
            } else {
                try {
                    Log.d("Mozator", "拍照");
                    camera.takePicture(null, null, new MyPictureCallback());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        //长按录制
        recordBtn.setOnLongClickListener(v -> {
            startRecorder(false);
            return true;
        });
        switchCamera.setOnClickListener(v -> switchCamera());
    }

    @TargetApi(9)
    private void switchCamera() {
        cameraId = (cameraId + 1) % Camera.getNumberOfCameras();
        resizeSurfaceView();
        shutdownCamera();
        initCamera(true);
        startPreview();
    }

    public void onResume() {
        super.onResume();
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void onPause() {
        super.onPause();
        getWindow().setFlags(0, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (recording) {
            stopRecorder();
            sendVideo();
        } else {
            shutdownCamera();
        }

    }

    public void onDestroy() {
        super.onDestroy();
        shutdownCamera();
        destroyed = true;
    }

    @Override
    public void onBackPressed() {
        if (recording) {
            stopRecorder();
        }
        shutdownCamera();
        setResult(RESULT_CANCELED);
        finish();
    }

    private class MyPictureCallback implements Camera.PictureCallback {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.d("Mozator", "保存图片");
            camera.release();
            //TODO save data[] to picture
            new SavePicTask(data).execute();
//            camera.startPreview(); // 拍完照后，重新开始预览
        }
    }

    private class SavePicTask extends AsyncTask<Void, Void, String> {
        private byte[] data;

        protected void onPreExecute() {

        }

        SavePicTask(byte[] data) {
            this.data = data;
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                return saveToSDCard(data);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            Log.d("Mozator", "保存成功");
            findViewById(R.id.frameImg).setVisibility(View.VISIBLE);
            Glide.with(CaptureVideoActivity.this)
                    .load(result)
                    .into(ivPreview);
            try {
                if (!isDestroyed()) {

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public String saveToSDCard(byte[] data) throws IOException {
        Bitmap croppedImage;
        //获得图片大小
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        options.inJustDecodeBounds = false;
        try {
            croppedImage = decodeRegionCrop(data);
        } catch (Exception e) {
            return null;
        }
        //TODO
        String parent = getExternalFilesDir("").getAbsolutePath() + "/img";
        if (!new File(parent).exists()) {
            new File(parent).mkdirs();
        }
        String imagePath = parent + "/" + System.currentTimeMillis() + ".png";
        FileOutputStream fos = new FileOutputStream(imagePath);
        croppedImage.compress(Bitmap.CompressFormat.JPEG, 60, fos);
        fos.close();
        croppedImage.recycle();
        return imagePath;
    }

    private Bitmap decodeRegionCrop(byte[] data) {
        InputStream is = null;
        System.gc();
        Bitmap croppedImage = null;
        try {
            is = new ByteArrayInputStream(data);
            croppedImage = BitmapFactory.decodeByteArray(data, 0, data.length);
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
            }
        }
        Matrix m = new Matrix();
        m.setRotate(90);
        if (cameraId == 1) {
            m.postScale(1, -1);
        }
        Bitmap rotatedImage = Bitmap.createBitmap(croppedImage, 0, 0, croppedImage.getWidth(), croppedImage.getHeight(), m, true);
        if (rotatedImage != croppedImage)
            croppedImage.recycle();
        return rotatedImage;
    }


    @SuppressLint("NewApi")
    private void getVideoPreviewSize(boolean isFront) {
        CamcorderProfile profile;
        int cameraId = 0;
        if (isFront) {
            cameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
        } else {
            cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
        }
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_480P)) {
            profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_480P);
            if (profile != null) {
                Point point = new Point();
                point.x = profile.videoFrameWidth;
                point.y = profile.videoFrameHeight;
                if (isFront) {
                    frontCameraSize.addLast(point);
                } else {
                    backCameraSize.addLast(point);
                }
            }
        }
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_CIF)) {
            profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_CIF);
            if (profile != null) {
                Point point = new Point();
                point.x = profile.videoFrameWidth;
                point.y = profile.videoFrameHeight;
                if (isFront) {
                    frontCameraSize.addLast(point);
                } else {
                    backCameraSize.addLast(point);
                }
            }
        }
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_QVGA)) {
            profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_QVGA);
            if (profile != null) {
                Point point = new Point();
                point.x = profile.videoFrameWidth;
                point.y = profile.videoFrameHeight;
                if (isFront) {
                    frontCameraSize.addLast(point);
                } else {
                    backCameraSize.addLast(point);
                }
            }
        }
        profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW);
        if (profile == null) {
            Point point = new Point();
            point.x = 320;
            point.y = 240;
            if (isFront) {
                frontCameraSize.addLast(point);
            } else {
                backCameraSize.addLast(point);
            }
        } else {
            Point point = new Point();
            point.x = profile.videoFrameWidth;
            point.y = profile.videoFrameHeight;
            if (isFront) {
                frontCameraSize.addLast(point);
            } else {
                backCameraSize.addLast(point);
            }
        }
    }


    @SuppressLint("NewApi")
    private void getVideoPreviewSize() {
        backCameraSize.clear();
        frontCameraSize.clear();
        getVideoPreviewSize(false);
        if (Camera.getNumberOfCameras() >= 2) {
            getVideoPreviewSize(true);
        }
    }

    private Point currentUsePoint = null;

    private void resizeSurfaceView() {
        Point point;
        if (cameraId == 0) {
            point = backCameraSize.getFirst();
        } else {
            point = frontCameraSize.getFirst();
        }
        if (currentUsePoint != null && point.equals(currentUsePoint)) {
            return;
        } else {
            currentUsePoint = point;
            int screenWidth = getWindowManager().getDefaultDisplay().getWidth();
            int surfaceHeight = screenWidth * point.x / point.y;
            /*if (surfaceview != null) {
                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) surfaceview.getLayoutParams();
                lp.width = screenWidth;
                lp.height = surfaceHeight;
                lp.addRule(13);
                surfaceview.setLayoutParams(lp);
            }*/
        }
    }


    @SuppressLint("NewApi")
    private void setCamcorderProfile() {
        CamcorderProfile profile;
        profile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
        if (profile != null) {
            if (currentUsePoint != null) {
                profile.videoFrameWidth = currentUsePoint.x;
                profile.videoFrameHeight = currentUsePoint.y;
            }
            profile.fileFormat = MediaRecorder.OutputFormat.MPEG_4;
            if (Build.MODEL.equalsIgnoreCase("MB525") || Build.MODEL.equalsIgnoreCase("C8812") ||
                    Build.MODEL.equalsIgnoreCase("C8650")) {
                profile.videoCodec = MediaRecorder.VideoEncoder.H263;
            } else {
                profile.videoCodec = MediaRecorder.VideoEncoder.H264;
            }
            profile.audioCodec = MediaRecorder.AudioEncoder.AAC;
            mediaRecorder.setProfile(profile);
        } else {
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setVideoSize(VIDEO_WIDTH, VIDEO_HEIGHT);
        }
    }

    @SuppressLint("NewApi")
    private void setVideoOrientation() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        mediaRecorder.setOrientationHint(info.orientation);
    }

    public void updateRecordUI() {
        if (recording) {
            recordBtn.setBackgroundResource(R.drawable.video_capture_stop_btn);
        } else {
            recordBtn.setBackgroundResource(R.drawable.video_capture_start_btn);
        }
    }

    private boolean startRecorderInternal(boolean isCamera) throws Exception {
        shutdownCamera();
        if (!initCamera(isCamera)) {
            return false;
        }
        switchCamera.setVisibility(View.GONE);
        if (!isCamera) {
            mediaRecorder = new MediaRecorder();
            camera.unlock();
            mediaRecorder.setCamera(camera);
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            setCamcorderProfile();
            mediaRecorder.setPreviewDisplay(surfaceHolder.getSurface());
            mediaRecorder.setMaxDuration(1000 * VIDEO_TIMES);
            mediaRecorder.setOutputFile(filename);
            setVideoOrientation();
            mediaRecorder.prepare();
            mediaRecorder.start();
        }
        return true;
    }

    private void startRecorder(boolean isCamera) {
        try {
            startRecorderInternal(isCamera);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "视频初始化失败", Toast.LENGTH_SHORT).show();
            if (mediaRecorder != null) {
                mediaRecorder.release();
                mediaRecorder = null;
            }
            camera.release();
            camera = null;
            return;
        }
        if (!isCamera) {
            recording = true;
            start = new Date().getTime();
            handler.postDelayed(runnable, 1000);
            recordingTimeTextView.setText("00:00");
            updateRecordUI();
        }
    }

    private void stopRecorder() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
            } catch (Exception e) {
            }
            mediaRecorder.release();
            mediaRecorder = null;
        }
        if (camera != null) {
            camera.release();
            camera = null;
        }
        handler.removeCallbacks(runnable);
        recordingState.setBackgroundResource(R.drawable.shape_red_circle);
        recording = false;
        updateRecordUI();
    }

    private void sendVideo() {
        File convertedFile = new File(filename);
        if (convertedFile.exists()) {
            int b = (int) convertedFile.length();
            int kb = b / 1024;
            float mb = kb / 1024f;
            if (mb < 1 && kb < 10) {
                Toast.makeText(this, "视频录制时间太短", Toast.LENGTH_SHORT).show();
                cancelRecord();
                return;
            }
            Toast.makeText(this, "录制成功", Toast.LENGTH_SHORT).show();
        }
    }


    /**
     * 取消重录
     */
    private void cancelRecord() {
        new File(filename).delete();
        recordingTimeTextView.setText("00:00");
        shutdownCamera();
        initCamera(true);
        startPreview();
        checkMultiCamera();
    }

    /**
     * *************************************************** Camera Start ***************************************************
     */
    @SuppressLint("NewApi")
    public void checkMultiCamera() {
        if (Camera.getNumberOfCameras() > 1) {
            multiCamera = true;
            switchCamera.setVisibility(View.VISIBLE);
        } else {
            switchCamera.setVisibility(View.GONE);
        }
    }

    @SuppressLint("NewApi")
    private boolean initCamera(boolean isCamera) {
        try {
            if (multiCamera) {
                camera = Camera.open(cameraId);
            } else {
                camera = Camera.open();
            }
        } catch (RuntimeException e) {
            Toast.makeText(this, "无法连接视频设备", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            return false;
        }
        if (camera != null) {
            setCameraParameters(isCamera);
        }
        return camera != null;
    }

    @SuppressLint("NewApi")
    private void setCameraParameters(boolean isCamera) {
        Camera.Parameters params = camera.getParameters();
        if (params.isVideoStabilizationSupported()) {
            params.setVideoStabilization(true);
        }
        if (isCamera) {
            setUpPicSize();
            setUpPreviewSize();
            Method downPolymorphic;
            params.setPictureFormat(PixelFormat.JPEG);
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            Log.d("Mozator", "初始化相机参数 设置宽高比  ");
            if (adapterSize != null) {
                params.setPictureSize(adapterSize.width, adapterSize.height);
            }
            if (previewSize != null) {
                params.setPreviewSize(previewSize.width, previewSize.height);
            }
            try {
                downPolymorphic = camera.getClass().getMethod("setDisplayOrientation",
                        new Class[]{int.class});
                if (downPolymorphic != null) {
                    downPolymorphic.invoke(camera, new Object[]{90});
                }
            } catch (Exception e) {
                Log.e("Came_e", "图像出错");
            }
        } else {
            List<String> focusMode = params.getSupportedFocusModes();
            if (focusMode.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            }
            mAngle = setCameraDisplayOrientation(this, cameraId, camera);
            params.setPreviewSize(currentUsePoint.x, currentUsePoint.y);
        }
        try {
            camera.setParameters(params);
        } catch (RuntimeException e) {
        }
        if (isCamera) {
            camera.startPreview();
            camera.cancelAutoFocus();// 2如果要实现连续的自动对焦，这一句必须加上
        }
    }

    private Camera.Size adapterSize = null;
    private Camera.Size previewSize = null;
    private static final double MAX_ASPECT_DISTORTION = 0.15;
    private static final int MIN_PREVIEW_PIXELS = 480 * 320;

    private void setUpPicSize() {
        if (adapterSize != null) {
            return;
        } else {
            adapterSize = findBestPictureResolution();
            return;
        }
    }

    private void setUpPreviewSize() {

        if (previewSize != null) {
            return;
        } else {
            previewSize = findBestPreviewResolution();
        }
    }


    private Camera.Size findBestPreviewResolution() {
        Camera.Parameters cameraParameters = camera.getParameters();
        Camera.Size defaultPreviewResolution = cameraParameters.getPreviewSize();

        List<Camera.Size> rawSupportedSizes = cameraParameters.getSupportedPreviewSizes();
        if (rawSupportedSizes == null) {
            return defaultPreviewResolution;
        }

        // 按照分辨率从大到小排序
        List<Camera.Size> supportedPreviewResolutions = new ArrayList<Camera.Size>(rawSupportedSizes);
        Collections.sort(supportedPreviewResolutions, (a, b) -> {
            int aPixels = a.height * a.width;
            int bPixels = b.height * b.width;
            if (bPixels < aPixels) {
                return -1;
            }
            if (bPixels > aPixels) {
                return 1;
            }
            return 0;
        });

        StringBuilder previewResolutionSb = new StringBuilder();
        for (Camera.Size supportedPreviewResolution : supportedPreviewResolutions) {
            previewResolutionSb.append(supportedPreviewResolution.width).append('x').append(supportedPreviewResolution.height)
                    .append(' ');
        }
        Log.v(TAG, "Supported preview resolutions: " + previewResolutionSb);


        // 移除不符合条件的分辨率
        double screenAspectRatio = (double) AppTools.getWindowWidth(this)
                / (double) AppTools.getWindowHeight(this);
        Iterator<Camera.Size> it = supportedPreviewResolutions.iterator();
        while (it.hasNext()) {
            Camera.Size supportedPreviewResolution = it.next();
            int width = supportedPreviewResolution.width;
            int height = supportedPreviewResolution.height;

            // 移除低于下限的分辨率，尽可能取高分辨率
            if (width * height < MIN_PREVIEW_PIXELS) {
                it.remove();
                continue;
            }

            // 在camera分辨率与屏幕分辨率宽高比不相等的情况下，找出差距最小的一组分辨率
            // 由于camera的分辨率是width>height，我们设置的portrait模式中，width<height
            // 因此这里要先交换然preview宽高比后在比较
            boolean isCandidatePortrait = width > height;
            int maybeFlippedWidth = isCandidatePortrait ? height : width;
            int maybeFlippedHeight = isCandidatePortrait ? width : height;
            double aspectRatio = (double) maybeFlippedWidth / (double) maybeFlippedHeight;
            double distortion = Math.abs(aspectRatio - screenAspectRatio);
            if (distortion > MAX_ASPECT_DISTORTION) {
                it.remove();
                continue;
            }

            // 找到与屏幕分辨率完全匹配的预览界面分辨率直接返回
            if (maybeFlippedWidth == AppTools.getWindowWidth(this)
                    && maybeFlippedHeight == AppTools.getWindowHeight(this)) {
                return supportedPreviewResolution;
            }
        }

        // 如果没有找到合适的，并且还有候选的像素，则设置其中最大比例的，对于配置比较低的机器不太合适
        if (!supportedPreviewResolutions.isEmpty()) {
            Camera.Size largestPreview = supportedPreviewResolutions.get(0);
            return largestPreview;
        }

        // 没有找到合适的，就返回默认的

        return defaultPreviewResolution;
    }

    private Camera.Size findBestPictureResolution() {
        Camera.Parameters cameraParameters = camera.getParameters();
        List<Camera.Size> supportedPicResolutions = cameraParameters.getSupportedPictureSizes(); // 至少会返回一个值

        StringBuilder picResolutionSb = new StringBuilder();
        for (Camera.Size supportedPicResolution : supportedPicResolutions) {
            picResolutionSb.append(supportedPicResolution.width).append('x')
                    .append(supportedPicResolution.height).append(" ");
        }
        Log.d(TAG, "Supported picture resolutions: " + picResolutionSb);

        Camera.Size defaultPictureResolution = cameraParameters.getPictureSize();
        Log.d(TAG, "default picture resolution " + defaultPictureResolution.width + "x"
                + defaultPictureResolution.height);

        // 排序
        List<Camera.Size> sortedSupportedPicResolutions = new ArrayList<Camera.Size>(
                supportedPicResolutions);
        Collections.sort(sortedSupportedPicResolutions, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size a, Camera.Size b) {
                int aPixels = a.height * a.width;
                int bPixels = b.height * b.width;
                if (bPixels < aPixels) {
                    return -1;
                }
                if (bPixels > aPixels) {
                    return 1;
                }
                return 0;
            }
        });

        // 移除不符合条件的分辨率
        double screenAspectRatio = (double) AppTools.getWindowWidth(this)
                / (double) AppTools.getWindowHeight(this);
        Iterator<Camera.Size> it = sortedSupportedPicResolutions.iterator();
        while (it.hasNext()) {
            Camera.Size supportedPreviewResolution = it.next();
            int width = supportedPreviewResolution.width;
            int height = supportedPreviewResolution.height;

            // 在camera分辨率与屏幕分辨率宽高比不相等的情况下，找出差距最小的一组分辨率
            // 由于camera的分辨率是width>height，我们设置的portrait模式中，width<height
            // 因此这里要先交换然后在比较宽高比
            boolean isCandidatePortrait = width > height;
            int maybeFlippedWidth = isCandidatePortrait ? height : width;
            int maybeFlippedHeight = isCandidatePortrait ? width : height;
            double aspectRatio = (double) maybeFlippedWidth / (double) maybeFlippedHeight;
            double distortion = Math.abs(aspectRatio - screenAspectRatio);
            if (distortion > MAX_ASPECT_DISTORTION) {
                it.remove();
                continue;
            }
        }

        // 如果没有找到合适的，并且还有候选的像素，对于照片，则取其中最大比例的，而不是选择与屏幕分辨率相同的
        if (!sortedSupportedPicResolutions.isEmpty()) {
            return sortedSupportedPicResolutions.get(0);
        }

        // 没有找到合适的，就返回默认的
        return defaultPictureResolution;
    }


    private void shutdownCamera() {
        try {
            if (camera != null) {
                if (previewing) {
                    camera.stopPreview();
                }
                camera.release();
                camera = null;
                previewing = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * **************************** SurfaceHolder.Callback Start *******************************
     */

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        surfaceHolder = holder;
    }

    public void surfaceCreated(SurfaceHolder holder) {
        surfaceHolder = holder;
        shutdownCamera();
        if (!initCamera(true)) {
            return;
        }
        startPreview();
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceHolder = null;
        mediaRecorder = null;
    }

    /**
     * ************************ SurfaceHolder.Callback Start ********************************
     */

    private void startPreview() {
        try {
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();
            previewing = true;
        } catch (Exception e) {
            Toast.makeText(this, "无法连接视频设备", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            shutdownCamera();
            e.printStackTrace();
        }
    }

    /**
     * ********************************* camera util ************************************
     */
    @SuppressLint("NewApi")
    public int setCameraDisplayOrientation(Context context, int cameraId, Camera camera) {
        int orientation;
        boolean front;
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        orientation = info.orientation;
        front = (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT);
        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        int rotation = manager.getDefaultDisplay().getRotation();
        int activityOrientation = roundRotation(rotation);
        int result;
        if (front) {
            result = (orientation + activityOrientation) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (orientation - activityOrientation + 360) % 360;
            //遇到过一个小米1s后置摄像头旋转180°，但是不确定是不是所有小米1s都是这样的. 先做一个适配,以后有问题再说.
            if ("Xiaomi_MI-ONE Plus".equalsIgnoreCase(Build.MANUFACTURER + "_" + Build.MODEL)) {
                result = 90;
            }
        }
        camera.setDisplayOrientation(result);
        return result;
    }

    private int roundRotation(int rotation) {
        switch (rotation) {
            case Surface.ROTATION_0:
                return 0;
            case Surface.ROTATION_90:
                return 90;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_270:
                return 270;
        }
        return 0;
    }

}