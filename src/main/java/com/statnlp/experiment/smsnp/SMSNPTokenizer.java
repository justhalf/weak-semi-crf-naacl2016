package com.statnlp.experiment.smsnp;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import edu.stanford.nlp.util.StringUtils;
import justhalf.nlp.tokenizer.Tokenizer;
import justhalf.nlp.tokenizer.WhitespaceTokenizer;

public class SMSNPTokenizer implements Serializable {
	
	private static final long serialVersionUID = -2097450154196277909L;

	public enum TokenizerMethod {
		WHITESPACE,
		REGEX,
	}
	
	private enum Argument{
		CONVERT_ALL_SMSNP_DATA(0,
				"Tokenize the three files SMSNP.train, SMSNP.dev, and SMSNP.test using various\n"
						+ "tokenization methods",
				"convertAllSMSNPData"),
		INPUT(1,
				"The input file to be tokenized. Must also specify -output and -tokenizer",
				"input",
				"inputFile"),
		OUTPUT(1,
				"The output file. Must also specify -input and -tokenizer",
				"output",
				"outputFile"),
		TOKENIZER(1,
				"The tokenizer to be used, either \"regex\" or \"whitespace\"",
				"tokenizer",
				"tokenizerMethod"),
		HELP(0,
				"Print this help message",
				"h,help"),
		;
		
		final private int numArgs;
		final private String[] argNames;
		final private String[] names;
		final private String help;
		private Argument(int numArgs, String help, String names, String... argNames){
			this.numArgs = numArgs;
			this.argNames = argNames;
			this.names = names.split(",");
			this.help = help;
		}
		
		/**
		 * Return the Argument which has the specified name
		 * @param name
		 * @return
		 */
		public static Argument argWithName(String name){
			for(Argument argument: Argument.values()){
				for(String argName: argument.names){
					if(argName.equals(name)){
						return argument;
					}
				}
			}
			throw new IllegalArgumentException("Unrecognized argument: "+name);
		}
		
		/**
		 * Print help message
		 */
		private static void printHelp(){
			StringBuilder result = new StringBuilder();
			result.append("Options:\n");
			for(Argument argument: Argument.values()){
				result.append("-"+StringUtils.join(argument.names, " -"));
				result.append(" "+StringUtils.join(argument.argNames, " "));
				result.append("\n");
				if(argument.help != null && argument.help.length() > 0){
					result.append("\t"+argument.help.replaceAll("\n","\n\t")+"\n");
				}
			}
			System.out.println(result.toString());
		}
	}
	
	public static Tokenizer whitespaceTokenizer = null;
	
	public static String[] tokenize(String input, TokenizerMethod method){
		switch(method){
		case WHITESPACE:
			return tokenize_whitespace(input);
		case REGEX:
			return tokenize_regex(input);
		default:
			throw new UnsupportedOperationException("The tokenizing method "+method+" is not recognized");
		}
	}
	
	public static String[] tokenize_whitespace(String input){
		if(whitespaceTokenizer == null){
			whitespaceTokenizer = new WhitespaceTokenizer();
		}
		return fix_tokenization(input, whitespaceTokenizer.tokenizeToString(input));
	}
	
	public static String[] tokenize_regex(String input){
		String[] tokens = input.split(" |((?<=[\\w\\p{IsL}])(?=[^\\w\\p{IsL}]))|((?<=[^\\w\\p{IsL}])(?=[\\w\\p{IsL}]))");
		return fix_tokenization(input, tokens);
	}
	
	private static String[] fix_tokenization(String input, String[] tokens){
		List<String> result = new ArrayList<String>();
		int lastEnd = 0;
		for(int i=0; i<tokens.length; i++){
			String token = tokens[i].trim();
			if(token.length() == 0){
				continue;
			}
			if(result.size() > 0){
				String prevToken = result.get(result.size()-1);
				if(prevToken.endsWith("<") && token.matches("(EMAIL|URL|IP|TIME|DATE|DECIMAL|name|#)")){
					result.set(result.size()-1, prevToken+token);
				} else if (prevToken.matches(".*(EMAIL|URL|IP|TIME|DATE|DECIMAL|name|#)$") && token.startsWith(">")){
					result.set(result.size()-1, prevToken+token);
				} else if (prevToken.endsWith("'") && token.matches("([dDsSmMtT]|ll|LL|re|RE|ve|VE).*") && lastEnd == input.indexOf(token, lastEnd)){
					result.set(result.size()-1, prevToken+token);
					if(result.size() >= 2){ // Split "can't" into "ca" "n't"
						String curToken = prevToken + token;
						prevToken = result.get(result.size()-2);
						if (prevToken.matches(".*[nN]$") && curToken.matches("'[tT]") && lastEnd-1-prevToken.length() == input.indexOf(prevToken+curToken, lastEnd-1-prevToken.length())){
							String notToken = prevToken.charAt(prevToken.length()-1)+curToken;
							result.set(result.size()-2, prevToken.substring(0, prevToken.length()-1));
							result.set(result.size()-1, notToken);
						}
					}
				} else {
					if(token.matches("([\"'`][,\\.?!]|[,\\.?!][\"'`]).*")){
						result.add(token.substring(0,1));
						result.add(token.substring(1));
					} else {
						result.add(token);
					}
				}
			} else {
				if(token.matches("([\"'`][,\\.?!]|[,\\.?!][\"'`]).*")){
					result.add(token.substring(0,1));
					result.add(token.substring(1));
				} else {
					result.add(token);
				}
			}
			lastEnd = input.indexOf(token, lastEnd)+token.length();
		}
		return result.toArray(new String[result.size()]);
	}
	
