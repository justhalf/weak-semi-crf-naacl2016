package com.statnlp.experiment.smsnp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Scanner;

import com.statnlp.commons.types.Instance;

public class CRFPPMain {
	
	public static void main(String[] args) throws IOException, InterruptedException{
		final String train_filename = "data/SMSNP.conll.train.500";
		final String test_filename = "data/SMSNP.conll.dev.100";
		final String model_filename = "experiments/crfpp.500.model";
		
		ProcessBuilder processBuilder = new ProcessBuilder("/usr/local/bin/crf_learn", "-c", "4.0", "experiments/template", train_filename, model_filename);
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
		new Thread(new Runnable(){
			private BufferedReader outputReader = new BufferedReader(new InputStreamReader(testProcess.getInputStream()));
			public void run(){
				try{
					ArrayList<SMSNPInstance> result = SMSNPIOUtil.readCoNLLData(outputReader, true, true);
					predictionsList.addAll(result);
				} catch (IOException e){
					throw new RuntimeException(e);
				}
			}
		}).start();
		new Thread(new Runnable(){
			private Scanner errorReader = new Scanner(testProcess.getErrorStream());
			public void run(){
				while(errorReader.hasNextLine()){
					System.err.println(errorReader.nextLine());
				}
			}
		}).start();
		testProcess.waitFor();
		
		Instance[] predictionsArray = predictionsList.toArray(new Instance[predictionsList.size()]);
		SMSNPEvaluator.evaluate(predictionsArray, null);
	}
}
