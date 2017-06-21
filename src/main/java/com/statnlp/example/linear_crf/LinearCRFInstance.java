/** Statistical Natural Language Processing System
    Copyright (C) 2014-2016  Lu, Wei

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * 
 */
package com.statnlp.example.linear_crf;

import java.util.ArrayList;

import com.statnlp.example.base.BaseInstance;

/**
 * @author wei_lu
 *
 */
public class LinearCRFInstance extends BaseInstance<LinearCRFInstance, ArrayList<String[]>, ArrayList<Label>>{
	
	private static final long serialVersionUID = 6415577909487373660L;
	
	public LinearCRFInstance(int instanceId, double weight){
		super(instanceId, weight);
	}
	
	public LinearCRFInstance(int instanceId, double weight, ArrayList<String[]> inputs, ArrayList<Label> outputs) {
		super(instanceId, weight);
		this.input = inputs;
		this.output = outputs;
	}
	
	public ArrayList<String[]> duplicateInput(){
		return input == null ? null : new ArrayList<String[]>(input);
	}
	
	public ArrayList<Label> duplicateOutput(){
		return output == null ? null : new ArrayList<Label>(output);
	}

	public ArrayList<Label> duplicatePrediction(){
		return prediction == null ? null : new ArrayList<Label>(prediction);
	}
	
	@Override
	public int size() {
		return this.input.size();
	}

	public String toString(){
		StringBuilder result = new StringBuilder();
		result.append(this.getInstanceId()+" --- ");
		for(int i=0; i<size(); i++){
			if(hasOutput()){
				result.append(input.get(i)+"/"+output.get(i)+" ");
			} else {
				result.append(input.get(i)+" ");
			}
		}
		return result.toString();
	}
	
}
