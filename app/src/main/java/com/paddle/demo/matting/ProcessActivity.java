package com.paddle.demo.matting;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.paddle.demo.matting.config.Config;
import com.paddle.demo.matting.preprocess.Preprocess;
import com.paddle.demo.matting.visual.Visualize;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ProcessActivity extends AppCompatActivity {
    private static final String TAG = ProcessActivity.class.getSimpleName();

    private ImageView ivInputImage;
    private TextView tvInferenceTime;
    private TextView tvOutputResult;
    private ProgressDialog pbLoadModel;
    private ProgressDialog pbRunModel;

    private Handler receiver;
    private Handler sender;
    private HandlerThread worker;

    private Predictor predictor;
    private Config config;
    private Preprocess preprocess;
    private Visualize visualize;

    protected static final int REQUEST_LOAD_MODEL = 0;
    protected static final int REQUEST_RUN_MODEL = 1;
    protected static final int RESPONSE_LOAD_MODEL_SUCCESSED = 0;
    protected static final int RESPONSE_LOAD_MODEL_FAILED = 1;
    protected static final int RESPONSE_RUN_MODEL_SUCCESSED = 2;
    protected static final int RESPONSE_RUN_MODEL_FAILED = 3;

    private static final int OPEN_GALLERY_REQUEST_CODE = 0;
    private static final int TAKE_PHOTO_REQUEST_CODE = 1;

    private Uri photoUri;

    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_process);

        // Initialize views
        ivInputImage = findViewById(R.id.iv_input_image);
        tvInferenceTime = findViewById(R.id.tv_inference_time);
        tvOutputResult = findViewById(R.id.tv_output_result);
        tvOutputResult.setMovementMethod(ScrollingMovementMethod.getInstance());

        // Initialize predictor, config, and preprocess
        predictor = new Predictor();
        config = new Config();
        preprocess = new Preprocess();
        visualize = new Visualize();

        // 定义消息接收线程
        receiver = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case RESPONSE_LOAD_MODEL_SUCCESSED:
                        Log.i(TAG, "recevied load model successed");
                        pbLoadModel.dismiss();
                        onLoadModelSuccessed();
                        break;
                    case RESPONSE_LOAD_MODEL_FAILED:
                        Log.i(TAG, "recevied load model failed");
                        pbLoadModel.dismiss();
                        Toast.makeText(ProcessActivity.this, "Load model failed!", Toast.LENGTH_SHORT).show();
                        onLoadModelFailed();
                        break;
                    case RESPONSE_RUN_MODEL_SUCCESSED:
                        pbRunModel.dismiss();
                        onRunModelSuccessed();
                        break;
                    case RESPONSE_RUN_MODEL_FAILED:
                        pbRunModel.dismiss();
                        Toast.makeText(ProcessActivity.this, "Run model failed!", Toast.LENGTH_SHORT).show();
                        onRunModelFailed();
                        break;
                    default:
                        break;
                }
            }
        };

        // 定义工作线程
        worker = new HandlerThread("Predictor Worker");
        worker.start();

        // 定义发送消息线程
        sender = new Handler(worker.getLooper()) {
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case REQUEST_LOAD_MODEL:
                        // load model and reload test image
                        if (onLoadModel()) {
                            Log.i(TAG, "load model successed");
                            receiver.sendEmptyMessage(RESPONSE_LOAD_MODEL_SUCCESSED);
                        } else {
                            Log.i(TAG, "load model failed");
                            receiver.sendEmptyMessage(RESPONSE_LOAD_MODEL_FAILED);
                        }
                        break;
                    case REQUEST_RUN_MODEL:
                        // run model if model is loaded
                        if (onRunModel())
                            receiver.sendEmptyMessage(RESPONSE_RUN_MODEL_SUCCESSED);
                        else
                            receiver.sendEmptyMessage(RESPONSE_RUN_MODEL_FAILED);
                        break;
                    default:
                        break;
                }
            }
        };


        // Load the model
        // 获取传递过来的图像 Uri
        updateSettingsAndReloadIfNeeded();

        Button nextBtn = findViewById(R.id.next_btn);
        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTryonActivity();
            }
        });
    }

    private void startTryonActivity() {
        ImageView ivInputImage = findViewById(R.id.iv_input_image);
        ivInputImage.setDrawingCacheEnabled(true);
        Bitmap bitmap = Bitmap.createBitmap(ivInputImage.getDrawingCache());
        ivInputImage.setDrawingCacheEnabled(false);

        // 保存图像到临时文件
        File tempFile = saveBitmapToTempFile(bitmap);
        if (tempFile != null) {
            Intent intent = new Intent(this, TryonActivity.class);
            intent.putExtra("imagePath", tempFile.getAbsolutePath());
            startActivity(intent);
        } else {
            Toast.makeText(this, "无法保存图像", Toast.LENGTH_SHORT).show();
        }
    }

    private File saveBitmapToTempFile(Bitmap bitmap) {
        File tempFile = null;
        try {
            File cacheDir = getCacheDir();
            tempFile = File.createTempFile("temp_image", ".png", cacheDir);
            FileOutputStream fos = new FileOutputStream(tempFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return tempFile;
    }

    private void updateSettingsAndReloadIfNeeded() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean settingsChanged = false;
        String model_path = sharedPreferences.getString(getString(R.string.MODEL_PATH_KEY),
                getString(R.string.MODEL_PATH_DEFAULT));
        String label_path = sharedPreferences.getString(getString(R.string.LABEL_PATH_KEY),
                getString(R.string.LABEL_PATH_DEFAULT));
        String image_path = sharedPreferences.getString(getString(R.string.IMAGE_PATH_KEY),
                getString(R.string.IMAGE_PATH_DEFAULT));
        String bg_path = sharedPreferences.getString(getString(R.string.BG_PATH_KEY),
                getString(R.string.BG_PATH_DEFAULT));
        settingsChanged |= !model_path.equalsIgnoreCase(config.modelPath);
        settingsChanged |= !label_path.equalsIgnoreCase(config.labelPath);
        settingsChanged |= !image_path.equalsIgnoreCase(config.imagePath);
        settingsChanged |= !bg_path.equalsIgnoreCase(config.bgPath);
        int cpu_thread_num = Integer.parseInt(sharedPreferences.getString(getString(R.string.CPU_THREAD_NUM_KEY),
                getString(R.string.CPU_THREAD_NUM_DEFAULT)));
        settingsChanged |= cpu_thread_num != config.cpuThreadNum;
        String cpu_power_mode =
                sharedPreferences.getString(getString(R.string.CPU_POWER_MODE_KEY),
                        getString(R.string.CPU_POWER_MODE_DEFAULT));
        settingsChanged |= !cpu_power_mode.equalsIgnoreCase(config.cpuPowerMode);
        String input_color_format =
                sharedPreferences.getString(getString(R.string.INPUT_COLOR_FORMAT_KEY),
                        getString(R.string.INPUT_COLOR_FORMAT_DEFAULT));
        settingsChanged |= !input_color_format.equalsIgnoreCase(config.inputColorFormat);
        long[] input_shape =
                Utils.parseLongsFromString(sharedPreferences.getString(getString(R.string.INPUT_SHAPE_KEY),
                        getString(R.string.INPUT_SHAPE_DEFAULT)), ",");

        settingsChanged |= input_shape.length != config.inputShape.length;

        if (!settingsChanged) {
            for (int i = 0; i < input_shape.length; i++) {
                settingsChanged |= input_shape[i] != config.inputShape[i];
            }
        }

        if (settingsChanged) {
            config.init(model_path, label_path, image_path, bg_path, cpu_thread_num, cpu_power_mode,
                    input_color_format, input_shape);
            preprocess.init(config);

            // 如果配置发生改变则重新加载模型并预测
            loadModel();
        }
    }

    public boolean onLoadModel() {
        // Log.i(TAG, "begin to load model");
        boolean isLoaded = predictor.init(ProcessActivity.this, config);
        Log.i(TAG, "model loaded: " + isLoaded);
        return isLoaded;
    }

    public boolean onRunModel() {
        Log.i(TAG, "predictor.isLoaded(): " + predictor.isLoaded());
        return predictor.isLoaded() && predictor.runModel(preprocess, visualize);
    }

    public void onLoadModelFailed() {}

    public void onRunModelFailed() {}

    public void loadModel() {
        Log.i(TAG, "begin to load model.");
        pbLoadModel = ProgressDialog.show(this, "", "加载模型中...", false, false);
        sender.sendEmptyMessage(REQUEST_LOAD_MODEL);
    }

    public void runModel() {
        Log.i(TAG, "begin to run model.");
        pbRunModel = ProgressDialog.show(this, "", "推理中...", false, false);
        sender.sendEmptyMessage(REQUEST_RUN_MODEL);
    }

    public void onLoadModelSuccessed() {
        Intent intent = getIntent();
        if (intent.hasExtra("imageUri")) {
            String imageUriString = intent.getStringExtra("imageUri");
            Uri imageUri = Uri.parse(imageUriString);
            try {
                Bitmap image = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                // 显示图像
                ivInputImage.setImageBitmap(image);
                // 处理图像
                onImageChanged(image);
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "无法加载图片", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @SuppressLint("SetTextI18s")
    public void onRunModelSuccessed() {
        // 获取抠图结果并更新UI
        tvInferenceTime.setText("推理耗时: " + predictor.inferenceTime() + " ms");
        Bitmap outputImage = predictor.outputImage();
        if (outputImage != null)
            ivInputImage.setImageBitmap(outputImage);

        tvOutputResult.setText(predictor.outputResult());
        tvOutputResult.scrollTo(0, 0);
    }

    public void onImageChanged(Bitmap image) {
        Log.i(TAG, "on Image Changed.");
        Bitmap bg = null;
        try {
            // System.out.println("config: " + config);
            if (config.bgPath.isEmpty()) {
                Log.i(TAG, "bgPath is empty.");
                return;
            }
            Log.i(TAG, "bgPath: " + config.bgPath);

            // 加载背景图像
            if (config.bgPath.charAt(0) != '/') {
                InputStream imageStream = getAssets().open(config.bgPath);
                bg = BitmapFactory.decodeStream(imageStream);
            } else {
                if (!new File(config.bgPath).exists()) {
                    return;
                }
                bg = BitmapFactory.decodeFile(config.bgPath);
            }
        } catch (IOException e) {
            Toast.makeText(ProcessActivity.this, "加载背景图失败!", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }

        // rerun model if users pick test image from gallery or camera
        Log.i(TAG, "begin to run model when Image Changed, predictor.isLoaded(): " + predictor.isLoaded());
        // 设置预测器图像
        if (image != null && predictor.isLoaded()) {
            Log.i(TAG, "set input image and run model.");
            predictor.setInputImage(image, bg);
            runModel();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_action_options, menu);
        return true;
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
        } else if (itemId == R.id.open_gallery) {
            if (requestAllPermissions()) {
                openGallery();
            }
        } else if (itemId == R.id.take_photo) {
            if (requestAllPermissions()) {
                takePhoto();
            }
        } else if (itemId == R.id.settings) {
            if (requestAllPermissions()) {
                // make sure we have SDCard r&w permissions to load model from SDCard
                onSettingsClicked();
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case OPEN_GALLERY_REQUEST_CODE:
                    try {
                        ContentResolver resolver = getContentResolver();
                        Uri uri = data.getData();
                        Bitmap image = MediaStore.Images.Media.getBitmap(resolver, uri);
                        String[] proj = {MediaStore.Images.Media.DATA};
                        Cursor cursor = managedQuery(uri, proj, null, null, null);
                        cursor.moveToFirst();
                        onImageChanged(image);
                    } catch (IOException e) {
                        Log.e(TAG, e.toString());
                    }
                    break;
                case TAKE_PHOTO_REQUEST_CODE:
                    if (photoUri != null) {
                        try {
                            Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), photoUri);
                            onImageChanged(bitmap);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean isLoaded = predictor.isLoaded();
        menu.findItem(R.id.open_gallery).setEnabled(isLoaded);
        menu.findItem(R.id.take_photo).setEnabled(isLoaded);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "begin onResume");
        super.onResume();
        updateSettingsAndReloadIfNeeded();
    }

    @Override
    protected void onDestroy() {
        if (predictor != null) {
            predictor.releaseModel();
        }
        worker.quit();
        super.onDestroy();
    }


    //region Some Tools
    private boolean requestAllPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                    0);
            return false;
        }
        return true;
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, OPEN_GALLERY_REQUEST_CODE);
    }

    private void takePhoto() {
        Intent takePhotoIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePhotoIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
                ex.printStackTrace();
            }
            if (photoFile != null) {
                photoUri = FileProvider.getUriForFile(this,
                        getApplicationContext().getPackageName() + ".fileprovider",
                        photoFile);
                takePhotoIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                takePhotoIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivityForResult(takePhotoIntent, TAKE_PHOTO_REQUEST_CODE);
            }
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
        return image;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED || grantResults[1] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap getBitMapFromPath(String imageFilePath) {
        Display currentDisplay = getWindowManager().getDefaultDisplay();
        int dw = currentDisplay.getWidth();
        int dh = currentDisplay.getHeight();
        // Load up the image's dimensions not the image itself
        BitmapFactory.Options bmpFactoryOptions = new BitmapFactory.Options();
        bmpFactoryOptions.inJustDecodeBounds = true;
        Bitmap bmp = BitmapFactory.decodeFile(imageFilePath, bmpFactoryOptions);
        int heightRatio = (int) Math.ceil(bmpFactoryOptions.outHeight
                / (float) dh);
        int widthRatio = (int) Math.ceil(bmpFactoryOptions.outWidth
                / (float) dw);

        // If both of the ratios are greater than 1,
        // one of the sides of the image is greater than the screen
        if (heightRatio > 1 && widthRatio > 1) {
            if (heightRatio > widthRatio) {
                // Height ratio is larger, scale according to it
                bmpFactoryOptions.inSampleSize = heightRatio;
            } else {
                // Width ratio is larger, scale according to it
                bmpFactoryOptions.inSampleSize = widthRatio;
            }
        }
        // Decode it for real
        bmpFactoryOptions.inJustDecodeBounds = false;
        bmp = BitmapFactory.decodeFile(imageFilePath, bmpFactoryOptions);
        return bmp;
    }

    // 打开设置页面
    public void onSettingsClicked() {
        startActivity(new Intent(this, SettingsActivity.class));
    }
    //endregion
}