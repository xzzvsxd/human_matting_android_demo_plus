package com.paddle.demo.matting;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
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

public class TryonActivity extends AppCompatActivity {
    private ImageView inputImage;
    private ImageView clothImage;
    private ImageView resultImage;
    private ImageView maskImage;
    private ProgressBar loadingSpinner;
    private String TRYON_URL;
    private static final int PICK_CLOTH_IMAGE = 1;

    private String inputImagePath;
    private Bitmap clothBitmap;

    private Button nextStepButton;
    private Bitmap resultBitmap;
    private Button uploadClothButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tryon);

        TRYON_URL = getString(R.string.TRYON_URL);

        inputImage = findViewById(R.id.input_image);
        clothImage = findViewById(R.id.cloth_image);
        resultImage = findViewById(R.id.result_image);
        maskImage = findViewById(R.id.mask_image);
        uploadClothButton = findViewById(R.id.upload_cloth_button);
        loadingSpinner = findViewById(R.id.loading_spinner);

        inputImagePath = getIntent().getStringExtra("imagePath");
        if (inputImagePath != null) {
            File imageFile = new File(inputImagePath);
            if (imageFile.exists()) {
                Bitmap processedImage = BitmapFactory.decodeFile(inputImagePath);
                if (processedImage != null) {
                    inputImage.setImageBitmap(processedImage);
                }
            }
        }

        uploadClothButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(intent, PICK_CLOTH_IMAGE);
        });

        setupImageClickListeners();

        nextStepButton = findViewById(R.id.next_step_button);
        nextStepButton.setVisibility(View.GONE);
        nextStepButton.setOnClickListener(v -> startFaceSwapActivity());
    }

    private void setupImageClickListeners() {
        inputImage.setOnClickListener(v -> showFullScreenImage(inputImage));
        clothImage.setOnClickListener(v -> showFullScreenImage(clothImage));
        resultImage.setOnClickListener(v -> showFullScreenImage(resultImage));
        maskImage.setOnClickListener(v -> showFullScreenImage(maskImage));
    }

    private void showFullScreenImage(ImageView imageView) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_fullscreen_image);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }

        ImageView fullscreenImage = dialog.findViewById(R.id.fullscreen_image);
        fullscreenImage.setImageDrawable(imageView.getDrawable());

        // 设置图片的背景为白色
        fullscreenImage.setBackgroundColor(getResources().getColor(android.R.color.white));

        fullscreenImage.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_CLOTH_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri selectedImage = data.getData();
            try {
                clothBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImage);
                clothImage.setImageBitmap(clothBitmap);
                if (inputImagePath != null && clothBitmap != null) {
                    performTryon();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void performTryon() {
        loadingSpinner.setVisibility(View.VISIBLE);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.MINUTES)
                .readTimeout(2, TimeUnit.MINUTES)
                .writeTimeout(2, TimeUnit.MINUTES)
                .build();

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("input_img", "input.png",
                        RequestBody.create(MediaType.parse("image/png"), new File(inputImagePath)))
                .addFormDataPart("cloth_img", "cloth.png",
                        RequestBody.create(MediaType.parse("image/png"), bitmapToByteArray(clothBitmap)))
                .addFormDataPart("pose_type", "upper_body")
                .addFormDataPart("preserve_background", "true")
                .addFormDataPart("inpaint_face", "true")
                .addFormDataPart("n_trials", "30")
                .addFormDataPart("use_input_pose", "true")
                .addFormDataPart("smooth_mask", "1")
                .addFormDataPart("smooth_face", "1")
                .build();

        Request request = new Request.Builder()
                .url(TRYON_URL)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    loadingSpinner.setVisibility(View.GONE);
                    Toast.makeText(TryonActivity.this, "网络请求失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    runOnUiThread(() -> {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            String resultImageBase64 = jsonResponse.getString("result_image");
                            String maskImageBase64 = jsonResponse.getString("mask_image");

                            resultBitmap = base64ToBitmap(resultImageBase64);
                            Bitmap maskBitmap = base64ToBitmap(maskImageBase64);

                            resultImage.setImageBitmap(resultBitmap);
                            maskImage.setImageBitmap(maskBitmap);

                            uploadClothButton.setVisibility(View.GONE);

                            nextStepButton.setVisibility(View.VISIBLE);
                            nextStepButton.setText("下一步");
                        } catch (JSONException e) {
                            e.printStackTrace();
                            Toast.makeText(TryonActivity.this, "解析响应失败", Toast.LENGTH_SHORT).show();
                        } finally {
                            loadingSpinner.setVisibility(View.GONE);
                        }
                    });
                } else {
                    final String errorBody = response.body().string();
                    runOnUiThread(() -> {
                        loadingSpinner.setVisibility(View.GONE);
                        Toast.makeText(TryonActivity.this, "服务器响应错误: " + response.code(), Toast.LENGTH_SHORT).show();
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

    private Bitmap base64ToBitmap(String base64String) {
        byte[] decodedString = Base64.decode(base64String, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (inputImagePath != null) {
            new File(inputImagePath).delete();
        }
    }

    private void startFaceSwapActivity() {
        if (resultBitmap != null) {
            String tempFilePath = saveBitmapToTempFile(resultBitmap);
            Intent intent = new Intent(this, FaceSwapActivity.class);
            intent.putExtra("resultImagePath", tempFilePath);
            startActivity(intent);
        } else {
            Toast.makeText(this, "请先完成试衣", Toast.LENGTH_SHORT).show();
        }
    }

    private String saveBitmapToTempFile(Bitmap bitmap) {
        File tempFile = null;
        try {
            tempFile = File.createTempFile("result_image", ".png", getCacheDir());
            FileOutputStream fos = new FileOutputStream(tempFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return tempFile != null ? tempFile.getAbsolutePath() : null;
    }
}