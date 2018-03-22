package hello;

import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.examples.Utils;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import redis.clients.jedis.Jedis;

import org.apache.commons.codec.binary.Hex;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@RestController
public class HelloController {

	@Autowired
	Jedis jedis = MyConfiguration.myJedis();
	
	public static final String SECRET = "SecretKeyToGenJWTs";
	public static final long EXPIRATION_TIME = 1000000;
	public static final String TOKEN_PREFIX = "Bearer ";
	public static final String HEADER_STRING = "Authorization";

/*	@RequestMapping(value = "/{type}/{id}/all", method = RequestMethod.GET, produces = "application/json")
	public ResponseEntity<?> myGetAll(@RequestHeader HttpHeaders headers, @PathVariable String type,
			@PathVariable String id) throws ProcessingException, IOException, ParseException {
		
		Set<String> claimsKeys = jedis.keys(type + "__" + id + "*");
		int claimsCounter = 0;
			JSONArray claimsArray = new JSONArray();
			Iterator claimsIterator = claimsKeys.iterator();
			while(claimsIterator.hasNext()){				
					JSONObject userClaims = new JSONObject(jedis.hgetAll((String) claimsIterator.next()));
					JSONObject resultJSON = convertToJson(userClaims);
					claimsArray.add(resultJSON);
					claimsCounter++;
				
			}
			if(claimsCounter>0)
				return new ResponseEntity<>(claimsArray, HttpStatus.OK);
			else
				return new ResponseEntity<>("Array does not exist or array is empty", HttpStatus.NOT_FOUND);
	}*/
	
	
	@RequestMapping(value = "/{type}/{id}", method = RequestMethod.GET, produces = "application/json")
	public ResponseEntity<?> myGet(@RequestHeader HttpHeaders headers, @PathVariable String type,
			@PathVariable String id) throws ProcessingException, IOException, ParseException {

		String strippedID = id;
		//if id is an array element, strip it to get user id 
				if(id.contains("_")){
					strippedID = id.substring(0, id.indexOf("_"));
				}
		
		//authorization header incorrect
		if(!authorizeUser(headers, strippedID)){
			return new ResponseEntity<>("Bad credentials for authorization", HttpStatus.UNAUTHORIZED);
		}
		
		//if key does not exist, return
		if (!jedis.exists(type + "__" + id)) {
			return new ResponseEntity<>("Key does not exist", HttpStatus.NOT_FOUND);
		}
		
		//return not modified etag
		List<String> header = headers.get("If-None-Match");
		System.out.println(headers);
		if(header!=null)
		{
			for (String header_value : header) {
				System.out.println("ETag header : " + header_value);
				String etag_value = jedis.get("etag" + "__" + type + "__" + id);
				System.out.println("ETag value : " + etag_value);
				if (etag_value!= null && header_value!=null && etag_value.equals(header_value)) {
					return new ResponseEntity<>("Value has not been modified, ETAG is same",HttpStatus.NOT_MODIFIED);
				} 
			}
		}

		JSONObject userClaims = new JSONObject(jedis.hgetAll(type + "__" + id));
			JSONObject resultJSON = convertToJson(userClaims);
		return new ResponseEntity<>(resultJSON, HttpStatus.OK);
	}

	//count occurrences of subtext in text
	public int count(String text, String subtext) {
		int count = 0;
		count = StringUtils.countOccurrencesOf(text, subtext);
		return count;
	}

