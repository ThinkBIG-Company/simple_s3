package com.abhimortal6.simple_s3;

import static com.amazonaws.event.ProgressEvent.COMPLETED_EVENT_CODE;
import static com.amazonaws.event.ProgressEvent.FAILED_EVENT_CODE;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.CognitoCredentialsProvider;
import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferNetworkLossHandler;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;

import java.io.File;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

/**
 * AwsS3Plugin
 */
public class SimpleS3Plugin implements FlutterPlugin, MethodCallHandler, EventChannel.StreamHandler {

    private static final String TAG = "S3Native";
    private static final String CHANNEL = "simple_s3";
    private static final String EVENTS = "simple_s3_events";
    private Result parentResult;
    private final ClientConfiguration clientConfiguration;
    private TransferUtility transferUtility;
    private Context mContext;
    private EventChannel eventChannel;
    private MethodChannel methodChannel;
    private EventChannel.EventSink events;

    public SimpleS3Plugin() {
        clientConfiguration = new ClientConfiguration();
    }

    private static void setup(SimpleS3Plugin plugin, BinaryMessenger binaryMessenger) {
        plugin.methodChannel = new MethodChannel(binaryMessenger, CHANNEL);
        plugin.methodChannel.setMethodCallHandler(plugin);

        plugin.eventChannel = new EventChannel(binaryMessenger, EVENTS);
        plugin.eventChannel.setStreamHandler(plugin);
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        this.mContext = flutterPluginBinding.getApplicationContext();
        setup(this, flutterPluginBinding.getBinaryMessenger());
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        if (call.method.equals("upload")) {
            upload(call, result);
        } else if (call.method.equals("delete")) {
            delete(call, result);
        } else {
            result.notImplemented();
        }
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        mContext = null;
        methodChannel.setMethodCallHandler(null);
        methodChannel = null;
        eventChannel.setStreamHandler(null);
        eventChannel = null;

        Log.d(TAG, "onDetachedFromEngine");
    }

    private void upload(@NonNull MethodCall call, @NonNull Result result) {
        parentResult = result;

        String accessKey = call.argument("accessKey");
        String secretKey = call.argument("secretKey");

        String bucketName = call.argument("bucketName");
        String filePath = call.argument("filePath");
        String s3FolderPath = call.argument("s3FolderPath");
        String fileName = call.argument("fileName");
        String poolID = call.argument("poolID");
        String region = call.argument("region");
        String subRegion = call.argument("subRegion");
        String contentType = call.argument("contentType");
        int accessControl = call.argument("accessControl");

        System.out.println(call.arguments);

        try {
            Regions parsedRegion = Regions.fromName(region);
            Regions parsedSubRegion = subRegion.length() != 0 ? Regions.fromName(subRegion) : parsedRegion;

            TransferNetworkLossHandler.getInstance(mContext.getApplicationContext());

            if ((accessKey != null && !accessKey.isEmpty()) && (secretKey != null && !secretKey.isEmpty())) {
                BasicAWSCredentials credentialsProvider = new BasicAWSCredentials(accessKey, secretKey);
                AmazonS3 amazonS3Client = new AmazonS3Client(credentialsProvider, Region.getRegion(Regions.EU_WEST_1));
                amazonS3Client.setRegion(Region.getRegion(parsedSubRegion));

                transferUtility = TransferUtility.builder().context(mContext).awsConfiguration(AWSMobileClient.getInstance().getConfiguration()).s3Client(amazonS3Client).build();
            } else {
                CognitoCredentialsProvider credentialsProvider = new CognitoCredentialsProvider(poolID, parsedRegion, clientConfiguration);

                AmazonS3Client amazonS3Client = new AmazonS3Client(credentialsProvider, Region.getRegion(Regions.EU_WEST_1));
                amazonS3Client.setRegion(com.amazonaws.regions.Region.getRegion(parsedSubRegion));

                transferUtility = TransferUtility.builder().context(mContext).awsConfiguration(AWSMobileClient.getInstance().getConfiguration()).s3Client(amazonS3Client).build();
            }

            /*CognitoCredentialsProvider credentialsProvider = new CognitoCredentialsProvider(poolID, parsedRegion, clientConfiguration);
            TransferNetworkLossHandler.getInstance(mContext.getApplicationContext());

            AmazonS3Client amazonS3Client = new AmazonS3Client(credentialsProvider);
            amazonS3Client.setRegion(com.amazonaws.regions.Region.getRegion(parsedSubRegion));*/
        } catch (Exception e) {
            parentResult.success(false);
            Log.e(TAG, "onMethodCall: exception: " + e.getMessage());
        }

        String awsPath = fileName;
        if (s3FolderPath != null && !s3FolderPath.equals("")) {
            awsPath = s3FolderPath + "/" + fileName;
        }
        ObjectMetadata objectMetadata = new ObjectMetadata();
        System.out.println(fileName.substring(fileName.lastIndexOf(".") + 1));
        objectMetadata.setContentType(contentType);

        CannedAccessControlList acl;
        switch (accessControl) {
            case 1:
                acl = CannedAccessControlList.Private;
                break;
            case 2:
                acl = CannedAccessControlList.PublicRead;
                break;
            case 3:
                acl = CannedAccessControlList.PublicReadWrite;
                break;
            case 4:
                acl = CannedAccessControlList.AuthenticatedRead;
                break;
            case 5:
                acl = CannedAccessControlList.AwsExecRead;
                break;
            case 6:
                acl = CannedAccessControlList.BucketOwnerRead;
                break;
            case 7:
                acl = CannedAccessControlList.BucketOwnerFullControl;
                break;
            default:
                acl = CannedAccessControlList.PublicRead;
        }

        TransferObserver transferObserver1 = transferUtility.upload(bucketName, awsPath, new File(filePath), objectMetadata, acl);

        transferObserver1.setTransferListener(new Transfer());
    }

