/* Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
*/

package cloudcode.helloworld;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import java.io.BufferedWriter;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.Part;
import com.google.cloud.vertexai.api.FunctionDeclaration;
import com.google.cloud.vertexai.api.FunctionResponse;
import com.google.cloud.vertexai.api.FunctionCall;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.GenerateContentRequest;
import com.google.cloud.vertexai.api.Schema;
import com.google.cloud.vertexai.api.Tool;
import com.google.cloud.vertexai.api.Type;
import com.google.cloud.vertexai.generativeai.ChatSession;
import com.google.cloud.vertexai.generativeai.ContentMaker;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.PartMaker;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import com.google.cloud.vertexai.api.HarmCategory;
import com.google.cloud.vertexai.api.SafetySetting;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import java.net.URI;

/*
This class demonstrates how to use Gemini 1.0 Pro for getting deterministic responses 
using Gemini's Function Calling feature, calls an external API (Geocoding API) 
and returns the formatted address in a JSON object. 
Generate a Geocoding API Key and 
update the API_KEY and projectId variables in the code before executing the program.
*/
public class HelloWorld implements HttpFunction {
  public static String API_STRING = "https://maps.googleapis.com/maps/api/geocode/json";
  public static String API_KEY = "YOUR_API_KEY";
  public static Gson gson = new Gson();
  @Override
  public void service(HttpRequest request, HttpResponse response) throws Exception {
    BufferedWriter writer = response.getWriter();

    // TODO(developer): Replace these variables before running the sample.
    String projectId = "YOUR_PROJECT_ID";
    String location = "us-central1";
    String modelName = "gemini-1.0-pro";

     // Get the request body as a JSON object.
    JsonObject requestJson = new Gson().fromJson(request.getReader(), JsonObject.class);
    JsonArray calls_array = requestJson.getAsJsonArray("calls");
    JsonArray calls = (JsonArray) calls_array.get(0);
    String latlngString = calls.get(0).toString().replace("\"", "");
    // Pass the incoming request body to the method
    String promptText = "What's the address for the latlong value '" + latlngString + "'?"; //40.714224,-73.961452
    String rawResult = callApi(projectId, location, modelName, promptText);
    //writer.write(rawResult);

    rawResult = rawResult.replace("\n","");
    String trimmed = rawResult.trim();
    List<String> resultList = Arrays.asList(trimmed);
    Map<String, List<String>> stringMap = new LinkedHashMap<>();
    stringMap.put("replies", resultList);

      // Serialization
    String return_value = gson.toJson(stringMap);
    writer.write(return_value);
  }

