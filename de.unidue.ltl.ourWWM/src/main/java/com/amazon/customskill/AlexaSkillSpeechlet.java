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
	static int diff;
	static int gameMode;
	

	// Was der User gesagt hat
	public static String userRequest;

	// In welchem Spracherkennerknoten sind wir?
	static enum RecognitionState {Answer, YesNo, Difficulty, Gamemode};
	RecognitionState recState;

	// Was hat der User grade gesagt. (Die "Semantic Tags"aus DialogOS)
	static enum UserIntent {Yes, No, A, B, C, D, Publikum, FiftyFifty, Error, Leicht, Schwer, Dialoge, S‰tze};
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
		recState = RecognitionState.Difficulty;
		return askUserResponse(utterances.get("welcomeMsg"));
	}

	// Ziehe eine Frage aus der Datenbank, abh√§ngig von der aktuellen Gewinnsumme, setze question und correctAnswer
	private void selectQuestion() {
		try {
			con = DBConnection.getConnection1();
			Statement stmt = con.createStatement();
			ResultSet rs = stmt
					.executeQuery("SELECT * FROM S‰tzeLeicht WHERE Englisch=" +  "");
			question = rs.getString("S‰tzeLeicht");
			ResultSet rs1 = stmt
					.executeQuery("SELECT * FROM S‰tzeLeicht WHERE Deutsch=" +  "");
			s‰tzeDeutsch = rs1.getString("S‰tzeLeicht");
			correctAnswer = rs.getString("Englisch");
			logger.info("Extracted question from database "+ question);
		} catch (Exception e){
			e.printStackTrace();
		}
	}
		


	// Hier gehen wir rein, wenn der User etwas gesagt hat
	// Wir speichern den String in userRequest, je nach recognition State reagiert das System unterschiedlich
	@Override
	public SpeechletResponse onIntent(SpeechletRequestEnvelope<IntentRequest> requestEnvelope)
	{
		IntentRequest request = requestEnvelope.getRequest();
		Intent intent = request.getIntent();
		userRequest = intent.getSlot("anything").getValue();
		logger.info("Received following text: [" + userRequest + "]");
		logger.info("recState is [" + recState + "]");
		SpeechletResponse resp = null;
		switch (recState) {
		case Answer: resp = evaluateAnswer(userRequest); break;
		case YesNo: resp = evaluateYesNo(userRequest); recState = RecognitionState.Answer; break;
		case Difficulty: resp = evaluateDiff(userRequest); recState = RecognitionState.Gamemode; break;
		case Gamemode: resp = evaluateGamemode(userRequest); recState = RecognitionState.Answer; break;
		default: resp = tellUserAndFinish("Erkannter Text: " + userRequest);
		}   
		return resp;
	}

	// Ja/Nein-Fragen kommen genau dann vor, wenn wir wissen wollen, ob der User weitermachen will.
	// Wenn Ja, stelle die n√§chste Frage
	// Wenn Nein, nenne die Gewinnsumme und verabschiede den user
	private SpeechletResponse evaluateYesNo(String userRequest) {
		SpeechletResponse res = null;
		recognizeUserIntent(userRequest);
		switch (ourUserIntent) {
		case Yes: {
			selectQuestion();
			res = askUserResponse(question+""+s‰tzeDeutsch); break;
		} case No: {
			res = tellUserAndFinish(utterances.get("sumMsg")+" "+utterances.get("goodbyeMsg")); break;
		} default: {
			res = askUserResponse(utterances.get(""));
		}
		}
		return res;
	}


	private SpeechletResponse evaluateAnswer(String userRequest) {
		SpeechletResponse res = null;
		recognizeUserIntent(userRequest);
		//switch (ourUserIntent) {
		//default :{
			//if (ourUserIntent.equals(UserIntent.A) 
				//	|| ourUserIntent.equals(UserIntent.B)
					//|| ourUserIntent.equals(UserIntent.C)
					//|| ourUserIntent.equals(UserIntent.D)	
					//) {
				//logger.info("User answer ="+ ourUserIntent.name().toLowerCase()+ "/correct answer="+correctAnswer);
				//if (ourUserIntent.name().toLowerCase().equals(correctAnswer)) {
					//logger.info("User answer recognized as correct.");
					//if
						//res = tellUserAndFinish(utterances.get("correctMsg")+" "+utterances.get("congratsMsg")+" "+utterances.get("goodbyeMsg"));
					//else
						//recState = RecognitionState.YesNo;
						//res = askUserResponse(utterances.get("correctMsg")+" "+utterances.get("continueMsg"));
					
			//	} else {
				//	res = tellUserAndFinish(utterances.get("wrongMsg")+ " "+ utterances.get("sumMsg")  + " " + utterances.get("goodbyeMsg"));
				//}
			//} else {
				//res = askUserResponse(utterances.get("errorAnswerMsg"));
			//}
		//}
		//}
		if(userRequest==correctAnswer) {
			logger.info("User answer recognized as correct.");
			recState = RecognitionState.YesNo;
			res = askUserResponse(utterances.get("correctMsg")+" "+utterances.get("continueMsg"));
		}
		else {
			res = askUserResponse(utterances.get("wrongMsg")+""+question+""+s‰tzeDeutsch);
		}
		
		return res;
	}
	private SpeechletResponse evaluateDiff(String userRequest) {
		SpeechletResponse res = null;
		recognizeUserIntent(userRequest);
		switch (ourUserIntent) {
		case Leicht:{
			diff = 1;
			res = askUserResponse(utterances.get("gamemodeMsg"));
		}; break;
		case Schwer:{
			diff = 2;
			res = askUserResponse(utterances.get("gamemodeMsg"));
		}; break;
		default: {
			res = askUserResponse(utterances.get(""));
		}
		}
		return res;
	}
	
	private SpeechletResponse evaluateGamemode(String userRequest) {
		SpeechletResponse res = null;
		recognizeUserIntent(userRequest);
		switch (ourUserIntent) {
		case S‰tze:{
			gameMode = 1;
			selectQuestion();
			res = askUserResponse(question+"total"+s‰tzeDeutsch);
		}; break;
		case Dialoge:{
			gameMode = 2;
			selectQuestion();
			res = askUserResponse(question+""+s‰tzeDeutsch);
		}; break;
		default: {
			res = askUserResponse(utterances.get(""));
		}
		}
		return res;
	}



	// Achtung, Reihenfolge ist wichtig!
	void recognizeUserIntent(String userRequest) {
		userRequest = userRequest.toLowerCase();
		String pattern = "(i want to play )?(on|the )?(easy|difficult)( difficulty)?( please)?";
		String pattern0 = "(i want to play )?(the )?(sentences|dialogues)( mode)?( please)?";
		String pattern1 = "What is your Name";
		String pattern2 = "My name is Alexa";
		String pattern3 = "Where are you from";
		String pattern4 = "I am from Germany";
		String pattern5 = "I never went to Germany before";
		String pattern6 = "What are your hobbies";
		String pattern7 = "My hobbies are reading and dancing";
		String pattern8 = "Whta do you work";
		String pattern9 = "I work as an assistant";
		String pattern10 = "What is your favourite color";
		String pattern11 = "My favourite color is blue";
		String pattern12 = "What languages do you speak";
		String pattern13 = "I am speaking german and englisch";
		String pattern14 = "Are you sIngle";
		String pattern15 = "I have to go now, Goodbye";
		String pattern16 = "\\bno\\b";
		String pattern17 = "\\byes\\b";


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
		
		if (m.find()) {
			String answer = m.group(3);
			switch (answer) {
			case "easy": ourUserIntent = UserIntent.Leicht; break;
			case "difficult": ourUserIntent = UserIntent.Schwer; break;
			}
		}
		else if (m0.find()) {
			String answer = m0.group(3);
			switch (answer) {
			case "sentences": ourUserIntent = UserIntent.S‰tze; break;
			case "dialogues": ourUserIntent = UserIntent.Dialoge; break;
			}
		}
		else if (m1.find()) {
			String answer = m1.group(3);
			switch (answer) {
			case "a": ourUserIntent = UserIntent.A; break;
			case "b": ourUserIntent = UserIntent.B; break;
			case "c": ourUserIntent = UserIntent.C; break;
			case "d": ourUserIntent = UserIntent.D; break;
			}
		} else if (m2.find()) {
			ourUserIntent = UserIntent.Publikum;
		} else if (m3.find()) {
			ourUserIntent = UserIntent.FiftyFifty;
		} else if (m4.find()) {
			ourUserIntent = UserIntent.No;
		} else if (m5.find()) {
			ourUserIntent = UserIntent.Yes;
		} else {
			ourUserIntent = UserIntent.Error;
		}
		logger.info("set ourUserIntent to " +ourUserIntent);
	}


	




	@Override
	public void onSessionEnded(SpeechletRequestEnvelope<SessionEndedRequest> requestEnvelope)
	{
		logger.info("Alexa session ends now");
	}



	/**
	 * Tell the user something - the Alexa session ends after a 'tell'
	 */
	private SpeechletResponse tellUserAndFinish(String text)
	{
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