    private void delete(@NonNull MethodCall call, @NonNull Result result) {
        parentResult = result;

        String accessKey = call.argument("accessKey");
        String secretKey = call.argument("secretKey");

        String bucketName = call.argument("bucketName");
        String filePath = call.argument("filePath");
        String poolID = call.argument("poolID");
        String region = call.argument("region");
        String subRegion = call.argument("subRegion");

        try {
            Regions parsedRegion = Regions.fromName(region);
            Regions parsedSubRegion = subRegion.length() != 0 ? Regions.fromName(subRegion) : parsedRegion;

            TransferNetworkLossHandler.getInstance(mContext.getApplicationContext());

            AmazonS3 amazonS3Client;
            if ((accessKey != null && !accessKey.isEmpty()) && (secretKey != null && !secretKey.isEmpty())) {
                BasicAWSCredentials credentialsProvider = new BasicAWSCredentials(accessKey, secretKey);

                amazonS3Client = new AmazonS3Client(credentialsProvider, Region.getRegion(Regions.EU_WEST_1));
            } else {
                CognitoCredentialsProvider credentialsProvider = new CognitoCredentialsProvider(poolID, parsedRegion, clientConfiguration);

                amazonS3Client = new AmazonS3Client(credentialsProvider, Region.getRegion(Regions.EU_WEST_1));
            }
            amazonS3Client.setRegion(Region.getRegion(parsedSubRegion));

            final DeleteObjectRequest deleteObjectRequest = new DeleteObjectRequest(bucketName, filePath).withGeneralProgressListener(new Progress());

            Thread thread = new Thread() {
                @Override
                public void run() {
                    amazonS3Client.deleteObject(deleteObjectRequest);
                }
            };

            thread.start();
            parentResult.success(true);
        } catch (Exception e) {
            parentResult.success(false);

            Log.e(TAG, "onMethodCall: exception: " + e.getMessage());
        }
    }

    @Override
    public void onListen(Object arguments, EventChannel.EventSink events) {
        this.events = events;
    }

    @Override
    public void onCancel(Object arguments) {
        invalidateEventSink();
    }

    private void invalidateEventSink() {
        if (events != null) {
            events.endOfStream();
            events = null;
        }
    }

    class Progress implements ProgressListener {
        @Override
        public void progressChanged(ProgressEvent progressEvent) {
            switch (progressEvent.getEventCode()) {

                case COMPLETED_EVENT_CODE:
                    parentResult.success(true);
                    break;
                case FAILED_EVENT_CODE:
                    parentResult.success(false);
                    break;
                default:
                    parentResult.success(false);
                    break;
            }
        }
    }

    class Transfer implements TransferListener {
        private static final String TAG = "Transfer";

        @Override
        public void onStateChanged(int id, TransferState state) {
            switch (state) {
                case COMPLETED:
                    Log.d(TAG, "onStateChanged: \"COMPLETED, ");
                    parentResult.success(true);
                    break;
                case WAITING:
                    Log.d(TAG, "onStateChanged: \"WAITING, ");
                    break;
                case FAILED:
                    invalidateEventSink();
                    Log.d(TAG, "onStateChanged: \"FAILED, ");
                    parentResult.success(false);
                    break;
                default:
                    Log.d(TAG, "onStateChanged: \"SOMETHING ELSE, ");
                    break;
            }
        }

        @Override
        public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
            float percentDoNef = ((float) bytesCurrent / (float) bytesTotal) * 100;
            int percentDone = (int) percentDoNef;
            Log.d(TAG, "ID:" + id + " bytesCurrent: " + bytesCurrent + " bytesTotal: " + bytesTotal + " " + percentDone + "%");

            if (events != null) {
                events.success(percentDone);
            }
        }

        @Override
        public void onError(int id, Exception ex) {
            Log.e(TAG, "onError: " + ex);
            invalidateEventSink();
        }
    }
}
