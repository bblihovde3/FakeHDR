package bb.hdr;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import mikera.arrayz.NDArray;

public class FileIO {

    public static final String STORAGEPATH = (new File(Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES), "HDR")).getPath() + File.separator;

    /** Create a File for saving an image or video */
    public static File getOutputMediaFile(int picNumber){

        // Check that the sdcard is mounted
        if(!Environment.getExternalStorageState().equals("mounted")){
            return null;
        }

        // "/sdcard/Pictures/HDR"
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "HDR");

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d("HDR", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String fileNumber = "" + picNumber;
        File mediaFile;
        mediaFile = new File(mediaStorageDir.getPath() + File.separator + "EXP_IMG_" + fileNumber + ".jpg");

        return mediaFile;
    }

    // Save the given image (NDArray) with the given filename
    public static void saveImage(NDArray pixelArray, String fileName, Context context) {

        int[] shape = pixelArray.getShape();
        int height = shape[0];
        int width = shape[1];

        int[] pixels = new int[width*height];

        // Put the image back into ARGB format
        for(int j = 0; j < shape[0]; j++){
            for(int i = 0; i < shape[1]; i++) {
                int val = 0xff000000 | ((int)(pixelArray.get(j,i,0)) << 16) | ((int)(pixelArray.get(j,i,1)) << 8) | (int)(pixelArray.get(j,i,2));
                pixels[j*width + i] = val;
            }
        }

        // Create bitmap
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        // Set the pixels
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);

        File file = null;

        FileOutputStream out = null;
        try {
            file = new File(STORAGEPATH + fileName + ".jpg");
            out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();

                    // Send intent to scan the files with the media scanner
                    Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    intent.setData(Uri.fromFile(file));
                    context.sendBroadcast(intent);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

}
