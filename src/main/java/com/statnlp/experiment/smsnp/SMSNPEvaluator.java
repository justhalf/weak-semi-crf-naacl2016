package com.statnlp.experiment.smsnp;

import static com.statnlp.experiment.smsnp.SMSNPIOUtil.print;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import com.statnlp.commons.types.Instance;

public class SMSNPEvaluator {
	
	public static void main(String[] args) throws IOException{
		if(args.length < 1){
			System.err.println("Please provide the result file (four lines per instance: input, gold, prediction, empty line) as first argument");
			System.exit(0);
		}
		SMSNPInstance[] instances = SMSNPIOUtil.readData(args[0], true, true);
		evaluate(instances, null);
	}
	
	public static void evaluate(Instance[] predictions, PrintStream outstream){
		int corr = 0;
		int totalGold = 0;
		int totalPred = 0;
		for(Instance inst: predictions){
			SMSNPInstance instance = (SMSNPInstance)inst;
			print("Input:", outstream, System.out);
			print(instance.input, outstream, System.out);
			print("Gold:", outstream, System.out);
			print(instance.output.toString(), outstream, System.out);
			print("Prediction:", outstream, System.out);
			print(instance.prediction.toString(), outstream, System.out);
			List<Span> goldSpans = instance.output;
			List<Span> predSpans = instance.prediction;
			int curTotalGold = goldSpans.size();
			totalGold += curTotalGold;
			int curTotalPred = predSpans.size();
			totalPred += curTotalPred;
			int curCorr = countOverlaps(goldSpans, predSpans);
			corr += curCorr;
			double precision = 100.0*curCorr/curTotalPred;
			double recall = 100.0*curCorr/curTotalGold;
			double f1 = 2/((1/precision)+(1/recall));
			if(curTotalPred == 0) precision = 0.0;
			if(curTotalGold == 0) recall = 0.0;
			if(curTotalPred == 0 || curTotalGold == 0) f1 = 0.0;
			print(String.format("Correct: %1$3d, Predicted: %2$3d, Gold: %3$3d ", curCorr, curTotalPred, curTotalGold), outstream, System.out);
			print(String.format("Overall P: %#5.2f%%, R: %#5.2f%%, F: %#5.2f%%", precision, recall, f1), outstream, System.out);
			print("", outstream, System.out);
			printScore(new Instance[]{instance}, outstream, System.out);
			print("", outstream, System.out);
		}
		print("", outstream, System.out);
		print("### Overall score ###", outstream, System.out);
		print(String.format("Correct: %1$3d, Predicted: %2$3d, Gold: %3$3d ", corr, totalPred, totalGold), outstream, System.out);
		double precision = 100.0*corr/totalPred;
		double recall = 100.0*corr/totalGold;
		double f1 = 2/((1/precision)+(1/recall));
		if(totalPred == 0) precision = 0.0;
		if(totalGold == 0) recall = 0.0;
		if(totalPred == 0 || totalGold == 0) f1 = 0.0;
		print(String.format("Overall P: %#5.2f%%, R: %#5.2f%%, F: %#5.2f%%", precision, recall, f1), outstream, System.out);
		print("", outstream, System.out);
		printScore(predictions, outstream, System.out);
	}
	
	private static void printScore(Instance[] instances, PrintStream... outstream){
		int size = Label.LABELS.size();
		int[] corrects = new int[size];
		int[] totalGold = new int[size];
		int[] totalPred = new int[size];
		for(Instance inst: instances){
			SMSNPInstance instance = (SMSNPInstance)inst;
			List<Span> predicted = duplicate(instance.getPrediction());
			List<Span> actual = duplicate(instance.getOutput());
			for(Span span: actual){
				if(predicted.contains(span)){
					predicted.remove(span);
					Label label = span.label;
					corrects[label.id] += 1;
					totalPred[label.id] += 1;
				}
				totalGold[span.label.id] += 1;
			}
			for(Span span: predicted){
				totalPred[span.label.id] += 1;
			}
		}
		double avgF1 = 0;
		for(int i=0; i<size; i++){
			double precision = (totalPred[i] == 0) ? 0.0 : 1.0*corrects[i]/totalPred[i];
			double recall = (totalGold[i] == 0) ? 0.0 : 1.0*corrects[i]/totalGold[i];
			double f1 = (precision == 0.0 || recall == 0.0) ? 0.0 : 2/((1/precision)+(1/recall));
			avgF1 += f1;
			print(String.format("%6s: #Corr:%2$3d, #Pred:%3$3d, #Gold:%4$3d, Pr=%5$#5.2f%% Rc=%6$#5.2f%% F1=%7$#5.2f%%", Label.get(i).form, corrects[i], totalPred[i], totalGold[i], precision*100, recall*100, f1*100), outstream);
		}
		print(String.format("Macro average F1: %.2f%%", 100*avgF1/size), outstream);
	}
	
	private static List<Span> duplicate(List<Span> list){
		List<Span> result = new ArrayList<Span>();
		for(Span span: list){
			result.add(span);
		}
		return result;
	}
	
	/**
	 * Count the number of overlaps (common elements) in the given lists.
	 * Duplicate objects are counted as separate objects.
	 * @param list1
	 * @param list2
	 * @return
	 */
	private static int countOverlaps(List<Span> list1, List<Span> list2){
		int result = 0;
		List<Span> copy = new ArrayList<Span>();
		copy.addAll(list2);
		for(Span span: list1){
			if(copy.contains(span)){
				copy.remove(span);
				result += 1;
			}
		}
		return result;
	}

}
