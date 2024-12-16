package com.paddle.demo.matting;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FaceSwapActivity extends AppCompatActivity {
    private ImageView sourceImage;
    private ImageView targetImage;
    private ImageView resultImage;
    private Button swapFacesButton;
    private ProgressBar loadingSpinner;
    private String FACE_URL;
    private static final int PICK_TARGET_IMAGE = 1;

    private String sourceImagePath;
    private Bitmap targetBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_swap);

        FACE_URL = getString(R.string.FACE_URL);

        sourceImage = findViewById(R.id.source_image);
        targetImage = findViewById(R.id.target_image);
        resultImage = findViewById(R.id.result_image);
        Button uploadTargetButton = findViewById(R.id.upload_target_button);
        swapFacesButton = findViewById(R.id.swap_faces_button);
        loadingSpinner = findViewById(R.id.loading_spinner);

        sourceImagePath = getIntent().getStringExtra("resultImagePath");
        if (sourceImagePath != null) {
            Bitmap sourceBitmap = BitmapFactory.decodeFile(sourceImagePath);
            sourceImage.setImageBitmap(sourceBitmap);
        }

        uploadTargetButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(intent, PICK_TARGET_IMAGE);
        });

        swapFacesButton.setOnClickListener(v -> performFaceSwap());

        setupImageClickListeners();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_TARGET_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri selectedImage = data.getData();
            try {
                targetBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImage);
                targetImage.setImageBitmap(targetBitmap);
                swapFacesButton.setEnabled(true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void setupImageClickListeners() {
        sourceImage.setOnClickListener(v -> showFullScreenImage(sourceImage));
        targetImage.setOnClickListener(v -> showFullScreenImage(targetImage));
        resultImage.setOnClickListener(v -> showFullScreenImage(resultImage));
    }

    private void showFullScreenImage(ImageView imageView) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_fullscreen_image);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }

        ImageView fullscreenImage = dialog.findViewById(R.id.fullscreen_image);
        fullscreenImage.setImageDrawable(imageView.getDrawable());

        fullscreenImage.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void performFaceSwap() {
        loadingSpinner.setVisibility(View.VISIBLE);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.MINUTES)
                .readTimeout(2, TimeUnit.MINUTES)
                .writeTimeout(2, TimeUnit.MINUTES)
                .build();

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("source_image", "source.png",
                        RequestBody.create(MediaType.parse("image/png"), bitmapToByteArray(targetBitmap)))
                .addFormDataPart("target_image", "target.png",
                        RequestBody.create(MediaType.parse("image/png"), new File(sourceImagePath)))
                .build();

        Request request = new Request.Builder()
                .url(FACE_URL)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    loadingSpinner.setVisibility(View.GONE);
                    Toast.makeText(FaceSwapActivity.this, "网络请求失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (response.isSuccessful()) {
                    final Bitmap resultBitmap = BitmapFactory.decodeStream(response.body().byteStream());
                    runOnUiThread(() -> {
                        resultImage.setImageBitmap(resultBitmap);
                        resultImage.setVisibility(View.VISIBLE);
                        loadingSpinner.setVisibility(View.GONE);
                    });
                } else {
                    runOnUiThread(() -> {
                        loadingSpinner.setVisibility(View.GONE);
                        Toast.makeText(FaceSwapActivity.this, "服务器响应错误: " + response.code(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private byte[] bitmapToByteArray(Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        return stream.toByteArray();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sourceImagePath != null) {
            new File(sourceImagePath).delete();
        }
    }
}