/*
 * Copyright (C) 2012 University of Washington
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.draw;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Interpolator;
import android.view.animation.ScaleAnimation;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.xw.net.client.ClientHelper;
import com.xw.net.interfaces.OnSingleClickListener;
import com.xw.net.utils.LogUtils;
import com.xw.net.client.websocket.parse.ParseRobotResponse;

import org.odk.collect.androidshared.bitmap.ImageFileUtils;
import org.odk.collect.androidshared.ui.DialogFragmentUtils;
import org.odk.collect.androidshared.ui.FragmentFactoryBuilder;
import org.odk.collect.androidshared.ui.ToastUtils;
import org.odk.collect.async.Scheduler;
import org.odk.collect.settings.SettingsProvider;
import org.odk.collect.settings.keys.MetaKeys;
import org.odk.collect.strings.localization.LocalizedActivity;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import dz.solc.viewtool.view.textview.FilletView;
import timber.log.Timber;

/**
 * Modified from the FingerPaint example found in The Android Open Source
 * Project.
 *
 * @author BehrAtherton@gmail.com
 */
public class DrawActivity extends LocalizedActivity  {
    public static final String OPTION = "option";
    public static final String IMAGE_URL = "image";
    public static final String OPTION_TYPE = "OPTION_TYPE";
    public static final String OPTION_SIGNATURE = "signature";
    public static final String OPTION_ANNOTATE = "annotate";
    public static final String OPTION_DRAW = "draw";
    public static final String REF_IMAGE = "refImage";
    public static final String SCREEN_ORIENTATION = "screenOrientation";
    public static final String EXTRA_OUTPUT = android.provider.MediaStore.EXTRA_OUTPUT;
    // restore

    // incoming options...
    private String loadOption;
    private File refImage;
    private File output;
    private File savepointImage;

    private DrawView drawView;
    private TextView tvVoiceText;
    private String alertTitleString;
    private AlertDialog alertDialog;
    private DrawViewModel drawViewModel;

    @Inject
    Scheduler scheduler;
    @Inject
    SettingsProvider settingsProvider;



    //1 绘图，2 写句子
    private int drawType = 1;




    private final OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            createQuitDrawDialog();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // 设置为横屏（顺时针90度）
//        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        // 设置为反向横屏（逆时针90度）
//        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);

//        mousePointerManager = new MousePointerManager(this);

        ((DrawDependencyComponentProvider) getApplicationContext()).getDrawDependencyComponent().inject(this);

        PenColorPickerViewModel viewModel = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @NonNull
            @Override
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass, @NonNull CreationExtras extras) {
                return (T) new PenColorPickerViewModel(settingsProvider.getMetaSettings(), MetaKeys.LAST_USED_PEN_COLOR);
            }
        }).get(PenColorPickerViewModel.class);

        this.getSupportFragmentManager().setFragmentFactory(new FragmentFactoryBuilder()
                .forClass(PenColorPickerDialog.class, () -> new PenColorPickerDialog(viewModel))
                .build());

        super.onCreate(savedInstanceState);
        setContentView(R.layout.draw_layout);

        String drawTypeString =  getIntent().getStringExtra(OPTION_TYPE);
        String imageUrl =  getIntent().getStringExtra(IMAGE_URL);
        if(TextUtils.equals(drawTypeString,"write_sentences")){
            drawType =2;
        }else{
            drawType = 1;
                ImageView ivSimpleImage = findViewById(R.id.ivSimpleImage);
                if(!TextUtils.isEmpty(imageUrl) && imageUrl.startsWith("jr://images")){
                    imageUrl = imageUrl.replace("jr://images/", "");
                    ivSimpleImage.setVisibility(VISIBLE);
                    Glide.with(this)
                            .load(imageUrl)
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .skipMemoryCache(true)
                            .centerInside()
                            .into(ivSimpleImage);
            }
        }


        TextView tvTip = findViewById(R.id.tvTip);
