package bb.hdr;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import mikera.arrayz.NDArray;

public class MainActivity extends AppCompatActivity {

    // this context
    private static Context context;

    // UI elements
    private TextView tV;
    private FrameLayout statusBox;
    private Camera camera;
    private CameraPreview mPreview;
    private int cameraId;
    private FrameLayout previewHolder;
    private Camera.PictureCallback pCall;
    private Button picButton;

    // Image sizes for camera
    private List<Camera.Size> sizes;

    // Exposure compensation Steps
    private int[] comps = new int[] {-12,-9,-6,-3,0,3,6,9,12};

    // actual value calculated at setup
    private int sizeIndex = -1;

    // current number of pictures taken and the desired number
    private int picsTaken = 0;
    private int picsDesired = 5;

    // Exposure values calculated at runtime
    private int exposureMin;
    private int exposureMax;
    private int exposureStep;

    // Message sent by HDRBlend when it has finished running
    public static final int BLEND_FINISHED = 0;

    // Handler passed to HDRBlend
    private Handler handler;

    // HDRBlend saves out to this NDArray
    private static NDArray output;

    // Last file saved
    private static String lastFileName;

    private final String TAG = "HDR_MAIN";

    // Permission request codes
    private static final int REQUEST_CODE_CAMERA = 0;
    private static final int REQUEST_CODE_STORAGE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this;
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        setUIElements();
        checkPermissions();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            initializeCamera();
            setListeners();
        }
        main();
    }

    private void main() {

        // set status message boxes as invisible at startup
        statusBox.setVisibility(View.INVISIBLE);
        tV.setVisibility(View.INVISIBLE);

    }

    // Instantiate the local UI variables
    private void setUIElements() {
        tV = (TextView) findViewById(R.id.textView);
        statusBox = (FrameLayout) findViewById(R.id.StatusBox);
        previewHolder = (FrameLayout) findViewById(R.id.camera_preview);
        picButton = (Button) findViewById(R.id.picButton);
    }

    /** Check if this device has a camera */
    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

    /** A safe way to get an instance of the Camera object. */
    public static Camera getCameraInstance(int id){
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    // Find camera and set camera specific settings
    private void initializeCamera() {
        if(checkCameraHardware(this)) { // Camera found

            cameraId = findBackFacingCamera();

            camera = getCameraInstance(cameraId);

            if(camera == null){
                tV.setText("Unable to open Camera instance");
            }
            else{

                Camera.Parameters p = camera.getParameters();

                // Set Camera size index
                List<Camera.Size> sizes = p.getSupportedPictureSizes();
                Camera.Size size;
                if (sizeIndex != -1) size = sizes.get(sizeIndex);
                else {
                    size = sizes.get(0);
                    int dist = Math.abs(size.width - 1280) + Math.abs(size.width - 720);
                    for (int i = 0; i < sizes.size(); i++) {
                        int d = Math.abs(sizes.get(i).width - 1280) + Math.abs(sizes.get(i).height - 720);
                        if (d < dist) {
                            dist = d;
                            size = sizes.get(i);
                            sizeIndex = i;
                        }
                    }
                }

                // Camera Image Settings
                p.setPictureSize(size.width, size.height);
                p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                p.setPictureFormat(ImageFormat.JPEG);
                p.setJpegQuality(100);
                p.setExposureCompensation(0);

                camera.setParameters(p);

                // Camera Preview
                mPreview = new CameraPreview(this, camera);
                previewHolder.addView(mPreview);

                setCallBack();

            }

        }
        else { // No camera found
            tV.setText("Device has no camera!");
        }
    }

    // Reset the camera settings (for resuming etc)
    private void resetCamera() {
        if (camera == null){
            camera = getCameraInstance(cameraId);
        }

        Camera.Parameters p = camera.getParameters();

        List<Camera.Size> sizes = p.getSupportedPictureSizes();
        Camera.Size size;
        if (sizeIndex != -1) size = sizes.get(sizeIndex);
        else {
            size = sizes.get(0);
            int dist = Math.abs(size.width - 1280) + Math.abs(size.width - 720);
            for (int i = 0; i < sizes.size(); i++) {
                int d = Math.abs(sizes.get(i).width - 1280) + Math.abs(sizes.get(i).height - 720);
                if (d < dist) {
                    dist = d;
                    size = sizes.get(i);
                    sizeIndex = i;
                }
            }
        }
        p.setPictureSize(size.width, size.height);
        p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        p.setPictureFormat(ImageFormat.JPEG);
        p.setJpegQuality(100);
        p.setExposureCompensation(0);

        camera.setParameters(p);

        mPreview = new CameraPreview(this,camera);
        previewHolder.addView(mPreview);
    }

    // Callback for camera taking a picture
    private void setCallBack() {
        pCall = new Camera.PictureCallback() {

            // When a picture has been taken
            @Override
            public void onPictureTaken(byte[] data, final Camera camera) {

                // Get File based on picture number
                File pictureFile = FileIO.getOutputMediaFile(picsTaken+1);
                if (pictureFile == null){ // sdcard not mounted or no permissions
                    Log.d(TAG, "Error creating media file, check storage permissions");
                    return;
                }
                try {
                    // Write the data to file
                    FileOutputStream fos = new FileOutputStream(pictureFile);
                    fos.write(data);
                    fos.close();

                    // Send intent to scan the folder for new media files
                    Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    intent.setData(Uri.fromFile(pictureFile));
                    sendBroadcast(intent);

                } catch (FileNotFoundException e) {
                    System.out.println("File not found: " + e.getMessage());
                } catch (IOException e) {
                    System.out.println("Error accessing file: " + e.getMessage());
                }

                picsTaken++;

                // Take pictures until the desired number has been reached
                if (picsTaken < picsDesired) {

                    // reset preview
                    camera.startPreview();

                    // increase exposure compensation
                    Camera.Parameters p = camera.getParameters();
                    p.setExposureCompensation(p.getExposureCompensation() + exposureStep);
                    camera.setParameters(p);

                    // automatically take the next picture after 1 second (gives time for exposure compensation etc to adjust)
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            camera.takePicture(null, null, pCall);
                        }
                    }, 1000);
                }
                else { // done taking pictures

                    // Reset camera parameters
                    Camera.Parameters p = camera.getParameters();
                    p.setExposureCompensation(0);
                    p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                    p.setAutoWhiteBalanceLock(false);
                    camera.setParameters(p);

                    // Start the blend process
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            processImages();
                        }
                    }, 1000);

                }

            }
        };
    }

    // Set button listener and HDRBlend handler
    private void setListeners() {
        picButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

//                openInGallery("EXP_IMG_3");

//                processImages();

                picsTaken = 0;

                picButton.setEnabled(false);

                Camera.Parameters p = camera.getParameters();
                exposureMin = p.getMinExposureCompensation();
                exposureMax = p.getMaxExposureCompensation();
                exposureStep = exposureMax / 2;
                p.setExposureCompensation(exposureMin);
                p.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
                p.setAutoWhiteBalanceLock(true);
                camera.setParameters(p);

                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        camera.takePicture(null, null, pCall);
                    }
                }, 1000);

                //camera.takePicture(null, null, pCall);

            }
        });

        // Handler for the HDRBLend runnable
        handler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                switch(msg.what) {
                    case BLEND_FINISHED: // Processing has finished
                        onProcessingFinished();
                        break;
                }
            }

        };

    }

    @Override
    protected void onPause() {
        super.onPause();
        if (camera != null) {
            camera.release();
            camera = null;
        }
        previewHolder.removeAllViews();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (camera != null){
            camera.release();
            camera = null;
        }
        previewHolder.removeAllViews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPermissions();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            resetCamera();
        }
    }

    public static NDArray normalizeNDArray(NDArray arrOriginal, double max) {
        NDArray newArray = arrOriginal.exactClone();

        newArray.add(newArray.elementMin() * -1);
        newArray.multiply(255.0 / newArray.elementMax());

        return newArray;
    }

    // Start the HDRBlend when the last picture is taken
    private void processImages() {

        // Restart the preview and show the 'Processing' message
        camera.startPreview();
        tV.setText("Processing...");
        tV.setVisibility(View.VISIBLE);
        statusBox.setVisibility(View.VISIBLE);
        statusBox.setBackgroundResource(R.drawable.layout_bg_round);

        // Run the HDRBlend on a background thread
        Runnable computeHdr = new HDRBlend(handler);
        Thread compute = new Thread(computeHdr);
        compute.setDaemon(true);
        compute.start();

    }

    // HDR BLend has finished
    private void onProcessingFinished() {
        // remove 'processing message'
        tV.setText("Not Working");
        tV.setVisibility(View.INVISIBLE);
        statusBox.setVisibility(View.INVISIBLE);

        // Re-enable the picture button
        picButton.setEnabled(true);

        // OUTPUT IMAGE HERE
        String timeStamp = new SimpleDateFormat("yyyyMMddhhmm").format(new Date());
        String fileName = "HDR_" + timeStamp;

        lastFileName = fileName;

        FileIO.saveImage(output, fileName, context);

        openInGallery(lastFileName);

    }

    public static void setOutput(NDArray out) {
        output = out;
    }

    // Finds the ID of the camera that is back-facing (in case there is a front facing one as well)
    private int findBackFacingCamera() {

        // Search for the back facing camera
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                cameraId = i;
                break;
            }
        }
        return cameraId;
    }

    // Get this context
    public static Context getContext() {
        return context;
    }

    // Open an image with the gallery
    private void openInGallery(String imgName) {

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse("file://" + "/sdcard/pictures/HDR/" + imgName + ".jpg"), "image/jpeg");
        startActivity(intent);

    }

    // Check for storage and camera permissions
    private void checkPermissions() {

        // Camera
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.CAMERA)) {


            } else {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        REQUEST_CODE_CAMERA);
            }
        }

        // Storage
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {


            } else {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_CODE_STORAGE);
            }
        }

    }

}
