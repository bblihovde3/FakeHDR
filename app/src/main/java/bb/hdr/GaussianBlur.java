package bb.hdr;

import android.content.Context;
import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;

public class GaussianBlur {

    // Blurs the given bitmap using a guassian blur
    public static Bitmap blur(Context context, Bitmap image) {
        int width = Math.round(image.getWidth() * 1.0f);
        int height = Math.round(image.getHeight() * 1.0f);

        Bitmap inputBitmap = Bitmap.createScaledBitmap(image, width, height, false);
        inputBitmap = inputBitmap.copy(inputBitmap.getConfig(),true);
        Bitmap outputBitmap = Bitmap.createBitmap(inputBitmap);

        RenderScript rs = RenderScript.create(context);
        ScriptIntrinsicBlur intrinsic = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
        Allocation tmpIn = Allocation.createFromBitmap(rs, inputBitmap);
        Allocation tmpOut = Allocation.createFromBitmap(rs, outputBitmap);
        intrinsic.setRadius(7.0f);
        intrinsic.setInput(tmpIn);
        intrinsic.forEach(tmpOut);
        tmpOut.copyTo(outputBitmap);

        return outputBitmap;
    }
}