/**
 * Mars Simulation Project
 * EmotionJSONConfig.java
 * @version 3.08 2015-12-12
 * @author Manny Kung
 */

package org.mars_sim.msp.core.person.ai;



import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
 
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
 
public class EmotionJSONConfig {
 
    public static final String JSON_FILE="/json/emotions.json";
    
    private String[] emotional_state_names = {
		"joy",
		"distress",
		"happy",
		"pity",
		"gloating",
		"resentment",
		"hope",
		"fear",
		"satisfaction",
		"fears-confirmed",
		"relief",
		"disappointment",
		"pride",
		"shame",
		"admiration",
		"reproach",
		"gratification",
		"remorse",
		"gratitude",
		"anger",
		"love",
		"hate"
	};
    
    private Emotion e;
    
    private Settler settler;
    
    public static void main(String[] args) {
			new EmotionJSONConfig();
    }
    
    public EmotionJSONConfig(){
    	
        InputStream fis = null;
        JsonReader jsonReader = null;
        fis =		this.getClass().getResourceAsStream(JSON_FILE);
        jsonReader = Json.createReader(fis);
         
        /**
         * We can create JsonReader from Factory also
        JsonReaderFactory factory = Json.createReaderFactory(null);
        jsonReader = factory.createReader(fis);
        */
         
        //get JsonObject from JsonReader
        JsonObject jsonObject = jsonReader.readObject();
         
        //we can close IO resource and JsonReader now
        jsonReader.close();
        try {
			fis.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
         
        //Retrieve data from JsonObject and create Employee bean
        settler = new Settler();
         
        settler.setName(jsonObject.getString("name"));
     
        //reading inner object from json object
        JsonObject innerJsonObject = jsonObject.getJsonObject("emotions");
        
        e = new Emotion();
        
        int size = emotional_state_names.length;

        try {
	        for (int i = 0; i< size; i++) {
	        	//String s = emotional_state_names[i];
	        	//System.out.println(innerJsonObject.getJsonNumber(s).intValue());
	        	e.setEmotions(innerJsonObject.getInt(emotional_state_names[i]), i);
	        }
        } catch (Exception e1) {
			e1.printStackTrace();
		}
        
        settler.setEmotion(e);
         
        //System.out.println(settler);
         
    }
 
	public int [] getEmotionalStates() {
		return e.emotional_states;
	}
    class Settler {
    	String name = null;
    	Emotion e = null;
    	
    	Settler() {}
    	void setName(String name) {
    		this.name = name;
    	}
    	void setEmotion(Emotion e) {
    		this.e = e;
    	}
    	Emotion getEmotion() {
    		return e;
    	}
    	@Override
        public String toString(){
            StringBuilder sb = new StringBuilder();
            sb.append("***** Settler's Preconfigured Emotional States *****\n");
            sb.append("Name = " + name);
            sb.append("\nEmotions : " + e);//toString());//+ getEmotion());
            //sb.append(emotionString[i]);
            sb.append("\n*****************************************************");
             
            return sb.toString();
        }
    }
    
    
    class Emotion {
    
    	int [] emotional_states = new int[22]; 
    	// range 1 to 10

    	Emotion() {}
    	void setEmotions(int value, int i) {
    		emotional_states[i] = value;
    	}
    	
    	public int [] getEmotionalStates() {
    		return emotional_states;
    	}
    	
    	public String toString() {
    		String result = "{ ";
    		
    		for (int e : emotional_states) {
    			result += e + " ";
    		}
    		
    		return result +"}";
    	}
    	
    	public int[] toPrint() {
    		return emotional_states;
    	}
    }
    
}