//        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        drawView = findViewById(R.id.drawView);
        drawView.setWatchDrawListener(new WatchDrawListener() {
            @Override
            public void onClear() {
                tvTip.setVisibility(VISIBLE);
            }
            @Override
            public void onDraw() {
                tvTip.setVisibility(GONE);
            }
        });
        tvVoiceText = findViewById(org.odk.collect.base.R.id.tvVoiceMessage);

        tvVoiceText.setText( drawType==1?"请绘制图案信息":"请造句");

        ImageView mIvBack = findViewById(org.odk.collect.base.R.id.mIvBack);
        TextView mTitle = findViewById(org.odk.collect.base.R.id.mTitle);

        mTitle.setText( drawType==1?"图形绘制":"请写句子");
        mTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);

        mIvBack.setVisibility(VISIBLE);
        mIvBack.setOnClickListener(new OnSingleClickListener() {
            @Override
            public void onSingleClick(View v) {
                if(drawView.isEmpty()){
                    ToastUtils.showLongToast( drawType==1?"请完成图案绘制":"请造一个句子");
                    ClientHelper.INSTANCE.sendTTSOver("", drawType==1?"请完成图案绘制":"请造一个句子");
                    tvVoiceText.setText(drawType==1?"请完成图案绘制":"请造一个句子");
                    return;
                }
                close(v);
            }
        });


        final FilletView cvClear = findViewById(R.id.cvClear);
        final FilletView fvSure = findViewById(R.id.fVSure);

        if(drawType==2){
            cvClear.setText("重写");
            tvTip.setText("请造句子");
        }
        cvClear.setOnClickListener(this::clear);
        fvSure.setOnClickListener(this::close);
//        fabSetColor.setOnClickListener(this::setColor);

        Bundle extras = getIntent().getExtras();
        String imagePath = drawView.getImagePath();
        LogUtils.INSTANCE.d("----------imagePath:"+imagePath);
        if (extras.getInt(SCREEN_ORIENTATION) == 1) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
