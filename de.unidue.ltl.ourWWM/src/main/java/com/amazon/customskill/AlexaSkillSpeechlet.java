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
	// Initialisiert den Logger. Am besten möglichst of Logmeldungen erstellen, hilft hinterher bei der Fehlersuche!
	static Logger logger = LoggerFactory.getLogger(AlexaSkillSpeechlet.class);

	// Variablen, die wir auch schon in DialogOS hatten
	static String answerOption1 = "";
	static String answerOption2 = "";
	static String question = "";
	static String correctAnswer = "";
	static String s�tzeDeutsch = "";
	static int cat=0; 
	static int gameMode=0;
	static int count = 1;
	static int countD = 2;
	static int quit = 0; //0=weiter, 1=zur�ck ins men� oder beenden
	private static boolean isRun = false;
	static int famCheck = 0;
	static boolean finished = false;
	static ArrayList<String> output = new ArrayList<String>();

	// Was der User gesagt hat
	public static String userRequest;

	// In welchem Spracherkennerknoten sind wir?
	static enum RecognitionState {Answer, YesNo, Category, Gamemode};
	RecognitionState recState;

	// Was hat der User grade gesagt. (Die "Semantic Tags"aus DialogOS)
	static enum UserIntent {Yes, No, Correct, Wrong, Finished, Error, Restaurant, Smalltalk, Directions, Dialoge, S�tze, Stop};
	UserIntent ourUserIntent;

	// Was das System sagen kann
	Map<String, String> utterances;

	// Baut die Systemäußerung zusammen
	String buildString(String msg, String replacement1, String replacement2) {
		return msg.replace("{replacement}", replacement1).replace("{replacement2}", replacement2);
	}

	// Liest am Anfang alle Systemäußerungen aus Datei ein
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

	// Datenbank für Quizfragen
	static String DBName = "AlexaBeispiel.db";
	static String DBName1 = "S�tze.db";
	static String DB_Dialoge = "Dialoge.db";
	private static Connection con = null;


	// Vorgegebene Methode wird am Anfang einmal ausgeführt, wenn ein neuer Dialog startet:
	// * lies Nutzeräußerungen ein
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
		output.add(utterances.get("famCheck")); 
		return askUserResponse(output);	
		
	}

	// Ziehe eine Frage aus der Datenbank.
	private void selectQuestion() {
		//f�r game mode Sentences
		if(gameMode==1) {
		isFinished(count);
		try {
			con = DBConnection.getConnection1();
			Statement stmt = con.createStatement();
			logger.info("Count: "+count);
			ResultSet rs = stmt.executeQuery("SELECT * FROM S�tzeLeicht ORDER BY random() LIMIT 1");
			logger.info("Count: "+count);
			s�tzeDeutsch = rs.getString("Deutsch");
			question = rs.getString("Englisch");
			logger.info("Extracted question from database: "+ question);
			logger.info("Extracted german sentence: "+ s�tzeDeutsch);
		} catch (Exception e){
			logger.info("Exception");
			e.printStackTrace();}
		}
		
		else if(gameMode==2){
		//f�r dialoge game mode
		try {
			//small conversation
			if (cat==1) {
				logger.info("Count: "+countD);
				isFinished(countD);
				logger.info("Finished: "+finished);
				con = DBConnection.getConnection2();
				Statement stmt = con.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT * FROM DialogeLeicht");
					for (int i=1; i<countD;i++) {
					rs.next();
					logger.info("for schleife");
				}
				question = rs.getString("Alexa");
		
			}
			else if (cat==2) {
				//restaurant
				isFinished(countD);
				logger.info("Finished: "+finished);
				con = DBConnection.getConnection2();
				Statement stmt = con.createStatement();
				ResultSet rs1 = stmt.executeQuery("SELECT * FROM DialogeRestaurant");
				logger.info("Count: "+countD);
				for (int i=1; i<countD;i++) {
					rs1.next();
				}
				question = rs1.getString("Alexa");
				//rs1.next();
			}
			else if (cat==3) {
				//directions
				isFinished(countD);
				logger.info("Finished: "+finished);
				con = DBConnection.getConnection2();
				Statement stmt = con.createStatement();
				ResultSet rs2 = stmt.executeQuery("SELECT * FROM DialogeWegbeschreibung");
				logger.info("Count: "+countD);
				for (int i=1; i<countD;i++) {
					rs2.next();
				}
				question = rs2.getString("Alexa");
		
			}
			else {
				logger.info("Error in Category selection");
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
		case Category: resp = evaluateCategory(userRequest);break;
		case Gamemode: resp = evaluateGamemode(userRequest);break;
		default: resp = tellUserAndFinish("Erkannter Text: " + userRequest);break;
		}   
		return resp;
	}

	// Ja/Nein-Fragen kommen genau dann vor, wenn wir wissen wollen, ob der User weitermachen will, wenn er gefragt wird ob er die Anwendung beenden will oder ob er bereits vertraut mit ihr ist
	// Wenn Ja, stelle die nächste Frage
	// Wenn Nein, verabschiede den user
	private SpeechletResponse evaluateYesNo(String userRequest) {
		gameMode=0;
		SpeechletResponse res = null;
		recognizeUserIntent(userRequest);
		output.clear();
		switch (ourUserIntent) {
		case Yes: {
			//User beantwortet Vertrautheit mit Ja 
			if(isRun==false) {
				isRun=true;
			    famCheck=1; // means familiar user
			    recState = RecognitionState.Gamemode;
			    output.add(utterances.get("gamemodeMsg"));
			    res = askUserResponse(output);
			    logger.info("familiarUserMsg");
			    break;
			}
			//falls er auf die frage, ob er die Anwendung beenden will, ja antwortet
			else if(quit==1) {
				res = tellUserAndFinish(utterances.get("goodbyeMsg"));
				break;
			}
			else {
				//wenn er mit den s�tzen fortfahren m�chte
				recState = RecognitionState.Answer;
				logger.info("rec State=Answer");
				selectQuestion();
				output.add(question+".");
				output.add(s�tzeDeutsch);
				res = askUserResponse(output);
				break;
			}
		} case No: {
			//nicht vertraut
			if(!isRun) {
				isRun=true;
				 recState = RecognitionState.Gamemode;
				 output.add(utterances.get("welcomeMsg"));
				 res = askUserResponse(output);
				 logger.info("welcomeMsg");
				 break;
			}
			//m�chte anwendung nicht beenden
			else {
				quit=0;
				count=1;
				countD=1;
				output.clear();
				recState = RecognitionState.Gamemode;
				output.add("Ok, what Gamemode do you want to try?");
				res = askUserResponse(output); 
				break;
			}
			
		} default: {
			output.add("Sorry, please say yes or no.");
			res = askUserResponse(output); 
			break;
		}
		}
		return res;
	}


	private SpeechletResponse evaluateAnswer(String userRequest) {
		SpeechletResponse res = null;
		recognizeUserIntent(userRequest);
		output.clear();
	
		if(gameMode==1) {
			//S�tze
			switch(ourUserIntent) {
			case Correct:{
				logger.info("User answer recognized as correct.");
				//nach 3 korrekten antworten wir nutzer gefragt ob er weitermachen m�chte
				if(count % 3 == 0) {
					count+=1;
					recState = RecognitionState.YesNo;
					output.add(utterances.get("correctMsg")+",");
					output.add(utterances.get("continueMsg"));
					res = askUserResponse(output);
					break;
				}
				else {
					count+=1;
					recState = RecognitionState.Answer;
					selectQuestion();
					output.add(utterances.get("correctMsg")+",");
					output.add(question+".");
					output.add(s�tzeDeutsch);
					res = askUserResponse(output);
					break;
				}
			}
			case Error:{
				logger.info("s�tze error");
				output.add(utterances.get("errorAnswerMsg")+",");
				output.add(question+".");
				output.add(s�tzeDeutsch);
				res = askUserResponse(output);
				break;
			}
			case Stop:{
				//falls nutzer mittendrin aufh�ren m�chte 
				quit = 1;
				recState = RecognitionState.YesNo;
				output.add("Do you want to quit?");
				res = askUserResponse(output);
				break;
			}
			case Finished:{
				//nutzer hat alle s�tze wiederholt
				quit = 1;
				countD=1;
				count=1;
				cat=0;
				recState = RecognitionState.YesNo;
				output.add(utterances.get("sentencesFinishing"));
				res = askUserResponse(output);
				break;
			}
			default:{
				//quasi error
				output.add(utterances.get("errorAnswerMsg")+",");
				output.add(question+".");
				output.add(s�tzeDeutsch);
				res = askUserResponse(output);
				break;
			}
			}
		}
		//dialoge
		//beinahe gleicher aufbau wie oben, nur ohne counter
		else if(gameMode==2) {
			logger.info("User Intent: "+ourUserIntent);
			switch(ourUserIntent) {
			case Correct:{
				logger.info("User answer recognized as correct.");
				countD+=1;
				recState = RecognitionState.Answer;
				selectQuestion();
				output.add(question);
				res = askUserResponse(output);
				break;
			}
			case Error:{
				output.add(utterances.get("wrongMsg")+",");
				output.add(question);
				res = askUserResponse(output);
				break;
			}
			case Stop:{
				quit = 1;
				cat=0;
				recState = RecognitionState.YesNo;
				output.add("Do you want to quit?");
				res = askUserResponse(output);
				break;
			}
			case Finished:{
				quit = 1;
				countD=1;
				count=1;
				cat=0;
				recState = RecognitionState.YesNo;
				output.add(utterances.get("dialogsFinishing"));
				res = askUserResponse(output);
				break;
			}
			default:{
				output.add(utterances.get("errorAnswerMsg"));
				res = askUserResponse(output);
				break;
			}
			}
		}
		else {
			output.add(utterances.get("errorAnswerMsg"));
			res = askUserResponse(output);
		}
		
		return res;
	}
	//dialoge auswahl welche category
	private SpeechletResponse evaluateCategory(String userRequest) {
		SpeechletResponse res = null;
		recognizeUserIntent(userRequest);
		output.clear();
		switch (ourUserIntent) {
		//short conversations
		case Smalltalk:{
			recState = RecognitionState.Answer;
			cat = 1;
			selectQuestion();
			output.add(question);
			res = askUserResponse(output);
			break;
		}
		case Restaurant:{
			recState = RecognitionState.Answer;
			cat = 2;
			selectQuestion();
			output.add(question);
			res = askUserResponse(output);
			break;
		}
		case Directions:{
			recState = RecognitionState.Answer;
			cat = 3;
			selectQuestion();
			output.add(question);
			res = askUserResponse(output);
			break;
		}
		default: {
			output.add("Sorry, please say conversation, restaurant or directions");
			res = askUserResponse(output);
			break;
		}
		}
		return res;
	}
	//auswahl des game modes
	private SpeechletResponse evaluateGamemode(String userRequest) {
		SpeechletResponse res = null;
		recognizeUserIntent(userRequest);
		logger.info("Gamemode");
		output.clear();
		switch (ourUserIntent) {
		case S�tze:{
			//wird gestartet
			gameMode = 1;
			selectQuestion();
			recState = RecognitionState.Answer;
			if(famCheck==1) {
				output.add(utterances.get("famSentenceMsg"));
				output.add(question+".");
				output.add(s�tzeDeutsch);
				res = askUserResponse(output);
				break;
			}else {
				output.add(utterances.get("sentenceMsg")+",");
				output.add(question+".");
				output.add(s�tzeDeutsch);
				res = askUserResponse(output);
				break;
			}
		}
		case Dialoge:{
			//wird gestartet
			gameMode = 2;
			logger.info("Dialoge gamemode");
			recState = RecognitionState.Category;
			if(famCheck==1) {
				output.add(utterances.get("familiarUserDialogsMsg"));
				res = askUserResponse(output);
				break;
			}else {
				output.add(utterances.get("dialogsCategoryMsg"));
				res = askUserResponse(output);
				break;
			}
		}
		default: {
			output.add("Sorry, please say sentences or dialogs");
			res = askUserResponse(output);
			break;
		}
		}
		return res;
	}
	//testet ob man das ende der Datenbank erreicht hat
	private boolean isFinished(int count) {
		if(cat==1) {//small conversation
			if(count>8) {
				finished = true;
			}
		}else if(cat==2) {//restaurant
			if(count>10) {
				finished = true;
				
			}
		}else if(cat==3) {//directions
			if(count>4) {
				finished = true;
			}
		}else {
			if(count>33) {//s�tze
				finished = true;
			}
		}
		return finished;
	}



	// Achtung, Reihenfolge ist wichtig!
	void recognizeUserIntent(String userRequest) {
		userRequest = userRequest.toLowerCase();
		logger.info("Patternsuche");
		String pattern = "go|turn|(i do not know)|((sorry )?i am not from here)|it(�s|is)"; //address
		String pattern0 = "(i (can )?speak |i am speaking )?((german|english|french|spanish|turkish|arabic)+( and )?(german|english|french|spanish|turkish|arabic)?)"; //which languages
		String pattern1 = "(i (would like)? (want)?)? | ([a-z]+)"; //drink
		String pattern2 = "(my favorite (color|one) is )?(blue|yellow|green|red|violet|black|white|orange|brown|gray|pink)"; //fav color
		String pattern3 = "(i want to play )?(on|the )?(restaurant|small conversation|directions)( difficulty)?( please)?";
		String pattern4 = "((my (hobbies are |hobby is )) | (i (like |love )) | (i am a fan of ))"; //hobbies
		String pattern5 = "(oh |well |ehm )?(no |yes )(i have|i have not|i don�t have (any)?)?( hobbies)?"; //hobbies
		String pattern6 = "(yes |no )?((i am (not)?single )|(i have a (girlfriend|boyfriend|wife|husband)))"; // are you single
		String pattern7 = "i (don�t|do not) (want to|wanna) (tell|say) (you )?(this|that)?";
		String pattern8 = "(i am |i work as |i am working as )?(a student|an assistant)"; //work
		String pattern9 = "(i want to play )?(the )?(sentences|dialogs)( mode)?( please)?";
		String pattern10 = "(oh |well )?(I am from )?(germany|egypt)( how about you| and you)?"; // where are you from
		String pattern11 = "(hello |hi )?(my name is|i am)"; // name
		String pattern12 = "(No )?(i don`t ((have a job)|work)|i am jobless)";
		String pattern13 = "i don�t have ((any( favorite color)?)|one)"; //color
		String pattern14 = "(good but )?i want to eat"; //what do you want to eat
		String pattern15 = "(i want to pay )?(cash|card)"; //paying
		String pattern16 = "(hello|hi)( my name is)?"; //Restaurant intro reply
		String pattern17 = "i am alone|(we are )?\\d"; // how many are you
		String pattern18 = "you too|thanks|thank you";
		String pattern19 = "\\byes\\b|\\bno\\b|sure";
		String pattern20 = "okay|ok";
		String pattern21 = "i want to do something else";
		String pattern22 = "(good )?bye";
		String pattern23 = "\\byes\\b";
		String pattern24 = "\\bno\\b";

		


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
		
		
		
		if (m3.find()) {
			String answer = m3.group(3);
			switch (answer) {
			case "restaurant": ourUserIntent = UserIntent.Restaurant; break;
			case "small conversation": ourUserIntent = UserIntent.Smalltalk; break;
			case "directions": ourUserIntent = UserIntent.Directions; break;
			}
		}
		else if (m9.find()) {
			String answer = m9.group(3);
			switch (answer) {
			case "sentences": ourUserIntent = UserIntent.S�tze; break;
			case "dialogs": ourUserIntent = UserIntent.Dialoge; break;
			}
		}else if(gameMode==1) {
			if(userRequest.equals(question)) {
				ourUserIntent = UserIntent.Correct;
				if(finished==true) {
					ourUserIntent = UserIntent.Finished;
				}
			}else if (m21.find()) {
				ourUserIntent = UserIntent.Stop;
			}else {
				ourUserIntent = UserIntent.Error;
			}
		
		}else if(cat==0){	
			if (m24.find()) {
				ourUserIntent = UserIntent.No;
			}else if (m23.find()) {
				ourUserIntent = UserIntent.Yes;
			}
		}else if (m21.find()) {
			ourUserIntent = UserIntent.Stop;
		}else if(cat==1){
			logger.info("Dialoge Matcher Conversation");
			if (m0.find()|m2.find()|m4.find()|m5.find()|m6.find()|m7.find()|m8.find()|m10.find()|
				m11.find()|m12.find()|m13.find()|m18.find()|m19.find()|m20.find()|m22.find()) {
				logger.info("match");
				ourUserIntent = UserIntent.Correct;
				if(finished==true) {
					ourUserIntent = UserIntent.Finished;
				}
			}

		}else if(cat==2){
			logger.info("Dialoge Matcher Restaurant");
			if (m1.find()|m14.find()|m15.find()|m16.find()|m17.find()|m18.find()|m19.find()|m20.find()|m22.find()) {
				ourUserIntent = UserIntent.Correct;
				if(finished==true) {
					ourUserIntent = UserIntent.Finished;
				}
			}

		}else if(cat==3){
			logger.info("Dialoge Matcher Directions");
			if (m.find()|m18.find()|m19.find()|m20.find()|m22.find()) {
				logger.info("match");
				ourUserIntent = UserIntent.Correct;
				if(finished==true) {
					ourUserIntent = UserIntent.Finished;
				}
				
			}
		}else {
			logger.info("Error Pattern");
			ourUserIntent = UserIntent.Error;
		}
		logger.info("set ourUserIntent to " +ourUserIntent);
		}
	


	




	@Override
	public void onSessionEnded(SpeechletRequestEnvelope<SessionEndedRequest> requestEnvelope)
	{
		//notwendig damit sich alles zur�cksetzt
		quit = 0;
		count = 1; 
		countD = 2;
		famCheck = 0;
		isRun = false;
		finished = false;
		cat=0;
		output.clear();
		logger.info("Alexa session ends now");
	}



	/**
	 * Tell the user something - the Alexa session ends after a 'tell'
	 */
	private SpeechletResponse tellUserAndFinish(String text)
	{
		//doppelt h�lt besser
		quit = 0;
		count = 1; 
		countD = 2;
		famCheck = 0;
		isRun = false;
		finished = false;
		cat=0;
		output.clear();
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
	private SpeechletResponse askUserResponse(ArrayList<String> text)
	{
		SsmlOutputSpeech speech = new SsmlOutputSpeech();
		if(text.contains(s�tzeDeutsch)) {
			text.remove(s�tzeDeutsch);
			//damit der deutsche teil auch auf deutsch ausgegeben wird
			speech.setSsml("<speak>"+text.toString()+"<voice name=\"Vicki\"><lang xml:lang=\"de-DE\">"+s�tzeDeutsch+"</lang></voice></speak>");
		}
		else {
			speech.setSsml("<speak>" + text.toString() + "</speak>");
		}
		
		// reprompt after 8 seconds
		SsmlOutputSpeech repromptSpeech = new SsmlOutputSpeech();
		repromptSpeech.setSsml("<speak><emphasis level=\"strong\">Hello.</emphasis> please say something.</speak>");

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
}