	//convert json object with jedis keys to json object with all values
	public JSONObject convertToJson(JSONObject userClaims) throws ParseException {

		JSONObject storedUserObject = new JSONObject();

		for (Object key : userClaims.keySet()) {
			Object value =  userClaims.get(key);  //value maybe a key in jedis or property
			int countOfValue = count(value.toString(), "__");
			JSONObject currentKeyValue = null;
			
	//if value is a key in jedis		
			if (jedis.exists(value.toString())) {
				currentKeyValue = new JSONObject(jedis.hgetAll(value.toString()));
				if(currentKeyValue!= null)
					storedUserObject.put(key, convertToJson(currentKeyValue)); //recursive call to get json object
			} 
			
	//if value is an array		
			else if (countOfValue>=2) {
				System.out.println("I am a JSON Array");
				JSONArray array = (JSONArray) new JSONParser().parse(value.toString());

				//StackOverflow code to get claims object substring which is a key in jedis
				String str = (String) array.get(0);
				if (null != str && str.length() > 0 )
				{
				    int endIndex = str.lastIndexOf("_");
				    if (endIndex != -1)  
				    {
				        str = str.substring(0, endIndex-1); // not forgot to put check if(endIndex != -1)
				    }
				}
				currentKeyValue = new JSONObject(jedis.hgetAll(str));
				
				//search for claims array in claims object
				if(currentKeyValue.containsKey(key)){
					array = (JSONArray) new JSONParser().parse((String) currentKeyValue.get(key));
				}
				
				JSONArray myArray = new JSONArray();
				for (int i = 0; i < array.size(); i++) {
					if(jedis.exists((String) array.get(i)))
					{
						currentKeyValue = new JSONObject(jedis.hgetAll((String) array.get(i)));
						if(currentKeyValue!= null){
							JSONObject arrayObject = convertToJson(currentKeyValue);
							myArray.add(arrayObject);
						}
					}
				}
				storedUserObject.put(key, myArray);
			}
			
	// code to insert value in json object by replacing jedis key		
			else {
				//if value is a number, convert string to number
				if(hasNumber(value.toString())){
					double myInt = Double.parseDouble(value.toString());
					storedUserObject.put(key, myInt);
				}
			//	 if value is a jedis key, ignore since it is handled above and probably deleted	
				else if(value.toString().contains(key.toString())){
					continue;
				}
				
		// put value in json object		
				else{
					storedUserObject.put(key, value);
				}
				continue;
			}
		}
		System.out.println(storedUserObject);
		System.out.println();
		return storedUserObject;
	}

	// check if value is number or string
	private boolean hasNumber(String value) {
		// TODO Auto-generated method stub
		try{
			double myInt = Double.parseDouble(value);
			return true;
		}
		catch(NumberFormatException ex){
			return false;
		}
	}

