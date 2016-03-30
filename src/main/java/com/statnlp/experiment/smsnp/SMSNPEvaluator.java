package com.statnlp.experiment.smsnp;

import static com.statnlp.experiment.smsnp.SMSNPUtil.print;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import com.statnlp.commons.types.Instance;
import com.statnlp.experiment.smsnp.SMSNPTokenizer.TokenizerMethod;

/**
 * The class to do evaluation on NP-chunking.<br>
 * The main function accepts two result formats:
 * <ol>
 * <li>Based on character offset</li>
 * <li>Based on tokens (in CoNLL format)</li>
 * </ol>
 * This class can also do bootstrap resampling for significance test.
 * Check the help in the main function for more information.
 * @author Aldrian Obaja <aldrianobaja.m@gmail.com>
 *
 */
public class SMSNPEvaluator {
	
	private static class Statistics {
		public int correct=0;
		public int totalPred=0;
		public int totalGold=0;
		
		public void add(Statistics s){
			this.correct += s.correct;
			this.totalPred += s.totalPred;
			this.totalGold += s.totalGold;
		}
		
		public double calculatePrecision(){
			if(totalPred == 0){
				return 0;
			}
			return 1.0*correct/totalPred;
		}
		
		public double calculateRecall(){
			if(totalGold == 0){
				return 0;
			}
			return 1.0*correct/totalGold;
		}
		
		public double calculateF1(){
			double precision = calculatePrecision();
			double recall = calculateRecall();
			double f1 = precision*recall;
			if(f1 == 0){
				return 0;
			}
			f1 = 2*f1/(precision+recall);
			return f1;
		}
		
		public void printScore(PrintStream... outstreams){
			double precision = calculatePrecision();
			double recall = calculateRecall();
			double f1 = calculateF1();
			print(String.format("Correct: %1$3d, Predicted: %2$3d, Gold: %3$3d ", correct, totalPred, totalGold), true, outstreams);
			print(String.format("Overall P: %#5.2f%%, R: %#5.2f%%, F: %#5.2f%%", 100*precision, 100*recall, 100*f1), true, outstreams);
		}
	}
	
	public static void main(String[] args) throws IOException{
		String resultFile = "";
		String testFile = null;
		boolean tokenized = false;
		boolean resample = false;
		boolean wordBased = false;
		int n = 1000;
		int numExamplesPrinted = 10;
		int argIndex = 0;
		while(argIndex < args.length){
			String arg = args[argIndex];
			if(arg.equals("-tokenized")){
				tokenized = true;
			} else if(arg.equals("-resample")){
				resample = true;
			} else if(arg.equals("-n")){
				n = Integer.parseInt(args[argIndex+1]);
				argIndex += 1;
			} else if(arg.equals("-wordBased")){
				wordBased = true;
			} else if(arg.equals("-testFile")){
				testFile = args[argIndex+1];
				argIndex += 1;
			} else if(arg.equals("-numExamplesPrinted")){
				numExamplesPrinted = Integer.parseInt(args[argIndex+1]);
				argIndex += 1;
			} else {
				resultFile = arg;
			}
			argIndex += 1;
		}
		if(resultFile == ""){
			System.err.println("Mandatory argument:\n"
					+ "<result_file>\n"
					+ "\tThe result file (four lines per instance: input, gold, prediction, empty line)\n"
					+ "\tOr in CoNLL format if -tokenized is specified\n"
					+ "Options:\n"
					+ "-tokenized\n"
					+ "\tIf the result file is in CoNLL format\n"
					+ "-testFile\n"
					+ "\tThe original test file, to do character-based evaluation\n"
					+ "-wordBased\n"
					+ "\tIf the evaluation should be done in word-based\n"
					+ "-resample\n"
					+ "\tTo do bootstrap resampling\n"
					+ "-n\n"
					+ "\tThe number of resampling to be done"
					+ "-numExamplesPrinted\n"
					+ "\tThe number of examples to be printed, default to 10");
			System.exit(0);
		}
		SMSNPInstance[] instances = null;
		if(tokenized){
			instances = SMSNPUtil.readCoNLLData(resultFile, true, true);
		} else {
			instances = SMSNPUtil.readData(resultFile, true, true);
		}
		if(testFile != null){
			SMSNPInstance[] origInstances = SMSNPUtil.readData(testFile, true, false);
			for(int i=0; i<origInstances.length; i++){
				origInstances[i].getInputTokenized(TokenizerMethod.REGEX, false, true);
				origInstances[i].getOutputTokenized(TokenizerMethod.REGEX, false, false);
				origInstances[i].setPredictionTokenized(instances[i].predictionTokenized);
				instances[i].input = origInstances[i].input;
				instances[i].output = origInstances[i].output;
				instances[i].wordSpans = origInstances[i].wordSpans;
				instances[i].prediction = origInstances[i].prediction;
			}
		}
		if(resample){
			evaluateBootstrap(instances, null, n, wordBased);
		} else {
			evaluate(instances, null, numExamplesPrinted, wordBased);
		}
	}
	
	public static void evaluateBootstrap(Instance[] predictions, PrintStream outstream, int n, boolean wordBased){
		Random rand = new Random(17);
		double[] precisions = new double[n];
		double[] recalls = new double[n];
		double[] f1s = new double[n];
		int popSize = predictions.length;
		int npID = SpanLabel.get("NP").id;
		for(int i=0; i<n; i++){
			Instance[] sampledPredictions = new Instance[popSize];
			for(int j=0; j<popSize; j++){
				sampledPredictions[j] = predictions[rand.nextInt(popSize)];
			}
			Statistics[] sampledResult = getScore(sampledPredictions, wordBased);
			precisions[i] = sampledResult[npID].calculatePrecision();
			recalls[i] = sampledResult[npID].calculateRecall();
			f1s[i] = sampledResult[npID].calculateF1();
		}
		ConfidenceInterval precisionCI = calculateCI(precisions);
		ConfidenceInterval recallCI = calculateCI(recalls);
		ConfidenceInterval f1CI = calculateCI(f1s);
		System.out.printf("Precision: %.2f(±%.2f)%% Recall: %.2f(±%.2f)%% F1: %.2f(±%.2f)%%\n",
				100*precisionCI.value, 100*precisionCI.margin,
				100*recallCI.value, 100*recallCI.margin,
				100*f1CI.value, 100*f1CI.margin);
	}
	
