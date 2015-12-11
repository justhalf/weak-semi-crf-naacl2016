package com.statnlp.experiment.smsnp;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Scanner;

import com.statnlp.commons.types.Instance;

public class CRFPPMain {
	
	private static void createTemplate(String template_filename) throws FileNotFoundException, UnsupportedEncodingException{
		PrintStream outstream = new PrintStream(template_filename, "UTF-8");
		String[] templates = new String[]{
				"# Unigram",
//				"U00:%x[-2,0]",
				"U01:%x[-1,0]",
				"U02:%x[0,0]",
//				"U03:%x[1,0]",
//				"U04:%x[2,0]",
//				"U05:%x[-1,0]/%x[0,0]",
//				"U06:%x[0,0]/%x[1,0]",
				"",
				"# Bigram",
				"B"};
		for(String template: templates){
			outstream.println(template);
		}
		outstream.close();
	}
	
	public static void main(String[] args) throws IOException, InterruptedException{
		final String train_filename = "data/SMSNP.conll.train";
		final String test_filename = "data/SMSNP.conll.dev";
		final String model_filename = "experiments/crfpp.500.model";
		final String result_filename = "experiments/crfpp.500.dev.100.result";
		final String template_filename = "experiments/template";
		createTemplate(template_filename);
		double regParam = 4.0;
		
		ProcessBuilder processBuilder = new ProcessBuilder("/usr/local/bin/crf_learn", "-c", regParam+"", template_filename, train_filename, model_filename);
		processBuilder.redirectErrorStream(true);
		final Process learnProcess = processBuilder.start();
		new Thread(new Runnable(){
			private Scanner outputReader = new Scanner(learnProcess.getInputStream());
			public void run(){
				while(outputReader.hasNextLine()){
					System.out.println(outputReader.nextLine());
				}
			}
		}).start();
		new Thread(new Runnable(){
			private Scanner errorReader = new Scanner(learnProcess.getErrorStream());
			public void run(){
				while(errorReader.hasNextLine()){
					System.err.println(errorReader.nextLine());
				}
			}
		}).start();
		learnProcess.waitFor();
		
		processBuilder = new ProcessBuilder("/usr/local/bin/crf_test", "-m", model_filename, test_filename);
		processBuilder.redirectErrorStream(true);
		Process testProcess = processBuilder.start();
		final ArrayList<SMSNPInstance> predictionsList = new ArrayList<SMSNPInstance>();
		Thread outputThread = new Thread(new Runnable(){
			private Scanner outputReader = new Scanner(testProcess.getInputStream());
			public void run(){
				try{
					StringBuilder builder = new StringBuilder();
					PrintStream resultStream = new PrintStream(result_filename, "UTF-8");
					while(!outputReader.hasNextLine()){
						Thread.sleep(100);
					}
					while(outputReader.hasNextLine()){
						String line = outputReader.nextLine();
						builder.append(line+"\n");
						resultStream.println(line);
					}
					resultStream.close();
					BufferedReader reader = new BufferedReader(new StringReader(builder.toString()));
					ArrayList<SMSNPInstance> result = SMSNPUtil.readCoNLLData(reader, true, true);
					predictionsList.addAll(result);
				} catch (IOException e){
					throw new RuntimeException(e);
				} catch (InterruptedException e){
					e.printStackTrace();
				}
			}
		});
		outputThread.start();
		new Thread(new Runnable(){
			private Scanner errorReader = new Scanner(testProcess.getErrorStream());
			public void run(){
				while(errorReader.hasNextLine()){
					System.err.println(errorReader.nextLine());
				}
			}
		}).start();
		testProcess.waitFor();
		outputThread.join();
		
		Instance[] predictionsArray = predictionsList.toArray(new Instance[predictionsList.size()]);
		SMSNPEvaluator.evaluate(predictionsArray, null, 10);
	}
}
