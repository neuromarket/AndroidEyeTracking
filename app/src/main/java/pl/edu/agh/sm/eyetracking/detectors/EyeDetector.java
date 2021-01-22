package pl.edu.agh.sm.eyetracking.detectors;

import android.util.Log;

import androidx.core.util.Pair;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.objdetect.CascadeClassifier;

import java.util.ArrayList;
import java.util.List;

import pl.edu.agh.sm.eyetracking.Frame;
import pl.edu.agh.sm.eyetracking.SquareRegion;
import pl.edu.agh.sm.eyetracking.util.Scale;
import pl.edu.agh.sm.eyetracking.util.Size;

public class EyeDetector {

    private static final String TAG = EyeDetector.class.getCanonicalName();
    public static final int SCALE = 4;
    private static final int MAX_SKIPPED_FRAMES = 5;

    private final CascadeClassifier classifier;
    private Frame frame;

    private SquareRegion leftEyeRegion;
    private SquareRegion rightEyeRegion;

    private int skippedFrames = MAX_SKIPPED_FRAMES;

    private boolean initialized;

    public EyeDetector(CascadeClassifier classifier) {
        this.classifier = classifier;
    }

    public void initialize(Size screenSize) {
        frame = new Frame(screenSize, SCALE);
        leftEyeRegion = new SquareRegion(screenSize);
        rightEyeRegion = new SquareRegion(screenSize);
        initialized = true;
    }

    public Pair<Rect, Rect> detect(CameraBridgeViewBase.CvCameraViewFrame originalFrame, Rect faceROI) {
        frame.update(originalFrame);

        Scale.scaleDown(faceROI, SCALE);
        Mat faceSubMat = frame.get().submat(faceROI);
        MatOfRect eyes = new MatOfRect();
        classifier.detectMultiScale(
                faceSubMat,
                eyes,
                1.1
        );
        faceSubMat.release();

        List<Rect> potentialEyes = new ArrayList<>();
        for (Rect rect : eyes.toArray()) {
            if (rect.y >= faceROI.height / 2) {
                continue;
            }
            potentialEyes.add(rect);
        }

        List<Rect> potentialLeftEyes = new ArrayList<>();
        List<Rect> potentialRightEyes = new ArrayList<>();
        for (Rect rect : potentialEyes) {
            if (rect.x < faceROI.width / 2) {
                potentialLeftEyes.add(rect);
            }
            else {
                potentialRightEyes.add(rect);
            }
        }

        if (potentialLeftEyes.isEmpty() && potentialRightEyes.isEmpty()) {
            if (skippedFrames == 0) {
                Log.d(TAG, "No eyes detected for " + MAX_SKIPPED_FRAMES + " frames");
                return null;
                // or: throw exception
            }
            skippedFrames--;
        }

        if (!potentialLeftEyes.isEmpty()) {
            updateRegion(leftEyeRegion, potentialLeftEyes, faceROI);
            Log.d(TAG, "Left eye region updated: " + leftEyeRegion.get().toString());
        }
        else {
            Log.d(TAG, "Left eye region reused");
        }

        if (!potentialRightEyes.isEmpty()) {
            updateRegion(rightEyeRegion, potentialRightEyes, faceROI);
            Log.d(TAG, "Right eye region updated: " + leftEyeRegion.get().toString());
        }
        else {
            Log.d(TAG, "Right eye region reused");
        }

        if (!potentialLeftEyes.isEmpty() && !potentialRightEyes.isEmpty()) {
            skippedFrames = MAX_SKIPPED_FRAMES;
        }

        return new Pair<>(leftEyeRegion.get(), rightEyeRegion.get());
    }

    private void updateRegion(SquareRegion region, List<Rect> potentialEyes, Rect faceROI) {
        Rect biggestEye = potentialEyes.get(0);
        for (Rect rect : potentialEyes) {
            if (rect.area() > biggestEye.area()) {
                biggestEye = rect;
            }
        }

        biggestEye.x += faceROI.x;
        biggestEye.y += faceROI.y;
        Scale.scaleUp(biggestEye, SCALE);

        region.update(biggestEye);
    }

    public void deinitialize() {
        if (!initialized) {
            return;
        }
        frame.release();

        frame = null;
        initialized = false;
    }
}