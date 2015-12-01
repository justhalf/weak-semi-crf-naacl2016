package com.statnlp.experiment.smsnp;

import java.util.List;

import com.statnlp.example.base.BaseInstance;

public class SMSNPInstance extends BaseInstance<SMSNPInstance, String, List<Span>> {
	
	private static final long serialVersionUID = -5338701879189642344L;
	
	public SMSNPInstance(int instanceId, String input, List<Span> output){
		this(instanceId, 1.0, input, output);
	}
	
	public SMSNPInstance(int instanceId, double weight) {
		this(instanceId, weight, null, null);
	}
	
	public SMSNPInstance(int instanceId, double weight, String input, List<Span> output){
		super(instanceId, weight);
		this.input = input;
		this.output = output;
	}

	@Override
	public int size() {
		return getInput().length();
	}

	public String toString(){
		StringBuilder builder = new StringBuilder();
		builder.append(getInstanceId()+":");
		builder.append(input);
		if(hasOutput()){
			builder.append("\n");
			for(Span span: output){
				builder.append(span+"|");
			}
		}
		return builder.toString();
	}
}
