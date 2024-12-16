package com.paddle.demo.matting;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.support.v7.app.ActionBar;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends AppCompatPreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private ListPreference lpChoosePreInstalledModel;
    private CheckBoxPreference cbEnableCustomSettings;
    private EditTextPreference etModelPath;
    private EditTextPreference etLabelPath;
    private EditTextPreference etImagePath;
    private ListPreference lpCPUThreadNum;
    private ListPreference lpCPUPowerMode;
    private ListPreference lpInputColorFormat;

    private List<String> preInstalledModelPaths;
    private List<String> preInstalledLabelPaths;
    private List<String> preInstalledImagePaths;
    private List<String> preInstalledCPUThreadNums;
    private List<String> preInstalledCPUPowerModes;
    private List<String> preInstalledInputColorFormats;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);
        setupActionBar();
        initializePreInstalledLists();
        initializeUIComponents();
    }

    private void setupActionBar() {
        ActionBar supportActionBar = getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    private void initializePreInstalledLists() {
        preInstalledModelPaths = new ArrayList<>();
        preInstalledLabelPaths = new ArrayList<>();
        preInstalledImagePaths = new ArrayList<>();
        preInstalledCPUThreadNums = new ArrayList<>();
        preInstalledCPUPowerModes = new ArrayList<>();
        preInstalledInputColorFormats = new ArrayList<>();

        // Add deeplab_mobilenet_for_cpu
        preInstalledModelPaths.add(getString(R.string.MODEL_PATH_DEFAULT));
        preInstalledLabelPaths.add(getString(R.string.LABEL_PATH_DEFAULT));
        preInstalledImagePaths.add(getString(R.string.IMAGE_PATH_DEFAULT));
        preInstalledCPUThreadNums.add(getString(R.string.CPU_THREAD_NUM_DEFAULT));
        preInstalledCPUPowerModes.add(getString(R.string.CPU_POWER_MODE_DEFAULT));
        preInstalledInputColorFormats.add(getString(R.string.INPUT_COLOR_FORMAT_DEFAULT));
    }

    private void initializeUIComponents() {
        lpChoosePreInstalledModel = (ListPreference) findPreference(getString(R.string.CHOOSE_PRE_INSTALLED_MODEL_KEY));
        setupPreInstalledModelList();

        cbEnableCustomSettings = (CheckBoxPreference) findPreference(getString(R.string.ENABLE_CUSTOM_SETTINGS_KEY));

        etModelPath = (EditTextPreference) findPreference(getString(R.string.MODEL_PATH_KEY));
        etModelPath.setTitle("Model Path (SDCard: " + Utils.getSDCardDirectory() + ")");

        etLabelPath = (EditTextPreference) findPreference(getString(R.string.LABEL_PATH_KEY));
        etImagePath = (EditTextPreference) findPreference(getString(R.string.IMAGE_PATH_KEY));

        lpCPUThreadNum = (ListPreference) findPreference(getString(R.string.CPU_THREAD_NUM_KEY));
        lpCPUPowerMode = (ListPreference) findPreference(getString(R.string.CPU_POWER_MODE_KEY));
        lpInputColorFormat = (ListPreference) findPreference(getString(R.string.INPUT_COLOR_FORMAT_KEY));
    }

    private void setupPreInstalledModelList() {
        String[] preInstalledModelNames = new String[preInstalledModelPaths.size()];
        for (int i = 0; i < preInstalledModelPaths.size(); i++) {
            preInstalledModelNames[i] = preInstalledModelPaths.get(i).substring(preInstalledModelPaths.get(i).lastIndexOf("/") + 1);
        }
        lpChoosePreInstalledModel.setEntries(preInstalledModelNames);
        lpChoosePreInstalledModel.setEntryValues(preInstalledModelPaths.toArray(new String[0]));
    }

    private void reloadPreferenceAndUpdateUI() {
        SharedPreferences sharedPreferences = getPreferenceScreen().getSharedPreferences();
        boolean enableCustomSettings = sharedPreferences.getBoolean(getString(R.string.ENABLE_CUSTOM_SETTINGS_KEY), false);
        String modelPath = sharedPreferences.getString(getString(R.string.CHOOSE_PRE_INSTALLED_MODEL_KEY), getString(R.string.MODEL_PATH_DEFAULT));

        updatePreInstalledModel(sharedPreferences, enableCustomSettings, modelPath);
        updateCustomSettings(enableCustomSettings);
        updatePreferences(sharedPreferences);
    }

    private void updatePreInstalledModel(SharedPreferences sharedPreferences, boolean enableCustomSettings, String modelPath) {
        int modelIdx = lpChoosePreInstalledModel.findIndexOfValue(modelPath);
        if (modelIdx >= 0 && modelIdx < preInstalledModelPaths.size() && !enableCustomSettings) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(getString(R.string.MODEL_PATH_KEY), preInstalledModelPaths.get(modelIdx));
            editor.putString(getString(R.string.LABEL_PATH_KEY), preInstalledLabelPaths.get(modelIdx));
            editor.putString(getString(R.string.IMAGE_PATH_KEY), preInstalledImagePaths.get(modelIdx));
            editor.putString(getString(R.string.CPU_THREAD_NUM_KEY), preInstalledCPUThreadNums.get(modelIdx));
            editor.putString(getString(R.string.CPU_POWER_MODE_KEY), preInstalledCPUPowerModes.get(modelIdx));
            editor.putString(getString(R.string.INPUT_COLOR_FORMAT_KEY), preInstalledInputColorFormats.get(modelIdx));
            editor.apply();
        }
        lpChoosePreInstalledModel.setSummary(modelPath);
    }

    private void updateCustomSettings(boolean enableCustomSettings) {
        cbEnableCustomSettings.setChecked(enableCustomSettings);
        etModelPath.setEnabled(enableCustomSettings);
        etLabelPath.setEnabled(enableCustomSettings);
        etImagePath.setEnabled(enableCustomSettings);
        lpCPUThreadNum.setEnabled(enableCustomSettings);
        lpCPUPowerMode.setEnabled(enableCustomSettings);
        lpInputColorFormat.setEnabled(enableCustomSettings);
    }

    private void updatePreferences(SharedPreferences sharedPreferences) {
        String modelPath = sharedPreferences.getString(getString(R.string.MODEL_PATH_KEY), getString(R.string.MODEL_PATH_DEFAULT));
        String labelPath = sharedPreferences.getString(getString(R.string.LABEL_PATH_KEY), getString(R.string.LABEL_PATH_DEFAULT));
        String imagePath = sharedPreferences.getString(getString(R.string.IMAGE_PATH_KEY), getString(R.string.IMAGE_PATH_DEFAULT));
        String cpuThreadNum = sharedPreferences.getString(getString(R.string.CPU_THREAD_NUM_KEY), getString(R.string.CPU_THREAD_NUM_DEFAULT));
        String cpuPowerMode = sharedPreferences.getString(getString(R.string.CPU_POWER_MODE_KEY), getString(R.string.CPU_POWER_MODE_DEFAULT));
        String inputColorFormat = sharedPreferences.getString(getString(R.string.INPUT_COLOR_FORMAT_KEY), getString(R.string.INPUT_COLOR_FORMAT_DEFAULT));

        updatePreference(etModelPath, modelPath);
        updatePreference(etLabelPath, labelPath);
        updatePreference(etImagePath, imagePath);
        updateListPreference(lpCPUThreadNum, cpuThreadNum);
        updateListPreference(lpCPUPowerMode, cpuPowerMode);
        updateListPreference(lpInputColorFormat, inputColorFormat);
    }

    private void updatePreference(EditTextPreference preference, String value) {
        preference.setSummary(value);
        preference.setText(value);
    }

    private void updateListPreference(ListPreference preference, String value) {
        preference.setValue(value);
        preference.setSummary(value);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        reloadPreferenceAndUpdateUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        assert key != null;
        if (key.equals(getString(R.string.CHOOSE_PRE_INSTALLED_MODEL_KEY))) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(getString(R.string.ENABLE_CUSTOM_SETTINGS_KEY), false);
            editor.apply();
        }
        reloadPreferenceAndUpdateUI();
    }
}