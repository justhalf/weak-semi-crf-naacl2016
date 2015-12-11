package com.statnlp.experiment.smsnp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Scanner;

import com.statnlp.experiment.smsnp.SMSNPTokenizer.TokenizerMethod;

public class CRFPPMain {
	
	private static void createTemplate(String template_filename) throws FileNotFoundException, UnsupportedEncodingException{
		File templateFile = new File(template_filename);
		templateFile.deleteOnExit();
		PrintStream outstream = new PrintStream(templateFile, "UTF-8");
		String[] templates = new String[]{
				"# Unigram",
//				"U00:%x[-2,0]",
				"U01:%x[-1,0]",
				"U02:%x[0,0]",
//				"U03:%x[1,0]",
//				"U04:%x[2,0]",
//				"U05:%x[-1,0]/%x[0,0]",
//				"U06:%x[0,0]/%x[1,0]",
//				"",
//				"# Bigram",
				"B",
				};
		for(String template: templates){
			outstream.println(template);
		}
		outstream.close();
	}
	
	public static void main(String[] args) throws IOException, InterruptedException{
		String timestamp = Calendar.getInstance().getTime().toString();
		String trainFilename = null;
//		String devFilename = "data/SMSNP.dev";
		String testFilename = null;
		String modelFilename = timestamp+"-crfpp.model";
		String resultFilename = timestamp+"-crfpp.result";
		String templateFilename = timestamp+"-crfpp.template";
		TokenizerMethod tokenizerMethod = TokenizerMethod.REGEX;
		double regParam = 4.0;
		boolean useGoldTokenization = false;
		boolean createTemplate = true;
		boolean writeModelText = false;
		
		int argIndex = 0;
		while(argIndex < args.length){
			switch(args[argIndex].substring(1)){
			case "trainPath":
				trainFilename = args[argIndex+1];
				argIndex += 2;
				break;
			case "testPath":
				testFilename = args[argIndex+1];
				argIndex += 2;
				break;
			case "modelPath":
				modelFilename = args[argIndex+1];
				argIndex += 2;
				break;
			case "writeModelText":
				writeModelText = true;
				argIndex += 1;
				break;
			case "resultPath":
				resultFilename = args[argIndex+1];
				argIndex += 2;
				break;
			case "templatePath":
				templateFilename = args[argIndex+1];
				createTemplate = false;
				argIndex += 2;
				break;
			case "tokenizer":
				tokenizerMethod = TokenizerMethod.valueOf(args[argIndex+1].toUpperCase());
				argIndex += 2;
				break;
			case "C":
				regParam = Double.parseDouble(args[argIndex+1]);
				argIndex += 2;
				break;
			case "useGoldTokenization":
				useGoldTokenization = true;
				argIndex += 1;
				break;
			case "h":
			case "help":
				printHelp();
				System.exit(0);
			default:
				throw new IllegalArgumentException("Unrecognized argument: "+args[argIndex]);
			}
		}
		
		if(createTemplate){
			createTemplate(templateFilename);
		}
		
		SMSNPInstance[] trainInstances = SMSNPUtil.readData(trainFilename, true, false);
//		SMSNPInstance[] devInstances = SMSNPUtil.readData(devFilename, false, false);
		SMSNPInstance[] testInstances = SMSNPUtil.readData(testFilename, false, false);

		File tempTrain = File.createTempFile("crfpp-", ".tmp", new File("experiments"));
//		File tempDev = File.createTempFile("crfpp-", ".tmp", new File("experiments"));
		File tempTest = File.createTempFile("crfpp-", ".tmp", new File("experiments"));
		tempTrain.deleteOnExit();
//		tempDev.deleteOnExit();
		tempTest.deleteOnExit();
//		tempTrain = new File("data/SMSNP.conll.regex.train");
//		tempDev = new File("data/SMSNP.conll.regex.dev");
//		tempTest = new File("data/SMSNP.conll.regex.test");
		long start = System.currentTimeMillis();
		System.out.print("Converting input files into CoNLL format using tokenizer "+tokenizerMethod+" (useGold:"+useGoldTokenization+")...");
		PrintStream outstream = new PrintStream(tempTrain);
		for(SMSNPInstance instance: trainInstances){
			outstream.println(instance.toCoNLLString(tokenizerMethod, useGoldTokenization));
		}
		outstream.close();
//		outstream = new PrintStream(tempDev);
//		for(SMSNPInstance instance: devInstances){
//			outstream.println(instance.toCoNLLString(tokenizerMethod, useGoldTokenization));
//		}
//		outstream.close();
		outstream = new PrintStream(tempTest);
		for(SMSNPInstance instance: testInstances){
			outstream.println(instance.toCoNLLString(tokenizerMethod, useGoldTokenization));
		}
		outstream.close();
		long end = System.currentTimeMillis();
		System.out.printf("Done in %.3fs\n", (end-start)/1000.0);
		
		ProcessBuilder processBuilder;
		if(trainFilename != null){
			 processBuilder = new ProcessBuilder("/usr/local/bin/crf_learn", "-c", regParam+"", templateFilename, tempTrain.getAbsolutePath(), modelFilename);
			if(writeModelText){
				processBuilder.command().add(1, "-t");
			}
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
		}
		
		if(testFilename != null){
			processBuilder = new ProcessBuilder("/usr/local/bin/crf_test", "-m", modelFilename, tempTest.getAbsolutePath());
			processBuilder.redirectErrorStream(true);
			final Process testProcess = processBuilder.start();
			final SMSNPInstance[] predictionInstances = new SMSNPInstance[testInstances.length];
			final String finalResultFilename = resultFilename;
			Thread outputThread = new Thread(new Runnable(){
				private Scanner outputReader = new Scanner(testProcess.getInputStream());
				public void run(){
					try{
						StringBuilder builder = new StringBuilder();
						PrintStream resultStream = new PrintStream(finalResultFilename, "UTF-8");
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
						int i=0;
						for(SMSNPInstance instance: result){
							predictionInstances[i] = instance;
							i++;
						}
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
			
			for(int i=0; i<predictionInstances.length; i++){
				SMSNPInstance predInstance = predictionInstances[i];
				SMSNPInstance testInstance = testInstances[i];
				predInstance.input = testInstance.input;
				predInstance.output = testInstance.output;
				predInstance.wordSpans = testInstance.wordSpans;
				predInstance.setPredictionTokenized(predInstance.predictionTokenized);
			}
			SMSNPEvaluator.evaluate(predictionInstances, null, 10);
		}
	}
	
	private static void printHelp(){
		System.out.println("Options:\n"
				+ "-modelPath <modelPath>\n"
				+ "\tSerialize model to <modelPath>\n"
				+ "-writeModelText\n"
				+ "\tAlso write the model in text version for debugging purpose\n"
				+ "-trainPath <trainPath>\n"
				+ "\tTake training file from <trainPath>. If not specified, no training will be performed\n"
				+ "-testPath <testPath>\n"
				+ "\tTake test file from <testPath>. If not specified, no testing will be performed\n"
				+ "-templatePath <templatePath>\n"
				+ "\tTake template file from <templatePath>. If not specified, a default feature set is used\n"
				+ "-resultPath <resultPath>\n"
				+ "\tPrint the result to <resultPath>\n"
				+ "-C <value>\n"
				+ "\tSet the L2 regularization parameter weight to <value>. Default to 4.0\n"
				+ "-tokenizer\n"
				+ "\tThe tokenizer method to be used: whitespace or regex. Default to regex\n"
				+ "-useGoldTokenization\n"
				+ "\tWhether to use gold standard while doing the tokenization\n"
				);
	}
}
