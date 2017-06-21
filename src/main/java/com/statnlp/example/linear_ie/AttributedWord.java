package com.statnlp.example.linear_ie;

import java.util.HashMap;
import java.util.Map;

import com.statnlp.example.linear_ie.LinearIEFeatureManager.FeatureType;

public class AttributedWord {
	public Map<String, String> attributes;
	public String form;
	
	public AttributedWord(String form){
		this.form = form;
		attributes = new HashMap<String, String>();
		attributes.put(FeatureType.ALL_CAPS.name(), (form.matches("[A-Z]+")+""));
		attributes.put(FeatureType.ALL_DIGITS.name(), (form.matches("[0-9,.]+"))+"");
		attributes.put(FeatureType.ALL_ALPHANUMERIC.name(), (form.matches("[A-Z0-9]+"))+"");
		attributes.put(FeatureType.CONTAINS_DIGITS.name(), (form.matches(".*[0-9].*"))+"");
		attributes.put(FeatureType.CONTAINS_HYPHEN.name(), (form.matches(".*-.*"))+"");
		attributes.put(FeatureType.INITIAL_CAPS.name(), (form.matches("[A-Z].*"))+"");
		attributes.put(FeatureType.LONELY_INITIAL.name(), (form.matches("[A-Z]+."))+"");
		attributes.put(FeatureType.PUNCTUATION_MARK.name(), (form.matches(".*[-!@#$%^&*()+=,.<>/?\\;:'\"{}\\[\\]].*"))+"");
		attributes.put(FeatureType.ROMAN_NUMBER.name(), (form.matches("[MDCLXVI]+"))+"");
		attributes.put(FeatureType.SINGLE_CHARACTER.name(), (form.length() == 0)+"");
		attributes.put(FeatureType.URL.name(), (form.matches("(https?://|www\\.).*"))+"");
	}
	
	public void addAttribute(String attributeName, String value){
		attributes.put(attributeName, value);
	}
	
	public String getAttribute(String attributeName){
		return attributes.get(attributeName);
	}
	
	public Map<String, String> getAttributes(){
		return attributes;
	}
	
	public String toString(){
		return form;
	}
}