	@RequestMapping(value = "/{type}/{id}", method = RequestMethod.PUT, consumes = "application/json")
	public ResponseEntity<?> myPut(@RequestHeader HttpHeaders headers, 
			@PathVariable String type, @PathVariable String id,
			@RequestBody String payload) throws ProcessingException, IOException, ParseException, NoSuchAlgorithmException {

		/*
		 * append elements to array, replace array elements, replace objects,
		 * replace/add property in object
		 */
		
		
		
		String strippedID = id;
		//if id is an array element, strip it to get user id 
				if(id.contains("_")){
					strippedID = id.substring(0, id.indexOf("_"));
				}
		
		//authorization header incorrect
	/*	if(!authorizeUser(headers, strippedID)){
			return new ResponseEntity<>("Bad credentials for authorization", HttpStatus.UNAUTHORIZED);
		}*/
		
		//check if payload is in correct json format
		try{
			JSONParser parser = new JSONParser();
			Object random = parser.parse(payload);
		}
		catch(Exception ex){
			return new ResponseEntity<>("Payload is not in proper json format", HttpStatus.FORBIDDEN);
		}
		
		boolean isArray = false;
/*		String strippedID = id;
		//if id is an array element, strip it to get user id 
				if(id.contains("_")){
					strippedID = id.substring(0, id.indexOf("_"));
				}*/
		
		//type is of array
		Set<String> claimsKeys = jedis.keys(type + "__" + id + "__" + "*");
		if(claimsKeys.size()>0){
			isArray = true;
		}
		
		final JsonNode fstabSchema = JsonLoader
				.fromPath("F:/VIGHNESH/Advances in Big data design and engineering/userSchema.json");
		final JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
		final JsonSchema schema = factory.getJsonSchema(fstabSchema);
		ProcessingReport report;
		
		//generate user json object to check schema later
		JSONObject userClaims = new JSONObject(jedis.hgetAll("user" + "__" + strippedID));		
		JSONObject userJSON = convertToJson(userClaims); 
		
		
		
		//get current json object from jedis
		JSONObject keys = new JSONObject(jedis.hgetAll(type+ "__" + id));
		JSONObject keysJSON = convertToJson(keys);
		
		//parse payload and convert to json object
		JSONParser parser = new JSONParser();
		JSONObject payloadJSON = new JSONObject();
		
		//payload is json object
		if(parser.parse(payload) instanceof JSONObject){
			payloadJSON = (JSONObject) parser.parse(payload);
		}
		
		//payload is json array
		if(parser.parse(payload) instanceof JSONArray){
			JSONArray tempArray = (JSONArray) parser.parse(payload);
			payloadJSON.put(type,tempArray);
		}
		
		//add/replace properties from payload into existing jsonobject obtained from jedis earlier
		JSONObject updatedJSON = addOrUpdate(keysJSON,payloadJSON, isArray);
		JsonNode bad = JsonLoader.fromString(updatedJSON.toJSONString());
		System.out.println(updatedJSON);
		
		//if type is not user, update user
		if(!(type.equals("user"))){
			userJSON = replaceKeyForUser(userJSON,updatedJSON,type,id);
			bad = JsonLoader.fromString(userJSON.toJSONString());
		}		
			report = schema.validate(bad);
			System.out.println(report);
		
		if(report.isSuccess()){
			Set<String> userKeys = jedis.keys(type + "__" + id + "*");
			for (String key : userKeys) {
				jedis.del(key);
			}
			saveToRedis(type, strippedID, JsonLoader.fromString(updatedJSON.toJSONString()));
		}
		
		//invalid keys
				if (!jedis.exists(type + "__"+ id)) {
					return new ResponseEntity<>("Key does not exist", HttpStatus.FORBIDDEN);
				}
		
		return new ResponseEntity<>("Update successful", HttpStatus.FORBIDDEN);
	}

	private JSONObject replaceKeyForUser(JSONObject userJSON, JSONObject keysJSON, String type, String id) {
		/*
		 * for key in userJSON:
		 * 		if key equals type:
		 * 			replace keys value with keysJSON
		 * 		else:
		 * 			value = userJSON.get(key)
		 * 			if value is an instance of jsonobject:
		 * 				recursive call to replace key
		 * 			else if it is an array:
		 * 				loop through array and replace key via recursive call
		 */
		
		if(userJSON.containsKey(type) && !(userJSON.get(type) instanceof JSONArray) ){
			userJSON.replace(type, keysJSON);
			return userJSON;
		}
		for(Object userKey : userJSON.keySet()){
			/*if(userKey.toString().equals(type) && !(userJSON.get(userKey) instanceof JSONArray)){
				userJSON.replace(userKey, keysJSON);
				return userJSON;
			}*/
			if(!(userJSON.get(userKey) instanceof JSONArray) && userJSON.get(userKey) instanceof JSONObject){
				JSONObject userValue = (JSONObject) userJSON.get(userKey);
				if(userValue.containsKey(type)){
					userValue = replaceKeyForUser(userValue, keysJSON, type, id);
					userJSON.replace(userKey, userValue);
					return userJSON;
				}				
			}
			if(userJSON.get(userKey) instanceof JSONArray){
				JSONArray userArray = (JSONArray) userJSON.get(userKey);
				for(int i = 0; i <userArray.size();i++){
					JSONObject arrayKey = (JSONObject) userArray.get(i);
					if(arrayKey.get("_id").equals(id) && arrayKey.get("_type").equals(type)){
						userArray.remove(i);
						userArray.add(keysJSON);
						break;
					}
				}
				userJSON.replace(userKey, userArray);
				break;
			}
		}
		return userJSON;
	}

