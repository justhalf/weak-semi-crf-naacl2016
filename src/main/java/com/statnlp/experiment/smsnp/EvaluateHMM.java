package com.statnlp.experiment.smsnp;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;

import com.statnlp.experiment.smsnp.SMSNPTokenizer.TokenizerMethod;

/**
 * The code that was used to evaluate results from a HMM system<br>
 * This was discontinued and was not included as baseline
 * @author Aldrian Obaja <aldrianobaja.m@gmail.com>
 *
 */
public class EvaluateHMM {
	
	public static void main(String[] args) throws IOException, InterruptedException{
		String testFilename = "data/SMSNP.test";
		String testResultFile = "experiments/HMM-result-test";
		int numExamplesPrinted = 10;
		
		PrintStream logstream = null;
		
		SMSNPInstance[] testInstances = SMSNPUtil.readData(testFilename, false, false);
		for(SMSNPInstance instance: testInstances){
			instance.getInputTokenized(TokenizerMethod.REGEX, false, true);
			instance.getOutputTokenized(TokenizerMethod.REGEX, false, true);
		}
		final SMSNPInstance[] predictionInstances = new SMSNPInstance[testInstances.length];
		BufferedReader reader = new BufferedReader(new FileReader(testResultFile));
		ArrayList<SMSNPInstance> result = SMSNPUtil.readCoNLLData(reader, true, true);
		int idx=0;
		for(SMSNPInstance instance: result){
			predictionInstances[idx] = instance;
			idx++;
		}
		
		for(int i=0; i<predictionInstances.length; i++){
			SMSNPInstance predInstance = predictionInstances[i];
			SMSNPInstance testInstance = testInstances[i];
			predInstance.input = testInstance.input;
			predInstance.output = testInstance.output;
			predInstance.wordSpans = testInstance.wordSpans;
			predInstance.setPredictionTokenized(predInstance.predictionTokenized);
		}
		SMSNPEvaluator.evaluate(predictionInstances, logstream, numExamplesPrinted);
	}
}
