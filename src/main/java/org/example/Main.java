package org.example;

import ai.whylabs.service.invoker.auth.ApiKeyAuth;
import ai.whylabs.service.model.AsyncLogResponse;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.whylogs.core.DatasetProfile;
import com.whylogs.core.message.DatasetProfileMessage;
import com.whylogs.core.message.ModelMetricsMessage;
import com.whylogs.core.message.ModelProfileMessage;
import com.whylogs.core.message.ModelType;
import com.whylogs.core.metrics.RegressionMetrics;
import ai.whylabs.service.api.LogApi;
import ai.whylabs.service.model.LogAsyncRequest;
import ai.whylabs.service.invoker.ApiClient;
import ai.whylabs.service.invoker.Configuration;
import ai.whylabs.service.invoker.ApiException;


import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;

public class Main {

    private static void uploadToUrl(String url, DatasetProfileMessage message) {
        try{
            HttpURLConnection connection = (HttpURLConnection )new URL(url).openConnection();
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/octet-stream");
            connection.setRequestMethod("PUT");

            OutputStream out = connection.getOutputStream();
            try (out){
               message.writeDelimitedTo(out);
            }

            if (connection.getResponseCode() != 200) {
                throw new RuntimeException("Error uploading profile: ${connection.responseCode} ${connection.responseMessage}");
            }
        }catch(ProtocolException e){
            // handle
            e.printStackTrace();
        } catch(MalformedURLException e){
            // handle
            e.printStackTrace();
        }catch(IOException e){
            // handle
            e.printStackTrace();
        }
    }

    private static void uploadDatasetProfileMessage(DatasetProfileMessage message){
        ApiClient client = Configuration.getDefaultApiClient();
        client.setBasePath("https://api.whylabsapp.com");
        ApiKeyAuth auth = (ApiKeyAuth )client.getAuthentication("ApiKeyAuth");
        auth.setApiKey("...");
        LogApi api = new LogApi(client);
        LogAsyncRequest request = new LogAsyncRequest()
                .segmentTags(new ArrayList<>())
                .datasetTimestamp(Instant.now().toEpochMilli());

        try{
            AsyncLogResponse response = api.logAsync("org-JpsdM6", "model-20", request);
            String url = response.getUploadUrl();
            uploadToUrl(url, message);
        }catch(ApiException e){
            // Error getting upload url
            e.printStackTrace();
        }
    }


    public static DatasetProfileMessage prepareMetricsForUpload(DatasetProfile profile, RegressionMetrics metrics) {


        DatasetProfileMessage.Builder builder = profile.toProtobuf().build().toBuilder();
        ModelMetricsMessage.Builder metricBuilder = builder
                .getModeProfile()
                .getMetrics()
                .toBuilder()
                .setModelType(ModelType.UNKNOWN)
                .setRegressionMetrics(metrics.toProtobuf());

        ModelProfileMessage modelMessage = builder.getModeProfile()
                .toBuilder()
                .setMetrics(metricBuilder.build())
                .build();

        return builder.setModeProfile(modelMessage).build();
    }

    public static DatasetProfileMessage manualMethod() {
        // Create a dataset profile with the given dataset timestamp.
        Instant dataset_timestamp = Instant.now();
        DatasetProfile profile = new DatasetProfile("", dataset_timestamp);

        // Track some normal input/data
        profile.track(ImmutableMap.of("col1", 3, "col2", 5));
        profile.track(ImmutableMap.of("col1", 4, "col2", 5));

        // Create a RegressionMetrics with the names of your prediction and target field.
        RegressionMetrics metrics = new RegressionMetrics("prediction", "target");

        // Track your performance data as a map
        metrics.track(ImmutableMap.of("prediction", 1, "target", 1));
        metrics.track(ImmutableMap.of("prediction", 2, "target", 1));

        // Manually unpack the profile and set the model type and the metrics directly onto the proto message format.
        // This is something that there isn't a "nice" way to do atm in whylogs java v0.
        return prepareMetricsForUpload(profile, metrics);
    }

    public static DatasetProfileMessage automaticMethod() {
        DatasetProfile original = new DatasetProfile("test", Instant.now(), null, Collections.emptyMap(), Collections.emptyMap())
                .withClassificationModel("pred", "target", "score", ImmutableList.of("additionalOutput"))
                .withRegressionModel("prediction", "target",ImmutableList.of("additionalOutput"));


        original.track("col1", "value");
        original.track("col1", 1);
        original.track("col2", "value");
        original.track(ImmutableMap.of("prediction", 1, "target", 1));
        original.track(ImmutableMap.of("prediction", 2, "target", 1));
        return original.toProtobuf().build();
    }

    public static void main(String[] args) {
        DatasetProfileMessage p1 = manualMethod();
        DatasetProfileMessage p2 = automaticMethod();

        uploadDatasetProfileMessage(p1);

    }
}