	public static void main(String[] args) throws IOException{
		boolean convertAllData = false;
		String inputFilename = null;
		String outputFilename = null;
		TokenizerMethod tokenizerMethod = null;
		int argIndex = 0;
		while(argIndex < args.length){
			String arg = args[argIndex];
			if(arg.length() > 0 && arg.charAt(0) == '-'){
				Argument argument = Argument.argWithName(arg.substring(1));
				switch(argument){
				case CONVERT_ALL_SMSNP_DATA:
					convertAllData = true;
					break;
				case INPUT:
					inputFilename = args[argIndex+1];
					break;
				case OUTPUT:
					outputFilename = args[argIndex+1];
					break;
				case TOKENIZER:
					tokenizerMethod = TokenizerMethod.valueOf(args[argIndex+1].toUpperCase());
					break;
				case HELP:
					Argument.printHelp();
					System.exit(0);
				}
				argIndex += argument.numArgs+1;
			} else {
				throw new IllegalArgumentException("Error while parsing: "+arg);
			}
		}
		if(convertAllData){
			convertAllData();
		} else {
			if(inputFilename != null && outputFilename != null && tokenizerMethod != null){
				Scanner input = new Scanner(new File(inputFilename));
				PrintStream output = new PrintStream(new File(outputFilename));
				System.out.print(String.format("Tokenizing %s into %s using %s...", inputFilename, outputFilename, tokenizerMethod));
				long start = System.currentTimeMillis();
				while(input.hasNextLine()){
					String[] tokens = tokenize(input.nextLine(), tokenizerMethod);
					output.println(StringUtils.join(tokens, " "));
				}
				long end = System.currentTimeMillis();
				System.out.println(String.format("Done in %.3fs", (end-start)/1000.0));
				input.close();
				output.close();
			} else {
				String[] inputs = new String[]{
						"Think will reach about<DECIMAL> .",
						"Yea they went there from promenade. Usb3 nowhere near <#> times la.<DECIMAL> in was abt <DECIMAL> times.",
						"Woot sob sob sob sorry my angel T.T... all my fault ~~~~ haha with uthen energetic le lol...",
						"Chou baobei. Gooooooood morning!:*:* hugs u tightly in ur dream.hee",
						"Lol i mean wah, 又是我做坏人.",
						"I'm so sad le:-(",
						"You can't do this to me =(",
						"YOU'RE THE BEST, MAN!",
						"'This is bad', he said.",
						"Ahhhh my msn spoilt again,=(",
						"I am not sure about night menu. . . I know only about noon menu",
						};
				for(String input: inputs){
					System.out.printf("Regex\n%s:\n%s\n", input, Arrays.asList(tokenize_regex(input)));
					System.out.printf("Whitespace\n%s:\n%s\n", input, Arrays.asList(tokenize_whitespace(input)));
				}
				Scanner sc = new Scanner(System.in);
				while(true){
					System.out.print("Enter text: ");
					String input = sc.nextLine();
					if(input == null){
						break;
					}
					System.out.printf("Regex\n%s:\n%s\n", input, Arrays.asList(tokenize_regex(input)));
					System.out.printf("Whitespace\n%s:\n%s\n", input, Arrays.asList(tokenize_whitespace(input)));
				}
				sc.close();
			}
		}
	}
	
	protected static void convertAllData() throws IOException{
		PrintStream outstream = null;
		for(boolean useGoldTokenization: new boolean[]{true, false}){
			for(TokenizerMethod tokenizerMethod: TokenizerMethod.values()){
				for(String fileType: new String[]{"dev", "test", "train"}){
					String filename = "data/SMSNP."+fileType;
					SMSNPInstance[] instances = SMSNPUtil.readData(filename, false, false);
					File outputFile = new File("data/SMSNP.conll."+tokenizerMethod.toString().toLowerCase()+(useGoldTokenization ? ".gold" : "")+"."+fileType);
					System.out.println("Converting "+fileType+" file into CoNLL format using tokenizer "+tokenizerMethod+" (useGold:"+useGoldTokenization+")...");
					outstream = new PrintStream(outputFile);
					for(SMSNPInstance instance: instances){
						instance.getInputTokenized(tokenizerMethod, useGoldTokenization, true);
						instance.getOutputTokenized(tokenizerMethod, useGoldTokenization, true);
						outstream.println(instance.toCoNLLString());
					}
					outstream.close();
				}
			}
		}
	}
}
