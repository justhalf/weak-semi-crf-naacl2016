package com.statnlp.experiment.smsnp;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import justhalf.nlp.tokenizer.StanfordTokenizer;
import justhalf.nlp.tokenizer.Tokenizer;
import justhalf.nlp.tokenizer.WhitespaceTokenizer;

public class SMSNPTokenizer implements Serializable {
	
	private static final long serialVersionUID = -2097450154196277909L;

	public enum TokenizerMethod {
		WHITESPACE,
		STANFORD,
		SMSNP,
	}
	
	public static Tokenizer stanfordTokenizer = null;
	public static Tokenizer whitespaceTokenizer = null;
	
	public static String[] tokenize(String input, TokenizerMethod method){
		switch(method){
		case WHITESPACE:
			return tokenize_whitespace(input);
		case STANFORD:
			return tokenize_stanford(input);
		case SMSNP:
			return tokenize_smsnp(input);
		default:
			throw new UnsupportedOperationException("The tokenizing method "+method+" is not recognized");
		}
	}
	
	public static String[] tokenize_whitespace(String input){
		if(whitespaceTokenizer == null){
			whitespaceTokenizer = new WhitespaceTokenizer();
		}
		return fix_tokenization(whitespaceTokenizer.tokenizeToString(input));
	}
	
	public static String[] tokenize_stanford(String input){
		if(stanfordTokenizer == null){
			stanfordTokenizer = new StanfordTokenizer();
		}
		return fix_tokenization(stanfordTokenizer.tokenizeToString(input));
	}
	
	public static String[] tokenize_smsnp(String input){
		String[] tokens = input.split("(?<=[\\w\\p{script=Han}]) *(?=[^\\w\\p{script=Han} ])|(?<=[^\\w\\p{script=Han} ]) *(?=[\\w\\p{script=Han}])");
		return fix_tokenization(tokens);
	}
	
	private static String[] fix_tokenization(String[] tokens){
		List<String> result = new ArrayList<String>();
		for(int i=0; i<tokens.length; i++){
			String token = tokens[i].trim();
			if(token.length() == 0){
				continue;
			}
			if(result.size() > 0){
				String prevToken = result.get(result.size()-1);
				if(prevToken.endsWith("<") && token.matches("(DECIMAL|EMAIL|URL|name|#)")){
					result.set(result.size()-1, prevToken+token);
				} else if (prevToken.matches(".*(DECIMAL|EMAIL|URL|name|#)$") && token.startsWith(">")){
					result.set(result.size()-1, prevToken+token);
				} else if (prevToken.endsWith("'") && token.matches("[dDsSmMtT]|ll|LL|re|RE|ve|VE")){
					result.set(result.size()-1, prevToken+token);
					if(result.size() >= 2){ // Split "can't" into "ca" "n't"
						token = prevToken + token;
						prevToken = result.get(result.size()-2);
						if (prevToken.matches(".*[nN]$") && token.matches("'[tT]")){
							String notToken = prevToken.charAt(prevToken.length()-1)+token;
							result.set(result.size()-2, prevToken.substring(0, prevToken.length()-1));
							result.set(result.size()-1, notToken);
						}
					}
				} else {
					if(token.matches("[\"'][,\\.]")){
						result.add(token.substring(0,1));
						result.add(token.substring(1));
					} else {
						result.add(token);
					}
				}
			} else {
				if(token.matches("',")){
					result.add(token.substring(0,1));
					result.add(token.substring(1));
				} else {
					result.add(token);
				}
			}
		}
		return result.toArray(new String[result.size()]);
	}
	
	public static void main(String[] args){
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
				};
		for(String input: inputs){
			System.out.printf("SMSNP\n%s:\n%s\n", input, Arrays.asList(tokenize_smsnp(input)));
			System.out.printf("Stanford\n%s:\n%s\n", input, Arrays.asList(tokenize_stanford(input)));
			System.out.printf("Whitespace\n%s:\n%s\n", input, Arrays.asList(tokenize_whitespace(input)));
		}
	}
}
