/*
 *  UVCCamera
 *  library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *  All files in the folder are under this Apache License, Version 2.0.
 *  Files in the libjpeg-turbo, libusb, libuvc, rapidjson folder
 *  may have a different license, see the respective files.
 */

package com.serenegiant.usbcameratest;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.serenegiant.common.BaseActivity;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.DeviceFilter;
import com.serenegiant.usb.IButtonCallback;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.IOpenCallback;
import com.serenegiant.usb.IStatusCallback;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener;
import com.serenegiant.usb.USBMonitor.UsbControlBlock;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.widget.PreviewView;
import com.serenegiant.widget.SimpleUVCCameraTextureView;
import com.serenegiant.widget.TexturePreviewView;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends BaseActivity implements OnClickListener {

    private final Object mSync = new Object();
    // for accessing USB and USB camera
    private USBMonitor mUSBMonitor;
    private UVCCamera mUVCCamera;
    private SimpleUVCCameraTextureView mUVCCameraView;
    // for open&start / stop&close camera preview
    private ImageButton mCameraButton;
    private Surface mPreviewSurface;

    private int MY_W = 640;
    private int MY_H = 480;
    private int MY_FORMAT = UVCCamera.FRAME_FORMAT_MJPEG;
    private List<UsbDevice> list_devices = new ArrayList<>();
    private ImageView imageview;
    private PreviewView previewView;
    private View container;
    private View circle_button;
    private ValueAnimator objectAnimator;


    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mCameraButton = (ImageButton) findViewById(R.id.camera_button);
        mCameraButton.setOnClickListener(mOnClickListener);
        imageview = findViewById(R.id.imageview);
        container = findViewById(R.id.container);
        circle_button = findViewById(R.id.circle_button);
        circle_button.setOnClickListener(this);
        previewView = (PreviewView) findViewById(R.id.preview_view);
//        previewView.setScaleType(PreviewView.ScaleType.FIT_WIDTH);
        previewView.getTextureView().setScaleX(-1);
//        mUVCCameraView = (SimpleUVCCameraTextureView) findViewById(R.id.UVCCameraTextureView1);
//        mUVCCameraView.setAspectRatio(UVCCamera.DEFAULT_PREVIEW_WIDTH / (float) UVCCamera.DEFAULT_PREVIEW_HEIGHT);

        boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        if (isPortrait) {
            previewView.setScaleType(PreviewView.ScaleType.FIT_HEIGHT);
            // 相机坚屏模式
        } else {
            previewView.setScaleType(PreviewView.ScaleType.FIT_WIDTH);
            // 相机横屏模式
        }

        mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);

        ImageButton ledBtn = (ImageButton) findViewById(R.id.led_button);
        ledBtn.setOnClickListener(v -> {
            synchronized (mSync) {
                if (mUVCCamera != null) {
//                    byte[] pdat = new byte[1];
//                    mUVCCamera.UVCExtRead(0xfea6, pdat, 1);
//                    if ((pdat[0] & 0x80) == 0) {//打开-灯
//                        pdat[0] |= 0x80;
//                    } else {//关闭-灯
//                        pdat[0] &= (~0x80);
//                    }
//                    mUVCCamera.UVCExtWrite(0xfea6, pdat, 1);
                    mUVCCamera.operate_light(0xfea6);
                }
            }
        });

        getSerialNumber();
    }


    /**
     * getSerialNumber
     *
     * @return result is same to getSerialNumber1()
     */

    public static String getSerialNumber() {

        String serial = null;

        try {

            Class<?> c = Class.forName("android.os.SystemProperties");

            Method get = c.getMethod("get", String.class);

            serial = (String) get.invoke(c, "ro.serialno");

        } catch (Exception e) {
            e.printStackTrace();
        }

        return serial;

    }

    @Override
    protected void onStart() {
        super.onStart();
        mUSBMonitor.register();
        synchronized (mSync) {
            if (mUVCCamera != null) {
                mUVCCamera.startPreview();
            }
        }
    }

    @Override
    protected void onStop() {
        synchronized (mSync) {
            if (mUVCCamera != null) {
                mUVCCamera.stopPreview();
            }
            if (mUSBMonitor != null) {
                mUSBMonitor.unregister();
            }
        }
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateDevices();
        request_permission();
    }

    /**
     * 请求预览权限
     */
    private void request_permission() {
        UsbDevice usbDevice = list_devices.get(0);
        new Handler().postDelayed(() -> {
            if (usbDevice instanceof UsbDevice) {
                mUSBMonitor.requestPermission(usbDevice);
            }
        }, 300);
    }


    /**
     * 搜索UVC摄像头列表
     */
    public void updateDevices() {
//		mUSBMonitor.dumpDevices();
        final List<DeviceFilter> filter = DeviceFilter.getDeviceFilters(MainActivity.this, com.serenegiant.uvccamera.R.xml.device_filter);
        list_devices = mUSBMonitor.getDeviceList(filter.get(0));

    }

    @Override
    protected void onDestroy() {
        synchronized (mSync) {
            releaseCamera();
            if (mToast != null) {
                mToast.cancel();
                mToast = null;
            }
            if (mUSBMonitor != null) {
                mUSBMonitor.destroy();
                mUSBMonitor = null;
            }
        }
//        mUVCCameraView = null;
        mCameraButton = null;
        super.onDestroy();
    }

    private final OnClickListener mOnClickListener = new OnClickListener() {
        @Override
        public void onClick(final View view) {
            synchronized (mSync) {
                if (mUVCCamera == null) {
                    CameraDialog.showDialog(MainActivity.this);
                } else {
                    releaseCamera();//关闭摄像头
                }
            }
        }
    };

    private Toast mToast;

    private final OnDeviceConnectListener mOnDeviceConnectListener = new OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {
            Toast.makeText(MainActivity.this, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show();
//
        }

        @Override
        public void onConnect(final UsbDevice device, final UsbControlBlock ctrlBlock, final boolean createNew) {
            releaseCamera();
            queueEvent(() -> {
                final UVCCamera camera = new UVCCamera();
                camera.open(ctrlBlock, new MyOpenBack());
                camera.setStatusCallback((statusClass, event, selector, statusAttribute, data) -> runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        final Toast toast = Toast.makeText(MainActivity.this, "onStatus(statusClass=" + statusClass
                                + "; " +
                                "event=" + event + "; " +
                                "selector=" + selector + "; " +
                                "statusAttribute=" + statusAttribute + "; " +
                                "data=...)", Toast.LENGTH_SHORT);
                        synchronized (mSync) {
                            if (mToast != null) {
                                mToast.cancel();
                            }
                            toast.show();
                            mToast = toast;
                        }
                    }
                }));

                camera.setButtonCallback((button, state) -> runOnUiThread(() -> {
                    final Toast toast = Toast.makeText(MainActivity.this, "onButton(button=" + button + "; " +
                            "state=" + state + ")", Toast.LENGTH_SHORT);
                    synchronized (mSync) {
                        if (mToast != null) {
                            mToast.cancel();
                        }
                        mToast = toast;
                        toast.show();
                    }
                }));
//					camera.setPreviewTexture(camera.getSurfaceTexture());
                if (mPreviewSurface != null) {
                    mPreviewSurface.release();
                    mPreviewSurface = null;
                }
                try {
                    camera.setPreviewSize(MY_W, MY_H, MY_FORMAT);
                } catch (final IllegalArgumentException e) {
                    // fallback to YUV mode
                    try {
                        camera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.DEFAULT_PREVIEW_MODE);
                    } catch (final IllegalArgumentException e1) {
                        camera.destroy();
                        return;
                    }
                }

                final SurfaceTexture st = previewView.getTextureView().getSurfaceTexture();
                previewView.setPreviewSize(MY_W, MY_H);
                if (st != null) {
                    mPreviewSurface = new Surface(st);
                    camera.setPreviewDisplay(mPreviewSurface);
                    camera.setFrameCallback(mIFrameCallback, UVCCamera.PIXEL_FORMAT_NV21);
                    camera.startPreview();
                }

                synchronized (mSync) {
                    mUVCCamera = camera;
                }

