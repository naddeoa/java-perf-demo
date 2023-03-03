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

    private static void uploadToUrl(String url, DatasetProfile message) {
        try{
            HttpURLConnection connection = (HttpURLConnection )new URL(url).openConnection();
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/octet-stream");
            connection.setRequestMethod("PUT");

            OutputStream out = connection.getOutputStream();
            try (out){
               message.toProtobuf().build().writeDelimitedTo(out);
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

    private static void uploadDatasetProfileMessage(DatasetProfile message){
        ApiClient client = Configuration.getDefaultApiClient();
        client.setBasePath("https://api.whylabsapp.com");
        ApiKeyAuth auth = (ApiKeyAuth )client.getAuthentication("ApiKeyAuth");
        auth.setApiKey("...");
        LogApi api = new LogApi(client);
        LogAsyncRequest request = new LogAsyncRequest()
                .segmentTags(new ArrayList<>())
                .datasetTimestamp(Instant.now().toEpochMilli());

        try{
            AsyncLogResponse response = api.logAsync("org-JpsdM6", "model-23", request);
            String url = response.getUploadUrl();
            uploadToUrl(url, message);
        }catch(ApiException e){
            // Error getting upload url
            e.printStackTrace();
        }
    }

    public static DatasetProfile automaticMethod() {
        DatasetProfile original = new DatasetProfile("test", Instant.now(), null, Collections.emptyMap(), Collections.emptyMap())
                // Make sure to create a WhyLabs model that has type Regression as well or the performance metrics won't appear
                .withRegressionModel("prediction", "target",ImmutableList.of("additionalOutput"));

        // Make sure not to track prediction values that are null. Those have to be filtered out or things will break.
        // You can log inputs/outputs together or separate. Make sure not to log inputs (col1, col2) twice in different places though.
        original.track(ImmutableMap.of("prediction", 1, "target", 1, "col1", 5.3, "col2", 7.8));
        original.track(ImmutableMap.of("prediction", 2, "target", 1, "col1", 7.3, "col2", 17.8));
        original.track(ImmutableMap.of("prediction", 3, "target", 1, "col1", 2.3, "col2", 78.8));

        // Or separate
        original.track(ImmutableMap.of("prediction", 1, "target", 1));
        original.track(ImmutableMap.of("col1", 5.3, "col2", 7.8));

        return original;
    }

    public static void main(String[] args) {
        DatasetProfile profile = automaticMethod();
        uploadDatasetProfileMessage(profile);
    }
}