	private JSONObject addOrUpdate(JSONObject keysJSON, JSONObject payload, boolean isArray) {
		/*		for key in payload:
		if keysjson has key:
			replace key with payload value
		else:
			add key value pair to keysjson*/
/*		if(isArray){
			
			return keysJSON;
		}*/
		for(Object payloadKey: payload.keySet()){
			if(keysJSON.containsKey(payloadKey) && !(payload.get(payloadKey) instanceof JSONArray)){
				keysJSON.replace(payloadKey, payload.get(payloadKey));
			}
			else if((payload.get(payloadKey) instanceof JSONArray) && keysJSON.containsKey(payloadKey)){
				JSONArray myArray = (JSONArray) keysJSON.get(payloadKey);
				myArray.addAll((Collection) payload.get(payloadKey));
				keysJSON.replace(payloadKey, myArray);
			}
			else{
				keysJSON.put(payloadKey, payload.get(payloadKey));
			}
		}		
		return keysJSON;
	}

	@RequestMapping(value = "/", method = RequestMethod.POST, consumes = "application/json")
	public ResponseEntity<?> myPost(@RequestHeader HttpHeaders headers, @RequestBody String payload)
			throws ProcessingException, IOException, ParseException, NoSuchAlgorithmException {
		
		//check if paylaod is in correct json format
		try{
			JSONParser parser = new JSONParser();
			JSONObject random = (JSONObject) parser.parse(payload);
		}
		catch(Exception ex){
			return new ResponseEntity<>("Payload is not in proper json format", HttpStatus.FORBIDDEN);
		}
		
		final JsonNode fstabSchema = JsonLoader
				.fromPath("F:/VIGHNESH/Advances in Big data design and engineering/userSchema.json");
		final JsonNode bad = JsonLoader.fromString(payload);
		final JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
		final JsonSchema schema = factory.getJsonSchema(fstabSchema);
		ProcessingReport report;
		
		//check for valid schema
		report = schema.validate(bad);
		if (report.isSuccess()) {
			String id = UUID.randomUUID().toString();
			String key = saveToRedis("user", id.toString(), JsonLoader.fromString(payload));
			String auth_code = generateAuthenticationCode(key);
			System.out.println("Authorization code is : " +auth_code);
			return new ResponseEntity<>(key, HttpStatus.OK);
		} else {
			return new ResponseEntity<>("Json is invalid according to schema", HttpStatus.FORBIDDEN);
		}
	}

	private String generateAuthenticationCode(String key) {
		System.out.println("generating JWT");
		String token = Jwts.builder()
		.setSubject((String) key)
		.setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
		.signWith(SignatureAlgorithm.HS512, SECRET.getBytes())
		.compact();
		//res..addHeader(HEADER_STRING, TOKEN_PREFIX + token);
		System.out.println("jwt is "+TOKEN_PREFIX + token);
		jedis.set("token" + "__" + key, TOKEN_PREFIX + token);
		return TOKEN_PREFIX + token;
	}
	
	private boolean authorizeUser(HttpHeaders headers, String id) {
	// TODO Auto-generated method stub
		List<String> header = headers.get(HEADER_STRING);
		
		if(header!=null)
		{
			for (String header_value : header)
			{			
				String stored_auth = jedis.get("token" + "__" + "user" + "__" + id);
				System.out.println("Authentication Key from jedis is "+stored_auth);
				System.out.println("Header value of auth is "+header_value);
				if (stored_auth!= null && header_value!= null && stored_auth.equals(header_value)) {
					return true;
				} 
			}
		}
		return false;		
}

