package com.statnlp.example.base;

import java.lang.reflect.InvocationTargetException;

import com.statnlp.commons.types.Instance;

public abstract class BaseInstance<SELF extends BaseInstance<SELF, IN, OUT>, IN, OUT> extends Instance {
	
	private static final long serialVersionUID = -5422835104552434445L;
	public IN input;
	public OUT output;
	public OUT prediction;

	public BaseInstance(int instanceId, double weight) {
		super(instanceId, weight);
	}

	@SuppressWarnings("unchecked")
	@Override
	public SELF duplicate() {
		SELF result;
		try {
			result = (SELF)this.getClass().getConstructor(int.class, double.class).newInstance(this.getInstanceId(), this.getWeight());
			result.input = duplicateInput();
			result.output = duplicateOutput();
			result.prediction = duplicatePrediction();
			return result;
		} catch (InstantiationException e1) {
			e1.printStackTrace();
		} catch (IllegalAccessException e1) {
			e1.printStackTrace();
		} catch (IllegalArgumentException e1) {
			e1.printStackTrace();
		} catch (InvocationTargetException e1) {
			e1.printStackTrace();
		} catch (NoSuchMethodException e1) {
			e1.printStackTrace();
		} catch (SecurityException e1) {
			e1.printStackTrace();
		}
		return null;
	}

	/**
	 * Duplicate the input.<br>
	 * Note that generally it is expected that the returned object is not the same object
	 * @return
	 */
	public abstract IN duplicateInput();
	/**
	 * Duplicate the output.<br>
	 * Note that generally it is expected that the returned object is not the same object
	 * @return
	 */
	public abstract OUT duplicateOutput();
	/**
	 * Duplicate the prediction.<br>
	 * Note that generally it is expected that the returned object is not the same object
	 * @return
	 */
	public abstract OUT duplicatePrediction();

	@Override
	public void removeOutput() {
		output = null;
	}

	@Override
	public void removePrediction() {
		prediction = null;
	}

	@Override
	public IN getInput() {
		return input;
	}

	@Override
	public OUT getOutput() {
		return output;
	}

	@Override
	public OUT getPrediction() {
		return prediction;
	}

	@Override
	public boolean hasOutput() {
		return output != null;
	}

	@Override
	public boolean hasPrediction() {
		return prediction != null;
	}
	
	@SuppressWarnings("unchecked")
	public void setOutput(Object o) {
		output = (OUT)o;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void setPrediction(Object o) {
		prediction = (OUT)o;
	}

}
