/**
    Copyright 2014-2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.

    Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the License. A copy of the License is located at

        http://aws.amazon.com/apache2.0/

    or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.amazon.customskill;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazon.speech.json.SpeechletRequestEnvelope;
import com.amazon.speech.slu.Intent;
import com.amazon.speech.speechlet.IntentRequest;
import com.amazon.speech.speechlet.LaunchRequest;
import com.amazon.speech.speechlet.SessionEndedRequest;
import com.amazon.speech.speechlet.SessionStartedRequest;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazon.speech.speechlet.SpeechletV2;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.Reprompt;
import com.amazon.speech.ui.SsmlOutputSpeech;



/*
 * This class is the actual skill. Here you receive the input and have to produce the speech output. 
 */
public class AlexaSkillSpeechlet
implements SpeechletV2
{
	// Initialisiert den Logger. Am besten m√∂glichst of Logmeldungen erstellen, hilft hinterher bei der Fehlersuche!
	static Logger logger = LoggerFactory.getLogger(AlexaSkillSpeechlet.class);

	// Variablen, die wir auch schon in DialogOS hatten
	static String answerOption1 = "";
	static String answerOption2 = "";
	static String question = "";
	static String correctAnswer = "";
	static String s‰tzeDeutsch = "";
	static int cat; //1=Restaurant, 2=Smalltalk, 3=Directions
	static int gameMode;
	static int count = 1;
	static int quit = 0; //0=weiter, 1=zur¸ck ins men¸ oder beenden
	private static boolean isRun = false;
	static int famCheck = 0;

	// Was der User gesagt hat
	public static String userRequest;

	// In welchem Spracherkennerknoten sind wir?
	static enum RecognitionState {Answer, YesNo, Category, Gamemode};
	RecognitionState recState;

	// Was hat der User grade gesagt. (Die "Semantic Tags"aus DialogOS)
	static enum UserIntent {Yes, No, Correct, Wrong, Error, Restaurant, Smalltalk, Directions, Dialoge, S‰tze, Stop};
	UserIntent ourUserIntent;

	// Was das System sagen kann
	Map<String, String> utterances;

	// Baut die System√§u√üerung zusammen
	String buildString(String msg, String replacement1, String replacement2) {
		return msg.replace("{replacement}", replacement1).replace("{replacement2}", replacement2);
	}

	// Liest am Anfang alle System√§u√üerungen aus Datei ein
	Map<String, String> readSystemUtterances() {
		Map<String, String> utterances = new HashMap<String, String>(); 
		try {
			for (String line : IOUtils.readLines(this.getClass().getClassLoader().getResourceAsStream("utterances.txt"))){
				if (line.startsWith("#")){
					continue;	
				}
				String[] parts = line.split("=");
				String key = parts[0].trim();
				String utterance = parts[1].trim();
				utterances.put(key, utterance);
			}
			logger.info("Read "  +utterances.keySet().size() + "utterances");
		} catch (IOException e) {
			logger.info("Could not read utterances: "+e.getMessage());
			System.err.println("Could not read utterances: "+e.getMessage());
		}
		return utterances;
	}

	// Datenbank f√ºr Quizfragen
	static String DBName = "AlexaBeispiel.db";
	static String DBName1 = "S‰tze.db";
	static String DB_Dialoge = "Dialoge.db";
	private static Connection con = null;


	// Vorgegebene Methode wird am Anfang einmal ausgef√ºhrt, wenn ein neuer Dialog startet:
	// * lies Nutzer√§u√üerungen ein
	// * Initialisiere Variablen
	@Override
	public void onSessionStarted(SpeechletRequestEnvelope<SessionStartedRequest> requestEnvelope)
	{
		logger.info("Alexa session begins");
		utterances = readSystemUtterances();
		
	}

	// Wir starten den Dialog:
	// * Hole die erste Frage aus der Datenbank
	// * Lies die Welcome-Message vor, dann die Frage
	// * Dann wollen wir eine Antwort erkennen
	@Override
	public SpeechletResponse onLaunch(SpeechletRequestEnvelope<LaunchRequest> requestEnvelope)
	{
		logger.info("onLaunch");
		recState = RecognitionState.YesNo;
		return askUserResponse(utterances.get("famCheck"));	
		
	}

	// Ziehe eine Frage aus der Datenbank.
	private void selectQuestion() {
		if(gameMode==1) {
		
		try {
			con = DBConnection.getConnection1();
			Statement stmt = con.createStatement();
			logger.info("ID: "+count);
			ResultSet rs = stmt.executeQuery("SELECT * FROM S‰tzeLeicht");
			ResultSet rs1 = stmt.executeQuery("SELECT * FROM S‰tzeLeicht");
			for (int i=1; i<count;i++) {
				rs.next();
				rs1.next();
			}
			s‰tzeDeutsch = rs1.getString("Deutsch");
			question = rs.getString("Englisch");
			//correctAnswer = rs.getString("Englisch");
			logger.info("Extracted question from database: "+ question);
		} catch (Exception e){
			logger.info("Exception");
			e.printStackTrace();}
		}
		//noch nicht korrekt
		else if(gameMode==2){
		try {
			con = DBConnection.getConnection2();
			Statement stmt = con.createStatement();
			if (cat==1) {
				ResultSet rs = stmt.executeQuery("SELECT * FROM DialogeLeicht");
				for (int i=1; i<count;i++) {
					rs.next();
				}
				question = rs.getString("Alexa");
			}
			else if (cat==2) {
				ResultSet rs1 = stmt.executeQuery("SELECT * FROM DialogeRestaurant");
				for (int i=1; i<count;i++) {
					rs1.next();
				}
				question = rs1.getString("Alexa");
			}
			else if (cat==3) {
				ResultSet rs2 = stmt.executeQuery("SELECT * FROM DialogeWegbeschreibung");
				for (int i=1; i<count;i++) {
					rs2.next();
				}
				question = rs2.getString("Alexa");
			}
			else {
				logger.info("Error in Categoryselection");
			}
			logger.info("Extracted question from database "+ question);
		} catch (Exception e){
			e.printStackTrace();}
		}
			
	}
		


	// Hier gehen wir rein, wenn der User etwas gesagt hat
	// Wir speichern den String in userRequest, je nach recognition State reagiert das System unterschiedlich
	@Override
	public SpeechletResponse onIntent(SpeechletRequestEnvelope<IntentRequest> requestEnvelope)
	{
		logger.info("onIntent");
		IntentRequest request = requestEnvelope.getRequest();
		Intent intent = request.getIntent();
		userRequest = intent.getSlot("anything").getValue();
		userRequest = userRequest.toLowerCase();
		logger.info("Received following text: [" + userRequest + "]");
		logger.info("recState is: "+ recState +"]");
		SpeechletResponse resp = null;
		switch (recState) {
		case Answer: resp = evaluateAnswer(userRequest); break;
		case YesNo: resp = evaluateYesNo(userRequest); break;
		case Category: resp = evaluateCategory(userRequest); recState = RecognitionState.Answer; break;
		case Gamemode: resp = evaluateGamemode(userRequest); recState = RecognitionState.Category; break;
		default: resp = tellUserAndFinish("Erkannter Text: " + userRequest);break;
		}   
		return resp;
	}

	// Ja/Nein-Fragen kommen genau dann vor, wenn wir wissen wollen, ob der User weitermachen will.
	// Wenn Ja, stelle die n√§chste Frage
	// Wenn Nein, verabschiede den user
	private SpeechletResponse evaluateYesNo(String userRequest) {
		SpeechletResponse res = null;
		recognizeUserIntent(userRequest);
		switch (ourUserIntent) {
		case Yes: {
			if(!isRun) {
				isRun=true;
			    famCheck=1; // means familiar user
			    recState = RecognitionState.Gamemode;
			    res = askUserResponse(utterances.get("gamemodeMsg"));
			    logger.info("familiarUserMsg");
			    break;
			}
			else if(quit==1) {
				res = tellUserAndFinish(utterances.get("goodbyeMsg"));
				break;
			}
			else {
				recState = RecognitionState.Answer;
				logger.info("rec State=Answer");
				selectQuestion();
				res = askUserResponse(question+""+s‰tzeDeutsch);
				break;
			}
		} case No: {
			if(!isRun) {
				isRun=true;
				 recState = RecognitionState.Gamemode;
				 res = askUserResponse(utterances.get("welcomeMsg"));
				 logger.info("welcomeMsg");
				 break;
			}
			else if(quit==0) {
				quit=1;
				recState = RecognitionState.YesNo;
				res=askUserResponse("Do you want to quit?");
				break;
			}
			else {
				quit=0;
				count=1;
				recState = RecognitionState.Gamemode;
				res = askUserResponse("Ok, what Gamemode do you want to try?"); 
				break;
			}
			
		} default: {
			res = askUserResponse(utterances.get("")); 
			break;
		}
		}
		return res;
	}


	private SpeechletResponse evaluateAnswer(String userRequest) {
		SpeechletResponse res = null;
		recognizeUserIntent(userRequest);
	
		if(gameMode==1) {
			if(userRequest.equals(question)) {
				logger.info("User answer recognized as correct.");
				
				if(count % 3 == 0) {
					count+=1;
					recState = RecognitionState.YesNo;
					res = askUserResponse(utterances.get("correctMsg")+" "+utterances.get("continueMsg"));
					
				}
				else {
					count+=1;
					recState = RecognitionState.Answer;
					selectQuestion();
					res = askUserResponse(utterances.get("correctMsg") + " " + question + " " + s‰tzeDeutsch);
					
				}
			}
			else if(userRequest.equals("penis")) {
				logger.info("geht ind den evaluateAnswer Block");
				recState = RecognitionState.YesNo;
				logger.info("recState YesNo");
				quit = 1;
				res = askUserResponse("Do you want to quit?");
			}
			else {
				logger.info("s‰tze error");
				res = askUserResponse(utterances.get("errorAnswerMsg")+" "+question+" "+s‰tzeDeutsch);
			}
		}
		
		else if(gameMode==2) {
			logger.info("User Intent: "+ourUserIntent);
			switch(ourUserIntent) {
			case Correct:{
				logger.info("User answer recognized as correct.");
				recState = RecognitionState.YesNo;
				res = askUserResponse(utterances.get("correctMsg")+" "+utterances.get("continueMsg"));
				break;
			}
			case Wrong:{
				res = askUserResponse(utterances.get("wrongMsg")+" "+question);
				break;
			}
			default:{
				res = askUserResponse(utterances.get("errorMsg"));
				break;
			}
			}
		}
		else {
			res = askUserResponse(utterances.get("errorMsg"));
		}
		
		return res;
	}
	private SpeechletResponse evaluateCategory(String userRequest) {
		SpeechletResponse res = null;
		recognizeUserIntent(userRequest);
		switch (ourUserIntent) {
		case Smalltalk:{
			cat = 1;
			selectQuestion();
			res = askUserResponse(question);
			break;
		}
		case Restaurant:{
			cat = 2;
			selectQuestion();
			break;
		}
		case Directions:{
			cat = 3;
			selectQuestion();
			break;
		}
		default: {
			res = askUserResponse(utterances.get(""));
			break;
		}
		}
		return res;
	}
	
	private SpeechletResponse evaluateGamemode(String userRequest) {
		SpeechletResponse res = null;
		recognizeUserIntent(userRequest);
		logger.info("Gamemode");
		switch (ourUserIntent) {
		case S‰tze:{
			gameMode = 1;
			selectQuestion();
			if(famCheck==1) {
				res = askUserResponse(utterances.get("famSentenceMsg")+question+" "+s‰tzeDeutsch);
				break;
			}else {
			res = askUserResponse(utterances.get("sentenceMsg")+question+" "+s‰tzeDeutsch);
			break;
			}
		}
		case Dialoge:{
			gameMode = 2;
			logger.info("Dialoge gamemode");
			res = askUserResponse("And what category do you want to play?");
			
			break;
		}
		default: {
			res = askUserResponse(utterances.get("error"));
			break;
		}
		}
		return res;
	}



	// Achtung, Reihenfolge ist wichtig!
	void recognizeUserIntent(String userRequest) {
		userRequest = userRequest.toLowerCase();
		logger.info("Patternsuche");
		String pattern = "(i want to play )?(on|the )?(restaurant|smalltalk|directions)( difficulty)?( please)?";
		String pattern0 = "(i want to play )?(the )?(sentences|dialogs)( mode)?( please)?";
		String pattern1 = "what is your name";
		String pattern2 = "my name is alexa";
		String pattern3 = "where are you from";
		String pattern4 = "i am from germany";
		String pattern5 = "I never went to Germany before";
		String pattern6 = "What are your hobbies";
		String pattern7 = "My hobbies are reading and dancing";
		String pattern8 = "What do you work";
		String pattern9 = "I work as an assistant";
		String pattern10 = "What is your favourite color";
		String pattern11 = "My favourite color is blue";
		String pattern12 = "What languages do you speak";
		String pattern13 = "I am speaking german and englisch";
		String pattern14 = "Are you single";
		String pattern15 = "I have to go now, Goodbye";
		String pattern16 = "penis";
		String pattern17 = "(my name is) | (i am)"; //--> keinen plan ob das so stimmt
		String pattern18 = "(i am|i come)?(from)";
		String pattern19 = "(my hobbies are | my hobby is | i can | i am interested in)";
		String pattern20 = "(i am a | i am working as | i don't have work | (i am)?jobless)";
		String pattern21 = "(my favourite color is | i like)";
		String pattern22 = "(i (am)? speak(ing)?| i can speak)";
		String pattern23 = "(you too| thanks |thank you)";
		String pattern24 = "\\bno\\b";
		String pattern25 = "\\byes\\b";
		


		Pattern p = Pattern.compile(pattern);
		Matcher m = p.matcher(userRequest);
		Pattern p0 = Pattern.compile(pattern0);
		Matcher m0 = p0.matcher(userRequest);
		Pattern p1 = Pattern.compile(pattern1);
		Matcher m1 = p1.matcher(userRequest);
		Pattern p2 = Pattern.compile(pattern2);
		Matcher m2 = p2.matcher(userRequest);
		Pattern p3 = Pattern.compile(pattern3);
		Matcher m3 = p3.matcher(userRequest);
		Pattern p4 = Pattern.compile(pattern4);
		Matcher m4 = p4.matcher(userRequest);
		Pattern p5 = Pattern.compile(pattern5);
		Matcher m5 = p5.matcher(userRequest);
		Pattern p6 = Pattern.compile(pattern6);
		Matcher m6 = p6.matcher(userRequest);
		Pattern p7 = Pattern.compile(pattern7);
		Matcher m7 = p7.matcher(userRequest);
		Pattern p8 = Pattern.compile(pattern8);
		Matcher m8 = p8.matcher(userRequest);
		Pattern p9 = Pattern.compile(pattern9);
		Matcher m9 = p9.matcher(userRequest);
		Pattern p10 = Pattern.compile(pattern10);
		Matcher m10 = p10.matcher(userRequest);
		Pattern p11 = Pattern.compile(pattern11);
		Matcher m11 = p11.matcher(userRequest);
		Pattern p12 = Pattern.compile(pattern12);
		Matcher m12 = p12.matcher(userRequest);
		Pattern p13 = Pattern.compile(pattern13);
		Matcher m13 = p13.matcher(userRequest);
		Pattern p14 = Pattern.compile(pattern14);
		Matcher m14 = p14.matcher(userRequest);
		Pattern p15 = Pattern.compile(pattern15);
		Matcher m15 = p15.matcher(userRequest);
		Pattern p16 = Pattern.compile(pattern16);
		Matcher m16 = p16.matcher(userRequest);
		Pattern p17 = Pattern.compile(pattern17);
		Matcher m17 = p17.matcher(userRequest);
		Pattern p18 = Pattern.compile(pattern18);
		Matcher m18 = p18.matcher(userRequest);
		Pattern p19 = Pattern.compile(pattern19);
		Matcher m19 = p19.matcher(userRequest);
		Pattern p20 = Pattern.compile(pattern20);
		Matcher m20 = p20.matcher(userRequest);
		Pattern p21 = Pattern.compile(pattern21);
		Matcher m21 = p21.matcher(userRequest);
		Pattern p22 = Pattern.compile(pattern22);
		Matcher m22 = p22.matcher(userRequest);
		Pattern p23 = Pattern.compile(pattern23);
		Matcher m23 = p23.matcher(userRequest);
		Pattern p24 = Pattern.compile(pattern24);
		Matcher m24 = p24.matcher(userRequest);
		Pattern p25 = Pattern.compile(pattern25);
		Matcher m25 = p25.matcher(userRequest);
		
		
		
		if (m.find()) {
			String answer = m.group(3);
			switch (answer) {
			case "restaurant": ourUserIntent = UserIntent.Restaurant; break;
			case "smalltalk": ourUserIntent = UserIntent.Smalltalk; break;
			case "directions": ourUserIntent = UserIntent.Directions; break;
			}
		}
		else if (m0.find()) {
			String answer = m0.group(3);
			switch (answer) {
			case "sentences": ourUserIntent = UserIntent.S‰tze; break;
			case "dialogs": ourUserIntent = UserIntent.Dialoge; break;
			}
		}else if(userRequest.equals(question)) {
			ourUserIntent = UserIntent.Correct;
		}else if (m24.find()) {
			ourUserIntent = UserIntent.No;
		} else if (m25.find()) {
			ourUserIntent = UserIntent.Yes;
		}else if (m16.find()) {
			ourUserIntent = UserIntent.Stop;
		
		}else if (m19.find()||m20.find()||m21.find()||m22.find()||m23.find()||m24.find()||m25.find()) {
			ourUserIntent = UserIntent.Correct;
		}else {
			ourUserIntent = UserIntent.Error;
		}
		logger.info("set ourUserIntent to " +ourUserIntent);
		}
	


	




	@Override
	public void onSessionEnded(SpeechletRequestEnvelope<SessionEndedRequest> requestEnvelope)
	{
		quit = 0;
		count = 1; 
		famCheck = 0;
		isRun = false;
		logger.info("Alexa session ends now");
	}



	/**
	 * Tell the user something - the Alexa session ends after a 'tell'
	 */
	private SpeechletResponse tellUserAndFinish(String text)
	{
		quit = 0;
		count = 1; 
		famCheck = 0;
		isRun = false;
		// Create the plain text output.
		PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
		speech.setText(text);

		return SpeechletResponse.newTellResponse(speech);
	}

	/**
	 * A response to the original input - the session stays alive after an ask request was send.
	 *  have a look on https://developer.amazon.com/de/docs/custom-skills/speech-synthesis-markup-language-ssml-reference.html
	 * @param text
	 * @return
	 */
	private SpeechletResponse askUserResponse(String text)
	{
		SsmlOutputSpeech speech = new SsmlOutputSpeech();
		speech.setSsml("<speak>" + text + "</speak>");

		// reprompt after 8 seconds
		SsmlOutputSpeech repromptSpeech = new SsmlOutputSpeech();
		repromptSpeech.setSsml("<speak><emphasis level=\"strong\">Hey!</emphasis> Bist du noch da?</speak>");

		Reprompt rep = new Reprompt();
		rep.setOutputSpeech(repromptSpeech);
		return SpeechletResponse.newAskResponse(speech, rep);
	}


	/**
	 * formats the text in weird ways
	 * @param text
	 * @param i
	 * @return
	 */
	private SpeechletResponse responseWithFlavour(String text, int i) {

		SsmlOutputSpeech speech = new SsmlOutputSpeech();
		switch(i){ 
		case 0: 
			speech.setSsml("<speak><amazon:effect name=\"whispered\">" + text + "</amazon:effect></speak>");
			break; 
		case 1: 
			speech.setSsml("<speak><emphasis level=\"strong\">" + text + "</emphasis></speak>");
			break; 
		case 2: 
			String firstNoun="erstes Wort buchstabiert";
			String firstN=text.split(" ")[3];
			speech.setSsml("<speak>"+firstNoun+ "<say-as interpret-as=\"spell-out\">"+firstN+"</say-as>"+"</speak>");
			break; 
		case 3: 
			speech.setSsml("<speak><audio src='soundbank://soundlibrary/transportation/amzn_sfx_airplane_takeoff_whoosh_01'/></speak>");
			break;
		default: 
			speech.setSsml("<speak><amazon:effect name=\"whispered\">" + text + "</amazon:effect></speak>");
		} 

		return SpeechletResponse.newTellResponse(speech);
	}

}