  public static String callApi(String projectId, String location,
      String modelName, String promptText)
      throws IOException, InterruptedException {

    try (VertexAI vertexAI = new VertexAI(projectId, location)) {
      /* Declare the function for the API that we want to invoke (Geo coding API) */
      FunctionDeclaration functionDeclaration = FunctionDeclaration.newBuilder()
          .setName("getAddress")
          .setDescription("Get the address for the given latitude and longitude value.")
          .setParameters(
              Schema.newBuilder()
                  .setType(Type.OBJECT)
                  .putProperties("latlng", Schema.newBuilder()
                      .setType(Type.STRING)
                      .setDescription("This must be a string of latitude and longitude coordinates separated by comma")
                      .build())
                  .addRequired("latlng")
                  .build())
          .build();

      // Add the function to a "tool"
      Tool tool = Tool.newBuilder()
          .addFunctionDeclarations(functionDeclaration)
          .build();

      // Invoke the Gemini model with the use of the  tool to generate the API parameters from the prompt input.
      GenerativeModel model = GenerativeModel.newBuilder()
          .setModelName(modelName)
          .setVertexAi(vertexAI)
          .setTools(Arrays.asList(tool))
          .build();
      GenerateContentResponse response = model.generateContent(promptText);
      Content responseJSONCnt = response.getCandidates(0).getContent();
      Part functionResponse = responseJSONCnt.getParts(0);
      String key = "";
      String val = "";

      if (functionResponse.hasFunctionCall()) {
        FunctionCall functionCall = functionResponse.getFunctionCall();
        if (functionCall.hasArgs()) {
          com.google.protobuf.Struct args = functionCall.getArgs();
          Map<String, com.google.protobuf.Value> fields = args.getFieldsMap();
          String latlng = fields.get("latlng").getStringValue();
          for (Map.Entry<String, com.google.protobuf.Value> entry : fields.entrySet()) {
            key = entry.getKey();
            com.google.protobuf.Value value = entry.getValue();
          }      
          val = latlng;
          System.out.println("kay and val: " + key + " : " + val);
        }
      }
     
   
      String params = "";
      params = params + "&" + key + "=" + val;
      
      /* API invocation code begins here.
      Invoke the API in the string "API_STRING" appended with the 
      request parameter "params" with the value from the variable "params" */
      // Create a client
       java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
      // Create a request
      String url = API_STRING + "?key=" + API_KEY + params;
      java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
          .uri(URI.create(url))
          .GET()
          .build();
      // Send the request and get the response
      java.net.http.HttpResponse<String> httpresponse = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
      // Save the response
      String jsonResult =  httpresponse.body().toString();
      System.out.println(jsonResult); 
      /* API invocation code ends here. */
      
      //convert String jsonResult to JSON object and extract the value of formatted address from it
      JsonObject jsonObject = gson.fromJson(jsonResult, JsonObject.class);

      // Get the results array from the JSON object
      JsonArray results = jsonObject.getAsJsonArray("results");
      // Iterate over the results array
      String formattedAddress = "Address String:";
      for (JsonElement result : results) {
        // Get the formatted address from the result
        formattedAddress = formattedAddress + result.getAsJsonObject().get("formatted_address").getAsString() + "; ";
      }
      System.out.println(formattedAddress);
         // Provide an answer to the model so that it knows what the result
      // of a "function call" is.
      String promptString = 
      "You are an AI address standardizer for assisting with standardizing addresses accurately. Your job is to give the accurate address in the standard format as a JSON object containing the fields DOOR_NUMBER, STREET_ADDRESS, AREA, CITY, TOWN, COUNTY, STATE, COUNTRY, ZIPCODE, LANDMARK by leveraging the address string that follows in the end. Remember the response cannot be empty or null. ";

  Content content =
          ContentMaker.fromMultiModalData(
              PartMaker.fromFunctionResponse(
                  "getAddress",
                  Collections.singletonMap("address", formattedAddress)));
      System.out.println("Provide the function response: " + content);
      String contentString = content.toString();
      String address = contentString.substring(contentString.indexOf("string_value: \"") + "string_value: \"".length(), contentString.indexOf('"', contentString.indexOf("string_value: \"") + "string_value: \"".length()));

      List<SafetySetting> safetySettings = Arrays.asList(
        SafetySetting.newBuilder()
            .setCategory(HarmCategory.HARM_CATEGORY_HATE_SPEECH)
            .setThreshold(SafetySetting.HarmBlockThreshold.BLOCK_ONLY_HIGH)
            .build(),
        SafetySetting.newBuilder()
            .setCategory(HarmCategory.HARM_CATEGORY_DANGEROUS_CONTENT)
            .setThreshold(SafetySetting.HarmBlockThreshold.BLOCK_ONLY_HIGH)
            .build()
    );

     GenerativeModel modelForFinalResponse = GenerativeModel.newBuilder()
      .setModelName(modelName)
      .setVertexAi(vertexAI)
      .build(); 
      GenerateContentResponse finalResponse = modelForFinalResponse.generateContent(promptString + ": " + address, safetySettings);
       System.out.println("promptString + content: " + promptString + ": " + address);
        // See what the model replies now
        System.out.println("Print response: ");
        System.out.println(finalResponse.toString());
        String finalAnswer = ResponseHandler.getText(finalResponse);
        System.out.println(finalAnswer);
      
    return finalAnswer;
    }
  }
}