	private static ConfidenceInterval calculateCI(double[] values){
		double mean = Arrays.stream(values).average().getAsDouble();
		double stdDev = Arrays.stream(values).reduce(0, (double result, double val) -> result+Math.pow(val-mean, 2));
		double margin = 1.96 * stdDev / Math.sqrt(values.length);
		return new ConfidenceInterval(mean, margin);
	}
	
	private static class ConfidenceInterval{
		public double value;
		public double margin;
		public ConfidenceInterval(double value, double margin){
			this.value = value;
			this.margin = margin;
		}
		
//		public boolean overlap(ConfidenceInterval interval){
//			double low = value-margin;
//			double high = value+margin;
//			double iLow = interval.value-interval.margin;
//			double iHigh = interval.value+interval.margin;
//			return (iLow < low && low < iHigh) || (low < iLow && iLow < high);
//		}
	}
	
	public static void evaluate(Instance[] predictions, PrintStream outstream, int printLimit){
		evaluate(predictions, outstream, printLimit, false);
	}
	
	public static void evaluate(Instance[] predictions, PrintStream outstream, int printLimit, boolean wordBased){
		int count = 0;
		PrintStream[] outstreams = new PrintStream[]{outstream, System.out};
		Statistics finalResult = new Statistics();
		for(Instance inst: predictions){
			if(count >= printLimit){
				outstreams = new PrintStream[]{outstream};
			}
			SMSNPInstance instance = (SMSNPInstance)inst;
			print("Input:", true, outstreams);
			print(instance.input, true, outstreams);
			print("Gold:", true, outstreams);
			print(instance.output.toString(), true, outstreams);
			print("Prediction:", true, outstreams);
			print(instance.prediction.toString(), true, outstreams);
			Statistics[] scores = getScore(new Instance[]{instance}, wordBased);
			Statistics overall = sum(scores);
			finalResult.add(overall);
			overall.printScore(outstreams);
			print("", true, outstreams);
			printDetailedScore(scores, outstreams);
			print("", true, outstreams);
			count += 1;
		}
		if(printLimit > 0){
			print("", true, outstream, System.out);
		} else {
			print("", true, outstreams);
		}
		outstreams = new PrintStream[]{outstream, System.out};
		print("### Overall score ###", true, outstream, System.out);
		finalResult.printScore(outstreams);
		print("", true, outstream, System.out);
		Statistics[] scores = getScore(predictions, wordBased);
		printDetailedScore(scores, outstream, System.out);
	}
	
	private static Statistics sum(Statistics[] scores){
		Statistics result = new Statistics();
		for(Statistics score: scores){
			result.add(score);
		}
		return result;
	}
	
	private static Statistics[] getScore(Instance[] instances, boolean wordBased){
		int size = SpanLabel.LABELS.size();
		Statistics[] result = createStatistics(size);
		for(Instance inst: instances){
			SMSNPInstance instance = (SMSNPInstance)inst;
			List<Span> predicted;
			List<Span> actual;
			if(!wordBased){
				predicted = duplicate(instance.getPrediction());
				actual = duplicate(instance.getOutput());
			} else {
				SMSNPInstance tmpInstance = new SMSNPInstance(instance.getInstanceId(), instance.getWeight(), instance.getInputTokenized(), instance.getOutputTokenized());
				tmpInstance.setPredictionTokenized(instance.predictionTokenized);
				predicted = duplicate(tmpInstance.getPrediction());
				actual = duplicate(tmpInstance.getOutput());
			}
			for(Span span: actual){
				if(predicted.contains(span)){
					predicted.remove(span);
					SpanLabel label = span.label;
					result[label.id].correct += 1;
					result[label.id].totalPred += 1;
				}
				result[span.label.id].totalGold += 1;
			}
			for(Span span: predicted){
				result[span.label.id].totalPred += 1;
			}
		}
		return result;
	}
	
	private static Statistics[] createStatistics(int size){
		Statistics[] result = new Statistics[size];
		for(int i=0; i<size; i++){
			result[i] = new Statistics();
		}
		return result;
	}
	
	private static void printDetailedScore(Statistics[] result, PrintStream... outstreams){
		double avgF1 = 0;
		for(int i=0; i<result.length; i++){
			double precision = result[i].calculatePrecision();
			double recall = result[i].calculateRecall();
			double f1 = result[i].calculateF1();
			avgF1 += f1;
			print(String.format("%6s: #Corr:%2$3d, #Pred:%3$3d, #Gold:%4$3d, Pr=%5$#5.2f%% Rc=%6$#5.2f%% F1=%7$#5.2f%%", SpanLabel.get(i).form, result[i].correct, result[i].totalPred, result[i].totalGold, precision*100, recall*100, f1*100), true, outstreams);
		}
		print(String.format("Macro average F1: %.2f%%", 100*avgF1/result.length), true, outstreams);
	}
	
	private static List<Span> duplicate(List<Span> list){
		List<Span> result = new ArrayList<Span>();
		for(Span span: list){
			result.add(span);
		}
		return result;
	}

}
