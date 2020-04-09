package com.reactlibrary;

import android.os.Environment;
import android.util.Log;

import com.alibaba.sdk.android.oss.ClientException;
import com.alibaba.sdk.android.oss.OSS;
import com.alibaba.sdk.android.oss.ServiceException;
import com.alibaba.sdk.android.oss.callback.OSSCompletedCallback;
import com.alibaba.sdk.android.oss.internal.OSSAsyncTask;
import com.alibaba.sdk.android.oss.model.GetObjectRequest;
import com.alibaba.sdk.android.oss.model.GetObjectResult;
import com.alibaba.sdk.android.oss.model.Range;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.concurrent.atomic.AtomicBoolean;

public class AliyunDownloadManager {
    private OSS mOSS;
    private Boolean reset = false;
    private AtomicBoolean abort = new AtomicBoolean(false);

    public void setAbort() {
        abort.set(true);
    }

    /**
     * AliyunDownloadManager
     * @param oss
     */
    public AliyunDownloadManager(OSS oss) {
        mOSS = oss;
    }

    public void asyncDownload(final ReactContext context,String bucketName, String ossFile, String updateDate, ReadableMap options, final Promise promise) {
        final String localFile = ossFile;
        int start = 0;
        int end = 0;
        GetObjectRequest get = new GetObjectRequest(bucketName, ossFile);

        // String xOssPositon = options.getString("x-oss-process");
        //process image
        // get.setxOssProcess(xOssPositon);

        try {
            start = options.getInt("start");
            end = options.getInt("end");
            if (end == 0) {
                get.setRange(new Range(start, Range.INFINITE));
            }
    
            if (end > 0) {
                get.setRange(new Range(start, start + end - 1));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            reset = options.getBoolean("reset");
            reset = start == 0 ? true : reset;
        } catch (Exception e) {
            e.printStackTrace();
        }

        OSSAsyncTask task = mOSS.asyncGetObject(get, new OSSCompletedCallback<GetObjectRequest, GetObjectResult>() {
            @Override
            public void onSuccess(GetObjectRequest request, GetObjectResult result) {

                Log.d("Content-Length", "" + result.getContentLength());

                InputStream inputStream = result.getObjectContent();
                long resultLength = result.getContentLength();
                Log.d("resultLength", "" + resultLength);

                byte[] buffer = new byte[2048];
                int len;

                FileOutputStream outputStream = null;
                String localFileURL = Environment.getExternalStorageDirectory().getAbsolutePath() +
                        "/boox/" +
                        localFile;
                Log.d("localFileURL", localFileURL);
                File cacheFile = new File(localFileURL);
                if (cacheFile.exists() && reset) {
                    cacheFile.delete();
                }
                if (!cacheFile.exists()) {
                    cacheFile.getParentFile().mkdirs();
                    try {
                        cacheFile.createNewFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                        promise.reject("DownloadFaile", e);
                    }
                }

                long readSize = cacheFile.length();
                try {
                    outputStream = new FileOutputStream(cacheFile, true);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    promise.reject("DownloadFaile", e);
                }
                if (resultLength == -1) {
                    promise.reject("DownloadFaile", "message:lengtherror");
                }

                try {
                    while ((len = inputStream.read(buffer)) != -1) {
                       // resove download data
                        if (abort.get()) {
                            cacheFile.delete();
                            promise.reject("DownloadFaile", new Exception("abort"));
                            break;
                        }
                        try {
                            outputStream.write(buffer, 0, len);
                            readSize += len;

                            String str_currentSize = Long.toString(readSize);
                            String str_totalSize = Long.toString(resultLength);
                            WritableMap onProgressValueData = Arguments.createMap();
                            onProgressValueData.putString("currentSize", str_currentSize);
                            onProgressValueData.putString("totalSize", str_totalSize);
                            context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                                    .emit("downloadProgress", onProgressValueData);

                        } catch (IOException e) {
                            e.printStackTrace();
                            promise.reject("DownloadFaile", e);
                        }
                    }
                    outputStream.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                    promise.reject("DownloadFaile", e);
                } finally {
                    if (outputStream != null) {
                        try {
                            outputStream.close();
                        } catch (IOException e) {
                            promise.reject("DownloadFaile", e);
                        }
                    }
                    if (inputStream != null) {
                        try {
                            inputStream.close();
                        } catch (IOException e) {
                            promise.reject("DownloadFaile", e);
                        }
                    }

                    promise.resolve(localFileURL);
                }
            }

            @Override
            public void onFailure(GetObjectRequest request, ClientException clientExcepion, ServiceException serviceException) {
                PromiseExceptionManager.resolvePromiseException(clientExcepion,serviceException,promise);
            }
        });
    }
}