//                previewView.animate().rotation(90);
//                container.setRotation(90);
            }, 0);
        }

        @Override
        public void onDisconnect(final UsbDevice device, final UsbControlBlock ctrlBlock) {
            // XXX you should check whether the coming device equal to camera device that currently using
            releaseCamera();
        }

        @Override
        public void onDettach(final UsbDevice device) {
            Toast.makeText(MainActivity.this, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCancel(final UsbDevice device) {

        }
    };

    /**
     * 打开摄像头回调处理
     */
    private class MyOpenBack implements IOpenCallback {

        @Override
        public void onOpen(int state) {
            switch (state) {
                case 0:
                    break;
                default:
//                    updateDevices();
//                    request_permission();
//                    Log.e("robin debug","UVC摄像头重启");
                    break;
            }
        }
    };

    private synchronized void releaseCamera() {
        synchronized (mSync) {
            if (mUVCCamera != null) {
                try {
                    mUVCCamera.setStatusCallback(null);
                    mUVCCamera.setButtonCallback(null);
                    mUVCCamera.close();
                    mUVCCamera.destroy();
                } catch (final Exception e) {
                    //
                }
                mUVCCamera = null;
            }

            if (mPreviewSurface != null) {
                mPreviewSurface.release();
                mPreviewSurface = null;
            }
        }
    }

    /**
     * to access from CameraDialog
     *
     * @return
     */
//    @Override
//    public USBMonitor getUSBMonitor() {
//        return mUSBMonitor;
//    }
//
//    @Override
//    public void onDialogResult(boolean canceled) {
//        if (canceled) {
//            runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//                    // FIXME
//                }
//            }, 0);
//        }
//    }

    // if you need frame data as byte array on Java side, you can use this callback method with UVCCamera#setFrameCallback
    // if you need to create Bitmap in IFrameCallback, please refer following snippet.
    private final IFrameCallback mIFrameCallback = frame -> {
        byte[] data = new byte[frame.remaining()];
        frame.get(data);
    };

    boolean istrue;

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.circle_button://屏幕旋转0度或者90度
//                container.setRotation(!istrue == true ? 90: 0);
                preview_rotation();
                if (mUVCCamera != null) {
                    mUVCCamera.stopPreview();
                }
                break;
        }
    }

    /**
     * 屏幕旋转0/90度
     */
    private void preview_rotation() {
        if (istrue) {
            istrue = false;
            objectAnimator = ObjectAnimator.ofFloat(previewView, "rotation", 0f);
            previewView.setScaleType(PreviewView.ScaleType.FIT_HEIGHT);
//                    previewView.setPreviewSize(MY_W, MY_H);

        } else {
            istrue = true;
            objectAnimator = ObjectAnimator.ofFloat(previewView, "rotation", 90f);
            previewView.setScaleType(PreviewView.ScaleType.FIT_WIDTH);
//                    previewView.setPreviewSize(MY_H, MY_W);
        }
        objectAnimator.setDuration(100);
        objectAnimator.start();
    }
}