	public String saveToRedis(String type, String id, JsonNode newUser) throws ParseException, UnsupportedEncodingException, NoSuchAlgorithmException {

		JSONObject map = new JSONObject();

		//add _id and _type to map
		map.put("_id", id);
		map.put("_type", type);

		Iterator<String> fieldNames = newUser.fieldNames();
		while (fieldNames.hasNext()) {
			String fieldName = fieldNames.next();
			JsonNode fieldValue = newUser.get(fieldName);
			if (fieldValue.isObject()) {
				map.put(fieldName, fieldName + "__" + id);
				saveToRedis(fieldName, id, fieldValue); //recursive call
			} else if (fieldValue.isArray()) {
				JSONObject arrayObject = new JSONObject(); //add _id and _type for array object
				arrayObject.put("_id", id);
				arrayObject.put("_type", fieldName);
				JSONArray array = new JSONArray();
				for (int i = 0; i < fieldValue.size(); i++) {
					String arrayID = UUID.randomUUID().toString();
					array.add(fieldName + "__" + id + "__" + arrayID);  //add arrayID for array element object
					saveToRedis(fieldName, id + "__" + arrayID, fieldValue.get(i)); //recursive call
				}
				arrayObject.put(fieldName ,array.toString());
				jedis.hmset(fieldName+ "__" + id, arrayObject);
				map.put(fieldName, array.toString());
			} else {
				if (fieldValue.isTextual()) {
					map.put(fieldName, fieldValue.textValue());  //add value to map
				} else {
					map.put(fieldName, fieldValue.toString());
				}
			}
		}
		
		MessageDigest md = null;
		byte[] bytesOfMessage = map.toString().getBytes("UTF-8");
		md = MessageDigest.getInstance("MD5");
		byte[] thedigest = md.digest(bytesOfMessage); // next, convert the byte array to string
		String result = new String(Hex.encodeHex(thedigest));
		String etag_key = "etag" + "__" + type + "__" + id;
		jedis.set(etag_key, result.toString());
		System.out.println(etag_key + " : " + result);
		
		System.out.println(map);
		System.out.println(type + "__" + id);
		System.out.println();
		System.out.println();
		jedis.hmset(type + "__" + id, map);
		return type + "__" + id;
	}

