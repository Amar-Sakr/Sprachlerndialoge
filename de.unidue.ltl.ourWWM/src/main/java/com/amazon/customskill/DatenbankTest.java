package com.amazon.customskill;

import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class DatenbankTest {


	private static Connection con = null;
	private static Statement stmt = null;


	public static void main (String[] args) throws URISyntaxException {

		System.out.println(DatenbankTest.class.getClassLoader().getResource("utterances.txt").toURI());
		
		try {
			con = DBConnection.getConnection();
			stmt = con.createStatement();
			ResultSet rs = stmt
					.executeQuery("SELECT * FROM SätzeLeicht WHERE Englisch=");
			String question = rs.getString("SätzeLeicht");
			String correctAnswer = rs.getString("Englisch");
			System.out.println(question+"\t"+correctAnswer);
		} catch (Exception e){
			e.printStackTrace();
		}
	}


}
/// "SELECT * FROM Fragen WHERE Gewinnsumme=" + 125000 + "");