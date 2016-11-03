package bb.hdr;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.os.Handler;

import java.io.IOException;

import mikera.arrayz.INDArray;
import mikera.arrayz.NDArray;

public class HDRBlend implements Runnable {

    private Handler handler;

    public HDRBlend(Handler hand){
        handler = hand;
    }

    // Loads images and blends them
    @Override
    public void run() {

        // Images
        NDArray[] images = new NDArray[5];
        NDArray[] greys = new NDArray[5];

        // Exposures (time)
        float[] exposures = new float[5];

        // File Names
        String[] fileNames = new String[5];

        // image folder
        String folderPath = FileIO.STORAGEPATH;

        // Get Images and exposures
        for(int i = 1; i < 6; i++) {
            String filePath = folderPath + "EXP_IMG_" + i + ".jpg";
            NDArray[] pixArrs = getPixelArray(filePath, true);
            images[i-1] = pixArrs[0];
            greys[i-1] = pixArrs[1];
            fileNames[i-1] = filePath;
            try{
                ExifInterface exif = new ExifInterface(filePath);
                exposures[i-1] = Float.parseFloat(exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME));
            } catch (IOException r) {

            }

        }

        NDArray likely = calculateLikelihoods(greys);

        NDArray out = blendWithLikelies(images,likely);

        // SET THE OUTPUT
        MainActivity.setOutput(out);

        // Delete Input Images
//        for(int i = 0; i < fileNames.length; i++) {
//            File file = new File(fileNames[i]);
//            file.delete();
//        }

        // SEND COMPLETED MSG
        handler.sendEmptyMessage(MainActivity.BLEND_FINISHED);

    }

    // Calculate the likelihood of a pixel being part of a shadow or highlight (based off middle exposure image)
    private NDArray calculateLikelihoods(NDArray[] g) {

        int h = g[0].getShape()[0];
        int w = g[0].getShape()[1];

        NDArray likelies = NDArray.newArray(new int[]{h,w});


        // Likelihood of shadow/highlight (0.0 = highlight, 1.0 = shadow)
        for(int r = 0; r < h; r++) {
            for(int c = 0; c < w; c++) {
                double val = g[2].get(r,c);
                double l;
                double hevDiff = g[4].get(r,c) - g[2].get(r,c);
                double levDiff = g[2].get(r,c) - g[0].get(r,c);
                if(val > 127) { // highlights
                    l = Math.pow((val - 127.0) / 128.0,1);
                    l = l*128 + 127;
                    l = 1-(l / 255.0);
//                    double diff = Math.abs(g[2].get(r,c) - g[1].get(r,c)) + Math.abs(g[2].get(r,c) - g[0].get(r,c));
//                    double diff1 = Math.min(diff,50.0) / 50.0;
//                    l = .5 - ((.5 - l) * diff1);
//                    if(levDiff > 205) {
//                        l = .5 - ((.5 - l) * .2);
//                    }
                }
                else{ // shadows
                    l = Math.pow(((val-127) * -1) / 128,1);
                    l = 127 - l*128;
                    l = 1-(l / 255.0);
//                    double diff = Math.abs(g[2].get(r, c) - g[3].get(r,c)) + Math.abs(g[2].get(r,c) - g[4].get(r,c));
//                    double diff1 = Math.min(diff,50.0) / 50.0;
//                    l = .5 + ((l - .5) * diff1);
//                    if(hevDiff > 205) {
//                        l = .5 + ((l - .5) * .2);
//                    }
                }
                likelies.set(new int[]{r,c},l);
            }
        }

        return likelies;
    }

    // Blend the images based on the calculated likelihoods
    private NDArray blendWithLikelies(NDArray[] images, NDArray likelies) {

        int h = images[0].getShape()[0];
        int w = images[0].getShape()[1];

        NDArray out = NDArray.newArray(images[0].getShape());
        NDArray cVals = NDArray.newArray(images[0].getShape());

        // Blend
        for(int i = 0; i < images.length; i++) {
            // Calculate weights from likelies
            for(int r = 0; r < h; r++) {
                for (int c = 0; c < w; c++) {
                    float mult = 1.0f - Math.min(Math.abs((float)(i*.25-likelies.get(r,c))),.25f) / .25f;
                    cVals.set(new int[]{r,c,0},mult);
                    cVals.set(new int[]{r,c,1},mult);
                    cVals.set(new int[]{r,c,2},mult);
                }
            }
            // Add weighted sum to output image
            INDArray b = images[i].multiplyCopy(cVals);
            out.add(b);
        }

        out = MainActivity.normalizeNDArray(out,255.0);

        return out;
    }

    // load an image from file (steps explicitly separated to avoid memory issues)
    private NDArray[] getPixelArray(String filePath, boolean greyScale) {

        Bitmap bitmap = BitmapFactory.decodeFile(filePath);

        // images blurred slightly to smooth transitions between adjacent pixels sampled from different input images
        Bitmap bitmapG = GaussianBlur.blur(MainActivity.getContext(), bitmap);

        int h = bitmap.getHeight();
        int w = bitmap.getWidth();

        int[] pixelsC = new int[bitmap.getWidth()*bitmap.getHeight()];
        bitmap.getPixels(pixelsC, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        int[] pixelsCG = new int[bitmap.getWidth()*bitmap.getHeight()];
        bitmapG.getPixels(pixelsCG, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        bitmap.recycle();
        bitmap = null;

        double[] pixels = new double[pixelsC.length*3];
        double[] gPix = new double[pixelsC.length];

        if(greyScale) { // convert color to greyscale
            for(int i = 0; i < pixelsC.length; i++){
                int j = i*3;
                pixels[j] = (pixelsC[i] >> 16) & 0xff;
                pixels[j+1] = (pixelsC[i] >> 8) & 0xff;
                pixels[j+2] = (pixelsC[i] >> 0) & 0xff;
                double r = (pixelsCG[i] >> 16) & 0xff;
                double g = (pixelsCG[i] >> 8) & 0xff;
                double b = (pixelsCG[i] >> 0) & 0xff;
                gPix[i] = (r+g+b) / 3;
            }
        }
        else {
            for (int i = 0; i < pixelsC.length; i++) {
                int j = i * 3;
                pixels[j] = (pixelsC[i] >> 16) & 0xff;
                pixels[j + 1] = (pixelsC[i] >> 8) & 0xff;
                pixels[j + 2] = (pixelsC[i] >> 0) & 0xff;
            }
        }

        pixelsC = null;

        int[] shape = new int[]{h, w, 3};
        NDArray image = NDArray.wrap(pixels, shape);
        pixels = null;

        if(greyScale){
            int[] shapeG = new int[]{h,w};
            NDArray gImage = NDArray.wrap(gPix,shapeG);
            gPix = null;
            return new NDArray[] {image,gImage};
        }

        return new NDArray[] {image};

    }

}