//            -setData-binaryName: 1754546339344.jpg
//            setData-binaryName:  1754546445334.jpg
        }
        loadOption = extras.getString(OPTION);
        if (loadOption == null) {
            loadOption = OPTION_DRAW;
        }
        // refImage can also be present if resuming a drawing
        Uri uri = (Uri) extras.get(REF_IMAGE);
        if (uri != null) {
            refImage = new File(uri.getPath());
        }
        savepointImage = new File(imagePath);
        savepointImage.delete();
        if (refImage != null && refImage.exists()) {
            ImageFileUtils.copyImageAndApplyExifRotation(refImage, savepointImage);
        }
        uri = (Uri) extras.get(EXTRA_OUTPUT);
        if (uri != null) {
            output = new File(uri.getPath());
        } else {
            output = new File(imagePath);
        }
        if (OPTION_SIGNATURE.equals(loadOption)) {
            alertTitleString = getString(org.odk.collect.strings.R.string.quit_application, getString(org.odk.collect.strings.R.string.sign_button));
        } else if (OPTION_ANNOTATE.equals(loadOption)) {
            alertTitleString = getString(org.odk.collect.strings.R.string.quit_application,
                    getString(org.odk.collect.strings.R.string.markup_image));
        } else {
            alertTitleString = getString(org.odk.collect.strings.R.string.quit_application,
                    getString(org.odk.collect.strings.R.string.draw_image));
        }

        drawView.setupView(OPTION_SIGNATURE.equals(loadOption));

        viewModel.getPenColor().observe(this, penColor -> {
            if (OPTION_SIGNATURE.equals(loadOption) && viewModel.isDefaultValue()) {
                drawView.setColor(Color.BLACK);
            } else {
                drawView.setColor(penColor);
            }
        });

        drawViewModel = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @NonNull
            @Override
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass, @NonNull CreationExtras extras) {
                return (T) new DrawViewModel(output, scheduler);
            }
        }).get(DrawViewModel.class);


        drawViewModel.getSaveResult().observe(this, (success) -> {
            if (success) {
                setResult(Activity.RESULT_OK);
            } else {
                setResult(Activity.RESULT_CANCELED);
            }
            finish();
        });

        getOnBackPressedDispatcher().addCallback(onBackPressedCallback);

    }

    private void reset() {
        savepointImage.delete();
        if (!OPTION_SIGNATURE.equals(loadOption) && refImage != null
                && refImage.exists()) {
            ImageFileUtils.copyImageAndApplyExifRotation(refImage, savepointImage);
        }
        drawView.reset();
        drawView.invalidate();
    }

    private void cancelAndClose() {
        setResult(Activity.RESULT_CANCELED);
        this.finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (event.isAltPressed()) {
                    createQuitDrawDialog();
                    return true;
                }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Create a dialog with options to save and exit, save, or quit without
     * saving
     */
    private void createQuitDrawDialog() {
        int dividerHeight = getResources().getDimensionPixelSize(org.odk.collect.androidshared.R.dimen.margin_extra_small);
        ListView actionListView = new ListView(this);
        actionListView.setPadding(0, dividerHeight, 0, 0);
        actionListView.setDivider(new ColorDrawable(Color.TRANSPARENT));
        actionListView.setDividerHeight(dividerHeight);

        List<IconMenuListAdapter.IconMenuItem> items;
        items = Arrays.asList(new IconMenuListAdapter.IconMenuItem(org.odk.collect.icons.R.drawable.ic_save, org.odk.collect.strings.R.string.keep_changes),
                new IconMenuListAdapter.IconMenuItem(org.odk.collect.icons.R.drawable.ic_delete, org.odk.collect.strings.R.string.discard_changes));

        final IconMenuListAdapter adapter = new IconMenuListAdapter(this, items);
        actionListView.setAdapter(adapter);
        actionListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                IconMenuListAdapter.IconMenuItem item = (IconMenuListAdapter.IconMenuItem) adapter.getItem(position);
                if (item.getTextResId() == org.odk.collect.strings.R.string.keep_changes) {
                    drawViewModel.save(drawView);
                } else {
                    cancelAndClose();
                }
                alertDialog.dismiss();
            }
        });
        alertDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(alertTitleString)
                .setPositiveButton(getString(org.odk.collect.strings.R.string.do_not_exit), null)
                .setView(actionListView).create();
        alertDialog.show();
    }

    private void clear(View view) {
        if (view.getVisibility() == VISIBLE) {
            reset();
        }
    }

    private void close(View view) {
        if (view.getVisibility() == VISIBLE) {
            drawViewModel.save(drawView);
        }
    }

    private void setColor(View view) {
        if (view.getVisibility() == VISIBLE) {
            DialogFragmentUtils.showIfNotShowing(PenColorPickerDialog.class, getSupportFragmentManager());
        }
    }

    private static void scaleInAnimation(final View view, int startOffset, int duration,
                                         Interpolator interpolator, final boolean isInvisible) {
        ScaleAnimation scaleInAnimation = new ScaleAnimation(0f, 1f, 0f, 1f, Animation.RELATIVE_TO_SELF,
                0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        scaleInAnimation.setInterpolator(interpolator);
        scaleInAnimation.setDuration(duration);
        scaleInAnimation.setStartOffset(startOffset);
        scaleInAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                if (isInvisible) {
                    view.setVisibility(VISIBLE);
                }
            }

            @Override
            public void onAnimationEnd(Animation animation) {
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        view.startAnimation(scaleInAnimation);
    }
//
//    @Override
//    protected void onStart() {
//        super.onStart();
//        getWindow().getDecorView().post(() -> {
//            mousePointerManager.showPointer(this, null, null);
//        });
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        mousePointerManager.hidePointer();
//    }


    @Override
    public void onMessageReceived(String topic,@NonNull String message) {
        Timber.v("DrawActivity Received message: %s", message);
        ParseRobotResponse.INSTANCE.parse(message, (s, response) -> {
            runOnUiThread(() -> tvVoiceText.setText(s));
            return null;
        });
    }

}
