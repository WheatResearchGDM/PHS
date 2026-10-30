package br.com.phs.coleta;

import android.graphics.*;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.*;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/** Reads only the central square visible in the preview; neighboring tags are discarded. */
final class ScanController {
  interface Listener {
    void onCode(String value);

    void onError(String message);
  }

  private final PreviewView preview;
  private final LifecycleOwner owner;
  private final Listener listener;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final BarcodeScanner scanner =
      BarcodeScanning.getClient(
          new BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build());
  private volatile boolean enabled = true, busy = false;
  private ProcessCameraProvider provider;
  private final ScanGate gate = new ScanGate();

  ScanController(PreviewView preview, LifecycleOwner owner, Listener listener) {
    this.preview = preview;
    this.owner = owner;
    this.listener = listener;
  }

  void start() {
    ListenableFuture<ProcessCameraProvider> future =
        ProcessCameraProvider.getInstance(preview.getContext());
    future.addListener(
        () -> {
          try {
            provider = future.get();
            Preview cameraPreview =
                new Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build();
            ImageAnalysis analysis =
                new ImageAnalysis.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
            cameraPreview.setSurfaceProvider(preview.getSurfaceProvider());
            analysis.setAnalyzer(executor, this::analyze);
            provider.unbindAll();
            provider.bindToLifecycle(
                owner, CameraSelector.DEFAULT_BACK_CAMERA, cameraPreview, analysis);
          } catch (Exception e) {
            listener.onError("Câmera indisponível. Use a busca por parcela.");
          }
        },
        ContextCompat.getMainExecutor(preview.getContext()));
  }

  void setEnabled(boolean value) {
    enabled = value;
  }

  void afterSave(String code) {
    gate.afterSave(code);
  }

  void allowRepeatedCode() {
    gate.clear();
  }

  private void analyze(ImageProxy image) {
    if (!enabled || busy) {
      image.close();
      return;
    }
    busy = true;
    try {
      ImageProxy.PlaneProxy plane = image.getPlanes()[0];
      ByteBuffer buffer = plane.getBuffer();
      int stride = plane.getRowStride(), pixel = plane.getPixelStride();
      int width = image.getWidth(), height = image.getHeight();
      int[] gray = new int[width * height];
      int offset = buffer.position();
      for (int y = 0; y < height; y++)
        for (int x = 0; x < width; x++) {
          int value = buffer.get(offset + y * stride + x * pixel) & 255;
          gray[y * width + x] = Color.rgb(value, value, value);
        }
      Bitmap raw = Bitmap.createBitmap(gray, width, height, Bitmap.Config.ARGB_8888);
      Matrix rotation = new Matrix();
      rotation.postRotate(image.getImageInfo().getRotationDegrees());
      Bitmap upright = Bitmap.createBitmap(raw, 0, 0, width, height, rotation, true);
      if (upright != raw) raw.recycle();
      // PreviewView is square and FILL_CENTER; the framing square covers 68% of it.
      int side = Math.round(Math.min(upright.getWidth(), upright.getHeight()) * .68f);
      Bitmap crop =
          Bitmap.createBitmap(
              upright,
              (upright.getWidth() - side) / 2,
              (upright.getHeight() - side) / 2,
              side,
              side);
      if (crop != upright) upright.recycle();
      scanner
          .process(InputImage.fromBitmap(crop, 0))
          .addOnSuccessListener(
              codes -> {
                if (!enabled) return;
                List<String> visible = new ArrayList<>();
                for (Barcode code : codes) {
                  Rect rect = code.getBoundingBox();
                  if (code.getRawValue() != null
                      && rect != null
                      && rect.left >= 0
                      && rect.top >= 0
                      && rect.right <= side
                      && rect.bottom <= side) {
                    visible.add(Domain.cleanId(code.getRawValue()));
                  }
                }
                String accepted = gate.accept(visible, android.os.SystemClock.elapsedRealtime());
                if (accepted != null) {
                  enabled = false;
                  listener.onCode(accepted);
                }
              })
          .addOnCompleteListener(
              task -> {
                crop.recycle();
                busy = false;
              });
    } catch (Exception e) {
      busy = false;
    } finally {
      image.close();
    }
  }

  void close() {
    enabled = false;
    if (provider != null) provider.unbindAll();
    scanner.close();
    executor.shutdown();
  }
}
