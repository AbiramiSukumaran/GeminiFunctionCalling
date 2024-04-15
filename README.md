# GeminiFunctionCalling
Explore Gemini Function Calling feature in a Java application. This is the Gen 2 Cloud Function implementation where we will invoke the Gemini model to orchestrate the input for function Calling, invoke the API and then process the response in another Gemini call and deploy it to a REST endpoint.

Gemini Function Calling stands out in the Generative AI era because it lets you blend the flexibility of generative language models with the precision of traditional programming. Here's how it works: 

Defining Functions: You describe functions as if you were explaining them to a coworker. These descriptions include:

The function's name (e.g., "getAddress")
The parameters it expects (e.g., "latlng" as a string)
The type of data it returns (e.g., a list of address strings)

"Tools" for Gemini: You package function descriptions in the form of API specification into "Tools". Think of a tool as a specialized toolbox Gemini can use to understand the functionality of the API.

Gemini as API Orchestrator: When you send a prompt to Gemini, it can analyze your request and recognize where it can use the tools you've provided. Gemini then acts as a smart orchestrator:

Generates API Parameters: It produces the necessary parameters to call your defined functions.
Calls External APIs: Gemini doesn’t call the API on your behalf. You call the API based on the parameters and signature that Gemini function calling has generated for you.
Processes Results: Gemini feeds the results from your API calls back into its generation, letting it incorporate structured information into its final response which you can process in any way you desire for your application.

High Level Architecture Diagram

![image](https://github.com/AbiramiSukumaran/GeminiFunctionCalling/assets/13735898/44a07c0e-8eee-4078-a56e-21f4817d1880)


## Let’s understand Function Calling by breaking down the class HelloWorld.java:

For this to work, you must update API_KEY and project_id with your geocoding API Key and Google Cloud Project ID respectively.

### Prompt Input:
In this example, this is what the input prompt looks like: 
“What's the address for the latlong value 40.714224,-73.961452”

You can find the below code snippet relevant to the input prompt in the file: 
String promptText = "What's the address for the latlong value '" + latlngString + "'?"; //40.714224,-73.961452


### API Specification / Signature Definition:
We decided to use the Reverse Geocoding API. In this example, this is what the API spec looks like: 

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


### Gemini to orchestrate the prompt with the API specification:
This is the part where we send the prompt input and the API spec to Gemini: 

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
      String responseJSON = ResponseHandler.getContent(response).toString();

The response from this is the orchestrated parameters JSON to the API. Output from this step would look like below:

role: "model"
parts {
  function_call {
    name: "getAddress"
    args {
      fields {
        key: "latlng"
        value {
          string_value: "40.714224,-73.961452"
        }
      }
    }
  }
}

The parameter that needs to be passed to the Reverse Geocoding API is this:
“latlng=40.714224,-73.961452”

Match the orchestrated result to the format “latlng=VALUE”. 

### Invoke the API:
At this point you have everything you need to invoke the API. The part of the code that does it is below:

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


The string jsonResult holds the stringified response from the reverse Geocoding API. It looks something like this: (This is a formatted version of the output. Please note the result is truncated as well).

“...277 Bedford Ave, Brooklyn, NY 11211, USA; 279 Bedford Ave, Brooklyn, NY 11211, USA; 277 Bedford Ave, Brooklyn, NY 11211, USA;...”


### Process the API response and prepare the prompt:
The below code processes the response from the API and prepares the prompt with instructions on how to process the response:

 // Provide an answer to the model so that it knows what the result
      // of a "function call" is.
      String promptString =
      "You are an AI address standardizer for assisting with standardizing addresses accurately. Your job is to give the accurate address in the standard format as a JSON object containing the fields DOOR_NUMBER, STREET_ADDRESS, AREA, CITY, TOWN, COUNTY, STATE, COUNTRY, ZIPCODE, LANDMARK by leveraging the address string that follows in the end. Remember the response cannot be empty or null. ";


Content content =
          ContentMaker.fromMultiModalData(
              PartMaker.fromFunctionResponse(
                  "getAddress",
                  Collections.singletonMap("address", formattedAddress)));
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


### Invoke Gemini and return the standardized address :
The below code passes the processed output from the above step as prompt to Gemini:

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


The finalAnswer variable has the standardized address in JSON format. Sample output below:

{"replies":["{ \"DOOR_NUMBER\": null, \"STREET_ADDRESS\": \"277 Bedford Ave\", \"AREA\": \"Brooklyn\", \"CITY\": \"New York\", \"TOWN\": null, \"COUNTY\": null, \"STATE\": \"NY\", \"COUNTRY\": \"USA\", \"ZIPCODE\": \"11211\", \"LANDMARK\": null} null}"]}

Now that you have understood how Gemini Function Calling works with the address standardization use case, go ahead and deploy the Cloud Function.

### Deploy and test
Go to Cloud Shell terminal, clone this repo:
git clone https://github.com/AbiramiSukumaran/GeminiFunctionCalling

Navigate to the project folder:
cd GeminiFunctionCalling

Execute the below statement build and deploy the Cloud Function:
gcloud functions deploy gemini-fn-calling --gen2 --region=us-central1 --runtime=java11 --source=. --entry-point=cloudcode.helloworld.HelloWorld --trigger-http

The URL after deployment would be in the format as below :
https://us-central1-YOUR_PROJECT_ID.cloudfunctions.net/gemini-fn-calling

Test this Cloud Function by running the following command from the terminal:
gcloud functions call gemini-fn-calling --region=us-central1 --gen2 --data '{"calls":[["40.714224,-73.961452"]]}'

Response for a random sample prompt:
  '{"replies":["{ \"DOOR_NUMBER\": \"277\", \"STREET_ADDRESS\": \"Bedford Ave\", \"AREA\":
  null, \"CITY\": \"Brooklyn\", \"TOWN\": null, \"COUNTY\": \"Kings County\", \"STATE\":
  \"NY\", \"COUNTRY\": \"USA\", \"ZIPCODE\": \"11211\", \"LANDMARK\": null}}```"]}'