	@RequestMapping(value = "/{type}/{id}", method = RequestMethod.DELETE, consumes = "application/json")
	public ResponseEntity<?> myDelete(@RequestHeader HttpHeaders headers, @PathVariable String type,
			@PathVariable String id) throws ProcessingException, IOException, ParseException {

		
		String strippedID = id;
		//if id is an array element, strip it to get user id 
				if(id.contains("_")){
					strippedID = id.substring(0, id.indexOf("_"));
				}
		
		//authorization header incorrect
		if(!authorizeUser(headers, strippedID)){
			return new ResponseEntity<>("Bad credentials for authorization", HttpStatus.UNAUTHORIZED);
		}
		
		//if key does not exist, return
		if (!jedis.exists(type + "__" + id)) {
			return new ResponseEntity<>("Key does not exist", HttpStatus.FORBIDDEN);
		}
		
		//if type is user, delete all keys
		if (type.equals("user")) {
			Set<String> userKeys = jedis.keys("*" + id + "*");
			for (String key : userKeys) {
				jedis.del(key);
			}
			return new ResponseEntity<>("All user keys deleted", HttpStatus.OK);
		}
		
		//key to delete, could be array or json object
		JSONObject keyToDelete = new JSONObject(jedis.hgetAll(type + "__" + id));
	//	System.out.println(keyToDelete);
		
		//stripped id necessary for array type
	//	String strippedID = id;
		boolean isArray = false;
/*		if(id.contains("_")){
			strippedID = id.substring(0, id.indexOf("_"));
		}*/
		Set<String> claimsKeys = jedis.keys(type + "__" + id + "*");
		if(claimsKeys.size()>1){
			isArray = true;
		}
			
		JSONObject userClaims = new JSONObject(jedis.hgetAll("user" + "__" + strippedID));		
	//	System.out.println(userClaims);

		byte[] claims = null;
		ArrayList<Map<String, String>> valuesList = new ArrayList<>();
		ArrayList<String> keysList = new ArrayList<>();
		
	//	jedis.del(type + "__" + id); //temporarily delete key from jedis, restore later if schema invalid

		if(!isArray){
		jedis.del(type + "__" + id); //temporarily delete object key from jedis, restore later if schema invalid
		//jedis.del("etag"+"__"+type+"__"+id);
		}
		else{
			//claims = jedis.dump(type + "__" + id);
	
			//temporarily delete array key and all element keys in array
			Set<String> userKeys = jedis.keys(type + "__" + id + "*");
			for (String key : userKeys) {
				Map<String, String> temp = jedis.hgetAll(key);
				keysList.add(key);
				valuesList.add(temp);
				jedis.del(key);
			}
			jedis.del(type + "__" + id);
		}
		
//		JSONObject resultClaims = deleteKeyFromUser(userClaims,id,type);
//		System.out.println(resultClaims);
//		JSONObject resultJSON = convertToJson(resultClaims);

		JSONObject resultJSON = convertToJson(userClaims);
		final JsonNode fstabSchema = JsonLoader
				.fromPath("F:/VIGHNESH/Advances in Big data design and engineering/userSchema.json");
		final JsonNode bad = JsonLoader.fromString(resultJSON.toJSONString());
		final JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
		final JsonSchema schema = factory.getJsonSchema(fstabSchema);
		ProcessingReport report;
		report = schema.validate(bad);
		System.out.println(report);
		
		//check if schema is valid after delete
		if (report.isSuccess()) {
			System.out.println(resultJSON.toJSONString());
			return new ResponseEntity<>("Keys deleted", HttpStatus.OK);
		} else {
			if(isArray){
				jedis.hmset(type+"__"+id, keyToDelete);
				for(int i = 0; i < valuesList.size();i++){
					jedis.hmset(keysList.get(i), valuesList.get(i));
				}
			}
			else{
				jedis.hmset(type+"__"+id, keyToDelete);				
			}
			
		//	jedis.hmset(type+"__"+id, keyToDelete);
			return new ResponseEntity<>("Cannot delete. Json schema becomes invalid", HttpStatus.FORBIDDEN);
		}
	}
}
/*	public JSONObject deleteKeyFromUser(JSONObject userObject, String id, String type) throws ParseException {
		
		//user object has key to be deleted
		if(userObject.containsKey(type) && !(id.contains("_"))){
			userObject.remove(type);
			return userObject;
		}
	
		//user object has jedis key which needs to be iterated to reach key to be deleted
			for (Object key : userObject.keySet()) {
				String value = (String) userObject.get(key); //value is the jedis key or an array
				int countOfValue = count(value, "__"); 
				if(jedis.exists(value)){
					JSONObject fetchedKey = new JSONObject(jedis.hgetAll(value));
					if(fetchedKey.containsKey(type) && !(id.contains("_"))){
						JSONObject newKey = deleteKeyFromUser(fetchedKey, id, type); //recursion
						userObject.replace(key, newKey);
						return userObject;	
					}
				}else if(countOfValue>=2){
					boolean removed = false;
					JSONArray array = (JSONArray) new JSONParser().parse(value); //value is an array
					if(array.size() == 0)
						return userObject;  //array is empty
					
					//check for key inside array
					for (int i = 0; i < array.size(); i++) {
						if(array.get(i).toString().equals(type + "__"+ id)){
							array.remove(i);
							removed = true;
							break;
						}
					}
					
//if key not found in array, perform jedis.get for all keys in array and search for key to be deleted from values obtained
//not tested, since not applicable for my JSON dataset
					if(removed == false){
						for(int i = 0;i<array.size();i++){
							if(jedis.exists((String) array.get(i)))
							{
								JSONObject fetchedKey = new JSONObject(jedis.hgetAll((String) array.get(i)));
								System.out.println(fetchedKey);
								if(fetchedKey.containsKey(type) && !(id.contains("_"))){
									fetchedKey = deleteKeyFromUser(fetchedKey, id, type);
									array.remove(i);
									array.add(fetchedKey);
									break;
								}	
							}
						}
					}
				userObject.replace(key, array.toString()); // replace object key
															// with new array
															// where input key is deleted
					return userObject;
				}
			}
			return userObject;
		
	}
}
*/